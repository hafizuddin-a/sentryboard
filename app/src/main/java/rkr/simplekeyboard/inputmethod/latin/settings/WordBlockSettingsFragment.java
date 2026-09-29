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
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
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
import rkr.simplekeyboard.inputmethod.latin.wordblock.WordFilter;

/**
 * "Word filter" settings sub screen: the on/off switch and the editor for the blocked-word list.
 *
 * Every change to the list goes through {@link BlockedWordStore#canEditBlockedList()}.
 */
public final class WordBlockSettingsFragment extends SubScreenFragment {
    private static final String KEY_LIST_CATEGORY = "wordblock_list";
    private static final String KEY_ADD = "wordblock_add";

    private BlockedWordStore mStore;
    private PreferenceCategory mListCategory;
    private final List<Preference> mEntryPreferences = new ArrayList<>();

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_wordblock);
        mStore = BlockedWordStore.getInstance(getActivity());
        mListCategory = (PreferenceCategory) findPreference(KEY_LIST_CATEGORY);

        findPreference(KEY_ADD).setOnPreferenceClickListener(preference -> {
            showAddDialog();
            return true;
        });
        refreshList();
    }

    private void refreshList() {
        for (final Preference preference : mEntryPreferences) {
            mListCategory.removePreference(preference);
        }
        mEntryPreferences.clear();

        final boolean editable = mStore.canEditBlockedList();
        findPreference(KEY_ADD).setEnabled(editable);

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
            preference.setEnabled(editable);
            if (!editable) {
                preference.setSummary(R.string.wordblock_locked);
            }
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

    private void showAddDialog() {
        if (!mStore.canEditBlockedList()) {
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
        if (!mStore.canEditBlockedList()) {
            return;
        }
        final String entry = rawEntry.trim();
        if (entry.isEmpty()) {
            return;
        }
        if (!WordFilter.isValidEntry(entry)) {
            toast(R.string.wordblock_invalid);
        } else if (!mStore.add(entry)) {
            toast(alreadyListed(entry) ? R.string.wordblock_duplicate
                    : R.string.wordblock_save_failed);
        }
        refreshList();
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
        if (!mStore.canEditBlockedList()) {
            return;
        }
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.wordblock_remove_title)
                .setMessage(entry)
                .setPositiveButton(R.string.wordblock_remove_confirm, (dialog, which) -> {
                    if (mStore.canEditBlockedList() && !mStore.remove(entry)) {
                        toast(R.string.wordblock_save_failed);
                    }
                    refreshList();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void toast(final int messageId) {
        Toast.makeText(getActivity(), messageId, Toast.LENGTH_LONG).show();
    }
}
