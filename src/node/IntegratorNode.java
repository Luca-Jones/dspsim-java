package node;

import java.math.BigInteger;
import java.util.List;

/** Running sum with transfer function z^-1/(1-z^-1): y[n] = y[n-1] + x[n-1]. */
public class IntegratorNode implements Node {

	private BigInteger acc = BigInteger.ZERO;

	@Override
	public BigInteger evaluate(List<BigInteger> inputs) {
		if (inputs.size() != 1)
			throw new IllegalArgumentException();
		acc = acc.add(inputs.getFirst());
		return acc;
	}

	@Override
	public void reset() {
		acc = BigInteger.ZERO;
	}

	@Override
	public int initialTokens() {
		return 1;
	}

	@Override
	public void checkWiring(int in, int out) throws InvalidWiringException {
		if (in != 1)
			throw new InvalidWiringException("should take exactly 1 input, takes " + in);
		if (out < 1)
			throw new InvalidWiringException("should give at least 1 output, gives " + out);
	}
}
