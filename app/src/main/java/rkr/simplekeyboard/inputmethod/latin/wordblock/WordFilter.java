/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin.wordblock;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides whether a typed word is on the user's blocked list.
 *
 * This class is pure Java (no Android imports) so it can be unit tested on the JVM. Instances are
 * immutable, so they can be shared between threads and swapped out wholesale when the list changes.
 *
 * <h3>List entry syntax</h3>
 * One entry per line:
 * <ul>
 * <li>{@code word}  - blocks exactly that word (whole word only).</li>
 * <li>{@code word*} - blocks any word that starts with {@code word}.</li>
 * <li>{@code word!} - blocks exactly that word, and also marks it "instant" so it can be removed
 *     as soon as it has been typed instead of waiting for a separator.</li>
 * <li>{@code two words} - blocks that phrase: any number of words separated by whitespace, and
 *     written with whatever gap (spaces, punctuation, line breaks) between them. See
 *     {@link PhraseSet}. {@code *} and {@code !} apply to the last word.</li>
 * </ul>
 * Entries that cannot ever match what is typed (blank, or containing anything other than
 * letters, digits, apostrophes, underscores, {@code @}, {@code $} and, for a phrase, the
 * whitespace between its words, once the marker is removed, or having no letter or digit at all)
 * are ignored.
 *
 * <h3>Normalization</h3>
 * Typed words and list entries go through the same pipeline: NFKD with combining marks removed,
 * lower-casing, leet substitution and apostrophe removal. See {@link #variantsOf}.
 */
public final class WordFilter {
    /** Upper bound on the number of leet variants generated for one word. */
    static final int MAX_VARIANTS = 8;

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private final Set<String> mWholeWords = new HashSet<>();
    private final Set<String> mInstantWords = new HashSet<>();
    private final Set<String> mStems = new HashSet<>();
    private final PhraseSet mPhrases = new PhraseSet();
    private int mMinStemLength = Integer.MAX_VALUE;
    private int mMaxStemLength = 0;
    private int mMaxEntryLength = 0;

    public WordFilter(final Collection<String> rawEntries) {
        if (rawEntries == null) {
            return;
        }
        for (final String rawEntry : rawEntries) {
            final Entry entry = Entry.parse(rawEntry);
            if (entry == null) {
                continue;
            }
            if (entry.mPhraseWords != null) {
                final List<Set<String>> words = new ArrayList<>();
                for (final String word : entry.mPhraseWords) {
                    words.add(variantsOf(word));
                }
                mPhrases.add(words, entry.mWildcard, entry.mInstant);
                continue;
            }
            for (final String variant : variantsOf(entry.mBody)) {
                mMaxEntryLength = Math.max(mMaxEntryLength, variant.length());
                if (entry.mWildcard) {
                    mStems.add(variant);
                    mMinStemLength = Math.min(mMinStemLength, variant.length());
                    mMaxStemLength = Math.max(mMaxStemLength, variant.length());
                } else {
                    mWholeWords.add(variant);
                    if (entry.mInstant) {
                        mInstantWords.add(variant);
                    }
                }
            }
        }
    }

    /**
     * @return whether {@code rawEntry} would produce a usable list entry. Intended for input
     * validation in the UI.
     */
    public static boolean isValidEntry(final String rawEntry) {
        return Entry.parse(rawEntry) != null;
    }

    /** @return true if there is nothing to match against, so callers can skip all work. */
    public boolean isEmpty() {
        return mWholeWords.isEmpty() && mStems.isEmpty() && mPhrases.isEmpty();
    }

    /**
     * Cleans up an entry the way it is stored: surrounding whitespace removed, and each run of
     * whitespace inside it (which separates the words of a phrase) made one plain space.
     */
    public static String tidyEntry(final String rawEntry) {
        if (rawEntry == null) {
            return null;
        }
        final String trimmed = trimWhitespace(rawEntry);
        final StringBuilder tidy = new StringBuilder(trimmed.length());
        boolean pendingSpace = false;
        for (int i = 0; i < trimmed.length(); i++) {
            final char c = trimmed.charAt(i);
            if (isWhitespace(c)) {
                pendingSpace = true;
                continue;
            }
            if (pendingSpace) {
                tidy.append(' ');
                pendingSpace = false;
            }
            tidy.append(c);
        }
        return tidy.toString();
    }

    /**
     * @param word a single word, as typed (not normalized).
     * @return true if the whole word matches a list entry, or starts with a wildcard entry's stem.
     */
    public boolean isBlocked(final CharSequence word) {
        if (isEmpty() || word == null || word.length() == 0) {
            return false;
        }
        for (final CharSequence candidate : candidates(word)) {
            for (final String variant : variantsOf(candidate)) {
                if (variant.isEmpty()) {
                    continue;
                }
                if (mWholeWords.contains(variant)) {
                    return true;
                }
                final int longest = Math.min(variant.length(), mMaxStemLength);
                for (int length = longest; length >= mMinStemLength; length--) {
                    if (mStems.contains(variant.substring(0, length))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * @param word a single word, as typed (not normalized).
     * @return true if the whole word matches an entry that was marked with {@code !}.
     */
    public boolean isInstant(final CharSequence word) {
        if (mInstantWords.isEmpty() || word == null || word.length() == 0) {
            return false;
        }
        for (final CharSequence candidate : candidates(word)) {
            for (final String variant : variantsOf(candidate)) {
                if (mInstantWords.contains(variant)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Finds the blocked word at the end of a run of word characters, as read back from the editor.
     *
     * A run that contains underscores, such as {@code my_word_list}, is looked at from each of its
     * underscore-bounded endings in turn (the whole run, then {@code word_list}, then
     * {@code list}), longest first, so that both {@code snake_case} as a single entry and a plain
     * {@code word} after an underscore are found. Only the matching ending is meant to be deleted,
     * so what comes before the underscore stays, as it did before underscores were word characters.
     *
     * @param run the run of word characters ending at the cursor.
     * @return how many UTF-16 units at the end of the run are a blocked word, or 0 if none.
     */
    public int blockedLength(final CharSequence run) {
        if (isEmpty() || run == null || run.length() == 0) {
            return 0;
        }
        for (final int start : endingStarts(run)) {
            if (isBlocked(start == 0 ? run : run.subSequence(start, run.length()))) {
                return run.length() - start;
            }
        }
        return 0;
    }

    /** Like {@link #blockedLength}, for entries marked with {@code !}. */
    public int instantLength(final CharSequence run) {
        if (mInstantWords.isEmpty() || run == null || run.length() == 0) {
            return 0;
        }
        for (final int start : endingStarts(run)) {
            if (isInstant(start == 0 ? run : run.subSequence(start, run.length()))) {
                return run.length() - start;
            }
        }
        return 0;
    }

    /** A blocked word found around the cursor: how many UTF-16 units to delete on each side of it. */
    public static final class Hit {
        public final int before;
        public final int after;

        Hit(final int before, final int after) {
            this.before = before;
            this.after = after;
        }
    }

    /**
     * Finds the blocked word or phrase at the end of the text before the cursor: what to delete
     * when a separator is about to be typed there. This is {@link #blockedLength} for the last word
     * of the text, and also looks for a phrase that ends at the cursor. If both a word and a phrase
     * match, the phrase (which includes the word) is what to delete.
     *
     * @param textBeforeCursor the text just before the cursor, which must not start in the middle
     * of a word (see {@link #dropPartialStart}).
     * @return how many UTF-16 units at the end of the text are blocked, or 0 if none are.
     */
    public int blockedLengthBefore(final CharSequence textBeforeCursor) {
        if (isEmpty() || textBeforeCursor == null) {
            return 0;
        }
        final int length = textBeforeCursor.length();
        final int run = trailingWordLength(textBeforeCursor);
        final int word = run == 0 ? 0 : blockedLength(textBeforeCursor.subSequence(length - run, length));
        return Math.max(word, mPhrases.lengthBefore(textBeforeCursor, false));
    }

    /** Like {@link #blockedLengthBefore}, for entries marked with {@code !}. */
    public int instantLengthBefore(final CharSequence textBeforeCursor) {
        if (!hasInstantEntries() || textBeforeCursor == null) {
            return 0;
        }
        final int length = textBeforeCursor.length();
        final int run = trailingWordLength(textBeforeCursor);
        final int word = run == 0 ? 0 : instantLength(textBeforeCursor.subSequence(length - run, length));
        return Math.max(word, mPhrases.lengthBefore(textBeforeCursor, true));
    }

    /** The longest run of word characters that {@link #blockedSpans} looks into for words. */
    private static final int MAX_SCANNED_RUN = 256;

    /**
     * Finds every blocked word and phrase in a block of text, such as text that was pasted. A word
     * is judged as in {@link #blockedLength}: whole, and also as an underscore-bounded part of a run
     * ({@code word} in {@code my_word_list}). Where matches overlap, the widest is kept.
     *
     * @return the [start, end) spans of the matches, in order and not overlapping. The text must
     * not start or end in the middle of a word.
     */
    public List<int[]> blockedSpans(final CharSequence text) {
        final List<int[]> found = new ArrayList<>();
        if (isEmpty() || text == null) {
            return found;
        }
        final int length = text.length();
        int i = 0;
        while (i < length) {
            int codePoint = Character.codePointAt(text, i);
            if (!isWordCodePoint(codePoint)) {
                i += Character.charCount(codePoint);
                continue;
            }
            final int start = i;
            while (i < length && isWordCodePoint(Character.codePointAt(text, i))) {
                i += Character.charCount(Character.codePointAt(text, i));
            }
            if (i - start <= MAX_SCANNED_RUN) {
                addWordSpans(text.subSequence(start, i).toString(), start, found);
            }
        }
        mPhrases.spans(text, found);

        // Widest first, keeping a span only if it does not overlap one already kept.
        found.sort((a, b) -> a[1] - a[0] != b[1] - b[0] ? (b[1] - b[0]) - (a[1] - a[0]) : a[0] - b[0]);
        final List<int[]> kept = new ArrayList<>();
        for (final int[] span : found) {
            boolean overlaps = false;
            for (final int[] other : kept) {
                if (span[0] < other[1] && other[0] < span[1]) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                kept.add(span);
            }
        }
        kept.sort((a, b) -> a[0] - b[0]);
        return kept;
    }

    private void addWordSpans(final String run, final int offset, final List<int[]> out) {
        final List<Integer> starts = new ArrayList<>();
        final List<Integer> ends = new ArrayList<>();
        starts.add(0);
        ends.add(run.length());
        for (int i = 0; i < run.length(); i++) {
            if (run.charAt(i) != '_') {
                continue;
            }
            if (i + 1 < run.length()) {
                starts.add(i + 1);
            }
            if (i > 0) {
                ends.add(i);
            }
        }
        for (final int start : starts) {
            for (final int end : ends) {
                if (end > start && isBlocked(run.substring(start, end))) {
                    out.add(new int[] {offset + start, offset + end});
                }
            }
        }
    }

    /**
     * Drops the run of word characters at the start of text read back from the editor, which the
     * edge of the window may have cut in half. Only for text that fills its window: if the text
     * ends before the window does, its first word is whole.
     *
     * @return the text from its first non-word character, or an empty text if it has none.
     */
    public static CharSequence dropPartialStart(final CharSequence text) {
        int start = 0;
        while (start < text.length()) {
            final int codePoint = Character.codePointAt(text, start);
            if (!isWordCodePoint(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        return text.subSequence(start, text.length());
    }

    /** Like {@link #dropPartialStart}, for the end of text read back from after the cursor. */
    public static CharSequence dropPartialEnd(final CharSequence text) {
        return text.subSequence(0, text.length() - trailingWordLength(text));
    }

    /**
     * Finds a blocked word or phrase that contains or touches the cursor, for text that was edited
     * inside or right next to a word that already had something after it. {@link #blockedLength}
     * only sees the end of a word, so it cannot tell that typing the {@code l} of {@code hel|o} has
     * just made {@code hello}, or that the {@code d} of {@code hello worl|d} has finished a phrase.
     *
     * For a single word, the run is the word characters before the cursor followed by the word
     * characters after it. As in {@link #blockedLength}, an underscore-bounded stretch of the run is
     * tried as a word of its own, but only one that reaches the cursor, so that a blocked word
     * elsewhere in the run is left as it was. A phrase is looked for among the words around the
     * cursor, and counts if it contains or touches the cursor. The widest match wins.
     *
     * @param before the text before the cursor, which must not start in the middle of a word.
     * @param after the text after the cursor, which must not end in the middle of a word.
     * @return what to delete, or null if nothing around the cursor is blocked.
     */
    public Hit blockedAround(final CharSequence before, final CharSequence after) {
        if (isEmpty() || before == null || after == null) {
            return null;
        }
        final int beforeRun = trailingWordLength(before);
        final Hit word = wordAround(before.subSequence(before.length() - beforeRun, before.length()),
                after.subSequence(0, leadingWordLength(after)));
        final Hit phrase = mPhrases.around(before, after);
        if (word == null || phrase == null) {
            return word == null ? phrase : word;
        }
        return phrase.before + phrase.after > word.before + word.after ? phrase : word;
    }

    private Hit wordAround(final CharSequence beforeRun, final CharSequence afterRun) {
        final int cursor = beforeRun.length();
        final String run = new StringBuilder(cursor + afterRun.length())
                .append(beforeRun).append(afterRun).toString();
        final int length = run.length();
        if (length == 0) {
            return null;
        }
        final List<Integer> starts = new ArrayList<>();
        final List<Integer> ends = new ArrayList<>();
        starts.add(0);
        ends.add(length);
        for (int i = 0; i < length; i++) {
            if (run.charAt(i) != '_') {
                continue;
            }
            if (i + 1 <= cursor && i + 1 < length) {
                starts.add(i + 1);
            }
            if (i >= cursor && i > 0) {
                ends.add(i);
            }
        }
        int bestStart = -1;
        int bestEnd = -1;
        for (final int start : starts) {
            for (final int end : ends) {
                if (end - start <= bestEnd - bestStart) {
                    continue;
                }
                if (isBlocked(run.substring(start, end))) {
                    bestStart = start;
                    bestEnd = end;
                }
            }
        }
        return bestStart < 0 ? null : new Hit(cursor - bestStart, bestEnd - cursor);
    }

    /** The positions where an underscore-bounded ending of the run starts, longest ending first. */
    private static int[] endingStarts(final CharSequence run) {
        int count = 1;
        for (int i = 0; i < run.length() - 1; i++) {
            if (run.charAt(i) == '_') {
                count++;
            }
        }
        final int[] starts = new int[count];
        int next = 1; // starts[0] is 0, the whole run.
        for (int i = 0; i < run.length() - 1; i++) {
            if (run.charAt(i) == '_') {
                starts[next++] = i + 1;
            }
        }
        return starts;
    }

    /**
     * The readings of a typed word that are worth comparing: the word itself and, if it starts or
     * ends with {@code @}, {@code $} or {@code _}, the word without those edge symbols. Inside a
     * word {@code @} and {@code $} stand for letters ({@code sh@t}), but at an edge all three are
     * far more likely to be a mention, a price or other punctuation ({@code @word}, {@code word$},
     * {@code _word_}), and must not let a blocked word through.
     */
    static List<CharSequence> candidates(final CharSequence word) {
        final List<CharSequence> result = new ArrayList<>(4);
        result.add(word);
        int start = 0;
        int end = word.length();
        while (start < end && isEdgeSymbol(word.charAt(start))) {
            start++;
        }
        while (end > start && isEdgeSymbol(word.charAt(end - 1))) {
            end--;
        }
        if (start == end) {
            return result; // Nothing but symbols.
        }
        if (start > 0) {
            result.add(word.subSequence(start, word.length()));
        }
        if (end < word.length()) {
            result.add(word.subSequence(0, end));
        }
        if (start > 0 && end < word.length()) {
            result.add(word.subSequence(start, end));
        }
        return result;
    }

    /** @return true if at least one entry is marked with {@code !}. */
    public boolean hasInstantEntries() {
        return !mInstantWords.isEmpty() || mPhrases.hasInstant();
    }

    /**
     * @return the length of the longest normalized entry, in chars, counting the widest gaps that
     * may separate the words of a phrase. Used to size the window of text that is read back from
     * the editor.
     */
    public int maxEntryLength() {
        return Math.max(mMaxEntryLength, mPhrases.maxLength());
    }

    /**
     * Word characters are letters, digits, apostrophes, underscores and the leet symbols {@code @}
     * and {@code $}, so that {@code snake_case} is one word and a disguised word such as
     * {@code w@rd} or {@code sh$t} is one word. Everything else separates words.
     */
    public static boolean isWordCodePoint(final int codePoint) {
        return Character.isLetterOrDigit(codePoint) || codePoint == '\'' || codePoint == '\u2019'
                || codePoint == '_' || isLeetSymbol(codePoint);
    }

    /**
     * @return true if typing this character completes the word before it, so that word should be
     * checked. That is every non-word character, and also the underscore: it is part of a word
     * (see {@link #isWordCodePoint}), but a blocked word in front of it, as in {@code word_list},
     * is still caught when the underscore is typed.
     */
    public static boolean endsWord(final int codePoint) {
        return !isWordCodePoint(codePoint) || codePoint == '_';
    }

    private static boolean isLeetSymbol(final int codePoint) {
        return codePoint == '@' || codePoint == '$';
    }

    /** Symbols that, at the edge of a word, are punctuation rather than part of it. */
    private static boolean isEdgeSymbol(final int codePoint) {
        return isLeetSymbol(codePoint) || codePoint == '_';
    }

    /**
     * @param textBeforeCursor the text immediately before the cursor, or null.
     * @return the length, in UTF-16 units (what {@code deleteSurroundingText} counts), of the run
     * of word characters that ends at the end of the text.
     */
    public static int trailingWordLength(final CharSequence textBeforeCursor) {
        if (textBeforeCursor == null) {
            return 0;
        }
        final int end = textBeforeCursor.length();
        int start = end;
        while (start > 0) {
            final int codePoint = Character.codePointBefore(textBeforeCursor, start);
            if (!isWordCodePoint(codePoint)) {
                break;
            }
            start -= Character.charCount(codePoint);
        }
        return end - start;
    }

    /**
     * @param textAfterCursor the text immediately after the cursor, or null.
     * @return the length, in UTF-16 units, of the run of word characters at the start of the text.
     */
    public static int leadingWordLength(final CharSequence textAfterCursor) {
        if (textAfterCursor == null) {
            return 0;
        }
        final int end = textAfterCursor.length();
        int index = 0;
        while (index < end) {
            final int codePoint = Character.codePointAt(textAfterCursor, index);
            if (!isWordCodePoint(codePoint)) {
                break;
            }
            index += Character.charCount(codePoint);
        }
        return index;
    }

    /**
     * Normalizes a word and returns every reading of it. All variants are equal except where the
     * digit {@code 1} occurs, which may stand for either {@code i} or {@code l}.
     *
     * Pipeline: NFKD and strip combining marks; lower-case ({@link Locale#ROOT}); leet mapping
     * ({@code 0->o, 1->i|l, 3->e, 4->a, 5->s, 7->t, @->a, $->s}); strip apostrophes.
     *
     * At most {@link #MAX_VARIANTS} variants are returned.
     */
    static Set<String> variantsOf(final CharSequence word) {
        String s = Normalizer.normalize(word, Normalizer.Form.NFKD);
        s = COMBINING_MARKS.matcher(s).replaceAll("");
        s = s.toLowerCase(Locale.ROOT);

        final List<StringBuilder> variants = new ArrayList<>();
        variants.add(new StringBuilder(s.length()));
        for (int i = 0; i < s.length(); ) {
            final int codePoint = s.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == '\'' || codePoint == '\u2019') {
                continue;
            }
            if (codePoint == '1') {
                final int existing = variants.size();
                for (int k = 0; k < existing && variants.size() < MAX_VARIANTS; k++) {
                    variants.add(new StringBuilder(variants.get(k)).append('l'));
                }
                for (int k = 0; k < existing; k++) {
                    variants.get(k).append('i');
                }
                continue;
            }
            final int mapped = leet(codePoint);
            for (final StringBuilder variant : variants) {
                variant.appendCodePoint(mapped);
            }
        }
        final Set<String> result = new LinkedHashSet<>();
        for (final StringBuilder variant : variants) {
            result.add(variant.toString());
        }
        return result;
    }

    private static int leet(final int codePoint) {
        switch (codePoint) {
            case '0': return 'o';
            case '3': return 'e';
            case '4': return 'a';
            case '5': return 's';
            case '7': return 't';
            case '@': return 'a';
            case '$': return 's';
            default: return codePoint;
        }
    }

    private static String trimWhitespace(final String s) {
        int start = 0;
        int end = s.length();
        while (start < end && isWhitespace(s.charAt(start))) {
            start++;
        }
        while (end > start && isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    private static boolean isWhitespace(final char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }

    /** One parsed line of the blocked list. */
    private static final class Entry {
        final String mBody;
        final boolean mWildcard;
        final boolean mInstant;
        /** The words of a phrase, or null for an entry that is a single word. */
        final List<String> mPhraseWords;

        private Entry(final String body, final boolean wildcard, final boolean instant,
                final List<String> phraseWords) {
            mBody = body;
            mWildcard = wildcard;
            mInstant = instant;
            mPhraseWords = phraseWords;
        }

        /** @return the parsed entry, or null if the line is blank or can never match a word. */
        static Entry parse(final String raw) {
            if (raw == null) {
                return null;
            }
            String body = trimWhitespace(raw);
            boolean wildcard = false;
            boolean instant = false;
            if (body.endsWith("*")) {
                wildcard = true;
                body = trimWhitespace(body.substring(0, body.length() - 1));
            } else if (body.endsWith("!")) {
                instant = true;
                body = trimWhitespace(body.substring(0, body.length() - 1));
            }
            if (body.isEmpty()) {
                return null;
            }
            if (containsWhitespace(body)) {
                return parsePhrase(body, wildcard, instant);
            }
            // Normalization only changes letters into other letters and drops apostrophes, so any
            // character left over that is not a letter, digit or underscore means the entry could
            // never match a typed word. At least one letter or digit is needed: an entry made of
            // underscores alone would match every run that happens to contain one.
            final Set<String> variants = variantsOf(body);
            for (final String variant : variants) {
                if (!isUsableBody(variant)) {
                    return null;
                }
            }
            return new Entry(body, wildcard, instant, null);
        }

        /**
         * A phrase is two or more words separated by whitespace. Underscores separate words too, as
         * in the text it is matched against (see {@link PhraseSet}), and each word must be made of
         * letters and digits only once normalized.
         */
        private static Entry parsePhrase(final String body, final boolean wildcard,
                final boolean instant) {
            final List<String> words = new ArrayList<>();
            final StringBuilder word = new StringBuilder();
            for (int i = 0; i <= body.length(); i++) {
                final char c = i < body.length() ? body.charAt(i) : ' ';
                if (c != '_' && !isWhitespace(c)) {
                    word.append(c);
                } else if (word.length() > 0) {
                    words.add(word.toString());
                    word.setLength(0);
                }
            }
            if (words.size() < 2) {
                return null;
            }
            final StringBuilder joined = new StringBuilder();
            for (final String each : words) {
                for (final String variant : variantsOf(each)) {
                    if (!isUsableWord(variant)) {
                        return null;
                    }
                }
                joined.append(joined.length() > 0 ? " " : "").append(each);
            }
            return new Entry(joined.toString(), wildcard, instant, words);
        }

        private static boolean containsWhitespace(final String s) {
            for (int i = 0; i < s.length(); i++) {
                if (isWhitespace(s.charAt(i))) {
                    return true;
                }
            }
            return false;
        }

        /** @return true if the (normalized) word is made of letters and digits, and not empty. */
        private static boolean isUsableWord(final String s) {
            if (s.isEmpty()) {
                return false;
            }
            for (int i = 0; i < s.length(); ) {
                final int codePoint = s.codePointAt(i);
                if (!Character.isLetterOrDigit(codePoint)) {
                    return false;
                }
                i += Character.charCount(codePoint);
            }
            return true;
        }

        private static boolean isUsableBody(final String s) {
            boolean hasLetterOrDigit = false;
            for (int i = 0; i < s.length(); ) {
                final int codePoint = s.codePointAt(i);
                if (Character.isLetterOrDigit(codePoint)) {
                    hasLetterOrDigit = true;
                } else if (codePoint != '_') {
                    return false;
                }
                i += Character.charCount(codePoint);
            }
            return hasLetterOrDigit;
        }
    }
}
