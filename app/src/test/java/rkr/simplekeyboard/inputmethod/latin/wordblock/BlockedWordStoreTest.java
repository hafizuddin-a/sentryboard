package rkr.simplekeyboard.inputmethod.latin.wordblock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.WriteGate;

public class BlockedWordStoreTest {
    private static final WriteGate OPEN = () -> true;

    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();

    private File file() {
        return new File(mFolder.getRoot(), BlockedWordStore.FILE_NAME);
    }

    @Test
    public void missingFileMeansEmptyList() {
        final BlockedWordStore store = new BlockedWordStore(file(), OPEN);
        assertTrue(store.getEntries().isEmpty());
        assertTrue(store.getFilter().isEmpty());
        assertFalse(store.getFilter().isBlocked("word"));
    }

    @Test
    public void addUpdatesTheFilterAndPersists() throws IOException {
        final BlockedWordStore store = new BlockedWordStore(file(), OPEN);
        assertTrue(store.add("word"));
        assertTrue(store.add("other*"));
        assertTrue(store.getFilter().isBlocked("word"));
        assertTrue(store.getFilter().isBlocked("others"));
        assertEquals(Arrays.asList("word", "other*"), store.getEntries());

        assertEquals("word\nother*\n", new String(Files.readAllBytes(file().toPath()), StandardCharsets.UTF_8));

        final BlockedWordStore reloaded = new BlockedWordStore(file(), OPEN);
        assertEquals(Arrays.asList("word", "other*"), reloaded.getEntries());
        assertTrue(reloaded.getFilter().isBlocked("word"));
    }

    @Test
    public void entriesAreTrimmedAndKeptAsTyped() {
        final BlockedWordStore store = new BlockedWordStore(file(), OPEN);
        assertTrue(store.add("  Word!  "));
        assertEquals(Collections.singletonList("Word!"), store.getEntries());
        assertTrue(store.getFilter().isInstant("word"));
    }

    @Test
    public void rejectsUnusableEntries() {
        final BlockedWordStore store = new BlockedWordStore(file(), OPEN);
        assertFalse(store.add(null));
        assertFalse(store.add(""));
        assertFalse(store.add("   "));
        assertFalse(store.add("two words"));
        assertFalse(store.add("*"));
        final StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i <= BlockedWordStore.MAX_ENTRY_LENGTH; i++) {
            tooLong.append('a');
        }
        assertFalse(store.add(tooLong.toString()));
        assertTrue(store.getEntries().isEmpty());
        assertFalse(file().exists());
    }

    @Test
    public void rejectsDuplicatesIgnoringCase() {
        final BlockedWordStore store = new BlockedWordStore(file(), OPEN);
        assertTrue(store.add("word"));
        assertFalse(store.add("word"));
        assertFalse(store.add("WORD"));
        assertFalse(store.add(" word "));
        assertTrue(store.add("word*"));
        assertEquals(2, store.getEntries().size());
    }

    @Test
    public void removeUpdatesTheFilterAndPersists() {
        final BlockedWordStore store = new BlockedWordStore(file(), OPEN);
        store.add("word");
        store.add("other");
        assertTrue(store.remove("word"));
        assertFalse(store.getFilter().isBlocked("word"));
        assertTrue(store.getFilter().isBlocked("other"));
        assertFalse(store.remove("word"));
        assertFalse(store.remove(null));

        assertEquals(Collections.singletonList("other"), new BlockedWordStore(file(), OPEN).getEntries());
    }

    @Test
    public void loadSkipsBlankDuplicateAndUnusableLines() throws IOException {
        Files.write(file().toPath(),
                "word\n\n  \nWORD\ntwo words\n *\nother!\r\n".getBytes(StandardCharsets.UTF_8));
        final BlockedWordStore store = new BlockedWordStore(file(), OPEN);
        assertEquals(Arrays.asList("word", "other!"), store.getEntries());
    }

    @Test
    public void nonAsciiEntriesSurviveAReload() {
        final BlockedWordStore store = new BlockedWordStore(file(), OPEN);
        assertTrue(store.add("w\u00f3rd"));
        assertTrue(store.add("\u043f\u0440\u0438\u0432\u0435\u0442"));
        final BlockedWordStore reloaded = new BlockedWordStore(file(), OPEN);
        assertEquals(Arrays.asList("w\u00f3rd", "\u043f\u0440\u0438\u0432\u0435\u0442"), reloaded.getEntries());
        assertTrue(reloaded.getFilter().isBlocked("word"));
    }

    @Test
    public void failedSaveLeavesTheListUnchanged() throws IOException {
        // A directory where the file should be makes the rename (and the delete) fail.
        final File blocker = file();
        assertTrue(blocker.mkdir());
        assertTrue(new File(blocker, "keep").createNewFile());
        final BlockedWordStore store = new BlockedWordStore(blocker, OPEN);
        assertFalse(store.add("word"));
        assertTrue(store.getEntries().isEmpty());
        assertFalse(store.getFilter().isBlocked("word"));
        assertFalse(new File(blocker.getPath() + ".tmp").exists());
    }

    @Test
    public void listCanBeReadWhileEditing() {
        final BlockedWordStore store = new BlockedWordStore(file(), OPEN);
        store.add("word");
        final java.util.List<String> snapshot = store.getEntries();
        store.add("other");
        // A previously handed-out list is a snapshot and does not change underneath its reader.
        assertEquals(1, snapshot.size());
        assertEquals(2, store.getEntries().size());
    }

    @Test
    public void canStillAddWhileLocked() throws IOException {
        final boolean[] locked = {true};
        final BlockedWordStore store = new BlockedWordStore(file(), () -> !locked[0]);
        assertTrue(store.add("word"));
        assertTrue(store.add("other*"));
        assertEquals(Arrays.asList("word", "other*"), store.getEntries());
        assertTrue(store.getFilter().isBlocked("word"));
        assertEquals("word\nother*\n", new String(Files.readAllBytes(file().toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void addingWhileLockedStillRejectsBadAndDuplicateEntries() {
        final BlockedWordStore store = new BlockedWordStore(file(), () -> false);
        assertTrue(store.add("word"));
        assertFalse(store.add("word"));
        assertFalse(store.add("two words"));
        assertFalse(store.add(""));
        assertEquals(Collections.singletonList("word"), store.getEntries());
    }

    @Test
    public void anEntryAddedWhileLockedCannotBeRemovedWhileLocked() {
        final boolean[] locked = {true};
        final BlockedWordStore store = new BlockedWordStore(file(), () -> !locked[0]);
        assertTrue(store.add("word"));
        assertFalse(store.remove("word"));
        assertTrue(store.getFilter().isBlocked("word"));

        locked[0] = false;
        assertTrue(store.remove("word"));
    }

    @Test
    public void underscoreEntriesAreAccepted() {
        final BlockedWordStore store = new BlockedWordStore(file(), OPEN);
        assertTrue(store.add("snake_case"));
        assertTrue(store.getFilter().isBlocked("snake_case"));
        assertEquals(Collections.singletonList("snake_case"), new BlockedWordStore(file(), OPEN).getEntries());
        assertFalse(store.add("___"));
    }

    @Test
    public void refusesToRemoveWhileLocked() throws IOException {
        final boolean[] locked = {false};
        final BlockedWordStore store = new BlockedWordStore(file(), () -> !locked[0]);
        store.add("word");
        locked[0] = true;
        assertFalse(store.remove("word"));
        assertEquals(Collections.singletonList("word"), store.getEntries());
        assertTrue(store.getFilter().isBlocked("word"));
        assertEquals("word\n", new String(Files.readAllBytes(file().toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void theListStaysReadableWhileLocked() {
        final boolean[] locked = {false};
        final BlockedWordStore store = new BlockedWordStore(file(), () -> !locked[0]);
        store.add("word");
        locked[0] = true;
        assertEquals(Collections.singletonList("word"), store.getEntries());
        assertTrue(store.getFilter().isBlocked("word"));
    }

    @Test
    public void aLockedStoreStillLoadsItsFile() throws IOException {
        Files.write(file().toPath(), "word\n".getBytes(StandardCharsets.UTF_8));
        final BlockedWordStore store = new BlockedWordStore(file(), () -> false);
        assertEquals(Collections.singletonList("word"), store.getEntries());
    }
}
