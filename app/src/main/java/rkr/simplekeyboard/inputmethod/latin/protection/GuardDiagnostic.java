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

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * For test builds only: a small floating readout of the foreground package and class, for finding
 * the right names to put in {@code guard_targets.xml} on a given phone. It needs Usage access and
 * Display over other apps, keeps everything in memory, and records and stores nothing.
 */
public final class GuardDiagnostic {
    private static final long POLL_MS = 500;
    private static final long PRIME_WINDOW_MS = 2 * 60 * 1000;

    private static GuardDiagnostic sInstance;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ForegroundTracker mTracker = new ForegroundTracker();
    private final UsageEvents.Event mEvent = new UsageEvents.Event();
    private final UsageStatsManager mUsageStats;
    private final Runnable mTick = this::tick;
    private TextView mView;
    private long mLastQueryEnd;

    private GuardDiagnostic(final Context context) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mUsageStats = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    /** Shows the readout if it is hidden, hides it if it is showing. @return true if now showing. */
    public static synchronized boolean toggle(final Context context) {
        if (sInstance == null) {
            sInstance = new GuardDiagnostic(context.getApplicationContext());
        }
        return sInstance.mView == null ? sInstance.show() : !sInstance.hide();
    }

    public static synchronized boolean isShowing() {
        return sInstance != null && sInstance.mView != null;
    }

    private boolean show() {
        if (!GuardEnvironment.canDrawOverlays(mContext) || !GuardEnvironment.hasUsageAccess(mContext)) {
            return false;
        }
        final TextView view = new TextView(mContext);
        view.setTextColor(Color.WHITE);
        view.setBackgroundColor(Color.argb(0xCC, 0, 0, 0));
        view.setTextSize(11);
        view.setPadding(12, 8, 12, 8);
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP;
        try {
            mWindowManager.addView(view, params);
        } catch (RuntimeException e) {
            return false;
        }
        mView = view;
        mTracker.reset();
        mLastQueryEnd = System.currentTimeMillis() - PRIME_WINDOW_MS;
        mHandler.post(mTick);
        return true;
    }

    private boolean hide() {
        mHandler.removeCallbacks(mTick);
        if (mView != null) {
            try {
                mWindowManager.removeViewImmediate(mView);
            } catch (RuntimeException e) {
                // Already gone.
            }
            mView = null;
        }
        mTracker.reset();
        return true;
    }

    private void tick() {
        if (mView == null) {
            return;
        }
        final long now = System.currentTimeMillis();
        try {
            final UsageEvents events = mUsageStats.queryEvents(mLastQueryEnd - 1000, now);
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(mEvent);
                mTracker.onEvent(mEvent.getEventType(), mEvent.getPackageName(), mEvent.getClassName());
            }
            mLastQueryEnd = now;
            mView.setText(mTracker.packageName() + "\n" + mTracker.className());
        } catch (RuntimeException e) {
            mView.setText("(cannot read usage events)");
        }
        mHandler.postDelayed(mTick, POLL_MS);
    }
}
