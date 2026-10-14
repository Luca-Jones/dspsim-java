package sim;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * An edge's sample FIFO. A bus of width w holds w-bit two's complement
 * values: whatever a node computes (in full precision) is wrapped to the low
 * w bits on the way in, like slicing [w-1:0] off a wider signal. Values that
 * already fit pass unchanged, so a wider bus sign-extends for free.
 */
public class Bus {

	/** Width meaning "no limit": samples stay full-precision BigIntegers. */
	public static final int UNBOUNDED = 0;

	public final int width;
	private final Queue<BigInteger> samples = new ArrayDeque<>();

	public Bus(int width) {
		if (width < 0)
			throw new IllegalArgumentException("width must be non-negative, was " + width);
		this.width = width;
	}

	public void push(BigInteger v) {
		samples.add(width == UNBOUNDED ? v : wrap(v, width));
	}

	public BigInteger poll() {
		return samples.poll();
	}

	public void clear() {
		samples.clear();
	}

	/** The low w bits of v, read as a w-bit two's complement number. */
	public static BigInteger wrap(BigInteger v, int w) {
		BigInteger mod = BigInteger.ONE.shiftLeft(w);
		BigInteger low = v.and(mod.subtract(BigInteger.ONE));
		return low.testBit(w - 1) ? low.subtract(mod) : low;
	}
}
