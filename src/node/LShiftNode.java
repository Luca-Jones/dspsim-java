package node;

import java.util.List;

public class LShiftNode implements Node {

	private final int shift;

	public LShiftNode(int shift) {
		this.shift = shift;
	}

	@Override
	public Integer evaluate(List<Integer> inputs) {
	    if (inputs.size() != 1)
			throw new IllegalArgumentException("LShiftNode takes exactly 1 input, " + inputs.size() + " were given.");
		return inputs.getFirst() << shift;
	}

	@Override
	public void checkWiring(int in, int out) throws InvalidWiringException {
		if (in != 1)
			throw new InvalidWiringException("should take exactly one input, takes " + in);
		if (out != 1)
			throw new InvalidWiringException("should give exactly one output, gives " + out);
	}
}

