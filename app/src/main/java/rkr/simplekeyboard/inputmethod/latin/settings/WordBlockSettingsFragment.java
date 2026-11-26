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
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
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
 * whatever this screen does. While locked the blocked-word list is hidden and words can still be
 * added to it (one at a time or many at once); removing a word, checking text against the list, and
 * every other setting, is what is locked.
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
    private static final String KEY_BULK = "wordblock_bulk";
    private static final String KEY_CHECK = "wordblock_check";
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
    private ListPreference mStrict;
    private AlertDialog mCheckDialog;
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
        mStrict = (ListPreference) findPreference(Settings.PREF_WORDBLOCK_STRICT);
        setUpIdleDelayChoices();

        mLock.setOnPreferenceClickListener(p -> { chooseDurationAndLock(); return true; });
        mUnlock.setOnPreferenceClickListener(p -> { startUnlock(); return true; });
        mLockNow.setOnPreferenceClickListener(p -> { lockNow(); return true; });
        mDuration.setOnPreferenceClickListener(p -> { changeDuration(); return true; });
        mRemove.setOnPreferenceClickListener(p -> { confirmRemoveLock(); return true; });
        findPreference(KEY_ADD).setOnPreferenceClickListener(p -> { showAddDialog(); return true; });
        findPreference(KEY_BULK).setOnPreferenceClickListener(p -> { showBulkDialog(); return true; });
        findPreference(KEY_CHECK).setOnPreferenceClickListener(p -> { showCheckDialog(); return true; });

        // These write through the gate; returning false leaves the switch where it was.
        mEnabled.setOnPreferenceChangeListener(
                (p, value) -> mWriter.setEnabled((Boolean) value));
        mIdleEnabled.setOnPreferenceChangeListener(
                (p, value) -> mWriter.setIdleEnabled((Boolean) value));
        mIdleDelay.setOnPreferenceChangeListener(
                (p, value) -> mWriter.setIdleMillis(Integer.parseInt((String) value)));
        mStrict.setOnPreferenceChangeListener(
                (p, value) -> mWriter.setStrict((String) value));
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
        dismissCheckDialog(); // Leaving relocks the settings.
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
        mStrict.setValue(Settings.readWordBlockStrict(prefs));
        mEnabled.setEnabled(editable);
        mIdleEnabled.setEnabled(editable);
        mIdleDelay.setEnabled(editable);
        mStrict.setEnabled(editable);
        // Adding a word is always allowed, even while locked; only removing is not.
        findPreference(KEY_ADD).setEnabled(true);
        findPreference(KEY_BULK).setEnabled(true);
        // The answers reveal what is on the list, so this is for when it is shown anyway.
        findPreference(KEY_CHECK).setEnabled(editable);
        findPreference(KEY_CHECK).setSummary(editable ? R.string.wordblock_check_summary
                : R.string.wordblock_check_locked);
        if (!editable) {
            dismissCheckDialog();
        }

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
        if (!editable) {
            // While locked the list says how many words there are, but not which.
            final Preference hidden = new Preference(getActivity());
            hidden.setTitle(getResources().getQuantityString(R.plurals.wordblock_list_hidden,
                    entries.size(), entries.size()));
            hidden.setSummary(R.string.wordblock_list_hidden_summary);
            hidden.setSelectable(false);
            addEntryPreference(hidden);
            return;
        }
        for (final String entry : entries) {
            final Preference preference = new Preference(getActivity());
            preference.setTitle(entry);
            preference.setPersistent(false);
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

    /**
     * A field for typing words that are, or may be, on the list. Typed here they would otherwise
     * be caught by this very filter (and, with another keyboard, learned by it). A "visible
     * password" field is skipped by the filter and is not learned or suggested by keyboards that
     * respect it.
     */
    private EditText wordInput(final int hint, final int extraInputType) {
        final EditText input = new EditText(getActivity());
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | extraInputType);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                | EditorInfo.IME_ACTION_DONE);
        return input;
    }

    /** Puts a view into a frame with the side margins dialogs use. */
    private View framed(final View view) {
        final FrameLayout container = new FrameLayout(getActivity());
        final int margin = (int) (24 * getResources().getDisplayMetrics().density);
        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(margin, 0, margin, 0);
        container.addView(view, params);
        return container;
    }

    private void showAddDialog() {
        final EditText input = wordInput(R.string.wordblock_add_hint, 0);
        input.setSingleLine(true);

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.wordblock_add_title)
                .setView(framed(input))
                .setPositiveButton(R.string.wordblock_add_confirm,
                        (dialog, which) -> addEntry(input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void addEntry(final String rawEntry) {
        final String entry = WordFilter.tidyEntry(rawEntry);
        if (entry.isEmpty()) {
            return;
        }
        // While locked the list is hidden, so nothing here may say whether a word was on it already.
        final boolean listHidden = !mGate.canModifyFilterSettings();
        if (!WordFilter.isValidEntry(entry)) {
            toast(R.string.wordblock_invalid);
        } else if (!mStore.add(entry)) {
            if (!alreadyListed(entry)) {
                toast(R.string.wordblock_save_failed);
            } else {
                toast(listHidden ? R.string.wordblock_added_hidden : R.string.wordblock_duplicate);
            }
        } else if (listHidden) {
            toast(R.string.wordblock_added_hidden);
        }
        refresh();
        // While the list is hidden, offer them whether or not the entry was new, so that the offer
        // itself says nothing about what was already there.
        if (WordFilter.isValidEntry(entry) && (listHidden || alreadyListed(entry))) {
            showSuggestions(entry, listHidden);
        }
    }

    /** Offers related forms of an entry, each unticked, to add as well. */
    private void showSuggestions(final String entry, final boolean listHidden) {
        final List<String> suggestions = mStore.suggestVariants(entry, listHidden);
        if (suggestions.isEmpty()) {
            return;
        }
        final CharSequence[] items = suggestions.toArray(new CharSequence[0]);
        final boolean[] ticked = new boolean[items.length];
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.wordblock_suggest_title)
                .setMultiChoiceItems(items, ticked,
                        (dialog, which, isChecked) -> ticked[which] = isChecked)
                .setPositiveButton(R.string.wordblock_suggest_confirm, (dialog, which) -> {
                    final List<String> chosen = new ArrayList<>();
                    for (int i = 0; i < ticked.length; i++) {
                        if (ticked[i]) {
                            chosen.add(suggestions.get(i));
                        }
                    }
                    if (!chosen.isEmpty()) {
                        addMany(chosen);
                    }
                })
                .setNegativeButton(R.string.wordblock_suggest_skip, null)
                .show();
    }

    private void showBulkDialog() {
        final EditText input = wordInput(R.string.wordblock_bulk_hint,
                InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setSingleLine(false);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        input.setMinLines(5);
        input.setMaxLines(10);
        input.setGravity(Gravity.TOP | Gravity.START);

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.wordblock_bulk)
                .setView(framed(input))
                .setPositiveButton(R.string.wordblock_bulk_confirm, (dialog, which) -> {
                    final List<String> lines =
                            BlockedWordStore.splitEntries(input.getText().toString());
                    if (!lines.isEmpty()) {
                        addMany(lines);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void addMany(final List<String> lines) {
        // While the list is hidden, "new" and "already there" are not told apart.
        final boolean listHidden = !mGate.canModifyFilterSettings();
        final BlockedWordStore.AddResult result = mStore.addAll(lines);
        final String message;
        if (result.saveFailed) {
            message = getString(R.string.wordblock_save_failed);
        } else if (listHidden) {
            message = getString(R.string.wordblock_bulk_result_hidden,
                    result.added + result.alreadyListed, result.unusable);
        } else {
            message = getString(R.string.wordblock_bulk_result, result.added,
                    result.alreadyListed, result.unusable);
        }
        Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
        refresh();
    }

    // ---- "Is it blocked?" ---------------------------------------------------------------------

    private void showCheckDialog() {
        if (!mGate.canModifyFilterSettings()) {
            refresh();
            return;
        }
        final EditText input = wordInput(R.string.wordblock_check_hint, 0);
        input.setSingleLine(true);
        final TextView answer = new TextView(getActivity());
        answer.setText(R.string.wordblock_check_empty);
        final int space = (int) (12 * getResources().getDisplayMetrics().density);
        answer.setPadding(0, space, 0, 0);
        final LinearLayout column = new LinearLayout(getActivity());
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(input);
        column.addView(answer);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count,
                    final int after) {
            }

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before,
                    final int count) {
            }

            @Override
            public void afterTextChanged(final Editable s) {
                if (mGate.canModifyFilterSettings()) {
                    answer.setText(verdict(s.toString()));
                } else {
                    refresh(); // The unlock window ended: this closes the dialog.
                }
            }
        });

        mCheckDialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.wordblock_check_title)
                .setView(framed(column))
                .setPositiveButton(R.string.wordblock_check_close, null)
                .show();
    }

    private void dismissCheckDialog() {
        if (mCheckDialog != null) {
            mCheckDialog.dismiss();
            mCheckDialog = null;
        }
    }

    /** What the filter would do with this text, as the list stands. */
    private String verdict(final String text) {
        if (text.trim().isEmpty()) {
            return getString(R.string.wordblock_check_empty);
        }
        final SharedPreferences prefs = getSharedPreferences();
        if (!Settings.readWordBlockEnabled(prefs)) {
            return getString(R.string.wordblock_check_disabled);
        }
        final WordFilter filter = mStore.getFilter();
        final List<int[]> exact = filter.blockedSpans(text);
        if (!exact.isEmpty()) {
            return getString(R.string.wordblock_check_everywhere, quoted(text, exact));
        }
        final List<int[]> strict = filter.strictSpans(text);
        if (strict.isEmpty()) {
            return getString(R.string.wordblock_check_no);
        }
        final String scope = Settings.readWordBlockStrict(prefs);
        final int message = Settings.STRICT_ALL.equals(scope) ? R.string.wordblock_check_strict_all
                : Settings.STRICT_OFF.equals(scope) ? R.string.wordblock_check_strict_off
                : R.string.wordblock_check_strict_browsers;
        return getString(message, quoted(text, strict));
    }

    private static String quoted(final String text, final List<int[]> spans) {
        final StringBuilder sb = new StringBuilder();
        for (final int[] span : spans) {
            sb.append(sb.length() > 0 ? ", " : "").append('"')
                    .append(text, span[0], span[1]).append('"');
        }
        return sb.toString();
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
