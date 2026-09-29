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

package rkr.simplekeyboard.inputmethod.latin.protection;

import android.content.Context;
import android.content.SharedPreferences;

import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat;

/**
 * Remembers that uninstall protection was turned off outside the app, until the user turns it back
 * on. Kept in a small preferences file of its own.
 */
public final class ProtectionStore {
    static final String PREFS_NAME = "protection";
    private static final String KEY_TAMPER_AT = "tamper_detected_at";

    private final SharedPreferences mPrefs;

    public ProtectionStore(final SharedPreferences prefs) {
        mPrefs = prefs;
    }

    public static ProtectionStore get(final Context context) {
        final Context deviceContext = PreferenceManagerCompat.getDeviceContext(
                context.getApplicationContext() != null ? context.getApplicationContext() : context);
        return new ProtectionStore(
                deviceContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE));
    }

    /** Records that protection was removed outside the app, at {@code wallClockMillis}. */
    public void recordTamper(final long wallClockMillis) {
        try {
            mPrefs.edit().putLong(KEY_TAMPER_AT, Math.max(1, wallClockMillis)).commit();
        } catch (RuntimeException e) {
            // Nothing sensible to do; the notification still tells the user.
        }
    }

    /** @return when tampering was detected (wall clock), or 0 if there is nothing to report. */
    public long tamperDetectedAt() {
        try {
            return mPrefs.getLong(KEY_TAMPER_AT, 0);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public boolean isTamperPending() {
        return tamperDetectedAt() > 0;
    }

    public void clearTamper() {
        try {
            mPrefs.edit().remove(KEY_TAMPER_AT).commit();
        } catch (RuntimeException e) {
            // Ignored.
        }
    }
}
