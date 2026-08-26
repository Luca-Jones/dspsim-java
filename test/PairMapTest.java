import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import util.PairMap;

public class PairMapTest {

	@Test
	void putAndGet() {
		PairMap<String, Integer> m = new PairMap<>();
		m.put("a", "b", 1);
		assertEquals(1, m.get("a", "b"));
	}

	@Test
	void keyOrderMatters() {
		PairMap<String, Integer> m = new PairMap<>();
		m.put("a", "b", 1);
		assertNull(m.get("b", "a"));
		assertFalse(m.contains("b", "a"));
	}

	@Test
	void missingKeyReturnsNull() {
		PairMap<String, Integer> m = new PairMap<>();
		assertNull(m.get("x", "y"));
	}

	@Test
	void putOverwrites() {
		PairMap<String, Integer> m = new PairMap<>();
		m.put("a", "b", 1);
		m.put("a", "b", 2);
		assertEquals(2, m.get("a", "b"));
	}

	@Test
	void contains() {
		PairMap<String, Integer> m = new PairMap<>();
		m.put("a", "b", 1);
		assertTrue(m.contains("a", "b"));
		assertFalse(m.contains("a", "c"));
	}

	@Test
	void remove() {
		PairMap<String, Integer> m = new PairMap<>();
		m.put("a", "b", 5);
		assertEquals(5, m.remove("a", "b"));
		assertFalse(m.contains("a", "b"));
		assertNull(m.remove("a", "b"));
	}

	@Test
	void mergeOnAbsentKeyInserts() {
		PairMap<String, Integer> m = new PairMap<>();
		m.merge("a", "b", 3, Integer::sum);
		assertEquals(3, m.get("a", "b"));
	}

	@Test
	void mergeOnPresentKeyCombines() {
		PairMap<String, Integer> m = new PairMap<>();
		m.put("a", "b", 3);
		m.merge("a", "b", 4, Integer::sum);
		assertEquals(7, m.get("a", "b"));
	}

	@Test
	void distinctPairsAreIndependent() {
		PairMap<String, Integer> m = new PairMap<>();
		m.put("a", "b", 1);
		m.put("a", "c", 2);
		m.put("b", "c", 3);
		assertEquals(1, m.get("a", "b"));
		assertEquals(2, m.get("a", "c"));
		assertEquals(3, m.get("b", "c"));
	}
}
