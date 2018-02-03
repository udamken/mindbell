/*
 * MindBell - Aims to give you a support for staying mindful in a busy life -
 *            for remembering what really counts
 *
 *     Copyright (C) 2010-2014 Marc Schroeder
 *     Copyright (C) 2014-2018 Uwe Damken
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
 */
package com.googlecode.mindbell.accessors;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.googlecode.mindbell.MindBell;
import com.googlecode.mindbell.MindBellMain;
import com.googlecode.mindbell.MindBellPreferences;
import com.googlecode.mindbell.MuteActivity;
import com.googlecode.mindbell.R;
import com.googlecode.mindbell.Scheduler;
import com.googlecode.mindbell.util.AlarmManagerCompat;
import com.googlecode.mindbell.util.TimeOfDay;

import java.io.IOException;
import java.text.MessageFormat;

import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;
import static com.googlecode.mindbell.MindBellPreferences.TAG;
import static com.googlecode.mindbell.accessors.PrefsAccessor.EXTRA_IS_RESCHEDULING;
import static com.googlecode.mindbell.accessors.PrefsAccessor.EXTRA_MEDITATION_PERIOD;
import static com.googlecode.mindbell.accessors.PrefsAccessor.EXTRA_NOW_TIME_MILLIS;

public class AndroidContextAccessor extends ContextAccessor implements AudioManager.OnAudioFocusChangeListener {

    private static final int STATUS_NOTIFICATION_ID = 0x7f030001; // historically, has been R.layout.bell for a long time

    private static final int RING_NOTIFICATION_ID = STATUS_NOTIFICATION_ID + 1;

    // Keep MediaPlayer to finish a started sound explicitly, reclaimed when app gets destroyed: http://stackoverflow.com/a/2476171
    private static MediaPlayer mediaPlayer = null;
    private static AudioManager audioManager = null;

    // ApplicationContext of MindBell
    private final Context context;

    /**
     * Constructor is private just in case we want to make this a singleton.
     */
    private AndroidContextAccessor(Context context, boolean logSettings) {
        this.context = context.getApplicationContext();
        this.prefs = new AndroidPrefsAccessor(context, logSettings);
    }

    /**
     * Returns an accessor for the given context, this call also validates the preferences.
     */
    public static AndroidContextAccessor getInstance(Context context) {
        return new AndroidContextAccessor(context, false);
    }

    /**
     * Returns an accessor for the given context, this call also validates the preferences.
     */
    public static AndroidContextAccessor getInstanceAndLogPreferences(Context context) {
        return new AndroidContextAccessor(context, true);
    }

    @Override
    protected String getReasonMutedInFlightMode() {
        return context.getText(R.string.reasonMutedInFlightMode).toString();
    }

    @Override
    protected String getReasonMutedOffHook() {
        return context.getText(R.string.reasonMutedOffHook).toString();
    }

    @Override
    protected String getReasonMutedWithPhone() {
        return context.getText(R.string.reasonMutedWithPhone).toString();
    }

    @Override
    protected String getReasonMutedTill() {
        TimeOfDay mutedTill = new TimeOfDay(prefs.getMutedTill());
        return MessageFormat.format(context.getText(R.string.reasonMutedTill).toString(), mutedTill.getDisplayString(context));
    }

    @Override
    public boolean isPhoneInFlightMode() {
        return Settings.System.getInt(context.getContentResolver(), retrieveAirplaneModeOnConstantName(), 0) == 1;
    }

    /**
     * Returns name of the airplane-mode-on constant depending on the version of Android.
     */
    @NonNull
    private String retrieveAirplaneModeOnConstantName() {
        if (Build.VERSION.SDK_INT >= 17) {
            return Global.AIRPLANE_MODE_ON;
        } else {
            return Settings.System.AIRPLANE_MODE_ON;
        }
    }

    @Override
    public boolean isPhoneMuted() {
        final AudioManager audioMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return audioMan.getStreamVolume(AudioManager.STREAM_RING) == 0;
    }

    @Override
    public boolean isPhoneOffHook() {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE;
    }

    @Override
    public void showMessage(String message) {
        MindBell.logDebug(message);
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void startPlayingSoundAndVibrate(final ActivityPrefsAccessor activityPrefs, final Runnable runWhenDone) {

        // Stop an already ongoing sound, this isn't wrong when phone and bell are muted, too
        finishBellSound();

        // Update ring notification and vibrate on either phone or wearable
        if (activityPrefs.isNotification()) {
            updateRingNotification(activityPrefs);
        }

        // Raise alarm volume to the max but keep the original volume for reset by finishBellSound() and start playing sound if
        // requested by preferences
        boolean playingSoundStarted = false;
        if (activityPrefs.isSound()) {
            playingSoundStarted = startPlayingSound(activityPrefs, runWhenDone);
        }

        // Explicitly start vibration if not already done by ring notification
        if (activityPrefs.isVibrate() && !activityPrefs.isNotification()) {
            startVibration();
        }

        // If ring notification and its dismissal is requested, then we have to wait for a while to dismiss the ring notification
        // afterwards. So a new thread is created that waits and dismisses the ring notification afterwards.
        if (activityPrefs.isNotification() && activityPrefs.isDismissNotification()) {
            startWaiting(new Runnable() {
                @Override
                public void run() {
                    cancelRingNotification(activityPrefs);
                }
            });
        }

        // A non-null runWhenDone means there is something to do at the end (hiding the bell after displaying or stopping the
        // meditation automatically). This is typically done when finishing playing the sound. But if playing a sound has not
        // been started because of preferences or because sound has been suppressed then we after to do it now - or after a
        // little while if the bell will be displayed by MindBell.onStart() after leaving this method.
        if (!playingSoundStarted && runWhenDone != null) {
            if (activityPrefs.isShow()) {
                startWaiting(runWhenDone);
            } else {
                runWhenDone.run();
            }
        }

    }

    @Override
    public void finishBellSound() {
        if (isBellSoundPlaying()) { // do we hold a reference to a MediaPlayer?
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                MindBell.logDebug("Ongoing MediaPlayer stopped");
            }
            mediaPlayer.reset(); // get rid of "mediaplayer went away with unhandled events" log entries
            mediaPlayer.release();
            mediaPlayer = null;
            MindBell.logDebug("Reference to MediaPlayer released");
            if (prefs.isPauseAudioOnSound()) {
                if (audioManager.abandonAudioFocus(this) == AUDIOFOCUS_REQUEST_FAILED) {
                    MindBell.logDebug("Abandon of audio focus failed");
                } else {
                    MindBell.logDebug("Audio focus successfully abandoned");
                }
            }
        }
        // Reset volume to originalVolume if it has been set before (does not equal -1)
        int originalVolume = prefs.getOriginalVolume();
        if (originalVolume < 0) {
            MindBell.logDebug("Finish bell sound found originalVolume " + originalVolume + ", alarm volume left untouched");
        } else {
            int alarmMaxVolume = getAlarmMaxVolume();
            if (originalVolume == alarmMaxVolume) { // "someone" else set it to max, so we don't touch it
                MindBell.logDebug(
                        "Finish bell sound found originalVolume " + originalVolume + " to be max, alarm volume left untouched");
            } else {
                MindBell.logDebug("Finish bell sound found originalVolume " + originalVolume + ", setting alarm volume to it");
                setAlarmVolume(originalVolume);
            }
            prefs.resetOriginalVolume(); // no longer needed therefore invalidate it
        }
    }

    /**
     * This is about updating the ring notification when ringing the bell.
     */
    public void updateRingNotification(ActivityPrefsAccessor activityPrefs) {
        int visibility = (prefs.isNotificationVisibilityPublic()) ?
                NotificationCompat.VISIBILITY_PUBLIC :
                NotificationCompat.VISIBILITY_PRIVATE;
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context.getApplicationContext()) //
                .setCategory(NotificationCompat.CATEGORY_ALARM) //
                .setAutoCancel(true) // cancel notification on touch
                .setColor(context.getResources().getColor(R.color.backgroundColor)) //
                .setContentTitle(prefs.getNotificationTitle()) //
                .setContentText(prefs.getNotificationText()).setSmallIcon(R.drawable.ic_stat_bell_ring) //
                .setVisibility(visibility);
        if (activityPrefs.isVibrate()) {
            notificationBuilder.setVibrate(prefs.getVibrationPattern());
        }
        Notification notification = notificationBuilder.build();
        NotificationManagerCompat.from(context).notify(RING_NOTIFICATION_ID, notification);
    }

    /**
     * Start playing bell sound and call runWhenDone when playing finishes but only if bell is not muted - returns true when
     * sound has been started, false otherwise.
     */
    private boolean startPlayingSound(ActivityPrefsAccessor activityPrefs, final Runnable runWhenDone) {
        Uri bellUri = activityPrefs.getSoundUri();
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (prefs.isNoSoundOnMusic() && audioManager.isMusicActive()) {
            MindBell.logDebug("Sound suppressed because setting is no sound on music and music is playing");
            return false;
        } else if (bellUri == null) {
            MindBell.logDebug("Sound suppressed because no sound has been set");
            return false;
        } else if (prefs.isPauseAudioOnSound()) {
            int requestResult = audioManager.requestAudioFocus(this, prefs.getAudioStream(), retrieveDurationHint());
            if (requestResult == AUDIOFOCUS_REQUEST_FAILED) {
                MindBell.logDebug("Sound suppressed because setting is pause audio on sound and request of audio focus failed");
                return false;
            }
            MindBell.logDebug("Audio focus successfully requested");
        }
        if (prefs.isUseAudioStreamVolumeSetting()) { // we don't care about setting the volume
            MindBell.logDebug("Start playing sound found without touching audio stream volume");
        } else {
            int originalVolume = getAlarmVolume();
            int alarmMaxVolume = getAlarmMaxVolume();
            if (originalVolume == alarmMaxVolume) { // "someone" else set it to max, so we don't touch it
                MindBell.logDebug(
                        "Start playing sound found originalVolume " + originalVolume + " to be max, alarm volume left untouched");
            } else {
                MindBell.logDebug(
                        "Start playing sound found and stored originalVolume " + originalVolume + ", setting alarm volume to max");
                setAlarmVolume(alarmMaxVolume);
                prefs.setOriginalVolume(originalVolume);
            }
        }
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(prefs.getAudioStream());
        if (!prefs.isUseAudioStreamVolumeSetting()) { // care about setting the volume
            float bellVolume = activityPrefs.getVolume();
            mediaPlayer.setVolume(bellVolume, bellVolume);
        }
        try {
            try {
                mediaPlayer.setDataSource(context, bellUri);
            } catch (IOException e) { // probably because of withdrawn permissions, hence use default bell
                mediaPlayer.setDataSource(context, prefs.getStandardSoundUri());
            }
            mediaPlayer.prepare();
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    finishBellSound();
                    if (runWhenDone != null) {
                        runWhenDone.run();
                    }
                }
            });
            mediaPlayer.start();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Could not start playing sound: " + e.getMessage(), e);
            finishBellSound();
            return false;
        }
    }

    /**
     * Vibrate with the requested vibration pattern.
     */
    private void startVibration() {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        vibrator.vibrate(prefs.getVibrationPattern(), -1);
    }

    /**
     * Start waiting for a specific time period and call runWhenDone when time is over.
     */
    private void startWaiting(final Runnable runWhenDone) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(prefs.getEffectiveWaitingTime());
                } catch (InterruptedException e) {
                    // doesn't care if sleep was interrupted, just move on
                }
                runWhenDone.run();
            }
        }).start();
    }

    /**
     * Cancel the ring notification (after ringing the bell).
     */
    public void cancelRingNotification(ActivityPrefsAccessor activityPrefs) {
        NotificationManagerCompat.from(context).cancel(RING_NOTIFICATION_ID);
    }

    @Override
    public boolean isBellSoundPlaying() {
        // if we hold a reference we haven't finished bell sound completely so only the reference is checked
        return mediaPlayer != null;
    }

    @Override
    public int getAlarmMaxVolume() {
        AudioManager audioMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return audioMan.getStreamMaxVolume(AudioManager.STREAM_ALARM);
    }

    /**
     * Returns duration hint for requesting audio focus depending on the version of Android.
     */
    private int retrieveDurationHint() {
        if (Build.VERSION.SDK_INT >= 19) {
            return AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;
        } else {
            return AUDIOFOCUS_GAIN_TRANSIENT;
        }
    }

    @Override
    public int getAlarmVolume() {
        AudioManager audioMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return audioMan.getStreamVolume(AudioManager.STREAM_ALARM);
    }

    @Override
    public void setAlarmVolume(int volume) {
        AudioManager audioMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioMan.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0);

    }

    /**
     * Send a newly created intent to Scheduler to update notification and setup a new bell schedule.
     */
    @Override
    public void updateBellSchedule() {
        Log.d(TAG, "Update bell schedule requested");
        Intent intent = createSchedulerIntent(false, null, null);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            sender.send();
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "Could not update bell schedule: " + e.getMessage(), e);
        }
    }

    /**
     * Create an intent to be send to Scheduler to update notification and to (re-)schedule the bell.
     *
     * @param isRescheduling
     *         True if the intents is meant for rescheduling instead of updating bell schedule.
     * @param nowTimeMillis
     *         If not null millis to be given to Scheduler as now (or nextTargetTimeMillis from the perspective of the previous
     *         call)
     * @param meditationPeriod
     *         Zero: ramp-up, 1-(n-1): intermediate period, n: last period, n+1: beyond end
     */
    private Intent createSchedulerIntent(boolean isRescheduling, Long nowTimeMillis, Integer meditationPeriod) {
        Log.d(TAG, "Creating scheduler intent: isRescheduling=" + isRescheduling + ", nowTimeMillis=" + nowTimeMillis +
                ", meditationPeriod=" + meditationPeriod);
        Intent intent = new Intent(context, Scheduler.class);
        if (isRescheduling) {
            intent.putExtra(EXTRA_IS_RESCHEDULING, true);
        }
        if (nowTimeMillis != null) {
            intent.putExtra(EXTRA_NOW_TIME_MILLIS, nowTimeMillis);
        }
        if (meditationPeriod != null) {
            intent.putExtra(EXTRA_MEDITATION_PERIOD, meditationPeriod);
        }
        return intent;
    }

    /**
     * Send a newly created intent to Scheduler to update notification and setup a new bell schedule for meditation.
     *
     * @param nextTargetTimeMillis
     *         Millis to be given to Scheduler as now (or nextTargetTimeMillis from the perspective of the previous call)
     * @param meditationPeriod
     *         Zero: ramp-up, 1-(n-1): intermediate period, n: last period, n+1: beyond end
     */
    @Override
    public void updateBellSchedule(long nextTargetTimeMillis, int meditationPeriod) {
        Log.d(TAG, "Update bell schedule requested, nextTargetTimeMillis=" + nextTargetTimeMillis);
        Intent intent = createSchedulerIntent(false, nextTargetTimeMillis, meditationPeriod);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            sender.send();
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "Could not update bell schedule: " + e.getMessage(), e);
        }
    }

    /**
     * Reschedule the bell by letting AlarmManager send an intent to Scheduler.
     *
     * @param nextTargetTimeMillis
     *         Millis to be given to Scheduler as now (or nextTargetTimeMillis from the perspective of the previous call)
     * @param nextMeditationPeriod
     *         null if not meditating, otherwise 0: ramp-up, 1-(n-1): intermediate period, n: last period, n+1: beyond end
     */
    @Override
    public void reschedule(long nextTargetTimeMillis, Integer nextMeditationPeriod) {
        Intent nextIntent = createSchedulerIntent(true, nextTargetTimeMillis, nextMeditationPeriod);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManagerCompat alarmManager = new AlarmManagerCompat(context);
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTargetTimeMillis, sender);
        TimeOfDay nextBellTime = new TimeOfDay(nextTargetTimeMillis);
        Log.d(TAG, "Scheduled next bell alarm for " + nextBellTime.getLogString());
    }

    /**
     * Send an intent to MindBellMain to finally stop meditation (change status, stop countdown) automatically instead of
     * pressing the stop meditation button manually.
     */
    @Override
    public void stopMeditation() {
        Log.d(TAG, "Starting activity MindBellMain to stop meditation");
        Intent intent = new Intent(context, MindBellMain.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK // context may be service context only, not an activity context
                | Intent.FLAG_ACTIVITY_CLEAR_TASK); // MindBellMain becomes the new root to let back button return to other apps
        intent.putExtra(PrefsAccessor.EXTRA_STOP_MEDITATION, true);
        context.startActivity(intent);
    }

    /**
     * Shows bell by starting activity MindBell
     */
    @Override
    public void showBell() {
        Log.d(TAG, "Starting activity MindBell to show bell");
        Intent intent = new Intent(context, MindBell.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK // context may be service context only, not an activity context
                | Intent.FLAG_ACTIVITY_CLEAR_TASK); // MindBell becomes the new root to let back button return to other apps
        context.startActivity(intent);
    }

    /**
     * This is about updating the status notification on changes in system settings.
     */
    @Override
    public void updateStatusNotification() {
        if ((!prefs.isActive() && !prefs.isMeditating()) || !prefs.isStatus()) {// bell inactive or no notification wanted?
            Log.i(TAG, "Remove status notification because of inactive and non-meditating bell or unwanted status notification");
            removeStatusNotification();
            return;
        }
        // Choose material design or pre material design status icons
        int bellActiveDrawable;
        int bellActiveButMutedDrawable;
        if (prefs.useStatusIconMaterialDesign()) {
            bellActiveDrawable = R.drawable.ic_stat_bell_active;
            bellActiveButMutedDrawable = R.drawable.ic_stat_bell_active_but_muted;
        } else {
            bellActiveDrawable = R.drawable.golden_bell_status_active;
            bellActiveButMutedDrawable = R.drawable.golden_bell_status_active_but_muted;
        }
        // Suppose bell is active and not muted and all settings can be satisfied
        int statusDrawable = bellActiveDrawable;
        CharSequence contentTitle = context.getText(R.string.statusTitleBellActive);
        String contentText;
        String muteRequestReason = getMuteRequestReason(false);
        Class<?> targetClass = MindBellMain.class;
        // Override icon and notification text if bell is muted or permissions are insufficient
        if (!canSettingsBeSatisfied(prefs)) { // Insufficient permissions => override icon/text, switch notifications off
            statusDrawable = R.drawable.ic_warning_white_24dp;
            contentTitle = context.getText(R.string.statusTitleNotificationsDisabled);
            contentText = context.getText(R.string.statusTextNotificationsDisabled).toString();
            targetClass = MindBellPreferences.class;
            // Status Notification would not be correct during incoming or outgoing calls because of the missing permission to
            // listen to phone state changes. Therefore we switch off notification and ask user for permission when he tries
            // to enable notification again. In this very moment we cannot ask for permission to avoid an ANR in receiver
            // UpdateStatusNotification.
            prefs.setStatus(false);
        } else if (prefs.isMeditating()) {// Bell meditation => override icon and notification text
            statusDrawable = R.drawable.ic_stat_bell_meditating;
            contentTitle = context.getText(R.string.statusTitleBellMeditating);
            contentText = MessageFormat.format(context.getText(R.string.statusTextBellMeditating).toString(), //
                    prefs.getMeditationDuration().getInterval(), //
                    new TimeOfDay(prefs.getMeditationEndingTimeMillis()).getDisplayString(context));
        } else if (muteRequestReason != null) { // Bell muted => override icon and notification text
            statusDrawable = bellActiveButMutedDrawable;
            contentText = muteRequestReason;
        } else { // enrich standard notification by times and days
            contentText = MessageFormat.format(context.getText(R.string.statusTextBellActive).toString(), //
                    prefs.getDaytimeStart().getDisplayString(context), //
                    prefs.getDaytimeEnd().getDisplayString(context), //
                    prefs.getActiveOnDaysOfWeekString());
        }
        // Now do the notification update
        Log.i(TAG, "Update status notification: " + contentText);
        PendingIntent openAppIntent =
                PendingIntent.getActivity(context, 0, new Intent(context, targetClass), PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent muteIntent =
                PendingIntent.getActivity(context, 2, new Intent(context, MuteActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        int visibility =
                (prefs.isStatusVisibilityPublic()) ? NotificationCompat.VISIBILITY_PUBLIC : NotificationCompat.VISIBILITY_PRIVATE;
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context.getApplicationContext()) //
                .setCategory(NotificationCompat.CATEGORY_STATUS) //
                .setColor(context.getResources().getColor(R.color.backgroundColor)) //
                .setContentTitle(contentTitle) //
                .setContentText(contentText) //
                .setContentIntent(openAppIntent) //
                .setOngoing(true) // ongoing is *not* shown on wearable
                .setSmallIcon(statusDrawable) //
                .setVisibility(visibility);
        if (!prefs.isMeditating()) {
            // Do not allow other actions than stopping meditation while meditating
            notificationBuilder //
                    .addAction(R.drawable.ic_action_refresh_status, context.getText(R.string.statusActionRefreshStatus),
                            createRefreshBroadcastIntent()) //
                    .addAction(R.drawable.ic_stat_bell_active_but_muted, context.getText(R.string.statusActionMuteFor), muteIntent);
        }
        Notification notification = notificationBuilder.build();
        NotificationManagerCompat.from(context).notify(STATUS_NOTIFICATION_ID, notification);
    }

    private void removeStatusNotification() {
        NotificationManagerCompat.from(context).cancel(STATUS_NOTIFICATION_ID);
    }

    /**
     * Returns true if mute bell with phone isn't requested or if the app has the permission to be informed in case of incoming or
     * outgoing calls. Notification bell could not be turned over correctly if muting with phone were requested without permission
     * granted.
     */
    private boolean canSettingsBeSatisfied(PrefsAccessor prefs) {
        boolean result = !prefs.isMuteOffHook() ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
                        PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "Can settings be satisfied? -> " + result);
        return result;
    }

    /**
     * Create an intent to be send to UpdateStatusNotification to update notification.
     */
    @Override
    public PendingIntent createRefreshBroadcastIntent() {
        return PendingIntent.getBroadcast(context, 1, new Intent("com.googlecode.mindbell.UPDATE_STATUS_NOTIFICATION"),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        MindBell.logDebug("Callback onAudioFocusChange() received focusChange=" + focusChange);
        switch (focusChange) {
            case AUDIOFOCUS_LOSS:
            case AUDIOFOCUS_LOSS_TRANSIENT: // could be handled by only pausing playback (not useful for bell sound)
            case AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: // could also be handled by lowering volume (not useful for bell sound)
                finishBellSound();
                break;
            default:
                break;
        }
    }
}
