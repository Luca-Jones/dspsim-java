package graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.w3c.dom.Node;

public class Digraph<V> {

	private Map<String, V> vertices;
	private Map<V, List<V>> children;
	private Map<V, List<V>> parents;

	public Digraph(GraphConfig config, VertexFactory<V> factory) {
		vertices = new HashMap<>();
		children = new IdentityHashMap<>();
		parents = new IdentityHashMap<>();
		for (VertexConfig vc : config.vertexConfigs())
			addVertex(vc, factory);
		for (EdgeConfig ec : config.edgeConfigs())
			addEdge(ec);
		checkFloatingVertices();
	}

	private void addVertex(VertexConfig vc, VertexFactory<V> factory) {
		V vertex = factory.createVertex(vc);
		vertices.put(vc.name(), vertex);
		children.put(vertex, new ArrayList<>());
		parents.put(vertex, new ArrayList<>());
	}

	private void addEdge(EdgeConfig ec) {
			if (!vertices.containsKey(ec.from()))
				throw new RuntimeException("No node " + ec.from() + " exists in this Digraph.");
			if (!vertices.containsKey(ec.to()))
				throw new RuntimeException("No node " + ec.to() + " exists in this Digraph.");
			V from = vertices.get(ec.from());
			V to = vertices.get(ec.to());
			if (children.get(from).contains(to))
				throw new RuntimeException("Duplicate edge " + ec.from() + " -> " + ec.to() + ".");
			List<V> fromChildren = children.get(from);
			fromChildren.add(to);
			children.put(from, fromChildren);
			List<V> toParents = parents.get(to);
			toParents.add(from);
			parents.put(to, toParents);
	}

	private void checkFloatingVertices() {
		for (Map.Entry<String, V> entry : vertices.entrySet()) {
	 		V v = entry.getValue();
			if (children.get(v).size() == 0 && parents.get(v).size() == 0)
				throw new RuntimeException("Vertex " + entry.getKey() + " is not connected to any other nodes.");
		}
	}

	public Set<V> getVertices() {
		assert(children.keySet().equals(parents.keySet()));
		return new HashSet<>(children.keySet());
	}

	public List<V> getChildren(V vertex) {
		if (!children.containsKey(vertex))
			throw new NoSuchElementException();
		return children.get(vertex);
	}

	public List<V> getParents(V vertex) {
		if (!parents.containsKey(vertex))
			throw new NoSuchElementException();
		return parents.get(vertex);
	}

}

