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
 * </ul>
 * Entries that cannot ever match a single typed word (blank, or containing anything other than
 * letters, digits, apostrophes, underscores, {@code @} and {@code $} once the marker is removed, or
 * having no letter or digit at all) are ignored.
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
        return mWholeWords.isEmpty() && mStems.isEmpty();
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
    private static List<CharSequence> candidates(final CharSequence word) {
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
        return !mInstantWords.isEmpty();
    }

    /**
     * @return the length of the longest normalized entry, in chars. Used to size the window of
     * text that is read back from the editor.
     */
    public int maxEntryLength() {
        return mMaxEntryLength;
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

        private Entry(final String body, final boolean wildcard, final boolean instant) {
            mBody = body;
            mWildcard = wildcard;
            mInstant = instant;
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
            return new Entry(body, wildcard, instant);
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
