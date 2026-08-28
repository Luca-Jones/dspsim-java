package node;

import java.math.BigInteger;
import java.util.List;

public class MultiplierNode implements Node {

	public MultiplierNode() {}

	@Override
	public BigInteger evaluate(List<BigInteger> inputs) {
		if (inputs == null || inputs.size() != 2)
			throw new IllegalArgumentException();
		return inputs.get(0).multiply(inputs.get(1));
	}

	@Override
	public void checkWiring(int in, int out) throws InvalidWiringException {
		if (in != 2)
			throw new InvalidWiringException("should take exactly 2 inputs, takes " + in);
		if (out < 1)
			throw new InvalidWiringException("should give at leaste one output, gives " + out);
	}

}

