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

import java.util.HashMap;
import java.util.Map;

/**
 * The part of the lock state that is written to disk, and the rules for reading it back.
 *
 * The countdown itself is never stored: a half-finished countdown that survived a restart would
 * defeat its purpose. So there is no "unlocking" state here, only locked or unlocked.
 *
 * Reading fails closed. If anything about what was stored looks wrong, the result is a lock that
 * is on, with the default duration. The only way to get "no lock" is for nothing to have been
 * stored at all, which is how a fresh install looks.
 */
public final class LockSnapshot {
    public static final String KEY_VERSION = "v";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_DURATION_MS = "duration_ms";
    public static final String KEY_UNLOCKED = "unlocked";
    public static final String KEY_UNLOCKED_AT = "unlocked_at";
    public static final String KEY_BOOT_COUNT = "boot_count";
    private static final int VERSION = 1;

    public final boolean lockEnabled;
    public final long durationMs;
    public final boolean unlocked;
    public final long unlockedAtElapsed;
    public final int bootCountAtUnlock;

    public LockSnapshot(final boolean lockEnabled, final long durationMs, final boolean unlocked,
            final long unlockedAtElapsed, final int bootCountAtUnlock) {
        this.lockEnabled = lockEnabled;
        this.durationMs = durationMs;
        this.unlocked = unlocked;
        this.unlockedAtElapsed = unlockedAtElapsed;
        this.bootCountAtUnlock = bootCountAtUnlock;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LockSnapshot)) {
            return false;
        }
        final LockSnapshot that = (LockSnapshot) other;
        return lockEnabled == that.lockEnabled && durationMs == that.durationMs
                && unlocked == that.unlocked && unlockedAtElapsed == that.unlockedAtElapsed
                && bootCountAtUnlock == that.bootCountAtUnlock;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(lockEnabled, durationMs, unlocked, unlockedAtElapsed,
                bootCountAtUnlock);
    }

    /** No lock, as on a fresh install. */
    public static LockSnapshot none() {
        return new LockSnapshot(false, LockConstants.DEFAULT_DURATION_MS, false, 0, 0);
    }

    /** A lock that is on, used whenever what was stored cannot be trusted. */
    public static LockSnapshot failClosed() {
        return new LockSnapshot(true, LockConstants.DEFAULT_DURATION_MS, false, 0, 0);
    }

    /** The key/value form that is stored. Values are Boolean, Long or Integer. */
    public Map<String, Object> toMap() {
        final Map<String, Object> map = new HashMap<>();
        map.put(KEY_VERSION, VERSION);
        map.put(KEY_ENABLED, lockEnabled);
        map.put(KEY_DURATION_MS, durationMs);
        map.put(KEY_UNLOCKED, unlocked);
        map.put(KEY_UNLOCKED_AT, unlockedAtElapsed);
        map.put(KEY_BOOT_COUNT, bootCountAtUnlock);
        return map;
    }

    /**
     * @param stored everything found in storage, or null if it could not be read.
     * @return the stored snapshot; {@link #none()} only if nothing at all was stored; otherwise
     * {@link #failClosed()} on any doubt.
     */
    public static LockSnapshot fromMap(final Map<String, ?> stored) {
        if (stored == null) {
            return failClosed();
        }
        if (stored.isEmpty()) {
            return none();
        }
        try {
            final Object version = stored.get(KEY_VERSION);
            final Object enabled = stored.get(KEY_ENABLED);
            final Object duration = stored.get(KEY_DURATION_MS);
            final Object unlocked = stored.get(KEY_UNLOCKED);
            final Object unlockedAt = stored.get(KEY_UNLOCKED_AT);
            final Object bootCount = stored.get(KEY_BOOT_COUNT);
            if (!(version instanceof Integer) || (Integer) version != VERSION
                    || !(enabled instanceof Boolean) || !(duration instanceof Long)
                    || !(unlocked instanceof Boolean) || !(unlockedAt instanceof Long)
                    || !(bootCount instanceof Integer)) {
                return failClosed();
            }
            final long durationMs = (Long) duration;
            if (durationMs <= 0 || durationMs > LockConstants.MAX_DURATION_MS) {
                return failClosed();
            }
            final boolean isEnabled = (Boolean) enabled;
            final boolean isUnlocked = (Boolean) unlocked;
            if (isUnlocked && !isEnabled) {
                return failClosed(); // Contradictory.
            }
            return new LockSnapshot(isEnabled, durationMs, isUnlocked, (Long) unlockedAt,
                    (Integer) bootCount);
        } catch (RuntimeException e) {
            return failClosed();
        }
    }
}
