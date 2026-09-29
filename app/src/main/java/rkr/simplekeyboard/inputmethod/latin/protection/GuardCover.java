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

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsActivity;

/**
 * The full-screen message drawn over a guarded screen: an overlay window (which needs "Display
 * over other apps"). It never traps: the system's Home, Back and Recents keys are outside it, its
 * own Back key goes Home, and it is taken down as soon as the guarded screen is no longer showing.
 */
final class GuardCover {
    private final Context mContext;
    private final WindowManager mWindowManager;
    private View mView;

    GuardCover(final Context context) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    boolean isShowing() {
        return mView != null;
    }

    void show() {
        if (mView != null) {
            return;
        }
        final View view = build();
        final int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, type,
                // Focusable on purpose, so that Back reaches this window and can go Home.
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.OPAQUE);
        params.setTitle("Sentryboard uninstall protection");
        try {
            mWindowManager.addView(view, params);
            mView = view;
            view.requestFocus();
        } catch (RuntimeException e) {
            mView = null; // No overlay permission (any more): the service will report it.
        }
    }

    void hide() {
        if (mView == null) {
            return;
        }
        try {
            mWindowManager.removeViewImmediate(mView);
        } catch (RuntimeException e) {
            // Already gone.
        }
        mView = null;
    }

    private View build() {
        final boolean dark = (mContext.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        final int background = dark ? Color.rgb(0x1C, 0x1B, 0x1F) : Color.rgb(0xFA, 0xFA, 0xFA);
        final int text = dark ? Color.WHITE : Color.rgb(0x1C, 0x1B, 0x1F);
        final float density = mContext.getResources().getDisplayMetrics().density;
        final int pad = (int) (24 * density);

        final FrameLayout root = new FrameLayout(mContext) {
            @Override
            public boolean dispatchKeyEvent(final KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        goHome();
                    }
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        root.setBackgroundColor(background);
        root.setFocusableInTouchMode(true);

        final LinearLayout column = new LinearLayout(mContext);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(pad, pad, pad, pad);

        final TextView title = new TextView(mContext);
        title.setText(R.string.guard_cover_title);
        title.setTextColor(text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setGravity(Gravity.CENTER);
        column.addView(title, wrap());

        final TextView body = new TextView(mContext);
        body.setText(R.string.guard_cover_body);
        body.setTextColor(text);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        body.setGravity(Gravity.CENTER);
        final LinearLayout.LayoutParams bodyParams = wrap();
        bodyParams.topMargin = pad;
        column.addView(body, bodyParams);

        final Button open = new Button(mContext);
        open.setText(R.string.guard_cover_open_app);
        open.setOnClickListener(v -> openApp());
        final LinearLayout.LayoutParams openParams = wrap();
        openParams.topMargin = pad * 2;
        column.addView(open, openParams);

        final Button home = new Button(mContext);
        home.setText(R.string.guard_cover_go_home);
        home.setOnClickListener(v -> goHome());
        column.addView(home, wrap());

        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return root;
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void openApp() {
        mContext.startActivity(new Intent(mContext, SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void goHome() {
        mContext.startActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
