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

/**
 * The settings lock: a state machine with a countdown. Pure Java, so it can be tested with a fake
 * clock.
 *
 * <pre>
 * NO_LOCK   --lock(duration)-->  LOCKED
 * LOCKED    --startUnlock()-->   UNLOCKING   (countdown running)
 * UNLOCKING --interruptUnlock()--> LOCKED    (countdown starts over next time)
 * UNLOCKING --deadline reached--> UNLOCKED   (edit window open)
 * UNLOCKED  --lockNow / window over / reboot / clock went backwards--> LOCKED
 * UNLOCKED  --removeLock()-->    NO_LOCK
 * </pre>
 *
 * All timing is done with {@link Clock#elapsedRealtime()}, never wall-clock time, so changing the
 * phone's clock cannot shorten a wait. Completion is decided by comparing against the deadline,
 * not by counting ticks, so it does not matter how often (or late) this is asked.
 *
 * The duration is fixed while locked: it can only be changed from NO_LOCK or UNLOCKED, and takes
 * effect the next time the lock goes on.
 *
 * Not thread safe; callers synchronize.
 */
public final class SettingsLock {
    public enum State { NO_LOCK, LOCKED, UNLOCKING, UNLOCKED }

    /** Time source. {@code elapsedRealtime} is monotonic milliseconds since boot. */
    public interface Clock {
        long elapsedRealtime();

        /** A number that changes on every reboot, or -1 if it is not available. */
        int bootCount();
    }

    private final Clock mClock;
    private final boolean mDebug;
    private State mState;
    private long mDurationMs;
    private long mUnlockStartedAt;
    private long mUnlockedAt;
    private int mBootCountAtUnlock;

    public SettingsLock(final Clock clock, final boolean debug) {
        this(clock, debug, LockSnapshot.none());
    }

    /** Restores from a stored snapshot; an edit window that has lapsed comes back locked. */
    public SettingsLock(final Clock clock, final boolean debug, final LockSnapshot snapshot) {
        mClock = clock;
        mDebug = debug;
        mDurationMs = snapshot.durationMs;
        if (!snapshot.lockEnabled) {
            mState = State.NO_LOCK;
        } else if (snapshot.unlocked) {
            mState = State.UNLOCKED;
            mUnlockedAt = snapshot.unlockedAtElapsed;
            mBootCountAtUnlock = snapshot.bootCountAtUnlock;
        } else {
            mState = State.LOCKED;
        }
        evaluate();
    }

    /** @return the state, after applying anything the passage of time has caused. */
    public State state() {
        evaluate();
        return mState;
    }

    /**
     * Applies the effects of time: a countdown reaching its deadline, or an edit window running out.
     * @return true if the state changed.
     */
    public boolean evaluate() {
        final long now = mClock.elapsedRealtime();
        if (mState == State.UNLOCKING) {
            if (now < mUnlockStartedAt) {
                mState = State.LOCKED; // The monotonic clock went backwards: distrust it.
                return true;
            }
            if (now - mUnlockStartedAt >= mDurationMs) {
                mState = State.UNLOCKED;
                mUnlockedAt = now;
                mBootCountAtUnlock = mClock.bootCount();
                return true;
            }
        } else if (mState == State.UNLOCKED) {
            if (now < mUnlockedAt || now - mUnlockedAt >= LockConstants.EDIT_WINDOW_MS
                    || mClock.bootCount() != mBootCountAtUnlock) {
                mState = State.LOCKED;
                return true;
            }
        }
        return false;
    }

    /** @return true if the filter settings may be changed right now. */
    public boolean canModify() {
        final State state = state();
        return state == State.NO_LOCK || state == State.UNLOCKED;
    }

    public long durationMs() {
        return mDurationMs;
    }

    /** @return milliseconds left on the countdown, or 0 if there is none running. */
    public long unlockRemainingMs() {
        if (state() != State.UNLOCKING) {
            return 0;
        }
        return Math.max(0, mUnlockStartedAt + mDurationMs - mClock.elapsedRealtime());
    }

    /** @return milliseconds left in the edit window, or 0 if it is not open. */
    public long editWindowRemainingMs() {
        if (state() != State.UNLOCKED) {
            return 0;
        }
        return Math.max(0, mUnlockedAt + LockConstants.EDIT_WINDOW_MS - mClock.elapsedRealtime());
    }

    /**
     * Turns the lock on. Allowed from NO_LOCK, or from UNLOCKED (which re-locks with the new
     * duration).
     *
     * @return false, changing nothing, if the state does not allow it or the duration is out of
     * bounds.
     */
    public boolean lock(final long durationMs) {
        final State state = state();
        if ((state != State.NO_LOCK && state != State.UNLOCKED)
                || !LockConstants.isValidDuration(durationMs, mDebug)) {
            return false;
        }
        mDurationMs = durationMs;
        mState = State.LOCKED;
        return true;
    }

    /**
     * Changes the countdown length that the next lock will use. Allowed from NO_LOCK and UNLOCKED
     * only.
     */
    public boolean setDuration(final long durationMs) {
        final State state = state();
        if ((state != State.NO_LOCK && state != State.UNLOCKED)
                || !LockConstants.isValidDuration(durationMs, mDebug)) {
            return false;
        }
        mDurationMs = durationMs;
        return true;
    }

    /** Starts the countdown. Allowed from LOCKED only. */
    public boolean startUnlock() {
        if (state() != State.LOCKED) {
            return false;
        }
        mState = State.UNLOCKING;
        mUnlockStartedAt = mClock.elapsedRealtime();
        return true;
    }

    /** Abandons a countdown in progress; the next one starts from zero. */
    public boolean interruptUnlock() {
        if (state() != State.UNLOCKING) {
            return false;
        }
        mState = State.LOCKED;
        return true;
    }

    /** Ends the edit window early, with the same duration. Allowed from UNLOCKED only. */
    public boolean lockNow() {
        if (state() != State.UNLOCKED) {
            return false;
        }
        mState = State.LOCKED;
        return true;
    }

    /** Turns the lock off. Allowed from UNLOCKED only. */
    public boolean removeLock() {
        if (state() != State.UNLOCKED) {
            return false;
        }
        mState = State.NO_LOCK;
        return true;
    }

    /**
     * @return what should be stored. A countdown in progress is stored as plain "locked".
     */
    public LockSnapshot snapshot() {
        final State state = state();
        return new LockSnapshot(state != State.NO_LOCK, mDurationMs, state == State.UNLOCKED,
                state == State.UNLOCKED ? mUnlockedAt : 0,
                state == State.UNLOCKED ? mBootCountAtUnlock : 0);
    }
}
