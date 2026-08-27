package parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Expands C-like object macros over the token stream, between the lexer and
 * the parser. The parser never sees a DEFINE token, so the grammar is
 * unchanged.
 *
 * define := DEFINE NAME value
 * value  := NUMBER | STRING | NAME
 *
 * A macro body is exactly one token because the lexer discards newlines, so
 * there is no way to express C's "body runs to end of line". Every later NAME
 * matching a defined macro is replaced by the stored value token, which
 * carries its own type: after expansion "ratio=RATIO" is a real NUMBER.
 * */
public class Preprocessor {

	public static List<Token> process(List<Token> tokens) {
		Map<String, Token> macros = new HashMap<>();
		List<Token> out = new ArrayList<>();
		int pos = 0;
		while (pos < tokens.size()) {
			Token token = tokens.get(pos);
			if (token.type() != TokenType.DEFINE) {
				out.add(expand(macros, token));
				pos++;
				continue;
			}
			Token name = at(tokens, pos + 1);
			Token value = at(tokens, pos + 2);
			if (name.type() != TokenType.NAME)
				throw new RuntimeException("#define expects a name but got " + name);
			if (!isValue(value))
				throw new RuntimeException("#define " + name.text() + " expects a value but got " + value);
			if (macros.containsKey(name.text()))
				throw new RuntimeException("macro '" + name.text() + "' redefined");
			// expanding the body here rather than at the use site makes
			// recursive macros structurally impossible
			macros.put(name.text(), expand(macros, value));
			pos += 3;
		}
		return out;
	}

	private static Token expand(Map<String, Token> macros, Token token) {
		if (token.type() != TokenType.NAME)
			return token;
		return macros.getOrDefault(token.text(), token);
	}

	private static boolean isValue(Token token) {
		return token.type() == TokenType.NUMBER ||
				token.type() == TokenType.STRING ||
				token.type() == TokenType.NAME;
	}

	private static Token at(List<Token> tokens, int pos) {
		if (pos >= tokens.size())
			throw new RuntimeException("unexpected end of input in #define");
		return tokens.get(pos);
	}

}
