package node;

import java.math.BigInteger;
import java.util.List;

public class RShiftNode implements Node {

	private final int shift;

	public RShiftNode(int shift) {
		this.shift = shift;
	}

	@Override
	public BigInteger evaluate(List<BigInteger> inputs) {
	    if (inputs.size() != 1)
			throw new IllegalArgumentException("RShiftNode takes exactly 1 input, " + inputs.size() + " were given.");
		return inputs.getFirst().shiftRight(shift);
	}

	@Override
	public void checkWiring(int in, int out) throws InvalidWiringException {
		if (in != 1)
			throw new InvalidWiringException("should take exactly one input, takes " + in);
		if (out < 1)
			throw new InvalidWiringException("should give at least one output, gives " + out);
	}
}

