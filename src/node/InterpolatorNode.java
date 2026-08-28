package node;

import java.math.BigInteger;
import java.util.List;

public class InterpolatorNode implements Node {

	private final int ratio;
	private int phase;

	public InterpolatorNode(int ratio) {
		this.ratio = ratio;
		this.phase = 0;
	}

	@Override
	public BigInteger evaluate(List<BigInteger> inputs) {
		if (inputs.size() != 1)
			throw new IllegalArgumentException();
		BigInteger out = (phase == 0) ? inputs.getFirst() : BigInteger.ZERO;
		phase = (phase + 1) % ratio;
		return out;
	}

	@Override
	public void reset() {
		phase = 0;
	}

	@Override
	public int outputRate() {
		return ratio;
	}

	@Override
	public void checkWiring(int in, int out) throws InvalidWiringException {
		if (in != 1)
			throw new InvalidWiringException("should take exactly 1 input, takes " + in);
		if (out < 1)
			throw new InvalidWiringException("should give at least 1 output, gives " + out);
	}
}

