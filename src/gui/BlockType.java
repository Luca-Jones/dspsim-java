package gui;

import java.util.List;

/**
 * Registry of the engine's node types. Everything the palette, the property
 * dialog, and the DotWriter need to know about a type lives here, so adding
 * a node type to the GUI is a one-line change.
 */
public enum BlockType {

	IMPULSE("impulse", "δ[n]", Category.SOURCE, InArity.NONE,
		"unit impulse: 1 then 0 forever"),
	CONSTANT("constant", "K", Category.SOURCE, InArity.NONE,
		"emits value every tick",
		Param.integer("value", true, "1")),
	SINE("sine", "∿", Category.SOURCE, InArity.NONE,
		"integer sine wave",
		Param.integer("amplitude", true, "100"),
		Param.integer("period", true, "16"),
		Param.integer("phase", false, "")),
	DATAIN("datain", "csv→", Category.SOURCE, InArity.NONE,
		"reads samples from a csv file",
		Param.string("file", true, "in.csv")),

	GAIN("gain", "×K", Category.MATH, InArity.ONE,
		"multiplies by value",
		Param.integer("value", true, "2")),
	LSHIFT("lshift", "≪", Category.MATH, InArity.ONE,
		"left shift by value bits",
		Param.integer("value", true, "1")),
	RSHIFT("rshift", "≫", Category.MATH, InArity.ONE,
		"right shift by value bits",
		Param.integer("value", true, "1")),
	SUM("sum", "Σ", Category.MATH, InArity.MANY,
		"adds all inputs (2 or more)"),
	MULTIPLIER("multiplier", "×", Category.MATH, InArity.TWO,
		"multiplies its two inputs"),

	DELAY("delay", "z⁻¹", Category.RATE, InArity.ONE,
		"delays by n samples",
		Param.integer("delay", false, "1")),
	DECIMATOR("decimator", "↓R", Category.RATE, InArity.ONE,
		"keeps 1 of every ratio samples",
		Param.integer("ratio", true, "2")),
	INTERPOLATOR("interpolator", "↑R", Category.RATE, InArity.ONE,
		"zero-stuffs by ratio",
		Param.integer("ratio", true, "2")),
	HOLD("hold", "S/H", Category.RATE, InArity.ONE,
		"repeats each sample ratio times",
		Param.integer("ratio", true, "2")),

	DATAOUT("dataout", "→csv", Category.SINK, InArity.ONE,
		"dumps samples to a csv file (blank = console)",
		Param.string("file", false, "out.csv"));

	public enum Category { SOURCE, MATH, RATE, SINK }

	/** How many wires the engine's checkWiring accepts into this node. */
	public enum InArity { NONE, ONE, TWO, MANY }

	public record Param(String key, boolean isInt, boolean required, String defaultValue) {
		static Param integer(String key, boolean required, String def) {
			return new Param(key, true, required, def);
		}
		static Param string(String key, boolean required, String def) {
			return new Param(key, false, required, def);
		}
	}

	public final String dotType;
	public final String glyph;
	public final Category category;
	public final InArity inArity;
	public final String description;
	public final List<Param> params;

	BlockType(String dotType, String glyph, Category category, InArity inArity,
			String description, Param... params) {
		this.dotType = dotType;
		this.glyph = glyph;
		this.category = category;
		this.inArity = inArity;
		this.description = description;
		this.params = List.of(params);
	}

	public boolean isSource() { return inArity == InArity.NONE; }

	public boolean isSink() { return category == Category.SINK; }

	public static BlockType fromDotType(String s) {
		for (BlockType t : values())
			if (t.dotType.equals(s))
				return t;
		return null;
	}
}
