package rkr.simplekeyboard.inputmethod.latin.protection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static rkr.simplekeyboard.inputmethod.latin.protection.ForegroundTracker.ACTIVITY_PAUSED;
import static rkr.simplekeyboard.inputmethod.latin.protection.ForegroundTracker.ACTIVITY_RESUMED;
import static rkr.simplekeyboard.inputmethod.latin.protection.ForegroundTracker.ACTIVITY_STOPPED;

import org.junit.Test;

public class ForegroundTrackerTest {
    private static final String SETTINGS = "com.android.settings";
    private static final String ADMIN = "com.android.settings.applications.specialaccess.deviceadmin.DeviceAdminAdd";
    private static final String LAUNCHER = "com.launcher";
    private static final GuardTargets TARGETS = new GuardTargets().add(SETTINGS, "DeviceAdminAdd");

    private final ForegroundTracker mTracker = new ForegroundTracker();

    @Test
    public void nothingIsKnownAtFirst() {
        assertNull(mTracker.packageName());
        assertFalse(mTracker.isForeground(TARGETS));
    }

    @Test
    public void aResumedActivityIsTheForegroundOne() {
        mTracker.onEvent(ACTIVITY_RESUMED, SETTINGS, ADMIN);
        assertEquals(SETTINGS, mTracker.packageName());
        assertEquals(ADMIN, mTracker.className());
        assertTrue(mTracker.isForeground(TARGETS));
    }

    @Test
    public void pausingItEndsTheForeground() {
        mTracker.onEvent(ACTIVITY_RESUMED, SETTINGS, ADMIN);
        mTracker.onEvent(ACTIVITY_PAUSED, SETTINGS, ADMIN);
        assertFalse(mTracker.isForeground(TARGETS));
        assertNull(mTracker.packageName());
    }

    @Test
    public void stoppingItEndsTheForegroundToo() {
        mTracker.onEvent(ACTIVITY_RESUMED, SETTINGS, ADMIN);
        mTracker.onEvent(ACTIVITY_STOPPED, SETTINGS, ADMIN);
        assertFalse(mTracker.isForeground(TARGETS));
    }

    @Test
    public void goingHomeMovesTheForegroundAway() {
        mTracker.onEvent(ACTIVITY_RESUMED, SETTINGS, ADMIN);
        mTracker.onEvent(ACTIVITY_PAUSED, SETTINGS, ADMIN);
        mTracker.onEvent(ACTIVITY_RESUMED, LAUNCHER, "Home");
        assertEquals(LAUNCHER, mTracker.packageName());
        assertFalse(mTracker.isForeground(TARGETS));
    }

    @Test
    public void aLatePausedForAnOldActivityDoesNotClearANewerOne() {
        mTracker.onEvent(ACTIVITY_RESUMED, SETTINGS, "com.android.settings.Settings");
        mTracker.onEvent(ACTIVITY_RESUMED, SETTINGS, ADMIN);          // Opened the admin screen...
        mTracker.onEvent(ACTIVITY_PAUSED, SETTINGS, "com.android.settings.Settings"); // ...late pause.
        assertTrue(mTracker.isForeground(TARGETS));
    }

    @Test
    public void aPauseForADifferentActivityOfTheSamePackageIsIgnored() {
        mTracker.onEvent(ACTIVITY_RESUMED, SETTINGS, ADMIN);
        mTracker.onEvent(ACTIVITY_STOPPED, SETTINGS, "com.android.settings.Other");
        assertTrue(mTracker.isForeground(TARGETS));
    }

    @Test
    public void otherEventTypesAreIgnored() {
        mTracker.onEvent(ACTIVITY_RESUMED, SETTINGS, ADMIN);
        mTracker.onEvent(15, SETTINGS, ADMIN); // SCREEN_INTERACTIVE
        mTracker.onEvent(16, null, null);      // SCREEN_NON_INTERACTIVE
        mTracker.onEvent(7, SETTINGS, ADMIN);  // USER_INTERACTION
        assertTrue(mTracker.isForeground(TARGETS));
    }

    @Test
    public void eventsWithoutAPackageAreIgnored() {
        mTracker.onEvent(ACTIVITY_RESUMED, null, ADMIN);
        assertNull(mTracker.packageName());
    }

    @Test
    public void resetForgetsEverything() {
        mTracker.onEvent(ACTIVITY_RESUMED, SETTINGS, ADMIN);
        mTracker.reset();
        assertFalse(mTracker.isForeground(TARGETS));
    }

    @Test
    public void aFullSessionTracksEnteringAndLeavingTheAdminScreen() {
        mTracker.onEvent(ACTIVITY_RESUMED, LAUNCHER, "Home");
        assertFalse(mTracker.isForeground(TARGETS));
        mTracker.onEvent(ACTIVITY_PAUSED, LAUNCHER, "Home");
        mTracker.onEvent(ACTIVITY_RESUMED, SETTINGS, "com.android.settings.Settings");
        assertFalse(mTracker.isForeground(TARGETS));
        mTracker.onEvent(ACTIVITY_PAUSED, SETTINGS, "com.android.settings.Settings");
        mTracker.onEvent(ACTIVITY_RESUMED, SETTINGS, ADMIN);
        assertTrue(mTracker.isForeground(TARGETS));
        mTracker.onEvent(ACTIVITY_PAUSED, SETTINGS, ADMIN);
        mTracker.onEvent(ACTIVITY_RESUMED, LAUNCHER, "Home");
        assertFalse(mTracker.isForeground(TARGETS));
    }
}
