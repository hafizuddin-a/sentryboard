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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.compat.PreferenceManagerCompat;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsGate;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.WriteGate;

/**
 * The user's blocked-word list: one entry per line in a private file, cached in memory.
 *
 * <p>The list lives in a plain file rather than in SharedPreferences so that it can be excluded
 * from Android's automatic backup on its own (see {@code res/xml/backup_rules.xml}) without
 * excluding the keyboard's other settings, and so that the on-disk format is exactly the entry
 * syntax the user types. It is kept in device-protected storage, like the rest of this keyboard's
 * settings, so the filter still works before the first unlock.
 *
 * <p>The list is read from disk once. {@link #getFilter()} hands out an immutable
 * {@link WordFilter} that is rebuilt whenever the list changes, so callers on the typing path
 * never touch the disk.
 *
 * <p>Privacy: the contents of the list are never logged. Failures are swallowed (the filter simply
 * ends up empty or unchanged) rather than reported with any entry text.
 */
public final class BlockedWordStore {
    static final String FILE_NAME = "blocked_words.txt";
    /** Longest entry accepted. Keeps the window that is read back from the editor small. */
    static final int MAX_ENTRY_LENGTH = 64;

    private static BlockedWordStore sInstance;

    private final File mFile;
    private final WriteGate mGate;
    private volatile List<String> mEntries = Collections.emptyList();
    private final CommonWords mCommonWords;
    private volatile WordFilter mFilter = new WordFilter(mEntries);

    /**
     * Creates a store backed by {@code file} and loads it. A missing or unreadable file is empty.
     * Removing entries is refused while {@code gate} says the settings are locked; adding is not.
     */
    public BlockedWordStore(final File file, final WriteGate gate) {
        this(file, gate, CommonWords.NONE);
    }

    BlockedWordStore(final File file, final WriteGate gate, final CommonWords commonWords) {
        mFile = file;
        mGate = gate;
        mCommonWords = commonWords;
        load();
    }

    /** The process-wide store, shared by the keyboard service and the settings screen. */
    public static synchronized BlockedWordStore getInstance(final Context context) {
        if (sInstance == null) {
            final Context deviceContext = PreferenceManagerCompat.getDeviceContext(context);
            sInstance = new BlockedWordStore(new File(deviceContext.getFilesDir(), FILE_NAME),
                    SettingsGate.getInstance(context), loadCommonWords(context));
        }
        return sInstance;
    }

    /** The ordinary-word list for strict matching, or none (which turns that guard off). */
    private static CommonWords loadCommonWords(final Context context) {
        try (InputStream in = context.getResources().openRawResource(R.raw.common_words)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 18);
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return CommonWords.fromBytes(out.toByteArray());
        } catch (IOException | RuntimeException e) {
            return CommonWords.NONE;
        }
    }

    /**
     * Related forms of an entry worth offering (see {@link VariantSuggester}).
     *
     * @param hideList true while the list is hidden: forms already on it are then offered too, so
     * that the suggestions give nothing away.
     */
    public List<String> suggestVariants(final String entry, final boolean hideList) {
        return VariantSuggester.suggest(entry, hideList ? null : mFilter, mCommonWords);
    }

    /** @return the current filter. Never null, and safe to call on every keystroke. */
    public WordFilter getFilter() {
        return mFilter;
    }

    /** @return the entries in the order they were added, exactly as the user typed them. */
    public List<String> getEntries() {
        return mEntries;
    }

    /**
     * Adds an entry. This is allowed even while the settings are locked: the lock exists to stop
     * the list being weakened, and adding a word can only make it stronger. (Removing is gated.)
     *
     * @return true if it was added; false if it is not a usable entry, it is already in the list,
     * or it could not be saved (in which case the list is unchanged).
     */
    public synchronized boolean add(final String rawEntry) {
        final String entry = clean(rawEntry);
        if (entry == null || indexOf(mEntries, entry) >= 0) {
            return false;
        }
        final List<String> updated = new ArrayList<>(mEntries);
        updated.add(entry);
        return commit(updated);
    }

    /** What became of the lines of a bulk add. */
    public static final class AddResult {
        public final int added;
        public final int alreadyListed;
        public final int unusable;
        /** True if the new entries could not be saved: then nothing was added. */
        public final boolean saveFailed;

        AddResult(final int added, final int alreadyListed, final int unusable,
                final boolean saveFailed) {
            this.added = added;
            this.alreadyListed = alreadyListed;
            this.unusable = unusable;
            this.saveFailed = saveFailed;
        }
    }

    /**
     * Adds several entries at once, saving the list once. Like {@link #add}, this is allowed while
     * the settings are locked. Blank lines are skipped and not counted.
     */
    public synchronized AddResult addAll(final List<String> rawEntries) {
        final List<String> updated = new ArrayList<>(mEntries);
        int added = 0;
        int alreadyListed = 0;
        int unusable = 0;
        for (final String raw : rawEntries) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            final String entry = clean(raw);
            if (entry == null) {
                unusable++;
            } else if (indexOf(updated, entry) >= 0) {
                alreadyListed++;
            } else {
                updated.add(entry);
                added++;
            }
        }
        if (added > 0 && !commit(updated)) {
            return new AddResult(0, alreadyListed, unusable, true);
        }
        return new AddResult(added, alreadyListed, unusable, false);
    }

    /** Splits pasted text into entries: one per line, or separated by commas. */
    public static List<String> splitEntries(final String pasted) {
        final List<String> entries = new ArrayList<>();
        if (pasted != null) {
            for (final String part : pasted.split("[\\r\\n,]+")) {
                if (!part.trim().isEmpty()) {
                    entries.add(part);
                }
            }
        }
        return entries;
    }

    /** @return true if an entry was removed; false if the settings are locked or it is not listed. */
    public synchronized boolean remove(final String entry) {
        if (!mGate.canModifyFilterSettings()) {
            return false;
        }
        final int index = indexOf(mEntries, entry == null ? "" : WordFilter.tidyEntry(entry));
        if (index < 0) {
            return false;
        }
        final List<String> updated = new ArrayList<>(mEntries);
        updated.remove(index);
        return commit(updated);
    }

    private boolean commit(final List<String> updated) {
        if (!save(updated)) {
            return false;
        }
        mEntries = Collections.unmodifiableList(updated);
        mFilter = new WordFilter(updated, mCommonWords);
        return true;
    }

    /**
     * @return the entry with its whitespace tidied (so {@code two  words} and {@code two words} are
     * one entry), or null if it is not usable.
     */
    private static String clean(final String rawEntry) {
        if (rawEntry == null) {
            return null;
        }
        final String entry = WordFilter.tidyEntry(rawEntry);
        if (entry.isEmpty() || entry.length() > MAX_ENTRY_LENGTH || !WordFilter.isValidEntry(entry)) {
            return null;
        }
        return entry;
    }

    private static int indexOf(final List<String> entries, final String entry) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).equalsIgnoreCase(entry)) {
                return i;
            }
        }
        return -1;
    }

    private void load() {
        final List<String> entries = new ArrayList<>();
        if (mFile.isFile()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(mFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String entry = clean(line);
                    if (entry != null && indexOf(entries, entry) < 0) {
                        entries.add(entry);
                    }
                }
            } catch (IOException | RuntimeException e) {
                // Fail open: an unreadable list means the filter does nothing. Do not log the
                // exception message, which could in principle echo file contents.
                entries.clear();
            }
        }
        mEntries = Collections.unmodifiableList(entries);
        mFilter = new WordFilter(entries, mCommonWords);
    }

    /** Writes the whole list to a temporary file and renames it over the real one. */
    private boolean save(final List<String> entries) {
        final File temp = new File(mFile.getPath() + ".tmp");
        try {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(temp), StandardCharsets.UTF_8))) {
                for (final String entry : entries) {
                    writer.write(entry);
                    writer.write('\n');
                }
            }
            if (!temp.renameTo(mFile)) {
                // Some file systems refuse to rename over an existing file.
                if (!mFile.delete() || !temp.renameTo(mFile)) {
                    temp.delete();
                    return false;
                }
            }
            return true;
        } catch (IOException | RuntimeException e) {
            temp.delete();
            return false;
        }
    }
}
