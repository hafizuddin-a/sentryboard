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

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.widget.Toast;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.protection.ProtectionController;
import rkr.simplekeyboard.inputmethod.latin.protection.ProtectionStore;
import rkr.simplekeyboard.inputmethod.latin.protection.TamperNotifier;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsGate;

/**
 * "Uninstall protection" settings sub screen: turning the device admin on and off, and the warning
 * shown if it was turned off outside the app.
 *
 * Removing protection goes through {@link SettingsGate}, so the unlock countdown is the one gate
 * for both editing the word filter and removing protection.
 */
public final class ProtectionSettingsFragment extends SubScreenFragment {
    private static final String KEY_CATEGORY = "prot_category";
    private static final String KEY_TAMPER = "prot_tamper";
    private static final String KEY_STATUS = "prot_status";
    private static final String KEY_ENABLE = "prot_enable";
    private static final String KEY_REMOVE = "prot_remove";
    private static final String KEY_ABOUT = "prot_about";
    private static final int REQUEST_ACTIVATE = 1;

    private SettingsGate mGate;
    private ProtectionStore mStore;
    private PreferenceCategory mCategory;
    private Preference mTamper;
    private Preference mStatus;
    private Preference mEnable;
    private Preference mRemove;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTick = this::refresh;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_protection);
        final Context context = getActivity();
        SettingsPageWatcher.install(getActivity());
        mGate = SettingsGate.getInstance(context);
        mStore = ProtectionStore.get(context);

        mCategory = (PreferenceCategory) findPreference(KEY_CATEGORY);
        mCategory.setOrderingAsAdded(false);
        mTamper = ordered(KEY_TAMPER, 0);
        mStatus = ordered(KEY_STATUS, 1);
        mEnable = ordered(KEY_ENABLE, 2);
        mRemove = ordered(KEY_REMOVE, 3);
        ordered(KEY_ABOUT, 4);

        mTamper.setOnPreferenceClickListener(p -> { activate(); return true; });
        mEnable.setOnPreferenceClickListener(p -> { activate(); return true; });
        mRemove.setOnPreferenceClickListener(p -> { confirmRemove(); return true; });
    }

    private Preference ordered(final String key, final int order) {
        final Preference preference = findPreference(key);
        preference.setOrder(order);
        return preference;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onPause() {
        mHandler.removeCallbacks(mTick);
        super.onPause();
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ACTIVATE) {
            refresh();
        }
    }

    private void refresh() {
        final Context context = getActivity();
        final boolean active = ProtectionController.isAdminActive(context);
        final boolean tamper = mStore.isTamperPending() && !active;
        final boolean mayRemove = mGate.canModifyFilterSettings();

        setVisible(mTamper, tamper);
        mStatus.setTitle(active ? R.string.protection_status_on : R.string.protection_status_off);
        mStatus.setSummary(active ? R.string.protection_status_on_summary
                : R.string.protection_status_off_summary);
        setVisible(mEnable, !active);
        setVisible(mRemove, active);
        mRemove.setEnabled(mayRemove);
        mRemove.setSummary(mayRemove ? R.string.protection_remove_summary
                : R.string.protection_remove_locked);
        if (active) {
            TamperNotifier.cancel(context);
        }
        // While the unlock window is open, re-check every second so "Remove" goes back to disabled
        // the moment the window ends. (The data layer refuses either way.)
        mHandler.removeCallbacks(mTick);
        if (mGate.state() == rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsLock.State.UNLOCKED) {
            mHandler.postDelayed(mTick, 1000);
        }
    }

    private void setVisible(final Preference preference, final boolean visible) {
        final boolean present = mCategory.findPreference(preference.getKey()) != null;
        if (visible && !present) {
            mCategory.addPreference(preference);
        } else if (!visible && present) {
            mCategory.removePreference(preference);
        }
    }

    private void activate() {
        startActivityForResult(ProtectionController.activationIntent(getActivity()),
                REQUEST_ACTIVATE);
    }

    private void confirmRemove() {
        if (!mGate.canModifyFilterSettings()) {
            refresh();
            return;
        }
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.protection_remove_title)
                .setMessage(R.string.protection_remove_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.protection_remove, (dialog, which) -> {
                    if (!ProtectionController.removeProtection(getActivity())) {
                        Toast.makeText(getActivity(), R.string.protection_remove_failed,
                                Toast.LENGTH_LONG).show();
                    }
                    refresh();
                })
                .show();
    }
}
