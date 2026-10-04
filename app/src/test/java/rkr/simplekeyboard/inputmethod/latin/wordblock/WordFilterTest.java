package rkr.simplekeyboard.inputmethod.latin.wordblock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    // --- '@' and '$' are word characters, but edge symbols do not hide a word ---

    @Test
    public void leetSymbolsAreWordCharacters() {
        assertTrue(WordFilter.isWordCodePoint('@'));
        assertTrue(WordFilter.isWordCodePoint('$'));
        assertFalse(WordFilter.isWordCodePoint('#'));
        assertFalse(WordFilter.isWordCodePoint('-'));
        assertFalse(WordFilter.isWordCodePoint('.'));
    }

    @Test
    public void disguisedWordsWithSymbolsInsideAreCaught() {
        assertTrue(filterOf("cast").isBlocked("c@$t"));
        assertTrue(filterOf("shat").isBlocked("sh@t"));
        assertTrue(filterOf("sword").isBlocked("$w0rd"));
        assertTrue(filterOf("pass").isBlocked("p@$$"));
        assertTrue(filterOf("ass").isBlocked("@$$"));
    }

    @Test
    public void edgeSymbolsDoNotLetABlockedWordThrough() {
        final WordFilter filter = filterOf("ass");
        assertTrue(filter.isBlocked("@ass"));
        assertTrue(filter.isBlocked("$ass"));
        assertTrue(filter.isBlocked("ass$"));
        assertTrue(filter.isBlocked("ass@"));
        assertTrue(filter.isBlocked("@ass$"));
        assertTrue(filter.isBlocked("$$ass@@"));
        assertTrue(filter.isBlocked("@ASS"));
    }

    @Test
    public void edgeSymbolsWorkForWildcardsAndInstantEntries() {
        assertTrue(filterOf("word*").isBlocked("@words"));
        assertTrue(filterOf("word*").isBlocked("wording$"));
        assertTrue(filterOf("xyz!").isInstant("@xyz"));
        assertTrue(filterOf("xyz!").isInstant("xyz$"));
        assertTrue(filterOf("xyz!").isBlocked("@xyz"));
    }

    @Test
    public void symbolsInsideALongerWordStillGuardAgainstFalsePositives() {
        final WordFilter filter = filterOf("ass");
        assertFalse(filter.isBlocked("cl@ss"));    // class
        assertFalse(filter.isBlocked("p@ss"));     // pass
        assertFalse(filter.isBlocked("@ssignment"));
        assertFalse(filter.isBlocked("user@ass"));  // one word, not "ass" after an address
        assertFalse(filter.isBlocked("a$$e$$"));
    }

    @Test
    public void wordMadeOnlyOfSymbolsIsNotBlockedByAnEmptyReading() {
        final WordFilter filter = filterOf("word");
        assertFalse(filter.isBlocked("@"));
        assertFalse(filter.isBlocked("$$"));
        assertFalse(filter.isBlocked("@$"));
    }

    @Test
    public void trailingWordLengthIncludesLeetSymbols() {
        assertEquals(4, WordFilter.trailingWordLength("say sh@t"));
        assertEquals(4, WordFilter.trailingWordLength("well, @ass"));
        assertEquals(2, WordFilter.trailingWordLength("costs $5"));
        assertEquals(1, WordFilter.trailingWordLength("hi @"));
        assertEquals(12, WordFilter.trailingWordLength("mail me@x.com user@example"));
        assertEquals(0, WordFilter.trailingWordLength("a # "));
        assertEquals(0, WordFilter.trailingWordLength("a@b "));
    }

    // --- Underscores ---

    @Test
    public void underscoreIsAWordCharacterThatAlsoEndsTheWordBeforeIt() {
        assertTrue(WordFilter.isWordCodePoint('_'));
        assertTrue(WordFilter.endsWord('_'));
        assertTrue(WordFilter.endsWord(' '));
        assertTrue(WordFilter.endsWord('.'));
        assertTrue(WordFilter.endsWord('-'));
        assertFalse(WordFilter.endsWord('a'));
        assertFalse(WordFilter.endsWord('7'));
        assertFalse(WordFilter.endsWord('@'));
        assertFalse(WordFilter.endsWord('$'));
        assertFalse(WordFilter.endsWord('\''));
    }

    @Test
    public void entriesWithUnderscoresAreAccepted() {
        assertTrue(WordFilter.isValidEntry("snake_case"));
        assertTrue(WordFilter.isValidEntry("a_b_c"));
        assertTrue(WordFilter.isValidEntry("snake_case*"));
        assertTrue(WordFilter.isValidEntry("snake_case!"));
        assertTrue(WordFilter.isValidEntry("_word"));
        assertTrue(WordFilter.isValidEntry("word_"));
        assertTrue(WordFilter.isValidEntry("don't_stop"));
        assertTrue(WordFilter.isValidEntry("sn@ke_c4se"));
    }

    @Test
    public void anEntryNeedsAtLeastOneLetterOrDigit() {
        assertFalse(WordFilter.isValidEntry("_"));
        assertFalse(WordFilter.isValidEntry("___"));
        assertFalse(WordFilter.isValidEntry("_*"));
        assertFalse(WordFilter.isValidEntry("_!"));
        assertFalse(WordFilter.isValidEntry("'_'"));
        assertTrue(WordFilter.isValidEntry("_1_"));
    }

    @Test
    public void otherPunctuationInAnEntryIsStillRejected() {
        assertFalse(WordFilter.isValidEntry("snake-case"));
        assertFalse(WordFilter.isValidEntry("snake.case"));
        assertFalse(WordFilter.isValidEntry("snake case"));
        assertFalse(WordFilter.isValidEntry("snake_case,"));
    }

    @Test
    public void anUnderscoreEntryBlocksThatExactWord() {
        final WordFilter filter = filterOf("snake_case");
        assertTrue(filter.isBlocked("snake_case"));
        assertTrue(filter.isBlocked("Snake_Case"));
        assertTrue(filter.isBlocked("SN4KE_CASE")); // Leet still applies.
        assertFalse(filter.isBlocked("snake"));
        assertFalse(filter.isBlocked("case"));
        assertFalse(filter.isBlocked("snake_cases"));
        assertFalse(filter.isBlocked("snakecase"));
        assertFalse(filter.isBlocked("snake__case"));
    }

    @Test
    public void anUnderscoreWildcardMatchesWordsStartingWithTheStem() {
        final WordFilter filter = filterOf("snake_*");
        assertTrue(filter.isBlocked("snake_case"));
        assertTrue(filter.isBlocked("snake_"));
        assertFalse(filter.isBlocked("snake"));
        assertFalse(filter.isBlocked("snakes"));
    }

    @Test
    public void anUnderscoreInstantEntryIsInstant() {
        final WordFilter filter = filterOf("snake_case!");
        assertTrue(filter.isInstant("snake_case"));
        assertFalse(filter.isInstant("snake_cases"));
        assertTrue(filter.hasInstantEntries());
    }

    @Test
    public void trailingWordLengthCountsUnderscores() {
        assertEquals(10, WordFilter.trailingWordLength("say snake_case"));
        assertEquals(2, WordFilter.trailingWordLength("a_"));
        assertEquals(1, WordFilter.trailingWordLength("hi _"));
        assertEquals(0, WordFilter.trailingWordLength("snake_case "));
        assertEquals(3, WordFilter.trailingWordLength("a-b_c-d_e")); // The hyphen still separates.
    }

    @Test
    public void edgeUnderscoresDoNotLetABlockedWordThrough() {
        final WordFilter filter = filterOf("ass");
        assertTrue(filter.isBlocked("_ass"));
        assertTrue(filter.isBlocked("ass_"));
        assertTrue(filter.isBlocked("__ass__"));
        assertTrue(filter.isBlocked("_@ass$_"));
        assertFalse(filter.isBlocked("_"));
        assertFalse(filter.isBlocked("__"));
        assertFalse(filter.isBlocked("cl_ass"));
    }

    // --- blockedLength / instantLength ---

    @Test
    public void blockedLengthOfAPlainWordIsItsWholeLength() {
        final WordFilter filter = filterOf("word");
        assertEquals(4, filter.blockedLength("word"));
        assertEquals(4, filter.blockedLength("WORD"));
        assertEquals(0, filter.blockedLength("words"));
        assertEquals(0, filter.blockedLength(""));
        assertEquals(0, filter.blockedLength(null));
    }

    @Test
    public void blockedLengthFindsABlockedWordAfterAnUnderscore() {
        // Only the blocked ending is deleted; what comes before the underscore stays.
        final WordFilter filter = filterOf("word");
        assertEquals(4, filter.blockedLength("my_word"));
        assertEquals(4, filter.blockedLength("a_b_word"));
        assertEquals(0, filter.blockedLength("my_words"));
        assertEquals(0, filter.blockedLength("sword_x"));
    }

    @Test
    public void blockedLengthDoesNotLookInTheMiddleOfARun() {
        // A word followed by more underscore-joined text was already caught when the underscore
        // after it was typed, so only the ending of the run matters.
        assertEquals(0, filterOf("word").blockedLength("word_list"));
    }

    @Test
    public void blockedLengthPrefersTheLongestMatch() {
        final WordFilter both = filterOf("word", "my_word");
        assertEquals(7, both.blockedLength("my_word"));
        assertEquals(4, both.blockedLength("your_word"));
        assertEquals(9, filterOf("word", "your_word").blockedLength("your_word"));
    }

    @Test
    public void blockedLengthFindsAnUnderscoreEntryAfterAnotherUnderscore() {
        final WordFilter filter = filterOf("snake_case");
        assertEquals(10, filter.blockedLength("snake_case"));
        assertEquals(10, filter.blockedLength("my_snake_case"));
        assertEquals(0, filter.blockedLength("snake"));
        assertEquals(0, filter.blockedLength("snake_cases"));
    }

    @Test
    public void blockedLengthWithEdgeSymbolsCoversTheWholeRun() {
        final WordFilter filter = filterOf("word");
        assertEquals(5, filter.blockedLength("_word"));
        assertEquals(5, filter.blockedLength("word_"));
        assertEquals(8, filter.blockedLength("__word__"));
        assertEquals(5, filter.blockedLength("@word"));
        assertEquals(5, filter.blockedLength("my_@word")); // "my_" stays.
    }

    @Test
    public void blockedLengthWithAWildcardDeletesTheMatchingEnding() {
        final WordFilter filter = filterOf("word*");
        assertEquals(7, filter.blockedLength("my_wording"));
        assertEquals(0, filter.blockedLength("my_sword"));
        assertEquals(10, filterOf("snake_*").blockedLength("snake_case"));
    }

    @Test
    public void blockedLengthCountsSupplementaryCharactersInUtf16Units() {
        // U+10428 is a letter outside the BMP: 2 UTF-16 units.
        final String letter = new String(Character.toChars(0x10428));
        final WordFilter filter = filterOf("a" + letter);
        assertEquals(3, filter.blockedLength("a" + letter));
        assertEquals(3, filter.blockedLength("x_a" + letter));
    }

    @Test
    public void instantLengthFindsTheInstantEndingOnly() {
        final WordFilter filter = filterOf("xyz!", "plain");
        assertEquals(3, filter.instantLength("xyz"));
        assertEquals(3, filter.instantLength("my_xyz"));
        assertEquals(0, filter.instantLength("plain"));      // Blocked, but not instant.
        assertEquals(0, filter.instantLength("xyzz"));
        assertEquals(0, filter.instantLength(""));
        assertEquals(0, filterOf("word").instantLength("word"));
    }

    @Test
    public void blockedLengthOnAnEmptyFilterIsZero() {
        assertEquals(0, new WordFilter(Collections.<String>emptyList()).blockedLength("anything_at_all"));
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

    // --- leadingWordLength ---

    @Test
    public void leadingWordLengthStopsAtTheFirstSeparator() {
        assertEquals(5, WordFilter.leadingWordLength("hello world"));
        assertEquals(5, WordFilter.leadingWordLength("hello"));
        assertEquals(0, WordFilter.leadingWordLength(" hello"));
        assertEquals(0, WordFilter.leadingWordLength(""));
        assertEquals(0, WordFilter.leadingWordLength(null));
    }

    @Test
    public void leadingWordLengthCountsTheSameCharactersAsTrailingWordLength() {
        assertEquals(11, WordFilter.leadingWordLength("don't_sh@t$ x"));
        assertEquals(3, WordFilter.leadingWordLength("a_b-c"));
        final String letter = new String(Character.toChars(0x10428));
        assertEquals(4, WordFilter.leadingWordLength(letter + letter + "."));
    }

    // --- blockedAround ---

    private static void assertHit(final int before, final int after, final WordFilter.Hit hit) {
        assertTrue("expected a hit", hit != null);
        assertEquals(before, hit.before);
        assertEquals(after, hit.after);
    }

    @Test
    public void blockedAroundFindsAWordCompletedInTheMiddle() {
        final WordFilter filter = filterOf("hello");
        assertHit(4, 1, filter.blockedAround("hell", "o"));
        assertHit(3, 2, filter.blockedAround("hel", "lo"));
        assertHit(1, 4, filter.blockedAround("h", "ello"));
        assertHit(0, 5, filter.blockedAround("", "hello"));
        assertHit(5, 0, filter.blockedAround("hello", ""));
    }

    @Test
    public void blockedAroundLeavesWordsThatAreNotBlocked() {
        final WordFilter filter = filterOf("hello");
        assertNull(filter.blockedAround("helo", ""));
        assertNull(filter.blockedAround("hel", "o"));
        assertNull(filter.blockedAround("hell", "ox"));
        assertNull(filter.blockedAround("sh", "ello"));
        assertNull(filter.blockedAround("", ""));
        assertNull(filter.blockedAround(null, "hello"));
        assertNull(filter.blockedAround("hello", null));
        assertNull(filterOf().blockedAround("hello", ""));
    }

    @Test
    public void blockedAroundKeepsWholeWordMatchingForShortEntries() {
        final WordFilter filter = filterOf("ass");
        assertNull(filter.blockedAround("cl", "ass"));
        assertNull(filter.blockedAround("ass", "ignment"));
        assertNull(filter.blockedAround("a", "ssess"));
        assertHit(1, 2, filter.blockedAround("a", "ss"));
    }

    @Test
    public void blockedAroundHonoursWildcardsAndDisguises() {
        assertHit(3, 4, filterOf("hello*").blockedAround("hel", "lo's"));
        assertHit(3, 2, filterOf("hello").blockedAround("he1", "1o"));
        assertHit(2, 4, filterOf("hello").blockedAround("@h", "ello"));
        assertHit(3, 2, filterOf("word").blockedAround("@wo", "rd"));
        assertHit(2, 3, filterOf("shat").blockedAround("sh", "@t$"));
    }

    @Test
    public void blockedAroundOnlyDeletesTheUnderscoreBoundedWordAtTheCursor() {
        final WordFilter filter = filterOf("hello");
        assertHit(3, 2, filter.blockedAround("my_hel", "lo_you"));
        assertHit(3, 2, filter.blockedAround("my_hel", "lo"));
        assertHit(3, 2, filter.blockedAround("hel", "lo_you"));
        // A blocked word elsewhere in the run was not edited and is left alone.
        assertNull(filter.blockedAround("hello_wor", "ld"));
        assertNull(filter.blockedAround("hello_", "world"));
    }

    @Test
    public void blockedAroundTouchingAnUnderscoreStillCountsAsTheCursorWord() {
        final WordFilter filter = filterOf("hello");
        assertHit(5, 0, filter.blockedAround("hello", "_world"));
        assertHit(0, 5, filter.blockedAround("world_", "hello"));
    }

    @Test
    public void blockedAroundPrefersTheWidestBlockedStretch() {
        final WordFilter filter = filterOf("hello", "hello_world");
        assertHit(3, 8, filter.blockedAround("hel", "lo_world"));
        assertHit(8, 3, filter.blockedAround("hello_wo", "rld"));
    }

    @Test
    public void blockedAroundFindsEntriesThatContainUnderscores() {
        final WordFilter filter = filterOf("snake_case");
        assertHit(7, 3, filter.blockedAround("snake_c", "ase"));
        assertHit(2, 8, filter.blockedAround("sn", "ake_case"));
        assertNull(filter.blockedAround("snake_c", "asey"));
    }

    @Test
    public void blockedAroundWithNothingAfterTheCursorMatchesBlockedLength() {
        final WordFilter filter = filterOf("word", "snake_case", "hello*");
        final String[] runs = {"word", "my_word", "a_b_word", "snake_case", "my_snake_case",
                "hello", "helloes", "words", "sword", "@word", "word$", "_word_", "w0rd", "x"};
        for (final String run : runs) {
            final WordFilter.Hit hit = filter.blockedAround(run, "");
            final int length = filter.blockedLength(run);
            if (length == 0) {
                assertNull(run, hit);
            } else {
                assertHit(length, 0, hit);
            }
        }
    }

    @Test
    public void blockedAroundCopesWithLongUnderscoreRuns() {
        final StringBuilder many = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            many.append("a_");
        }
        assertNull(filterOf("hello").blockedAround(many.toString(), many.toString()));
    }
}
