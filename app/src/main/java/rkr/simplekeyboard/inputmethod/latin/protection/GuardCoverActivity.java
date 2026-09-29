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

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.settings.SettingsActivity;

/**
 * The cover, as a normal full-screen activity that the guard starts on top of a guarded screen.
 *
 * It never traps: Back goes Home, the system's Home and Recents keys work as always, and the
 * activity closes itself the moment it is no longer in front.
 */
public final class GuardCoverActivity extends Activity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        final boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        final int background = dark ? Color.rgb(0x1C, 0x1B, 0x1F) : Color.rgb(0xFA, 0xFA, 0xFA);
        final int text = dark ? Color.WHITE : Color.rgb(0x1C, 0x1B, 0x1F);
        final int pad = (int) (24 * getResources().getDisplayMetrics().density);

        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(background);
        final LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(pad, pad, pad, pad);

        final TextView title = new TextView(this);
        title.setText(R.string.guard_cover_title);
        title.setTextColor(text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setGravity(Gravity.CENTER);
        column.addView(title, wrap());

        final TextView body = new TextView(this);
        body.setText(R.string.guard_cover_body);
        body.setTextColor(text);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        body.setGravity(Gravity.CENTER);
        final LinearLayout.LayoutParams bodyParams = wrap();
        bodyParams.topMargin = pad;
        column.addView(body, bodyParams);

        final Button open = new Button(this);
        open.setText(R.string.guard_cover_open_app);
        open.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
        });
        final LinearLayout.LayoutParams openParams = wrap();
        openParams.topMargin = pad * 2;
        column.addView(open, openParams);

        final Button home = new Button(this);
        home.setText(R.string.guard_cover_go_home);
        home.setOnClickListener(v -> goHome());
        column.addView(home, wrap());

        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setContentView(root);
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onBackPressed() {
        goHome();
    }

    private void goHome() {
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    @Override
    protected void onStop() {
        // Once the cover is no longer in front it has nothing left to say.
        finish();
        super.onStop();
    }
}
