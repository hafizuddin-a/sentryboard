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

package rkr.simplekeyboard.inputmethod.latin.protection;

import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsLock;

/** Decides whether the loss of uninstall protection counts as tampering. Pure Java. */
public final class TamperPolicy {
    private TamperPolicy() {
        // Intentional empty constructor for utility class.
    }

    /**
     * The in-app "Remove uninstall protection" button only works when the settings are editable
     * (no lock, or unlocked). So if the device admin is switched off while the lock is on (locked,
     * or a countdown is running), it was switched off by some other route, such as the system's
     * Device admin apps screen.
     */
    public static boolean isTamper(final SettingsLock.State lockState) {
        return lockState == SettingsLock.State.LOCKED || lockState == SettingsLock.State.UNLOCKING;
    }
}
