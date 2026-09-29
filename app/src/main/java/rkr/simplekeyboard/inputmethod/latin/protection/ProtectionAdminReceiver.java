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

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsGate;

/**
 * The device admin component. Being an active device admin is what makes Android require
 * deactivation before this app can be uninstalled. It declares no policies, so it cannot lock,
 * wipe or otherwise control the phone.
 */
public final class ProtectionAdminReceiver extends DeviceAdminReceiver {
    @Override
    public CharSequence onDisableRequested(final Context context, final Intent intent) {
        return context.getString(R.string.protection_disable_message);
    }

    @Override
    public void onEnabled(final Context context, final Intent intent) {
        // Protection is on again, so whatever was reported no longer applies.
        ProtectionStore.get(context).clearTamper();
        TamperNotifier.cancel(context);
        GuardService.ensureRunning(context);
    }

    @Override
    public void onDisabled(final Context context, final Intent intent) {
        try {
            if (TamperPolicy.isTamper(SettingsGate.getInstance(context).state())) {
                ProtectionStore.get(context).recordTamper(System.currentTimeMillis());
                TamperNotifier.post(context);
            }
        } catch (RuntimeException e) {
            // Never let a failure here matter; the app also checks on its own when opened.
        }
    }
}
