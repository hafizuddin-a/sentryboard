package rkr.simplekeyboard.inputmethod.latin.wordblock.lock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import rkr.simplekeyboard.inputmethod.latin.wordblock.FakeSharedPreferences;

public class SettingsLockStoreTest {
    private final FakeSharedPreferences mPrefs = new FakeSharedPreferences();
    private final SettingsLockStore mStore = new SettingsLockStore(mPrefs);

    @Test
    public void freshStorageMeansNoLock() {
        assertFalse(mStore.load().lockEnabled);
    }

    @Test
    public void saveThenLoad() {
        final LockSnapshot saved = new LockSnapshot(true, 7_200_000L, true, 555L, 3);
        assertTrue(mStore.save(saved));
        assertEquals(saved, mStore.load());
    }

    @Test
    public void aWrongTypeOnDiskFailsClosed() {
        mStore.save(new LockSnapshot(true, 7_200_000L, false, 0, 0));
        mPrefs.putRaw(LockSnapshot.KEY_DURATION_MS, "oops");
        final LockSnapshot loaded = mStore.load();
        assertTrue(loaded.lockEnabled);
        assertEquals(LockConstants.DEFAULT_DURATION_MS, loaded.durationMs);
    }

    @Test
    public void aMissingFieldOnDiskFailsClosed() {
        mStore.save(new LockSnapshot(false, 7_200_000L, false, 0, 0));
        mPrefs.raw().remove(LockSnapshot.KEY_ENABLED);
        assertTrue(mStore.load().lockEnabled);
    }

    @Test
    public void unreadableStorageFailsClosed() {
        mPrefs.failReads = true;
        final LockSnapshot loaded = mStore.load();
        assertTrue(loaded.lockEnabled);
        assertFalse(loaded.unlocked);
    }

    @Test
    public void reportsAFailedWrite() {
        mPrefs.failWrites = true;
        assertFalse(mStore.save(new LockSnapshot(true, 60_000L, false, 0, 0)));
    }

    @Test
    public void resetNoteIsShownOnce() {
        assertFalse(mStore.consumeResetNote());
        mStore.setResetNotePending(true);
        assertTrue(mStore.consumeResetNote());
        assertFalse(mStore.consumeResetNote());
    }

    @Test
    public void resetNoteDoesNotDisturbTheLockState() {
        mStore.save(new LockSnapshot(true, 7_200_000L, false, 0, 0));
        mStore.setResetNotePending(true);
        assertTrue(mStore.load().lockEnabled);
        assertEquals(7_200_000L, mStore.load().durationMs);
    }
}
