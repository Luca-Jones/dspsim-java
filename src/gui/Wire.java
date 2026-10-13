package gui;

/**
 * A directed connection between two blocks. The engine has no port identity
 * (arity is pure edge count and sum/multiplier inputs are unordered), so
 * basic blocks ignore the port numbers and their input slots are assigned
 * visually by wire order. Only sub-diagram blocks have numbered ports.
 */
public class Wire {

	public final Block src, dst;
	/** Zero-based port on a sub-diagram block; always 0 elsewhere. */
	public final int srcPort, dstPort;

	public Wire(Block src, Block dst) {
		this(src, 0, dst, 0);
	}

	public Wire(Block src, int srcPort, Block dst, int dstPort) {
		this.src = src;
		this.srcPort = srcPort;
		this.dst = dst;
		this.dstPort = dstPort;
	}
}
