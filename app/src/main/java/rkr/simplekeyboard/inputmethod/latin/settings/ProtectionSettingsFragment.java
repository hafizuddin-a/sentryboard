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

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.widget.Toast;

import java.util.EnumMap;
import java.util.Map;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.protection.GuardCondition;
import rkr.simplekeyboard.inputmethod.latin.protection.GuardCondition.Requirement;
import rkr.simplekeyboard.inputmethod.latin.protection.GuardDiagnostic;
import rkr.simplekeyboard.inputmethod.latin.protection.GuardEnvironment;
import rkr.simplekeyboard.inputmethod.latin.protection.GuardLabels;
import rkr.simplekeyboard.inputmethod.latin.protection.GuardService;
import rkr.simplekeyboard.inputmethod.latin.protection.ProtectionController;
import rkr.simplekeyboard.inputmethod.latin.protection.ProtectionStore;
import rkr.simplekeyboard.inputmethod.latin.protection.TamperNotifier;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsGate;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsLock;

/**
 * "Uninstall protection" settings sub screen: turning the device admin on and off, the warning
 * shown if it was turned off outside the app, and the guard's setup checklist.
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
    private static final int REQUEST_NOTIFICATIONS = 2;

    private SettingsGate mGate;
    private ProtectionStore mStore;
    private PreferenceCategory mCategory;
    private Preference mTamper;
    private Preference mStatus;
    private Preference mEnable;
    private Preference mRemove;
    private PreferenceCategory mGuardCategory;
    private Preference mGuardState;
    private final Map<Requirement, Preference> mRows = new EnumMap<>(Requirement.class);
    private Preference mDiagnostic;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTick = this::refresh;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_protection);
        final Context context = getActivity();
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
        buildGuardCategory(context);
    }

    private Preference ordered(final String key, final int order) {
        final Preference preference = findPreference(key);
        preference.setOrder(order);
        return preference;
    }

    /** The guard's status and its setup checklist, built in code because its rows are uniform. */
    private void buildGuardCategory(final Context context) {
        mGuardCategory = new PreferenceCategory(context);
        mGuardCategory.setTitle(R.string.guard_setup_category);
        getPreferenceScreen().addPreference(mGuardCategory);

        mGuardState = new Preference(context);
        mGuardState.setSelectable(false);
        mGuardCategory.addPreference(mGuardState);

        for (final Requirement requirement : Requirement.values()) {
            final Preference row = new Preference(context);
            row.setPersistent(false);
            row.setOnPreferenceClickListener(p -> { fix(requirement); return true; });
            mRows.put(requirement, row);
            mGuardCategory.addPreference(row);
        }

        final Preference battery = new Preference(context);
        battery.setTitle(R.string.guard_battery);
        battery.setSummary(R.string.guard_battery_summary);
        battery.setOnPreferenceClickListener(p -> {
            open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            return true;
        });
        mGuardCategory.addPreference(battery);

        final boolean debuggable =
                (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (debuggable) {
            mDiagnostic = new Preference(context);
            mDiagnostic.setTitle(R.string.guard_diagnostic);
            mDiagnostic.setSummary(R.string.guard_diagnostic_summary);
            mDiagnostic.setOnPreferenceClickListener(p -> {
                if (!GuardDiagnostic.toggle(getActivity()) && !GuardDiagnostic.isShowing()) {
                    Toast.makeText(getActivity(), R.string.guard_diagnostic_needs,
                            Toast.LENGTH_LONG).show();
                }
                return true;
            });
            mGuardCategory.addPreference(mDiagnostic);
        }
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

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions,
            final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            if (!GuardEnvironment.notificationsEnabled(getActivity())) {
                // Denied, and Android will not ask again: send the user to the switch instead.
                openNotificationSettings();
            }
            refresh();
        }
    }

    private void refresh() {
        final Context context = getActivity();
        mHandler.removeCallbacks(mTick);
        final boolean active = ProtectionController.isAdminActive(context);
        final boolean tamper = mStore.isTamperPending() && !active;
        final boolean mayRemove = mGate.canModifyFilterSettings();
        final SettingsLock.State lock = mGate.state();

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
        refreshGuard(context, lock);

        // Locking or activating turns the guard on; start it if it is not running.
        GuardService.ensureRunning(context);
        // While the unlock window is open, re-check every second so "Remove" goes back to disabled
        // the moment the window ends. (The data layer refuses either way.)
        if (lock == SettingsLock.State.UNLOCKED) {
            mHandler.postDelayed(mTick, 1000);
        }
    }

    private void refreshGuard(final Context context, final SettingsLock.State lock) {
        final GuardCondition.Requirements requirements =
                GuardEnvironment.checkRequirements(context);
        final boolean degraded = GuardCondition.isDegraded(requirements, lock);

        if (degraded) {
            mGuardState.setTitle(red(getString(R.string.guard_state_degraded)));
            mGuardState.setSummary(R.string.guard_state_degraded_summary);
        } else if (GuardService.isCoverBlocked()) {
            mGuardState.setTitle(red(getString(R.string.guard_state_blocked)));
            mGuardState.setSummary(R.string.guard_state_blocked_summary);
        } else if (lock == SettingsLock.State.NO_LOCK) {
            mGuardState.setTitle(R.string.guard_state_idle);
            mGuardState.setSummary(R.string.guard_state_idle_summary);
        } else if (lock == SettingsLock.State.UNLOCKED) {
            mGuardState.setTitle(R.string.guard_state_idle);
            mGuardState.setSummary(R.string.guard_state_unlocked_summary);
        } else {
            mGuardState.setTitle(R.string.guard_state_working);
            mGuardState.setSummary(R.string.guard_state_working_summary);
        }

        for (final Requirement requirement : Requirement.values()) {
            final boolean met = !requirements.missing().contains(requirement);
            final Preference row = mRows.get(requirement);
            final String title = (met ? "\u2713 " : "\u2717 ")
                    + getString(GuardLabels.title(requirement));
            row.setTitle(!met && degraded ? red(title) : title);
            row.setSummary(getString(summaryOf(requirement))
                    + (met ? "" : " " + getString(R.string.guard_row_fix)));
        }
    }

    private static int summaryOf(final Requirement requirement) {
        switch (requirement) {
            case DEVICE_ADMIN: return R.string.guard_req_device_admin_summary;
            case USAGE_ACCESS: return R.string.guard_req_usage_access_summary;
            case OVERLAY: return R.string.guard_req_overlay_summary;
            default: return R.string.guard_req_notifications_summary;
        }
    }

    private static CharSequence red(final String text) {
        final SpannableString span = new SpannableString(text);
        span.setSpan(new ForegroundColorSpan(Color.rgb(0xD3, 0x2F, 0x2F)), 0, text.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    private void setVisible(final Preference preference, final boolean visible) {
        final boolean present = mCategory.findPreference(preference.getKey()) != null;
        if (visible && !present) {
            mCategory.addPreference(preference);
        } else if (!visible && present) {
            mCategory.removePreference(preference);
        }
    }

    // ---- Actions ------------------------------------------------------------------------------

    private void activate() {
        startActivityForResult(ProtectionController.activationIntent(getActivity()),
                REQUEST_ACTIVATE);
    }

    /** Opens the system screen where a missing requirement can be allowed. */
    private void fix(final Requirement requirement) {
        final Uri self = Uri.parse("package:" + getActivity().getPackageName());
        switch (requirement) {
            case DEVICE_ADMIN:
                activate();
                break;
            case USAGE_ACCESS:
                // The package URI opens this app's own row on recent Android versions.
                open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, self),
                        new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                break;
            case OVERLAY:
                open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, self),
                        new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
                break;
            default:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && getActivity().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS},
                            REQUEST_NOTIFICATIONS);
                } else {
                    openNotificationSettings();
                }
                break;
        }
    }

    private void openNotificationSettings() {
        open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getActivity().getPackageName()));
    }

    /** Starts the first of the intents that some installed activity can handle. */
    private void open(final Intent... intents) {
        for (final Intent intent : intents) {
            try {
                startActivity(intent);
                return;
            } catch (ActivityNotFoundException e) {
                // Try the next one.
            }
        }
        Toast.makeText(getActivity(), R.string.protection_remove_failed, Toast.LENGTH_SHORT).show();
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
