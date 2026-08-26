import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import graph.Digraph;
import graph.EdgeConfig;
import graph.GraphConfig;
import graph.VertexConfig;
import graph.VertexFactory;

public class DigraphTest {

	// Digraph uses identity maps internally, so the test keeps the exact
	// vertex objects the factory handed out.
	private static class Fixture {
		final Map<String, Object> made = new HashMap<>();
		final Digraph<Object> graph;

		Fixture(List<String> names, List<EdgeConfig> edges) {
			List<VertexConfig> vcs = names.stream()
				.map(n -> new VertexConfig(n, Map.of())).toList();
			VertexFactory<Object> factory = vc -> {
				Object v = new Object() {
					@Override public String toString() { return vc.name(); }
				};
				made.put(vc.name(), v);
				return v;
			};
			graph = new Digraph<>(new GraphConfig(vcs, edges), factory);
		}

		Object v(String name) { return made.get(name); }
	}

	@Test
	void verticesAreCreatedFromConfig() {
		Fixture f = new Fixture(List.of("a", "b"), List.of(new EdgeConfig("a", "b")));
		assertEquals(2, f.graph.getVertices().size());
		assertTrue(f.graph.getVertices().contains(f.v("a")));
		assertTrue(f.graph.getVertices().contains(f.v("b")));
	}

	@Test
	void edgeCreatesChildAndParentLinks() {
		Fixture f = new Fixture(List.of("a", "b"), List.of(new EdgeConfig("a", "b")));
		assertEquals(List.of(f.v("b")), f.graph.getChildren(f.v("a")));
		assertEquals(List.of(f.v("a")), f.graph.getParents(f.v("b")));
		assertTrue(f.graph.getParents(f.v("a")).isEmpty());
		assertTrue(f.graph.getChildren(f.v("b")).isEmpty());
	}

	@Test
	void fanOutAndFanIn() {
		Fixture f = new Fixture(
			List.of("a", "b", "c", "d"),
			List.of(new EdgeConfig("a", "b"), new EdgeConfig("a", "c"),
				new EdgeConfig("b", "d"), new EdgeConfig("c", "d")));
		assertEquals(List.of(f.v("b"), f.v("c")), f.graph.getChildren(f.v("a")));
		assertEquals(List.of(f.v("b"), f.v("c")), f.graph.getParents(f.v("d")));
	}

	@Test
	void duplicateEdgeThrows() {
		RuntimeException e = assertThrows(RuntimeException.class, () -> new Fixture(
			List.of("a", "b"),
			List.of(new EdgeConfig("a", "b"), new EdgeConfig("a", "b"))));
		assertTrue(e.getMessage().contains("Duplicate edge"));
	}

	@Test
	void cycleIsAllowedAtGraphLevel() {
		Fixture f = new Fixture(
			List.of("a", "b"),
			List.of(new EdgeConfig("a", "b"), new EdgeConfig("b", "a")));
		assertEquals(List.of(f.v("b")), f.graph.getChildren(f.v("a")));
		assertEquals(List.of(f.v("b")), f.graph.getParents(f.v("a")));
	}

	@Test
	void selfLoopIsAllowedAtGraphLevel() {
		Fixture f = new Fixture(List.of("a"), List.of(new EdgeConfig("a", "a")));
		assertEquals(List.of(f.v("a")), f.graph.getChildren(f.v("a")));
	}

	@Test
	void edgeFromUnknownVertexThrows() {
		RuntimeException e = assertThrows(RuntimeException.class,
			() -> new Fixture(List.of("a"), List.of(new EdgeConfig("ghost", "a"))));
		assertTrue(e.getMessage().contains("ghost"));
	}

	@Test
	void edgeToUnknownVertexThrows() {
		RuntimeException e = assertThrows(RuntimeException.class,
			() -> new Fixture(List.of("a"), List.of(new EdgeConfig("a", "ghost"))));
		assertTrue(e.getMessage().contains("ghost"));
	}

	@Test
	void floatingVertexThrows() {
		RuntimeException e = assertThrows(RuntimeException.class,
			() -> new Fixture(List.of("a", "b", "lonely"), List.of(new EdgeConfig("a", "b"))));
		assertTrue(e.getMessage().contains("lonely"));
	}

	@Test
	void unknownVertexQueryThrows() {
		Fixture f = new Fixture(List.of("a", "b"), List.of(new EdgeConfig("a", "b")));
		Object stranger = new Object();
		assertThrows(NoSuchElementException.class, () -> f.graph.getChildren(stranger));
		assertThrows(NoSuchElementException.class, () -> f.graph.getParents(stranger));
	}
}
