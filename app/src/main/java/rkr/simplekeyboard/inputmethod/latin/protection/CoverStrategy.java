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

package rkr.simplekeyboard.inputmethod.latin.protection;

/**
 * Decides how to cover a guarded screen, and notices when nothing works. Pure Java.
 *
 * Android's rules about starting an activity from the background, and about drawing over sensitive
 * Settings screens, differ between versions and manufacturers. On an Android 16 emulator, for
 * instance, an overlay window is rejected over the device-admin screen, while starting an activity
 * from the foreground service is allowed. So the guard does not rely on either: it starts the
 * cover activity first; if the guarded screen is still in front after a moment it tries again,
 * then falls back to an overlay window, and if the screen is still uncovered after that it reports
 * that the cover is being blocked so the app can say so rather than fail silently.
 */
public final class CoverStrategy {
    public enum Action { NONE, START_ACTIVITY, SHOW_OVERLAY }

    /** How long to wait after an attempt before deciding it did not work. */
    public static final long RETRY_MS = 2000;
    /** Attempts with the cover activity before the overlay is tried as well. */
    public static final int ACTIVITY_ATTEMPTS = 2;
    /** Attempts in a row that changed nothing, after which the cover counts as blocked. */
    public static final int BLOCKED_AFTER = 5;

    private int mAttempts;
    private long mLastAttempt = Long.MIN_VALUE / 2;

    /**
     * @param targetInFront whether a guarded screen is currently the foreground one.
     * @param nowMs a monotonic time in milliseconds.
     * @return what to do now.
     */
    public Action onTick(final boolean targetInFront, final long nowMs) {
        if (!targetInFront) {
            // Either nothing to cover, or the cover worked (something else, the cover itself, is now
            // in front). Start afresh next time, including the retry delay, which only exists to
            // avoid starting a second cover while the first is still on its way.
            mAttempts = 0;
            mLastAttempt = Long.MIN_VALUE / 2;
            return Action.NONE;
        }
        if (nowMs - mLastAttempt < RETRY_MS) {
            return Action.NONE;
        }
        mLastAttempt = nowMs;
        mAttempts++;
        return mAttempts <= ACTIVITY_ATTEMPTS ? Action.START_ACTIVITY : Action.SHOW_OVERLAY;
    }

    /** @return true if the guarded screen has stayed uncovered through every kind of attempt. */
    public boolean isBlocked() {
        return mAttempts >= BLOCKED_AFTER;
    }

    public void reset() {
        mAttempts = 0;
        mLastAttempt = Long.MIN_VALUE / 2;
    }
}
