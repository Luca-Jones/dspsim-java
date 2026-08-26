package util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class PairMap<K, V> {

	private static record Key<K>(K a, K b) {

		@Override
		public final String toString() {
			return "(" + a + ", " + b + ")";
		}

	}

	private final Map<Key<K>, V> map;

	public PairMap() {
		map = new HashMap<>();
	}

	public void put(K k1, K k2, V v) {
		map.put(new Key<>(k1, k2), v);
	}

	public V remove(K k1, K k2) {
		return map.remove(new Key<K>(k1, k2));
	}

	public V get(K k1, K k2) {
		return map.get(new Key<>(k1, k2));
	}

	public boolean contains(K k1, K k2) {
		return map.containsKey(new Key<K>(k1, k2));
	}

	public V merge(K k1, K k2, V v, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		return map.merge(new Key<>(k1, k2), v, remappingFunction);
	}

	@Override
	public String toString() {
		return map.toString();
	}

}

