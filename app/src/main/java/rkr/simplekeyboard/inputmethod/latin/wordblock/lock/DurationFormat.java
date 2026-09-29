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

package rkr.simplekeyboard.inputmethod.latin.wordblock.lock;

import java.util.Locale;

/** Human-readable durations for the lock screens. Pure Java. */
public final class DurationFormat {
    private DurationFormat() {
        // Intentional empty constructor for utility class.
    }

    /** @return e.g. "5 minutes", "1 hour", "4 hours 30 minutes", "45 seconds". */
    public static String describe(final long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000); // Round up: never claim less time.
        final long hours = seconds / 3600;
        seconds -= hours * 3600;
        final long minutes = seconds / 60;
        seconds -= minutes * 60;
        final StringBuilder text = new StringBuilder();
        append(text, hours, "hour");
        append(text, minutes, "minute");
        append(text, seconds, "second");
        return text.length() == 0 ? "0 seconds" : text.toString();
    }

    /** @return a countdown display such as "1:30:00" or "4:07", rounded up to whole seconds. */
    public static String clock(final long millis) {
        final long total = Math.max(0, (millis + 999) / 1000);
        final long hours = total / 3600;
        final long minutes = (total % 3600) / 60;
        final long seconds = total % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    private static void append(final StringBuilder text, final long count, final String unit) {
        if (count == 0) {
            return;
        }
        if (text.length() > 0) {
            text.append(' ');
        }
        text.append(count).append(' ').append(unit);
        if (count != 1) {
            text.append('s');
        }
    }
}
