import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import sim.SDFGraph;

/**
 * Runs the shipped example graphs in resources/ and checks their exact
 * output. These graphs print to stdout, which is captured per test.
 * Tests are skipped when not run from the repo root (as `make test` does).
 */
public class ResourceGraphsTest {

	private List<BigInteger> runResource(String name, int iterations) throws IOException {
		Path file = Path.of("resources", name);
		assumeTrue(Files.exists(file), "run from repo root to test resource graphs");
		PrintStream original = System.out;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setOut(new PrintStream(captured, true));
		try {
			// DataOut grabs System.out at construction, so redirect before load
			SDFGraph.loadFromFile(file.toString()).run(iterations);
		} finally {
			System.setOut(original);
		}
		return captured.toString().lines()
			.map(s -> s.replace(",", "").trim())
			.filter(s -> !s.isEmpty())
			.map(BigInteger::new)
			.toList();
	}

	@Test
	void zDotIsARunningSum() throws IOException {
		assertEquals(List.of(BigInteger.valueOf(1), BigInteger.valueOf(2), BigInteger.valueOf(3), BigInteger.valueOf(4), BigInteger.valueOf(5)), runResource("z.dot", 5));
	}

	@Test
	void accDotIntegratesImpulseThroughGain() throws IOException {
		assertEquals(List.of(BigInteger.valueOf(-2), BigInteger.valueOf(-2), BigInteger.valueOf(-2), BigInteger.valueOf(-2)), runResource("acc.dot", 4));
	}

	@Test
	void chainDotDelaysConstantByThree() throws IOException {
		assertEquals(List.of(BigInteger.valueOf(0), BigInteger.valueOf(0), BigInteger.valueOf(0), BigInteger.valueOf(1), BigInteger.valueOf(1)), runResource("chain.dot", 5));
	}

	@Test
	void firDotImpulseResponseIsItsTaps() throws IOException {
		assertEquals(List.of(BigInteger.valueOf(2), BigInteger.valueOf(4), BigInteger.valueOf(6), BigInteger.valueOf(8), BigInteger.valueOf(0), BigInteger.valueOf(0)), runResource("fir.dot", 6));
	}

	@Test
	void multDotMultipliesConstants() throws IOException {
		assertEquals(List.of(BigInteger.valueOf(12), BigInteger.valueOf(12), BigInteger.valueOf(12)), runResource("mult.dot", 3));
	}

	@Test
	void interpDotZeroStuffsByThree() throws IOException {
		assertEquals(List.of(BigInteger.valueOf(1), BigInteger.valueOf(0), BigInteger.valueOf(0), BigInteger.valueOf(0), BigInteger.valueOf(0), BigInteger.valueOf(0)), runResource("interp.dot", 2));
	}

	@Test
	void multirateDotUpFourDownTwo() throws IOException {
		assertEquals(List.of(BigInteger.valueOf(1), BigInteger.valueOf(0), BigInteger.valueOf(0), BigInteger.valueOf(0)), runResource("multirate.dot", 2));
	}

	@Test
	void sine1DotMatchesItsDocumentedOutput() throws IOException {
		assertEquals(List.of(BigInteger.valueOf(0), BigInteger.valueOf(10), BigInteger.valueOf(0), BigInteger.valueOf(-10), BigInteger.valueOf(0), BigInteger.valueOf(10), BigInteger.valueOf(0), BigInteger.valueOf(-10)), runResource("sine1.dot", 8));
	}

	@Test
	void sine2DotMatchesItsDocumentedOutput() throws IOException {
		assertEquals(List.of(BigInteger.valueOf(100), BigInteger.valueOf(71), BigInteger.valueOf(0), BigInteger.valueOf(-71), BigInteger.valueOf(-100), BigInteger.valueOf(-71), BigInteger.valueOf(0), BigInteger.valueOf(71)), runResource("sine2.dot", 8));
	}

	@Test
	void sine3DotMatchesItsDocumentedOutput() throws IOException {
		assertEquals(List.of(BigInteger.valueOf(0), BigInteger.valueOf(9), BigInteger.valueOf(-9), BigInteger.valueOf(0), BigInteger.valueOf(9), BigInteger.valueOf(-9)), runResource("sine3.dot", 6));
	}

	@Test
	void cicDotImpulseResponse() throws IOException {
		// 7 integrators (each z^-1/(1-z^-1)), decimate by 2, 7 combs (1-z^-1):
		// H(z) = z^-7 (1+z^-1)^7 at input rate, sampled at even indices after
		// decimation -> binomial taps C(7,1), C(7,3), C(7,5), C(7,7)
		assertEquals(List.of(BigInteger.valueOf(0), BigInteger.valueOf(0), BigInteger.valueOf(0), BigInteger.valueOf(0), BigInteger.valueOf(7), BigInteger.valueOf(35), BigInteger.valueOf(21), BigInteger.valueOf(1)), runResource("cic.dot", 8));
	}
}
