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
 * Works out which activity is in the foreground from a stream of usage events, in time order.
 * Pure Java: the event type numbers are those of {@code UsageEvents.Event}.
 */
public final class ForegroundTracker {
    public static final int ACTIVITY_RESUMED = 1;
    public static final int ACTIVITY_PAUSED = 2;
    public static final int ACTIVITY_STOPPED = 23;

    private String mPackage;
    private String mClass;

    /** Feeds one event. Events of other kinds, or without a component, are ignored. */
    public void onEvent(final int type, final String packageName, final String className) {
        if (packageName == null) {
            return;
        }
        if (type == ACTIVITY_RESUMED) {
            mPackage = packageName;
            mClass = className;
        } else if ((type == ACTIVITY_PAUSED || type == ACTIVITY_STOPPED)
                && packageName.equals(mPackage) && equal(className, mClass)) {
            // The current activity went away. If another was resumed meanwhile, its RESUMED event
            // already replaced this one, so a late PAUSED for the old one changes nothing.
            mPackage = null;
            mClass = null;
        }
    }

    public void reset() {
        mPackage = null;
        mClass = null;
    }

    /** @return the package of the foreground activity, or null if unknown. */
    public String packageName() {
        return mPackage;
    }

    /** @return the class of the foreground activity, or null if unknown. */
    public String className() {
        return mClass;
    }

    public boolean isForeground(final GuardTargets targets) {
        return targets.matches(mPackage, mClass);
    }

    private static boolean equal(final String a, final String b) {
        return a == null ? b == null : a.equals(b);
    }
}
