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

import static com.googlecode.mindbell.MindBellPreferences.TAG;

import java.util.Calendar;

import com.googlecode.mindbell.accessors.AndroidContextAccessor;
import com.googlecode.mindbell.accessors.PrefsAccessor;
import com.googlecode.mindbell.logic.RingingLogic;
import com.googlecode.mindbell.logic.SchedulerLogic;
import com.googlecode.mindbell.util.AlarmManagerCompat;
import com.googlecode.mindbell.util.TimeOfDay;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Ring the bell and reschedule.
 *
 * @author marc
 *
 */
public class Scheduler extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        Log.d(TAG, "random scheduler reached");

        AlarmManagerCompat alarmManager = new AlarmManagerCompat(context);
        PrefsAccessor prefs = AndroidContextAccessor.getInstance(context).getPrefs();

        if (!prefs.isBellActive()) {
            Log.d(TAG, "bell is not active -- not ringing, not rescheduling.");
            return;
        }

        // reschedule
        Intent nextIntent = new Intent(context, Scheduler.class);
        nextIntent.putExtra(context.getText(R.string.extraIsRescheduling).toString(), true);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        long nowMillis = Calendar.getInstance().getTimeInMillis();
        long nextBellTimeMillis = SchedulerLogic.getNextTargetTimeMillis(nowMillis, prefs);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextBellTimeMillis, sender);
        TimeOfDay nextBellTime = new TimeOfDay(nextBellTimeMillis);
        Log.d(TAG, "scheduled next bell alarm for " + nextBellTime.hour + ":" + String.format("%02d", nextBellTime.minute)
                + " on weekday " + nextBellTime.weekday);

        if (!intent.getBooleanExtra(context.getText(R.string.extraIsRescheduling).toString(), false)) {
            Log.d(TAG, "not ringing, has been called by preferences or activate bell button");
            return;
        }

        // ring if daytime
        if (!prefs.isDaytime()) {
            Log.d(TAG, "not ringing, it is night time");
            return;
        }

        if (prefs.doShowBell()) {
            Log.d(TAG, "audio-visual ring");

            Intent ringBell = new Intent(context, MindBell.class);
            PendingIntent bellIntent = PendingIntent.getActivity(context, -1, ringBell, PendingIntent.FLAG_UPDATE_CURRENT);
            try {
                bellIntent.send();
            } catch (CanceledException e) {
                Log.d(TAG, "cannot ring audio-visual bell: " + e.getMessage());
            }

        } else { // ring audio-only immediately:
            Log.d(TAG, "audio-only ring");
            RingingLogic.ringBellAndWait(AndroidContextAccessor.getInstance(context), 15000);
        }

    }

}
