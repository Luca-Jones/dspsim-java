package node;

import java.math.BigInteger;
import java.util.List;

public class ConstantNode implements Node {

	private final BigInteger value;

	public ConstantNode(BigInteger value) {
		this.value = value;
	}

	@Override
	public BigInteger evaluate(List<BigInteger> inputs) {
		if (inputs == null || inputs.size() != 0)
			throw new IllegalArgumentException();
		return value;
	}

	@Override
	public int inputRate() {
		return 0;
	}

	@Override
	public void checkWiring(int in, int out) throws InvalidWiringException {
		if (in > 0)
			throw new InvalidWiringException("should not take any inputs, takes " + in);
		if (out <= 0)
			throw new InvalidWiringException("should give at least one output, gives " + out);
	}

}

