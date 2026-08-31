package gui;

import java.util.LinkedHashMap;

/** A placed block: position, orientation, unique identifier-safe name, and
 *  parameters. */
public class Block {

	public static final int W = 96;
	public static final int H = 64;

	/**
	 * The side the output faces; inputs sit on the opposite side. Because
	 * inputs are unordered and the glyph stays upright, this one value is the
	 * block's whole orientation — flips reduce to direction swaps.
	 */
	public enum Dir {
		E, S, W, N;

		Dir cw() { return values()[(ordinal() + 1) % 4]; }
		Dir ccw() { return values()[(ordinal() + 3) % 4]; }
		Dir opposite() { return values()[(ordinal() + 2) % 4]; }
		Dir flipH() { return this == E ? W : this == W ? E : this; }
		Dir flipV() { return this == N ? S : this == S ? N : this; }

		int dx() { return this == E ? 1 : this == W ? -1 : 0; }
		int dy() { return this == S ? 1 : this == N ? -1 : 0; }
	}

	public final int id;
	public final BlockType type;
	public int x, y;
	public Dir dir = Dir.E;
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
