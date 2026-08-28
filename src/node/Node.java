package node;

import java.math.BigInteger;
import java.util.List;

public interface Node {
	BigInteger evaluate (List<BigInteger> inputs);
	default int inputRate() { return 1; }
	default int outputRate() { return 1; }
	default void reset() {}

	class InvalidWiringException extends RuntimeException {
		public InvalidWiringException(String message) {
			super(message);
		}
	}
	public void checkWiring(int in, int out) throws InvalidWiringException;
}

