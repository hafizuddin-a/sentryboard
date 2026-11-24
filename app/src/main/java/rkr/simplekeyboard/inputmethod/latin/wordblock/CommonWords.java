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

/**
 * Tells an ordinary English word from a mistyped one, for the strict matching in
 * {@link FuzzyMatcher}: a word that is really a word is never blocked by a typo rule, so a blocked
 * {@code hello} does not take {@code hell} or {@code help} with it.
 *
 * It is a Bloom filter built by {@code tools/make_common_words.py}. A Bloom filter can say "yes" for
 * something that is not in it (about 4% of the time) but never "no" for something that is, and
 * here a wrong "yes" only means a typo goes unblocked, never that a real word is blocked.
 *
 * Pure Java and immutable. {@link #NONE} knows no words, which turns the guard off.
 */
final class CommonWords {
    /** Knows no words: nothing is ever treated as an ordinary word. */
    static final CommonWords NONE = new CommonWords(new byte[0], 0, 0);

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final byte[] mBits;
    private final int mHashes;
    private final long mBitCount;

    private CommonWords(final byte[] bits, final int hashes, final long bitCount) {
        mBits = bits;
        mHashes = hashes;
        mBitCount = bitCount;
    }

    /**
     * @param data the file made by {@code make_common_words.py}.
     * @return the words in it, or {@link #NONE} if the data is missing or malformed.
     */
    static CommonWords fromBytes(final byte[] data) {
        if (data == null || data.length < 6) {
            return NONE;
        }
        final int hashes = data[0] & 0xff;
        final long bitCount = ((data[1] & 0xffL) << 24) | ((data[2] & 0xffL) << 16)
                | ((data[3] & 0xffL) << 8) | (data[4] & 0xffL);
        if (hashes == 0 || bitCount == 0 || data.length - 5 < (bitCount + 7) / 8) {
            return NONE;
        }
        final byte[] bits = new byte[data.length - 5];
        System.arraycopy(data, 5, bits, 0, bits.length);
        return new CommonWords(bits, hashes, bitCount);
    }

    boolean isEmpty() {
        return mBitCount == 0;
    }

    /**
     * @param word lower-case letters only.
     * @return true if this is an ordinary word, or an ordinary word with a common ending
     * ({@code kills}, {@code killed}, {@code killing}, {@code quickly}, {@code bigger}).
     */
    boolean isWord(final String word) {
        final int length = word.length();
        if (mBitCount == 0 || length < 2) {
            return false;
        }
        if (contains(word)) {
            return true;
        }
        if (length < 4) {
            return false;
        }
        if (endsWith(word, "s") && contains(word.substring(0, length - 1))) {
            return true;
        }
        if (endsWith(word, "es") && contains(word.substring(0, length - 2))) {
            return true;
        }
        if (endsWith(word, "ies") && contains(word.substring(0, length - 3) + "y")) {
            return true;
        }
        if (endsWith(word, "ed") || endsWith(word, "er")) {
            if (stemIsWord(word, length - 2)) {
                return true;
            }
        }
        if (endsWith(word, "d") && contains(word.substring(0, length - 1))) {
            return true;
        }
        if (endsWith(word, "ers") && stemIsWord(word, length - 3)) {
            return true;
        }
        if (endsWith(word, "ing") && stemIsWord(word, length - 3)) {
            return true;
        }
        if (endsWith(word, "ly") && stemIsWord(word, length - 2)) {
            return true;
        }
        return false;
    }

    /** The stem before an ending, also with its final consonant undoubled or an "e" put back. */
    private boolean stemIsWord(final String word, final int stemLength) {
        final String stem = word.substring(0, stemLength);
        if (contains(stem) || contains(stem + "e")) {
            return true;
        }
        final int n = stem.length();
        return n >= 3 && stem.charAt(n - 1) == stem.charAt(n - 2)
                && contains(stem.substring(0, n - 1));
    }

    private static boolean endsWith(final String word, final String ending) {
        return word.endsWith(ending);
    }

    private boolean contains(final String word) {
        long hash = FNV_OFFSET;
        for (int i = 0; i < word.length(); i++) {
            hash = (hash ^ (word.charAt(i) & 0xff)) * FNV_PRIME;
        }
        final long h1 = hash & 0xffffffffL;
        final long h2 = (hash >>> 32) | 1L;
        for (int i = 0; i < mHashes; i++) {
            final long bit = (h1 + i * h2) % mBitCount;
            if ((mBits[(int) (bit >> 3)] & (1 << (int) (bit & 7))) == 0) {
                return false;
            }
        }
        return true;
    }
}
