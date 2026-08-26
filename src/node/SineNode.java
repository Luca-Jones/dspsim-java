package node;

import java.util.List;

public class SineNode implements Node {

	private long n;
	private int amplitude, period, phase;

	public SineNode(int amplitude, int period) {
		this(amplitude, period, 0);
	}

	public SineNode(int amplitude, int period, int phase) {
		n = 0;
		this.amplitude = amplitude;
		this.period = period;
		this.phase = phase;
	}

	@Override
	public Integer evaluate(List<Integer> inputs) {
		if (!inputs.isEmpty())
			throw new RuntimeException();
		return (int) Math.round(amplitude * Math.sin(2 * Math.PI * ((n++)+phase) / period));
	}

	@Override
	public void reset() {
		n = 0;
	}

	@Override
	public int inputRate() {
		return 0;
	}

	@Override
	public void checkWiring(int in, int out) throws InvalidWiringException {
		if (in > 0)
			throw new InvalidWiringException("should not take any inputs, has " + in);
		if (out < 1)
			throw new InvalidWiringException("should give at least one output, gives " + out);
	}

}

