package rkr.simplekeyboard.inputmethod.latin.protection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import rkr.simplekeyboard.inputmethod.latin.protection.GuardCondition.Requirement;
import rkr.simplekeyboard.inputmethod.latin.protection.GuardCondition.Requirements;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsLock.State;

public class GuardConditionTest {
    private static final Requirements ALL = new Requirements(true, true, true, true);

    @Test
    public void guardsOnlyWhenEverythingHolds() {
        // Enumerate every combination of the five inputs; exactly one should guard.
        int guarding = 0;
        for (int bits = 0; bits < 16; bits++) {
            final Requirements requirements = new Requirements((bits & 1) != 0, (bits & 2) != 0,
                    (bits & 4) != 0, (bits & 8) != 0);
            for (final State state : State.values()) {
                for (final boolean screenOn : new boolean[] {false, true}) {
                    final boolean expected = bits == 15 && screenOn
                            && (state == State.LOCKED || state == State.UNLOCKING);
                    assertEquals("bits=" + bits + " state=" + state + " screen=" + screenOn,
                            expected, GuardCondition.shouldGuard(requirements, state, screenOn));
                    if (expected) guarding++;
                }
            }
        }
        assertEquals(2, guarding); // LOCKED and UNLOCKING, with everything else on.
    }

    @Test
    public void neverGuardsWithoutALock() {
        assertFalse(GuardCondition.shouldGuard(ALL, State.NO_LOCK, true));
    }

    @Test
    public void neverGuardsWhileTheEditWindowIsOpen() {
        // Otherwise there would be no way to earn removal.
        assertFalse(GuardCondition.shouldGuard(ALL, State.UNLOCKED, true));
    }

    @Test
    public void neverGuardsWithTheScreenOff() {
        assertFalse(GuardCondition.shouldGuard(ALL, State.LOCKED, false));
    }

    @Test
    public void guardsWhileACountdownIsRunning() {
        assertTrue(GuardCondition.shouldGuard(ALL, State.UNLOCKING, true));
    }

    @Test
    public void everyMissingRequirementStopsTheGuard() {
        assertFalse(GuardCondition.shouldGuard(new Requirements(false, true, true, true), State.LOCKED, true));
        assertFalse(GuardCondition.shouldGuard(new Requirements(true, false, true, true), State.LOCKED, true));
        assertFalse(GuardCondition.shouldGuard(new Requirements(true, true, false, true), State.LOCKED, true));
        assertFalse(GuardCondition.shouldGuard(new Requirements(true, true, true, false), State.LOCKED, true));
    }

    @Test
    public void theServiceRunsWhenProtectionAndALockAreOn() {
        for (final State state : State.values()) {
            assertEquals(state != State.NO_LOCK, GuardCondition.shouldRunService(true, state));
            assertFalse(GuardCondition.shouldRunService(false, state));
        }
    }

    @Test
    public void degradedMeansItShouldBeWorkingButIsNot() {
        assertFalse(GuardCondition.isDegraded(ALL, State.LOCKED));
        assertTrue(GuardCondition.isDegraded(new Requirements(true, false, true, true), State.LOCKED));
        assertTrue(GuardCondition.isDegraded(new Requirements(true, true, true, false), State.UNLOCKED));
        // With no lock there is nothing for the guard to do, so nothing is degraded.
        assertFalse(GuardCondition.isDegraded(new Requirements(false, false, false, false), State.NO_LOCK));
    }

    @Test
    public void missingListsRequirementsInChecklistOrder() {
        assertEquals(Collections.emptyList(), ALL.missing());
        assertEquals(Arrays.asList(Requirement.USAGE_ACCESS, Requirement.NOTIFICATIONS),
                new Requirements(true, false, true, false).missing());
        assertEquals(Arrays.asList(Requirement.DEVICE_ADMIN, Requirement.USAGE_ACCESS,
                Requirement.OVERLAY, Requirement.NOTIFICATIONS),
                new Requirements(false, false, false, false).missing());
    }
}
