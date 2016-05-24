/*******************************************************************************
 * MindBell - Aims to give you a support for staying mindful in a busy life -
 *            for remembering what really counts
 *
 *     Copyright (C) 2010-2014 Marc Schroeder
 *     Copyright (C) 2014-2016 Uwe Damken
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.googlecode.mindbell;

import com.googlecode.mindbell.accessors.AndroidContextAccessor;
import com.googlecode.mindbell.accessors.AndroidPrefsAccessor;
import com.googlecode.mindbell.accessors.ContextAccessor;
import com.googlecode.mindbell.accessors.PrefsAccessor;
import com.googlecode.mindbell.logic.RingingLogic;
import com.googlecode.mindbell.util.Utils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.widget.Toast;

public class MindBellMain extends Activity {

    // TODO dkn For the time being there is no need to show a popup on first startup
    // private static final String POPUP_PREFS_FILE = "popup-prefs";

    // TODO dkn For the time being there is no need to show a popup on first startup
    // private static final String KEY_POPUP = "popup";

    // TODO dkn For the time being there is no need to show a popup on first startup
    // private SharedPreferences popupPrefs;

    // TODO dkn For the time being there is no need to show a popup on first startup
    // private void checkWhetherToShowPopup() {
    // if (!hasShownPopup()) {
    // setPopupShown(true);
    // showPopup();
    // }
    // }

    // TODO dkn For the time being there is no need to show a popup on first startup
    // private boolean hasShownPopup() {
    // return popupPrefs.getBoolean(KEY_POPUP, false);
    // }

    /**
     * Show hint how to activate the bell.
     */
    private void notifyIfNotActive() {
        if (!new AndroidPrefsAccessor(this).isBellActive()) {
            Toast.makeText(this, R.string.howToSet, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // TODO dkn For the time being there is no need to show a popup on first startup
        // popupPrefs = getSharedPreferences(POPUP_PREFS_FILE, MODE_PRIVATE);
        setContentView(R.layout.main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the currently selected menu XML resource.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings, menu);
        MenuItem settingsItem = menu.findItem(R.id.settings);
        settingsItem.setIntent(new Intent(this, MindBellPreferences.class));
        MenuItem aboutItem = menu.findItem(R.id.about);
        aboutItem.setIntent(new Intent(this, AboutActivity.class));
        MenuItem activeItem = menu.findItem(R.id.active);
        activeItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {

            public boolean onMenuItemClick(MenuItem item) {
                PrefsAccessor prefsAccessor = new AndroidPrefsAccessor(MindBellMain.this);
                prefsAccessor.setBellActive(!prefsAccessor.isBellActive()); // toggle active/inactive
                Utils.updateBellSchedule(MindBellMain.this);
                invalidateOptionsMenu(); // re-call onPrepareOptionsMenu()
                return true;
            }
        });
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem activeItem = menu.findItem(R.id.active);
        PrefsAccessor prefsAccessor = new AndroidPrefsAccessor(MindBellMain.this);
        activeItem.setIcon((prefsAccessor.isBellActive()) ? R.drawable.ic_action_bell_off : R.drawable.ic_action_bell_on);
        return true;
    }

    @Override
    protected void onResume() {
        invalidateOptionsMenu(); // Maybe active setting has been changed via MindBellPreferences
        super.onResume();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            notifyIfNotActive();
            ContextAccessor ca = AndroidContextAccessor.get(this);
            RingingLogic.ringBell(ca, null);
        }
        return true;
    }

    // TODO dkn For the time being there is no need to show a popup on first startup
    // @Override
    // public void onWindowFocusChanged(boolean hasFocus) {
    // super.onWindowFocusChanged(hasFocus);
    // if (hasFocus) {
    // checkWhetherToShowPopup();
    // }
    // }

    // TODO dkn For the time being there is no need to show a popup on first startup
    // private void setPopupShown(boolean shown) {
    // popupPrefs.edit().putBoolean(KEY_POPUP, shown).commit();
    // }

    // TODO dkn For the time being there is no need to show a popup on first startup
    // private void showPopup() {
    // DialogInterface.OnClickListener yesListener = new DialogInterface.OnClickListener() {
    // public void onClick(DialogInterface dialog, int which) {
    // dialog.dismiss();
    // takeUserToOffer();
    // }
    // };
    //
    // View popupView = LayoutInflater.from(this).inflate(R.layout.popup_dialog, null);
    // new AlertDialog.Builder(this).setTitle(R.string.main_title_popup).setIcon(R.drawable.alarm_natural_icon)
    // .setView(popupView).setPositiveButton(R.string.main_yes_popup, yesListener)
    // .setNegativeButton(R.string.main_no_popup, null).show();
    // }

    // TODO dkn For the time being there is no need to show a popup on first startup
    // private void takeUserToOffer() {
    // startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.main_uri_popup))));
    // }
}
