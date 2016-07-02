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
package com.googlecode.mindbell.accessors;

import static com.googlecode.mindbell.MindBellPreferences.TAG;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.googlecode.mindbell.R;
import com.googlecode.mindbell.util.TimeOfDay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

public class AndroidPrefsAccessor extends PrefsAccessor {

    public static final String NORMALIZE_NONE = "-1";

    private final SharedPreferences settings;
    private final String[] hours;

    private final String keyActive;
    private final String keyShow;
    private final String keyStatus;
    private final String keyStatusVisibilityPublic;
    private final String keyStatusIconMaterialDesign;
    private final String keyMuteInFlightMode;
    private final String keyMuteOffHook;
    private final String keyMuteWithPhone;
    private final String keyVibrate;

    private final String keyFrequency;
    private final String keyRandomize;
    private final String keyNormalize;
    private final String keyStart;
    private final String keyEnd;
    private final String keyActiveOnDaysOfWeek;

    private final String keyVolume;

    /**
     * Preference default values ... must correspond with settings in xml definitions
     */
    private final boolean defaultActive = false;
    private final boolean defaultShow = true;
    private final boolean defaultStatus = false;
    private final boolean defaultStatusVisibilityPublic = true;
    private final boolean defaultStatusIconMaterialDesign = true;
    private final boolean defaultMuteInFlightMode = false;
    private final boolean defaultMuteOffHook = true;
    private final boolean defaultMuteWithPhone = true;
    private final boolean defaultVibrate = false;

    private final String defaultFrequency = "3600000";
    private final boolean defaultRandomize = true;
    private final String defaultNormalize = NORMALIZE_NONE;
    private final String defaultStart = "9";
    private final String defaultEnd = "21";
    private final Set<String> defaultActiveOnDaysOfWeek = new HashSet<String>(
            Arrays.asList(new String[] { "1", "2", "3", "4", "5", "6", "7" })); // every day
    private final String[] daysOfWeekValues;
    private final String[] weekdayAbbreviations;

    private final float defaultVolume = AndroidContextAccessor.MINUS_SIX_DB;

    /**
     * Constructs an accessor for preferences in the given context, please use {@link AndroidContextAccessor#getPrefs()} instead
     * of calling this directly.
     */
    protected AndroidPrefsAccessor(Context context) {
        // From target SDK version 11 (HONEYCOMB) upwards changes made in the settings dialog do not arrive in
        // UpdateStatusNotification if MODE_MULTI_PROCESS is not set, see API docs for MODE_MULTI_PROCESS.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            this.settings = context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_MULTI_PROCESS);
        } else {
            this.settings = PreferenceManager.getDefaultSharedPreferences(context);
        }

        hours = context.getResources().getStringArray(R.array.hourStrings);

        keyActive = context.getString(R.string.keyActive);
        keyShow = context.getString(R.string.keyShow);
        keyStatus = context.getString(R.string.keyStatus);
        keyStatusVisibilityPublic = context.getString(R.string.keyStatusVisibilityPublic);
        keyStatusIconMaterialDesign = context.getString(R.string.keyStatusIconMaterialDesign);
        keyMuteInFlightMode = context.getString(R.string.keyMuteInFlightMode);
        keyMuteOffHook = context.getString(R.string.keyMuteOffHook);
        keyMuteWithPhone = context.getString(R.string.keyMuteWithPhone);
        keyVibrate = context.getString(R.string.keyVibrate);

        keyFrequency = context.getString(R.string.keyFrequency);
        keyRandomize = context.getString(R.string.keyRandomize);
        keyNormalize = context.getString(R.string.keyNormalize);
        keyStart = context.getString(R.string.keyStart);
        keyEnd = context.getString(R.string.keyEnd);
        keyActiveOnDaysOfWeek = context.getString(R.string.keyActiveOnDaysOfWeek);
        daysOfWeekValues = context.getResources().getStringArray(R.array.daysOfWeekValues);
        weekdayAbbreviations = context.getResources().getStringArray(R.array.weekdayAbbreviations);

        keyVolume = context.getString(R.string.keyVolume);
        checkSettings();
    }

    /**
     * Check that any data in the SharedPreferences are of the expected type. Should we find anything that doesn't fit the
     * expectations, we delete it.
     */
    private void checkSettings() {
        // boolean settings:
        String[] booleanSettings = new String[] { keyShow, keyStatus, keyStatusVisibilityPublic, keyStatusIconMaterialDesign,
                keyActive, keyMuteInFlightMode, keyMuteOffHook, keyMuteWithPhone, keyVibrate, keyRandomize };
        for (String s : booleanSettings) {
            try {
                settings.getBoolean(s, false);
            } catch (ClassCastException e) {
                settings.edit().remove(s).commit();
                Log.w(TAG, "Removed setting '" + s + "' since it had wrong type");
            }
        }
        // string settings:
        String[] stringSettings = new String[] { keyFrequency, keyNormalize, keyStart, keyEnd };
        for (String s : stringSettings) {
            try {
                settings.getString(s, null);
            } catch (ClassCastException e) {
                settings.edit().remove(s).commit();
                Log.w(TAG, "Removed setting '" + s + "' since it had wrong type");
            }
        }
        // string set settings:
        String[] stringSetSettings = new String[] { keyActiveOnDaysOfWeek };
        for (String s : stringSetSettings) {
            try {
                settings.getStringSet(s, null);
            } catch (ClassCastException e) {
                settings.edit().remove(s).commit();
                Log.w(TAG, "Removed setting '" + s + "' since it had wrong type");
            }
        }
        // float settings:
        String[] floatSettings = new String[] { keyVolume };
        for (String s : floatSettings) {
            try {
                settings.getFloat(s, 0);
            } catch (ClassCastException e) {
                settings.edit().remove(s).commit();
                Log.w(TAG, "Removed setting '" + s + "' since it had wrong type");
            }
        }

        String frequencyString = settings.getString(keyFrequency, null);
        if (frequencyString != null) {
            try {
                long interval = Long.valueOf(frequencyString);
                if (interval < 5 * 60000) { // less than five minutes
                    settings.edit().remove(keyFrequency).commit();
                    Log.w(TAG, "Removed setting '" + keyFrequency + "' since value '" + frequencyString + "' was too low");
                }
            } catch (NumberFormatException e) {
                settings.edit().remove(keyFrequency).commit();
                Log.w(TAG, "Removed setting '" + keyFrequency + "' since value '" + frequencyString + "' is not a number");
            }
        }

        // Now set default values for those that are missing
        if (!settings.contains(keyActive)) {
            setBellActive(defaultActive);
            Log.w(TAG, "Reset missing setting for '" + keyActive + "' to '" + defaultActive + "'");
        }
        if (!settings.contains(keyShow)) {
            settings.edit().putBoolean(keyShow, defaultShow).commit();
            Log.w(TAG, "Reset missing setting for '" + keyShow + "' to '" + defaultShow + "'");
        }
        if (!settings.contains(keyStatus)) {
            settings.edit().putBoolean(keyStatus, defaultStatus).commit();
            Log.w(TAG, "Reset missing setting for '" + keyStatus + "' to '" + defaultStatus + "'");
        }
        if (!settings.contains(keyStatusVisibilityPublic)) {
            settings.edit().putBoolean(keyStatusVisibilityPublic, defaultStatusVisibilityPublic).commit();
            Log.w(TAG,
                    "Reset missing setting for '" + keyStatusVisibilityPublic + "' to '" + defaultStatusVisibilityPublic + "'");
        }
        if (!settings.contains(keyStatusIconMaterialDesign)) {
            settings.edit().putBoolean(keyStatusIconMaterialDesign, defaultStatusIconMaterialDesign).commit();
            Log.w(TAG, "Reset missing setting for '" + keyStatusIconMaterialDesign + "' to '" + defaultStatusIconMaterialDesign
                    + "'");
        }
        if (!settings.contains(keyMuteInFlightMode)) {
            settings.edit().putBoolean(keyMuteInFlightMode, defaultMuteInFlightMode).commit();
            Log.w(TAG, "Reset missing setting for '" + keyMuteInFlightMode + "' to '" + defaultMuteInFlightMode + "'");
        }
        if (!settings.contains(keyMuteOffHook)) {
            settings.edit().putBoolean(keyMuteOffHook, defaultMuteOffHook).commit();
            Log.w(TAG, "Reset missing setting for '" + keyMuteOffHook + "' to '" + defaultMuteOffHook + "'");
        }
        if (!settings.contains(keyMuteWithPhone)) {
            settings.edit().putBoolean(keyMuteWithPhone, defaultMuteWithPhone).commit();
            Log.w(TAG, "Reset missing setting for '" + keyMuteWithPhone + "' to '" + defaultMuteWithPhone + "'");
        }
        if (!settings.contains(keyVibrate)) {
            settings.edit().putBoolean(keyVibrate, defaultVibrate).commit();
            Log.w(TAG, "Reset missing setting for '" + keyVibrate + "' to '" + defaultVibrate + "'");
        }

        if (!settings.contains(keyFrequency)) {
            settings.edit().putString(keyFrequency, defaultFrequency).commit();
            Log.w(TAG, "Reset missing setting for '" + keyFrequency + "' to '" + defaultFrequency + "'");
        }
        if (!settings.contains(keyRandomize)) {
            settings.edit().putBoolean(keyRandomize, defaultRandomize).commit();
            Log.w(TAG, "Reset missing setting for '" + keyRandomize + "' to '" + defaultRandomize + "'");
        }
        if (!settings.contains(keyNormalize)) {
            settings.edit().putString(keyNormalize, defaultNormalize).commit();
            Log.w(TAG, "Reset missing setting for '" + keyNormalize + "' to '" + defaultNormalize + "'");
        }
        if (!settings.contains(keyStart)) {
            settings.edit().putString(keyStart, defaultStart).commit();
            Log.w(TAG, "Reset missing setting for '" + keyStart + "' to '" + defaultStart + "'");
        }
        if (!settings.contains(keyEnd)) {
            settings.edit().putString(keyEnd, defaultEnd).commit();
            Log.w(TAG, "Reset missing setting for '" + keyEnd + "' to '" + defaultEnd + "'");
        }
        if (!settings.contains(keyActiveOnDaysOfWeek)) {
            settings.edit().putStringSet(keyActiveOnDaysOfWeek, defaultActiveOnDaysOfWeek).commit();
            Log.w(TAG, "Reset missing setting for '" + keyActiveOnDaysOfWeek + "' to '" + defaultActiveOnDaysOfWeek + "'");
        }
        if (!settings.contains(keyVolume)) {
            settings.edit().putFloat(keyVolume, defaultVolume).commit();
            Log.w(TAG, "Reset missing setting for '" + keyVolume + "' to '" + defaultVolume + "'");
        }
        // and report the settings:
        StringBuilder sb = new StringBuilder();
        sb.append("Effective settings:\n");
        for (String s : booleanSettings) {
            sb.append(s).append(" = ").append(settings.getBoolean(s, false)).append("\n");
        }
        for (String s : stringSettings) {
            sb.append(s).append(" = ").append(settings.getString(s, null)).append("\n");
        }
        for (String s : stringSetSettings) {
            sb.append(s).append(" = ").append(settings.getStringSet(s, null)).append("\n");
        }
        for (String s : floatSettings) {
            sb.append(s).append(" = ").append(settings.getFloat(s, -1)).append("\n");
        }
        Log.v(TAG, sb.toString());
    }

    @Override
    public boolean doShowBell() {
        return settings.getBoolean(keyShow, defaultShow);
    }

    @Override
    public boolean doStatusNotification() {
        return settings.getBoolean(keyStatus, defaultStatus);
    }

    @Override
    public Set<Integer> getActiveOnDaysOfWeek() {
        Set<String> strings = settings.getStringSet(keyActiveOnDaysOfWeek, defaultActiveOnDaysOfWeek);
        Set<Integer> integers = new HashSet<Integer>();
        for (String string : strings) {
            integers.add(Integer.valueOf(string));
        }
        return integers;
    }

    @Override
    public String getActiveOnDaysOfWeekString() {
        // Warning: Similar code in MindBellPreferences#setMultiSelectListPreferenceSummary()
        Set<Integer> activeOnDaysOfWeek = getActiveOnDaysOfWeek();
        StringBuilder sb = new StringBuilder();
        for (String dayOfWeekValue : daysOfWeekValues) { // internal weekday value in locale oriented order
            Integer dayOfWeekValueAsInteger = Integer.valueOf(dayOfWeekValue);
            if (activeOnDaysOfWeek.contains(dayOfWeekValueAsInteger)) { // active on this day?
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(weekdayAbbreviations[dayOfWeekValueAsInteger - 1]); // add day to the list of active days
            }
        }
        return sb.toString();
    }

    @Override
    public float getBellVolume(float defaultVolume) {
        if (defaultVolume > 1f) {
            throw new IllegalArgumentException("Default volume out of range: " + defaultVolume);
        }
        try {
            return settings.getFloat(keyVolume, defaultVolume);
        } catch (ClassCastException e) {
            Log.e(TAG, "Not a float for volume", e);
            return defaultVolume;
        }
    }

    @Override
    public TimeOfDay getDaytimeEnd() {
        return new TimeOfDay(getDaytimeEndHour(), 0);
    }

    /**
     * @return
     */
    private int getDaytimeEndHour() {
        return Integer.valueOf(settings.getString(keyEnd, defaultEnd));
    }

    @Override
    public String getDaytimeEndString() {
        return hours[getDaytimeEndHour()];
    }

    @Override
    public TimeOfDay getDaytimeStart() {
        return new TimeOfDay(getDaytimeStartHour(), 0);
    }

    private int getDaytimeStartHour() {
        return Integer.valueOf(settings.getString(keyStart, defaultStart));
    }

    @Override
    public String getDaytimeStartString() {
        return hours[getDaytimeStartHour()];
    }

    @Override
    public long getInterval() {
        Log.d(TAG, "frequency: " + settings.getString(keyFrequency, defaultFrequency));
        long interval = Long.valueOf(settings.getString(keyFrequency, defaultFrequency));
        if (interval < 5 * 60000) { // min: 5 minutes
            interval = Long.valueOf(defaultFrequency);
        }
        return interval;
    }

    @Override
    public boolean isBellActive() {
        return settings.getBoolean(keyActive, defaultActive);
    }

    @Override
    public boolean isRandomize() {
        return settings.getBoolean(keyRandomize, defaultRandomize);
    }

    @Override
    public boolean isSettingMuteInFlightMode() {
        return settings.getBoolean(keyMuteInFlightMode, defaultMuteInFlightMode);
    }

    @Override
    public boolean isSettingMuteOffHook() {
        return settings.getBoolean(keyMuteOffHook, defaultMuteOffHook);
    }

    @Override
    public boolean isSettingMuteWithPhone() {
        return settings.getBoolean(keyMuteWithPhone, defaultMuteWithPhone);
    }

    @Override
    public boolean isSettingVibrate() {
        return settings.getBoolean(keyVibrate, defaultVibrate);
    }

    @Override
    public boolean makeStatusNotificationVisibilityPublic() {
        return settings.getBoolean(keyStatusVisibilityPublic, defaultStatusVisibilityPublic);
    }

    @Override
    public void setBellActive(boolean active) {
        settings.edit().putBoolean(keyActive, active).commit();
    }

    @Override
    public void setStatusNotification(boolean statusNotification) {
        settings.edit().putBoolean(keyStatus, statusNotification).commit();
    }

    @Override
    public boolean useStatusIconMaterialDesign() {
        return settings.getBoolean(keyStatusIconMaterialDesign, defaultStatusIconMaterialDesign);
    }

}
