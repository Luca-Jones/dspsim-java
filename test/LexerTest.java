import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import parser.Lexer;
import parser.Token;
import parser.TokenType;

public class LexerTest {

	private static List<TokenType> types(String input) {
		return Lexer.lex(input).stream().map(Token::type).toList();
	}

	@Test
	void emptyInputYieldsOnlyEof() {
		assertEquals(List.of(TokenType.EOF), types(""));
	}

	@Test
	void whitespaceOnlyYieldsOnlyEof() {
		assertEquals(List.of(TokenType.EOF), types("  \t\n\r  "));
	}

	@Test
	void punctuationTokens() {
		assertEquals(
			List.of(TokenType.LBRACE, TokenType.RBRACE, TokenType.LBRACKET,
				TokenType.RBRACKET, TokenType.SEMICOLON, TokenType.COMMA,
				TokenType.EQUALS, TokenType.EOF),
			types("{}[];,="));
	}

	@Test
	void arrowToken() {
		List<Token> tokens = Lexer.lex("a -> b");
		assertEquals(TokenType.ARROW, tokens.get(1).type());
		assertEquals("->", tokens.get(1).text());
	}

	@Test
	void nameToken() {
		List<Token> tokens = Lexer.lex("foo_1");
		assertEquals(TokenType.NAME, tokens.get(0).type());
		assertEquals("foo_1", tokens.get(0).text());
	}

	@Test
	void nameMayStartWithUnderscore() {
		List<Token> tokens = Lexer.lex("_x");
		assertEquals(TokenType.NAME, tokens.get(0).type());
		assertEquals("_x", tokens.get(0).text());
	}

	@Test
	void numberToken() {
		List<Token> tokens = Lexer.lex("42");
		assertEquals(TokenType.NUMBER, tokens.get(0).type());
		assertEquals("42", tokens.get(0).text());
	}

	@Test
	void negativeNumberToken() {
		List<Token> tokens = Lexer.lex("-17");
		assertEquals(TokenType.NUMBER, tokens.get(0).type());
		assertEquals("-17", tokens.get(0).text());
	}

	@Test
	void minusWithoutDigitsThrows() {
		assertThrows(RuntimeException.class, () -> Lexer.lex("-x"));
		assertThrows(RuntimeException.class, () -> Lexer.lex("a - b"));
		assertThrows(RuntimeException.class, () -> Lexer.lex("-"));
	}

	@Test
	void stringToken() {
		List<Token> tokens = Lexer.lex("\"hello world.csv\"");
		assertEquals(TokenType.STRING, tokens.get(0).type());
		assertEquals("hello world.csv", tokens.get(0).text());
	}

	@Test
	void emptyStringToken() {
		List<Token> tokens = Lexer.lex("\"\"");
		assertEquals(TokenType.STRING, tokens.get(0).type());
		assertEquals("", tokens.get(0).text());
	}

	@Test
	void digraphKeywordIsCaseInsensitive() {
		assertEquals(TokenType.DIGRAPH, Lexer.lex("digraph").get(0).type());
		assertEquals(TokenType.DIGRAPH, Lexer.lex("DIGRAPH").get(0).type());
		assertEquals(TokenType.DIGRAPH, Lexer.lex("DiGraph").get(0).type());
	}

	@Test
	void defineDirectiveToken() {
		assertEquals(
			List.of(TokenType.DEFINE, TokenType.NAME, TokenType.NUMBER, TokenType.EOF),
			types("#define RATIO 2"));
		assertEquals("#define", Lexer.lex("#define RATIO 2").get(0).text());
	}

	@Test
	void unknownDirectiveThrows() {
		RuntimeException e = assertThrows(RuntimeException.class, () -> Lexer.lex("#include \"x\""));
		assertTrue(e.getMessage().contains("Unknown directive"));
	}

	@Test
	void lineCommentIsSkipped() {
		assertEquals(
			List.of(TokenType.NAME, TokenType.NAME, TokenType.EOF),
			types("a // comment ; { } ->\nb\n"));
	}

	@Test
	void lineCommentAtEndOfFile() {
		assertEquals(List.of(TokenType.NAME, TokenType.EOF), types("a // trailing"));
	}

	@Test
	void blockCommentIsSkipped() {
		assertEquals(
			List.of(TokenType.NAME, TokenType.NAME, TokenType.EOF),
			types("a /* comment ; { } */ b"));
	}

	@Test
	void emptyBlockCommentIsSkipped() {
		assertEquals(List.of(TokenType.NAME, TokenType.EOF), types("/**/ a"));
	}

	@Test
	void blockCommentContainingLoneStar() {
		assertEquals(List.of(TokenType.NAME, TokenType.EOF), types("/* a * b */ x"));
	}

	@Test
	void unterminatedStringThrows() {
		RuntimeException e = assertThrows(RuntimeException.class, () -> Lexer.lex("\"never closed"));
		assertTrue(e.getMessage().contains("Unterminated string"));
	}

	@Test
	void unexpectedCharacterThrows() {
		RuntimeException e = assertThrows(RuntimeException.class, () -> Lexer.lex("@"));
		assertTrue(e.getMessage().contains("Unexpected character"));
	}

	@Test
	void loneSlashThrows() {
		assertThrows(RuntimeException.class, () -> Lexer.lex("/ x"));
	}

	@Test
	void fullGraphTokenStream() {
		assertEquals(
			List.of(
				TokenType.DIGRAPH, TokenType.LBRACE,
				TokenType.NAME, TokenType.LBRACKET, TokenType.NAME,
				TokenType.EQUALS, TokenType.STRING, TokenType.COMMA,
				TokenType.NAME, TokenType.EQUALS, TokenType.NUMBER,
				TokenType.RBRACKET, TokenType.SEMICOLON,
				TokenType.NAME, TokenType.ARROW, TokenType.NAME, TokenType.SEMICOLON,
				TokenType.RBRACE, TokenType.EOF),
			types("digraph { a [type=\"gain\", value=3]; a -> b; }"));
	}

	@Test
	void alwaysEndsWithEof() {
		List<Token> tokens = Lexer.lex("a b c");
		assertEquals(TokenType.EOF, tokens.get(tokens.size() - 1).type());
	}
}
