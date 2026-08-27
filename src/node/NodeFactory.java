package node;

import java.io.ObjectInputFilter.Status;
import java.util.Map;

import graph.VertexFactory;
import graph.VertexConfig;

public class NodeFactory implements VertexFactory<Node> {

	public NodeFactory() {}

	@Override
	public Node createVertex(VertexConfig config) {
		if (!config.attributes().containsKey("type"))
			throw new RuntimeException("Node " + config.name() + " has no type.");
		String type = config.attributes().get("type").toLowerCase();

		return switch (type) {
			case "constant" -> createConstantNode(config.attributes());
			case "impulse" -> createImpulseNode();
			case "sine" -> createSineNode(config.attributes());
			case "datain" -> createDataInNode(config.attributes());
			case "gain" -> createGainNode(config.attributes());
			case "lshift" -> createLShiftNode(config.attributes());
			case "rshift" -> createRShiftNode(config.attributes());
			case "sum" -> createSumNode();
			case "multiplier" -> createMultiplierNode();
			case "delay" -> createDelayNode(config.attributes());
			case "decimator" -> createDecimatorNode(config.attributes());
			case "interpolator" -> createInterpolatorNode(config.attributes());
			case "hold" -> createSampleHoldNode(config.attributes());
			case "dataout" -> createDataOutNode(config.attributes());
			default -> throw new RuntimeException("No such node type: " + type);
		};
	}

	private static ConstantNode createConstantNode(Map<String, String> attrs) {
		if (!attrs.containsKey("value"))
			throw new RuntimeException("Constant needs a value attribute.");
		Integer value = Integer.parseInt(attrs.get("value"));
		return new ConstantNode(value);
	}

	private static ImpulseNode createImpulseNode() {
		return new ImpulseNode();
	}

	private static SineNode createSineNode(Map<String, String> attrs) {
		if (!attrs.containsKey("amplitude"))
			throw new RuntimeException("Sine needs an amplitude attribute.");
		if (!attrs.containsKey("period"))
			throw new RuntimeException("Sine needs a period attribute.");
		int amplitude = Integer.parseInt(attrs.get("amplitude"));
		int period = Integer.parseInt(attrs.get("period"));
		if (attrs.containsKey("phase")) {
			int phase = Integer.parseInt(attrs.get("phase"));
			return new SineNode(amplitude, period, phase);
		}
		return new SineNode(amplitude, period);
	}

	private static GainNode createGainNode(Map<String, String> attrs) {
		if (!attrs.containsKey("value"))
			throw new RuntimeException("Gain needs a value attribute.");
		Integer value = Integer.parseInt(attrs.get("value"));
		return new GainNode(value);
	}

	private static LShiftNode createLShiftNode(Map<String, String> attrs) {
		if (!attrs.containsKey("value"))
			throw new RuntimeException("LShift needs a value attribute.");
		Integer value = Integer.parseInt(attrs.get("value"));
		return new LShiftNode(value);
	}

	private static RShiftNode createRShiftNode(Map<String, String> attrs) {
		if (!attrs.containsKey("value"))
			throw new RuntimeException("RShift needs a value attribute.");
		Integer value = Integer.parseInt(attrs.get("value"));
		return new RShiftNode(value);
	}

	private static SumNode createSumNode() {
		return new SumNode();
	}

	private static MultiplierNode createMultiplierNode() {
		return new MultiplierNode();
	}

	private static DelayNode createDelayNode(Map<String, String> attrs) {
		if (attrs.containsKey("delay")) {
			Integer delay = Integer.parseInt(attrs.get("delay"));
			return new DelayNode(delay);
		}
		return new DelayNode();
	}

	private static DecimatorNode createDecimatorNode(Map<String, String> attrs) {
		if (!attrs.containsKey("ratio"))
			throw new RuntimeException("Decimator needs a ratio attribute.");
		Integer ratio = Integer.parseInt(attrs.get("ratio"));
		return new DecimatorNode(ratio);
	}

	private static SampleHoldNode createSampleHoldNode(Map<String, String> attrs) {
		if (!attrs.containsKey("ratio"))
			throw new RuntimeException("Hold needs a ratio attribute.");
		Integer ratio = Integer.parseInt(attrs.get("ratio"));
		return new SampleHoldNode(ratio);
	}

	private static InterpolatorNode createInterpolatorNode(Map<String, String> attrs) {
		if (!attrs.containsKey("ratio"))
			throw new RuntimeException("Interpolator needs a ratio attribute.");
		Integer ratio = Integer.parseInt(attrs.get("ratio"));
		return new InterpolatorNode(ratio);
	}

	private static DataInNode createDataInNode(Map<String, String> attrs) {
		if (!attrs.containsKey("file"))
			throw new RuntimeException("DataIn needs a file attribute.");
		return new DataInNode(attrs.get("file"));
	}

	private static DataOutNode createDataOutNode(Map<String, String> attrs) {
		if (attrs.containsKey("file"))
			return new DataOutNode(attrs.get("file"));
		return new DataOutNode();
	}

}

