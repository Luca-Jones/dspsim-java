import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import sim.Bus;

public class BusTest {

	private static BigInteger wrap(long v, int w) {
		return Bus.wrap(BigInteger.valueOf(v), w);
	}

	@Test
	void overflowWrapsTwosComplement() {
		assertEquals(BigInteger.valueOf(-128), wrap(128, 8));
		assertEquals(BigInteger.valueOf(127), wrap(-129, 8));
		assertEquals(BigInteger.valueOf(44), wrap(300, 8));
		assertEquals(BigInteger.valueOf(-1), wrap(255, 8));
	}

	@Test
	void valuesThatFitPassUnchanged() {
		for (long v = -128; v <= 127; v++)
			assertEquals(BigInteger.valueOf(v), wrap(v, 8));
	}

	@Test
	void oneBitIsZeroOrMinusOne() {
		assertEquals(BigInteger.ZERO, wrap(0, 1));
		assertEquals(BigInteger.valueOf(-1), wrap(1, 1));
		assertEquals(BigInteger.ZERO, wrap(2, 1));
		assertEquals(BigInteger.valueOf(-1), wrap(-1, 1));
	}

	@Test
	void pushWrapsOnlyBoundedBuses() {
		BigInteger big = BigInteger.ONE.shiftLeft(100);
		Bus unbounded = new Bus(Bus.UNBOUNDED);
		unbounded.push(big);
		assertEquals(big, unbounded.poll());
		Bus narrow = new Bus(4);
		narrow.push(BigInteger.valueOf(9));
		narrow.push(BigInteger.valueOf(-9));
		assertEquals(BigInteger.valueOf(-7), narrow.poll());
		assertEquals(BigInteger.valueOf(7), narrow.poll());
		assertNull(narrow.poll());
	}
}
