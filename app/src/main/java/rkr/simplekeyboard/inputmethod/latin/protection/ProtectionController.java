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

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsGate;

/**
 * Turning uninstall protection (the device admin) on and off.
 *
 * Only ordinary, non-owner device admin APIs are used. Turning it off goes through
 * {@link SettingsGate}, so the unlock countdown is the one gate for both editing the filter and
 * removing protection.
 */
public final class ProtectionController {
    private ProtectionController() {
        // Intentional empty constructor for utility class.
    }

    public static ComponentName component(final Context context) {
        return new ComponentName(context, ProtectionAdminReceiver.class);
    }

    private static DevicePolicyManager manager(final Context context) {
        return (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public static boolean isAdminActive(final Context context) {
        final DevicePolicyManager manager = manager(context);
        return manager != null && manager.isAdminActive(component(context));
    }

    /** The system screen that asks the user to activate the admin. */
    public static Intent activationIntent(final Context context) {
        return new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        context.getString(R.string.protection_activate_explanation));
    }

    /**
     * Turns protection off. Refused unless the settings are editable, that is, there is no lock or
     * the unlock countdown has been completed.
     *
     * @return true if protection was removed (or was not on).
     */
    public static boolean removeProtection(final Context context) {
        if (!SettingsGate.getInstance(context).canModifyFilterSettings()) {
            return false;
        }
        final DevicePolicyManager manager = manager(context);
        if (manager == null) {
            return false;
        }
        if (manager.isAdminActive(component(context))) {
            manager.removeActiveAdmin(component(context));
        }
        return !manager.isAdminActive(component(context));
    }
}
