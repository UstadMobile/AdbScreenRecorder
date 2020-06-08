package com.ustadmobile.adbscreenrecorder

import android.widget.TextView
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ustadmobile.adbscreenrecorder.client.AdbScreenRecord
import com.ustadmobile.adbscreenrecorder.client.AdbScreenRecordRule

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Rule

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
@AdbScreenRecord("Example Test")
class ExampleInstrumentedTest {

    @JvmField
    @Rule
    var adbScreenRecordRule = AdbScreenRecordRule()

    @AdbScreenRecord("Run a 15 second counter")
    @Test
    fun runUiTest() {
        val scenario = launchActivity<MainActivity>()
        for(i in 1 .. 15){
            scenario.onActivity {
                it.findViewById<TextView>(R.id.activity_main_text).text = "$i"
            }
            Thread.sleep(1000)
        }
        onView(withId(R.id.activity_main_text)).check(matches(isDisplayed()))
    }

}