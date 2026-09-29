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

import java.util.ArrayList;
import java.util.List;

import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsLock;

/**
 * What the guard needs in order to work, and when it should be doing so. Pure Java.
 *
 * The guard covers the system's device-admin deactivation screen so that switching off uninstall
 * protection has to go through the settings lock's countdown. If the lock is off there is no way
 * to earn removal, so the guard is off too; the device admin alone still gives baseline friction.
 */
public final class GuardCondition {
    /** Things the guard depends on. */
    public enum Requirement { DEVICE_ADMIN, USAGE_ACCESS, OVERLAY, NOTIFICATIONS }

    private GuardCondition() {
        // Intentional empty constructor for utility class.
    }

    /** The current state of each requirement. */
    public static final class Requirements {
        public final boolean deviceAdmin;
        public final boolean usageAccess;
        public final boolean overlay;
        public final boolean notifications;

        public Requirements(final boolean deviceAdmin, final boolean usageAccess,
                final boolean overlay, final boolean notifications) {
            this.deviceAdmin = deviceAdmin;
            this.usageAccess = usageAccess;
            this.overlay = overlay;
            this.notifications = notifications;
        }

        /** @return whatever is missing, in the order the setup checklist shows them. */
        public List<Requirement> missing() {
            final List<Requirement> missing = new ArrayList<>();
            if (!deviceAdmin) missing.add(Requirement.DEVICE_ADMIN);
            if (!usageAccess) missing.add(Requirement.USAGE_ACCESS);
            if (!overlay) missing.add(Requirement.OVERLAY);
            if (!notifications) missing.add(Requirement.NOTIFICATIONS);
            return missing;
        }

        public boolean allMet() {
            return deviceAdmin && usageAccess && overlay && notifications;
        }
    }

    /**
     * @return true if the guard service should be running: protection is on and the settings lock
     * is enabled. (It may still be idle; see {@link #shouldGuard}.)
     */
    public static boolean shouldRunService(final boolean deviceAdminActive,
            final SettingsLock.State lockState) {
        return deviceAdminActive && lockState != SettingsLock.State.NO_LOCK;
    }

    /**
     * @return true if the guard should be watching the foreground screen right now: every
     * requirement is met, the settings lock is on and not open for editing, and the screen is on.
     */
    public static boolean shouldGuard(final Requirements requirements,
            final SettingsLock.State lockState, final boolean screenOn) {
        return requirements.allMet() && screenOn
                && lockState != SettingsLock.State.NO_LOCK
                && lockState != SettingsLock.State.UNLOCKED;
    }

    /**
     * @return true if the guard is meant to be working but something it needs is missing, so the
     * setup card should turn red and the notification say so.
     */
    public static boolean isDegraded(final Requirements requirements,
            final SettingsLock.State lockState) {
        return lockState != SettingsLock.State.NO_LOCK && !requirements.allMet();
    }
}
