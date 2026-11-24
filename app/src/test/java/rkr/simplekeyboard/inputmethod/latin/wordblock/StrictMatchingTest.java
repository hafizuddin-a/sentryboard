package rkr.simplekeyboard.inputmethod.latin.wordblock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public class StrictMatchingTest {
    private static CommonWords sWords;

    @BeforeClass
    public static void loadDictionary() throws Exception {
        // Gradle runs unit tests from the module directory.
        sWords = CommonWords.fromBytes(
                Files.readAllBytes(new File("src/main/res/raw/common_words.bin").toPath()));
        assertFalse("the dictionary asset must load", sWords.isEmpty());
    }

    private static WordFilter filterOf(final String... entries) {
        return new WordFilter(Arrays.asList(entries), sWords);
    }

    private static String strict(final WordFilter filter, final String text) {
        final StringBuilder sb = new StringBuilder();
        for (final int[] span : filter.strictSpans(text)) {
            sb.append(sb.length() > 0 ? "|" : "").append(text, span[0], span[1]);
        }
        return sb.toString();
    }

    // --- the dictionary ---

    @Test
    public void commonWordsKnowsOrdinaryWordsAndTheirEndings() {
        for (final String word : new String[] {"hello", "hell", "help", "the", "kills", "killed",
                "killing", "quickly", "bigger", "running", "tries", "cities", "hopped"}) {
            assertTrue(word, sWords.isWord(word));
        }
        for (final String word : new String[] {"helo", "wrld", "heeello", "xqzv", "a", ""}) {
            assertFalse(word, sWords.isWord(word));
        }
    }

    @Test
    public void commonWordsWithNoDataKnowsNothing() {
        assertTrue(CommonWords.NONE.isEmpty());
        assertFalse(CommonWords.NONE.isWord("hello"));
        assertTrue(CommonWords.fromBytes(null).isEmpty());
        assertTrue(CommonWords.fromBytes(new byte[3]).isEmpty());
        // A header that promises more bits than the data holds is refused.
        assertTrue(CommonWords.fromBytes(new byte[] {5, 0, 0, 100, 0, 0}).isEmpty());
        assertFalse(CommonWords.fromBytes(new byte[] {5, 0, 0, 0, 8, 0}).isEmpty());
    }

    // --- helpers ---

    @Test
    public void squashKeepsLettersAndDigitsOnly() {
        assertEquals("hello2", FuzzyMatcher.squash("h.e-l l_o 2!"));
        assertEquals("", FuzzyMatcher.squash(" ._-"));
    }

    @Test
    public void foldMergesRepeatedLettersButNotDigits() {
        assertEquals("helo", FuzzyMatcher.fold("heeello"));
        assertEquals("helo", FuzzyMatcher.fold("hello"));
        assertEquals("a66", FuzzyMatcher.fold("aa66"));
    }

    @Test
    public void withinEditsCountsSubstitutionInsertionDeletionAndSwap() {
        assertTrue(FuzzyMatcher.withinEdits("hello", "hallo", 1));
        assertTrue(FuzzyMatcher.withinEdits("hello", "helo", 1));
        assertTrue(FuzzyMatcher.withinEdits("hello", "helllo", 1));
        assertTrue(FuzzyMatcher.withinEdits("hello", "hlelo", 1));
        assertFalse(FuzzyMatcher.withinEdits("hello", "hxllx", 1));
        assertTrue(FuzzyMatcher.withinEdits("hello", "hxllx", 2));
        assertFalse(FuzzyMatcher.withinEdits("hello", "he", 2));
    }

    // --- disguises that are caught ---

    @Test
    public void aSplitPhraseIsCaught() {
        final WordFilter filter = filterOf("hello world");
        assertEquals("h ello world", strict(filter, "h ello world"));
        assertEquals("hello wor ld", strict(filter, "say hello wor ld now"));
        assertEquals("hel lo wor ld", strict(filter, "hel lo wor ld"));
        assertEquals("hello  world", strict(filter, "hello  world"));
    }

    @Test
    public void separatedLettersAreCaught() {
        final WordFilter filter = filterOf("hello world", "kill");
        assertEquals("h.e.l.l.o w.o.r.l.d", strict(filter, "h.e.l.l.o w.o.r.l.d"));
        assertEquals("h e l l o", strict(filterOf("hello"), "h e l l o"));
        assertEquals("k i l l", strict(filter, "a k i l l b"));
    }

    @Test
    public void repeatedLettersAreCaught() {
        final WordFilter filter = filterOf("hello");
        assertEquals("heeello", strict(filter, "heeello"));
        assertEquals("hhhello", strict(filter, "hhhello"));
        assertEquals("helllllo", strict(filter, "oh helllllo"));
    }

    @Test
    public void aTypoIsCaughtForLongEntries() {
        final WordFilter filter = filterOf("gambling");
        assertEquals("gamblng", strict(filter, "gamblng"));
        assertEquals("gamblinq", strict(filter, "gamblinq"));
        assertEquals("gmabling", strict(filter, "gmabling"));
        assertEquals("gambbling", strict(filter, "gambbling"));
    }

    @Test
    public void aTypoOfAPhraseIsCaught() {
        final WordFilter filter = filterOf("hello world");
        assertEquals("helo wrld", strict(filter, "helo wrld"));
        assertEquals("hello wrld", strict(filter, "hello wrld"));
        assertEquals("hellp world", strict(filter, "hellp world"));
    }

    @Test
    public void leetAndLookalikesStillWorkInsideStrictMatching() {
        assertEquals("h 3llo", strict(filterOf("hello"), "h 3llo"));
        assertEquals("h еllo", strict(filterOf("hello"), "h еllo"));
    }

    @Test
    public void invisibleCharactersBetweenWordsAreSeenThrough() {
        assertEquals(1, filterOf("hello world").strictSpans("h​ello wor​ld").size());
        assertEquals(1, filterOf("hello world").strictSpans("hello​world").size());
    }

    // --- innocent text that must be left alone ---

    @Test
    public void realWordsNextToABlockedWordAreNotBlocked() {
        final WordFilter filter = filterOf("hello", "gambling", "kidnap");
        for (final String text : new String[] {"hell", "help", "hull", "hallo", "he", "skill", "killing"}) {
            assertEquals(text, "", strict(filter, text));
        }
        assertEquals("", strict(filterOf("hello", "kill"), "kills killed killing skill"));
    }

    @Test
    public void aTypoIsNotAppliedToShortEntries() {
        final WordFilter filter = filterOf("hello", "kill", "lamp");
        assertEquals("", strict(filter, "hellp"));
        assertEquals("", strict(filter, "helo"));
        assertEquals("", strict(filter, "kil"));
        assertEquals("", strict(filter, "lapm"));
    }

    @Test
    public void twoOrdinaryWordsAreNotAJoinedEntry() {
        assertEquals("", strict(filterOf("also"), "al so"));
        assertEquals("", strict(filterOf("also"), "al sort"));
        assertEquals("", strict(filterOf("hello"), "she llo"));
        assertEquals("", strict(filterOf("hello"), "the llama world"));
        assertEquals("", strict(filterOf("hotdog"), "hot dog"));
        assertEquals("", strict(filterOf("hotdog"), "hot dogs"));
        assertEquals("", strict(filterOf("hotdog"), "not dog"));
    }

    @Test
    public void ordinarySentencesAreNotBlocked() {
        final WordFilter filter = filterOf("hello world", "kill", "also", "casino", "gambling",
                "kidnap", "snake_case");
        final String[] sentences = {
                "I will see you at the market tomorrow",
                "Can you analyse the data and tell me what it means",
                "She went to a casing factory and a skill centre",
                "We cooked al dente pasta over a gambit of chess",
                "Please help the hell out of this problem, thanks",
                "Hello there, what a lovely world this is",
                "The snake case style is used in some languages",
                "Meet me at 5 pm near the old mill by the river"};
        for (final String sentence : sentences) {
            final List<int[]> spans = filter.strictSpans(sentence);
            final List<int[]> exact = filter.blockedSpans(sentence);
            assertEquals(sentence, exact.size(), spans.size());
        }
    }

    // --- how it plugs into the filter ---

    @Test
    public void strictMatchingIncludesEverythingTheExactMatchingFinds() {
        final WordFilter filter = filterOf("hello", "hello world");
        assertEquals("hello world", strict(filter, "say hello world"));
        assertEquals("hello", strict(filter, "say hello now"));
    }

    @Test
    public void wildcardEntriesAreNotMatchedStrictly() {
        assertEquals("", strict(filterOf("hello*"), "h ello"));
    }

    @Test
    public void strictBlockedLengthBeforeFindsADisguisedEntryEndingAtTheCursor() {
        final WordFilter filter = filterOf("hello world");
        assertEquals(12, filter.strictBlockedLengthBefore("say h ello world"));
        assertEquals(0, filter.strictBlockedLengthBefore("h ello world now"));
        assertEquals(0, filter.strictBlockedLengthBefore("say"));
        assertEquals(0, filter.strictBlockedLengthBefore(null));
    }

    @Test
    public void strictBlockedAroundFindsADisguisedEntryTouchingTheCursor() {
        final WordFilter filter = filterOf("hello world");
        final WordFilter.Hit hit = filter.strictBlockedAround("h ello wor", "ld");
        assertNotNull(hit);
        assertEquals(10, hit.before);
        assertEquals(2, hit.after);
        assertNull(filter.strictBlockedAround("say ", "hi"));
    }

    @Test
    public void maxEntryLengthMakesRoomForSeparatedLetters() {
        assertTrue(filterOf("hello world").maxReadLength() >= 10 * 2 + FuzzyMatcher.MAX_GAP);
        assertEquals(4, filterOf("word").maxEntryLength());
    }

    @Test
    public void withoutTheDictionaryNothingIsTreatedAsAnOrdinaryWord() {
        final WordFilter filter = new WordFilter(Arrays.asList("gambling"));
        assertEquals("gamblng", strict(filter, "gamblng"));
    }
}
