package com.ustadmobile.adbscreenrecorder.httpserver
import fi.iki.elonen.NanoHTTPD
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import java.io.File
import java.io.FileFilter
import java.io.FileWriter
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.text.DateFormat
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern


class AdbScreenRecorderHttpServer(hostName: String?, port: Int, val adbPath: String, val destDir: File)  : NanoHTTPD(hostName, port) {

    val recordingManager = RecordingManager(adbPath, destDir)

    val dateFormatter = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG)

    val allKnownDevices = mutableSetOf<String>()

    override fun serve(session: IHTTPSession): Response {
        if(session.uri.startsWith("/startRecording") || session.uri.startsWith("/endRecording")) {
            val openPort = session.parameters.get("openPort")?.get(0)?.toInt() ?: -1
            val testClazz = session.parameters.get("testClazz")?.get(0)
            val testMethod = session.parameters.get("testMethod")?.get(0)

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

            allKnownDevices += deviceName

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

        fun generateReport(baseDir: File, deviceNames: List<String>) {
            val fileWriter = FileWriter(File(baseDir, "index.html"))

            fileWriter.appendHTML().html {
                body {
                    table {
                        tr {
                            td {
                                + " - "
                            }

                            deviceNames.forEach {deviceName ->
                                td {
                                    + deviceName
                                }
                            }
                        }


                        baseDir.listFiles(FileFilter { it.isDirectory }).forEach {testClazzDir ->
                            tr {
                                th {
                                    colSpan = (deviceNames.size + 1).toString()
                                    + "${testClazzDir.name}"
                                }
                            }

                            testClazzDir.listFiles(FileFilter { it.isDirectory }).forEach { testMethodDir ->
                                tr {
                                    td {
                                        + testMethodDir.name
                                    }

                                    deviceNames.forEach { deviceName ->
                                        td {
                                            if(File(testMethodDir, "$deviceName.mp4").exists()) {
                                                video {
                                                    src = "${testClazzDir.name}/${testMethodDir.name}/$deviceName.mp4"
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

    }
}