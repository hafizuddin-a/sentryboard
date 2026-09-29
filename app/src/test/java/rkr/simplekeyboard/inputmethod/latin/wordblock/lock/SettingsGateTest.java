package rkr.simplekeyboard.inputmethod.latin.wordblock.lock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import rkr.simplekeyboard.inputmethod.latin.wordblock.FakeSharedPreferences;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsLock.State;

public class SettingsGateTest {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;

    private static final class FakeClock implements SettingsLock.Clock {
        long elapsed = 1_000_000L;
        int boot = 4;

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
    private FakeSharedPreferences mPrefs;
    private SettingsGate mGate;

    @Before
    public void setUp() {
        mClock = new FakeClock();
        mPrefs = new FakeSharedPreferences();
        mGate = newGate();
    }

    private SettingsGate newGate() {
        return new SettingsGate(mClock, false, new SettingsLockStore(mPrefs));
    }

    private void unlock() {
        assertTrue(mGate.lock(HOUR));
        assertTrue(mGate.startUnlock() != 0);
        mClock.elapsed += HOUR;
        assertEquals(State.UNLOCKED, mGate.state());
    }

    @Test
    public void startsEditable() {
        assertTrue(mGate.canModifyFilterSettings());
        assertEquals(State.NO_LOCK, mGate.state());
    }

    @Test
    public void lockingForbidsChangesAndSurvivesARestart() {
        assertTrue(mGate.lock(HOUR));
        assertFalse(mGate.canModifyFilterSettings());

        final SettingsGate restarted = newGate();
        assertEquals(State.LOCKED, restarted.state());
        assertFalse(restarted.canModifyFilterSettings());
        assertEquals(HOUR, restarted.durationMs());
    }

    @Test
    public void aCountdownInProgressDoesNotSurviveARestart() {
        mGate.lock(HOUR);
        mGate.startUnlock();
        mClock.elapsed += 59 * MINUTE;
        assertEquals(State.UNLOCKING, mGate.state());

        final SettingsGate restarted = newGate();
        assertEquals(State.LOCKED, restarted.state());
        restarted.startUnlock();
        assertEquals(HOUR, restarted.unlockRemainingMs());
    }

    @Test
    public void aCompletedCountdownIsPersistedAsUnlocked() {
        unlock();
        assertTrue(mGate.canModifyFilterSettings());
        assertEquals(State.UNLOCKED, newGate().state());
    }

    @Test
    public void anExpiredEditWindowIsPersistedAsLocked() {
        unlock();
        mClock.elapsed += LockConstants.EDIT_WINDOW_MS;
        assertFalse(mGate.canModifyFilterSettings());
        // A restart must not resurrect the window.
        mClock.elapsed = 5;
        assertEquals(State.LOCKED, newGate().state());
    }

    @Test
    public void aRebootRelocksTheEditWindow() {
        unlock();
        mClock.boot++;
        mClock.elapsed = 5;
        assertEquals(State.LOCKED, newGate().state());
    }

    @Test
    public void leavingTheSettingsPageRelocks() {
        unlock();
        mGate.onSettingsPageLeft();
        assertFalse(mGate.canModifyFilterSettings());
        assertEquals(State.LOCKED, mGate.state());
    }

    @Test
    public void leavingTheSettingsPageDoesNotDisturbACountdown() {
        mGate.lock(HOUR);
        mGate.startUnlock();
        mGate.onSettingsPageLeft();
        assertEquals(State.UNLOCKING, mGate.state());
    }

    @Test
    public void removingTheLockIsOnlyPossibleWhenUnlocked() {
        mGate.lock(HOUR);
        assertFalse(mGate.removeLock());
        mGate.startUnlock();
        assertFalse(mGate.removeLock());
        mClock.elapsed += HOUR;
        assertTrue(mGate.removeLock());
        assertTrue(mGate.canModifyFilterSettings());
        assertEquals(State.NO_LOCK, newGate().state());
    }

    @Test
    public void interruptionSaysSoNextTimeOnlyWhenThePageWasLeft() {
        mGate.lock(HOUR);
        long generation = mGate.startUnlock();
        mGate.interruptUnlock(generation, true);
        assertEquals(State.LOCKED, mGate.state());
        assertTrue(mGate.consumeResetNote());
        assertFalse(mGate.consumeResetNote());

        generation = mGate.startUnlock();
        mGate.interruptUnlock(generation, false); // Cancel button.
        assertFalse(mGate.consumeResetNote());
    }

    @Test
    public void startingACountdownClearsAStaleNote() {
        mGate.lock(HOUR);
        mGate.interruptUnlock(mGate.startUnlock(), true);
        mGate.startUnlock();
        assertFalse(mGate.consumeResetNote());
    }

    @Test
    public void aLockThatCannotBeSavedIsNotTurnedOn() {
        mPrefs.failWrites = true;
        assertFalse(mGate.lock(HOUR));
        assertEquals(State.NO_LOCK, mGate.state());
        assertTrue(mGate.canModifyFilterSettings());
    }

    @Test
    public void corruptStoredStateStartsLocked() {
        mGate.lock(HOUR);
        mPrefs.putRaw(LockSnapshot.KEY_ENABLED, "garbage");
        final SettingsGate restarted = newGate();
        assertEquals(State.LOCKED, restarted.state());
        assertFalse(restarted.canModifyFilterSettings());
        assertEquals(30 * MINUTE, restarted.durationMs());
    }

    @Test
    public void unreadableStoredStateStartsLocked() {
        mPrefs.failReads = true;
        final SettingsGate restarted = newGate();
        assertFalse(restarted.canModifyFilterSettings());
    }

    @Test
    public void aLateCallbackFromAnEarlierPageCannotCancelANewerCountdown() {
        mGate.lock(HOUR);
        final long first = mGate.startUnlock();
        mGate.interruptUnlock(first, true);           // The first page is left...
        final long second = mGate.startUnlock();      // ...and a new countdown is started.
        mGate.interruptUnlock(first, true);           // The first page's late onStop arrives.
        assertEquals(State.UNLOCKING, mGate.state());
        assertEquals(HOUR, mGate.unlockRemainingMs());
        mGate.interruptUnlock(second, true);
        assertEquals(State.LOCKED, mGate.state());
    }

    @Test
    public void interruptingACompletedCountdownDoesNothing() {
        mGate.lock(HOUR);
        final long generation = mGate.startUnlock();
        mClock.elapsed += HOUR;
        mGate.interruptUnlock(generation, true);
        assertEquals(State.UNLOCKED, mGate.state());
        assertFalse(mGate.consumeResetNote());
    }

    @Test
    public void generationZeroNeverInterruptsAnything() {
        mGate.lock(HOUR);
        mGate.startUnlock();
        mGate.interruptUnlock(0, true);
        assertEquals(State.UNLOCKING, mGate.state());
    }

    @Test
    public void durationOnlyChangesWhenAllowed() {
        assertTrue(mGate.setDuration(4 * HOUR));
        mGate.lock(4 * HOUR);
        assertFalse(mGate.setDuration(MINUTE));
        assertEquals(4 * HOUR, mGate.durationMs());
    }
}
