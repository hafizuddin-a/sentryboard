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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import java.util.List;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsActivity;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsGate;
import rkr.simplekeyboard.inputmethod.latin.wordblock.lock.SettingsLock;

/**
 * The guard. While uninstall protection and the settings lock are both on, this foreground service
 * (with a permanent, visible notification) watches which screen is in front and, if it is the
 * system's device-admin screen, covers it. See {@link GuardCondition} for when it is active.
 *
 * It polls {@code UsageStatsManager} events only while it should be guarding and the screen is on,
 * and does nothing at all while the screen is off. It needs Usage access to see the foreground,
 * and Display over other apps to draw the cover. It is not an accessibility service and reads
 * nothing but which screen is in front.
 */
public final class GuardService extends Service {
    private static final String CHANNEL_ID = "guard";
    private static final int NOTIFICATION_ID = 1;
    /** How often the foreground screen is checked while guarding. */
    private static final long POLL_MS = 750;
    /** How often the (in-memory) lock state is re-read while not guarding. */
    private static final long IDLE_MS = 2000;
    /** How often the slower-changing inputs (permissions, admin) are re-read. */
    private static final long SLOW_CHECK_MS = 5000;
    /** History replayed when guarding starts, to learn what is already in front. */
    private static final long PRIME_WINDOW_MS = 2 * 60 * 1000;
    /** Events are re-read slightly early so that none is missed; replaying is harmless. */
    private static final long OVERLAP_MS = 1000;

    private static volatile boolean sRunning;
    private static volatile boolean sCoverBlocked;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTick = this::tick;
    private final ForegroundTracker mTracker = new ForegroundTracker();
    private final CoverStrategy mCoverStrategy = new CoverStrategy();
    // One reusable event object: the polling loop allocates nothing else.
    private final UsageEvents.Event mEvent = new UsageEvents.Event();
    private GuardTargets mTargets;
    private GuardCover mCover;
    private UsageStatsManager mUsageStats;
    private GuardCondition.Requirements mRequirements;
    private boolean mDegradedShown;
    private boolean mBlockedShown;
    private boolean mScreenOn = true;
    private boolean mNeedPrime = true;
    private long mLastQueryEnd;
    private long mLastSlowCheck;

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            mScreenOn = Intent.ACTION_SCREEN_ON.equals(intent.getAction());
            mNeedPrime = true;
            mHandler.removeCallbacks(mTick);
            if (mScreenOn) {
                mLastSlowCheck = 0; // Look at everything afresh.
                mHandler.post(mTick);
            } else {
                mCover.hide();
                mTracker.reset();
                mCoverStrategy.reset();
            }
        }
    };

    /**
     * Starts the guard if protection and the lock are on and it is not already running. Safe to
     * call often, and from anywhere: if Android will not allow a foreground service to start from
     * where this is called, nothing happens.
     */
    public static void ensureRunning(final Context context) {
        if (sRunning) {
            return;
        }
        try {
            final Context appContext = context.getApplicationContext() != null
                    ? context.getApplicationContext() : context;
            if (GuardCondition.shouldRunService(ProtectionController.isAdminActive(appContext),
                    SettingsGate.getInstance(appContext).state())) {
                appContext.startForegroundService(new Intent(appContext, GuardService.class));
            }
        } catch (RuntimeException e) {
            // For example ForegroundServiceStartNotAllowedException. It is tried again later.
        }
    }

    public static boolean isRunning() {
        return sRunning;
    }

    /** @return true if Android has been stopping the guard from covering the device-admin screen. */
    public static boolean isCoverBlocked() {
        return sRunning && sCoverBlocked;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sRunning = true;
        mTargets = GuardEnvironment.loadTargets(this);
        mCover = new GuardCover(this);
        mUsageStats = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        final PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mScreenOn = power == null || power.isInteractive();
        createChannel();
        startInForeground(buildNotification(null));
        final IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenReceiver, filter);
        if (mScreenOn) {
            mHandler.post(mTick);
        }
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        // A start request means something changed: look at everything again.
        mLastSlowCheck = 0;
        mHandler.removeCallbacks(mTick);
        if (mScreenOn) {
            mHandler.post(mTick);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        sRunning = false;
        mHandler.removeCallbacks(mTick);
        mCover.hide();
        try {
            unregisterReceiver(mScreenReceiver);
        } catch (IllegalArgumentException e) {
            // Not registered.
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    private void tick() {
        mHandler.removeCallbacks(mTick);
        if (!mScreenOn) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (mRequirements == null || now - mLastSlowCheck >= SLOW_CHECK_MS) {
            mLastSlowCheck = now;
            mRequirements = checkRequirements();
            final SettingsLock.State lock = SettingsGate.getInstance(this).state();
            if (!GuardCondition.shouldRunService(mRequirements.deviceAdmin, lock)) {
                stopSelf(); // Protection or the lock was turned off: nothing left to guard.
                return;
            }
            updateNotification(lock);
        }
        final SettingsLock.State lock = SettingsGate.getInstance(this).state();
        if (!GuardCondition.shouldGuard(mRequirements, lock, mScreenOn)) {
            mCover.hide();
            mTracker.reset();
            mCoverStrategy.reset();
            sCoverBlocked = false;
            mNeedPrime = true;
            mHandler.postDelayed(mTick, IDLE_MS);
            return;
        }
        pollForeground(now);
        cover();
        mHandler.postDelayed(mTick, POLL_MS);
    }

    /**
     * Reads the requirements from the system. The one reading that cannot be trusted at every
     * moment is "Display over other apps": while the device-admin screen is in front, Android hides
     * other apps' overlays and reports the permission as not granted. Taken at face value that would
     * switch the guard off exactly when it is needed, so while a guarded screen is in front the last
     * value is kept instead.
     */
    private GuardCondition.Requirements checkRequirements() {
        final GuardCondition.Requirements fresh = GuardEnvironment.checkRequirements(this);
        if (mRequirements != null && mTracker.isForeground(mTargets)) {
            return new GuardCondition.Requirements(fresh.deviceAdmin, fresh.usageAccess,
                    mRequirements.overlay, fresh.notifications);
        }
        return fresh;
    }

    /** Covers the guarded screen if it is in front: see {@link CoverStrategy}. */
    private void cover() {
        final boolean inFront = mTracker.isForeground(mTargets);
        switch (mCoverStrategy.onTick(inFront, SystemClock.elapsedRealtime())) {
            case START_ACTIVITY:
                try {
                    startActivity(new Intent(this, GuardCoverActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException e) {
                    // Blocked by the system; the strategy will try again, then fall back.
                }
                break;
            case SHOW_OVERLAY:
                mCover.show();
                break;
            default:
                break;
        }
        if (!inFront) {
            mCover.hide();
        }
        sCoverBlocked = mCoverStrategy.isBlocked();
        if (sCoverBlocked != mBlockedShown) {
            mBlockedShown = sCoverBlocked;
            final NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.notify(NOTIFICATION_ID, buildNotification(
                    mDegradedShown ? mRequirements : null, sCoverBlocked));
        }
    }

    /** Feeds new usage events to the tracker. On the first poll it also replays recent history. */
    private void pollForeground(final long now) {
        final long begin;
        if (mNeedPrime) {
            mTracker.reset();
            begin = now - PRIME_WINDOW_MS;
            mNeedPrime = false;
        } else {
            begin = mLastQueryEnd - OVERLAP_MS;
        }
        mLastQueryEnd = now;
        try {
            final UsageEvents events = mUsageStats.queryEvents(begin, now);
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(mEvent);
                mTracker.onEvent(mEvent.getEventType(), mEvent.getPackageName(), mEvent.getClassName());
            }
        } catch (RuntimeException e) {
            // Usage access was just revoked; the next slow check will notice and say so.
        }
    }

    // ---- Notification -------------------------------------------------------------------------

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    getString(R.string.guard_channel_name), NotificationManager.IMPORTANCE_LOW));
        }
    }

    private void startInForeground(final Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    /** Shows the normal or the degraded notification, whichever is true now. */
    private void updateNotification(final SettingsLock.State lock) {
        final boolean degraded = GuardCondition.isDegraded(mRequirements, lock);
        if (degraded == mDegradedShown && mRequirements.allMet() == !degraded) {
            return;
        }
        mDegradedShown = degraded;
        final NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification(degraded ? mRequirements : null));
    }

    private Notification buildNotification(final GuardCondition.Requirements degradedBy) {
        return buildNotification(degradedBy, false);
    }

    private Notification buildNotification(final GuardCondition.Requirements degradedBy,
            final boolean blocked) {
        final PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, SettingsActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        final Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_notification_shield)
                .setContentIntent(open)
                .setOngoing(true);
        if (blocked) {
            builder.setContentTitle(getString(R.string.guard_notification_degraded_title))
                    .setContentText(getString(R.string.guard_notification_blocked_text));
        } else if (degradedBy == null) {
            builder.setContentTitle(getString(R.string.guard_notification_title))
                    .setContentText(getString(R.string.guard_notification_text));
        } else {
            builder.setContentTitle(getString(R.string.guard_notification_degraded_title))
                    .setContentText(getString(R.string.guard_notification_degraded_text,
                            describe(degradedBy.missing())));
        }
        return builder.build();
    }

    private String describe(final List<GuardCondition.Requirement> missing) {
        final StringBuilder text = new StringBuilder();
        for (final GuardCondition.Requirement requirement : missing) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(getString(GuardLabels.title(requirement)));
        }
        return text.toString();
    }
}
