package rkr.simplekeyboard.inputmethod.latin.wordblock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FilterSettingsWriterTest {
    private final FakeSharedPreferences mPrefs = new FakeSharedPreferences();
    private boolean mLocked = false;
    private final FilterSettingsWriter mWriter =
            new FilterSettingsWriter(mPrefs, () -> !mLocked);

    @Test
    public void writesWhenNotLocked() {
        assertTrue(mWriter.setEnabled(false));
        assertTrue(mWriter.setIdleEnabled(true));
        assertTrue(mWriter.setIdleMillis(750));
        assertEquals(false, mPrefs.raw().get(FilterSettingsWriter.KEY_ENABLED));
        assertEquals(true, mPrefs.raw().get(FilterSettingsWriter.KEY_IDLE_ENABLED));
        assertEquals("750", mPrefs.raw().get(FilterSettingsWriter.KEY_IDLE_MS));
    }

    @Test
    public void refusesEveryWriteWhileLocked() {
        mLocked = true;
        assertFalse(mWriter.setEnabled(false));
        assertFalse(mWriter.setIdleEnabled(true));
        assertFalse(mWriter.setIdleMillis(750));
        assertTrue(mPrefs.raw().isEmpty());
    }

    @Test
    public void writesAgainOnceUnlocked() {
        mLocked = true;
        assertFalse(mWriter.setEnabled(false));
        mLocked = false;
        assertTrue(mWriter.setEnabled(false));
    }

    @Test
    public void idleDelayIsBounded() {
        assertFalse(mWriter.setIdleMillis(FilterSettingsWriter.MIN_IDLE_MS - 1));
        assertFalse(mWriter.setIdleMillis(FilterSettingsWriter.MAX_IDLE_MS + 1));
        assertFalse(mWriter.setIdleMillis(-5));
        assertTrue(mWriter.setIdleMillis(FilterSettingsWriter.MIN_IDLE_MS));
        assertTrue(mWriter.setIdleMillis(FilterSettingsWriter.MAX_IDLE_MS));
    }

    @Test
    public void reportsAFailedWrite() {
        mPrefs.failWrites = true;
        assertFalse(mWriter.setEnabled(false));
    }
}
