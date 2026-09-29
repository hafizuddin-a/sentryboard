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

package rkr.simplekeyboard.inputmethod.latin;

import android.app.Application;

import rkr.simplekeyboard.inputmethod.latin.protection.GuardService;
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsPageWatcher;

/**
 * Runs once whenever the app's process starts: after a reboot, an update, or the system stopping
 * the app. It closes an open unlock window when the settings are left, and restarts the
 * uninstall-protection guard if it should be running.
 */
public final class SentryboardApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SettingsPageWatcher.install(this);
        GuardService.ensureRunning(this);
    }
}
