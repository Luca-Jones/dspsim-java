import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import graph.VertexConfig;
import node.CombNode;
import node.ConstantNode;
import node.DataInNode;
import node.DataOutNode;
import node.DecimatorNode;
import node.DelayNode;
import node.GainNode;
import node.ImpulseNode;
import node.IntegratorNode;
import node.InterpolatorNode;
import node.MultiplierNode;
import node.Node;
import node.NodeFactory;
import node.SineNode;
import node.SumNode;

public class NodeFactoryTest {

	@TempDir
	Path tmp;

	private final NodeFactory factory = new NodeFactory();

	private Node make(String type, String... kv) {
		Map<String, String> attrs = new HashMap<>();
		if (type != null)
			attrs.put("type", type);
		for (int i = 0; i < kv.length; i += 2)
			attrs.put(kv[i], kv[i + 1]);
		return factory.createVertex(new VertexConfig("n", attrs));
	}

	@Test
	void createsConstant() {
		Node n = make("constant", "value", "9");
		assertInstanceOf(ConstantNode.class, n);
		assertEquals(BigInteger.valueOf(9), n.evaluate(List.of()));
	}

	@Test
	void createsImpulse() {
		assertInstanceOf(ImpulseNode.class, make("impulse"));
	}

	@Test
	void createsSine() {
		Node n = make("sine", "amplitude", "10", "period", "4");
		assertInstanceOf(SineNode.class, n);
		n.evaluate(List.of()); // n=0 -> 0
		assertEquals(BigInteger.valueOf(10), n.evaluate(List.of()));
	}

	@Test
	void createsSineWithPhase() {
		Node n = make("sine", "amplitude", "100", "period", "8", "phase", "2");
		assertEquals(BigInteger.valueOf(100), n.evaluate(List.of()));
	}

	@Test
	void createsGain() {
		Node n = make("gain", "value", "3");
		assertInstanceOf(GainNode.class, n);
		assertEquals(BigInteger.valueOf(6), n.evaluate(List.of(BigInteger.valueOf(2))));
	}

	@Test
	void createsSum() {
		assertInstanceOf(SumNode.class, make("sum"));
	}

	@Test
	void createsMultiplier() {
		assertInstanceOf(MultiplierNode.class, make("multiplier"));
	}

	@Test
	void createsDelayWithDefaultOfOne() {
		Node n = make("delay");
		assertInstanceOf(DelayNode.class, n);
		assertEquals(1, ((DelayNode) n).delay);
	}

	@Test
	void createsDelayWithExplicitDelay() {
		assertEquals(4, ((DelayNode) make("delay", "delay", "4")).delay);
	}

	@Test
	void createsIntegrator() {
		Node n = make("integrator");
		assertInstanceOf(IntegratorNode.class, n);
		assertEquals(1, n.initialTokens());
	}

	@Test
	void createsCombWithDefaultLengthOfOne() {
		Node n = make("comb");
		assertInstanceOf(CombNode.class, n);
		// L=1: second output is x[1] - x[0]
		n.evaluate(List.of(BigInteger.valueOf(3)));
		assertEquals(BigInteger.valueOf(2), n.evaluate(List.of(BigInteger.valueOf(5))));
	}

	@Test
	void createsCombWithExplicitLength() {
		Node n = make("comb", "value", "2");
		assertInstanceOf(CombNode.class, n);
		// L=2: third output is x[2] - x[0]
		n.evaluate(List.of(BigInteger.valueOf(3)));
		n.evaluate(List.of(BigInteger.valueOf(4)));
		assertEquals(BigInteger.valueOf(6), n.evaluate(List.of(BigInteger.valueOf(9))));
	}

	@Test
	void createsDecimator() {
		Node n = make("decimator", "ratio", "2");
		assertInstanceOf(DecimatorNode.class, n);
		assertEquals(2, n.inputRate());
	}

	@Test
	void createsInterpolator() {
		Node n = make("interpolator", "ratio", "3");
		assertInstanceOf(InterpolatorNode.class, n);
		assertEquals(3, n.outputRate());
	}

	@Test
	void createsDataIn() throws Exception {
		Path f = tmp.resolve("in.csv");
		Files.writeString(f, "8\n");
		Node n = make("datain", "file", f.toString());
		assertInstanceOf(DataInNode.class, n);
		assertEquals(BigInteger.valueOf(8), n.evaluate(List.of()));
	}

	@Test
	void createsDataOutWithFile() {
		Node n = make("dataout", "file", tmp.resolve("out.csv").toString());
		assertInstanceOf(DataOutNode.class, n);
	}

	@Test
	void createsDataOutDefaultingToStdout() {
		assertInstanceOf(DataOutNode.class, make("dataout"));
	}

	@Test
	void typeIsCaseInsensitive() {
		assertInstanceOf(ConstantNode.class, make("Constant", "value", "1"));
		assertInstanceOf(SumNode.class, make("SUM"));
	}

	@Test
	void numericAttributesMayBeQuoted() {
		// fir.dot uses value="2"
		Node n = make("gain", "value", "2");
		assertEquals(BigInteger.valueOf(4), n.evaluate(List.of(BigInteger.valueOf(2))));
	}

	@Test
	void missingTypeThrows() {
		RuntimeException e = assertThrows(RuntimeException.class, () -> make(null));
		assertTrue(e.getMessage().contains("has no type"));
	}

	@Test
	void unknownTypeThrows() {
		RuntimeException e = assertThrows(RuntimeException.class, () -> make("fft"));
		assertTrue(e.getMessage().contains("No such node type"));
	}

	@Test
	void constantWithoutValueThrows() {
		assertThrows(RuntimeException.class, () -> make("constant"));
	}

	@Test
	void gainWithoutValueThrows() {
		assertThrows(RuntimeException.class, () -> make("gain"));
	}

	@Test
	void sineWithoutAmplitudeThrows() {
		assertThrows(RuntimeException.class, () -> make("sine", "period", "4"));
	}

	@Test
	void sineWithoutPeriodThrows() {
		assertThrows(RuntimeException.class, () -> make("sine", "amplitude", "10"));
	}

	@Test
	void decimatorWithoutRatioThrows() {
		assertThrows(RuntimeException.class, () -> make("decimator"));
	}

	@Test
	void interpolatorWithoutRatioThrows() {
		assertThrows(RuntimeException.class, () -> make("interpolator"));
	}

	@Test
	void dataInWithoutFileThrows() {
		assertThrows(RuntimeException.class, () -> make("datain"));
	}

	@Test
	void nonNumericNumberAttributeThrows() {
		assertThrows(NumberFormatException.class, () -> make("gain", "value", "loud"));
	}
}
