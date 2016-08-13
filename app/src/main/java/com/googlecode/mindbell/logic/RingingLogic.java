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
package com.googlecode.mindbell.logic;

import com.googlecode.mindbell.accessors.ContextAccessor;

/**
 * This class decides wether to ring the bell and in case kicks off the bell ring.
 */
public class RingingLogic {

    /** Time to wait for bell sound to finish or for displayed bell to be send back */
    public static final long WAITING_TIME = 15000L;

    /**
     * Instance to start the bell ring and wait for the end of ringing.
     */
    public static class KeepAlive {

        private boolean isDone = false;

        private final long time;

        private final long sleepDuration;

        private final ContextAccessor ca;

        public KeepAlive(ContextAccessor ca, long timeout) {
            this.ca = ca;
            this.time = timeout;
            this.sleepDuration = timeout / 10;
        }

        public void ringBell() {
            RingingLogic.ringBell(ca, new Runnable() {
                public void run() {
                    setDone();
                }
            });
            long totalSlept = 0;
            while (!isDone && totalSlept < time) {
                try {
                    Thread.sleep(sleepDuration);
                } catch (InterruptedException ie) {
                }
                totalSlept += sleepDuration;
            }
        }

        private void setDone() {
            isDone = true;
        }

    }

    /**
     * Start playing bell sound and vibration if requested.
     *
     * @param contextAccessor
     *            the ContextAccessor to be used to ring the bell.
     * @param runWhenDone
     *            an optional Runnable to call on completion of all ringing activities, or null.
     * @return true if bell started ringing, false otherwise
     */
    public static boolean ringBell(ContextAccessor contextAccessor, final Runnable runWhenDone) {

        // Verify if we should be muted
        if (contextAccessor.isMuteRequested(true)) {
            if (runWhenDone != null) {
                runWhenDone.run();
            }
            return false;
        }
        // Stop any ongoing ring, and manually reset volume to original.
        if (contextAccessor.isBellSoundPlaying()) { // probably false, as context is (probably) different from that for startPlayingSoundAndVibrate()
            contextAccessor.finishBellSound();
        }

        // Kick off the playback of the bell sound, with an automatic volume reset built-in if not stopped.
        contextAccessor.startPlayingSoundAndVibrate(runWhenDone);

        return true;
    }

    /**
     * Ring the bell if requested and wait till it's done or time reached. Ringing the bell means executing all requested activities,
     * such as showing the bell, playing a sound and vibrating.
     *
     * @param contextAccessor
     *            the ContextAccessor to be used to ring the bell.
     */
    public static void ringBellAndWait(ContextAccessor contextAccessor) {
        new KeepAlive(contextAccessor, WAITING_TIME).ringBell();
    }

}
