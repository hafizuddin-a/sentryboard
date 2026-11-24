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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The "strict" matching: finds a blocked word or phrase that has been disguised by splitting it,
 * separating its letters, repeating letters or mistyping it, the way a search engine would still
 * understand it. {@code h ello world}, {@code h.e.l.l.o}, {@code heeello} and {@code hellp} are all
 * read as {@code hello}.
 *
 * Pure Java and immutable. It works on the <em>squash</em> of a run of whole words: the letters
 * and digits of the words, with the same normalization as everywhere else ({@link
 * WordFilter#variantsOf}: accents, case, leet, lookalike letters), joined together with whatever
 * was between them thrown away. A run is up to {@link #MAX_RUN_TOKENS} words with at most
 * {@link #MAX_GAP} characters between them. Three ways for a run to match an entry:
 * <ol>
 * <li><b>Same squash</b> (splitting, separating): {@code h ello} is {@code hello}.</li>
 * <li><b>Same squash once repeated letters are folded</b>, if the text only has more letters than
 *     the entry: {@code heeello} is {@code hello}. Not for entries that fold down to under four
 *     letters ({@code ass} would become {@code as}).</li>
 * <li><b>One typo</b> (a letter changed, added, left out or two swapped; two typos for entries of
 *     ten letters or more): {@code hellp} is {@code hello}. Only for entries of at least six
 *     letters (eight for phrases), and a single word only ever matches a single word, so a split
 *     and a typo never combine.</li>
 * </ol>
 *
 * The danger is blocking innocent text, and it is what the {@link CommonWords} guard is for: a run
 * made only of ordinary words is never blocked here, so {@code hell} and {@code help} stay safe
 * next to {@code hello}, and {@code al so} is not {@code also}. A single letter is never an ordinary
 * word.
 */
final class FuzzyMatcher {
    /** The most characters that may separate two words of a run. */
    static final int MAX_GAP = 6;
    /** The most words a run may have. */
    static final int MAX_RUN_TOKENS = 12;
    /** Entries that squash to fewer letters than this are not matched strictly. */
    static final int MIN_SKELETON = 4;
    static final int MIN_TYPO_SINGLE = 6;
    static final int MIN_TYPO_PHRASE = 8;
    static final int TWO_TYPOS_FROM = 10;
    private static final int MAX_COMBINATIONS = 8;

    private final CommonWords mWords;
    private final Set<String> mExact = new HashSet<>();
    /** Folded squash -> the length of the shortest entry that folds to it. */
    private final Map<String, Integer> mFolded = new HashMap<>();
    private final List<String> mSingleTypo = new ArrayList<>();
    private final List<String> mPhraseTypo = new ArrayList<>();
    private int mMaxSkeleton;

    FuzzyMatcher(final CommonWords words) {
        mWords = words;
    }

    /**
     * @param readings every reading of an entry (the words of a phrase joined).
     * @param multiToken whether the entry is several words (a phrase, or one with underscores).
     */
    void add(final Set<String> readings, final boolean multiToken) {
        for (final String reading : readings) {
            final String squash = squash(reading);
            if (squash.length() < MIN_SKELETON) {
                continue;
            }
            mExact.add(squash);
            mMaxSkeleton = Math.max(mMaxSkeleton, squash.length());
            final String folded = fold(squash);
            if (folded.length() >= MIN_SKELETON) {
                final Integer shortest = mFolded.get(folded);
                if (shortest == null || squash.length() < shortest) {
                    mFolded.put(folded, squash.length());
                }
            }
            if (multiToken) {
                if (squash.length() >= MIN_TYPO_PHRASE) {
                    mPhraseTypo.add(squash);
                }
            } else if (squash.length() >= MIN_TYPO_SINGLE) {
                mSingleTypo.add(squash);
            }
        }
    }

    boolean isEmpty() {
        return mExact.isEmpty();
    }

    /** @return the most text a match can span, so callers know how much to read back. */
    int maxLength() {
        return mExact.isEmpty() ? 0 : mMaxSkeleton * 2 + MAX_RUN_TOKENS * MAX_GAP;
    }

    /** Adds the [start, end) span of every disguised entry found anywhere in the text to out. */
    void spans(final CharSequence text, final List<int[]> out) {
        if (mExact.isEmpty() || text == null) {
            return;
        }
        spans(text, false, out);
        spans(text, true, out); // A format character may also be a separator between two words.
    }

    private void spans(final CharSequence text, final boolean formatAsGap, final List<int[]> out) {
        final List<int[]> tokens = PhraseSet.tokenSpans(text, formatAsGap);
        final int count = tokens.size();
        @SuppressWarnings("unchecked")
        final List<String>[] readings = new List[count];
        final boolean[] common = new boolean[count];
        final int lengthLimit = mMaxSkeleton * 4 + 8;
        for (int first = 0; first < count; first++) {
            List<String> squashes = new ArrayList<>();
            squashes.add("");
            boolean allCommon = true;
            for (int last = first; last < count && last - first < MAX_RUN_TOKENS; last++) {
                if (last > first && tokens.get(last)[0] - tokens.get(last - 1)[1] > MAX_GAP) {
                    break;
                }
                if (readings[last] == null) {
                    readings[last] = readingsOf(text, tokens.get(last));
                    common[last] = isCommon(readings[last]);
                }
                allCommon &= common[last];
                squashes = join(squashes, readings[last]);
                if (shortest(squashes) > lengthLimit) {
                    break;
                }
                if (!allCommon && matches(squashes, last == first)) {
                    out.add(new int[] {tokens.get(first)[0], tokens.get(last)[1]});
                }
            }
        }
    }

    private boolean matches(final List<String> squashes, final boolean singleToken) {
        for (final String squash : squashes) {
            if (mExact.contains(squash)) {
                return true;
            }
            final String folded = fold(squash);
            if (folded.length() >= MIN_SKELETON) {
                final Integer shortest = mFolded.get(folded);
                if (shortest != null && squash.length() >= shortest) {
                    return true;
                }
            }
            if (typo(squash, mPhraseTypo) || (singleToken && typo(squash, mSingleTypo))) {
                return true;
            }
        }
        return false;
    }

    private static boolean typo(final String squash, final List<String> entries) {
        for (final String entry : entries) {
            final int most = entry.length() >= TWO_TYPOS_FROM ? 2 : 1;
            if (Math.abs(squash.length() - entry.length()) <= most && !squash.equals(entry)
                    && withinEdits(squash, entry, most)) {
                return true;
            }
        }
        return false;
    }

    /** Every squashed reading of a token, with and without its edge symbols. */
    private static List<String> readingsOf(final CharSequence text, final int[] span) {
        final Set<String> result = new LinkedHashSet<>();
        final CharSequence token = text.subSequence(span[0], span[1]);
        for (final CharSequence candidate : WordFilter.candidates(token)) {
            for (final String variant : WordFilter.variantsOf(candidate)) {
                final String squash = squash(variant);
                if (!squash.isEmpty()) {
                    result.add(squash);
                }
            }
        }
        return new ArrayList<>(result);
    }

    private boolean isCommon(final List<String> readings) {
        for (final String reading : readings) {
            if (isLettersOnly(reading) && mWords.isWord(reading)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLettersOnly(final String s) {
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c < 'a' || c > 'z') {
                return false;
            }
        }
        return !s.isEmpty();
    }

    private static List<String> join(final List<String> prefixes, final List<String> readings) {
        final Set<String> joined = new LinkedHashSet<>();
        for (final String prefix : prefixes) {
            for (final String reading : readings) {
                joined.add(prefix + reading);
                if (joined.size() >= MAX_COMBINATIONS) {
                    return new ArrayList<>(joined);
                }
            }
        }
        return new ArrayList<>(joined);
    }

    private static int shortest(final List<String> strings) {
        int shortest = Integer.MAX_VALUE;
        for (final String s : strings) {
            shortest = Math.min(shortest, s.length());
        }
        return shortest;
    }

    /** The letters and digits of a normalized reading, nothing else. */
    static String squash(final String reading) {
        final StringBuilder sb = new StringBuilder(reading.length());
        for (int i = 0; i < reading.length(); ) {
            final int codePoint = reading.codePointAt(i);
            if (Character.isLetterOrDigit(codePoint)) {
                sb.appendCodePoint(codePoint);
            }
            i += Character.charCount(codePoint);
        }
        return sb.toString();
    }

    /** A squash with each run of the same letter folded to one letter (digits are left alone). */
    static String fold(final String squash) {
        final StringBuilder sb = new StringBuilder(squash.length());
        char previous = 0;
        for (int i = 0; i < squash.length(); i++) {
            final char c = squash.charAt(i);
            if (c != previous || Character.isDigit(c)) {
                sb.append(c);
            }
            previous = c;
        }
        return sb.toString();
    }

    /** @return true if a can be made into b with at most {@code most} single-letter edits. */
    static boolean withinEdits(final String a, final String b, final int most) {
        final int n = a.length();
        final int m = b.length();
        if (Math.abs(n - m) > most) {
            return false;
        }
        // Optimal string alignment distance: substitute, insert, delete, or swap two neighbours.
        final int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                final int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int best = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    best = Math.min(best, d[i - 2][j - 2] + 1);
                }
                d[i][j] = best;
            }
        }
        return d[n][m] <= most;
    }
}
