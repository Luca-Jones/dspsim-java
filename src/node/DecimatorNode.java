package node;

import java.math.BigInteger;
import java.util.List;

public class DecimatorNode implements Node {

	private final int ratio;

	public DecimatorNode(int ratio) {
		if (ratio < 1)
			throw new IllegalArgumentException("ratio must no less than 1, was " + ratio);
		this.ratio = ratio;
	}

	@Override
	public BigInteger evaluate(List<BigInteger> inputs) {
		if (inputs.size() < ratio) // sample n and n-1
			throw new IllegalArgumentException();
		return inputs.getFirst();
	}

	@Override
	public int inputRate() {
		return ratio;
	}

	@Override
	public void checkWiring(int in, int out) throws InvalidWiringException {
		if (in != 1)
			throw new InvalidWiringException("should take exactly 1 input, takes " + in);
		if (out < 1)
			throw new InvalidWiringException("should give at least one output, gives " + out);
	}


}

