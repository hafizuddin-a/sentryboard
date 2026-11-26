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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Suggests related forms of an entry that the user might also want on the list: the words run
 * together, the two words the other way round, and the vowels left out. Strict matching catches
 * most of these in browsers; adding them blocks them everywhere, and covers short forms that the
 * automatic rules leave alone ({@code lmp}).
 *
 * Pure Java.
 */
final class VariantSuggester {
    private VariantSuggester() {
    }

    /**
     * @param entry the entry as stored, possibly ending in {@code !} or {@code *}.
     * @param listed the current list, to leave out forms it already blocks; or null to offer
     * every form (used while the list is hidden, so that nothing about it is given away).
     * @param words ordinary words: a form that is only ordinary words is not offered, except for
     * the words put the other way round or run together.
     * @return suggested entries, each carrying the same marker as {@code entry}.
     */
    static List<String> suggest(final String entry, final WordFilter listed,
            final CommonWords words) {
        if (entry == null || !WordFilter.isValidEntry(entry)) {
            return Collections.emptyList();
        }
        String body = WordFilter.tidyEntry(entry);
        String marker = "";
        if (body.endsWith("!") || body.endsWith("*")) {
            marker = body.substring(body.length() - 1);
            body = body.substring(0, body.length() - 1).trim();
        }
        final List<String> parts = new ArrayList<>();
        for (final String part : body.toLowerCase(Locale.ROOT).split("[ _]+")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        final Set<String> forms = new LinkedHashSet<>();
        if (parts.size() > 1) {
            forms.add(join(parts, ""));
        }
        if (parts.size() == 2) {
            forms.add(parts.get(1) + " " + parts.get(0));
            forms.add(parts.get(1) + parts.get(0));
        }
        final List<String> bare = new ArrayList<>();
        boolean everyPartKeeps = true;
        boolean onlyOrdinary = true;
        for (final String part : parts) {
            final String stripped = FuzzyMatcher.withoutVowels(part);
            everyPartKeeps &= !stripped.isEmpty();
            onlyOrdinary &= words.isWord(stripped);
            bare.add(stripped);
        }
        // A form of one or two letters would block far too much.
        if (everyPartKeeps && !onlyOrdinary && join(bare, "").length() > 2) {
            forms.add(join(bare, " "));
            if (bare.size() > 1) {
                forms.add(join(bare, ""));
            }
        }
        final List<String> suggestions = new ArrayList<>();
        for (final String form : forms) {
            if (form.equalsIgnoreCase(body) || !WordFilter.isValidEntry(form + marker)
                    || (listed != null && listed.blockedLengthBefore(form) == form.length())) {
                continue;
            }
            suggestions.add(form + marker);
        }
        return suggestions;
    }

    private static String join(final List<String> parts, final String separator) {
        final StringBuilder sb = new StringBuilder();
        for (final String part : parts) {
            sb.append(sb.length() > 0 ? separator : "").append(part);
        }
        return sb.toString();
    }
}
