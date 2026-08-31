package node;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Comb filter with transfer function 1-z^-L: y[n] = x[n] - x[n-L]. */
public class CombNode implements Node {

	private final int length;
	private final Deque<BigInteger> memory = new ArrayDeque<>();

	public CombNode() {
		this(1);
	}

	public CombNode(int length) {
		if (length < 1)
			throw new IllegalArgumentException("length must be no less than 1, was " + length);
		this.length = length;
		reset();
	}

	@Override
	public BigInteger evaluate(List<BigInteger> inputs) {
		if (inputs.size() != 1)
			throw new IllegalArgumentException();
		BigInteger x = inputs.getFirst();
		memory.add(x);
		return x.subtract(memory.poll());
	}

	@Override
	public void reset() {
		memory.clear();
		for (int i = 0; i < length; i++)
			memory.add(BigInteger.ZERO);
	}

	@Override
	public void checkWiring(int in, int out) throws InvalidWiringException {
		if (in != 1)
			throw new InvalidWiringException("should take exactly 1 input, takes " + in);
		if (out < 1)
			throw new InvalidWiringException("should give at least 1 output, gives " + out);
	}
}
