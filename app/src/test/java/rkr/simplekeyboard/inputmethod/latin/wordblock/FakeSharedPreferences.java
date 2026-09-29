package rkr.simplekeyboard.inputmethod.latin.wordblock;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** An in-memory SharedPreferences for JVM tests. */
public final class FakeSharedPreferences implements SharedPreferences {
    private final Map<String, Object> mValues = new HashMap<>();
    /** When true, commit() reports failure and writes nothing. */
    public boolean failWrites = false;
    /** When true, getAll() throws, as unreadable storage might. */
    public boolean failReads = false;

    /** Puts a raw value, including one of the wrong type, to simulate corruption. */
    public void putRaw(final String key, final Object value) {
        mValues.put(key, value);
    }

    public Map<String, Object> raw() {
        return mValues;
    }

    @Override
    public Map<String, ?> getAll() {
        if (failReads) {
            throw new RuntimeException("unreadable");
        }
        return new HashMap<>(mValues);
    }

    @Override
    public String getString(final String key, final String defValue) {
        final Object value = mValues.get(key);
        return value instanceof String ? (String) value : defValue;
    }

    @Override
    public Set<String> getStringSet(final String key, final Set<String> defValues) {
        return defValues;
    }

    @Override
    public int getInt(final String key, final int defValue) {
        final Object value = mValues.get(key);
        return value instanceof Integer ? (Integer) value : defValue;
    }

    @Override
    public long getLong(final String key, final long defValue) {
        final Object value = mValues.get(key);
        return value instanceof Long ? (Long) value : defValue;
    }

    @Override
    public float getFloat(final String key, final float defValue) {
        final Object value = mValues.get(key);
        return value instanceof Float ? (Float) value : defValue;
    }

    @Override
    public boolean getBoolean(final String key, final boolean defValue) {
        final Object value = mValues.get(key);
        return value instanceof Boolean ? (Boolean) value : defValue;
    }

    @Override
    public boolean contains(final String key) {
        return mValues.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new FakeEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
            final OnSharedPreferenceChangeListener listener) {
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
            final OnSharedPreferenceChangeListener listener) {
    }

    private final class FakeEditor implements Editor {
        private final Map<String, Object> mPending = new HashMap<>();
        private boolean mClear;

        @Override
        public Editor putString(final String key, final String value) {
            mPending.put(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(final String key, final Set<String> values) {
            mPending.put(key, values);
            return this;
        }

        @Override
        public Editor putInt(final String key, final int value) {
            mPending.put(key, value);
            return this;
        }

        @Override
        public Editor putLong(final String key, final long value) {
            mPending.put(key, value);
            return this;
        }

        @Override
        public Editor putFloat(final String key, final float value) {
            mPending.put(key, value);
            return this;
        }

        @Override
        public Editor putBoolean(final String key, final boolean value) {
            mPending.put(key, value);
            return this;
        }

        @Override
        public Editor remove(final String key) {
            mPending.put(key, null);
            return this;
        }

        @Override
        public Editor clear() {
            mClear = true;
            return this;
        }

        @Override
        public boolean commit() {
            if (failWrites) {
                return false;
            }
            if (mClear) {
                mValues.clear();
            }
            for (final Map.Entry<String, Object> entry : mPending.entrySet()) {
                if (entry.getValue() == null) {
                    mValues.remove(entry.getKey());
                } else {
                    mValues.put(entry.getKey(), entry.getValue());
                }
            }
            return true;
        }

        @Override
        public void apply() {
            commit();
        }
    }
}
