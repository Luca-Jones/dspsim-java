import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Runs the shipped example graphs in resources/ and checks their exact
 * output. These graphs print to stdout, which is captured per test.
 * Tests are skipped when not run from the repo root (as `make test` does).
 */
public class ResourceGraphsTest {

	private List<Integer> runResource(String name, int iterations) throws IOException {
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
			.map(Integer::parseInt)
			.toList();
	}

	@Test
	void zDotIsARunningSum() throws IOException {
		assertEquals(List.of(1, 2, 3, 4, 5), runResource("z.dot", 5));
	}

	@Test
	void accDotIntegratesImpulseThroughGain() throws IOException {
		assertEquals(List.of(-2, -2, -2, -2), runResource("acc.dot", 4));
	}

	@Test
	void chainDotDelaysConstantByThree() throws IOException {
		assertEquals(List.of(0, 0, 0, 1, 1), runResource("chain.dot", 5));
	}

	@Test
	void firDotImpulseResponseIsItsTaps() throws IOException {
		assertEquals(List.of(2, 4, 6, 8, 0, 0), runResource("fir.dot", 6));
	}

	@Test
	void multDotMultipliesConstants() throws IOException {
		assertEquals(List.of(12, 12, 12), runResource("mult.dot", 3));
	}

	@Test
	void interpDotZeroStuffsByThree() throws IOException {
		assertEquals(List.of(1, 0, 0, 0, 0, 0), runResource("interp.dot", 2));
	}

	@Test
	void multirateDotUpFourDownTwo() throws IOException {
		assertEquals(List.of(1, 0, 0, 0), runResource("multirate.dot", 2));
	}

	@Test
	void sine1DotMatchesItsDocumentedOutput() throws IOException {
		assertEquals(List.of(0, 10, 0, -10, 0, 10, 0, -10), runResource("sine1.dot", 8));
	}

	@Test
	void sine2DotMatchesItsDocumentedOutput() throws IOException {
		assertEquals(List.of(100, 71, 0, -71, -100, -71, 0, 71), runResource("sine2.dot", 8));
	}

	@Test
	void sine3DotMatchesItsDocumentedOutput() throws IOException {
		assertEquals(List.of(0, 9, -9, 0, 9, -9), runResource("sine3.dot", 6));
	}

	@Test
	void cicDotImpulseResponse() throws IOException {
		// 7 integrators (each z^-1/(1-z^-1)), decimate by 2, 7 combs (1-z^-1):
		// H(z) = z^-7 (1+z^-1)^7 at input rate, sampled at even indices after
		// decimation -> binomial taps C(7,1), C(7,3), C(7,5), C(7,7)
		assertEquals(List.of(0, 0, 0, 0, 7, 35, 21, 1), runResource("cic.dot", 8));
	}
}
