package com.mealmind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandLineTokenizerTest {

    @Test
    void splitsPlainWords() {
        assertEquals(List.of("recipe", "show", "R101"), CommandLineTokenizer.tokenize("recipe show R101"));
    }

    @Test
    void quotedNamesKeepSpacesAndLoseQuotes() {
        assertEquals(List.of("pantry", "add", "Chicken Breast", "500", "g"),
                CommandLineTokenizer.tokenize("pantry add \"Chicken Breast\" 500 g"));
    }

    @Test
    void multipleSpacesAndTabsAreOneSeparator() {
        assertEquals(List.of("plan", "show"), CommandLineTokenizer.tokenize("   plan  \t  show   "));
    }

    @Test
    void emptyInputGivesNoTokens() {
        assertEquals(List.of(), CommandLineTokenizer.tokenize(""));
        assertEquals(List.of(), CommandLineTokenizer.tokenize("    "));
    }

    @Test
    void unterminatedQuoteThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CommandLineTokenizer.tokenize("pantry add \"Chicken Breast 500 g"));
        assertEquals("Unterminated quote", e.getMessage());
    }

    @Test
    void quotesInsideATokenJoinIt() {
        assertEquals(List.of("name=Olive Oil", "x"), CommandLineTokenizer.tokenize("name=\"Olive Oil\" x"));
        assertEquals(List.of(""), CommandLineTokenizer.tokenize("\"\""));
    }
}
