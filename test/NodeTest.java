import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import node.ConstantNode;
import node.DataInNode;
import node.DataOutNode;
import node.DecimatorNode;
import node.DelayNode;
import node.GainNode;
import node.ImpulseNode;
import node.InterpolatorNode;
import node.MultiplierNode;
import node.Node;
import node.Node.InvalidWiringException;
import node.SineNode;
import node.SumNode;

public class NodeTest {

	@TempDir
	Path tmp;

	// ---------- Constant ----------

	@Test
	void constantAlwaysReturnsItsValue() {
		ConstantNode n = new ConstantNode(7);
		assertEquals(7, n.evaluate(List.of()));
		assertEquals(7, n.evaluate(List.of()));
	}

	@Test
	void constantRejectsInputs() {
		ConstantNode n = new ConstantNode(7);
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(List.of(1)));
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(null));
	}

	@Test
	void constantRates() {
		ConstantNode n = new ConstantNode(7);
		assertEquals(0, n.inputRate());
		assertEquals(1, n.outputRate());
	}

	@Test
	void constantWiring() {
		ConstantNode n = new ConstantNode(7);
		assertDoesNotThrow(() -> n.checkWiring(0, 1));
		assertDoesNotThrow(() -> n.checkWiring(0, 3));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(1, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(0, 0));
	}

	// ---------- Impulse ----------

	@Test
	void impulseEmitsOneThenZeros() {
		ImpulseNode n = new ImpulseNode();
		assertEquals(1, n.evaluate(List.of()));
		assertEquals(0, n.evaluate(List.of()));
		assertEquals(0, n.evaluate(List.of()));
	}

	@Test
	void impulseResetRearms() {
		ImpulseNode n = new ImpulseNode();
		n.evaluate(List.of());
		n.evaluate(List.of());
		n.reset();
		assertEquals(1, n.evaluate(List.of()));
	}

	@Test
	void impulseRejectsInputs() {
		assertThrows(IllegalArgumentException.class, () -> new ImpulseNode().evaluate(List.of(1)));
	}

	@Test
	void impulseWiring() {
		ImpulseNode n = new ImpulseNode();
		assertEquals(0, n.inputRate());
		assertDoesNotThrow(() -> n.checkWiring(0, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(1, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(0, 0));
	}

	// ---------- Sine ----------

	@Test
	void sinePeriodFour() {
		SineNode n = new SineNode(10, 4);
		int[] expected = {0, 10, 0, -10, 0, 10, 0, -10};
		for (int e : expected)
			assertEquals(e, n.evaluate(List.of()));
	}

	@Test
	void sineWithPhaseOffset() {
		// sin(2*pi*(n+2)/8)*100
		SineNode n = new SineNode(100, 8, 2);
		int[] expected = {100, 71, 0, -71, -100, -71, 0, 71};
		for (int e : expected)
			assertEquals(e, n.evaluate(List.of()));
	}

	@Test
	void sineRoundsToNearestInteger() {
		// sin(2*pi/3)*10 = 8.66 -> 9
		SineNode n = new SineNode(10, 3);
		int[] expected = {0, 9, -9, 0, 9, -9};
		for (int e : expected)
			assertEquals(e, n.evaluate(List.of()));
	}

	@Test
	void sineResetRestartsSequence() {
		SineNode n = new SineNode(10, 4);
		n.evaluate(List.of());
		n.evaluate(List.of());
		n.reset();
		assertEquals(0, n.evaluate(List.of()));
		assertEquals(10, n.evaluate(List.of()));
	}

	@Test
	void sinePeriodZeroSilentlyProducesZeros() {
		// quirk pinned as-is: n/0 -> NaN or Infinity, sin -> NaN,
		// Math.round(NaN) = 0, so every sample is 0 with no error
		SineNode n = new SineNode(10, 0);
		assertEquals(0, n.evaluate(List.of()));
		assertEquals(0, n.evaluate(List.of()));
		assertEquals(0, n.evaluate(List.of()));
	}

	@Test
	void sineRejectsInputs() {
		assertThrows(RuntimeException.class, () -> new SineNode(1, 4).evaluate(List.of(1)));
	}

	@Test
	void sineWiring() {
		SineNode n = new SineNode(1, 4);
		assertEquals(0, n.inputRate());
		assertDoesNotThrow(() -> n.checkWiring(0, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(1, 1));
	}

	// ---------- Gain ----------

	@Test
	void gainMultiplies() {
		assertEquals(12, new GainNode(3).evaluate(List.of(4)));
		assertEquals(-8, new GainNode(-2).evaluate(List.of(4)));
		assertEquals(0, new GainNode(5).evaluate(List.of(0)));
	}

	@Test
	void gainRejectsWrongArity() {
		GainNode n = new GainNode(3);
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(List.of()));
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(List.of(1, 2)));
	}

	@Test
	void gainWiring() {
		GainNode n = new GainNode(3);
		assertDoesNotThrow(() -> n.checkWiring(1, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(0, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(2, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(1, 2));
	}

	// ---------- Sum ----------

	@Test
	void sumAddsTwoInputs() {
		assertEquals(3, new SumNode().evaluate(List.of(1, 2)));
	}

	@Test
	void sumAddsManyInputs() {
		assertEquals(10, new SumNode().evaluate(List.of(1, 2, 3, 4)));
		assertEquals(0, new SumNode().evaluate(List.of(5, -5)));
	}

	@Test
	void sumRejectsFewerThanTwoInputs() {
		SumNode n = new SumNode();
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(List.of()));
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(List.of(1)));
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(null));
	}

	@Test
	void sumWiring() {
		SumNode n = new SumNode();
		assertDoesNotThrow(() -> n.checkWiring(2, 1));
		assertDoesNotThrow(() -> n.checkWiring(5, 2));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(1, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(2, 0));
	}

	// ---------- Multiplier ----------

	@Test
	void multiplierMultipliesTwoInputs() {
		assertEquals(12, new MultiplierNode().evaluate(List.of(3, 4)));
		assertEquals(-12, new MultiplierNode().evaluate(List.of(3, -4)));
		assertEquals(0, new MultiplierNode().evaluate(List.of(0, 9)));
	}

	@Test
	void multiplierRejectsWrongArity() {
		MultiplierNode n = new MultiplierNode();
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(List.of(1)));
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(List.of(1, 2, 3)));
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(null));
	}

	@Test
	void multiplierWiring() {
		MultiplierNode n = new MultiplierNode();
		assertDoesNotThrow(() -> n.checkWiring(2, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(1, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(3, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(2, 0));
	}

	// ---------- Delay ----------

	@Test
	void delayDefaultsToOne() {
		assertEquals(1, new DelayNode().delay);
	}

	@Test
	void delayStoresConfiguredDelay() {
		assertEquals(3, new DelayNode(3).delay);
	}

	@Test
	void delayEvaluatePassesThrough() {
		// the delay itself is realized by bus priming in SDFGraph
		assertEquals(7, new DelayNode().evaluate(List.of(7)));
	}

	@Test
	void delayRejectsWrongArity() {
		DelayNode n = new DelayNode();
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(List.of()));
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(List.of(1, 2)));
	}

	@Test
	void delayWiring() {
		DelayNode n = new DelayNode();
		assertDoesNotThrow(() -> n.checkWiring(1, 1));
		assertDoesNotThrow(() -> n.checkWiring(1, 2));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(0, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(2, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(1, 0));
	}

	// ---------- Decimator ----------

	@Test
	void decimatorInputRateIsRatio() {
		assertEquals(2, new DecimatorNode(2).inputRate());
		assertEquals(3, new DecimatorNode(3).inputRate());
	}

	@Test
	void decimatorRatioTwoKeepsFirstSample() {
		assertEquals(9, new DecimatorNode(2).evaluate(List.of(9, 3)));
	}

	@Test
	void decimatorRejectsWrongArity() {
		assertThrows(IllegalArgumentException.class, () -> new DecimatorNode(2).evaluate(List.of(1)));
	}

	@Test
	void decimatorRatioThreeKeepsFirstSample() {
		assertEquals(7, new DecimatorNode(3).evaluate(List.of(7, 5, 6)));
	}

	@Test
	void decimatorRejectsRatioBelowOne() {
		assertThrows(IllegalArgumentException.class, () -> new DecimatorNode(0));
		assertThrows(IllegalArgumentException.class, () -> new DecimatorNode(-2));
	}

	@Test
	void decimatorWiring() {
		DecimatorNode n = new DecimatorNode(2);
		assertDoesNotThrow(() -> n.checkWiring(1, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(2, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(1, 0));
	}

	// ---------- Interpolator ----------

	@Test
	void interpolatorOutputRateIsRatio() {
		assertEquals(3, new InterpolatorNode(3).outputRate());
		assertEquals(1, new InterpolatorNode(3).inputRate());
	}

	@Test
	void interpolatorZeroStuffs() {
		InterpolatorNode n = new InterpolatorNode(3);
		assertEquals(5, n.evaluate(List.of(5)));
		assertEquals(0, n.evaluate(List.of(5)));
		assertEquals(0, n.evaluate(List.of(5)));
		assertEquals(6, n.evaluate(List.of(6)));
	}

	@Test
	void interpolatorResetRestartsPhase() {
		InterpolatorNode n = new InterpolatorNode(3);
		n.evaluate(List.of(5));
		n.evaluate(List.of(5));
		n.reset();
		assertEquals(9, n.evaluate(List.of(9)));
	}

	@Test
	void interpolatorRejectsWrongArity() {
		InterpolatorNode n = new InterpolatorNode(2);
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(List.of()));
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(List.of(1, 2)));
	}

	@Test
	void interpolatorWiring() {
		InterpolatorNode n = new InterpolatorNode(2);
		assertDoesNotThrow(() -> n.checkWiring(1, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(0, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(1, 2));
	}

	// ---------- DataIn ----------

	@Test
	void dataInReadsOneIntPerLine() throws Exception {
		Path f = tmp.resolve("in.csv");
		Files.writeString(f, "1\n2\n3\n");
		DataInNode n = new DataInNode(f.toString());
		assertEquals(1, n.evaluate(List.of()));
		assertEquals(2, n.evaluate(List.of()));
		assertEquals(3, n.evaluate(List.of()));
	}

	@Test
	void dataInPadsWithZerosPastEndOfFile() throws Exception {
		Path f = tmp.resolve("in.csv");
		Files.writeString(f, "5\n");
		DataInNode n = new DataInNode(f.toString());
		assertEquals(5, n.evaluate(List.of()));
		assertEquals(0, n.evaluate(List.of()));
		assertEquals(0, n.evaluate(List.of()));
	}

	@Test
	void dataInTakesFirstColumnOfCsv() throws Exception {
		Path f = tmp.resolve("in.csv");
		Files.writeString(f, "5, 100\n6, 200\n");
		DataInNode n = new DataInNode(f.toString());
		assertEquals(5, n.evaluate(List.of()));
		assertEquals(6, n.evaluate(List.of()));
	}

	@Test
	void dataInSkipsHeaderLines() throws Exception {
		Path f = tmp.resolve("in.csv");
		Files.writeString(f, "time,value\n4\n7\n");
		DataInNode n = new DataInNode(f.toString());
		assertEquals(4, n.evaluate(List.of()));
		assertEquals(7, n.evaluate(List.of()));
	}

	@Test
	void dataInStopsAtFirstNonNumericLineAfterData() throws Exception {
		Path f = tmp.resolve("in.csv");
		Files.writeString(f, "1\nnot a number\n2\n");
		DataInNode n = new DataInNode(f.toString());
		assertEquals(1, n.evaluate(List.of()));
		assertEquals(0, n.evaluate(List.of())); // 2 is never read
	}

	@Test
	void dataInHandlesNegativeValuesAndWhitespace() throws Exception {
		Path f = tmp.resolve("in.csv");
		Files.writeString(f, "  -3 \n 4\n");
		DataInNode n = new DataInNode(f.toString());
		assertEquals(-3, n.evaluate(List.of()));
		assertEquals(4, n.evaluate(List.of()));
	}

	@Test
	void dataInResetRestartsStream() throws Exception {
		Path f = tmp.resolve("in.csv");
		Files.writeString(f, "1\n2\n");
		DataInNode n = new DataInNode(f.toString());
		n.evaluate(List.of());
		n.evaluate(List.of());
		n.reset();
		assertEquals(1, n.evaluate(List.of()));
	}

	@Test
	void dataInMissingFileThrows() {
		RuntimeException e = assertThrows(RuntimeException.class,
			() -> new DataInNode(tmp.resolve("nope.csv").toString()));
		assertTrue(e.getMessage().contains("Cannot open input file"));
	}

	@Test
	void dataInRejectsInputsAndWiring() throws Exception {
		Path f = tmp.resolve("in.csv");
		Files.writeString(f, "1\n");
		DataInNode n = new DataInNode(f.toString());
		assertEquals(0, n.inputRate());
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(List.of(1)));
		assertDoesNotThrow(() -> n.checkWiring(0, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(1, 1));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(0, 0));
	}

	// ---------- DataOut ----------

	@Test
	void dataOutWritesCsvLinesAndPassesValueThrough() throws Exception {
		Path f = tmp.resolve("out.csv");
		DataOutNode n = new DataOutNode(f.toString());
		assertEquals(42, n.evaluate(List.of(42)));
		assertEquals(-1, n.evaluate(List.of(-1)));
		assertEquals(List.of("42,", "-1,"), Files.readAllLines(f));
	}

	@Test
	void dataOutRejectsWrongArity() throws Exception {
		DataOutNode n = new DataOutNode(tmp.resolve("out.csv").toString());
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(List.of()));
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(List.of(1, 2)));
		assertThrows(IllegalArgumentException.class, () -> n.evaluate(null));
	}

	@Test
	void dataOutUnwritableFileThrows() {
		RuntimeException e = assertThrows(RuntimeException.class,
			() -> new DataOutNode(tmp.resolve("no/such/dir/out.csv").toString()));
		assertTrue(e.getMessage().contains("Cannot open output file"));
	}

	@Test
	void dataOutWiring() throws Exception {
		DataOutNode n = new DataOutNode(tmp.resolve("out.csv").toString());
		assertDoesNotThrow(() -> n.checkWiring(1, 0));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(0, 0));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(2, 0));
		assertThrows(InvalidWiringException.class, () -> n.checkWiring(1, 1));
	}

	// ---------- Node defaults ----------

	@Test
	void defaultRatesAreOne() {
		Node n = new GainNode(1);
		assertEquals(1, n.inputRate());
		assertEquals(1, n.outputRate());
	}
}
