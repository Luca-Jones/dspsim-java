import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sim.SDFGraph;

/**
 * Sample rate resolution: a source (constant, impulse) has no rate of its own
 * and takes the rate of whatever it feeds, however far downstream that is.
 * Graphs whose rates genuinely disagree must still be rejected.
 *
 * Dot sources use %OUT%, %OUT2% and %IN% placeholders for temp file paths.
 */
public class SampleRateTest {

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

	private List<BigInteger> run(String dot, int iterations) throws IOException {
		load(dot).run(iterations);
		return output("out.csv");
	}

	private List<BigInteger> output(String name) throws IOException {
		return Files.readAllLines(tmp.resolve(name)).stream()
			.map(s -> s.replace(",", "").trim())
			.filter(s -> !s.isEmpty())
			.map(BigInteger::new)
			.toList();
	}

	/** Writes 1, 2, 3, ... n as the datain file. */
	private void writeRamp(int n) throws IOException {
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= n; i++)
			sb.append(i).append('\n');
		Files.writeString(tmp.resolve("in.csv"), sb.toString());
	}

	private static List<BigInteger> ints(long... values) {
		return LongStream.of(values).mapToObj(BigInteger::valueOf).toList();
	}

	// ---------- sources downstream of a rate change ----------

	@Test
	void constantDirectlyAfterDecimator() throws IOException {
		writeRamp(8);
		List<BigInteger> out = run("""
			digraph {
				in [type="datain", file="%IN%"];
				down [type="decimator", ratio=4];
				c [type="constant", value=3];
				m [type="multiplier"];
				out [type="dataout", file="%OUT%"];
				in -> down;
				down -> m;
				c -> m;
				m -> out;
			}
			""", 2);
		assertEquals(ints(3, 15), out);
	}

	@Test
	void constantOneBlockAwayFromDecimatedPath() throws IOException {
		writeRamp(8);
		List<BigInteger> out = run("""
			digraph {
				in [type="datain", file="%IN%"];
				down [type="decimator", ratio=4];
				c [type="constant", value=3];
				g [type="gain", value=2];
				m [type="multiplier"];
				out [type="dataout", file="%OUT%"];
				in -> down;
				down -> m;
				c -> g;
				g -> m;
				m -> out;
			}
			""", 2);
		assertEquals(ints(6, 30), out);
	}

	@Test
	void constantSeveralBlocksAwayFromDecimatedPath() throws IOException {
		writeRamp(8);
		List<BigInteger> out = run("""
			digraph {
				in [type="datain", file="%IN%"];
				down [type="decimator", ratio=4];
				c [type="constant", value=3];
				g1 [type="gain", value=2];
				g2 [type="gain", value=5];
				d [type="delay", delay=0];
				m [type="multiplier"];
				out [type="dataout", file="%OUT%"];
				in -> down;
				down -> m;
				c -> g1;
				g1 -> g2;
				g2 -> d;
				d -> m;
				m -> out;
			}
			""", 2);
		assertEquals(ints(30, 150), out);
	}

	@Test
	void constantAfterTwoDecimators() throws IOException {
		writeRamp(24);
		List<BigInteger> out = run("""
			digraph {
				in [type="datain", file="%IN%"];
				d1 [type="decimator", ratio=4];
				d2 [type="decimator", ratio=3];
				c [type="constant", value=2];
				g [type="gain", value=1];
				m [type="multiplier"];
				out [type="dataout", file="%OUT%"];
				in -> d1;
				d1 -> d2;
				d2 -> m;
				c -> g;
				g -> m;
				m -> out;
			}
			""", 2);
		assertEquals(ints(2, 26), out);
	}

	@Test
	void constantSharedByTwoMultipliersAtTheSameRate() throws IOException {
		writeRamp(8);
		List<BigInteger> out = run("""
			digraph {
				in [type="datain", file="%IN%"];
				down [type="decimator", ratio=4];
				c [type="constant", value=3];
				g [type="gain", value=1];
				m1 [type="multiplier"];
				m2 [type="multiplier"];
				s [type="sum"];
				out [type="dataout", file="%OUT%"];
				in -> down;
				down -> m1;
				down -> m2;
				c -> g;
				g -> m1;
				c -> m2;
				m1 -> s;
				m2 -> s;
				s -> out;
			}
			""", 2);
		assertEquals(ints(6, 30), out);
	}

	/** x * c computed as hi*x + (lo*x >> 4) with c split into hi and lo, the fir64 pattern. */
	@Test
	void splitCoefficientMultiplierAfterDecimator() throws IOException {
		writeRamp(16);
		List<BigInteger> out = run("""
			digraph {
				in [type="datain", file="%IN%"];
				down [type="decimator", ratio=4];
				c [type="constant", value=37];
				hi [type="rshift", value=4];
				hiShifted [type="lshift", value=4];
				neg [type="gain", value=-1];
				lo [type="sum"];
				mHi [type="multiplier"];
				mLo [type="multiplier"];
				loShift [type="rshift", value=4];
				s [type="sum"];
				out [type="dataout", file="%OUT%"];
				in -> down;
				c -> hi;
				hi -> hiShifted;
				hiShifted -> neg;
				neg -> lo;
				c -> lo;
				down -> mHi;
				hi -> mHi;
				down -> mLo;
				lo -> mLo;
				mLo -> loShift;
				mHi -> s;
				loShift -> s;
				s -> out;
			}
			""", 4);
		// floor(37 * x / 16) for x = 1, 5, 9, 13
		assertEquals(ints(2, 11, 20, 30), out);
	}

	/** An impulse-started counter loop, the mux2 select pattern, running after a decimator. */
	@Test
	void impulseDrivenSelectAfterDecimator() throws IOException {
		writeRamp(16);
		List<BigInteger> out = run("""
			digraph {
				in [type="datain", file="%IN%"];
				down [type="decimator", ratio=4];
				start [type="impulse"];
				count [type="sum"];
				wrap [type="delay", delay=2];
				gate [type="multiplier"];
				out [type="dataout", file="%OUT%"];
				in -> down;
				start -> count;
				count -> wrap;
				wrap -> count;
				down -> gate;
				count -> gate;
				gate -> out;
			}
			""", 4);
		// select is 1, 0, 1, 0 at the decimated rate
		assertEquals(ints(1, 0, 9, 0), out);
	}

	@Test
	void impulseAfterDecimatorTicksAtTheLocalRate() throws IOException {
		writeRamp(12);
		List<BigInteger> out = run("""
			digraph {
				in [type="datain", file="%IN%"];
				down [type="decimator", ratio=4];
				imp [type="impulse"];
				g [type="gain", value=100];
				s [type="sum"];
				out [type="dataout", file="%OUT%"];
				in -> down;
				down -> s;
				imp -> g;
				g -> s;
				s -> out;
			}
			""", 3);
		assertEquals(ints(101, 5, 9), out);
	}

	/** A source found upstream must still drive its other consumers. */
	@Test
	void sourceFoundUpstreamFeedsItsOtherBranch() throws IOException {
		writeRamp(8);
		load("""
			digraph {
				in [type="datain", file="%IN%"];
				down [type="decimator", ratio=4];
				c [type="constant", value=3];
				g [type="gain", value=2];
				m [type="multiplier"];
				side [type="gain", value=7];
				out [type="dataout", file="%OUT%"];
				out2 [type="dataout", file="%OUT2%"];
				in -> down;
				down -> m;
				c -> g;
				g -> m;
				m -> out;
				g -> side;
				side -> out2;
			}
			""").run(2);
		assertEquals(ints(6, 30), output("out.csv"));
		assertEquals(ints(42, 42), output("out2.csv"));
	}

	// ---------- sources on the fast side of an interpolator ----------

	@Test
	void constantAfterInterpolator() throws IOException {
		List<BigInteger> out = run("""
			digraph {
				imp [type="impulse"];
				up [type="interpolator", ratio=2];
				c [type="constant", value=5];
				g [type="gain", value=1];
				s [type="sum"];
				out [type="dataout", file="%OUT%"];
				imp -> up;
				up -> s;
				c -> g;
				g -> s;
				s -> out;
			}
			""", 2);
		// one iteration is one sample at the slowest rate, so two outputs each
		assertEquals(ints(6, 5, 5, 5), out);
	}

	@Test
	void twoImpulsesEitherSideOfAnInterpolator() throws IOException {
		List<BigInteger> out = run("""
			digraph {
				a [type="impulse"];
				b [type="impulse"];
				up [type="interpolator", ratio=2];
				g [type="gain", value=10];
				s [type="sum"];
				out [type="dataout", file="%OUT%"];
				a -> up;
				up -> s;
				b -> g;
				g -> s;
				s -> out;
			}
			""", 2);
		assertEquals(ints(11, 0, 0, 0), out);
	}

	// ---------- schedule length ----------

	@Test
	void oneIterationIsOneSampleAtTheSlowestRate() throws IOException {
		writeRamp(24);
		load("""
			digraph {
				in [type="datain", file="%IN%"];
				down [type="decimator", ratio=4];
				c [type="constant", value=1];
				g [type="gain", value=1];
				m [type="multiplier"];
				out [type="dataout", file="%OUT%"];
				out2 [type="dataout", file="%OUT2%"];
				in -> down;
				in -> out2;
				down -> m;
				c -> g;
				g -> m;
				m -> out;
			}
			""").run(3);
		assertEquals(ints(1, 5, 9), output("out.csv"));
		assertEquals(12, output("out2.csv").size());
	}

	@Test
	void sameRatioOnBothPathsIsConsistent() throws IOException {
		writeRamp(8);
		List<BigInteger> out = run("""
			digraph {
				in [type="datain", file="%IN%"];
				d1 [type="decimator", ratio=2];
				d2 [type="decimator", ratio=2];
				s [type="sum"];
				out [type="dataout", file="%OUT%"];
				in -> d1;
				in -> d2;
				d1 -> s;
				d2 -> s;
				s -> out;
			}
			""", 3);
		assertEquals(ints(2, 6, 10), out);
	}

	// ---------- sink rates relative to the highest rate ----------

	private static SDFGraph.Ratio ratio(long num, long den) {
		return new SDFGraph.Ratio(num, den);
	}

	@Test
	void ratioIsReducedAndScalesARate() {
		assertEquals(ratio(1, 2), ratio(2, 4));
		assertEquals(2500.0, ratio(1, 4).times(10000));
	}

	@Test
	void decimatedSinkIsAFractionOfTheInputRate() throws IOException {
		writeRamp(8);
		SDFGraph g = load("""
			digraph {
				in [type="datain", file="%IN%"];
				down [type="decimator", ratio=4];
				out [type="dataout", file="%OUT%"];
				out2 [type="dataout", file="%OUT2%"];
				in -> out2;
				in -> down;
				down -> out;
			}
			""");
		assertEquals(Map.of("out", ratio(1, 4), "out2", ratio(1, 1)), g.sinkRates());
	}

	@Test
	void interpolatedSinkIsTheHighestRate() throws IOException {
		SDFGraph g = load("""
			digraph {
				imp [type="impulse"];
				up [type="interpolator", ratio=2];
				out [type="dataout", file="%OUT%"];
				out2 [type="dataout", file="%OUT2%"];
				imp -> out2;
				imp -> up;
				up -> out;
			}
			""");
		assertEquals(Map.of("out", ratio(1, 1), "out2", ratio(1, 2)), g.sinkRates());
	}

	/** Up 3 then down 2: the highest rate is on the edge between them, where no sink sits. */
	@Test
	void highestRateCanBeOnAnInternalEdge() throws IOException {
		SDFGraph g = load("""
			digraph {
				imp [type="impulse"];
				up [type="interpolator", ratio=3];
				down [type="decimator", ratio=2];
				out [type="dataout", file="%OUT%"];
				out2 [type="dataout", file="%OUT2%"];
				imp -> out2;
				imp -> up;
				up -> down;
				down -> out;
			}
			""");
		assertEquals(Map.of("out", ratio(1, 2), "out2", ratio(1, 3)), g.sinkRates());
	}

	/** A constant on the slow side takes that rate, so it doesn't count as a faster edge. */
	@Test
	void sourceOnTheSlowSideDoesNotRaiseTheHighestRate() throws IOException {
		writeRamp(8);
		SDFGraph g = load("""
			digraph {
				in [type="datain", file="%IN%"];
				down [type="decimator", ratio=4];
				c [type="constant", value=3];
				m [type="multiplier"];
				out [type="dataout", file="%OUT%"];
				in -> down;
				down -> m;
				c -> m;
				m -> out;
			}
			""");
		assertEquals(Map.of("out", ratio(1, 4)), g.sinkRates());
	}

	@Test
	void eachConnectedComponentHasItsOwnHighestRate() throws IOException {
		SDFGraph g = load("""
			digraph {
				a [type="impulse"];
				down [type="decimator", ratio=2];
				out [type="dataout", file="%OUT%"];
				a -> down;
				down -> out;

				b [type="impulse"];
				up [type="interpolator", ratio=4];
				down2 [type="decimator", ratio=3];
				out2 [type="dataout", file="%OUT2%"];
				b -> up;
				up -> down2;
				down2 -> out2;
			}
			""");
		assertEquals(Map.of("out", ratio(1, 2), "out2", ratio(1, 3)), g.sinkRates());
	}

	// ---------- graphs that must still be rejected ----------

	@Test
	void onePathDecimatedAndOneNotThrows() {
		assertThrows(SDFGraph.InconsistentSampleRateException.class, () -> load("""
			digraph {
				in [type="impulse"];
				down [type="decimator", ratio=2];
				s [type="sum"];
				out [type="dataout", file="%OUT%"];
				in -> down;
				down -> s;
				in -> s;
				s -> out;
			}
			"""));
	}

	@Test
	void differentRatiosOnTwoPathsThrows() {
		assertThrows(SDFGraph.InconsistentSampleRateException.class, () -> load("""
			digraph {
				in [type="impulse"];
				d1 [type="decimator", ratio=2];
				d2 [type="decimator", ratio=3];
				s [type="sum"];
				out [type="dataout", file="%OUT%"];
				in -> d1;
				in -> d2;
				d1 -> s;
				d2 -> s;
				s -> out;
			}
			"""));
	}

	@Test
	void constantFeedingTwoDifferentRatesThrows() {
		assertThrows(SDFGraph.InconsistentSampleRateException.class, () -> load("""
			digraph {
				in [type="impulse"];
				down [type="decimator", ratio=4];
				c [type="constant", value=3];
				g [type="gain", value=1];
				fast [type="multiplier"];
				slow [type="multiplier"];
				out [type="dataout", file="%OUT%"];
				out2 [type="dataout", file="%OUT2%"];
				in -> fast;
				in -> down;
				down -> slow;
				c -> g;
				g -> fast;
				g -> slow;
				fast -> out;
				slow -> out2;
			}
			"""));
	}
}
