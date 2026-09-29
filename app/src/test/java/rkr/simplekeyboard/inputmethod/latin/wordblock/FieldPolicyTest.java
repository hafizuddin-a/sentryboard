package rkr.simplekeyboard.inputmethod.latin.wordblock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.text.InputType;

import org.junit.Test;

public class FieldPolicyTest {
    @Test
    public void plainTextIsFiltered() {
        assertTrue(FieldPolicy.isFilterable(InputType.TYPE_CLASS_TEXT));
        assertTrue(FieldPolicy.isFilterable(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        assertTrue(FieldPolicy.isFilterable(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME));
        assertTrue(FieldPolicy.isFilterable(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS));
        assertTrue(FieldPolicy.isFilterable(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT));
        assertTrue(FieldPolicy.isFilterable(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
    }

    @Test
    public void passwordsAreLeftAlone() {
        assertFalse(FieldPolicy.isFilterable(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
        assertFalse(FieldPolicy.isFilterable(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
        assertFalse(FieldPolicy.isFilterable(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD));
        assertFalse(FieldPolicy.isFilterable(
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD));
        // Terminal-style fields commonly combine visible-password with no-suggestions.
        assertFalse(FieldPolicy.isFilterable(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
    }

    @Test
    public void numberPhoneAndDateFieldsAreLeftAlone() {
        assertFalse(FieldPolicy.isFilterable(InputType.TYPE_CLASS_NUMBER));
        assertFalse(FieldPolicy.isFilterable(
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL));
        assertFalse(FieldPolicy.isFilterable(InputType.TYPE_CLASS_PHONE));
        assertFalse(FieldPolicy.isFilterable(InputType.TYPE_CLASS_DATETIME));
    }

    @Test
    public void typeNullIsLeftAlone() {
        assertFalse(FieldPolicy.isFilterable(InputType.TYPE_NULL));
    }

    @Test
    public void unspecifiedClassWithTextFlagsIsTreatedAsText() {
        // Google Contacts' name fields report 0x2060: person-name variation and cap-words flag,
        // but the class bits are zero.
        assertTrue(FieldPolicy.isFilterable(0x2060));
        assertTrue(FieldPolicy.isFilterable(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));
    }

    @Test
    public void unspecifiedClassWithPasswordVariationIsLeftAlone() {
        assertFalse(FieldPolicy.isFilterable(InputType.TYPE_TEXT_VARIATION_PASSWORD));
        assertFalse(FieldPolicy.isFilterable(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
        assertFalse(FieldPolicy.isFilterable(InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD));
    }
}
