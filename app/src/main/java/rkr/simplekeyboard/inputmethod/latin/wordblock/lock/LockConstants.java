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

/** Every tunable number of the settings lock, in one place. */
public final class LockConstants {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;

    /** Shortest countdown a user may choose. */
    public static final long MIN_DURATION_MS = MINUTE;
    /** Shortest countdown in a debuggable build, so the lock can be tested without waiting. */
    public static final long DEBUG_MIN_DURATION_MS = 5_000L;
    /** Longest countdown a user may choose. */
    public static final long MAX_DURATION_MS = 24 * HOUR;
    /** Countdown used when the stored lock state cannot be trusted, and the first pre-fill. */
    public static final long DEFAULT_DURATION_MS = 30 * MINUTE;
    /** How long settings stay editable after a completed countdown. */
    public static final long EDIT_WINDOW_MS = 5 * MINUTE;

    /** The presets offered when locking. */
    public static final long[] PRESET_DURATIONS_MS = {
        5 * MINUTE, 30 * MINUTE, HOUR, 4 * HOUR, 12 * HOUR, 24 * HOUR
    };

    private LockConstants() {
        // Intentional empty constructor for constants class.
    }

    public static long minDurationMs(final boolean debug) {
        return debug ? DEBUG_MIN_DURATION_MS : MIN_DURATION_MS;
    }

    public static boolean isValidDuration(final long durationMs, final boolean debug) {
        return durationMs >= minDurationMs(debug) && durationMs <= MAX_DURATION_MS;
    }
}
