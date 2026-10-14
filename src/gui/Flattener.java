package gui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles sub-diagrams away. Returns a diagram of basic blocks only, ready
 * for DotWriter, so the engine never knows sub-diagrams exist.
 *
 * Each SUB block is replaced by a copy of its contents, named
 * "<instance>_<block>" (nesting stacks the prefixes). Its port blocks emit no
 * node: a wire into input port i is reconnected to whatever inport i feeds,
 * and a wire out of output port j starts from whatever drives outport j.
 * The sub-diagram's macros are its parameters: each one takes the
 * instance's value when set, else the macro's own, and is substituted into
 * the copied param values, so sub-diagram macros never reach the .dot file.
 * Wire widths are bound the same way. Where a port joins two wires into one,
 * the flattened wire takes the narrower width: slicing a slice keeps the
 * smaller one.
 */
public class Flattener {

	/** A flattened block at the far end of a port, with the narrowest bus
	 *  width ("" = unbounded) along the way there. */
	private record Tap(Block block, String width) {}

	/** What a diagram's ports connect to once flattened: per inport, the
	 *  blocks it feeds; per outport, the block driving it (null if none). */
	private record Ports(List<List<Tap>> inTargets, List<Tap> outDrivers) {}

	/** Throws IllegalArgumentException with a user-facing message. */
	public static Diagram flatten(Diagram top, File baseDir) {
		for (Block b : top.blocks)
			if (b.type == BlockType.INPORT || b.type == BlockType.OUTPORT)
				throw new IllegalArgumentException("this is a sub-diagram (it has ports): "
						+ "place it in another diagram to run it");
		Diagram out = new Diagram();
		out.macros.putAll(top.macros);
		expand(top, baseDir, "", Map.of(), new ArrayDeque<>(), out);
		return out;
	}

	private static Ports expand(Diagram d, File dir, String prefix, Map<String, String> bind,
			Deque<File> stack, Diagram out) {
		Map<Block, Block> flat = new HashMap<>();
		Map<Block, Ports> subs = new HashMap<>();
		// same order as Subsystem.Def, which is what the parent's port numbers index
		List<Block> ins = Subsystem.ports(d, BlockType.INPORT);
		List<Block> outs = Subsystem.ports(d, BlockType.OUTPORT);
		for (Block b : d.blocks) {
			switch (b.type) {
				case INPORT, OUTPORT -> {}
				case SUB -> subs.put(b, expandSub(b, dir, prefix, bind, stack, out));
				default -> {
					String name = prefix + b.name;
					if (out.findByName(name) != null)
						throw new IllegalArgumentException("two blocks flatten to the name " + name
								+ "; rename one");
					Block c = new Block(out.blocks.size() + 1, b.type, b.x, b.y, name);
					c.dir = b.dir;
					b.params.forEach((k, v) -> c.params.put(k, bind.getOrDefault(v.trim(), v)));
					out.blocks.add(c);
					flat.put(b, c);
				}
			}
		}

		Ports ports = new Ports(new ArrayList<>(), new ArrayList<>(Collections.nCopies(outs.size(), null)));
		for (int i = 0; i < ins.size(); i++)
			ports.inTargets.add(new ArrayList<>());
		for (Wire w : d.wires) {
			if (w.src.type == BlockType.INPORT && w.dst.type == BlockType.OUTPORT)
				throw new IllegalArgumentException(prefix + w.src.name + " is wired straight to "
						+ prefix + w.dst.name + "; route it through a block (e.g. a delay of 0)");
			String width = bind.getOrDefault(w.width.trim(), w.width.trim());
			List<Tap> targets = switch (w.dst.type) {
				case OUTPORT -> null;
				case SUB -> port(subs.get(w.dst).inTargets, w.dstPort, prefix + w.dst.name, "input");
				default -> List.of(new Tap(flat.get(w.dst), ""));
			};
			if (w.src.type == BlockType.INPORT) {
				for (Tap t : targets)
					ports.inTargets.get(ins.indexOf(w.src))
							.add(new Tap(t.block, narrower(width, t.width, out.macros)));
				continue;
			}
			Tap src = w.src.type == BlockType.SUB
					? port(subs.get(w.src).outDrivers, w.srcPort, prefix + w.src.name, "output")
					: new Tap(flat.get(w.src), "");
			if (src == null)
				throw new IllegalArgumentException(prefix + w.src.name + " output "
						+ (w.srcPort + 1) + " is not driven inside its sub-diagram");
			width = narrower(src.width, width, out.macros);
			if (targets == null) {
				ports.outDrivers.set(outs.indexOf(w.dst), new Tap(src.block, width));
			} else {
				for (Tap t : targets) {
					Wire c = new Wire(src.block, t.block);
					c.width = narrower(width, t.width, out.macros);
					out.wires.add(c);
				}
			}
		}
		return ports;
	}

	private static Ports expandSub(Block b, File dir, String prefix, Map<String, String> bind,
			Deque<File> stack, Diagram out) {
		String where = prefix + b.name;
		Subsystem.Def def;
		try {
			def = Subsystem.load(b.params.get("src"), dir);
		} catch (IOException e) {
			throw new IllegalArgumentException(where + ": cannot load sub-diagram: " + e.getMessage());
		}
		if (stack.contains(def.file()))
			throw new IllegalArgumentException(where + ": " + def.file().getName() + " contains itself");
		// a sub macro's own value may name an earlier sub macro, an instance
		// value is written in the parent's terms
		Map<String, String> childBind = new LinkedHashMap<>();
		def.d().macros.forEach((k, v) -> {
			String inst = b.params.getOrDefault(k, "").trim();
			childBind.put(k, inst.isEmpty()
					? childBind.getOrDefault(v, v)
					: bind.getOrDefault(inst, inst));
		});
		stack.push(def.file());
		Ports p = expand(def.d(), def.file().getParentFile(), where + "_", childBind, stack, out);
		stack.pop();
		return p;
	}

	/** The narrower of two bus widths, keeping its original spelling (an
	 *  integer or a top-level macro); "" is unbounded. */
	private static String narrower(String a, String b, Map<String, String> macros) {
		if (a.isEmpty() || a.equals(b))
			return b;
		if (b.isEmpty())
			return a;
		return bits(a, macros) <= bits(b, macros) ? a : b;
	}

	private static int bits(String width, Map<String, String> macros) {
		int n = DotWriter.parseWidth(macros.getOrDefault(width, width));
		if (n < 1)
			throw new IllegalArgumentException("bus width \"" + width + "\" must be a positive integer"
					+ " or a macro naming one");
		return n;
	}

	private static <T> T port(List<T> ports, int i, String block, String kind) {
		if (i < 0 || i >= ports.size())
			throw new IllegalArgumentException(block + " has no " + kind + " port " + (i + 1)
					+ " any more; rewire it");
		return ports.get(i);
	}
}
