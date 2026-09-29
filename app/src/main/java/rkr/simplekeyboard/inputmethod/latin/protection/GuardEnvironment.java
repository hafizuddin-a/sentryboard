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

import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;
import android.os.Process;
import android.provider.Settings;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import rkr.simplekeyboard.inputmethod.R;

/** Reads the guard's inputs from the system: what it may cover, and what it is allowed to do. */
public final class GuardEnvironment {
    private GuardEnvironment() {
        // Intentional empty constructor for utility class.
    }

    /** Loads the screens to cover from {@code res/xml/guard_targets.xml}. */
    public static GuardTargets loadTargets(final Context context) {
        final GuardTargets targets = new GuardTargets();
        final XmlResourceParser parser = context.getResources().getXml(R.xml.guard_targets);
        try {
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "target".equals(parser.getName())) {
                    targets.add(parser.getAttributeValue(null, "package"),
                            parser.getAttributeValue(null, "class-contains"));
                }
            }
        } catch (XmlPullParserException | IOException | RuntimeException e) {
            // Keep whatever was read; an empty list simply means nothing is covered.
        } finally {
            parser.close();
        }
        return targets;
    }

    public static GuardCondition.Requirements checkRequirements(final Context context) {
        return new GuardCondition.Requirements(
                ProtectionController.isAdminActive(context),
                hasUsageAccess(context),
                canDrawOverlays(context),
                notificationsEnabled(context));
    }

    public static boolean hasUsageAccess(final Context context) {
        try {
            final AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            final int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
            if (mode == AppOpsManager.MODE_DEFAULT) {
                return context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
                        == PackageManager.PERMISSION_GRANTED;
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean canDrawOverlays(final Context context) {
        try {
            return Settings.canDrawOverlays(context);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean notificationsEnabled(final Context context) {
        try {
            final NotificationManager manager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            return manager != null && manager.areNotificationsEnabled();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
