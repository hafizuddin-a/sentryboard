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

import android.content.SharedPreferences;

import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.WriteGate;

/**
 * The only code that writes the word filter's on/off and timing settings. It refuses to write
 * while the settings are locked, whatever the UI has done.
 *
 * The keys are those in {@code Settings}; they are repeated here as constants because reading
 * them through {@code Settings} would drag in that class's Android singleton.
 */
public final class FilterSettingsWriter {
    static final String KEY_ENABLED = "pref_wordblock_enabled";
    static final String KEY_IDLE_ENABLED = "pref_wordblock_idle_enabled";
    static final String KEY_IDLE_MS = "pref_wordblock_idle_ms";
    static final int MIN_IDLE_MS = 100;
    static final int MAX_IDLE_MS = 10_000;

    private final SharedPreferences mPrefs;
    private final WriteGate mGate;

    public FilterSettingsWriter(final SharedPreferences prefs, final WriteGate gate) {
        mPrefs = prefs;
        mGate = gate;
    }

    /** @return true if the change was saved. */
    public boolean setEnabled(final boolean enabled) {
        return mGate.canModifyFilterSettings()
                && mPrefs.edit().putBoolean(KEY_ENABLED, enabled).commit();
    }

    public boolean setIdleEnabled(final boolean enabled) {
        return mGate.canModifyFilterSettings()
                && mPrefs.edit().putBoolean(KEY_IDLE_ENABLED, enabled).commit();
    }

    /** The delay is stored as a string, because the list preference that edits it stores one. */
    public boolean setIdleMillis(final int millis) {
        return millis >= MIN_IDLE_MS && millis <= MAX_IDLE_MS && mGate.canModifyFilterSettings()
                && mPrefs.edit().putString(KEY_IDLE_MS, String.valueOf(millis)).commit();
    }
}
