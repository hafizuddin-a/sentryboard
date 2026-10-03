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
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import rkr.simplekeyboard.inputmethod.latin.LatinIME;
import rkr.simplekeyboard.inputmethod.latin.RichInputConnection;
import rkr.simplekeyboard.inputmethod.latin.settings.Settings;
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsValues;

/**
 * Removes a blocked word from the editor.
 *
 * There are three ways a word can be removed:
 * <ul>
 * <li>When the user completes it: {@code InputLogic} calls {@link #beforeSeparator} (or
 *     {@link #beforeEditorAction}) just before it commits a separator or performs an editor
 *     action, and a blocked word immediately before the cursor is deleted first.</li>
 * <li>As soon as it is typed, if it matches an "instant" (word!) entry:
 *     {@link #afterWordCharacter}. This closes the gap where the user taps the app's own Send
 *     button without typing a separator.</li>
 * <li>After a pause in typing, if that optional setting is on: {@link #scheduleIdleRemoval}.</li>
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

    private final LatinIME mLatinIme;
    private final RichInputConnection mConnection;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mIdleRemoval = this::removeAfterIdle;
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
            final CharSequence run = trailingWord(mConnection.getTextBeforeCursor(window), window);
            final int length = run == null ? 0 : filter.blockedLength(run);
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
     */
    public void afterWordCharacter(final SettingsValues settingsValues,
            final EditorInfo editorInfo, final int codePoint) {
        try {
            // Digits are sent to the editor as asynchronous key events (see
            // InputLogic#sendKeyCodePoint), so a read straight afterwards may not include them yet.
            // An instant entry that ends in a digit is still removed when a separator follows.
            if (codePoint >= '0' && codePoint <= '9') {
                return;
            }
            if (!isActive(settingsValues, editorInfo)) {
                return;
            }
            final WordFilter filter = getStore().getFilter();
            if (!filter.hasInstantEntries()) {
                return;
            }
            final int window = windowFor(filter);
            final CharSequence cachedRun =
                    trailingWord(mConnection.getCachedTextBeforeCursor(window), window);
            if (cachedRun == null || filter.instantLength(cachedRun) == 0) {
                return;
            }
            final CharSequence run = trailingWord(mConnection.getTextBeforeCursor(window), window);
            final int length = run == null ? 0 : filter.instantLength(run);
            if (length > 0) {
                deleteNow(length);
            }
        } catch (RuntimeException e) {
            logFailure(e);
        }
    }

    // ---- After a pause in typing (optional) ---------------------------------------------------

    /** To be called at the start of every keystroke: the user is not idle any more. */
    public void cancelIdleRemoval() {
        mHandler.removeCallbacks(mIdleRemoval);
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
            final CharSequence run = trailingWord(mConnection.getTextBeforeCursor(window), window);
            final int length = run == null ? 0 : filter.blockedLength(run);
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
     * @return the run of word characters at the end of {@code text} (an underscore counts as one),
     * or null if there is none or it may have been cut off by the edge of the window.
     */
    private static CharSequence trailingWord(final CharSequence text, final int window) {
        if (text == null) {
            return null;
        }
        final int length = WordFilter.trailingWordLength(text);
        final int available = text.length();
        if (length == 0 || (length == available && available >= window)) {
            return null;
        }
        return text.subSequence(available - length, available);
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
