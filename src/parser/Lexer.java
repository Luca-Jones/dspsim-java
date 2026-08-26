package parser;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
	public static List<Token> lex(String input) {
		List<Token> tokens = new ArrayList<>();
		int pos = 0;
		char c;
		while (pos < input.length()) {
			c = input.charAt(pos);
			switch (c) {
				case ' ', '\t', '\n', '\r' -> pos++;
				case '{' -> { tokens.add(new Token(TokenType.LBRACE, "{")); pos++; }
				case '}' -> { tokens.add(new Token(TokenType.RBRACE, "}")); pos++; }
				case '[' -> { tokens.add(new Token(TokenType.LBRACKET, "[")); pos++; }
				case ']' -> { tokens.add(new Token(TokenType.RBRACKET, "]")); pos++; }
				case ';' -> { tokens.add(new Token(TokenType.SEMICOLON, ";")); pos++; }
				case ',' -> { tokens.add(new Token(TokenType.COMMA, ",")); pos++; }
				case '=' -> { tokens.add(new Token(TokenType.EQUALS, "=")); pos++; }
				case '-' -> {
					if (pos + 1 < input.length() && input.charAt(pos+1) == '>') {
						tokens.add(arrow(input, pos));
						pos += 2;
					} else if (pos + 1 < input.length() && Character.isDigit(input.charAt(pos+1))) {
						Token token = number(input, pos);
						tokens.add(token);
						pos += token.text().length();
					} else {
						throw new RuntimeException("Unexpected character '-' at " + pos);
					}
				}
				case '"' -> {
					Token token = string(input, pos);
					tokens.add(token);
					pos += token.text().length() + 2;
				}
				case '/' -> {
					pos++;
					if (input.charAt(pos) == '/') {
						pos++;
						while (pos < input.length() && input.charAt(pos) != '\n')
							pos++;
					} else if (input.charAt(pos) == '*') {
						pos++;
						while (pos < input.length() &&
								!(input.charAt(pos) == '*' && input.charAt(pos+1) == '/'))
							pos++;
						if (input.charAt(++pos) == '/') {
							pos++;
						} else {
							throw new RuntimeException("Unexpected character " + input.charAt(pos) + " expected '/'");
						}
					} else {
						throw new RuntimeException("Unexpected character " + input.charAt(pos) + " expected '/' or '*'");
					}
				}
				default -> {
					if (Character.isDigit(c)) {
						Token token = number(input, pos);
						tokens.add(token);
						pos += token.text().length();
					} else if (Character.isLetter(c) || c == '_') {
						Token token = name(input, pos);
						tokens.add(token);
						pos += token.text().length();
					} else {
						throw new RuntimeException("Unexpected character '" + c + "' at " + pos);
					}
				}
			}
		}
		tokens.add(new Token(TokenType.EOF, ""));
		return tokens;
	}

	private static Token number(String input, int pos) {
		int start = pos;
		if (input.charAt(pos) == '-')
			pos++;
		while (pos < input.length() && Character.isDigit(input.charAt(pos)))
			pos++;
		return new Token(TokenType.NUMBER, input.substring(start, pos));
	}

	private static Token string(String input, int pos) {
		int start = ++pos;
		while (pos < input.length() && input.charAt(pos) != '"')
			pos++;
		if (pos >= input.length())
			throw new RuntimeException("Unterminated string at " + start);
		return new Token(TokenType.STRING, input.substring(start, pos));
	}

	private static Token name(String input, int pos) {
		int start = pos;
		while (pos < input.length() && (
				Character.isLetter(input.charAt(pos)) ||
				Character.isDigit(input.charAt(pos)) ||
				input.charAt(pos) == '_')
		) {
			pos++;
		}
		String str = input.substring(start, pos);
		if (str.toLowerCase().equals("digraph"))
			return new Token(TokenType.DIGRAPH, "digraph");
		return new Token(TokenType.NAME, input.substring(start, pos));
	}

	private static Token arrow(String input, int pos) {
		if (pos + 1 >= input.length() || input.charAt(pos + 1) != '>')
			throw new RuntimeException();
		return new Token(TokenType.ARROW, "->");
	}

}

