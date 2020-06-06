
### Adb Screen Recorder for UI tests: seeing is believing

How many times have users and clients said "why haven't you tested this?". Test reports and coverage
percentages don't mean much to project managers, users, or people who haven't spent hours writing
Espresso tests. ADB Screen Recorder makes it easy to record an individual video file for each
Espresso test you run on each device.

###Getting started

Add the plugin:
```
plugins {
    id "com.ustadmobile.adbscreenrecorder" version "0.1"
}

...

adbScreenRecord {
   port = 8089
   adbPath = android.adbPath
}
```

Add the test rule:
```
@RunWith(AndroidJUnit4::class)
class MyTest {

    @JvmField
    @Rule
    val screenRecordRule = AdbScreenRecordRule()

    @Test
    fun givenSomeSituation_whenSomethingHappens_thenTheSunWillRaise() {

    }

}
```

And that's it! Now just open up build/reports/adbScreenRecord and you'll find a report page
showing videos of every test.

