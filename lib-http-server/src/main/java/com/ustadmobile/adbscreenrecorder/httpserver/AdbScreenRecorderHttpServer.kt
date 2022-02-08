package com.ustadmobile.adbscreenrecorder.httpserver
import com.google.gson.Gson
import com.ustadmobile.adbscreenrecorder.DEFAULT_DEVICE_PORT
import com.ustadmobile.adbscreenrecorder.DeviceInfo
import com.ustadmobile.adbscreenrecorder.TestInfo
import fi.iki.elonen.NanoHTTPD
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import java.io.*
import java.lang.IllegalStateException
import java.text.DateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern


class AdbScreenRecorderHttpServer(
    val deviceName: String,
    private val adbPath: String,
    destDir: File,
    private val devicePort: Int = DEFAULT_DEVICE_PORT,
    hostName: String? = null,
    port: Int = 0,
    private val logLevel: AdbRecorderLogLevel = AdbRecorderLogLevel.NORMAL
)  : NanoHTTPD(hostName, port) {

    enum class AdbRecorderLogLevel {
        NORMAL, INFO, DEBUG
    }

    private val recordingManager = RecordingManager(adbPath, destDir)

    private val dateFormatter = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG)

    var deviceInfo = DeviceInfo()

    //Map key = "testClazz/testMethod"
    val testResultsMap = mutableMapOf<String, TestInfo>()

    val gson = Gson()

    fun startPortForwarding() {
        if(listeningPort <= 0)
            throw IllegalStateException("AdbScreenRecorder cannot start port forwarding before httpd started")

        ProcessBuilder(listOf(adbPath, "-s", deviceName, "reverse",
            "tcp:$devicePort", "tcp:$listeningPort"))
            .start()
            .waitFor(5, TimeUnit.SECONDS)

        if(logLevel == AdbRecorderLogLevel.DEBUG)
            println("AdbScreenRecorderHttp forwarding $deviceName device port $devicePort to " +
                    "local port $listeningPort")
    }

    fun stopPortForwarding() {
        ProcessBuilder(listOf(adbPath, "-s", deviceName, "reverse",
            "--remove", "tcp:$devicePort"))
            .start()
            .waitFor(5, TimeUnit.SECONDS)
    }

    override fun serve(session: IHTTPSession): Response {
        if(session.uri.startsWith("/startRecording") || session.uri.startsWith("/endRecording")) {
            val testClazz = session.parameters.get("testClazz")?.get(0)
            val testMethod = session.parameters.get("testMethod")?.get(0)
            val deviceInfoJson = session.parameters.get("deviceInfo")?.get(0)
            val testInfoJson = session.parameters.get("testInfo")?.get(0)

            if(testClazz == null || testMethod == null) {
                println("AdbScreenRecorderHttpServer: GET ${session.uri} (400) - missing testClazz or testMethod")
                return newFixedLengthResponse(Response.Status.BAD_REQUEST,
                    "text/plain", "missing testClazz or testMethod")
            }

            if(deviceInfoJson != null) {
                val reqDeviceInfo = gson.fromJson(deviceInfoJson, DeviceInfo::class.java).also {
                    it.deviceName = deviceName
                }

                //if this hasn't been set to the actual info yet, then set it
                if(deviceInfo.sdkInt == -1) {
                    deviceInfo = reqDeviceInfo
                }
            }

            if(testInfoJson != null) {
                testResultsMap["$testClazz/$testMethod"] = gson.fromJson(testInfoJson, TestInfo::class.java)
            }

            if(session.uri.startsWith("/startRecording")) {
                if(logLevel == AdbRecorderLogLevel.INFO || logLevel == AdbRecorderLogLevel.DEBUG)
                    println("(${dateFormatter.format(Date())}) ADBScreenRecord start recording " +
                            "request for $deviceName $testClazz.$testMethod ")

                try {
                    recordingManager.startRecording(deviceName, testClazz, testMethod)
                    return newFixedLengthResponse("ADB recording started OK")
                }catch(e: Exception) {
                    println("ADBScreenRecord: ERROR: $e")
                    e.printStackTrace()
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain",
                        e.toString())
                }

            }else {
                if(logLevel == AdbRecorderLogLevel.INFO || logLevel == AdbRecorderLogLevel.DEBUG)
                    println("(${dateFormatter.format(Date())}) ADBScreenRecord stop recording " +
                            "request ${session.uri}")
                try {
                    val destFile = recordingManager.stopRecording(deviceName, testClazz, testMethod)
                    return newFixedLengthResponse("ADB recording stopped and saved to ${destFile.absolutePath}")
                }catch(e: Exception) {
                    println("ERR: ADBScreenRecord stop recording request ${session.uri}")
                    e.printStackTrace()
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain",
                        e.toString())
                }
            }
        }

        return newFixedLengthResponse(Response.Status.OK,  "text/plain", WELCOME_MESSAGE)
    }

    companion object {
        val WELCOME_MESSAGE = """"ADB Screen Recording HTTP Server
            |Available endpoints:
            | * /startRecording?openPort=123&testClazz=ClazzName&testMethod=methodName
            | * /stopRecording?openPort=123&testClazz=ClazzName&testMethod=methodName
            | 
            | Parameters
            | * openPort a random HTTP port that is opened on the client device that will be used to identify the running device
            | * testClazz Class name of the test that is running
            | * testMethod Method name of the test that is running
        """.trimMargin()

        internal fun runProcess(cmd: List<String>, timeout: Long = 10): InputStream {
            val proc = ProcessBuilder(cmd)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start().also {
                    it.waitFor(timeout, TimeUnit.SECONDS)
                }

            return proc.inputStream
        }

        fun listAndroidDevices(adbPath: String): List<String> {
            val proc = ProcessBuilder(listOf(adbPath, "devices"))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()
            proc.waitFor(20, TimeUnit.SECONDS)
            val devices = proc.inputStream.bufferedReader().readText().lines().filterIndexed { index, line ->
                index != 0 && line.isNotBlank()
            }.map { it.split(Pattern.compile("\\s+")).first()}

            return devices
        }

        fun getAndroidSdkVersion(adbPath: String, deviceName: String): Int {
            return runProcess(listOf(adbPath, "-s", deviceName, "shell", "getprop", "ro.build.version.sdk"), 10)
                .bufferedReader().readText().trim().toIntOrNull() ?: -1
        }

        fun getWindowIdForDevice(wmCtrlPath: String, deviceName: String): String? {
            val devicePort = deviceName.substringAfter("-").toIntOrNull() ?: return null
            val windowList = runProcess(listOf(wmCtrlPath, "-l")).bufferedReader().readText().lines()
            return windowList.firstOrNull { it.endsWith(":$devicePort") }?.split("\\s".toRegex(), 2)?.get(0)
        }

        private fun File.findVideoInDir(deviceName: String): File? {
            for(f in listOf(File(this, "$deviceName.mp4"), File(this, "$deviceName.ogv"))) {
                if(f.exists())
                    return f
            }

            return null
        }

        fun generateReport(projectName: String, destDir: File, devices: Map<String, DeviceInfo>, testResults: Map<String, Map<String, TestInfo>>) {
            destDir.mkdirs() //this should have already been created, but would be empty if no tests have been recorded
            FileOutputStream(File(destDir, "adbscreenrecord.css")).use {fileOut ->
                AdbScreenRecorderHttpServer::class.java.getResourceAsStream("/adbscreenrecord.css")?.use { resourceIn ->
                    resourceIn.copyTo(fileOut)
                    fileOut.flush()
                }
            }


            val fileWriter = FileWriter(File(destDir, "index.html"))

            fileWriter.appendHTML().html {
                head {
                    link(href="adbscreenrecord.css", rel="stylesheet", type="text/css")
                }

                body {
                    h2 {
                        + "ADB Screen Recorder Report"
                    }
                    div(classes = "subtitle") {
                        span(classes = "projectname") {
                            + projectName
                        }
                        span(classes = "timestamp") {
                            + DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG).format(Date())
                        }
                    }

                    table {
                        tr {
                            td {
                                + " Device "
                            }

                            devices.forEach {deviceName ->
                                td(classes="devicetd") {
                                    + "${deviceName.value.manufacturer}  - ${deviceName.value.modelName}"
                                    br {}
                                    + "Android Version ${deviceName.value.androidRelease} (SDK ${deviceName.value.sdkInt})"
                                }
                            }
                        }



                        destDir.listFiles(FileFilter { it.isDirectory })?.forEach {testClazzDir ->
                            val clazzName = testClazzDir.name

                            val clazzNameEntry = testResults.values.flatMap { it.entries }
                                .firstOrNull { it.key.startsWith("$clazzName/") }

                            tr {
                                th(classes= "classth" ) {
                                    colSpan = (devices.size + 1).toString()
                                    val clazzDesc = clazzNameEntry?.value?.clazzDesc
                                    if(clazzDesc != null) {
                                        span(classes = "classdesc") {
                                            + clazzDesc
                                        }
                                        br {  }
                                    }
                                    span(classes = "classname") {
                                        + clazzName
                                    }
                                }
                            }

                            testClazzDir.listFiles(FileFilter { it.isDirectory })?.forEach { testMethodDir ->
                                tr {
                                    val methodName = testMethodDir.name
                                    td(classes = "methodtd") {
                                        //val methodNameEntry = testResultsMap.entries.firstOrNull { it.key.contains("/$clazzName/$methodName") }
                                        val methodNameEntry = testResults.values.flatMap { it.entries }
                                            .firstOrNull { it.key == "$clazzName/$methodName" }
                                        val methodDesc = methodNameEntry?.value?.methodDesc
                                        if(methodDesc != null) {
                                            span(classes="methoddesc") {
                                                + methodDesc
                                            }
                                            br { }
                                        }
                                        span(classes="methodname") {
                                            + methodName
                                        }
                                    }

                                    devices.values.forEach { deviceInfo ->
                                        val testInfo = testResults[deviceInfo.deviceName]?.get("$clazzName/$methodName")
                                        val tdCssClass = if(testInfo != null) {
                                            MAP_STATUS_TO_CSS_CLASS[testInfo.status]
                                        }else {
                                            ""
                                        }

                                        td(classes = "testvideo $tdCssClass") {
                                            //val videoFile = File(testMethodDir, "${deviceInfo.deviceName}.mp4")
                                            val videoFile = testMethodDir.findVideoInDir(deviceInfo.deviceName)
                                            if(videoFile != null) {
                                                video {
                                                    src = "${testClazzDir.name}/${testMethodDir.name}/${videoFile.name}"
                                                    controls = true
                                                    attributes["preload"] = "none"

                                                    val coverImageFile = File(testMethodDir, "${deviceInfo.deviceName}.jpg")
                                                    if(coverImageFile.exists()) {
                                                        poster = "${testClazzDir.name}/${testMethodDir.name}/${coverImageFile.name}"
                                                    }
                                                }
                                            }else {
                                                + "[No video]"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            fileWriter.flush()
            fileWriter.close()
            println("Adb Screen Recorder report saved to: ${destDir.absolutePath}/index.html")
        }


        val MAP_STATUS_TO_CSS_CLASS = mapOf(TestInfo.STATUS_FAIL to "fail",
            TestInfo.STATUS_NOT_RUN to "notrun",
            TestInfo.STATUS_PASS to "pass",
            TestInfo.STATUS_SKIPPED to "skipped")

    }
}