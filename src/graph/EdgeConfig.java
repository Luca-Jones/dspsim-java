package graph;

import java.util.Map;

public record EdgeConfig(String from, String to, Map<String, String> attributes) {

	public EdgeConfig(String from, String to) {
		this(from, to, Map.of());
	}
}
