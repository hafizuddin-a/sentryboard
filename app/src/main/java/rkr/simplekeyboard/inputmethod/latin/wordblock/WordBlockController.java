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

import android.content.Context;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import rkr.simplekeyboard.inputmethod.latin.RichInputConnection;
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsValues;

/**
 * Removes a blocked word from the editor once the user has finished typing it.
 *
 * {@code InputLogic} calls in here just before it commits a separator (or performs an editor
 * action). If the word immediately before the cursor is on the blocked list it is deleted, so the
 * separator that follows lands after the now-missing word.
 *
 * Nothing here may ever break typing: every entry point catches {@link RuntimeException}, logs
 * only the exception's class name and lets the keystroke through. The typed text and the list
 * contents are never logged.
 */
public final class WordBlockController {
    private static final String TAG = "WordBlock";
    private static final int MIN_WINDOW = 32;
    private static final int WINDOW_MARGIN = 8;

    private final Context mContext;
    private final RichInputConnection mConnection;
    // Resolved on first use: this object is created while the service is still being constructed
    // and has no base context yet.
    private BlockedWordStore mStore;

    public WordBlockController(final Context context, final RichInputConnection connection) {
        mContext = context;
        mConnection = connection;
    }

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
        if (codePoint < 0 || WordFilter.isWordCodePoint(codePoint)) {
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
            try {
                mConnection.endBatchEdit();
            } catch (RuntimeException e) {
                logFailure(e);
            }
        }
    }

    /** @return true if a word was deleted and the batch edit it started is still open. */
    private boolean removeBlockedWord(final SettingsValues settingsValues,
            final EditorInfo editorInfo) {
        try {
            if (!settingsValues.mWordBlockEnabled || !isFilterableField(editorInfo)) {
                return false;
            }
            final WordFilter filter = getStore().getFilter();
            if (filter.isEmpty()) {
                return false;
            }
            final int window = Math.max(MIN_WINDOW, filter.maxEntryLength() + WINDOW_MARGIN);
            // Some editors (terminals, custom views) return null. Then there is nothing to check.
            final CharSequence textBeforeCursor = mConnection.getTextBeforeCursor(window);
            if (textBeforeCursor == null) {
                return false;
            }
            final int length = WordFilter.trailingWordLength(textBeforeCursor);
            final int available = textBeforeCursor.length();
            if (length == 0) {
                return false;
            }
            if (length == available && available >= window) {
                // The word fills the whole window, so its start may have been cut off.
                return false;
            }
            final CharSequence word = textBeforeCursor.subSequence(available - length, available);
            if (!filter.isBlocked(word)) {
                return false;
            }
            return deleteInBatch(length);
        } catch (RuntimeException e) {
            logFailure(e);
            return false;
        }
    }

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

    private BlockedWordStore getStore() {
        if (mStore == null) {
            mStore = BlockedWordStore.getInstance(mContext);
        }
        return mStore;
    }

    private static boolean isFilterableField(final EditorInfo editorInfo) {
        return editorInfo != null && FieldPolicy.isFilterable(editorInfo.inputType);
    }

    private static void logFailure(final RuntimeException e) {
        // Class name only: the message could contain text from the editor.
        Log.w(TAG, "Word filter failed: " + e.getClass().getSimpleName());
    }
}
