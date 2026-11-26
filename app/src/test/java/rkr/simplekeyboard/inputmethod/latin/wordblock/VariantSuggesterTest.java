package rkr.simplekeyboard.inputmethod.latin.wordblock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class VariantSuggesterTest {
    private static CommonWords sWords;

    @BeforeClass
    public static void loadDictionary() throws Exception {
        sWords = CommonWords.fromBytes(
                Files.readAllBytes(new File("src/main/res/raw/common_words.bin").toPath()));
    }

    private static List<String> suggest(final String entry, final String... listed) {
        return VariantSuggester.suggest(entry, new WordFilter(Arrays.asList(listed), sWords),
                sWords);
    }

    @Test
    public void aTwoWordEntryGetsJoinedReversedAndVowelLessForms() {
        assertEquals(Arrays.asList("amberkolt!", "kolt amber!", "koltamber!", "mbr klt!", "mbrklt!"),
                suggest("amber kolt!", "amber kolt!"));
    }

    @Test
    public void underscoreEntriesAreSplitIntoTheirWords() {
        assertEquals(Arrays.asList("snakecase", "case snake", "casesnake", "snk cs", "snkcs"),
                suggest("snake_case", "snake_case"));
    }

    @Test
    public void aSingleWordGetsItsVowelLessFormIncludingShortOnes() {
        assertEquals(Collections.singletonList("kmqt"), suggest("kumquat", "kumquat"));
        // The automatic rule leaves three consonants alone, so the suggestion is useful.
        assertEquals(Collections.singletonList("lmp"), suggest("lamp", "lamp"));
        assertEquals(Collections.singletonList("hll*"), suggest("hello*", "hello*"));
    }

    @Test
    public void formsThatWouldBlockTooMuchAreNotOffered() {
        assertTrue(suggest("eat", "eat").isEmpty());          // "t"
        assertTrue(suggest("audio", "audio").isEmpty());      // "d"
        assertTrue(suggest("rhythm", "rhythm").isEmpty());    // no vowels to drop
    }

    @Test
    public void formsAlreadyBlockedAreLeftOutUnlessTheListIsHidden() {
        assertEquals(Arrays.asList("amberkolt", "koltamber", "mbr klt", "mbrklt"),
                suggest("amber kolt", "amber kolt", "kolt amber"));
        // With the list hidden every form is offered, whatever is on it.
        assertEquals(5, VariantSuggester.suggest("amber kolt", null, sWords).size());
    }

    @Test
    public void unusableEntriesGetNoSuggestions() {
        assertTrue(VariantSuggester.suggest("a-b", null, sWords).isEmpty());
        assertTrue(VariantSuggester.suggest(null, null, sWords).isEmpty());
        assertTrue(VariantSuggester.suggest("  ", null, sWords).isEmpty());
    }
}
