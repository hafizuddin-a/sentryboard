/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin.wordblock;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Which apps are web browsers, for the "strict matching in browsers" setting. The keyboard is told
 * the package of the app it is typing in; it cannot tell a search box from any other field on a web
 * page, and a page shown inside another app (a chat app's built-in browser) reports that app.
 */
public final class BrowserApps {
    private static final Set<String> PACKAGES = new HashSet<>(Arrays.asList(
            // Chrome and Chromium
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
            "com.google.android.apps.chrome", "org.chromium.chrome", "org.chromium.webview_shell",
            // Firefox, Tor, Focus
            "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
            "org.mozilla.focus", "org.mozilla.klar", "org.torproject.torbrowser",
            // Others
            "com.brave.browser", "com.brave.browser_beta", "com.microsoft.emmx",
            "com.sec.android.app.sbrowser", "com.opera.browser", "com.opera.mini.native",
            "com.duckduckgo.mobile.android", "com.vivaldi.browser", "com.kiwibrowser.browser",
            "com.ecosia.android", "com.yandex.browser", "com.UCMobile.intl",
            "com.mi.globalbrowser", "com.huawei.browser", "com.android.browser"));

    private BrowserApps() {
    }

    /** @return true if {@code packageName} is a known web browser. */
    public static boolean isBrowser(final String packageName) {
        return packageName != null && PACKAGES.contains(packageName);
    }
}
