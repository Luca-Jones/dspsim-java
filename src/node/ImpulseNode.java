package node;

import java.util.List;

public class ImpulseNode implements Node {

	private boolean t0;

	public ImpulseNode() {
		t0 = true;
	}

	@Override
	public Integer evaluate(List<Integer> inputs) {
		if (!inputs.isEmpty())
			throw new IllegalArgumentException();
		if (t0) {
			t0 = false;
			return 1;
		}
		return 0;
	}

	@Override
	public int inputRate() {
		return 0;
	}

	@Override
	public void reset() {
		t0 = true;
	}

	@Override
	public void checkWiring(int in, int out) throws InvalidWiringException {
		if (in > 0)
			throw new InvalidWiringException("should not take any inputs, has " + in);
		if (out < 1)
			throw new InvalidWiringException("should give at least one output, gives " + out);
	}

}

