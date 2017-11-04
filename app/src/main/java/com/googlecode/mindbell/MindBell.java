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
package com.googlecode.mindbell;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.googlecode.mindbell.accessors.ContextAccessor;

import static com.googlecode.mindbell.MindBellPreferences.TAG;

public class MindBell extends Activity {

    public static void logDebug(String message) {
        Log.d(TAG, message);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bell);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ContextAccessor contextAccessor = ContextAccessor.getInstance(this);
        contextAccessor.startPlayingSoundAndVibrate(contextAccessor.getPrefs().forRegularOperation(), new Runnable() {
            public void run() {
                Log.d(TAG, "Hiding bell");
                MindBell.this.moveTaskToBack(true);
                MindBell.this.finish();
            }
        });
    }

}