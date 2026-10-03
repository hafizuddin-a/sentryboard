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

package rkr.simplekeyboard.inputmethod.latin.settings;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsGate;

/**
 * Closes an open unlock window as soon as the user leaves the settings, meaning the whole
 * {@link SettingsActivity} and not one of its sub screens: moving between "Word filter" and
 * "Uninstall protection" is still being on the settings page.
 *
 * It is a lifecycle callback rather than code in the activity so that upstream's
 * {@code SettingsActivity} stays untouched. It listens for {@code onPause}, which arrives the
 * moment the page is covered; {@code onStop} can arrive seconds late, after a newer unlock has
 * completed. Rotating the screen recreates the activity and is not leaving it.
 */
public final class SettingsPageWatcher implements Application.ActivityLifecycleCallbacks {
    private static boolean sInstalled;

    private final SettingsGate mGate;

    private SettingsPageWatcher(final SettingsGate gate) {
        mGate = gate;
    }

    /** Starts watching, once per process. */
    public static synchronized void install(final Application application) {
        if (sInstalled) {
            return;
        }
        sInstalled = true;
        application.registerActivityLifecycleCallbacks(
                new SettingsPageWatcher(SettingsGate.getInstance(application)));
    }

    @Override
    public void onActivityPaused(final Activity activity) {
        if (activity instanceof SettingsActivity && !activity.isChangingConfigurations()) {
            mGate.onSettingsPageLeft();
        }
    }

    @Override
    public void onActivityCreated(final Activity activity, final Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(final Activity activity) {
    }

    @Override
    public void onActivityResumed(final Activity activity) {
    }

    @Override
    public void onActivityStopped(final Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(final Activity activity, final Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(final Activity activity) {
    }
}
