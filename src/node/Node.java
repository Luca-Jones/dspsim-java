package node;

import java.util.List;

public interface Node {
	Integer evaluate (List<Integer> inputs);
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

