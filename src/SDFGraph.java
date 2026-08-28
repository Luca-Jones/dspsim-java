import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import graph.Digraph;
import graph.GraphConfig;
import node.Node;
import node.NodeFactory;
import node.DelayNode;
import parser.*;
import util.PairMap;

public class SDFGraph {


	private Digraph<Node> graph;
	private Map<Node, Integer> multiplicities;
	private List<Node> schedule;
	private PairMap<Node, Queue<BigInteger>> buses;

	private SDFGraph(GraphConfig gc) {
		NodeFactory factory = new NodeFactory();
		graph = new Digraph<>(gc, factory);
		multiplicities = new IdentityHashMap<>();
		schedule = new ArrayList<>();
		buses = new PairMap<>();
	}

	public static SDFGraph loadFromFile(String filename) throws IOException {
		String fileContents = Files.readString(Path.of(filename));
		List<Token> rawTokens = Lexer.lex(fileContents);
		List<Token> tokens = Preprocessor.process(rawTokens);
		Parser parser = new Parser(tokens);
		GraphConfig gc = parser.parseGraph();
		SDFGraph sdf = new SDFGraph(gc);
		for (Node node : sdf.graph.getVertices()) {
			node.checkWiring(sdf.graph.getParents(node).size(), sdf.graph.getChildren(node).size());
			for (Node child : sdf.graph.getChildren(node)) {
				sdf.buses.put(node, child, new ArrayDeque<>());
			}
		}
		sdf.multiplicities = multiplicities(sdf.graph);
		sdf.schedule = schedule(sdf.graph, sdf.multiplicities);
		return sdf;
	}

	private static Map<Node, Integer> multiplicities(Digraph<Node> graph) {
		Map<Node, Fraction> fractions = new IdentityHashMap<>();
		Queue<Node> queue = new LinkedList<>();
		Set<Node> visited = new HashSet<>();
		graph.getVertices().stream().filter(n -> n.inputRate() == 0).forEach(queue::add);
		if (queue.isEmpty())
			throw new RuntimeException("Graph has no source node (a node with input rate 0).");

		while (!queue.isEmpty()) {
			Node node = queue.poll();
			visited.add(node);
			if (!fractions.containsKey(node))
				fractions.put(node, new Fraction(1, 1));
			Fraction nf = fractions.get(node);
			for (Node child : graph.getChildren(node)) {
				if (!fractions.containsKey(child)) {
					fractions.put(
						child,
						new Fraction (
							node.outputRate() * nf.num(),
							child.inputRate() * nf.den()
						)
					);
				} else {
					Fraction cf = fractions.get(child);
					if (
						(long) node.outputRate() * nf.num() * cf.den() !=
						(long) child.inputRate() * cf.num() * nf.den()
					) {
						System.out.println(
							"node: " + node + " " +
							node.outputRate() * nf.num() + " / " + nf.den()
							+ " vs " +
							"child: " + child + " " +
							child.inputRate() * cf.num() + " / " + cf.den()
						);
						throw new InconsistentSampleRateException();
					}
				}

				if (!visited.contains(child))
					queue.add(child);
			}
		}

		for (Node node : graph.getVertices())
			if (!fractions.containsKey(node))
				throw new RuntimeException("Node " + node + " is not reachable from any source node.");

		Map<Node, Integer> multiplicities = new IdentityHashMap<>();
		int lcm = fractions.entrySet().stream().map(e -> e.getValue().den()).reduce(1, SDFGraph::lcm);
		for (Map.Entry<Node, Fraction> entry : fractions.entrySet()) {
			Fraction f = entry.getValue();
			multiplicities.put(entry.getKey(), Math.multiplyExact(f.num(), lcm / f.den()));
		}
		return multiplicities;
	}

	public static class InconsistentSampleRateException extends RuntimeException {
		public InconsistentSampleRateException() {
			super("Inconsistent sample rates detected!");
		}
	}

	private record Fraction(int num, int den) {
		Fraction {
			int g = gcd(num, den);
			num /= g;
			den /= g;
		}
	}

	private static int gcd(int a, int b) {
		while (b != 0) {
			int tmp = a % b;
			a = b;
			b = tmp;
		}
		return a;
	}

	private static int lcm(int a, int b) {
		long l = (long) (a / gcd(a, b)) * b;
		if (l > Integer.MAX_VALUE)
			throw new ArithmeticException("Schedule multiplicities overflow int: lcm " + l);
		return (int) l;
	}

	private static List<Node> schedule(Digraph<Node> graph, Map<Node, Integer> multiplicities) {
		List<Node> schedule = new ArrayList<>();
		List<Node> nodes = new ArrayList<>();
		List<Node> toRemove = new ArrayList<>();
		PairMap<Node, Integer> tokens = new PairMap<>();
		boolean canFire = false;

		for (Node node : graph.getVertices()) {
			for (int i = 0; i < multiplicities.get(node); i++) {
				nodes.add(node);
			}
			for (Node child : graph.getChildren(node)) {
				tokens.put(node, child, (node instanceof DelayNode d) ? d.delay : 0);
			}
		}
		while (!nodes.isEmpty()) {
			for (Node node : nodes) {
				canFire = true;
				for (Node parent : graph.getParents(node)) {
					if (tokens.get(parent, node) < node.inputRate()) {
						canFire = false;
						break;
					}
				}
				if (canFire) {
					for (Node parent : graph.getParents(node))
						tokens.merge(parent, node, -node.inputRate(), Integer::sum);
					for (Node child : graph.getChildren(node))
						tokens.merge(node, child, node.outputRate(), Integer::sum);
					toRemove.add(node);
					schedule.add(node);
				}
			}
			if (toRemove.isEmpty())
				throw new RuntimeException("No valid schedule could be found.");
			for (Node node : toRemove) {
				nodes.remove(node);
			}
			toRemove.clear();
		}

		return schedule;
	}

	public void run(int iterations) {
		reset();
		for (int i = 0; i < iterations; i++) {
			tick();
		}
	}

	private void tick() {
		List<BigInteger> inputs = new ArrayList<>();
		for (Node node : schedule) {
			inputs.clear();
			for (Node parent : graph.getParents(node)) {
				for (int i = 0; i < node.inputRate(); i++) {
					inputs.add(buses.get(parent, node).poll());
				}
			}
			for (int i = 0; i < node.outputRate(); i++) {
				BigInteger output = node.evaluate(inputs);
				for (Node child : graph.getChildren(node)) {
					buses.get(node, child).add(output);
				}
			}
		}
	}

	private void reset() {
		for (Node node : graph.getVertices()) {
			node.reset();
			for (Node child : graph.getChildren(node)) {
				buses.get(node, child).clear();
				if (node instanceof DelayNode d) {
					for (int i = 0; i < d.delay; i++) {
						buses.get(node, child).add(BigInteger.ZERO);
					}
				}
			}
		}
	}

}

