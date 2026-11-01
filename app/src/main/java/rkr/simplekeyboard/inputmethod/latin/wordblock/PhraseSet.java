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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The phrases (entries of two or more words) on the blocked list, and the matching of them.
 *
 * Pure Java, immutable once built, like {@link WordFilter}, which owns one of these.
 *
 * <h3>Words and gaps</h3>
 * A text is read as a sequence of <em>tokens</em>, separated by <em>gaps</em>. A token is a run of
 * word characters other than the underscore ({@link WordFilter#isWordCodePoint}) that has a letter
 * or digit in it, so a lone {@code @} or {@code '} is not a token. A gap is anything in between:
 * spaces, line breaks, punctuation, underscores, such a lone symbol. Two words of a phrase match two
 * tokens whatever the gap between them is, so {@code hello,  world} and {@code hello_world} both
 * match {@code hello world}, as long as the gap is not longer than {@link #MAX_GAP}.
 *
 * Each word of a phrase is compared with a token the way a single entry is compared with a word:
 * both are normalized by {@link WordFilter#variantsOf} and a token also counts without its edge
 * symbols. A phrase ending in {@code *} lets its last word match any token that starts with it.
 * A phrase is never matched as a joined string, so a word with several readings costs one set
 * lookup, not a combination of all the readings of all the words.
 */
final class PhraseSet {
    /** The longest gap, in chars, that can separate two words of a phrase. */
    static final int MAX_GAP = 64;

    private final List<Phrase> mPhrases = new ArrayList<>();
    private boolean mHasInstant;
    private int mMaxLength;

    /**
     * @param words the normalized readings of each word of the phrase, in order (two or more).
     * @param wildcard whether the last word is only a stem.
     * @param instant whether the phrase may be removed as soon as it has been typed.
     */
    void add(final List<Set<String>> words, final boolean wildcard, final boolean instant) {
        mPhrases.add(new Phrase(words, wildcard, instant));
        mHasInstant |= instant;
        int length = (words.size() - 1) * MAX_GAP;
        for (final Set<String> readings : words) {
            int longest = 0;
            for (final String reading : readings) {
                longest = Math.max(longest, reading.length());
            }
            length += longest;
        }
        mMaxLength = Math.max(mMaxLength, length);
    }

    boolean isEmpty() {
        return mPhrases.isEmpty();
    }

    boolean hasInstant() {
        return mHasInstant;
    }

    /** @return the most text a phrase can span, with the widest allowed gaps. */
    int maxLength() {
        return mMaxLength;
    }

    /**
     * Finds a phrase that ends exactly at the end of the text, which is the text before the cursor.
     *
     * @param instantOnly look only at phrases marked with {@code !}.
     * @return how many chars at the end of the text make up the phrase, or 0 if none does. If
     * several match, the longest.
     */
    int lengthBefore(final CharSequence text, final boolean instantOnly) {
        if (mPhrases.isEmpty() || text == null) {
            return 0;
        }
        final Tokens tokens = new Tokens(text);
        final int count = tokens.count();
        if (count < 2 || tokens.end(count - 1) != text.length()) {
            return 0;
        }
        int best = 0;
        for (final Phrase phrase : mPhrases) {
            final int first = count - phrase.size();
            if (first < 0 || (instantOnly && !phrase.mInstant)
                    || !tokens.adjacent(first, phrase.size()) || !phrase.matches(tokens, first)) {
                continue;
            }
            best = Math.max(best, text.length() - tokens.start(first));
        }
        return best;
    }

    /**
     * Finds a phrase that contains or touches the cursor, so that editing a word of a finished
     * phrase (or putting a space inside it, or taking one out) cannot slip it past
     * {@link #lengthBefore}.
     *
     * @return what to delete on each side of the cursor (the widest phrase), or null if none.
     */
    WordFilter.Hit around(final CharSequence before, final CharSequence after) {
        if (mPhrases.isEmpty() || before == null || after == null) {
            return null;
        }
        final int cursor = before.length();
        final Tokens tokens = new Tokens(new StringBuilder(cursor + after.length())
                .append(before).append(after));
        int bestStart = -1;
        int bestEnd = -1;
        for (final Phrase phrase : mPhrases) {
            final int size = phrase.size();
            for (int first = 0; first + size <= tokens.count(); first++) {
                final int start = tokens.start(first);
                final int end = tokens.end(first + size - 1);
                if (start > cursor) {
                    break;
                }
                if (end < cursor || end - start <= bestEnd - bestStart) {
                    continue;
                }
                if (tokens.adjacent(first, size) && phrase.matches(tokens, first)) {
                    bestStart = start;
                    bestEnd = end;
                }
            }
        }
        return bestStart < 0 ? null : new WordFilter.Hit(cursor - bestStart, bestEnd - cursor);
    }

    /** Adds the [start, end) span of every phrase found anywhere in the text to {@code out}. */
    void spans(final CharSequence text, final List<int[]> out) {
        if (mPhrases.isEmpty() || text == null) {
            return;
        }
        final Tokens tokens = new Tokens(text);
        for (final Phrase phrase : mPhrases) {
            final int size = phrase.size();
            for (int first = 0; first + size <= tokens.count(); first++) {
                if (tokens.adjacent(first, size) && phrase.matches(tokens, first)) {
                    out.add(new int[] {tokens.start(first), tokens.end(first + size - 1)});
                }
            }
        }
    }

    /** The tokens of a text, as [start, end) pairs, with their readings worked out on demand. */
    static List<int[]> tokenSpans(final CharSequence text) {
        final List<int[]> spans = new ArrayList<>();
        final int length = text.length();
        int i = 0;
        while (i < length) {
            int codePoint = Character.codePointAt(text, i);
            if (!isTokenCodePoint(codePoint)) {
                i += Character.charCount(codePoint);
                continue;
            }
            final int start = i;
            boolean hasLetterOrDigit = false;
            while (i < length) {
                codePoint = Character.codePointAt(text, i);
                if (!isTokenCodePoint(codePoint)) {
                    break;
                }
                hasLetterOrDigit |= Character.isLetterOrDigit(codePoint);
                i += Character.charCount(codePoint);
            }
            if (hasLetterOrDigit) {
                spans.add(new int[] {start, i});
            }
        }
        return spans;
    }

    private static boolean isTokenCodePoint(final int codePoint) {
        return codePoint != '_' && WordFilter.isWordCodePoint(codePoint);
    }

    private static final class Tokens {
        private final CharSequence mText;
        private final List<int[]> mSpans;
        private final List<Set<String>> mReadings;

        Tokens(final CharSequence text) {
            mText = text;
            mSpans = tokenSpans(text);
            mReadings = new ArrayList<>(mSpans.size());
            for (int i = 0; i < mSpans.size(); i++) {
                mReadings.add(null);
            }
        }

        int count() {
            return mSpans.size();
        }

        int start(final int index) {
            return mSpans.get(index)[0];
        }

        int end(final int index) {
            return mSpans.get(index)[1];
        }

        /** @return true if no gap between {@code count} tokens from {@code first} is too long. */
        boolean adjacent(final int first, final int count) {
            for (int i = first + 1; i < first + count; i++) {
                if (start(i) - end(i - 1) > MAX_GAP) {
                    return false;
                }
            }
            return true;
        }

        /** Every reading of a token: of the token, and of it without its edge symbols. */
        Set<String> readings(final int index) {
            Set<String> readings = mReadings.get(index);
            if (readings == null) {
                readings = new LinkedHashSet<>();
                final CharSequence token = mText.subSequence(start(index), end(index));
                for (final CharSequence candidate : WordFilter.candidates(token)) {
                    for (final String variant : WordFilter.variantsOf(candidate)) {
                        if (!variant.isEmpty()) {
                            readings.add(variant);
                        }
                    }
                }
                mReadings.set(index, readings);
            }
            return readings;
        }
    }

    private static final class Phrase {
        private final List<Set<String>> mWords;
        private final boolean mWildcard;
        final boolean mInstant;

        Phrase(final List<Set<String>> words, final boolean wildcard, final boolean instant) {
            mWords = words;
            mWildcard = wildcard;
            mInstant = instant;
        }

        int size() {
            return mWords.size();
        }

        /** @return true if the tokens from {@code first} are, one for one, the words of the phrase. */
        boolean matches(final Tokens tokens, final int first) {
            for (int i = 0; i < mWords.size(); i++) {
                final boolean stem = mWildcard && i == mWords.size() - 1;
                if (!matchesWord(tokens.readings(first + i), mWords.get(i), stem)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean matchesWord(final Set<String> readings, final Set<String> word,
                final boolean stem) {
            for (final String reading : readings) {
                if (!stem) {
                    if (word.contains(reading)) {
                        return true;
                    }
                    continue;
                }
                for (final String prefix : word) {
                    if (reading.startsWith(prefix)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
