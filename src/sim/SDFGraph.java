package sim;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

import graph.Digraph;
import graph.EdgeConfig;
import graph.GraphConfig;
import node.Node;
import node.NodeFactory;
import parser.*;
import util.PairMap;

public class SDFGraph {


	private Digraph<Node> graph;
	private Map<Node, Integer> multiplicities;
	private List<Node> schedule;
	private PairMap<Node, Bus> buses;
	private Map<String, Ratio> sinkRates;

	private SDFGraph(GraphConfig gc) {
		NodeFactory factory = new NodeFactory();
		graph = new Digraph<>(gc, factory);
		multiplicities = new IdentityHashMap<>();
		schedule = new ArrayList<>();
		buses = new PairMap<>();
		sinkRates = Map.of();
	}

	public static SDFGraph loadFromFile(String filename) throws IOException {
		String fileContents = Files.readString(Path.of(filename));
		List<Token> rawTokens = Lexer.lex(fileContents);
		List<Token> tokens = Preprocessor.process(rawTokens);
		Parser parser = new Parser(tokens);
		GraphConfig gc = parser.parseGraph();
		for (EdgeConfig ec : gc.edgeConfigs())
			busWidth(ec);
		SDFGraph sdf = new SDFGraph(gc);
		for (Node node : sdf.graph.getVertices()) {
			node.checkWiring(sdf.graph.getParents(node).size(), sdf.graph.getChildren(node).size());
			for (Node child : sdf.graph.getChildren(node)) {
				sdf.buses.put(node, child, new Bus(busWidth(sdf.graph.getEdgeAttributes(node, child))));
			}
		}
		sdf.multiplicities = multiplicities(sdf.graph);
		sdf.schedule = schedule(sdf.graph, sdf.multiplicities);
		sdf.sinkRates = sinkRates(sdf.graph, sdf.multiplicities);
		return sdf;
	}

	/** Checks an edge's attributes, naming the edge in the error. */
	private static void busWidth(EdgeConfig ec) {
		try {
			busWidth(ec.attributes());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(ec.from() + " -> " + ec.to() + ": " + e.getMessage());
		}
	}

	/** The w attribute, or Bus.UNBOUNDED when absent. */
	private static int busWidth(Map<String, String> attrs) {
		for (String key : attrs.keySet())
			if (!key.equals("w"))
				throw new IllegalArgumentException("unknown edge attribute " + key);
		if (!attrs.containsKey("w"))
			return Bus.UNBOUNDED;
		try {
			int w = Integer.parseInt(attrs.get("w"));
			if (w >= 1)
				return w;
		} catch (NumberFormatException ignored) {
			// reported below
		}
		throw new IllegalArgumentException("w must be a positive integer, was " + attrs.get("w"));
	}

	private static Map<Node, Integer> multiplicities(Digraph<Node> graph) {
		Map<Node, Fraction> fractions = new IdentityHashMap<>();
		Queue<Node> queue = new LinkedList<>();
		Set<Node> visited = new HashSet<>();
		List<Node> sources = graph.getVertices().stream().filter(n -> n.inputRate() == 0).toList();
		if (sources.isEmpty())
			throw new RuntimeException("Graph has no source node (a node with input rate 0).");

		Queue<Node> seeds = new LinkedList<>(sources);
		while (!queue.isEmpty() || !seeds.isEmpty()) {
			if (queue.isEmpty()) {
				Node seed = seeds.poll();
				if (fractions.containsKey(seed))
					continue;
				fractions.put(seed, new Fraction(1, 1));
				queue.add(seed);
			}

			Node node = queue.poll();
			visited.add(node);
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

			for (Node parent : graph.getParents(node)) {
				if (!fractions.containsKey(parent)) {
					fractions.put(parent,
						new Fraction(
							node.inputRate() * nf.num(),
							parent.outputRate() * nf.den()
						)
					);
					queue.add(parent);
				}
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

	/**
	 * Each sink's sample rate as a fraction of the highest sample rate in its
	 * connected component, keyed by the sink's name in the graph file. A sink
	 * is a node with no children, e.g. a dataout.
	 */
	public Map<String, Ratio> sinkRates() {
		return sinkRates;
	}

	/** A reduced fraction num/den; 1/1 is the highest rate in the component. */
	public record Ratio(long num, long den) {
		public Ratio {
			long g = gcd(num, den);
			num /= g;
			den /= g;
		}

		/** This fraction of fs, e.g. a sink's rate in Hz given the highest rate in Hz. */
		public double times(double fs) {
			return fs * num / den;
		}
	}

	private static Map<String, Ratio> sinkRates(Digraph<Node> graph, Map<Node, Integer> multiplicities) {
		// The samples an edge carries per schedule period are its source's
		// firings times its output rate. A rate changer fires at one rate and
		// outputs at another, so the peak is a property of edges, not nodes.
		Map<Node, Long> peaks = new IdentityHashMap<>();
		for (Node start : graph.getVertices()) {
			if (peaks.containsKey(start))
				continue;
			List<Node> component = new ArrayList<>();
			Deque<Node> stack = new ArrayDeque<>();
			stack.push(start);
			peaks.put(start, 0L); // doubles as the visited mark until the peak is known
			long peak = 0;
			while (!stack.isEmpty()) {
				Node node = stack.pop();
				component.add(node);
				if (!graph.getChildren(node).isEmpty())
					peak = Math.max(peak, (long) multiplicities.get(node) * node.outputRate());
				for (List<Node> neighbors : List.of(graph.getChildren(node), graph.getParents(node)))
					for (Node neighbor : neighbors)
						if (peaks.putIfAbsent(neighbor, 0L) == null)
							stack.push(neighbor);
			}
			for (Node node : component)
				peaks.put(node, peak);
		}

		Map<String, Ratio> rates = new TreeMap<>();
		graph.getVertexNames().forEach((name, node) -> {
			if (graph.getChildren(node).isEmpty())
				rates.put(name, new Ratio((long) multiplicities.get(node) * node.inputRate(), peaks.get(node)));
		});
		return Collections.unmodifiableMap(rates);
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

	private static long gcd(long a, long b) {
		while (b != 0) {
			long tmp = a % b;
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
				tokens.put(node, child, node.initialTokens());
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
					buses.get(node, child).push(output);
				}
			}
		}
	}

	private void reset() {
		for (Node node : graph.getVertices()) {
			node.reset();
			for (Node child : graph.getChildren(node)) {
				buses.get(node, child).clear();
				for (int i = 0; i < node.initialTokens(); i++) {
					buses.get(node, child).push(BigInteger.ZERO);
				}
			}
		}
	}

}

