import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import gui.Block;
import gui.BlockType;
import gui.Diagram;
import gui.DotWriter;
import gui.Flattener;
import sim.SDFGraph;

public class FlattenerTest {

	@TempDir
	Path tmp;

	private static List<BigInteger> readCsv(Path p) throws IOException {
		List<BigInteger> out = new ArrayList<>();
		for (String line : Files.readAllLines(p))
			if (!line.isBlank())
				out.add(new BigInteger(line.replace(",", "").trim()));
		return out;
	}

	/** Flattens and runs a test/graphs diagram (which links the shipped
	 *  blocks/), with its dataouts redirected into tmp. */
	private void runGraph(String name, int iterations) throws IOException {
		Path dsg = Path.of("test", "graphs", name);
		Diagram flat = Flattener.flatten(Diagram.load(dsg.toFile()), dsg.toFile().getParentFile());
		assertTrue(DotWriter.validate(flat).isEmpty(), DotWriter.validate(flat).toString());
		for (Block b : flat.blocks) {
			assertNotEquals(BlockType.SUB, b.type);
			if (b.type == BlockType.DATAOUT)
				b.params.put("file", tmp.resolve(b.params.get("file")).toString());
		}
		Path dot = tmp.resolve("flat.dot");
		DotWriter.write(flat, dot);
		SDFGraph.loadFromFile(dot.toString()).run(iterations);
	}

	@Test
	void countingMuxAndHoldingDemuxKeepTheRate() throws IOException {
		// x_k[n] = (k+1)*n, so each muxed sample shows which input and which
		// sample of it was selected
		runGraph("muxdemux.dsg", 8);
		List<BigInteger> m = readCsv(tmp.resolve("m.csv"));
		assertEquals(32, m.size());
		for (int n = 0; n < 32; n++)
			assertEquals(BigInteger.valueOf((n % 4 + 1) * n), m.get(n), "y[n] = x_{n mod 4}[n] at n=" + n);
		for (int k = 0; k < 4; k++) {
			List<BigInteger> y = readCsv(tmp.resolve("y" + k + ".csv"));
			assertEquals(32, y.size(), "demux output keeps the input rate");
			for (int n = 0; n < 32; n++) {
				// latched on clock k of the previous frame, held for a frame
				long want = n < 4 ? 0 : m.get(4 * (n / 4 - 1) + k).longValue();
				assertEquals(BigInteger.valueOf(want), y.get(n), "y" + k + "[" + n + "]");
			}
		}
	}

	@Test
	void interleaveThenDeinterleaveRecoversEachStream() throws IOException {
		runGraph("interleave.dsg", 8);
		List<BigInteger> m = readCsv(tmp.resolve("m.csv"));
		assertEquals(32, m.size());
		for (int i = 0; i < m.size(); i++)
			assertEquals(BigInteger.valueOf(i % 4 + 1), m.get(i), "muxed sample " + i);
		for (int k = 0; k < 4; k++) {
			List<BigInteger> y = readCsv(tmp.resolve("y" + k + ".csv"));
			assertEquals(8, y.size());
			assertEquals(BigInteger.ZERO, y.get(0), "one frame of latency");
			for (int n = 1; n < 8; n++)
				assertEquals(BigInteger.valueOf(k + 1), y.get(n), "y" + k + "[" + n + "]");
		}
	}

	/** x -> gain(G) -> y, with MACRO G=2. */
	private File gainSub() throws IOException {
		Diagram d = new Diagram();
		d.macros.put("G", "2");
		Block in = d.addBlock(BlockType.INPORT, 0, 0);
		Block g = d.addBlock(BlockType.GAIN, 100, 0);
		g.params.put("value", "G");
		Block out = d.addBlock(BlockType.OUTPORT, 200, 0);
		d.connect(in, g);
		d.connect(g, out);
		File f = tmp.resolve("g.dsg").toFile();
		d.save(f);
		return f;
	}

	private Diagram parentOf(String src, String g) {
		Diagram d = new Diagram();
		Block c = d.addBlock(BlockType.CONSTANT, 0, 0);
		Block s = d.addBlock(BlockType.SUB, 100, 0);
		s.params.put("src", src);
		if (g != null)
			s.params.put("G", g);
		Block o = d.addBlock(BlockType.DATAOUT, 200, 0);
		d.connect(c, s);
		d.connect(s, o);
		return d;
	}

	@Test
	void subParametersDefaultOverrideAndPassThroughParentMacros() throws IOException {
		gainSub();
		File dir = tmp.toFile();
		String byDefault = DotWriter.toDot(Flattener.flatten(parentOf("g.dsg", null), dir));
		assertTrue(byDefault.contains("sub1_gain1 [type=\"gain\", value=2]"), byDefault);
		assertFalse(byDefault.contains("#define G"), "sub macros are substituted, not exported");

		String overridden = DotWriter.toDot(Flattener.flatten(parentOf("g.dsg", "5"), dir));
		assertTrue(overridden.contains("value=5"), overridden);
		assertTrue(overridden.contains("constant1 -> sub1_gain1;"), overridden);
		assertTrue(overridden.contains("sub1_gain1 -> dataout1;"), overridden);

		Diagram parent = parentOf("g.dsg", "K");
		parent.macros.put("K", "7");
		String viaMacro = DotWriter.toDot(Flattener.flatten(parent, dir));
		assertTrue(viaMacro.contains("#define K 7"), viaMacro);
		assertTrue(viaMacro.contains("value=K"), viaMacro);
	}

	@Test
	void rejectsSelfInclusionPassThroughAndPortsAtTopLevel() throws IOException {
		File dir = tmp.toFile();
		Diagram self = new Diagram();
		Block in = self.addBlock(BlockType.INPORT, 0, 0);
		Block s = self.addBlock(BlockType.SUB, 100, 0);
		s.params.put("src", "self.dsg");
		Block out = self.addBlock(BlockType.OUTPORT, 200, 0);
		self.connect(in, s);
		self.connect(s, out);
		self.save(tmp.resolve("self.dsg").toFile());
		var e = assertThrows(IllegalArgumentException.class,
				() -> Flattener.flatten(parentOf("self.dsg", null), dir));
		assertTrue(e.getMessage().contains("contains itself"), e.getMessage());

		Diagram wire = new Diagram();
		wire.connect(wire.addBlock(BlockType.INPORT, 0, 0), wire.addBlock(BlockType.OUTPORT, 100, 0));
		wire.save(tmp.resolve("wire.dsg").toFile());
		e = assertThrows(IllegalArgumentException.class,
				() -> Flattener.flatten(parentOf("wire.dsg", null), dir));
		assertTrue(e.getMessage().contains("straight"), e.getMessage());

		assertThrows(IllegalArgumentException.class, () -> Flattener.flatten(wire, dir));
		e = assertThrows(IllegalArgumentException.class,
				() -> Flattener.flatten(parentOf("missing.dsg", null), dir));
		assertTrue(e.getMessage().contains("not found"), e.getMessage());
	}

	@Test
	void wirePortsSurviveSaveAndLoad() {
		Diagram d = new Diagram();
		Block c = d.addBlock(BlockType.CONSTANT, 0, 0);
		Block s = d.addBlock(BlockType.SUB, 100, 0);
		Block o = d.addBlock(BlockType.DATAOUT, 200, 0);
		assertNull(d.connect(c, 0, s, 2));
		assertNull(d.connect(c, 0, s, 3));
		assertNull(d.connect(s, 1, o, 0));
		Block c2 = d.addBlock(BlockType.CONSTANT, 0, 100);
		assertNull(d.connect(c2, 0, s, 2), "rewiring a port replaces its wire");
		Diagram back = Diagram.fromSnapshot(d.snapshot());
		assertEquals(3, back.wires.size());
		assertEquals(List.of(3, 0, 2), back.wires.stream().map(w -> w.dstPort).toList());
		assertEquals(List.of(0, 1, 0), back.wires.stream().map(w -> w.srcPort).toList());
	}

	/** x -[W]-> gain -[12]-> y, with MACRO W=10. */
	private void widthSub() throws IOException {
		Diagram d = new Diagram();
		d.macros.put("W", "10");
		Block in = d.addBlock(BlockType.INPORT, 0, 0);
		Block g = d.addBlock(BlockType.GAIN, 100, 0);
		g.params.put("value", "1");
		Block out = d.addBlock(BlockType.OUTPORT, 200, 0);
		d.connect(in, g);
		d.wires.getLast().width = "W";
		d.connect(g, out);
		d.wires.getLast().width = "12";
		d.save(tmp.resolve("w.dsg").toFile());
	}

	private Diagram widthParent(String w) {
		Diagram d = new Diagram();
		Block c = d.addBlock(BlockType.CONSTANT, 0, 0);
		c.params.put("value", "1");
		Block s = d.addBlock(BlockType.SUB, 100, 0);
		s.params.put("src", "w.dsg");
		if (w != null)
			s.params.put("W", w);
		Block o = d.addBlock(BlockType.DATAOUT, 200, 0);
		d.connect(c, s);
		d.wires.getLast().width = "8";
		d.connect(s, o);
		d.wires.getLast().width = "16";
		return d;
	}

	@Test
	void narrowerWidthWinsAcrossAPort() throws IOException {
		widthSub();
		File dir = tmp.toFile();
		String dot = DotWriter.toDot(Flattener.flatten(widthParent(null), dir));
		assertTrue(dot.contains("constant1 -> sub1_gain1 [w=8];"), dot);
		assertTrue(dot.contains("sub1_gain1 -> dataout1 [w=12];"), dot);

		dot = DotWriter.toDot(Flattener.flatten(widthParent("6"), dir));
		assertTrue(dot.contains("constant1 -> sub1_gain1 [w=6];"), "sub macro bound per instance: " + dot);

		Diagram parent = widthParent("K");
		parent.macros.put("K", "4");
		Diagram flat = Flattener.flatten(parent, dir);
		assertTrue(DotWriter.validate(flat).isEmpty(), DotWriter.validate(flat).toString());
		dot = DotWriter.toDot(flat);
		assertTrue(dot.contains("constant1 -> sub1_gain1 [w=K];"), "parent macro kept by name: " + dot);
	}

	@Test
	void invalidWireWidthIsReported() {
		Diagram d = new Diagram();
		Block c = d.addBlock(BlockType.CONSTANT, 0, 0);
		c.params.put("value", "1");
		d.connect(c, d.addBlock(BlockType.DATAOUT, 100, 0));
		d.wires.getLast().width = "NOPE";
		assertEquals(1, DotWriter.validate(d).size(), DotWriter.validate(d).toString());
		d.macros.put("NOPE", "3");
		assertTrue(DotWriter.validate(d).isEmpty(), DotWriter.validate(d).toString());
	}

	@Test
	void wireWidthSurvivesSaveAndLoad() {
		Diagram d = new Diagram();
		Block c = d.addBlock(BlockType.CONSTANT, 0, 0);
		Block s = d.addBlock(BlockType.SUB, 100, 0);
		Block o = d.addBlock(BlockType.DATAOUT, 200, 0);
		d.connect(c, 0, s, 1);
		d.wires.getLast().width = "BITS";
		d.connect(s, o);
		d.wires.getLast().width = "8";
		d.connect(c, 0, s, 0);
		Diagram back = Diagram.fromSnapshot(d.snapshot());
		assertEquals(List.of("BITS", "8", ""), back.wires.stream().map(w -> w.width).toList());
		assertEquals(1, back.wires.get(0).dstPort);
	}
}
