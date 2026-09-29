package rkr.simplekeyboard.inputmethod.latin.protection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import rkr.simplekeyboard.inputmethod.latin.protection.CoverStrategy.Action;

public class CoverStrategyTest {
    private final CoverStrategy mStrategy = new CoverStrategy();

    @Test
    public void doesNothingWhenNothingGuardedIsInFront() {
        assertEquals(Action.NONE, mStrategy.onTick(false, 0));
        assertEquals(Action.NONE, mStrategy.onTick(false, 10_000));
        assertFalse(mStrategy.isBlocked());
    }

    @Test
    public void startsTheCoverActivityAtOnce() {
        assertEquals(Action.START_ACTIVITY, mStrategy.onTick(true, 1_000));
    }

    @Test
    public void doesNotStartAgainBeforeTheRetryDelay() {
        assertEquals(Action.START_ACTIVITY, mStrategy.onTick(true, 1_000));
        assertEquals(Action.NONE, mStrategy.onTick(true, 1_750));
        assertEquals(Action.NONE, mStrategy.onTick(true, 1_000 + CoverStrategy.RETRY_MS - 1));
        assertEquals(Action.START_ACTIVITY, mStrategy.onTick(true, 1_000 + CoverStrategy.RETRY_MS));
    }

    @Test
    public void whenTheCoverWorksTheNextVisitStartsAfresh() {
        assertEquals(Action.START_ACTIVITY, mStrategy.onTick(true, 1_000));
        // The cover came up, so the guarded screen is no longer in front.
        assertEquals(Action.NONE, mStrategy.onTick(false, 1_750));
        // The user comes back to the guarded screen a little later: cover it again immediately.
        assertEquals(Action.START_ACTIVITY, mStrategy.onTick(true, 2_500));
    }

    @Test
    public void fallsBackToAnOverlayAfterTheActivityKeepsFailing() {
        long now = 0;
        for (int i = 0; i < CoverStrategy.ACTIVITY_ATTEMPTS; i++) {
            assertEquals(Action.START_ACTIVITY, mStrategy.onTick(true, now));
            now += CoverStrategy.RETRY_MS;
        }
        assertEquals(Action.SHOW_OVERLAY, mStrategy.onTick(true, now));
    }

    @Test
    public void reportsBlockedOnlyAfterEveryKindOfAttemptFailed() {
        long now = 0;
        for (int i = 0; i < CoverStrategy.BLOCKED_AFTER - 1; i++) {
            mStrategy.onTick(true, now);
            now += CoverStrategy.RETRY_MS;
            assertFalse(mStrategy.isBlocked());
        }
        mStrategy.onTick(true, now);
        assertTrue(mStrategy.isBlocked());
    }

    @Test
    public void aWorkingCoverClearsTheBlockedState() {
        long now = 0;
        for (int i = 0; i < CoverStrategy.BLOCKED_AFTER; i++) {
            mStrategy.onTick(true, now);
            now += CoverStrategy.RETRY_MS;
        }
        assertTrue(mStrategy.isBlocked());
        mStrategy.onTick(false, now);
        assertFalse(mStrategy.isBlocked());
    }

    @Test
    public void resetForgetsEverything() {
        mStrategy.onTick(true, 0);
        mStrategy.reset();
        assertEquals(Action.START_ACTIVITY, mStrategy.onTick(true, 1));
        assertFalse(mStrategy.isBlocked());
    }
}
