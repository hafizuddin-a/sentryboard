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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.DurationFormat;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.LockConstants;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsGate;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsLock;

/**
 * The unlock page: a full-screen countdown that must run to the end without the user leaving.
 *
 * The countdown is owned by {@link SettingsGate}, which times it against a monotonic clock and
 * decides when it is done; this page only shows it and reports interruptions. Leaving the page in
 * any way (Home, Recents, another app, the screen turning off, Back, Cancel) resets it to zero.
 * Rotation, font size and dark-mode changes are handled in place (see {@code configChanges} in the
 * manifest) and do not.
 */
public final class UnlockActivity extends Activity {
    private static final long TICK_MS = 200;
    private static final int PROGRESS_MAX = 1000;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTick = this::render;
    private SettingsGate mGate;
    private TextView mClock;
    private TextView mNote;
    private ProgressBar mProgress;
    private long mGeneration;
    private boolean mFinishing;
    private final BroadcastReceiver mScreenOff = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            abandon(true);
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mGate = SettingsGate.getInstance(this);
        buildContent();

        // Read the note before starting: starting a countdown clears it.
        final boolean showNote = mGate.consumeResetNote();
        final SettingsLock.State state = mGate.state();
        mGeneration = state == SettingsLock.State.LOCKED ? mGate.startUnlock() : 0;
        if (mGeneration == 0) {
            // No lock, already unlocked, or a countdown is already running elsewhere.
            finishNow();
            return;
        }
        if (showNote) {
            mNote.setText(R.string.wordblock_unlock_reset_note);
        }
    }

    private void buildContent() {
        final float density = getResources().getDisplayMetrics().density;
        final int pad = (int) (24 * density);
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad * 2, pad, pad);

        final TextView title = new TextView(this);
        title.setText(R.string.wordblock_unlock_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, wrap());

        mClock = new TextView(this);
        mClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        mClock.setTypeface(Typeface.MONOSPACE);
        mClock.setGravity(Gravity.CENTER);
        final LinearLayout.LayoutParams clockParams = wrap();
        clockParams.topMargin = pad * 2;
        clockParams.bottomMargin = pad;
        root.addView(mClock, clockParams);

        mProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        mProgress.setMax(PROGRESS_MAX);
        root.addView(mProgress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView instruction = new TextView(this);
        instruction.setText(R.string.wordblock_unlock_instruction);
        instruction.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        instruction.setGravity(Gravity.CENTER);
        final LinearLayout.LayoutParams instructionParams = wrap();
        instructionParams.topMargin = pad;
        root.addView(instruction, instructionParams);

        mNote = new TextView(this);
        mNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mNote.setGravity(Gravity.CENTER);
        final LinearLayout.LayoutParams noteParams = wrap();
        noteParams.topMargin = pad;
        root.addView(mNote, noteParams);

        final Button cancel = new Button(this);
        cancel.setText(R.string.wordblock_unlock_cancel);
        cancel.setOnClickListener(view -> abandon(false));
        final LinearLayout.LayoutParams cancelParams = wrap();
        cancelParams.topMargin = pad * 2;
        root.addView(cancel, cancelParams);

        setContentView(root);
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mScreenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onPause() {
        mHandler.removeCallbacks(mTick);
        // onPause runs the moment the page is covered (Home, Recents, another app, screen off).
        // onStop, which follows, can be delayed by seconds, and the countdown must not keep
        // running meanwhile.
        abandon(true);
        super.onPause();
    }

    @Override
    protected void onStop() {
        try {
            unregisterReceiver(mScreenOff);
        } catch (IllegalArgumentException e) {
            // Not registered.
        }
        // Nothing to do if onPause already ended the countdown; this only makes sure.
        abandon(true);
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (mGate.state() != SettingsLock.State.UNLOCKING) {
            finishNow();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.wordblock_unlock_leave_title)
                .setMessage(R.string.wordblock_unlock_leave_message)
                .setNegativeButton(R.string.wordblock_unlock_stay, null)
                .setPositiveButton(R.string.wordblock_unlock_leave, (dialog, which) -> abandon(true))
                .show();
    }

    /** Shows the time left, and closes the page once the countdown has finished. */
    private void render() {
        mHandler.removeCallbacks(mTick);
        if (mFinishing) {
            return;
        }
        final SettingsLock.State state = mGate.state();
        if (state == SettingsLock.State.UNLOCKED) {
            Toast.makeText(this, getString(R.string.wordblock_unlock_done,
                    DurationFormat.describe(LockConstants.EDIT_WINDOW_MS)), Toast.LENGTH_LONG).show();
            finishNow();
            return;
        }
        if (state != SettingsLock.State.UNLOCKING) {
            finishNow(); // Reset behind our back, for instance by the clock going backwards.
            return;
        }
        final long remaining = mGate.unlockRemainingMs();
        final long total = mGate.durationMs();
        mClock.setText(DurationFormat.clock(remaining));
        mProgress.setProgress((int) ((total - remaining) * PROGRESS_MAX / Math.max(1, total)));
        mHandler.postDelayed(mTick, TICK_MS);
    }

    /**
     * Resets this page's countdown, if it is still running, and closes the page. A countdown that
     * has already finished is left alone.
     */
    private void abandon(final boolean leftThePage) {
        if (mGate != null) {
            mGate.interruptUnlock(mGeneration, leftThePage);
        }
        if (mGate == null || mGate.state() != SettingsLock.State.UNLOCKED) {
            finishNow();
        }
    }

    private void finishNow() {
        mFinishing = true;
        mHandler.removeCallbacks(mTick);
        if (!isFinishing()) {
            finish();
        }
    }
}
