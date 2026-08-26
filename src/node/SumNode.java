package node;

import java.util.List;

public class SumNode implements Node {

	public SumNode() {}

	@Override
	public Integer evaluate(List<Integer> inputs) {
		if (inputs == null || inputs.size() < 2)
			throw new IllegalArgumentException();
		return inputs.stream().reduce(0, Integer::sum);
	}

	@Override
	public void checkWiring(int in, int out) throws InvalidWiringException {
		if (in < 2)
			throw new InvalidWiringException("should take at least 2 inputs, takes " + in);
		if (out < 1)
			throw new InvalidWiringException("should give at least one output, gives " + out);

	}

}

