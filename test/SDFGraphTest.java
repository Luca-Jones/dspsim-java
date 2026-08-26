import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import node.Node;

/**
 * End-to-end tests: write a .dot file, load it, run the simulation and
 * assert the exact samples that arrive in the DataOut file.
 *
 * Dot sources use %OUT% (and %IN%) placeholders for temp file paths.
 */
public class SDFGraphTest {

	@TempDir
	Path tmp;

	private SDFGraph load(String dot) throws IOException {
		Path dotFile = tmp.resolve("g.dot");
		Files.writeString(dotFile, dot
			.replace("%OUT%", tmp.resolve("out.csv").toString())
			.replace("%OUT2%", tmp.resolve("out2.csv").toString())
			.replace("%IN%", tmp.resolve("in.csv").toString()));
		return SDFGraph.loadFromFile(dotFile.toString());
	}

	private List<Integer> run(String dot, int iterations) throws IOException {
		load(dot).run(iterations);
		return output();
	}

	private List<Integer> output() throws IOException {
		return output("out.csv");
	}

	private List<Integer> output(String name) throws IOException {
		return Files.readAllLines(tmp.resolve(name)).stream()
			.map(s -> s.replace(",", "").trim())
			.filter(s -> !s.isEmpty())
			.map(Integer::parseInt)
			.toList();
	}

	private void writeInput(String content) throws IOException {
		Files.writeString(tmp.resolve("in.csv"), content);
	}

	// ---------- basic signal flow ----------

	@Test
	void constantThroughGain() throws IOException {
		List<Integer> out = run("""
			digraph {
				in [type="constant", value=2];
				g [type="gain", value=5];
				out [type="dataout", file="%OUT%"];
				in -> g;
				g -> out;
			}
			""", 3);
		assertEquals(List.of(10, 10, 10), out);
	}

	@Test
	void impulseStraightToOutput() throws IOException {
		List<Integer> out = run("""
			digraph {
				in [type="impulse"];
				out [type="dataout", file="%OUT%"];
				in -> out;
			}
			""", 4);
		assertEquals(List.of(1, 0, 0, 0), out);
	}

	@Test
	void sineStraightToOutput() throws IOException {
		List<Integer> out = run("""
			digraph {
				in [type="sine", amplitude=10, period=4];
				out [type="dataout", file="%OUT%"];
				in -> out;
			}
			""", 4);
		assertEquals(List.of(0, 10, 0, -10), out);
	}

	@Test
	void sumOfThreeConstants() throws IOException {
		List<Integer> out = run("""
			digraph {
				a [type="constant", value=1];
				b [type="constant", value=2];
				c [type="constant", value=3];
				s [type="sum"];
				out [type="dataout", file="%OUT%"];
				a -> s; b -> s; c -> s;
				s -> out;
			}
			""", 2);
		assertEquals(List.of(6, 6), out);
	}

	@Test
	void multiplierOfTwoConstants() throws IOException {
		List<Integer> out = run("""
			digraph {
				a [type="constant", value=3];
				b [type="constant", value=-4];
				m [type="multiplier"];
				out [type="dataout", file="%OUT%"];
				a -> m; b -> m;
				m -> out;
			}
			""", 2);
		assertEquals(List.of(-12, -12), out);
	}

	@Test
	void fanOutFeedsBothBranches() throws IOException {
		List<Integer> out = run("""
			digraph {
				in [type="impulse"];
				g2 [type="gain", value=2];
				g3 [type="gain", value=3];
				s [type="sum"];
				out [type="dataout", file="%OUT%"];
				in -> g2; in -> g3;
				g2 -> s; g3 -> s;
				s -> out;
			}
			""", 3);
		assertEquals(List.of(5, 0, 0), out);
	}

	// ---------- delays and feedback ----------

	@Test
	void delayShiftsImpulseByOne() throws IOException {
		List<Integer> out = run("""
			digraph {
				in [type="impulse"];
				d [type="delay"];
				out [type="dataout", file="%OUT%"];
				in -> d;
				d -> out;
			}
			""", 4);
		assertEquals(List.of(0, 1, 0, 0), out);
	}

	@Test
	void delayOfThreeShiftsImpulseByThree() throws IOException {
		List<Integer> out = run("""
			digraph {
				in [type="impulse"];
				d [type="delay", delay=3];
				out [type="dataout", file="%OUT%"];
				in -> d;
				d -> out;
			}
			""", 5);
		assertEquals(List.of(0, 0, 0, 1, 0), out);
	}

	@Test
	void chainedDelaysAccumulate() throws IOException {
		// constant 1 through delay(1) + delay(2) = total shift of 3
		List<Integer> out = run("""
			digraph {
				in [type="constant", value=1];
				d1 [type="delay"];
				d2 [type="delay", delay=2];
				out [type="dataout", file="%OUT%"];
				in -> d1;
				d1 -> d2;
				d2 -> out;
			}
			""", 6);
		assertEquals(List.of(0, 0, 0, 1, 1, 1), out);
	}

	@Test
	void accumulatorFeedbackLoop() throws IOException {
		// classic 1/(1-z^-1) integrator: running sum of a constant 1
		List<Integer> out = run("""
			digraph {
				in [type="constant", value=1];
				sum [type="sum"];
				delay [type="delay"];
				out [type="dataout", file="%OUT%"];
				in -> sum;
				sum -> delay;
				delay -> sum;
				sum -> out;
			}
			""", 5);
		assertEquals(List.of(1, 2, 3, 4, 5), out);
	}

	@Test
	void impulseIntoAccumulatorWithGain() throws IOException {
		// impulse integrates to a constant 1, gain -2 -> constant -2
		List<Integer> out = run("""
			digraph {
				in [type="impulse"];
				s [type="sum"];
				d [type="delay"];
				g [type="gain", value=-2];
				out [type="dataout", file="%OUT%"];
				in -> s;
				s -> d;
				d -> s;
				s -> g;
				g -> out;
			}
			""", 4);
		assertEquals(List.of(-2, -2, -2, -2), out);
	}

	private static final String FIR_DOT = """
		digraph {
			in [type="impulse"];
			d1 [type="delay"]; d2 [type="delay"]; d3 [type="delay"];
			h0 [type="gain", value=2];
			h1 [type="gain", value=4];
			h2 [type="gain", value=6];
			h3 [type="gain", value=8];
			s [type="sum"];
			out [type="dataout", file="%OUT%"];
			in -> h0; h0 -> s;
			in -> d1; d1 -> h1; h1 -> s;
			d1 -> d2; d2 -> h2; h2 -> s;
			d2 -> d3; d3 -> h3; h3 -> s;
			s -> out;
		}
		""";

	@Test
	void firFilterImpulseResponseIsItsTaps() throws IOException {
		assertEquals(List.of(2, 4, 6, 8, 0, 0), run(FIR_DOT, 6));
	}

	// ---------- multirate ----------

	@Test
	void interpolatorZeroStuffs() throws IOException {
		List<Integer> out = run("""
			digraph {
				in [type="impulse"];
				up [type="interpolator", ratio=3];
				out [type="dataout", file="%OUT%"];
				in -> up;
				up -> out;
			}
			""", 2);
		assertEquals(List.of(1, 0, 0, 0, 0, 0), out);
	}

	@Test
	void decimatorByTwoKeepsEveryOtherSample() throws IOException {
		writeInput("10\n1\n20\n2\n30\n3\n");
		List<Integer> out = run("""
			digraph {
				in [type="datain", file="%IN%"];
				down [type="decimator", ratio=2];
				out [type="dataout", file="%OUT%"];
				in -> down;
				down -> out;
			}
			""", 3);
		assertEquals(List.of(10, 20, 30), out);
	}

	@Test
	void interpolatorThenDecimator() throws IOException {
		// up by 4 then down by 2: net rate x2, one output sample kept per pair
		List<Integer> out = run("""
			digraph {
				in [type="impulse"];
				up [type="interpolator", ratio=4];
				down [type="decimator", ratio=2];
				out [type="dataout", file="%OUT%"];
				in -> up;
				up -> down;
				down -> out;
			}
			""", 2);
		assertEquals(List.of(1, 0, 0, 0), out);
	}

	// ---------- file I/O ----------

	@Test
	void dataInThroughGainPadsZerosPastEof() throws IOException {
		writeInput("1\n2\n3\n");
		List<Integer> out = run("""
			digraph {
				in [type="datain", file="%IN%"];
				g [type="gain", value=2];
				out [type="dataout", file="%OUT%"];
				in -> g;
				g -> out;
			}
			""", 5);
		assertEquals(List.of(2, 4, 6, 0, 0), out);
	}

	@Test
	void dataInSkipsCsvHeader() throws IOException {
		writeInput("time,value\n7, 0\n8, 0\n");
		List<Integer> out = run("""
			digraph {
				in [type="datain", file="%IN%"];
				out [type="dataout", file="%OUT%"];
				in -> out;
			}
			""", 2);
		assertEquals(List.of(7, 8), out);
	}

	@Test
	void outputFileUsesTrailingCommaFormat() throws IOException {
		run("""
			digraph {
				in [type="constant", value=5];
				out [type="dataout", file="%OUT%"];
				in -> out;
			}
			""", 2);
		assertEquals(List.of("5,", "5,"), Files.readAllLines(tmp.resolve("out.csv")));
	}

	@Test
	void zeroIterationsProducesNoOutput() throws IOException {
		assertEquals(List.of(), run("""
			digraph {
				in [type="constant", value=5];
				out [type="dataout", file="%OUT%"];
				in -> out;
			}
			""", 0));
	}

	@Test
	void secondRunRestartsStatefulNodes() throws IOException {
		SDFGraph g = load("""
			digraph {
				in [type="impulse"];
				out [type="dataout", file="%OUT%"];
				in -> out;
			}
			""");
		g.run(2);
		g.run(2);
		// both runs should start with a fresh impulse
		assertEquals(List.of(1, 0, 1, 0), output());
	}

	// ---------- load-time errors ----------

	@Test
	void missingDotFileThrowsIOException() {
		assertThrows(IOException.class,
			() -> SDFGraph.loadFromFile(tmp.resolve("missing.dot").toString()));
	}

	@Test
	void edgeToUndefinedNodeThrows() {
		RuntimeException e = assertThrows(RuntimeException.class, () -> load("""
			digraph {
				in [type="impulse"];
				in -> ghost;
			}
			"""));
		assertTrue(e.getMessage().contains("ghost"));
	}

	@Test
	void floatingNodeThrows() {
		RuntimeException e = assertThrows(RuntimeException.class, () -> load("""
			digraph {
				in [type="impulse"];
				out [type="dataout", file="%OUT%"];
				lonely [type="constant", value=1];
				in -> out;
			}
			"""));
		assertTrue(e.getMessage().contains("lonely"));
	}

	@Test
	void sumWithSingleInputFailsWiringCheck() {
		assertThrows(Node.InvalidWiringException.class, () -> load("""
			digraph {
				in [type="impulse"];
				s [type="sum"];
				out [type="dataout", file="%OUT%"];
				in -> s;
				s -> out;
			}
			"""));
	}

	@Test
	void gainWithTwoInputsFailsWiringCheck() {
		assertThrows(Node.InvalidWiringException.class, () -> load("""
			digraph {
				a [type="impulse"];
				b [type="impulse"];
				g [type="gain", value=1];
				out [type="dataout", file="%OUT%"];
				a -> g; b -> g;
				g -> out;
			}
			"""));
	}

	@Test
	void multiplierWithThreeInputsFailsWiringCheck() {
		assertThrows(Node.InvalidWiringException.class, () -> load("""
			digraph {
				a [type="constant", value=1];
				b [type="constant", value=2];
				c [type="constant", value=3];
				m [type="multiplier"];
				out [type="dataout", file="%OUT%"];
				a -> m; b -> m; c -> m;
				m -> out;
			}
			"""));
	}

	@Test
	void constantWithParentFailsWiringCheck() {
		assertThrows(Node.InvalidWiringException.class, () -> load("""
			digraph {
				a [type="impulse"];
				c [type="constant", value=1];
				out [type="dataout", file="%OUT%"];
				a -> c;
				c -> out;
			}
			"""));
	}

	@Test
	void dataOutWithChildFailsWiringCheck() {
		assertThrows(Node.InvalidWiringException.class, () -> load("""
			digraph {
				in [type="impulse"];
				out [type="dataout", file="%OUT%"];
				g [type="gain", value=1];
				out2 [type="dataout", file="%OUT%"];
				in -> out;
				out -> g;
				g -> out2;
			}
			"""));
	}

	@Test
	void feedbackLoopWithoutDelayHasNoSchedule() {
		RuntimeException e = assertThrows(RuntimeException.class, () -> load("""
			digraph {
				in [type="impulse"];
				s [type="sum"];
				g [type="gain", value=1];
				out [type="dataout", file="%OUT%"];
				in -> s;
				s -> g;
				g -> s;
				s -> out;
			}
			"""));
		assertTrue(e.getMessage().contains("No valid schedule"));
	}

	@Test
	void inconsistentSampleRatesThrow() {
		assertThrows(SDFGraph.InconsistentSampleRateException.class, () -> load("""
			digraph {
				a [type="impulse"];
				b [type="impulse"];
				up [type="interpolator", ratio=2];
				s [type="sum"];
				out [type="dataout", file="%OUT%"];
				a -> up;
				up -> s;
				b -> s;
				s -> out;
			}
			"""));
	}

	@Test
	void graphWithNoSourceFailsLoudly() {
		RuntimeException e = assertThrows(RuntimeException.class, () -> load("""
			digraph {
				d1 [type="delay"];
				d2 [type="delay"];
				d1 -> d2;
				d2 -> d1;
			}
			"""));
		assertTrue(e.getMessage().contains("source"));
	}

	@Test
	void subgraphUnreachableFromAnySourceThrows() {
		// the delay cycle is connected to nothing upstream, so it has no
		// firing rate relative to the sourced component
		RuntimeException e = assertThrows(RuntimeException.class, () -> load("""
			digraph {
				in [type="impulse"];
				out [type="dataout", file="%OUT%"];
				d1 [type="delay"];
				d2 [type="delay"];
				in -> out;
				d1 -> d2;
				d2 -> d1;
			}
			"""));
		assertTrue(e.getMessage().contains("source"));
	}

	@Test
	void nodeWithoutTypeThrowsThroughFullPipeline() {
		RuntimeException e = assertThrows(RuntimeException.class, () -> load("""
			digraph {
				mystery;
				out [type="dataout", file="%OUT%"];
				mystery -> out;
			}
			"""));
		assertTrue(e.getMessage().contains("has no type"));
	}

	@Test
	void unknownNodeTypeThrowsThroughFullPipeline() {
		RuntimeException e = assertThrows(RuntimeException.class, () -> load("""
			digraph {
				x [type="quantumfft"];
				out [type="dataout", file="%OUT%"];
				x -> out;
			}
			"""));
		assertTrue(e.getMessage().contains("No such node type"));
	}

	@Test
	void syntaxErrorInDotFileThrows() {
		assertThrows(RuntimeException.class, () -> load("digraph { in [type=\"impulse\" }"));
	}

	// ---------- duplicate edges ----------

	@Test
	void duplicateEdgesAreRejectedAtLoad() {
		RuntimeException e = assertThrows(RuntimeException.class, () -> load("""
			digraph {
				in [type="impulse"];
				s [type="sum"];
				out [type="dataout", file="%OUT%"];
				in -> s; in -> s;
				s -> out;
			}
			"""));
		assertTrue(e.getMessage().contains("Duplicate edge"));
	}

	// ---------- self-loops ----------

	@Test
	void delaySelfLoopCannotBeSourcedAndFailsToLoad() {
		// the loop consumes the delay's only input, so the subgraph can never
		// have a rate-0 source
		RuntimeException e = assertThrows(RuntimeException.class, () -> load("""
			digraph {
				d [type="delay"];
				out [type="dataout", file="%OUT%"];
				d -> d;
				d -> out;
			}
			"""));
		assertTrue(e.getMessage().contains("source"));
	}

	// ---------- more multirate ----------

	@Test
	void cascadedDecimatorsKeepEveryFourthSample() throws IOException {
		writeInput("1\n2\n3\n4\n5\n6\n7\n8\n");
		List<Integer> out = run("""
			digraph {
				in [type="datain", file="%IN%"];
				downA [type="decimator", ratio=2];
				downB [type="decimator", ratio=2];
				out [type="dataout", file="%OUT%"];
				in -> downA;
				downA -> downB;
				downB -> out;
			}
			""", 2);
		assertEquals(List.of(1, 5), out);
	}

	@Test
	void interpolatorDecimatorPairInsideFeedbackLoopAccumulates() throws IOException {
		// net rate 1 around the loop: up zero-stuffs [x, 0], down keeps x,
		// so the loop is the plain accumulator and the impulse integrates to 1
		List<Integer> out = run("""
			digraph {
				in [type="impulse"];
				s [type="sum"];
				up [type="interpolator", ratio=2];
				down [type="decimator", ratio=2];
				d [type="delay"];
				out [type="dataout", file="%OUT%"];
				in -> s;
				s -> up;
				up -> down;
				down -> d;
				d -> s;
				s -> out;
			}
			""", 4);
		assertEquals(List.of(1, 1, 1, 1), out);
	}

	@Test
	void multipleDataOutNodesWriteIndependently() throws IOException {
		List<Integer> out = run("""
			digraph {
				in [type="impulse"];
				up [type="interpolator", ratio=2];
				out [type="dataout", file="%OUT%"];
				out2 [type="dataout", file="%OUT2%"];
				in -> out;
				in -> up;
				up -> out2;
			}
			""", 2);
		assertEquals(List.of(1, 0), out);
		// out2 sits on the multirate branch and fires twice per iteration
		assertEquals(List.of(1, 0, 0, 0), output("out2.csv"));
	}

	// ---------- schedule determinism ----------

	@Test
	void sameGraphGivesSameOutputAcrossLoads() throws IOException {
		// Digraph.getVertices() is a HashSet, so schedule order can differ
		// per load; bus FIFO order must keep the output invariant anyway
		for (int i = 0; i < 5; i++)
			assertEquals(List.of(2, 4, 6, 8, 0, 0), run(FIR_DOT, 6), "load #" + i);
	}

	// ---------- overflow and nonsensical parameters ----------

	@Test
	void gainOverflowWrapsSilently() throws IOException {
		// as-documented: plain int arithmetic, 2 * Integer.MAX_VALUE wraps
		// to -2 with no error (sum and multiplier wrap the same way)
		List<Integer> out = run("""
			digraph {
				in [type="constant", value=2147483647];
				g [type="gain", value=2];
				out [type="dataout", file="%OUT%"];
				in -> g;
				g -> out;
			}
			""", 1);
		assertEquals(List.of(-2), out);
	}

	@Test
	void interpolatorRatioZeroSilentlyProducesNoOutput() throws IOException {
		// quirk pinned as-is: outputRate 0 gives everything downstream
		// multiplicity 0, so dataout never fires and the file stays empty
		// (negative ratios behave the same way)
		List<Integer> out = run("""
			digraph {
				in [type="impulse"];
				up [type="interpolator", ratio=0];
				out [type="dataout", file="%OUT%"];
				in -> up;
				up -> out;
			}
			""", 3);
		assertEquals(List.of(), out);
	}

	@Test
	void negativeDelayHasNoSchedule() {
		// delay=-1 starts the outgoing edge at -1 tokens, so the child can
		// never fire
		RuntimeException e = assertThrows(RuntimeException.class, () -> load("""
			digraph {
				in [type="impulse"];
				d [type="delay", delay=-1];
				out [type="dataout", file="%OUT%"];
				in -> d;
				d -> out;
			}
			"""));
		assertTrue(e.getMessage().contains("No valid schedule"));
	}

	// ---------- file I/O extras ----------

	@Test
	void dataOutToDevStdoutRuns() {
		// README documents writing to /dev/stdout
		assumeTrue(Files.exists(Path.of("/dev/stdout")));
		assertDoesNotThrow(() -> load("""
			digraph {
				in [type="constant", value=1];
				out [type="dataout", file="/dev/stdout"];
				in -> out;
			}
			""").run(2));
	}

	@Test
	void fileOutThenDataInRoundTrip() throws IOException {
		// temp-file version of resources/fileout.dot + datain.dot: first graph
		// writes trailing-comma CSV to %IN%, second reads it back through gain 2
		load("""
			digraph {
				in [type="constant", value=2];
				g [type="gain", value=5];
				out [type="dataout", file="%IN%"];
				in -> g;
				g -> out;
			}
			""").run(3);
		List<Integer> out = run("""
			digraph {
				in [type="datain", file="%IN%"];
				g [type="gain", value=2];
				out [type="dataout", file="%OUT%"];
				in -> g;
				g -> out;
			}
			""", 3);
		assertEquals(List.of(20, 20, 20), out);
	}

	// ---------- big-graph sanity ----------

	@Test
	void twoHundredNodeChainSchedulesAndRuns() throws IOException {
		StringBuilder dot = new StringBuilder("digraph {\n\tin [type=\"impulse\"];\n");
		for (int i = 0; i < 200; i++)
			dot.append("\tg").append(i).append(" [type=\"gain\", value=1];\n");
		dot.append("\tout [type=\"dataout\", file=\"%OUT%\"];\n\tin -> g0;\n");
		for (int i = 0; i < 199; i++)
			dot.append("\tg").append(i).append(" -> g").append(i + 1).append(";\n");
		dot.append("\tg199 -> out;\n}\n");
		assertEquals(List.of(1, 0, 0), run(dot.toString(), 3));
	}
}
