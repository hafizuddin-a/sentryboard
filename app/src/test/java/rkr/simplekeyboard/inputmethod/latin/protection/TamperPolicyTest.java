package rkr.simplekeyboard.inputmethod.latin.protection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsLock.State;

public class TamperPolicyTest {
    @Test
    public void removalWhileTheLockIsOnCountsAsTampering() {
        assertTrue(TamperPolicy.isTamper(State.LOCKED));
        assertTrue(TamperPolicy.isTamper(State.UNLOCKING));
    }

    @Test
    public void removalWhenSettingsAreEditableIsNotTampering() {
        assertFalse(TamperPolicy.isTamper(State.NO_LOCK));
        assertFalse(TamperPolicy.isTamper(State.UNLOCKED));
    }
}
