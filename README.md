
### Adb Screen Recorder for UI tests: seeing is believing

How many times have users and clients said "why haven't you tested this?". Test reports and coverage
percentages don't mean much to project managers, users, or people who haven't spent hours writing
Espresso tests. ADB Screen Recorder makes it easy to record an individual video file for each
Espresso test you run on each device.

###Getting started

Add the plugin to your project:
```
//Apply the plugin
plugins {
    id "com.ustadmobile.adbscreenrecorder" version "0.1"
}

...

//Add the dependency for the test rule
dependencies {
    androidTestImplementation "com.ustadmobile.adbscreenrecorder:lib-client:0.1"
}

//Optionally configure the output directory
adbScreenRecord {
   reportDir = "$buildDir/reports/adbScreenRecord"
}
```

Add the test rule to your UI tests:
```

//Annotate the test using @AdbScreenRecord to set a user-friendly description to appear in the test.
// If you don't use the annotation, then the class and method name will be used.
@AdbScreenRecord("Requirement 1: Screen Name")
@RunWith(AndroidJUnit4::class)
class ScreenNameFragmentTest {

    @JvmField
    @Rule
    val screenRecordRule = AdbScreenRecordRule()

    @AdbScreenRecord("Requirement 1a: Screen Name will make sun raise in some situation when something happens")
    @Test
    fun givenSomeSituation_whenSomethingHappens_thenTheSunWillRaise() {

    }

}
```

Now run the connectedCheck task as normal:

```
$ ./gradlew app:connectedCheck
```

And that's it! Now just open up build/reports/adbScreenRecord and you'll find a report page
showing videos of every test.

### How it works

This plugin uses the adb command to record videos of each test running. The plugin will create a
mini http server for each connected device, and setup adb reverse port forwarding so that the
test rule on the client can connect to it and send requests to start/stop recording as tests
start and finish. The port forwarding operates independently of the connectivity of the device
and works as long as adb is connected.

