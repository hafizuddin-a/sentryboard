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

package rkr.simplekeyboard.inputmethod.latin.wordblock;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import rkr.simplekeyboard.inputmethod.latin.LatinIME;
import rkr.simplekeyboard.inputmethod.latin.RichInputConnection;
import rkr.simplekeyboard.inputmethod.latin.settings.Settings;
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsValues;

/**
 * Removes a blocked word from the editor.
 *
 * There are four ways a word can be removed:
 * <ul>
 * <li>When the user completes it: {@code InputLogic} calls {@link #beforeSeparator} (or
 *     {@link #beforeEditorAction}) just before it commits a separator or performs an editor
 *     action, and a blocked word immediately before the cursor is deleted first.</li>
 * <li>As soon as it is typed, if it matches an "instant" (word!) entry:
 *     {@link #afterWordCharacter}. This closes the gap where the user taps the app's own Send
 *     button without typing a separator.</li>
 * <li>After a pause in typing, if that optional setting is on: {@link #scheduleIdleRemoval}.</li>
 * <li>When the user edits a word that already has text after the cursor, which the three ways
 *     above never look at: {@link #afterEdit} checks the whole word around the cursor.</li>
 * <li>When text appears that the keyboard did not type, such as a paste:
 *     {@link #afterSelectionChange} checks the inserted text and the words at its edges.</li>
 * </ul>
 *
 * Nothing here may ever break typing: every entry point catches {@link RuntimeException}, logs
 * only the exception's class name and lets the keystroke through. The typed text and the list
 * contents are never logged.
 *
 * All methods must be called on the main thread, where {@code InputLogic} runs.
 */
public final class WordBlockController {
    private static final String TAG = "WordBlock";
    private static final int MIN_WINDOW = 32;
    private static final int WINDOW_MARGIN = 8;
    // Long enough for a digit, which reaches the editor as an asynchronous key event, to arrive.
    private static final long SETTLE_MILLIS = 150;
    // A cursor jump this soon after the keyboard last did something is the keyboard's own doing.
    private static final long OWN_EDIT_QUIET_MILLIS = 400;
    // Apps such as Telegram swap the input connection right after a paste (about 10 ms later) and
    // return nothing until it is in place: read the text a moment later, and try again if needed.
    private static final long INSERT_SCAN_FIRST_MILLIS = 50;
    private static final long INSERT_SCAN_RETRY_MILLIS = 80;
    private static final int INSERT_SCAN_ATTEMPTS = 8;
    // The most pasted text that is scanned (the rest of a longer paste is left alone).
    private static final int MAX_PASTE_SCAN = 4000;

    private final LatinIME mLatinIme;
    private final RichInputConnection mConnection;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mIdleRemoval = this::removeAfterIdle;
    private final Runnable mSettledCheck = this::checkAfterSettling;
    private long mLastActivityMillis;
    private final Runnable mInsertScan = this::scanInsertedText;
    private int mInsertedLength;
    private int mInsertedEnd;
    private int mScanAttempts;
    // Resolved on first use: this object is created while the service is still being constructed
    // and has no base context yet.
    private BlockedWordStore mStore;

    public WordBlockController(final LatinIME latinIme, final RichInputConnection connection) {
        mLatinIme = latinIme;
        mConnection = connection;
    }

    // ---- When the user completes a word -------------------------------------------------------

    /**
     * To be called before a separator code point is committed. If a blocked word was deleted, the
     * deletion has been started inside a batch edit that is still open, so that it and the
     * separator that follows reach the editor as one change.
     *
     * @return true if a batch edit was left open. The caller must then call
     * {@link RichInputConnection#endBatchEdit()} exactly once when it is done.
     */
    public boolean beforeSeparator(final SettingsValues settingsValues, final EditorInfo editorInfo,
            final int codePoint) {
        if (codePoint < 0 || !WordFilter.endsWord(codePoint)) {
            return false; // Still inside a word.
        }
        return removeBlockedWord(settingsValues, editorInfo);
    }

    /**
     * To be called before an editor action (Enter as Send/Go/Search, Next, Previous) is
     * performed. The deletion is complete, and any batch edit closed, when this returns: some
     * editors hold back edits made inside a batch, and the action must see the final text.
     */
    public void beforeEditorAction(final SettingsValues settingsValues,
            final EditorInfo editorInfo) {
        if (removeBlockedWord(settingsValues, editorInfo)) {
            endBatch();
        }
    }

    /** @return true if a word was deleted and the batch edit it started is still open. */
    private boolean removeBlockedWord(final SettingsValues settingsValues,
            final EditorInfo editorInfo) {
        try {
            if (!isActive(settingsValues, editorInfo)) {
                return false;
            }
            final WordFilter filter = getStore().getFilter();
            if (filter.isEmpty()) {
                return false;
            }
            final int window = windowFor(filter);
            // Some editors (terminals, custom views) return null. Then there is nothing to check.
            final int length = filter.blockedLengthBefore(
                    wholeWordsOnly(mConnection.getTextBeforeCursor(window), window));
            if (length == 0) {
                return false;
            }
            return deleteInBatch(length);
        } catch (RuntimeException e) {
            logFailure(e);
            return false;
        }
    }

    // ---- As soon as an "instant" word has been typed ------------------------------------------

    /**
     * To be called after a word character has been sent to the editor. If the word now before the
     * cursor exactly matches an instant (word!) entry it is deleted immediately.
     *
     * This runs on every word-character keystroke, so it first looks at the local text cache,
     * which costs nothing, and only asks the editor to confirm a hit. Acting on the editor's own
     * text also guarantees that what is deleted is what was checked.
     *
     * Digits are sent to the editor as asynchronous key events (see
     * InputLogic#sendKeyCodePoint), so a read straight afterwards may not include them yet. The
     * cache does, so a hit on a digit is confirmed a moment later instead (see
     * {@link #SETTLE_MILLIS}), which is what lets an entry that ends in a digit be instant.
     */
    public void afterWordCharacter(final SettingsValues settingsValues,
            final EditorInfo editorInfo, final int codePoint) {
        try {
            if (!isActive(settingsValues, editorInfo)) {
                return;
            }
            final WordFilter filter = getStore().getFilter();
            if (!filter.hasInstantEntries()) {
                return;
            }
            final int window = windowFor(filter);
            if (filter.instantLengthBefore(wholeWordsOnly(
                    mConnection.getCachedTextBeforeCursor(window), window)) == 0) {
                return;
            }
            // Typing inside a word that goes on after the cursor: the whole word is judged by
            // afterEdit, not the part of it typed so far.
            if (wordContinuesAfterCursor()) {
                return;
            }
            if (codePoint >= '0' && codePoint <= '9') {
                mHandler.removeCallbacks(mSettledCheck);
                mHandler.postDelayed(mSettledCheck, SETTLE_MILLIS);
                return;
            }
            removeInstantWord(filter);
        } catch (RuntimeException e) {
            logFailure(e);
        }
    }

    /** Deletes the word or phrase before the cursor if the editor's text says it is an instant entry. */
    private boolean removeInstantWord(final WordFilter filter) {
        final int window = windowFor(filter);
        final int length = filter.instantLengthBefore(
                wholeWordsOnly(mConnection.getTextBeforeCursor(window), window));
        if (length == 0) {
            return false;
        }
        deleteNow(length);
        return true;
    }

    /** @return true if, going by the local cache, a word character follows the cursor. */
    private boolean wordContinuesAfterCursor() {
        final CharSequence after = mConnection.getCachedTextAfterCursor(2);
        return after.length() > 0 && WordFilter.isWordCodePoint(Character.codePointAt(after, 0));
    }

    // ---- After editing a word that has text after the cursor ----------------------------------

    /**
     * To be called after every keystroke has been handled. If there is text after the cursor, the
     * word around the cursor is a finished one that is being edited, which is how a blocked word
     * can be slipped past the other checks: type {@code helo} and a space, then go back and add
     * the {@code l}. So the whole word, the part before the cursor and the part after it, is read
     * back and deleted if it is blocked. This covers inserting, deleting and splitting a word.
     *
     * With nothing after the cursor, which is nearly all typing, this costs one local string
     * check. The same check runs again a moment later (see {@link #SETTLE_MILLIS}) in case the
     * editor had not yet applied the keystroke.
     */
    public void afterEdit(final SettingsValues settingsValues, final EditorInfo editorInfo) {
        try {
            final WordFilter filter = filterToCheckAroundCursor(settingsValues, editorInfo);
            if (filter == null) {
                return;
            }
            removeAroundCursor(filter);
            mHandler.removeCallbacks(mSettledCheck);
            mHandler.postDelayed(mSettledCheck, SETTLE_MILLIS);
        } catch (RuntimeException e) {
            logFailure(e);
        }
    }

    /**
     * The check that runs {@link #SETTLE_MILLIS} after a keystroke whose effect the editor may not
     * have shown yet: an instant word ending in a digit, and the word around the cursor.
     */
    private void checkAfterSettling() {
        try {
            final SettingsValues settingsValues = Settings.getInstance().getCurrent();
            final EditorInfo editorInfo = mLatinIme.getCurrentInputEditorInfo();
            if (!isActive(settingsValues, editorInfo) || mConnection.hasSelection()) {
                return;
            }
            final WordFilter filter = getStore().getFilter();
            if (filter.hasInstantEntries() && !wordContinuesAfterCursor()
                    && removeInstantWord(filter)) {
                return;
            }
            if (filterToCheckAroundCursor(settingsValues, editorInfo) != null) {
                removeAroundCursor(filter);
            }
        } catch (RuntimeException e) {
            logFailure(e);
        }
    }

    /** @return the filter, if a word around the cursor is worth checking now, else null. */
    private WordFilter filterToCheckAroundCursor(final SettingsValues settingsValues,
            final EditorInfo editorInfo) {
        if (!isActive(settingsValues, editorInfo) || mConnection.hasSelection()) {
            return null;
        }
        final WordFilter filter = getStore().getFilter();
        if (filter.isEmpty()) {
            return null;
        }
        // Nothing after the cursor means the user is typing at the end of the text. That word is
        // not finished: separators, instant entries and the pause setting deal with it.
        return mConnection.getCachedTextAfterCursor(1).length() == 0 ? null : filter;
    }

    private void removeAroundCursor(final WordFilter filter) {
        final int window = windowFor(filter);
        final CharSequence before = wholeWordsOnly(mConnection.getTextBeforeCursor(window), window);
        final CharSequence after = mConnection.getTextAfterCursor(window);
        if (before == null || after == null || after.length() == 0) {
            return;
        }
        // A word cut by the far edge of the window may go on beyond it: do not judge a word by a
        // piece of it.
        final WordFilter.Hit hit = filter.blockedAround(before,
                after.length() >= window ? WordFilter.dropPartialEnd(after) : after);
        if (hit == null) {
            return;
        }
        mConnection.beginBatchEdit();
        try {
            if (hit.after > 0) {
                mConnection.deleteTextAfterCursor(hit.after);
            }
            if (hit.before > 0) {
                mConnection.deleteTextBeforeCursor(hit.before);
            }
        } finally {
            endBatch();
        }
    }

    // ---- After text appears that the keyboard did not type (a paste) --------------------------

    /**
     * To be called when the editor reports a new selection, before the local text cache is
     * reloaded. A paste (or autofill, or drag and drop) inserts text and moves the cursor to its end
     * without the keyboard having typed anything. That looks like this: the cursor is collapsed, it
     * moved forward by two or more from where the keyboard had it, and the keyboard has not been
     * typing. A forward tap on the screen looks the same, and is treated the same: the text the
     * cursor moved over is scanned, and a blocked word in it is removed.
     *
     * The scan is done a moment later, because some apps replace the input connection right after
     * a paste and refuse to hand out text until the new one is in place.
     *
     * @param expectedStart where the keyboard thought the cursor was, before this update.
     */
    public void afterSelectionChange(final int expectedStart, final int oldSelStart,
            final int newSelStart, final int newSelEnd) {
        try {
            // Where the cursor was, by the keyboard's own tracking when it has one. The editor's
            // "old" position is not reliable: an app that restarts the input session after a paste
            // can have told the system about the new position already.
            final int from = expectedStart >= 0 ? expectedStart : oldSelStart;
            final int length = newSelStart - from;
            if (newSelStart != newSelEnd || length < 2 || newSelStart == expectedStart
                    || SystemClock.uptimeMillis() - mLastActivityMillis < OWN_EDIT_QUIET_MILLIS) {
                return;
            }
            mInsertedLength = length;
            mInsertedEnd = newSelStart;
            mScanAttempts = 0;
            mHandler.removeCallbacks(mInsertScan);
            mHandler.postDelayed(mInsertScan, INSERT_SCAN_FIRST_MILLIS);
        } catch (RuntimeException e) {
            logFailure(e);
        }
    }

    private void scanInsertedText() {
        try {
            final WordFilter filter = filterToCheckAfterInsert(Settings.getInstance().getCurrent(),
                    mLatinIme.getCurrentInputEditorInfo());
            // Not if the cursor has moved on, or something is selected, in the meantime.
            if (filter == null || mConnection.hasSelection()
                    || mConnection.getExpectedSelectionStart() != mInsertedEnd) {
                return;
            }
            final int window = windowFor(filter);
            final CharSequence after = mConnection.getTextAfterCursor(window);
            if (after == null || !removeFromInsertedText(filter, mInsertedLength, window, after)) {
                if (++mScanAttempts < INSERT_SCAN_ATTEMPTS) {
                    mHandler.postDelayed(mInsertScan, INSERT_SCAN_RETRY_MILLIS);
                }
            }
        } catch (RuntimeException e) {
            logFailure(e);
        }
    }

    private WordFilter filterToCheckAfterInsert(final SettingsValues settingsValues,
            final EditorInfo editorInfo) {
        if (!isActive(settingsValues, editorInfo)) {
            return null;
        }
        final WordFilter filter = getStore().getFilter();
        return filter.isEmpty() ? null : filter;
    }

    /** @return false if the editor would not hand out the text, so that it can be tried again. */
    private boolean removeFromInsertedText(final WordFilter filter, final int insertedLength,
            final int window, final CharSequence afterRead) {
        final int scanned = Math.min(insertedLength, MAX_PASTE_SCAN);
        final CharSequence beforeRead = mConnection.getTextBeforeCursor(scanned + window);
        if (beforeRead == null) {
            return false;
        }
        // Words cut in half by the far edges of what was read are left out.
        final CharSequence before = beforeRead.length() >= scanned + window
                ? WordFilter.dropPartialStart(beforeRead) : beforeRead;
        final CharSequence after = afterRead.length() >= window
                ? WordFilter.dropPartialEnd(afterRead) : afterRead;
        final int cursor = before.length();
        final int insertedStart = Math.max(0, cursor - insertedLength);
        final String text = new StringBuilder(cursor + after.length())
                .append(before).append(after).toString();

        // The blocked words that overlap the inserted text, which may reach into the words next to it.
        final boolean[] removed = new boolean[text.length()];
        int first = Integer.MAX_VALUE;
        int last = -1;
        for (final int[] span : filter.blockedSpans(text)) {
            if (span[0] < cursor && span[1] > insertedStart) {
                java.util.Arrays.fill(removed, span[0], span[1], true);
                first = Math.min(first, span[0]);
                last = Math.max(last, span[1]);
            }
        }
        if (last < 0) {
            return true;
        }
        final StringBuilder keptBefore = new StringBuilder();
        final StringBuilder keptAfter = new StringBuilder();
        // Everything from the first blocked word to the cursor is deleted, so all of it that is not
        // blocked has to be put back, not only what lies before the last blocked word.
        for (int i = first; i < Math.max(last, cursor); i++) {
            if (!removed[i]) {
                (i < cursor ? keptBefore : keptAfter).append(text.charAt(i));
            }
        }
        mConnection.beginBatchEdit();
        try {
            if (last > cursor) {
                mConnection.deleteTextAfterCursor(last - cursor);
            }
            mConnection.deleteTextBeforeCursor(cursor - first);
            if (keptBefore.length() > 0) {
                mConnection.commitText(keptBefore, 1);
            }
            if (keptAfter.length() > 0) {
                mConnection.commitText(keptAfter, 0); // 0: the cursor stays in front of it
            }
        } finally {
            endBatch();
            mLastActivityMillis = SystemClock.uptimeMillis();
        }
        return true;
    }

    // ---- After a pause in typing (optional) ---------------------------------------------------

    /** To be called at the start of every keystroke: the user is not idle any more. */
    public void cancelIdleRemoval() {
        mLastActivityMillis = SystemClock.uptimeMillis();
        mHandler.removeCallbacks(mIdleRemoval);
        mHandler.removeCallbacks(mSettledCheck);
        mHandler.removeCallbacks(mInsertScan);
    }

    /**
     * To be called at the end of every keystroke. If the idle setting is on, the word before the
     * cursor will be removed if it is blocked and no other key is pressed for the configured time.
     */
    public void scheduleIdleRemoval(final SettingsValues settingsValues,
            final EditorInfo editorInfo) {
        mHandler.removeCallbacks(mIdleRemoval);
        try {
            if (settingsValues.mWordBlockIdleEnabled && isActive(settingsValues, editorInfo)
                    && !getStore().getFilter().isEmpty()) {
                mHandler.postDelayed(mIdleRemoval, settingsValues.mWordBlockIdleMillis);
            }
        } catch (RuntimeException e) {
            logFailure(e);
        }
    }

    private void removeAfterIdle() {
        try {
            final SettingsValues settingsValues = Settings.getInstance().getCurrent();
            final EditorInfo editorInfo = mLatinIme.getCurrentInputEditorInfo();
            if (!settingsValues.mWordBlockIdleEnabled || !isActive(settingsValues, editorInfo)
                    || mConnection.hasSelection()) {
                return;
            }
            final WordFilter filter = getStore().getFilter();
            final int window = windowFor(filter);
            final int length = filter.blockedLengthBefore(
                    wholeWordsOnly(mConnection.getTextBeforeCursor(window), window));
            if (length > 0) {
                deleteNow(length);
            }
        } catch (RuntimeException e) {
            logFailure(e);
        }
    }

    // ---- Shared -------------------------------------------------------------------------------

    private boolean isActive(final SettingsValues settingsValues, final EditorInfo editorInfo) {
        return settingsValues.mWordBlockEnabled && editorInfo != null
                && FieldPolicy.isFilterable(editorInfo.inputType);
    }

    /** How much text to read back from the editor to be sure to see a whole listed word. */
    private static int windowFor(final WordFilter filter) {
        return Math.max(MIN_WINDOW, filter.maxEntryLength() + WINDOW_MARGIN);
    }

    /**
     * @return the text read back from before the cursor, minus a first word that the far edge of
     * the window may have cut in two (null if the editor returned nothing). Text shorter than the
     * window starts where the field does, so its first word is whole.
     */
    private static CharSequence wholeWordsOnly(final CharSequence before, final int window) {
        return before != null && before.length() >= window ? WordFilter.dropPartialStart(before)
                : before;
    }

    /** Deletes text before the cursor in a batch edit that is left open on success. */
    private boolean deleteInBatch(final int length) {
        mConnection.beginBatchEdit();
        try {
            mConnection.deleteTextBeforeCursor(length);
        } catch (RuntimeException e) {
            mConnection.endBatchEdit();
            throw e;
        }
        return true;
    }

    private void deleteNow(final int length) {
        deleteInBatch(length);
        endBatch();
    }

    private void endBatch() {
        try {
            mConnection.endBatchEdit();
        } catch (RuntimeException e) {
            logFailure(e);
        }
    }

    private BlockedWordStore getStore() {
        if (mStore == null) {
            mStore = BlockedWordStore.getInstance(mLatinIme);
        }
        return mStore;
    }

    private static void logFailure(final RuntimeException e) {
        // Class name only: the message could contain text from the editor.
        Log.w(TAG, "Word filter failed: " + e.getClass().getSimpleName());
    }
}
