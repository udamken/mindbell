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

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.NumberPicker
import com.googlecode.mindbell.accessors.ContextAccessor

/**
 * Activity to ask for the time period to mute the bell for.
 */
class MuteActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contextAccessor = ContextAccessor.getInstance(this)
        val numberPicker = NumberPicker(this)
        val hours = 24
        numberPicker.minValue = 0
        numberPicker.maxValue = hours
        numberPicker.displayedValues = createDisplayedHourValues(hours)
        AlertDialog.Builder(this) //
                .setTitle(R.string.statusActionMuteFor) //
                .setView(numberPicker) //
                .setPositiveButton(android.R.string.ok) { dialog, which ->
                    val newValue = numberPicker.value
                    val nextTargetTimeMillis = System.currentTimeMillis() + newValue * 3600000L
                    contextAccessor.prefs!!.mutedTill = nextTargetTimeMillis
                    contextAccessor.updateStatusNotification()
                    contextAccessor.scheduleUpdateStatusNotificationMutedTill(nextTargetTimeMillis)
                    this@MuteActivity.finish()
                } //
                .setNegativeButton(android.R.string.cancel) { dialog, which -> this@MuteActivity.finish() } //
                .show()
    }

    private fun createDisplayedHourValues(hours: Int): Array<String> {
        return Array<String>(hours + 1, { i -> i.toString() + " h" })
    }

}