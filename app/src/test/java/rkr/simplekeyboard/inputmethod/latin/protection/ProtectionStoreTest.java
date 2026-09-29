package rkr.simplekeyboard.inputmethod.latin.protection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import rkr.simplekeyboard.inputmethod.latin.wordblock.FakeSharedPreferences;

public class ProtectionStoreTest {
    private final FakeSharedPreferences mPrefs = new FakeSharedPreferences();
    private final ProtectionStore mStore = new ProtectionStore(mPrefs);

    @Test
    public void nothingIsPendingAtFirst() {
        assertFalse(mStore.isTamperPending());
        assertEquals(0, mStore.tamperDetectedAt());
    }

    @Test
    public void recordedTamperingIsRememberedUntilCleared() {
        mStore.recordTamper(1_700_000_000_000L);
        assertTrue(mStore.isTamperPending());
        assertEquals(1_700_000_000_000L, mStore.tamperDetectedAt());
        assertTrue(new ProtectionStore(mPrefs).isTamperPending()); // Survives a restart.

        mStore.clearTamper();
        assertFalse(mStore.isTamperPending());
    }

    @Test
    public void aZeroTimestampStillCountsAsPending() {
        mStore.recordTamper(0);
        assertTrue(mStore.isTamperPending());
    }

    @Test
    public void unreadableStorageReportsNothingRatherThanCrashing() {
        mPrefs.putRaw("tamper_detected_at", "garbage");
        assertFalse(mStore.isTamperPending());
    }
}
