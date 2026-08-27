package parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * define := DEFINE NAME value
 * value  := NUMBER | STRING | NAME
 * */
public class Preprocessor {

	public static List<Token> process(List<Token> tokens) {
		Map<String, Token> macros = new HashMap<>();
		List<Token> out = new ArrayList<>();
		int pos = 0;
		while (pos < tokens.size()) {
			Token token = tokens.get(pos);
			if (token.type() == TokenType.DEFINE) {
				Token name = tokenAt(tokens, pos+1);
				if (name.type() != TokenType.NAME)
					throw new RuntimeException("#define expects a name but got " + name);
				Token value = tokenAt(tokens, pos+2);
				if (!isValue(value))
					throw new RuntimeException("#define " + name.text() + " expects a value but got " + value);

				if (macros.containsKey(name.text()))
					throw new RuntimeException("macro '" + name.text() + "' redefined");

				pos += 3;
			} else {
				out.add(expandMacro(token, macros));
				pos++;
			}
		}
		return out;
	}

	private static Token expandMacro(Token token, Map<String, Token> macros) {
		if (token.type() != TokenType.NAME)
			return token;
		return macros.getOrDefault(token.text(), token);
	}

	private static boolean isValue(Token token) {
		return token.type() == TokenType.NUMBER ||
				token.type() == TokenType.STRING ||
				token.type() == TokenType.NAME;
	}

	private static Token tokenAt(List<Token> tokens, int pos) {
		if (pos >= tokens.size())
			throw new RuntimeException("unexpected end of input");
		return tokens.get(pos);
	}

}
