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
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsActivity;

/** The notification shown when uninstall protection is turned off outside the app. */
public final class TamperNotifier {
    static final String CHANNEL_ID = "protection_alerts";
    private static final int NOTIFICATION_ID = 2;

    private TamperNotifier() {
        // Intentional empty constructor for utility class.
    }

    public static void post(final Context context) {
        final NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        final PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, SettingsActivity.class), PendingIntent.FLAG_IMMUTABLE
                        | PendingIntent.FLAG_UPDATE_CURRENT);
        final Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.protection_alert_channel),
                    NotificationManager.IMPORTANCE_HIGH));
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }
        builder.setSmallIcon(R.drawable.ic_notification_shield)
                .setContentTitle(context.getString(R.string.protection_tamper_title))
                .setContentText(context.getString(R.string.protection_tamper_text))
                .setContentIntent(open)
                .setAutoCancel(true);
        // Without the notification permission this is quietly dropped; the app shows a banner too.
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    public static void cancel(final Context context) {
        final NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }
}
