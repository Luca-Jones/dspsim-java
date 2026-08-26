package node;

import java.util.List;

public class DelayNode implements Node {

	public final int delay;

	public DelayNode() {
		this(1);
	}

	public DelayNode(Integer delay) {
		this.delay = delay;
	}

	@Override
	public Integer evaluate(List<Integer> inputs) {
		if (inputs.size() != 1)
			throw new IllegalArgumentException();
		return inputs.getFirst();
	}

	@Override
	public void checkWiring(int in, int out) throws InvalidWiringException {
		if (in != 1)
			throw new InvalidWiringException("should take exactly 1 input, takes " + in);
		if (out < 1)
			throw new InvalidWiringException("should give at least 1 output, gives " + out);
	}
}

