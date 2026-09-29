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

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.SystemClock;
import android.provider.Settings;

import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat;

/**
 * The single entry point for "may the filter settings be changed?", and the owner of the lock.
 *
 * {@link #canModifyFilterSettings()} is used by the settings screens (to disable controls) and by
 * the data layer ({@code BlockedWordStore}, {@code FilterSettingsWriter}), which refuse writes
 * while locked no matter what the UI did. State is written to disk after every change; a
 * countdown in progress lives in memory only.
 *
 * Thread safe: all methods synchronize on this object.
 */
public final class SettingsGate implements WriteGate {
    private static SettingsGate sInstance;

    private final SettingsLock.Clock mClock;
    private final boolean mDebug;
    private final SettingsLockStore mStore;
    private SettingsLock mLock;
    private LockSnapshot mPersisted;
    private long mUnlockGeneration;

    /** The process-wide gate, shared by the settings screens, the unlock page and the guard. */
    public static synchronized SettingsGate getInstance(final Context context) {
        if (sInstance == null) {
            final Context appContext = context.getApplicationContext() != null
                    ? context.getApplicationContext() : context;
            final Context deviceContext = PreferenceManagerCompat.getDeviceContext(appContext);
            final boolean debug =
                    (appContext.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            sInstance = new SettingsGate(new AndroidClock(appContext.getContentResolver()), debug,
                    new SettingsLockStore(deviceContext.getSharedPreferences(
                            SettingsLockStore.PREFS_NAME, Context.MODE_PRIVATE)));
        }
        return sInstance;
    }

    SettingsGate(final SettingsLock.Clock clock, final boolean debug,
            final SettingsLockStore store) {
        mClock = clock;
        mDebug = debug;
        mStore = store;
        mPersisted = store.load();
        mLock = new SettingsLock(clock, debug, mPersisted);
        persistIfChanged();
    }

    public boolean isDebug() {
        return mDebug;
    }

    @Override
    public synchronized boolean canModifyFilterSettings() {
        final boolean can = mLock.canModify();
        persistIfChanged();
        return can;
    }

    public synchronized SettingsLock.State state() {
        final SettingsLock.State state = mLock.state();
        persistIfChanged();
        return state;
    }

    public synchronized long durationMs() {
        return mLock.durationMs();
    }

    public synchronized long unlockRemainingMs() {
        final long remaining = mLock.unlockRemainingMs();
        persistIfChanged();
        return remaining;
    }

    public synchronized long editWindowRemainingMs() {
        final long remaining = mLock.editWindowRemainingMs();
        persistIfChanged();
        return remaining;
    }

    /**
     * Turns the lock on with a countdown of {@code durationMs}. If the lock cannot be saved it is
     * not turned on, since a lock that would vanish on the next restart is worse than none.
     */
    public synchronized boolean lock(final long durationMs) {
        final LockSnapshot before = mLock.snapshot();
        if (!mLock.lock(durationMs)) {
            persistIfChanged();
            return false;
        }
        if (!persistIfChanged()) {
            mLock = new SettingsLock(mClock, mDebug, before);
            return false;
        }
        return true;
    }

    public synchronized boolean setDuration(final long durationMs) {
        final boolean ok = mLock.setDuration(durationMs);
        persistIfChanged();
        return ok;
    }

    /**
     * Starts the countdown, which lives in memory only.
     *
     * @return a number identifying this countdown, to be passed to {@link #interruptUnlock}, or 0
     * if a countdown could not be started. Late callbacks from an earlier unlock page therefore
     * cannot cancel a newer countdown.
     */
    public synchronized long startUnlock() {
        if (!mLock.startUnlock()) {
            persistIfChanged();
            return 0;
        }
        mStore.setResetNotePending(false);
        persistIfChanged();
        return ++mUnlockGeneration;
    }

    /**
     * Abandons the countdown identified by {@code generation}, so the next one starts from zero.
     * Does nothing if that countdown is not the current one, or has already finished.
     *
     * @param leftThePage true if the user left the unlock page (rather than pressing Cancel), in
     * which case the page will say so the next time it is shown.
     */
    public synchronized void interruptUnlock(final long generation, final boolean leftThePage) {
        if (generation != 0 && generation == mUnlockGeneration && mLock.interruptUnlock()
                && leftThePage) {
            mStore.setResetNotePending(true);
        }
        persistIfChanged();
    }

    public synchronized boolean lockNow() {
        final boolean ok = mLock.lockNow();
        persistIfChanged();
        return ok;
    }

    public synchronized boolean removeLock() {
        final boolean ok = mLock.removeLock();
        persistIfChanged();
        return ok;
    }

    /**
     * The user left the settings page: an open edit window closes. Call this from a callback that
     * runs promptly when the page is covered ({@code onPause}); a late one could otherwise close a
     * window that was opened afterwards.
     */
    public synchronized void onSettingsPageLeft() {
        mLock.lockNow();
        persistIfChanged();
    }

    /** @return true, once, if a countdown was cut short since the unlock page last said so. */
    public synchronized boolean consumeResetNote() {
        return mStore.consumeResetNote();
    }

    /** @return false if the state changed but could not be written. */
    private boolean persistIfChanged() {
        final LockSnapshot now = mLock.snapshot();
        if (now.equals(mPersisted)) {
            return true;
        }
        if (mStore.save(now)) {
            mPersisted = now;
            return true;
        }
        return false;
    }

    private static final class AndroidClock implements SettingsLock.Clock {
        private final ContentResolver mResolver;

        AndroidClock(final ContentResolver resolver) {
            mResolver = resolver;
        }

        @Override
        public long elapsedRealtime() {
            return SystemClock.elapsedRealtime();
        }

        @Override
        public int bootCount() {
            try {
                return Settings.Global.getInt(mResolver, Settings.Global.BOOT_COUNT, -1);
            } catch (RuntimeException e) {
                return -1;
            }
        }
    }
}
