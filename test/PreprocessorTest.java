import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import parser.Lexer;
import parser.Preprocessor;
import parser.Token;
import parser.TokenType;

public class PreprocessorTest {

	private static List<Token> process(String input) {
		return Preprocessor.process(Lexer.lex(input));
	}

	private static List<TokenType> types(String input) {
		return process(input).stream().map(Token::type).toList();
	}

	@Test
	void emptyInputPassesThrough() {
		assertEquals(List.of(TokenType.EOF), types(""));
	}

	@Test
	void inputWithoutMacrosIsUnchanged() {
		String input = "digraph { a [type=\"gain\", value=3]; a -> b; }";
		assertEquals(Lexer.lex(input), process(input));
	}

	@Test
	void definitionIsRemovedFromStream() {
		assertEquals(List.of(TokenType.EOF), types("#define RATIO 2"));
	}

	@Test
	void numberMacroExpands() {
		List<Token> tokens = process("#define RATIO 2\nRATIO");
		assertEquals(TokenType.NUMBER, tokens.get(0).type());
		assertEquals("2", tokens.get(0).text());
	}

	@Test
	void stringMacroExpands() {
		List<Token> tokens = process("#define OUTFILE \"cic.csv\"\nOUTFILE");
		assertEquals(TokenType.STRING, tokens.get(0).type());
		assertEquals("cic.csv", tokens.get(0).text());
	}

	@Test
	void nameMacroExpands() {
		List<Token> tokens = process("#define SRC in\nSRC -> mag;");
		assertEquals(TokenType.NAME, tokens.get(0).type());
		assertEquals("in", tokens.get(0).text());
	}

	@Test
	void negativeNumberMacroExpands() {
		List<Token> tokens = process("#define INVERT -1\nINVERT");
		assertEquals(TokenType.NUMBER, tokens.get(0).type());
		assertEquals("-1", tokens.get(0).text());
	}

	@Test
	void everyOccurrenceIsReplaced() {
		List<Token> tokens = process("#define N 4\nN N N");
		assertEquals(3, tokens.size() - 1);
		tokens.subList(0, 3).forEach(t -> assertEquals("4", t.text()));
	}

	@Test
	void undefinedNameIsLeftAlone() {
		List<Token> tokens = process("#define RATIO 2\nother");
		assertEquals(TokenType.NAME, tokens.get(0).type());
		assertEquals("other", tokens.get(0).text());
	}

	@Test
	void usesBeforeDefinitionAreNotExpanded() {
		List<Token> tokens = process("RATIO\n#define RATIO 2");
		assertEquals(TokenType.NAME, tokens.get(0).type());
		assertEquals("RATIO", tokens.get(0).text());
	}

	@Test
	void macroBodyExpandsUsingEarlierMacros() {
		List<Token> tokens = process("#define A 3\n#define B A\nB");
		assertEquals(TokenType.NUMBER, tokens.get(0).type());
		assertEquals("3", tokens.get(0).text());
	}

	@Test
	void selfReferentialMacroTerminates() {
		// A is not yet defined while its own body is expanded, so it stays a NAME
		List<Token> tokens = process("#define A A\nA");
		assertEquals(TokenType.NAME, tokens.get(0).type());
		assertEquals("A", tokens.get(0).text());
	}

	@Test
	void macroNameInsideStringIsNotExpanded() {
		List<Token> tokens = process("#define RATIO 2\n\"RATIO\"");
		assertEquals(TokenType.STRING, tokens.get(0).type());
		assertEquals("RATIO", tokens.get(0).text());
	}

	@Test
	void macroNameInsideCommentIsNotExpanded() {
		assertEquals(List.of(TokenType.EOF), types("#define RATIO 2\n// RATIO\n"));
	}

	@Test
	void definitionMayAppearInsideGraphBody() {
		assertEquals(
			List.of(
				TokenType.DIGRAPH, TokenType.LBRACE,
				TokenType.NAME, TokenType.LBRACKET, TokenType.NAME,
				TokenType.EQUALS, TokenType.NUMBER,
				TokenType.RBRACKET, TokenType.SEMICOLON,
				TokenType.RBRACE, TokenType.EOF),
			types("digraph { #define N 2\n a [value=N]; }"));
	}

	@Test
	void expandedGraphMatchesLiteralGraph() {
		assertEquals(
			Lexer.lex("digraph { dec [type=\"decimator\", ratio=2]; }"),
			process("#define RATIO 2\ndigraph { dec [type=\"decimator\", ratio=RATIO]; }"));
	}

	@Test
	void redefinitionThrows() {
		RuntimeException e = assertThrows(RuntimeException.class,
			() -> process("#define A 1\n#define A 2"));
		assertTrue(e.getMessage().contains("redefined"));
	}

	@Test
	void missingNameThrows() {
		assertThrows(RuntimeException.class, () -> process("#define 2 3"));
		assertThrows(RuntimeException.class, () -> process("#define"));
	}

	@Test
	void missingValueThrows() {
		assertThrows(RuntimeException.class, () -> process("#define A"));
		assertThrows(RuntimeException.class, () -> process("#define A ;"));
	}
}
