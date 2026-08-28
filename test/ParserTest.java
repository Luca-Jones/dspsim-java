import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import graph.EdgeConfig;
import graph.GraphConfig;
import graph.VertexConfig;
import parser.Lexer;
import parser.Parser;

public class ParserTest {

	private static GraphConfig parse(String input) {
		return new Parser(Lexer.lex(input)).parseGraph();
	}

	@Test
	void emptyGraph() {
		GraphConfig gc = parse("digraph { }");
		assertTrue(gc.vertexConfigs().isEmpty());
		assertTrue(gc.edgeConfigs().isEmpty());
	}

	@Test
	void nodeWithoutAttributes() {
		GraphConfig gc = parse("digraph { a; }");
		assertEquals(1, gc.vertexConfigs().size());
		VertexConfig vc = gc.vertexConfigs().get(0);
		assertEquals("a", vc.name());
		assertTrue(vc.attributes().isEmpty());
	}

	@Test
	void nodeWithEmptyAttributeList() {
		GraphConfig gc = parse("digraph { a []; }");
		assertEquals("a", gc.vertexConfigs().get(0).name());
		assertTrue(gc.vertexConfigs().get(0).attributes().isEmpty());
	}

	@Test
	void nodeWithSingleAttribute() {
		GraphConfig gc = parse("digraph { a [type=\"impulse\"]; }");
		assertEquals(Map.of("type", "impulse"), gc.vertexConfigs().get(0).attributes());
	}

	@Test
	void commaSeparatedAttributes() {
		GraphConfig gc = parse("digraph { a [type=\"gain\", value=3]; }");
		assertEquals(Map.of("type", "gain", "value", "3"), gc.vertexConfigs().get(0).attributes());
	}

	@Test
	void semicolonSeparatedAttributes() {
		GraphConfig gc = parse("digraph { a [type=\"delay\"; delay=2]; }");
		assertEquals(Map.of("type", "delay", "delay", "2"), gc.vertexConfigs().get(0).attributes());
	}

	@Test
	void attributesWithoutSeparatorStillParse() {
		// separator between props is optional in this grammar
		GraphConfig gc = parse("digraph { a [type=\"gain\" value=3]; }");
		assertEquals(Map.of("type", "gain", "value", "3"), gc.vertexConfigs().get(0).attributes());
	}

	@Test
	void numberValueAttribute() {
		GraphConfig gc = parse("digraph { a [value=-7]; }");
		assertEquals("-7", gc.vertexConfigs().get(0).attributes().get("value"));
	}

	@Test
	void stringValueAttribute() {
		GraphConfig gc = parse("digraph { a [file=\"out.csv\"]; }");
		assertEquals("out.csv", gc.vertexConfigs().get(0).attributes().get("file"));
	}

	@Test
	void quotedNumberStaysString() {
		GraphConfig gc = parse("digraph { a [value=\"2\"]; }");
		assertEquals("2", gc.vertexConfigs().get(0).attributes().get("value"));
	}

	@Test
	void singleEdge() {
		GraphConfig gc = parse("digraph { a; b; a -> b; }");
		assertEquals(1, gc.edgeConfigs().size());
		assertEquals(new EdgeConfig("a", "b"), gc.edgeConfigs().get(0));
	}

	@Test
	void multipleNodesAndEdges() {
		GraphConfig gc = parse("""
			digraph {
				in [type="impulse"];
				g [type="gain", value=2];
				out [type="dataout"];
				in -> g;
				g -> out;
			}
			""");
		assertEquals(3, gc.vertexConfigs().size());
		assertEquals(2, gc.edgeConfigs().size());
		assertEquals("in", gc.vertexConfigs().get(0).name());
		assertEquals(new EdgeConfig("g", "out"), gc.edgeConfigs().get(1));
	}

	@Test
	void interleavedNodeDefsAndEdges() {
		GraphConfig gc = parse("digraph { a; b; a -> b; c; b -> c; }");
		assertEquals(3, gc.vertexConfigs().size());
		assertEquals(2, gc.edgeConfigs().size());
	}

	@Test
	void commentsInsideGraph() {
		GraphConfig gc = parse("""
			digraph {
				// a source
				a [type="impulse"]; /* inline */
				b;
				a -> b;
			}
			""");
		assertEquals(2, gc.vertexConfigs().size());
		assertEquals(1, gc.edgeConfigs().size());
	}

	@Test
	void missingDigraphKeywordThrows() {
		RuntimeException e = assertThrows(RuntimeException.class, () -> parse("{ a; }"));
		assertTrue(e.getMessage().contains("expected DIGRAPH"));
	}

	@Test
	void missingLeftBraceThrows() {
		assertThrows(RuntimeException.class, () -> parse("digraph a; }"));
	}

	@Test
	void missingRightBraceThrows() {
		assertThrows(RuntimeException.class, () -> parse("digraph { a;"));
	}

	@Test
	void missingSemicolonAfterNodeDefThrows() {
		assertThrows(RuntimeException.class, () -> parse("digraph { a [type=\"impulse\"] }"));
	}

	@Test
	void missingSemicolonAfterEdgeThrows() {
		assertThrows(RuntimeException.class, () -> parse("digraph { a -> b }"));
	}

	@Test
	void missingEdgeTargetThrows() {
		assertThrows(RuntimeException.class, () -> parse("digraph { a -> ; }"));
	}

	@Test
	void missingEqualsInAttributeThrows() {
		assertThrows(RuntimeException.class, () -> parse("digraph { a [type \"impulse\"]; }"));
	}

	@Test
	void bareNameIsAllowedAsAttributeValue() {
		GraphConfig gc = parse("digraph { a [type=impulse]; }");
		assertEquals(Map.of("type", "impulse"), gc.vertexConfigs().get(0).attributes());
	}

	@Test
	void missingClosingBracketThrows() {
		assertThrows(RuntimeException.class, () -> parse("digraph { a [type=\"impulse\"; }"));
	}
}
