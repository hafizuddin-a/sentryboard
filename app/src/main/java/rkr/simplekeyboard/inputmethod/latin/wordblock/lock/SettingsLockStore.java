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

import android.content.SharedPreferences;

/**
 * Persistence for the lock: a small SharedPreferences file kept apart from the keyboard's other
 * settings. Reads fail closed (see {@link LockSnapshot#fromMap}).
 */
public final class SettingsLockStore {
    public static final String PREFS_NAME = "settings_lock";
    private static final String KEY_RESET_NOTE = "reset_note";

    private final SharedPreferences mPrefs;

    public SettingsLockStore(final SharedPreferences prefs) {
        mPrefs = prefs;
    }

    /** @return the stored lock state; never null, and locked if anything looks wrong. */
    public LockSnapshot load() {
        try {
            return LockSnapshot.fromMap(mPrefs.getAll());
        } catch (RuntimeException e) {
            return LockSnapshot.failClosed();
        }
    }

    /** Writes synchronously. @return false if it could not be written. */
    public boolean save(final LockSnapshot snapshot) {
        try {
            final SharedPreferences.Editor editor = mPrefs.edit();
            editor.putInt(LockSnapshot.KEY_VERSION, (Integer) snapshot.toMap().get(LockSnapshot.KEY_VERSION));
            editor.putBoolean(LockSnapshot.KEY_ENABLED, snapshot.lockEnabled);
            editor.putLong(LockSnapshot.KEY_DURATION_MS, snapshot.durationMs);
            editor.putBoolean(LockSnapshot.KEY_UNLOCKED, snapshot.unlocked);
            editor.putLong(LockSnapshot.KEY_UNLOCKED_AT, snapshot.unlockedAtElapsed);
            editor.putInt(LockSnapshot.KEY_BOOT_COUNT, snapshot.bootCountAtUnlock);
            return editor.commit();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Remembers that a countdown was cut short, so the unlock page can say so next time. */
    public void setResetNotePending(final boolean pending) {
        try {
            mPrefs.edit().putBoolean(KEY_RESET_NOTE, pending).commit();
        } catch (RuntimeException e) {
            // The note is a courtesy; losing it is harmless.
        }
    }

    /** @return true, once, if a countdown was cut short since the note was last shown. */
    public boolean consumeResetNote() {
        try {
            if (!mPrefs.getBoolean(KEY_RESET_NOTE, false)) {
                return false;
            }
            mPrefs.edit().putBoolean(KEY_RESET_NOTE, false).commit();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
