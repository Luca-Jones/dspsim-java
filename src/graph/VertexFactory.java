package graph;

import java.util.Map;

public interface VertexFactory<V> {
	V createVertex(VertexConfig config);
}

