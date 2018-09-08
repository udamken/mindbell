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
package com.googlecode.mindbell.activity

import android.content.res.Resources
import android.support.test.InstrumentationRegistry
import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.action.ViewActions.*
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.contrib.PickerActions
import android.support.test.espresso.matcher.ViewMatchers.*
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import android.widget.EditText
import android.widget.TimePicker
import com.googlecode.mindbell.R
import com.googlecode.mindbell.R.id.*
import com.googlecode.mindbell.R.string.*
import com.googlecode.mindbell.mission.Prefs
import com.googlecode.mindbell.mission.Prefs.Companion.ONE_MINUTE_MILLIS
import org.hamcrest.Matchers
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    var activityTestRule = ActivityTestRule(MainActivity::class.java, false, false) // don't launch

    private lateinit var prefs: Prefs

    private lateinit var resources: Resources

    @Before
    fun setUp() {
        prefs = Prefs.getInstance(InstrumentationRegistry.getTargetContext())
        prefs.resetSettings()
        resources = InstrumentationRegistry.getTargetContext().resources
        activityTestRule.launchActivity(null) // launch activity now to get reset preferences
    }

    @Test
    fun mainActivityTest() {

        // Close help dialog at startup
        onView(allOf(withId(android.R.id.button1), withText(android.R.string.ok))).perform(scrollTo(), click())

        // Open meditation dialog
        onView(allOf(withId(meditating), withContentDescription(prefsMeditatingOn))).perform(click())
        onView(withText(title_meditation_dialog)).check(matches(isDisplayed()))

        // Change ramp up time
        onView(withId(textViewRampUpTime)).perform(scrollTo(), click())
        onView(withText(prefsRampUpTime)).check(matches(isDisplayed()))
        onView(withClassName(Matchers.equalTo(TimePicker::class.java.name))).perform(PickerActions.setTime(0, 10))
        onView(allOf(withId(android.R.id.button1), withText(android.R.string.ok))).perform(scrollTo(), click())

        // Change meditation duration
        onView(withId(textViewMeditationDuration)).perform(scrollTo(), click())
        onView(withText(prefsMeditationDuration)).check(matches(isDisplayed()))
        onView(withClassName(Matchers.equalTo(TimePicker::class.java.name))).perform(PickerActions.setTime(0, 4))
        onView(allOf(withId(android.R.id.button1), withText(android.R.string.ok))).perform(scrollTo(), click())

        // Change pattern of periods
        onView(withId(R.id.textViewPatternOfPeriodsLabel)).perform(scrollTo(), click())
        onView(withText(prefsPatternOfPeriods)).check(matches(isDisplayed()))
        onView(withClassName(Matchers.equalTo(EditText::class.java.name))).perform(replaceText("1,x,1"), closeSoftKeyboard())
        onView(allOf(withId(android.R.id.button1), withText(android.R.string.ok))).perform(scrollTo(), click())

        // Enable option to stop meditation automatically
        onView(withId(R.id.checkBoxStopMeditationAutomatically)).perform(scrollTo(), click())

        // Start meditation
        onView(withText(buttonStartMeditation)).perform(scrollTo(), click())
        onView(allOf(withId(meditating), withContentDescription(prefsMeditatingOff))).check(matches(isDisplayed()))

        // Wait for meditation to come to an end
        do {
            Thread.sleep(ONE_MINUTE_MILLIS)
        } while (prefs.isMeditating)

        onView(allOf(withId(meditating), withContentDescription(prefsMeditatingOn))).perform(click())

        // Check statistics for success


    }

}