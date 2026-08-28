package parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import graph.*;

/**
 * graph   := DIGRAPH LBRACE nodedef* edge* RBRACE
 * nodedef := NAME attrs? SEMICOLON
 * bus     := NAME ARROW NAME SEMICOLON
 * attrs   := LBRACKET ( prop (";"|"," prop)* )? RBRACKET
 * prop    := NAME "=" value
 * value   := NUMBER | STRING | NAME
 * */
public class Parser {

	private final List<Token> tokens;
	private int pos;
	private List<VertexConfig> vertexConfigs;
	private List<EdgeConfig> edgeConfigs;

	public Parser(List<Token> tokens) {
		this.tokens = tokens;
		this.pos = 0;
		this.vertexConfigs = new ArrayList<>();
		this.edgeConfigs = new ArrayList<>();
	}

	public GraphConfig parseGraph() {
		expect(TokenType.DIGRAPH);
		expect(TokenType.LBRACE);
		while (peek().type() == TokenType.NAME) {
			if (lookAhead().type() == TokenType.ARROW) {
				edgeConfigs.add(parseBus());
			} else {
				vertexConfigs.add(parseNodeDef());
			}
		}
		expect(TokenType.RBRACE);
		return new GraphConfig(vertexConfigs, edgeConfigs);
	}

	private VertexConfig parseNodeDef() {
		Map<String, String> attrs = new HashMap<>();
		String name = expect(TokenType.NAME).text();
		if (peek().type() == TokenType.SEMICOLON) {
			expect(TokenType.SEMICOLON);
			return new VertexConfig(name, attrs);
		}
		attrs = parseAttrs();
		expect(TokenType.SEMICOLON);
		return new VertexConfig(name, attrs);
	}

	private Map<String, String> parseAttrs() {
		Map<String, String> attrs = new HashMap<>();
		expect(TokenType.LBRACKET);
		while (peek().type() == TokenType.NAME) {
			String name = expect(TokenType.NAME).text();
			expect(TokenType.EQUALS);
			String value = parseValue();
			attrs.put(name, value);
			if (peek().type() == TokenType.COMMA ||
				peek().type() == TokenType.SEMICOLON)
				advance();
		}
		expect(TokenType.RBRACKET);
		return attrs;
	}

	private String parseValue() {
		String value;
		if (peek().type() == TokenType.NUMBER)
			value = expect(TokenType.NUMBER).text();
		else if(peek().type() == TokenType.STRING)
			value = expect(TokenType.STRING).text();
		else
			value = expect(TokenType.NAME).text();
		return value;
	}

	private EdgeConfig parseBus() {
		String from = expect(TokenType.NAME).text();
		expect(TokenType.ARROW);
		String to = expect(TokenType.NAME).text();
		expect(TokenType.SEMICOLON);
		return new EdgeConfig(from, to);
	}

	private Token peek() {
		return tokens.get(pos);
	}

	private Token lookAhead() {
		return tokens.get(Math.min(pos+1, tokens.size()-1));
	}

	private Token advance() {
		return tokens.get(pos++);
	}

	private Token expect(TokenType tt) {
		if (this.peek().type() == tt)
			return this.advance();
		throw new RuntimeException("expected " + tt + " but got " + peek());
	}

}

