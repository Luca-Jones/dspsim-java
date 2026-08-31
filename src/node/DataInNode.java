package node;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DataInNode implements Node {

	private final List<BigInteger> data;
	private int index;

	public DataInNode(String file) {
		data = new ArrayList<>();
		List<String> lines;
		try {
			lines = Files.readAllLines(Path.of(file));
		} catch (IOException e) {
			throw new RuntimeException("Cannot open input file: " + file, e);
		}
		for (String line : lines) {
			String s = line.trim();
			try {
				data.add(new BigInteger(s.split(",")[0].trim()));
			} catch (NumberFormatException e) {
				if (!data.isEmpty())
					break;
			}
		}
	}

	@Override
	public BigInteger evaluate(List<BigInteger> inputs) {
		if (!inputs.isEmpty())
			throw new IllegalArgumentException();
		if (index < data.size())
			return data.get(index++);
		return BigInteger.ZERO;
	}

	@Override
	public int inputRate() {
		return 0;
	}

	@Override
	public void reset() {
		index = 0;
	}

	@Override
	public void checkWiring(int in, int out) throws InvalidWiringException {
		if (in > 0)
			throw new InvalidWiringException("should not take any inputs, has " + in);
		if (out < 1)
			throw new InvalidWiringException("should give at least one output, gives " + out);
	}

}
