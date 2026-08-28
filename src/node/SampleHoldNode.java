package node;

import java.math.BigInteger;
import java.util.List;

public class SampleHoldNode implements Node {

	private final int ratio;

	public SampleHoldNode(int ratio) {
		this.ratio = ratio;
	}

	@Override
	public BigInteger evaluate(List<BigInteger> inputs) {
		if (inputs.size() != 1)
			throw new IllegalArgumentException();
		return inputs.getFirst();
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

