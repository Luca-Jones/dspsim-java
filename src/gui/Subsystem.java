package gui;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A sub-diagram is an ordinary .dsg file whose inport/outport blocks are its
 * ports and whose macros are its parameters. A SUB block links one by path
 * through its "src" param; definitions are cached per file and reloaded when
 * the file changes, so a saved edit shows up in every diagram using it.
 */
public class Subsystem {

	/** ins/outs are the port blocks in port order (by index, then name). */
	public record Def(File file, Diagram d, List<Block> ins, List<Block> outs) {}

	private record Cached(long mtime, Def def) {}

	private static final Map<File, Cached> cache = new HashMap<>();

	/** Relative paths resolve against the parent diagram's directory first,
	 *  then the working directory (where the shipped blocks/ library lives). */
	public static File resolve(String src, File parentDir) {
		File f = new File(src);
		if (!f.isAbsolute() && parentDir != null) {
			File local = new File(parentDir, src);
			if (local.exists())
				return local;
		}
		return f;
	}

	public static Def load(String src, File parentDir) throws IOException {
		if (src == null || src.isBlank())
			throw new IOException("no src file set");
		File f = resolve(src.trim(), parentDir).getCanonicalFile();
		if (!f.isFile())
			throw new IOException(src + " not found");
		Cached c = cache.get(f);
		if (c != null && c.mtime == f.lastModified())
			return c.def;
		Diagram d = Diagram.load(f);
		Def def = new Def(f, d, ports(d, BlockType.INPORT), ports(d, BlockType.OUTPORT));
		cache.put(f, new Cached(f.lastModified(), def));
		return def;
	}

	/** Null instead of an exception, for painting. */
	public static Def tryLoad(Block b, File parentDir) {
		try {
			return load(b.params.get("src"), parentDir);
		} catch (IOException e) {
			return null;
		}
	}

	static List<Block> ports(Diagram d, BlockType type) {
		return d.blocks.stream()
				.filter(b -> b.type == type)
				.sorted(Comparator.comparingInt(Subsystem::index).thenComparing(b -> b.name))
				.toList();
	}

	private static int index(Block b) {
		try {
			return Integer.parseInt(b.params.getOrDefault("index", "").trim());
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}
}
