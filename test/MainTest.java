import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class MainTest {

	private String captureStdout(Runnable r) {
		PrintStream original = System.out;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setOut(new PrintStream(captured, true));
		try {
			r.run();
		} finally {
			System.setOut(original);
		}
		return captured.toString();
	}

	private String captureStderr(Runnable r) {
		PrintStream original = System.err;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setErr(new PrintStream(captured, true));
		try {
			r.run();
		} finally {
			System.setErr(original);
		}
		return captured.toString();
	}

	@Test
	void runsGraphFileForGivenIterations() {
		assumeTrue(Files.exists(Path.of("resources/z.dot")), "run from repo root");
		int[] code = new int[1];
		String out = captureStdout(() -> code[0] = Main.run(new String[] {"resources/z.dot", "3"}));
		assertEquals("1,\n2,\n3,\n", out);
		assertEquals(0, code[0]);
	}

	@Test
	void missingFileReportsErrorAndExitsNonzero() {
		int[] code = new int[1];
		String err = captureStderr(() -> code[0] = Main.run(new String[] {"/no/such/file.dot"}));
		assertEquals(1, code[0]);
		assertTrue(err.contains("/no/such/file.dot"));
	}

	@Test
	void nonNumericIterationsReportsErrorAndExitsNonzero() {
		// iterations are parsed before the file is touched, so no fixture needed
		int[] code = new int[1];
		String err = captureStderr(() -> code[0] = Main.run(new String[] {"any.dot", "lots"}));
		assertEquals(2, code[0]);
		assertTrue(err.contains("lots"));
	}

	@Test
	void tooManyArgsPrintsUsage() {
		int[] code = new int[1];
		String err = captureStderr(() -> code[0] = Main.run(new String[] {"a.dot", "3", "extra"}));
		assertEquals(2, code[0]);
		assertTrue(err.contains("usage"));
	}
}
