package rkr.simplekeyboard.inputmethod.latin.wordblock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BrowserAppsTest {
    @Test
    public void knownBrowsersAreRecognised() {
        for (final String pkg : new String[] {"com.android.chrome", "org.mozilla.firefox",
                "com.brave.browser", "com.microsoft.emmx", "com.sec.android.app.sbrowser",
                "com.duckduckgo.mobile.android", "com.ecosia.android", "com.opera.browser"}) {
            assertTrue(pkg, BrowserApps.isBrowser(pkg));
        }
    }

    @Test
    public void otherAppsAndMissingPackagesAreNotBrowsers() {
        assertFalse(BrowserApps.isBrowser("org.telegram.messenger"));
        assertFalse(BrowserApps.isBrowser("com.google.android.contacts"));
        assertFalse(BrowserApps.isBrowser("com.example.sentryboard"));
        assertFalse(BrowserApps.isBrowser(""));
        assertFalse(BrowserApps.isBrowser(null));
    }
}
