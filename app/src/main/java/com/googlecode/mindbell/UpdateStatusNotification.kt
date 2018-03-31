/*
 * MindBell - Aims to give you a support for staying mindful in a busy life -
 *            for remembering what really counts
 *
 *     Copyright (C) 2010-2014 Marc Schroeder
 *     Copyright (C) 2014-2017 Uwe Damken
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
package com.googlecode.mindbell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.googlecode.mindbell.accessors.ContextAccessor

/**
 * Update status notification on changes in system settings to display an active icon or active but muted icon or no icon at all.
 */
class UpdateStatusNotification : BroadcastReceiver() { // TODO RefreshBroadcastReceiver

    override fun onReceive(context: Context, intent: Intent) {
        MindBell.logDebug("Update status notification intent received " + intent.action!!)
        val contextAccessor = ContextAccessor.getInstance(context)
        contextAccessor.updateStatusNotification()
        contextAccessor.scheduleUpdateStatusNotificationDayNight()
    }

}
