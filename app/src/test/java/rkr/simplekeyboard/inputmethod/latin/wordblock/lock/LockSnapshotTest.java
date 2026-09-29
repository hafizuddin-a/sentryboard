package rkr.simplekeyboard.inputmethod.latin.wordblock.lock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class LockSnapshotTest {
    private static Map<String, Object> valid() {
        return new LockSnapshot(true, 3_600_000L, true, 12345L, 9).toMap();
    }

    private static void assertFailsClosed(final Map<String, Object> stored) {
        final LockSnapshot snapshot = LockSnapshot.fromMap(stored);
        assertTrue(snapshot.lockEnabled);
        assertFalse(snapshot.unlocked);
        assertEquals(LockConstants.DEFAULT_DURATION_MS, snapshot.durationMs);
    }

    @Test
    public void nothingStoredMeansNoLock() {
        final LockSnapshot snapshot = LockSnapshot.fromMap(new HashMap<String, Object>());
        assertFalse(snapshot.lockEnabled);
        assertEquals(LockConstants.DEFAULT_DURATION_MS, snapshot.durationMs);
    }

    @Test
    public void unreadableStorageFailsClosed() {
        final LockSnapshot snapshot = LockSnapshot.fromMap(null);
        assertTrue(snapshot.lockEnabled);
        assertFalse(snapshot.unlocked);
    }

    @Test
    public void roundTrip() {
        final LockSnapshot original = new LockSnapshot(true, 4 * 3_600_000L, true, 987654321L, 42);
        final LockSnapshot copy = LockSnapshot.fromMap(original.toMap());
        assertTrue(copy.lockEnabled);
        assertTrue(copy.unlocked);
        assertEquals(4 * 3_600_000L, copy.durationMs);
        assertEquals(987654321L, copy.unlockedAtElapsed);
        assertEquals(42, copy.bootCountAtUnlock);
    }

    @Test
    public void roundTripOfNoLockKeepsTheDuration() {
        final LockSnapshot copy = LockSnapshot.fromMap(
                new LockSnapshot(false, 12 * 3_600_000L, false, 0, 0).toMap());
        assertFalse(copy.lockEnabled);
        assertEquals(12 * 3_600_000L, copy.durationMs);
    }

    @Test
    public void everyMissingFieldFailsClosed() {
        for (final String key : valid().keySet()) {
            final Map<String, Object> stored = valid();
            stored.remove(key);
            assertFailsClosed(stored);
        }
    }

    @Test
    public void everyWrongTypeFailsClosed() {
        for (final String key : valid().keySet()) {
            final Map<String, Object> stored = valid();
            stored.put(key, "garbage");
            assertFailsClosed(stored);
        }
    }

    @Test
    public void wrongNumericTypesFailClosed() {
        final Map<String, Object> stored = valid();
        stored.put(LockSnapshot.KEY_DURATION_MS, 3600); // Integer, not Long.
        assertFailsClosed(stored);
        final Map<String, Object> other = valid();
        other.put(LockSnapshot.KEY_BOOT_COUNT, 9L); // Long, not Integer.
        assertFailsClosed(other);
    }

    @Test
    public void unknownVersionFailsClosed() {
        final Map<String, Object> stored = valid();
        stored.put(LockSnapshot.KEY_VERSION, 2);
        assertFailsClosed(stored);
    }

    @Test
    public void outOfRangeDurationFailsClosed() {
        for (final long bad : new long[] {0L, -1L, Long.MIN_VALUE, 24 * 3_600_000L + 1, Long.MAX_VALUE}) {
            final Map<String, Object> stored = valid();
            stored.put(LockSnapshot.KEY_DURATION_MS, bad);
            assertFailsClosed(stored);
        }
    }

    @Test
    public void contradictoryStateFailsClosed() {
        final Map<String, Object> stored = valid();
        stored.put(LockSnapshot.KEY_ENABLED, false);
        stored.put(LockSnapshot.KEY_UNLOCKED, true);
        assertFailsClosed(stored);
    }

    @Test
    public void unrelatedExtraKeysAreTolerated() {
        final Map<String, Object> stored = valid();
        stored.put("note_pending", true);
        assertTrue(LockSnapshot.fromMap(stored).unlocked);
    }

    @Test
    public void aPartialFileNeverReadsAsNoLock() {
        // Only one key left: this is corruption, not a fresh install.
        final Map<String, Object> stored = new HashMap<>();
        stored.put(LockSnapshot.KEY_VERSION, 1);
        assertFailsClosed(stored);
    }
}
