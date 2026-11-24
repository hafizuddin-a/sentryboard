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
    /**
     * Words of two and three letters that people really write. The big word list is full of
     * obscure short words ({@code tui}, {@code tu}) that would make a name look like ordinary
     * text, so for words this short only this list is trusted.
     */
    private static final java.util.Set<String> SHORT_WORDS = new java.util.HashSet<>(
            java.util.Arrays.asList((
            // two letters
            "ab ad ag ah ai al am an ar as at aw ax ay ba be bi bo by do ed eh el em en er es ex go "
            + "ha he hi hm ho id if in is it jo la li lo ma me mi mm mo my na no nu of oh oi om on "
            + "op or ow ox oy pa pi re sh so ta to uh um un up us we ya ye yo "
            // three letters
            + "ace act add ads age ago aid aim air all and any ape arc are arm art ask ate awe axe "
            + "bad bag ban bar bat bay bed bee bet bid big bin bit bow box boy bud bug bus but buy bye "
            + "cab can cap car cat cop cow cry cub cup cut dad dam day den dew did die dig dim dip "
            + "dog dot dry dub due dug duo ear eat egg ego end era eve eye fan far fat fax fed fee few "
            + "fig fin fit fix fly fog for fox fry fun fur gag gap gas gel gem get gig god got gum gun "
            + "gut guy gym had hag ham has hat hay her hey hid him hip his hit hog hop hot how hub hug "
            + "hut ice icy ill ink inn ion its ivy jab jam jar jaw jet job jog joy jug key kid kin kit "
            + "lab lad lag lap law lay lea led leg let lid lie lip lit log lot low mad man map mat may "
            + "men met mix mob mom mop mud mug nap net new nil nod nor not now nun nut oak oar oat odd "
            + "off oil old one opt orb ore our out owe owl own pad pal pan pat paw pay pea peg pen pet "
            + "pie pig pin pit ply pod pop pot pub pun put rag ram ran rap rat raw ray red rib rid rig "
            + "rim rip rob rod rot row rub rug rum run rut rye sad sag sat saw say sea see set sew shy "
            + "sin sip sir sit six ski sky sly sob sod son sow soy spa spy sub sue sum sun tab tag tan "
            + "tap tar tax tea ten the thy tie tin tip toe ton too top tow toy try tub tug two urn use "
            + "van vat vet via vow wag war was wax way web wed wet who why wig win wit woe won wow yak "
            + "yam yap yes yet yew you zap zip zoo sex").split(" ")));


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
        if (known(word)) {
            return true;
        }
        if (length < 4) {
            return false;
        }
        if (endsWith(word, "s") && known(word.substring(0, length - 1))) {
            return true;
        }
        if (endsWith(word, "es") && known(word.substring(0, length - 2))) {
            return true;
        }
        if (endsWith(word, "ies") && known(word.substring(0, length - 3) + "y")) {
            return true;
        }
        if (endsWith(word, "ed") || endsWith(word, "er")) {
            if (stemIsWord(word, length - 2)) {
                return true;
            }
        }
        if (endsWith(word, "d") && known(word.substring(0, length - 1))) {
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
        if (known(stem) || known(stem + "e")) {
            return true;
        }
        final int n = stem.length();
        return n >= 3 && stem.charAt(n - 1) == stem.charAt(n - 2)
                && known(stem.substring(0, n - 1));
    }

    private static boolean endsWith(final String word, final String ending) {
        return word.endsWith(ending);
    }

    /** A word of two or three letters is known if it is on the short list, else if it is in the filter. */
    private boolean known(final String word) {
        return word.length() <= 3 ? SHORT_WORDS.contains(word) : contains(word);
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
