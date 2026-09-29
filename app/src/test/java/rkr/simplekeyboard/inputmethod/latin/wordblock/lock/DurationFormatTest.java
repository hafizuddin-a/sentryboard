package rkr.simplekeyboard.inputmethod.latin.wordblock.lock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DurationFormatTest {
    private static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;

    @Test
    public void describesTheLockPresets() {
        assertEquals("5 minutes", DurationFormat.describe(5 * MINUTE));
        assertEquals("30 minutes", DurationFormat.describe(30 * MINUTE));
        assertEquals("1 hour", DurationFormat.describe(HOUR));
        assertEquals("4 hours", DurationFormat.describe(4 * HOUR));
        assertEquals("12 hours", DurationFormat.describe(12 * HOUR));
        assertEquals("24 hours", DurationFormat.describe(24 * HOUR));
    }

    @Test
    public void describesMixedAndSingularUnits() {
        assertEquals("1 minute", DurationFormat.describe(MINUTE));
        assertEquals("1 hour 1 minute", DurationFormat.describe(HOUR + MINUTE));
        assertEquals("4 hours 30 minutes", DurationFormat.describe(4 * HOUR + 30 * MINUTE));
        assertEquals("45 seconds", DurationFormat.describe(45 * SECOND));
        assertEquals("1 minute 5 seconds", DurationFormat.describe(MINUTE + 5 * SECOND));
    }

    @Test
    public void roundsPartialSecondsUp() {
        assertEquals("1 second", DurationFormat.describe(1));
        assertEquals("6 seconds", DurationFormat.describe(5 * SECOND + 1));
    }

    @Test
    public void describesZeroAndNegativeAsZero() {
        assertEquals("0 seconds", DurationFormat.describe(0));
        assertEquals("0 seconds", DurationFormat.describe(-5));
    }

    @Test
    public void clockShowsMinutesAndSecondsUnderAnHour() {
        assertEquals("0:00", DurationFormat.clock(0));
        assertEquals("0:05", DurationFormat.clock(5 * SECOND));
        assertEquals("4:07", DurationFormat.clock(4 * MINUTE + 7 * SECOND));
        assertEquals("59:59", DurationFormat.clock(HOUR - SECOND));
    }

    @Test
    public void clockShowsHoursFromOneHour() {
        assertEquals("1:00:00", DurationFormat.clock(HOUR));
        assertEquals("1:30:00", DurationFormat.clock(HOUR + 30 * MINUTE));
        assertEquals("24:00:00", DurationFormat.clock(24 * HOUR));
    }

    @Test
    public void clockRoundsUpSoItOnlyReadsZeroWhenDone() {
        assertEquals("0:01", DurationFormat.clock(1));
        assertEquals("0:01", DurationFormat.clock(SECOND));
        assertEquals("0:02", DurationFormat.clock(SECOND + 1));
        assertEquals("0:00", DurationFormat.clock(-100));
    }
}
