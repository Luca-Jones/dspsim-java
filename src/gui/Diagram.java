package gui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The drawing: blocks plus wires, with the engine's wiring rules enforced at
 * connect time, and a line-based save format (extension .dsg):
 *
 *   DSPSIM 1
 *   BLOCK <id> <dotType> <x> <y> <name>
 *   P <key>=<value>          (attaches to the preceding BLOCK)
 *   WIRE <srcId> <dstId>
 */
public class Diagram {

	public final List<Block> blocks = new ArrayList<>();
	public final List<Wire> wires = new ArrayList<>();
	public boolean dirty = false;
	private int nextId = 1;

	public Block addBlock(BlockType type, int x, int y) {
		Block b = new Block(nextId++, type, x, y, uniqueName(type));
		blocks.add(b);
		dirty = true;
		return b;
	}

	private String uniqueName(BlockType type) {
		for (int i = 1;; i++) {
			String name = type.dotType + i;
			if (findByName(name) == null)
				return name;
		}
	}

	public Block findByName(String name) {
		for (Block b : blocks)
			if (b.name.equals(name))
				return b;
		return null;
	}

	public void remove(Block b) {
		blocks.remove(b);
		wires.removeIf(w -> w.src == b || w.dst == b);
		dirty = true;
	}

	public void remove(Wire w) {
		wires.remove(w);
		dirty = true;
	}

	/**
	 * Wire src into dst, honoring the engine's rules: no self loops, no
	 * duplicate (src, dst) edges, sinks have no output, sources no input.
	 * When the destination is already at capacity the oldest driver wire is
	 * replaced. Returns an error message, or null on success.
	 */
	public String connect(Block src, Block dst) {
		if (src == dst)
			return "cannot wire a block to itself";
		if (src.type.isSink())
			return src.name + " has no output";
		if (dst.type.inArity == BlockType.InArity.NONE)
			return dst.name + " has no inputs";
		for (Wire w : wires)
			if (w.src == src && w.dst == dst)
				return src.name + " is already wired to " + dst.name;
		int capacity = switch (dst.type.inArity) {
			case ONE -> 1;
			case TWO -> 2;
			default -> Integer.MAX_VALUE;
		};
		List<Wire> in = wiresInto(dst);
		if (in.size() >= capacity)
			wires.remove(in.get(0));
		wires.add(new Wire(src, dst));
		dirty = true;
		return null;
	}

	public List<Wire> wiresInto(Block b) {
		List<Wire> in = new ArrayList<>();
		for (Wire w : wires)
			if (w.dst == b)
				in.add(w);
		return in;
	}

	public List<Wire> wiresOutOf(Block b) {
		List<Wire> out = new ArrayList<>();
		for (Wire w : wires)
			if (w.src == b)
				out.add(w);
		return out;
	}

	// ---- persistence ---------------------------------------------------

	public void save(File f) throws IOException {
		try (PrintWriter pw = new PrintWriter(f)) {
			pw.println("DSPSIM 1");
			for (Block b : blocks) {
				pw.println("BLOCK " + b.id + " " + b.type.dotType + " "
						+ b.x + " " + b.y + " " + b.name);
				for (Map.Entry<String, String> e : b.params.entrySet())
					pw.println("P " + e.getKey() + "=" + e.getValue());
			}
			for (Wire w : wires)
				pw.println("WIRE " + w.src.id + " " + w.dst.id);
		}
		dirty = false;
	}

	public static Diagram load(File f) throws IOException {
		Diagram d = new Diagram();
		Map<Integer, Block> byId = new HashMap<>();
		Block cur = null;
		int maxId = 0;
		try (BufferedReader br = new BufferedReader(new FileReader(f))) {
			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("BLOCK ")) {
					String[] t = line.split("\\s+");
					BlockType type = BlockType.fromDotType(t[2]);
					if (type == null)
						throw new IOException("unknown block type: " + t[2]);
					cur = new Block(Integer.parseInt(t[1]), type,
							Integer.parseInt(t[3]), Integer.parseInt(t[4]), t[5]);
					d.blocks.add(cur);
					byId.put(cur.id, cur);
					maxId = Math.max(maxId, cur.id);
				} else if (line.startsWith("P ") && cur != null) {
					String kv = line.substring(2);
					int eq = kv.indexOf('=');
					if (eq > 0)
						cur.params.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
				} else if (line.startsWith("WIRE ")) {
					String[] t = line.split("\\s+");
					Block src = byId.get(Integer.parseInt(t[1]));
					Block dst = byId.get(Integer.parseInt(t[2]));
					if (src != null && dst != null)
						d.wires.add(new Wire(src, dst));
				}
				// unknown prefixes are ignored for forward compatibility
			}
		}
		d.nextId = maxId + 1;
		d.dirty = false;
		return d;
	}
}
