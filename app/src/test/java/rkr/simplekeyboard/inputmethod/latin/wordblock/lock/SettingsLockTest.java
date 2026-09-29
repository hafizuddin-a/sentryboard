package rkr.simplekeyboard.inputmethod.latin.wordblock.lock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsLock.State;

public class SettingsLockTest {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;

    private static final class FakeClock implements SettingsLock.Clock {
        long elapsed = 1_000_000L;
        int boot = 7;

        @Override
        public long elapsedRealtime() {
            return elapsed;
        }

        @Override
        public int bootCount() {
            return boot;
        }
    }

    private FakeClock mClock;
    private SettingsLock mLock;

    @Before
    public void setUp() {
        mClock = new FakeClock();
        mLock = new SettingsLock(mClock, false);
    }

    /** Locks for 30 minutes and completes the countdown, leaving the lock UNLOCKED. */
    private void unlock() {
        assertTrue(mLock.lock(30 * MINUTE));
        assertTrue(mLock.startUnlock());
        mClock.elapsed += 30 * MINUTE;
        assertEquals(State.UNLOCKED, mLock.state());
    }

    // --- Initial state and locking ---

    @Test
    public void initialStateIsEditable() {
        assertEquals(State.NO_LOCK, mLock.state());
        assertTrue(mLock.canModify());
    }

    @Test
    public void lockMovesToLockedAndForbidsModification() {
        assertTrue(mLock.lock(HOUR));
        assertEquals(State.LOCKED, mLock.state());
        assertFalse(mLock.canModify());
        assertEquals(HOUR, mLock.durationMs());
    }

    @Test
    public void lockRejectsDurationsOutOfBounds() {
        assertFalse(mLock.lock(0));
        assertFalse(mLock.lock(-1));
        assertFalse(mLock.lock(-HOUR));
        assertFalse(mLock.lock(MINUTE - 1));
        assertFalse(mLock.lock(24 * HOUR + 1));
        assertFalse(mLock.lock(Long.MAX_VALUE));
        assertFalse(mLock.lock(Long.MIN_VALUE));
        assertEquals(State.NO_LOCK, mLock.state());

        assertTrue(mLock.lock(MINUTE));
        assertEquals(State.LOCKED, mLock.state());
    }

    @Test
    public void maximumDurationIsAccepted() {
        assertTrue(mLock.lock(24 * HOUR));
        assertEquals(24 * HOUR, mLock.durationMs());
    }

    @Test
    public void debugBuildsAllowFiveSeconds() {
        final SettingsLock debug = new SettingsLock(mClock, true);
        assertFalse(debug.lock(4_999));
        assertTrue(debug.lock(5_000));
        assertFalse(mLock.lock(5_000)); // A release build does not.
    }

    // --- Countdown ---

    @Test
    public void countdownCompletesExactlyAtTheDeadline() {
        mLock.lock(HOUR);
        assertTrue(mLock.startUnlock());
        assertEquals(State.UNLOCKING, mLock.state());
        assertEquals(HOUR, mLock.unlockRemainingMs());

        mClock.elapsed += HOUR - 1;
        assertEquals(State.UNLOCKING, mLock.state());
        assertFalse(mLock.canModify());
        assertEquals(1, mLock.unlockRemainingMs());

        mClock.elapsed += 1;
        assertEquals(State.UNLOCKED, mLock.state());
        assertTrue(mLock.canModify());
        assertEquals(0, mLock.unlockRemainingMs());
    }

    @Test
    public void completionDoesNotDependOnHowOftenItIsChecked() {
        mLock.lock(HOUR);
        mLock.startUnlock();
        // Checked once, long after the deadline, without any intermediate tick.
        mClock.elapsed += 10 * HOUR;
        assertEquals(State.UNLOCKED, mLock.state());
    }

    @Test
    public void remainingTimeShrinksWithTheClock() {
        mLock.lock(HOUR);
        mLock.startUnlock();
        mClock.elapsed += 20 * MINUTE;
        assertEquals(40 * MINUTE, mLock.unlockRemainingMs());
    }

    @Test
    public void startUnlockOnlyFromLocked() {
        assertFalse(mLock.startUnlock()); // NO_LOCK
        mLock.lock(HOUR);
        assertTrue(mLock.startUnlock());
        assertFalse(mLock.startUnlock()); // Already unlocking.
        mClock.elapsed += HOUR;
        assertEquals(State.UNLOCKED, mLock.state());
        assertFalse(mLock.startUnlock()); // Already unlocked.
    }

    @Test
    public void interruptionResetsTheCountdownToZero() {
        mLock.lock(HOUR);
        mLock.startUnlock();
        mClock.elapsed += 59 * MINUTE;

        assertTrue(mLock.interruptUnlock());
        assertEquals(State.LOCKED, mLock.state());
        assertFalse(mLock.canModify());

        // A new attempt starts from the full duration; the 59 minutes do not count.
        assertTrue(mLock.startUnlock());
        assertEquals(HOUR, mLock.unlockRemainingMs());
        mClock.elapsed += HOUR - 1;
        assertEquals(State.UNLOCKING, mLock.state());
    }

    @Test
    public void interruptOnlyAffectsACountdownInProgress() {
        assertFalse(mLock.interruptUnlock());
        mLock.lock(HOUR);
        assertFalse(mLock.interruptUnlock());
        assertEquals(State.LOCKED, mLock.state());
    }

    // --- Edit window ---

    @Test
    public void editWindowExpiresAtFiveMinutes() {
        unlock();
        assertEquals(LockConstants.EDIT_WINDOW_MS, mLock.editWindowRemainingMs());
        mClock.elapsed += LockConstants.EDIT_WINDOW_MS - 1;
        assertEquals(State.UNLOCKED, mLock.state());
        assertTrue(mLock.canModify());
        mClock.elapsed += 1;
        assertEquals(State.LOCKED, mLock.state());
        assertFalse(mLock.canModify());
    }

    @Test
    public void rebootRelocksTheEditWindow() {
        unlock();
        mClock.boot++;
        assertEquals(State.LOCKED, mLock.state());
    }

    @Test
    public void unavailableBootCountThatStaysUnavailableDoesNotRelock() {
        mClock.boot = -1;
        unlock();
        assertEquals(State.UNLOCKED, mLock.state());
    }

    @Test
    public void clockGoingBackwardsRelocks() {
        unlock();
        mClock.elapsed -= 1;
        assertEquals(State.LOCKED, mLock.state());
    }

    @Test
    public void clockGoingBackwardsDuringACountdownRelocks() {
        mLock.lock(HOUR);
        mLock.startUnlock();
        mClock.elapsed -= 5;
        assertEquals(State.LOCKED, mLock.state());
    }

    @Test
    public void lockNowRelocksWithTheSameDuration() {
        unlock();
        assertTrue(mLock.lockNow());
        assertEquals(State.LOCKED, mLock.state());
        assertEquals(30 * MINUTE, mLock.durationMs());
        assertFalse(mLock.lockNow());
    }

    @Test
    public void lockNowOnlyFromUnlocked() {
        assertFalse(mLock.lockNow());
        mLock.lock(HOUR);
        assertFalse(mLock.lockNow());
        mLock.startUnlock();
        assertFalse(mLock.lockNow());
    }

    // --- Duration ---

    @Test
    public void durationCanBeChangedOnlyFromNoLockOrUnlocked() {
        assertTrue(mLock.setDuration(4 * HOUR)); // NO_LOCK: pre-fill for the next lock.
        assertTrue(mLock.lock(4 * HOUR));

        assertFalse(mLock.setDuration(5 * MINUTE)); // LOCKED
        mLock.startUnlock();
        assertFalse(mLock.setDuration(5 * MINUTE)); // UNLOCKING
        assertEquals(4 * HOUR, mLock.durationMs());

        mClock.elapsed += 4 * HOUR;
        assertEquals(State.UNLOCKED, mLock.state());
        assertTrue(mLock.setDuration(5 * MINUTE));
        assertEquals(5 * MINUTE, mLock.durationMs());
    }

    @Test
    public void changedDurationAppliesAtTheNextLock() {
        unlock();
        assertTrue(mLock.setDuration(12 * HOUR));
        assertEquals(State.UNLOCKED, mLock.state());
        assertTrue(mLock.lockNow());
        assertEquals(12 * HOUR, mLock.durationMs());
        mLock.startUnlock();
        assertEquals(12 * HOUR, mLock.unlockRemainingMs());
    }

    @Test
    public void setDurationValidatesBounds() {
        assertFalse(mLock.setDuration(0));
        assertFalse(mLock.setDuration(MINUTE - 1));
        assertFalse(mLock.setDuration(24 * HOUR + 1));
        assertEquals(LockConstants.DEFAULT_DURATION_MS, mLock.durationMs());
    }

    @Test
    public void relockingPrefillsThePreviousDuration() {
        unlock();
        mLock.lockNow();
        assertEquals(30 * MINUTE, mLock.durationMs());
        // The lock is on again, so it cannot be "locked" a second time or re-timed.
        assertFalse(mLock.lock(HOUR));
        assertEquals(30 * MINUTE, mLock.durationMs());
    }

    // --- Removing the lock ---

    @Test
    public void lockCanOnlyBeRemovedFromUnlocked() {
        assertFalse(mLock.removeLock()); // NO_LOCK
        mLock.lock(HOUR);
        assertFalse(mLock.removeLock()); // LOCKED
        mLock.startUnlock();
        assertFalse(mLock.removeLock()); // UNLOCKING
        mClock.elapsed += HOUR;
        assertTrue(mLock.removeLock());
        assertEquals(State.NO_LOCK, mLock.state());
        assertTrue(mLock.canModify());
    }

    @Test
    public void afterRemovalTheDurationIsStillRemembered() {
        mLock.lock(4 * HOUR);
        mLock.startUnlock();
        mClock.elapsed += 4 * HOUR;
        mLock.removeLock();
        assertEquals(4 * HOUR, mLock.durationMs());
        assertTrue(mLock.lock(4 * HOUR));
    }

    @Test
    public void lockFromUnlockedRelocksWithTheNewDuration() {
        unlock();
        assertTrue(mLock.lock(2 * HOUR));
        assertEquals(State.LOCKED, mLock.state());
        assertEquals(2 * HOUR, mLock.durationMs());
    }

    @Test
    public void lockIsRejectedWhileLockedOrUnlocking() {
        mLock.lock(HOUR);
        assertFalse(mLock.lock(5 * MINUTE));
        mLock.startUnlock();
        assertFalse(mLock.lock(5 * MINUTE));
        assertEquals(HOUR, mLock.durationMs());
    }

    // --- Snapshots ---

    @Test
    public void snapshotOfNoLock() {
        final LockSnapshot snapshot = mLock.snapshot();
        assertFalse(snapshot.lockEnabled);
        assertFalse(snapshot.unlocked);
    }

    @Test
    public void snapshotOfLocked() {
        mLock.lock(HOUR);
        final LockSnapshot snapshot = mLock.snapshot();
        assertTrue(snapshot.lockEnabled);
        assertFalse(snapshot.unlocked);
        assertEquals(HOUR, snapshot.durationMs);
    }

    @Test
    public void aCountdownInProgressIsStoredAsLocked() {
        mLock.lock(HOUR);
        mLock.startUnlock();
        mClock.elapsed += 59 * MINUTE;
        final LockSnapshot snapshot = mLock.snapshot();
        assertTrue(snapshot.lockEnabled);
        assertFalse(snapshot.unlocked);

        // Restarting from that snapshot (the process died) leaves the lock on, with no progress.
        final SettingsLock restored = new SettingsLock(mClock, false, snapshot);
        assertEquals(State.LOCKED, restored.state());
        restored.startUnlock();
        assertEquals(HOUR, restored.unlockRemainingMs());
    }

    @Test
    public void unlockedSnapshotRestoresWhileTheWindowIsOpen() {
        unlock();
        mClock.elapsed += MINUTE;
        final LockSnapshot snapshot = mLock.snapshot();
        assertTrue(snapshot.unlocked);

        final SettingsLock restored = new SettingsLock(mClock, false, snapshot);
        assertEquals(State.UNLOCKED, restored.state());
        assertEquals(LockConstants.EDIT_WINDOW_MS - MINUTE, restored.editWindowRemainingMs());
    }

    @Test
    public void unlockedSnapshotComesBackLockedAfterTheWindow() {
        unlock();
        final LockSnapshot snapshot = mLock.snapshot();
        mClock.elapsed += LockConstants.EDIT_WINDOW_MS;
        assertEquals(State.LOCKED, new SettingsLock(mClock, false, snapshot).state());
    }

    @Test
    public void unlockedSnapshotComesBackLockedAfterAReboot() {
        unlock();
        final LockSnapshot snapshot = mLock.snapshot();
        mClock.boot++;
        mClock.elapsed = 5; // The monotonic clock restarts at boot.
        assertEquals(State.LOCKED, new SettingsLock(mClock, false, snapshot).state());
    }

    @Test
    public void lockedSnapshotRestoresLockedWithItsDuration() {
        mLock.lock(12 * HOUR);
        final SettingsLock restored = new SettingsLock(mClock, false, mLock.snapshot());
        assertEquals(State.LOCKED, restored.state());
        assertEquals(12 * HOUR, restored.durationMs());
        assertFalse(restored.canModify());
    }

    @Test
    public void corruptStoredStateRestoresLockedWithTheDefaultDuration() {
        final SettingsLock restored = new SettingsLock(mClock, false, LockSnapshot.failClosed());
        assertEquals(State.LOCKED, restored.state());
        assertEquals(30 * MINUTE, restored.durationMs());
        assertFalse(restored.canModify());
    }
}
