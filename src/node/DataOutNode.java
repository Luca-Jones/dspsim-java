package node;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.List;

public class DataOutNode implements Node {

	private final PrintStream out;

	public DataOutNode(String file) {
		try {
			out = new PrintStream(new FileOutputStream(file), true);
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Cannot open output file: " + file, e);
		}
	}

	public DataOutNode() {
		out = System.out;
	}

	@Override
	public Integer evaluate(List<Integer> inputs) {
		if (inputs == null || inputs.size() != 1)
			throw new IllegalArgumentException();
		out.println(inputs.getFirst()+",");
		return inputs.getFirst();
	}

	@Override
	public void checkWiring(int in, int out) throws InvalidWiringException {
		if (in != 1)
			throw new InvalidWiringException("should take exactly one input, takes " + in);
		if (out > 0)
			throw new InvalidWiringException("should not give any outputs, gives " + out);
	}
}

