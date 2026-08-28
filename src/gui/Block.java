package gui;

import java.util.LinkedHashMap;

/** A placed block: position, unique identifier-safe name, and parameters. */
public class Block {

	public static final int W = 96;
	public static final int H = 64;

	public final int id;
	public final BlockType type;
	public int x, y;
	/** Doubles as the .dot node name, so it must match [A-Za-z_][A-Za-z0-9_]*. */
	public String name;
	public final LinkedHashMap<String, String> params = new LinkedHashMap<>();

	public Block(int id, BlockType type, int x, int y, String name) {
		this.id = id;
		this.type = type;
		this.x = x;
		this.y = y;
		this.name = name;
		for (BlockType.Param p : type.params)
			params.put(p.key(), p.defaultValue());
	}

	public boolean contains(double mx, double my) {
		return mx >= x && mx <= x + W && my >= y && my <= y + H;
	}
}
