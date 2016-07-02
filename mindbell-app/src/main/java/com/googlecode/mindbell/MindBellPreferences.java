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

import java.util.Set;

import com.googlecode.mindbell.accessors.AndroidContextAccessor;
import com.googlecode.mindbell.accessors.AndroidPrefsAccessor;
import com.googlecode.mindbell.preference.ListPreferenceWithSummaryFix;
import com.googlecode.mindbell.preference.MultiSelectListPreferenceWithSummary;
import com.googlecode.mindbell.util.Utils;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

public class MindBellPreferences extends PreferenceActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    public static final String TAG = "MindBell";

    private static final int REQUEST_CODE_STATUS = 0;

    private static final int REQUEST_CODE_MUTE_OFF_HOOK = 1;

    private void handleMuteHookOffAndStatusPermissionRequestResult(CheckBoxPreference one, int[] grantResults) {
        if (grantResults.length == 0) {
            // if request is cancelled, the result arrays are empty, so leave this option "off" and don't explain it
        } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // User granted the needed permission therefore this option is set to "on"
            one.setChecked(true);
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(MindBellPreferences.this,
                Manifest.permission.READ_PHONE_STATE)) {
            // User denied the needed permission and can be given an explanation, so we show an explanation
            AlertDialog dialog = new AlertDialog.Builder(MindBellPreferences.this) //
                    .setTitle(R.string.reasonReadPhoneStateTitle) //
                    .setMessage(R.string.reasonReadPhoneStateText) //
                    .setPositiveButton(R.string.buttonOk, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // nothing to do
                        }
                    }) //
                    .create();
            dialog.show();
        } else {
            // User denied the needed permission and checked never ask again
            AlertDialog dialog = new AlertDialog.Builder(MindBellPreferences.this) //
                    .setTitle(R.string.neverAskAgainReadPhoneStateTitle) //
                    .setMessage(R.string.neverAskAgainPhoneStateText) //
                    .setPositiveButton(R.string.buttonOk, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // nothing to do
                        }
                    }) //
                    .create();
            dialog.show();
        }
    }

    /**
     * Returns true, if frequency divides an hour in whole numbers, e.g. true for 20 minutes.
     */
    private boolean isFrequencyDividesAnHour(String frequencyValue) {
        long frequencyValueInMinutes = Long.parseLong(frequencyValue) / 60000L;
        return 60 % frequencyValueInMinutes == 0;
    }

    /**
     * Returns true, if normalize - ringing on the minute - is requested
     */
    private boolean isNormalize(String normalizeValue) {
        return !AndroidPrefsAccessor.NORMALIZE_NONE.equals(normalizeValue);
    }

    /**
     * Ensures that the CheckBoxPreferences checkBoxPreferenceMuteOffHook and checkBoxPreferenceStatus cannot be both "on" without
     * having READ_PHONE_STATE permission by returning false when this rule is violated.
     */
    private boolean mediateMuteOffHookAndStatus(CheckBoxPreference other, Object newValue, int requestCode) {
        if (!other.isChecked() || !((Boolean) newValue) || ContextCompat.checkSelfPermission(MindBellPreferences.this,
                Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            // Allow setting this option to "on" if other option is "off" or permission is granted
            return true;
        } else {
            // Ask for permission if other option is "on" and this option shall be set to "on" but permission is missing
            ActivityCompat.requestPermissions(MindBellPreferences.this, new String[] { Manifest.permission.READ_PHONE_STATE },
                    requestCode);
            // As the permission request is asynchronous we habe to deny setting this option (to "on")
            return false;
        }
    }

    @SuppressWarnings("deprecation") // deprecation is because MindBell is not fragment-based
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // check settings, delete any settings that are not valid
        AndroidContextAccessor.getInstance(this).getPrefs();

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences_1);
        addPreferencesFromResource(R.xml.preferences_2); // notifications depend on SDK
        addPreferencesFromResource(R.xml.preferences_3);

        final CheckBoxPreference preferenceStatus = (CheckBoxPreference) getPreferenceScreen()
                .findPreference(getText(R.string.keyStatus));
        final CheckBoxPreference preferenceMuteOffHook = (CheckBoxPreference) getPreferenceScreen()
                .findPreference(getText(R.string.keyMuteOffHook));
        final ListPreferenceWithSummaryFix preferenceFrequency = (ListPreferenceWithSummaryFix) getPreferenceScreen()
                .findPreference(getText(R.string.keyFrequency));
        final CheckBoxPreference preferenceRandomize = (CheckBoxPreference) getPreferenceScreen()
                .findPreference(getText(R.string.keyRandomize));
        final ListPreferenceWithSummaryFix preferenceNormalize = (ListPreferenceWithSummaryFix) getPreferenceScreen()
                .findPreference(getText(R.string.keyNormalize));
        final MultiSelectListPreferenceWithSummary preferenceActiveOnDaysOfWeek = (MultiSelectListPreferenceWithSummary) getPreferenceScreen()
                .findPreference(getText(R.string.keyActiveOnDaysOfWeek));

        preferenceStatus.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            public boolean onPreferenceChange(Preference preference, Object newValue) {
                return mediateMuteOffHookAndStatus(preferenceMuteOffHook, newValue, REQUEST_CODE_STATUS);
            }

        });

        preferenceMuteOffHook.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            public boolean onPreferenceChange(Preference preference, Object newValue) {
                return mediateMuteOffHookAndStatus(preferenceStatus, newValue, REQUEST_CODE_MUTE_OFF_HOOK);
            }

        });

        preferenceRandomize.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if ((Boolean) newValue) {
                    // if interval deviation is selected, normalize is disabled on screen but it must be disabled in preferences,
                    // too. Otherwise the following scenario could happen: set interval 1 h, de-select randomize, set normalize to
                    // hh:00, select randomize, set interval 2 h, de-select randomize again ... hh:00 would be left in normalize
                    // erroneously.
                    preferenceNormalize.setValue(AndroidPrefsAccessor.NORMALIZE_NONE);
                }
                return true;
            }

        });

        preferenceFrequency.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (preferenceRandomize.isChecked()) {
                    // if interval varies randomly, ringing on the minute is disabled and set to "no" anyway
                    return true;
                } else if (isFrequencyDividesAnHour((String) newValue)) {
                    // if frequency is factor of an hour, ringing on the minute may be requested
                    preferenceNormalize.setEnabled(true);
                } else {
                    // if frequency is NOT factor of an hour, ringing on the minute may NOT be set
                    if (preferenceNormalize.isEnabled() && isNormalize(preferenceNormalize.getValue())) {
                        Toast.makeText(preferenceActiveOnDaysOfWeek.getContext(), R.string.frequencyDoesNotFitIntoAnHour,
                                Toast.LENGTH_SHORT).show();
                        return false;
                    } else {
                        preferenceNormalize.setEnabled(false);
                    }
                }
                return true;
            }

        });

        preferenceNormalize.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (!isNormalize((String) newValue)) {
                    // if normalize - ringing on the minute - is not wanted, it's fine, no more to check here
                    return true;
                } else if (isFrequencyDividesAnHour(preferenceFrequency.getValue())) {
                    // if frequency is factor of an hour, requesting ringing on the minute is allowed
                    return true;
                } else {
                    // if frequency is NOT factor of an hour, ringing on the minute may NOT be set
                    Toast.makeText(preferenceActiveOnDaysOfWeek.getContext(), R.string.frequencyDoesNotFitIntoAnHour,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
            }

        });

        preferenceActiveOnDaysOfWeek.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            public boolean onPreferenceChange(Preference preference, Object newValues) {
                if (((Set<?>) newValues).isEmpty()) {
                    Toast.makeText(preferenceActiveOnDaysOfWeek.getContext(), R.string.atLeastOneActiveDayNeeded,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }

        });

    }

    @Override
    public void onPause() {
        super.onPause();
        Utils.updateBellSchedule(this);
    }

    @SuppressWarnings("deprecation") // deprecation is because MindBell is not fragment-based
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
        case REQUEST_CODE_STATUS:
            CheckBoxPreference checkBoxPreferenceStatus = (CheckBoxPreference) getPreferenceScreen()
                    .findPreference(getText(R.string.keyStatus));
            handleMuteHookOffAndStatusPermissionRequestResult(checkBoxPreferenceStatus, grantResults);
            break;
        case REQUEST_CODE_MUTE_OFF_HOOK:
            CheckBoxPreference checkBoxPreferenceMuteOffHook = (CheckBoxPreference) getPreferenceScreen()
                    .findPreference(getText(R.string.keyMuteOffHook));
            handleMuteHookOffAndStatusPermissionRequestResult(checkBoxPreferenceMuteOffHook, grantResults);
            break;
        default:
            break;
        }
    }

}