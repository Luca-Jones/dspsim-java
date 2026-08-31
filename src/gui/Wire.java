package gui;

/**
 * A directed connection between two blocks. The engine has no port identity
 * (arity is pure edge count and sum/multiplier inputs are unordered), so
 * neither does the model; input slots are assigned visually by wire order.
 */
public class Wire {

	public final Block src, dst;

	public Wire(Block src, Block dst) {
		this.src = src;
		this.dst = dst;
	}
}
