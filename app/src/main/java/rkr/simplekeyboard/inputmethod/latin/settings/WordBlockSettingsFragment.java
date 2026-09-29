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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.SwitchPreference;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.wordblock.BlockedWordStore;
import rkr.simplekeyboard.inputmethod.latin.wordblock.FilterSettingsWriter;
import rkr.simplekeyboard.inputmethod.latin.wordblock.WordFilter;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.DurationFormat;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsGate;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsLock;

/**
 * "Word filter" settings sub screen: the settings lock, the on/off switch, the optional removal
 * after a pause, and the editor for the blocked-word list.
 *
 * What is editable follows {@link SettingsGate#canModifyFilterSettings()}, which is re-read every
 * time the screen is shown and again before each change. That is only the first layer: the data
 * layer ({@link BlockedWordStore}, {@link FilterSettingsWriter}) refuses changes while locked
 * whatever this screen does. While locked the blocked-word list can still be viewed.
 */
public final class WordBlockSettingsFragment extends SubScreenFragment {
    private static final String KEY_LOCK_CATEGORY = "lock_category";
    private static final String KEY_LOCK_STATUS = "lock_status";
    private static final String KEY_LOCK_LOCK = "lock_lock";
    private static final String KEY_LOCK_UNLOCK = "lock_unlock";
    private static final String KEY_LOCK_NOW = "lock_now";
    private static final String KEY_LOCK_DURATION = "lock_duration";
    private static final String KEY_LOCK_REMOVE = "lock_remove";
    private static final String KEY_LIST_CATEGORY = "wordblock_list";
    private static final String KEY_ADD = "wordblock_add";

    private SettingsGate mGate;
    private BlockedWordStore mStore;
    private FilterSettingsWriter mWriter;
    private PreferenceCategory mLockCategory;
    private PreferenceCategory mListCategory;
    private Preference mStatus;
    private Preference mLock;
    private Preference mUnlock;
    private Preference mLockNow;
    private Preference mDuration;
    private Preference mRemove;
    private SwitchPreference mEnabled;
    private SwitchPreference mIdleEnabled;
    private ListPreference mIdleDelay;
    private final List<Preference> mEntryPreferences = new ArrayList<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTick = this::onTick;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_wordblock);
        final Context context = getActivity();
        mGate = SettingsGate.getInstance(context);
        mStore = BlockedWordStore.getInstance(context);
        mWriter = new FilterSettingsWriter(getSharedPreferences(), mGate);

        mLockCategory = (PreferenceCategory) findPreference(KEY_LOCK_CATEGORY);
        mListCategory = (PreferenceCategory) findPreference(KEY_LIST_CATEGORY);
        // The lock section shows and hides its items by state, so they are placed by explicit order.
        mLockCategory.setOrderingAsAdded(false);
        mStatus = orderedLockItem(KEY_LOCK_STATUS, 0);
        mLock = orderedLockItem(KEY_LOCK_LOCK, 1);
        mUnlock = orderedLockItem(KEY_LOCK_UNLOCK, 2);
        mLockNow = orderedLockItem(KEY_LOCK_NOW, 3);
        mDuration = orderedLockItem(KEY_LOCK_DURATION, 4);
        mRemove = orderedLockItem(KEY_LOCK_REMOVE, 5);
        mEnabled = (SwitchPreference) findPreference(Settings.PREF_WORDBLOCK_ENABLED);
        mIdleEnabled = (SwitchPreference) findPreference(Settings.PREF_WORDBLOCK_IDLE_ENABLED);
        mIdleDelay = (ListPreference) findPreference(Settings.PREF_WORDBLOCK_IDLE_MS);
        setUpIdleDelayChoices();

        mLock.setOnPreferenceClickListener(p -> { chooseDurationAndLock(); return true; });
        mUnlock.setOnPreferenceClickListener(p -> { startUnlock(); return true; });
        mLockNow.setOnPreferenceClickListener(p -> { lockNow(); return true; });
        mDuration.setOnPreferenceClickListener(p -> { changeDuration(); return true; });
        mRemove.setOnPreferenceClickListener(p -> { confirmRemoveLock(); return true; });
        findPreference(KEY_ADD).setOnPreferenceClickListener(p -> { showAddDialog(); return true; });

        // These write through the gate; returning false leaves the switch where it was.
        mEnabled.setOnPreferenceChangeListener(
                (p, value) -> mWriter.setEnabled((Boolean) value));
        mIdleEnabled.setOnPreferenceChangeListener(
                (p, value) -> mWriter.setIdleEnabled((Boolean) value));
        mIdleDelay.setOnPreferenceChangeListener(
                (p, value) -> mWriter.setIdleMillis(Integer.parseInt((String) value)));
    }

    private Preference orderedLockItem(final String key, final int order) {
        final Preference preference = findPreference(key);
        preference.setOrder(order);
        return preference;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Never trust what was shown before: the lock may have changed while this was away.
        refresh();
    }

    @Override
    public void onPause() {
        mHandler.removeCallbacks(mTick);
        // Leaving the settings page closes an open edit window. This is onPause rather than onStop
        // because onStop can arrive seconds late, after a newer unlock has completed. Rotating the
        // screen recreates this page and is not leaving it.
        if (!getActivity().isChangingConfigurations()) {
            mGate.onSettingsPageLeft();
        }
        super.onPause();
    }

    // ---- Showing the current state ------------------------------------------------------------

    /** Brings every control in line with the lock state and the stored settings. */
    private void refresh() {
        mHandler.removeCallbacks(mTick);
        final SettingsLock.State state = mGate.state();
        final boolean editable = mGate.canModifyFilterSettings();
        final SharedPreferences prefs = getSharedPreferences();

        mEnabled.setChecked(Settings.readWordBlockEnabled(prefs));
        mIdleEnabled.setChecked(Settings.readWordBlockIdleEnabled(prefs));
        mIdleDelay.setValue(String.valueOf(Settings.readWordBlockIdleMillis(prefs)));
        mEnabled.setEnabled(editable);
        mIdleEnabled.setEnabled(editable);
        mIdleDelay.setEnabled(editable);
        findPreference(KEY_ADD).setEnabled(editable);

        refreshLockSection(state);
        refreshList(editable);
        if (state == SettingsLock.State.UNLOCKED) {
            mHandler.postDelayed(mTick, 1000);
        }
    }

    private void refreshLockSection(final SettingsLock.State state) {
        final String duration = DurationFormat.describe(mGate.durationMs());
        final boolean locked = state == SettingsLock.State.LOCKED
                || state == SettingsLock.State.UNLOCKING;
        final boolean unlocked = state == SettingsLock.State.UNLOCKED;
        final boolean off = state == SettingsLock.State.NO_LOCK;

        if (off) {
            mStatus.setTitle(R.string.wordblock_lock_status_off);
            mStatus.setSummary(R.string.wordblock_lock_status_off_summary);
        } else if (locked) {
            mStatus.setTitle(R.string.wordblock_lock_status_locked);
            mStatus.setSummary(getString(R.string.wordblock_lock_status_locked_summary, duration));
        } else {
            mStatus.setTitle(R.string.wordblock_lock_status_unlocked);
            mStatus.setSummary(getString(R.string.wordblock_lock_status_unlocked_summary,
                    DurationFormat.clock(mGate.editWindowRemainingMs())));
        }

        setVisible(mLock, off);
        setVisible(mUnlock, locked);
        setVisible(mLockNow, unlocked);
        setVisible(mDuration, true);
        setVisible(mRemove, locked || unlocked);

        mUnlock.setSummary(getString(R.string.wordblock_lock_unlock_summary, duration));
        // The lock's own settings are part of what is locked.
        mDuration.setEnabled(!locked);
        mDuration.setSummary(locked
                ? getString(R.string.wordblock_lock_duration_locked, duration) : duration);
        mRemove.setEnabled(unlocked);
        mRemove.setSummary(unlocked ? R.string.wordblock_lock_remove_summary
                : R.string.wordblock_lock_remove_locked);
    }

    private void setVisible(final Preference preference, final boolean visible) {
        final boolean present = mLockCategory.findPreference(preference.getKey()) != null;
        if (visible && !present) {
            mLockCategory.addPreference(preference);
        } else if (!visible && present) {
            mLockCategory.removePreference(preference);
        }
    }

    /** Once a second while unlocked: shows the time left, and re-locks when the window ends. */
    private void onTick() {
        if (mGate.state() != SettingsLock.State.UNLOCKED) {
            refresh(); // The window ended: everything goes read-only again.
            return;
        }
        mStatus.setSummary(getString(R.string.wordblock_lock_status_unlocked_summary,
                DurationFormat.clock(mGate.editWindowRemainingMs())));
        mHandler.postDelayed(mTick, 1000);
    }

    // ---- Lock actions -------------------------------------------------------------------------

    private void chooseDurationAndLock() {
        if (mGate.state() != SettingsLock.State.NO_LOCK) {
            refresh();
            return;
        }
        DurationDialog.show(getActivity(), mGate.isDebug(), mGate.durationMs(), chosen -> {
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.wordblock_lock_confirm_title)
                    .setMessage(getString(R.string.wordblock_lock_confirm_message,
                            DurationFormat.describe(chosen)))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.wordblock_lock_confirm_yes, (dialog, which) -> {
                        if (!mGate.lock(chosen)) {
                            toast(R.string.wordblock_lock_saving_failed);
                        }
                        refresh();
                    })
                    .show();
        });
    }

    private void changeDuration() {
        if (!mGate.canModifyFilterSettings()) {
            refresh();
            return;
        }
        DurationDialog.show(getActivity(), mGate.isDebug(), mGate.durationMs(), chosen -> {
            if (!mGate.setDuration(chosen)) {
                toast(R.string.wordblock_lock_failed);
            }
            refresh();
        });
    }

    private void startUnlock() {
        if (mGate.state() != SettingsLock.State.LOCKED) {
            refresh();
            return;
        }
        startActivity(new Intent(getActivity(), UnlockActivity.class));
    }

    private void lockNow() {
        mGate.lockNow();
        refresh();
    }

    private void confirmRemoveLock() {
        if (mGate.state() != SettingsLock.State.UNLOCKED) {
            refresh();
            return;
        }
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.wordblock_lock_remove_title)
                .setMessage(R.string.wordblock_lock_remove_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.wordblock_lock_remove, (dialog, which) -> {
                    if (!mGate.removeLock()) {
                        toast(R.string.wordblock_lock_failed);
                    }
                    refresh();
                })
                .show();
    }

    // ---- Idle delay ---------------------------------------------------------------------------

    /** The pause length is chosen from a short list, stored as a string by the list preference. */
    private void setUpIdleDelayChoices() {
        final int[] millis = {250, 500, 750, 1000, 1500, 2000};
        final CharSequence[] entries = new CharSequence[millis.length];
        final CharSequence[] values = new CharSequence[millis.length];
        for (int i = 0; i < millis.length; i++) {
            entries[i] = getString(R.string.wordblock_idle_delay_format, millis[i]);
            values[i] = String.valueOf(millis[i]);
        }
        mIdleDelay.setEntries(entries);
        mIdleDelay.setEntryValues(values);
    }

    // ---- The blocked-word list ----------------------------------------------------------------

    private void refreshList(final boolean editable) {
        for (final Preference preference : mEntryPreferences) {
            mListCategory.removePreference(preference);
        }
        mEntryPreferences.clear();

        final List<String> entries = new ArrayList<>(mStore.getEntries());
        Collections.sort(entries, String.CASE_INSENSITIVE_ORDER);
        if (entries.isEmpty()) {
            final Preference empty = new Preference(getActivity());
            empty.setTitle(R.string.wordblock_empty);
            empty.setSelectable(false);
            addEntryPreference(empty);
            return;
        }
        for (final String entry : entries) {
            final Preference preference = new Preference(getActivity());
            preference.setTitle(entry);
            preference.setPersistent(false);
            // Locked entries stay readable but cannot be opened for removal.
            preference.setSelectable(editable);
            preference.setOnPreferenceClickListener(p -> {
                showRemoveDialog(entry);
                return true;
            });
            addEntryPreference(preference);
        }
    }

    private void addEntryPreference(final Preference preference) {
        mListCategory.addPreference(preference);
        mEntryPreferences.add(preference);
    }

    private void showAddDialog() {
        if (!mGate.canModifyFilterSettings()) {
            refresh();
            return;
        }
        final Context context = getActivity();
        final EditText input = new EditText(context);
        input.setHint(R.string.wordblock_add_hint);
        input.setSingleLine(true);
        // Typed here, the words would otherwise be caught by this very filter (and, with another
        // keyboard, learned by it). A "visible password" field is skipped by the filter and is
        // not learned or suggested by keyboards that respect it.
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                | EditorInfo.IME_ACTION_DONE);

        final FrameLayout container = new FrameLayout(context);
        final int margin = (int) (24 * context.getResources().getDisplayMetrics().density);
        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(margin, 0, margin, 0);
        container.addView(input, params);

        new AlertDialog.Builder(context)
                .setTitle(R.string.wordblock_add_title)
                .setView(container)
                .setPositiveButton(R.string.wordblock_add_confirm,
                        (dialog, which) -> addEntry(input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void addEntry(final String rawEntry) {
        final String entry = rawEntry.trim();
        if (entry.isEmpty()) {
            return;
        }
        if (!mGate.canModifyFilterSettings()) {
            toast(R.string.wordblock_lock_status_locked);
        } else if (!WordFilter.isValidEntry(entry)) {
            toast(R.string.wordblock_invalid);
        } else if (!mStore.add(entry)) {
            toast(alreadyListed(entry) ? R.string.wordblock_duplicate
                    : R.string.wordblock_save_failed);
        }
        refresh();
    }

    private boolean alreadyListed(final String entry) {
        for (final String existing : mStore.getEntries()) {
            if (existing.equalsIgnoreCase(entry)) {
                return true;
            }
        }
        return false;
    }

    private void showRemoveDialog(final String entry) {
        if (!mGate.canModifyFilterSettings()) {
            refresh();
            return;
        }
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.wordblock_remove_title)
                .setMessage(entry)
                .setPositiveButton(R.string.wordblock_remove_confirm, (dialog, which) -> {
                    if (!mStore.remove(entry)) {
                        toast(mGate.canModifyFilterSettings() ? R.string.wordblock_save_failed
                                : R.string.wordblock_lock_status_locked);
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void toast(final int messageId) {
        Toast.makeText(getActivity(), messageId, Toast.LENGTH_LONG).show();
    }
}
