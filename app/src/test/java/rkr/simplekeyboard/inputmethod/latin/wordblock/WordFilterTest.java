package rkr.simplekeyboard.inputmethod.latin.wordblock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class WordFilterTest {
    private static WordFilter filterOf(final String... entries) {
        return new WordFilter(Arrays.asList(entries));
    }

    // --- Exact / case / accents / apostrophes ---

    @Test
    public void exactMatch() {
        final WordFilter filter = filterOf("word");
        assertTrue(filter.isBlocked("word"));
        assertFalse(filter.isBlocked("other"));
    }

    @Test
    public void caseInsensitive() {
        final WordFilter filter = filterOf("word");
        assertTrue(filter.isBlocked("Word"));
        assertTrue(filter.isBlocked("WORD"));
        assertTrue(filterOf("WoRd").isBlocked("word"));
    }

    @Test
    public void accentsAreStripped() {
        final WordFilter filter = filterOf("word");
        assertTrue(filter.isBlocked("w\u00f3rd"));
        assertTrue(filter.isBlocked("W\u00d3RD"));
        assertTrue(filter.isBlocked("w\u00f6rd"));
        // Decomposed form: "o" followed by a combining acute accent.
        assertTrue(filter.isBlocked("wo\u0301rd"));
        // And in the entry rather than the typed word.
        assertTrue(filterOf("w\u00f3rd").isBlocked("word"));
    }

    @Test
    public void dottedCapitalIStillMatches() {
        assertTrue(filterOf("iris").isBlocked("\u0130RIS"));
    }

    @Test
    public void apostrophesAreIgnored() {
        assertTrue(filterOf("dont").isBlocked("don't"));
        assertTrue(filterOf("dont").isBlocked("don\u2019t"));
        assertTrue(filterOf("dont").isBlocked("DON'T"));
        assertTrue(filterOf("don't").isBlocked("dont"));
        assertTrue(filterOf("don't").isBlocked("don\u2019t"));
    }

    // --- Leet ---

    @Test
    public void leetDigitsAndSymbols() {
        final WordFilter filter = filterOf("word");
        assertTrue(filter.isBlocked("w0rd"));
        assertTrue(filter.isBlocked("W0RD"));
        assertTrue(filterOf("sword").isBlocked("$word"));
        assertTrue(filterOf("cat").isBlocked("c@7"));
        assertTrue(filterOf("seat").isBlocked("53@7"));
        assertTrue(filterOf("eat").isBlocked("347"));
    }

    @Test
    public void leetAppliesToEntriesToo() {
        assertTrue(filterOf("w0rd").isBlocked("word"));
        assertTrue(filterOf("w0rd").isBlocked("w0rd"));
        assertTrue(filterOf("$word").isBlocked("sword"));
    }

    @Test
    public void ambiguousOneMatchesBothIAndL() {
        final WordFilter kill = filterOf("kill");
        assertTrue(kill.isBlocked("k1ll"));   // 1 -> i
        assertTrue(kill.isBlocked("ki11"));   // 1 -> l, l
        assertTrue(kill.isBlocked("k111"));   // i, l, l
        assertTrue(filterOf("k1ll").isBlocked("kill"));
        assertTrue(filterOf("k1ll").isBlocked("klll"));

        final WordFilter lit = filterOf("lit");
        assertTrue(lit.isBlocked("1it"));     // 1 -> l
        final WordFilter iron = filterOf("iron");
        assertTrue(iron.isBlocked("1r0n"));   // 1 -> i
        assertFalse(iron.isBlocked("2r0n"));
    }

    @Test
    public void variantsAreCapped() {
        final Set<String> two = WordFilter.variantsOf("11");
        assertEquals(4, two.size());
        assertTrue(two.containsAll(Arrays.asList("ii", "il", "li", "ll")));

        assertEquals(8, WordFilter.variantsOf("111").size());
        assertEquals(WordFilter.MAX_VARIANTS, WordFilter.variantsOf("1111").size());
        assertEquals(WordFilter.MAX_VARIANTS, WordFilter.variantsOf("11111111").size());
        assertEquals(1, WordFilter.variantsOf("word").size());
    }

    @Test
    public void cappedVariantsStillIncludeTheAllIReading() {
        final Set<String> variants = WordFilter.variantsOf("1111");
        assertTrue(variants.contains("iiii"));
    }

    // --- Whole word only ---

    @Test
    public void neverMatchesInsideALongerWord() {
        final WordFilter filter = filterOf("ass");
        assertTrue(filter.isBlocked("ass"));
        assertTrue(filter.isBlocked("Ass"));
        assertFalse(filter.isBlocked("class"));
        assertFalse(filter.isBlocked("assignment"));
        assertFalse(filter.isBlocked("pass"));
        assertFalse(filter.isBlocked("assess"));
        assertFalse(filter.isBlocked("bass"));
        assertFalse(filter.isBlocked("asses"));
    }

    // --- Wildcards ---

    @Test
    public void wildcardMatchesWordsStartingWithTheStem() {
        final WordFilter filter = filterOf("word*");
        assertTrue(filter.isBlocked("word"));
        assertTrue(filter.isBlocked("words"));
        assertTrue(filter.isBlocked("wording"));
        assertTrue(filter.isBlocked("WORDSMITH"));
        assertFalse(filter.isBlocked("sword"));
        assertFalse(filter.isBlocked("wor"));
        assertFalse(filter.isBlocked("wrd"));
    }

    @Test
    public void wildcardIsOptInPerEntry() {
        final WordFilter filter = filterOf("word*", "ass");
        assertTrue(filter.isBlocked("wordy"));
        assertFalse(filter.isBlocked("assess"));
    }

    @Test
    public void wildcardStemIsNormalized() {
        final WordFilter filter = filterOf("W0rd*");
        assertTrue(filter.isBlocked("w\u00f3rds"));
        assertTrue(filter.isBlocked("word's"));
    }

    @Test
    public void wildcardWithSeveralStemLengths() {
        final WordFilter filter = filterOf("ab*", "abcdef*");
        assertTrue(filter.isBlocked("abz"));
        assertTrue(filter.isBlocked("abcdefgh"));
        assertFalse(filter.isBlocked("a"));
        assertFalse(filter.isBlocked("zab"));
    }

    // --- Instant entries ---

    @Test
    public void instantEntriesAreBlockedAndInstant() {
        final WordFilter filter = filterOf("word!");
        assertTrue(filter.isBlocked("word"));
        assertTrue(filter.isInstant("word"));
        assertTrue(filter.isInstant("W0RD"));
        assertTrue(filter.isBlocked("Word"));
    }

    @Test
    public void nonInstantEntriesAreNotInstant() {
        final WordFilter filter = filterOf("word", "other*");
        assertTrue(filter.isBlocked("word"));
        assertFalse(filter.isInstant("word"));
        assertTrue(filter.isBlocked("others"));
        assertFalse(filter.isInstant("others"));
    }

    @Test
    public void instantIsExactMatchOnly() {
        final WordFilter filter = filterOf("word!");
        assertFalse(filter.isInstant("words"));
        assertFalse(filter.isInstant("sword"));
        assertFalse(filter.isBlocked("words"));
        assertFalse(filter.isInstant(""));
        assertFalse(filter.isInstant(null));
    }

    @Test
    public void instantMarkerIsNotPartOfTheWord() {
        // "word!" must not require the user to type an exclamation mark.
        assertTrue(filterOf("word!").isBlocked("word"));
        assertTrue(filterOf("  word !  ").isInstant("word"));
    }

    @Test
    public void hasInstantEntries() {
        assertFalse(filterOf("word", "other*").hasInstantEntries());
        assertTrue(filterOf("word", "other!").hasInstantEntries());
        assertFalse(new WordFilter(Collections.<String>emptyList()).hasInstantEntries());
        // Instant entries that cannot match anything do not count.
        assertFalse(filterOf("two words!").hasInstantEntries());
    }

    // --- List hygiene ---

    @Test
    public void emptyListDoesNothing() {
        final WordFilter filter = new WordFilter(Collections.<String>emptyList());
        assertTrue(filter.isEmpty());
        assertFalse(filter.isBlocked("word"));
        assertFalse(filter.isInstant("word"));
        assertEquals(0, filter.maxEntryLength());
        assertTrue(new WordFilter(null).isEmpty());
    }

    @Test
    public void blankLinesAndNullsAreSkipped() {
        final WordFilter filter = new WordFilter(Arrays.asList("", "   ", "\t", null, "\u00a0"));
        assertTrue(filter.isEmpty());
        assertFalse(filter.isBlocked("word"));
    }

    @Test
    public void whitespaceIsTrimmed() {
        final WordFilter filter = filterOf("  word\t", "\u00a0other\u00a0");
        assertTrue(filter.isBlocked("word"));
        assertTrue(filter.isBlocked("other"));
    }

    @Test
    public void duplicatesAreHarmless() {
        final WordFilter filter = filterOf("word", "WORD", "word", "w0rd");
        assertTrue(filter.isBlocked("word"));
        assertEquals(4, filter.maxEntryLength());
    }

    @Test
    public void unusableEntriesAreIgnored() {
        final WordFilter filter = filterOf("two words", "*", "!", "a-b", "x.y", "word!*");
        assertTrue(filter.isEmpty());
        assertFalse(filter.isBlocked("words"));
        assertFalse(filter.isBlocked("two"));
    }

    @Test
    public void isValidEntry() {
        assertTrue(WordFilter.isValidEntry("word"));
        assertTrue(WordFilter.isValidEntry("word*"));
        assertTrue(WordFilter.isValidEntry("word!"));
        assertTrue(WordFilter.isValidEntry(" don't "));
        assertFalse(WordFilter.isValidEntry(""));
        assertFalse(WordFilter.isValidEntry("   "));
        assertFalse(WordFilter.isValidEntry("*"));
        assertFalse(WordFilter.isValidEntry("!"));
        assertFalse(WordFilter.isValidEntry("two words"));
        assertFalse(WordFilter.isValidEntry("a-b"));
        assertFalse(WordFilter.isValidEntry("'"));
        assertFalse(WordFilter.isValidEntry(null));
    }

    @Test
    public void maxEntryLengthTracksNormalizedLongestEntry() {
        assertEquals(6, filterOf("ab", "abcdef", "abc*").maxEntryLength());
        assertEquals(4, filterOf("don't").maxEntryLength());
    }

    @Test
    public void blockedChecksTolerateEmptyAndNull() {
        final WordFilter filter = filterOf("word");
        assertFalse(filter.isBlocked(null));
        assertFalse(filter.isBlocked(""));
        assertFalse(filter.isBlocked("'"));
    }

    @Test
    public void manyEntries() {
        final List<String> entries = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            entries.add("entry" + i + "x");
        }
        final WordFilter filter = new WordFilter(entries);
        assertTrue(filter.isBlocked("entry4999x"));
        assertFalse(filter.isBlocked("entry5000x"));
    }

    // --- trailingWordLength ---

    @Test
    public void trailingWordLengthOfEmptyOrNull() {
        assertEquals(0, WordFilter.trailingWordLength(""));
        assertEquals(0, WordFilter.trailingWordLength(null));
    }

    @Test
    public void trailingWordLengthWithOnlySeparators() {
        assertEquals(0, WordFilter.trailingWordLength(" "));
        assertEquals(0, WordFilter.trailingWordLength(" ,.!?-\n\t"));
    }

    @Test
    public void trailingWordLengthAtEnd() {
        assertEquals(5, WordFilter.trailingWordLength("hello"));
        assertEquals(5, WordFilter.trailingWordLength("hi hello"));
        assertEquals(5, WordFilter.trailingWordLength("say, hello"));
        assertEquals(5, WordFilter.trailingWordLength("a-hello"));
        assertEquals(5, WordFilter.trailingWordLength("line one\nhello"));
    }

    @Test
    public void trailingWordLengthIsZeroWhenTextEndsInASeparator() {
        assertEquals(0, WordFilter.trailingWordLength("hello "));
        assertEquals(0, WordFilter.trailingWordLength("hello."));
        assertEquals(0, WordFilter.trailingWordLength("hello\n"));
    }

    @Test
    public void trailingWordLengthCountsDigitsAndApostrophes() {
        assertEquals(5, WordFilter.trailingWordLength("don't"));
        assertEquals(5, WordFilter.trailingWordLength("don\u2019t"));
        assertEquals(4, WordFilter.trailingWordLength("say w0rd"));
        assertEquals(4, WordFilter.trailingWordLength("a 1337"));
        assertEquals(7, WordFilter.trailingWordLength("it's a 'quoted"));
    }

    @Test
    public void trailingWordLengthWithEmojiBeforeTheWord() {
        // U+1F600 is one code point but two UTF-16 units; it is not a word character.
        assertEquals(4, WordFilter.trailingWordLength("\ud83d\ude00word"));
        assertEquals(4, WordFilter.trailingWordLength("hi \ud83d\ude00\ud83d\ude00word"));
    }

    @Test
    public void trailingWordLengthWithEmojiAtTheEnd() {
        assertEquals(0, WordFilter.trailingWordLength("word\ud83d\ude00"));
    }

    @Test
    public void trailingWordLengthWithLoneSurrogate() {
        // A window cut through the middle of a surrogate pair must not be counted or crash.
        assertEquals(0, WordFilter.trailingWordLength("\uDE00"));
        assertEquals(4, WordFilter.trailingWordLength("\uDE00word"));
        assertEquals(0, WordFilter.trailingWordLength("\uD83D"));
    }

    @Test
    public void trailingWordLengthCountsSupplementaryLettersInUtf16Units() {
        // U+10428 DESERET SMALL LETTER LONG I is a letter outside the BMP: 2 UTF-16 units.
        final String letter = new String(Character.toChars(0x10428));
        assertEquals(2, letter.length());
        assertEquals(6, WordFilter.trailingWordLength("x " + letter + "ab" + letter));
    }

    @Test
    public void trailingWordLengthForNonLatinScripts() {
        assertEquals(6, WordFilter.trailingWordLength("\u043f\u0440\u0438\u0432\u0435\u0442"));
        assertEquals(3, WordFilter.trailingWordLength("x \u05e9\u05dc\u05d5"));
    }

    @Test
    public void trailingWordLengthDrivesDeletionOfTheRightSlice() {
        final String text = "so \ud83d\ude00 tell s\u00f3rd";
        final int length = WordFilter.trailingWordLength(text);
        assertEquals(4, length);
        assertEquals("s\u00f3rd", text.substring(text.length() - length));
    }
}
