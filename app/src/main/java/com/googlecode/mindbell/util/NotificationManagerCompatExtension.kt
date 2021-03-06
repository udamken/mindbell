/*
 * MindBell - Aims to give you a support for staying mindful in a busy life -
 *            for remembering what really counts
 *
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

package com.googlecode.mindbell.util

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Hide Android API level differences from application code.
 *
 * However, with Kotlin I found no way to extend existing NotificationManagerCompat. (1) It cannot be inherited from as it is
 * final. (2) Kotlin's delegation feature is not usable because NotificationManagerCompat is no interface. (3) Classic delegation
 * would have required to *type* all delegate method because AndroidStudio is not able to generate them - probably because of (2)
 * or because one would not do that ;-).
 */
class NotificationManagerCompatExtension private constructor(val context: Context) {

    /**
     * Creates a notification channel with API level 26 or higher, does nothing otherwise.
     *
     * Following https://developer.android.com/training/notify-user/channels.html notification channel may IMHO be created
     * everytime when notifying: >> Creating an existing notification channel with its original values performs no operation, so
     * it's safe to call this code when starting an app. <<
     */
    @SuppressLint("InlinedApi") // IMPORTANCE_DEFAULT is ignored in method body for API level < 26
    fun createNotificationChannel(id: String, name: String, description: String, importance: Int = NotificationManager
            .IMPORTANCE_DEFAULT, lights: Boolean = false, lightColor: Int? = null, vibration: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 26) {
            val mChannel = NotificationChannel(id, name, importance)
            mChannel.description = description
            mChannel.enableLights(lights)
            if (lightColor != null) {
                mChannel.lightColor = lightColor
            }
            mChannel.enableVibration(vibration)
            val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mNotificationManager.createNotificationChannel(mChannel)
        }
    }

    /**
     * Returns the current interruption filter (do-not-disturb mode) for API level 23 or higher, otherwise
     * NotificationManager.INTERRUPTION_FILTER_NONE.
     */
    @SuppressLint("InlinedApi")
    fun isPhoneInDoNotDisturbMode(): Boolean {
        return if (Build.VERSION.SDK_INT >= 23) {
            val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val currentInterruptionFilter = mNotificationManager.currentInterruptionFilter
            currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        } else {
            false
        }
    }

    companion object {

        fun getInstance(context: Context): NotificationManagerCompatExtension {
            return NotificationManagerCompatExtension(context.applicationContext)
        }

    }

}