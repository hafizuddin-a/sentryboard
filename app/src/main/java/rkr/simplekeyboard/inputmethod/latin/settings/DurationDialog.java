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
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.DurationFormat;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.LockConstants;

/**
 * Lets the user choose the length of the unlock countdown: a preset, or hours and minutes. Test
 * builds also offer a few short presets and a seconds field.
 */
final class DurationDialog {
    interface OnChosen {
        void onChosen(long durationMs);
    }

    private DurationDialog() {
        // Intentional empty constructor for utility class.
    }

    static void show(final Context context, final boolean debug, final long currentMs,
            final OnChosen callback) {
        final List<Long> choices = new ArrayList<>();
        if (debug) {
            choices.add(10_000L);
            choices.add(30_000L);
        }
        for (final long preset : LockConstants.PRESET_DURATIONS_MS) {
            choices.add(preset);
        }
        final CharSequence[] labels = new CharSequence[choices.size() + 1];
        int checked = choices.size(); // "Custom", unless a preset matches.
        for (int i = 0; i < choices.size(); i++) {
            labels[i] = DurationFormat.describe(choices.get(i));
            if (choices.get(i) == currentMs) {
                checked = i;
            }
        }
        labels[choices.size()] = context.getString(R.string.wordblock_lock_custom);

        new AlertDialog.Builder(context)
                .setTitle(R.string.wordblock_lock_pick_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which < choices.size()) {
                        callback.onChosen(choices.get(which));
                    } else {
                        showCustom(context, debug, currentMs, callback);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showCustom(final Context context, final boolean debug,
            final long currentMs, final OnChosen callback) {
        final long totalSeconds = currentMs / 1000;
        final EditText hours = numberField(context, R.string.wordblock_lock_hours, totalSeconds / 3600);
        final EditText minutes = numberField(context, R.string.wordblock_lock_minutes, (totalSeconds % 3600) / 60);
        final EditText seconds = debug
                ? numberField(context, R.string.wordblock_lock_seconds, totalSeconds % 60) : null;

        final LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        final int margin = (int) (24 * context.getResources().getDisplayMetrics().density);
        form.setPadding(margin, 0, margin, 0);
        addLabeled(form, context, R.string.wordblock_lock_hours, hours);
        addLabeled(form, context, R.string.wordblock_lock_minutes, minutes);
        if (seconds != null) {
            addLabeled(form, context, R.string.wordblock_lock_seconds, seconds);
        }

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.wordblock_lock_custom_title)
                .setView(form)
                .setPositiveButton(android.R.string.ok, null) // Set below, so it can stay open.
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            final long millis = (parse(hours) * 3600 + parse(minutes) * 60
                    + (seconds == null ? 0 : parse(seconds))) * 1000;
            if (!LockConstants.isValidDuration(millis, debug)) {
                Toast.makeText(context, context.getString(R.string.wordblock_lock_range_error,
                        DurationFormat.describe(LockConstants.minDurationMs(debug)),
                        DurationFormat.describe(LockConstants.MAX_DURATION_MS)),
                        Toast.LENGTH_LONG).show();
                return;
            }
            dialog.dismiss();
            callback.onChosen(millis);
        });
    }

    private static EditText numberField(final Context context, final int hintId, final long value) {
        final EditText field = new EditText(context);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setHint(hintId);
        field.setText(value == 0 ? "" : String.valueOf(value));
        return field;
    }

    private static void addLabeled(final LinearLayout form, final Context context,
            final int labelId, final EditText field) {
        final TextView label = new TextView(context);
        label.setText(labelId);
        form.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        form.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** @return the number in the field; an empty or unreadable field counts as zero. */
    private static long parse(final EditText field) {
        try {
            return Math.max(0, Long.parseLong(field.getText().toString().trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
