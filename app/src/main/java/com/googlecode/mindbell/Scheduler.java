/*******************************************************************************
 * MindBell - Aims to give you a support for staying mindful in a busy life -
 * for remembering what really counts
 * <p>
 * Copyright (C) 2010-2014 Marc Schroeder
 * Copyright (C) 2014-2016 Uwe Damken
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.googlecode.mindbell;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.googlecode.mindbell.accessors.AndroidContextAccessor;
import com.googlecode.mindbell.accessors.ContextAccessor;
import com.googlecode.mindbell.accessors.PrefsAccessor;
import com.googlecode.mindbell.logic.SchedulerLogic;
import com.googlecode.mindbell.util.TimeOfDay;

import java.util.Calendar;

import static com.googlecode.mindbell.MindBellPreferences.TAG;

/**
 * Ring the bell and reschedule.
 */
public class Scheduler extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        // Fetch intent argument ids
        final String extraIsRescheduling = context.getText(R.string.extraIsRescheduling).toString();
        final String extraNowTimeMillis = context.getText(R.string.extraNowTimeMillis).toString();
        final String extraMeditationPeriod = context.getText(R.string.extraMeditationPeriod).toString();

        // Fetch intent arguments
        final boolean isRescheduling = intent.getBooleanExtra(extraIsRescheduling, false);
        final long nowTimeMillis = intent.getLongExtra(extraNowTimeMillis, Calendar.getInstance().getTimeInMillis());
        final int meditationPeriod = intent.getIntExtra(extraMeditationPeriod, -1);

        MindBell.logDebug("Scheduler received intent: isRescheduling=" + isRescheduling + ", nowTimeMillis=" + nowTimeMillis + ", meditationPeriod=" + meditationPeriod);

        // Create working environment
        ContextAccessor contextAccessor = AndroidContextAccessor.getInstanceAndLogPreferences(context);
        PrefsAccessor prefs = contextAccessor.getPrefs();

        // Update notification just in case state has changed or MindBell missed a muting
        contextAccessor.updateStatusNotification();

        // Evaluate next time to ring and reschedule or terminate method if neither active nor meditating
        if (prefs.setMeditating()) { // Meditating overrides Active therefore check this first

            handleMeditatingBell(contextAccessor, nowTimeMillis, meditationPeriod);

        } else if (prefs.setActive()) {

            handleActiveBell(contextAccessor, nowTimeMillis, isRescheduling);

        } else {

            Log.d(TAG, "Bell is neither meditating nor active -- not ringing, not rescheduling.");

        }
    }

    /**
     * Reschedules next alarm, shows bell, plays bell sound and vibrates - whatever is requested.
     */
    private void handleActiveBell(ContextAccessor contextAccessor, long nowTimeMillis, boolean isRescheduling) {
        PrefsAccessor prefs = contextAccessor.getPrefs();

        long nextTargetTimeMillis = SchedulerLogic.getNextTargetTimeMillis(nowTimeMillis, prefs);
        contextAccessor.reschedule(nextTargetTimeMillis, null);

        if (!isRescheduling) {

            Log.d(TAG, "Not ringing (show/sound/vibrate), has been called by activate bell button or preferences or when boot completed or after updating");

        } else if (!(new TimeOfDay()).isDaytime(prefs)) {

            Log.d(TAG, "Not ringing (show/sound/vibrate), it is night time");

        } else if (contextAccessor.isMuteRequested(true)) {

            Log.d(TAG, "Not ringing (show/sound/vibrate), bell is muted with phone");

         } else if (prefs.isShow()) {

            Log.d(TAG, "Show bell, then play sound and vibrate if requested");
            contextAccessor.showBell();

        } else {

            Log.d(TAG, "Play sound and vibrate if requested but do not show bell");
            contextAccessor.startPlayingSoundAndVibrate(prefs.forRegularOperation(), null);
        }
    }

    /**
     * Reschedules next alarm and rings differently depending on the currently started (!) meditation period.
     */
    private void handleMeditatingBell(ContextAccessor contextAccessor, long nowTimeMillis, int meditationPeriod) {
        PrefsAccessor prefs = contextAccessor.getPrefs();

        if (meditationPeriod == 0) { // beginning of ramp-up period?

            long nextTargetTimeMillis = nowTimeMillis + prefs.getRampUpTimeMillis();
            contextAccessor.reschedule(nextTargetTimeMillis, meditationPeriod + 1);

        } else if (meditationPeriod == 1) { // beginning of meditation period 1

            long nextTargetTimeMillis = nowTimeMillis + prefs.getMeditationDurationMillis() / prefs.getNumberOfPeriodsAsInteger();
            contextAccessor.reschedule(nextTargetTimeMillis, meditationPeriod + 1);
            contextAccessor.startPlayingSoundAndVibrate(prefs.forMeditationBeginning(), null);

        } else if (meditationPeriod <= prefs.getNumberOfPeriodsAsInteger()) { // beginning of meditation period 2..n

            long nextTargetTimeMillis = nowTimeMillis + prefs.getMeditationDurationMillis() / prefs.getNumberOfPeriodsAsInteger();
            contextAccessor.reschedule(nextTargetTimeMillis, meditationPeriod + 1);
            contextAccessor.startPlayingSoundAndVibrate(prefs.forMeditationInterrupting(), null);

        } else { // end of last meditation period

            Log.d(TAG, "Meditation is over -- not rescheduling.");
            contextAccessor.startPlayingSoundAndVibrate(prefs.forMeditationEnding(), null);

        }
    }

}
