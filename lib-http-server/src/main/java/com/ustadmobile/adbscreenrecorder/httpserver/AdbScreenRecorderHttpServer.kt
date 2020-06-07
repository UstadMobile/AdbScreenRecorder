package com.ustadmobile.adbscreenrecorder.httpserver
import com.google.gson.Gson
import com.ustadmobile.adbscreenrecorder.DeviceInfo
import com.ustadmobile.adbscreenrecorder.TestInfo
import fi.iki.elonen.NanoHTTPD
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import java.io.File
import java.io.FileFilter
import java.io.FileOutputStream
import java.io.FileWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.DateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern


class AdbScreenRecorderHttpServer(hostName: String?, port: Int, val adbPath: String, val destDir: File)  : NanoHTTPD(hostName, port) {

    val recordingManager = RecordingManager(adbPath, destDir)

    val dateFormatter = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG)

    val allKnownDevices = mutableMapOf<String, DeviceInfo>()

    //Map key = "devicename/testClazz/testMethod"
    val testResultsMap = mutableMapOf<String, TestInfo>()

    val gson = Gson()

    override fun serve(session: IHTTPSession): Response {
        if(session.uri.startsWith("/startRecording") || session.uri.startsWith("/endRecording")) {
            val openPort = session.parameters.get("openPort")?.get(0)?.toInt() ?: -1
            val testClazz = session.parameters.get("testClazz")?.get(0)
            val testMethod = session.parameters.get("testMethod")?.get(0)
            val deviceInfoJson = session.parameters.get("deviceInfo")?.get(0)
            val testInfoJson = session.parameters.get("testInfo")?.get(0)

            if(testClazz == null || testMethod == null || openPort == -1) {
                println("AdbScreenRecorderHttpServer: GET ${session.uri} (400) - missing testClazz or testMethod")
                return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST,
                    "text/plain", "missing testClazz or testMethod")
            }

            val connectedDevices = listAndroidDevices(adbPath)
            val deviceName = identifyByOpenPort(adbPath, connectedDevices, openPort)

            if(deviceName == null) {
                println("(${dateFormatter.format(Date())}) AdbScreenRecorderHttpServer: GET ${session.uri} (410) Cannot identify android device on $openPort")
                return newFixedLengthResponse(
                    Response.Status.GONE, "text/plain",
                    "Adb does not find any device with http server on port: $openPort. Connected devices=${connectedDevices.joinToString()}"
                )
            }


            if(deviceInfoJson != null) {
                val deviceInfo = gson.fromJson(deviceInfoJson, DeviceInfo::class.java)
                if(!allKnownDevices.containsKey(deviceName) || allKnownDevices[deviceName]?.sdkInt == -1) {
                    deviceInfo.deviceName = deviceName
                    allKnownDevices[deviceName] = deviceInfo
                }
            }else if(!allKnownDevices.containsKey(deviceName)) {
                allKnownDevices[deviceName] = DeviceInfo(deviceName = deviceName)//put it in the list - but it is unknown
            }

            if(testInfoJson != null) {
                println("Received test info JSON: $testInfoJson")
                testResultsMap["$deviceName/$testClazz/$testMethod"] = gson.fromJson(testInfoJson, TestInfo::class.java)
            }

            if(session.uri.startsWith("/startRecording")) {
                println("(${dateFormatter.format(Date())}) ADBScreenRecord start recording request for $deviceName $testClazz.$testMethod ")
                try {
                    recordingManager.startRecording(deviceName, testClazz, testMethod)
                    println("ADB started recording for $deviceName - $testClazz.$testMethod")
                    return newFixedLengthResponse("ADB recording started OK")
                }catch(e: Exception) {
                    println("ADBScreenRecord: ERROR: $e")
                    e.printStackTrace()
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain",
                        e.toString())
                }

            }else {
                println("(${dateFormatter.format(Date())}) ADBScreenRecord stop recording request ${session.uri}")
                try {
                    val destFile = recordingManager.stopRecording(deviceName, testClazz, testMethod)
                    return newFixedLengthResponse("ADB recording stopped and saved to ${destFile.absolutePath}")
                }catch(e: Exception) {
                    println("ADBScreenRecord stop recording request ${session.uri}")
                    e.printStackTrace()
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain",
                        e.toString())
                }
            }
        }

        return newFixedLengthResponse(Response.Status.OK,  "text/plain", WELCOME_MESSAGE)
    }

    fun generateReport(projectName: String) {
        println("Generating report. Have test info for ${testResultsMap.keys.joinToString()} ")
        FileOutputStream(File(destDir, "adbscreenrecord.css")).use {fileOut ->
            javaClass.getResourceAsStream("/adbscreenrecord.css").use { resourceIn ->
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

                        allKnownDevices.forEach {deviceName ->
                            td(classes="devicetd") {
                                + "${deviceName.value.manufacturer}  - ${deviceName.value.modelName}"
                                br {}
                                + "Android Version ${deviceName.value.androidRelease} (SDK ${deviceName.value.sdkInt})"
                            }
                        }
                    }



                    destDir.listFiles(FileFilter { it.isDirectory }).forEach {testClazzDir ->
                        val clazzName = testClazzDir.name
                        val clazzNameEntry = testResultsMap.entries.firstOrNull {
                            it.key.contains("/$clazzName/") && it.value.clazzDesc != null }
                        tr {
                            th(classes= "classth" ) {
                                colSpan = (allKnownDevices.size + 1).toString()
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

                        testClazzDir.listFiles(FileFilter { it.isDirectory }).forEach { testMethodDir ->
                            tr {
                                val methodName = testMethodDir.name
                                td(classes = "methodtd") {
                                    val methodNameEntry = testResultsMap.entries.firstOrNull { it.key.contains("/$clazzName/$methodName") }
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

                                allKnownDevices.values.forEach { deviceInfo ->
                                    val testInfo = testResultsMap["${deviceInfo.deviceName}/${testClazzDir.name}/${testMethodDir.name}"]
                                    val tdCssClass = if(testInfo != null) {
                                        MAP_STATUS_TO_CSS_CLASS[testInfo.status]
                                    }else {
                                        ""
                                    }

                                    td(classes = "testvideo $tdCssClass") {
                                        if(File(testMethodDir, "${deviceInfo.deviceName}.mp4").exists()) {
                                            video {
                                                src = "${testClazzDir.name}/${testMethodDir.name}/${deviceInfo.deviceName}.mp4"
                                                controls = true
                                            }
                                        }else {
                                            + "No video"
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


        fun identifyByOpenPort(adbPath: String, deviceList: List<String>, openPort: Int) : String? {
            return deviceList.firstOrNull {deviceName ->
                ProcessBuilder(listOf(adbPath, "-s", deviceName, "forward",
                    "tcp:$openPort", "tcp:$openPort"))
                    .start()
                    .waitFor(5, TimeUnit.SECONDS)

                //now try and connect
                var connectedOk = false

                var input: HttpURLConnection? = null

                try {
                    val url = URL("http://localhost:$openPort/")
                    input = url.openConnection() as HttpURLConnection
                    connectedOk = input.responseCode == 200
                }catch(e: Exception) {
                    //no connection
                }finally {
                    input?.disconnect()
                }

                ProcessBuilder(listOf(adbPath, "-s", deviceName, "forward",
                    "--remove", "tcp:$openPort"))
                    .start()
                    .waitFor(5, TimeUnit.SECONDS)

                connectedOk
            }
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


        val MAP_STATUS_TO_CSS_CLASS = mapOf(TestInfo.STATUS_FAIL to "fail",
            TestInfo.STATUS_NOT_RUN to "notrun",
            TestInfo.STATUS_PASS to "pass",
            TestInfo.STATUS_SKIPPED to "skipped")

    }
}