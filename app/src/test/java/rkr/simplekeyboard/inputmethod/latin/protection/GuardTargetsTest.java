package rkr.simplekeyboard.inputmethod.latin.protection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GuardTargetsTest {
    private static final GuardTargets DEFAULT =
            new GuardTargets().add("com.android.settings", "DeviceAdminAdd");

    @Test
    public void matchesTheStockDeviceAdminScreen() {
        assertTrue(DEFAULT.matches("com.android.settings",
                "com.android.settings.applications.specialaccess.deviceadmin.DeviceAdminAdd"));
        assertTrue(DEFAULT.matches("com.android.settings", "com.android.settings.DeviceAdminAdd"));
    }

    @Test
    public void matchesAWrappedOrRenamedClassContainingTheFragment() {
        assertTrue(DEFAULT.matches("com.android.settings", "com.vendor.ui.DeviceAdminAddActivity"));
    }

    @Test
    public void nearMissesDoNotMatch() {
        // Right package, wrong screen.
        assertFalse(DEFAULT.matches("com.android.settings", "com.android.settings.Settings"));
        assertFalse(DEFAULT.matches("com.android.settings", "com.android.settings.DeviceAdminSettings"));
        assertFalse(DEFAULT.matches("com.android.settings",
                "com.android.settings.Settings$DeviceAdminSettingsActivity"));
        // Right class, wrong package.
        assertFalse(DEFAULT.matches("com.example.settings", "x.DeviceAdminAdd"));
        assertFalse(DEFAULT.matches("com.android.settings.evil", "x.DeviceAdminAdd"));
        assertFalse(DEFAULT.matches("com.android.settingsx", "x.DeviceAdminAdd"));
        assertFalse(DEFAULT.matches("com.android", "x.DeviceAdminAdd"));
        // Case matters.
        assertFalse(DEFAULT.matches("com.android.settings", "x.deviceadminadd"));
        assertFalse(DEFAULT.matches("COM.ANDROID.SETTINGS", "x.DeviceAdminAdd"));
    }

    @Test
    public void nullsNeverMatch() {
        assertFalse(DEFAULT.matches(null, "DeviceAdminAdd"));
        assertFalse(DEFAULT.matches("com.android.settings", null));
        assertFalse(DEFAULT.matches(null, null));
    }

    @Test
    public void anEmptyListMatchesNothing() {
        assertFalse(new GuardTargets().matches("com.android.settings", "DeviceAdminAdd"));
    }

    @Test
    public void severalTargetsCanBeListed() {
        final GuardTargets targets = new GuardTargets()
                .add("com.android.settings", "DeviceAdminAdd")
                .add("com.samsung.android.settings", "DeviceAdminAdd")
                .add("com.miui.securitycenter", "AdminDetail");
        assertEquals(3, targets.size());
        assertTrue(targets.matches("com.samsung.android.settings", "a.DeviceAdminAdd"));
        assertTrue(targets.matches("com.miui.securitycenter", "a.AdminDetailActivity"));
        assertFalse(targets.matches("com.miui.securitycenter", "a.DeviceAdminAdd"));
    }

    @Test
    public void blankTargetsAreIgnored() {
        final GuardTargets targets = new GuardTargets().add("", "X").add("p", "").add(null, "X").add("p", null);
        assertEquals(0, targets.size());
    }
}
