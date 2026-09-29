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

import android.text.InputType;

import rkr.simplekeyboard.inputmethod.latin.utils.InputTypeUtils;

/**
 * Decides which kinds of editor field the word filter may touch, from
 * {@code EditorInfo.inputType}. Only integer arithmetic happens here, so it can be unit tested.
 */
final class FieldPolicy {
    private static final int VARIATION_PASSWORD = 0x80;
    private static final int VARIATION_VISIBLE_PASSWORD = 0x90;
    private static final int VARIATION_WEB_PASSWORD = 0xe0;

    private FieldPolicy() {
        // Intentional empty constructor for utility class.
    }

    /**
     * @return true if words typed into a field of this input type may be filtered.
     *
     * Left alone:
     * <ul>
     * <li>Passwords, including "visible" passwords, which is also what many terminal-style
     *     fields report.</li>
     * <li>Number, phone and date/time fields. There the leet mapping would turn digits into
     *     letters, so a typed number could be deleted as a "word".</li>
     * <li>{@code TYPE_NULL}: the app is saying it does not want text editing at all.</li>
     * </ul>
     *
     * A field whose class bits are zero but which sets text variation or flag bits is treated as
     * text. Some apps (Google Contacts' name fields, for one) do that instead of declaring
     * {@code TYPE_CLASS_TEXT}, and rejecting them would silently disable the filter there.
     */
    static boolean isFilterable(final int inputType) {
        if (inputType == InputType.TYPE_NULL) {
            return false;
        }
        final int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        if (inputClass == InputType.TYPE_CLASS_NUMBER
                || inputClass == InputType.TYPE_CLASS_PHONE
                || inputClass == InputType.TYPE_CLASS_DATETIME) {
            return false;
        }
        if (InputTypeUtils.isPasswordInputType(inputType)
                || InputTypeUtils.isVisiblePasswordInputType(inputType)) {
            return false;
        }
        if (inputClass != InputType.TYPE_CLASS_TEXT) {
            // Unspecified class: the password checks above only recognise TYPE_CLASS_TEXT, so
            // look at the variation bits directly.
            final int variation = inputType & InputType.TYPE_MASK_VARIATION;
            return variation != VARIATION_PASSWORD
                    && variation != VARIATION_VISIBLE_PASSWORD
                    && variation != VARIATION_WEB_PASSWORD;
        }
        return true;
    }
}
