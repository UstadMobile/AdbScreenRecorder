package com.ustadmobile.adbscreenrecorder.httpserver
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern


class AdbScreenRecorderHttpServer(hostName: String?, port: Int, var adbPath: String, val destDir: File)  : NanoHTTPD(hostName, port) {

    val recordingManager = RecordingManager(adbPath, destDir)

    override fun serve(session: IHTTPSession): Response {
        if(session.uri.startsWith("/startRecording") || session.uri.startsWith("/endRecording")) {
            val openPort = session.parameters.get("openPort")?.get(0)?.toInt() ?: -1
            val testClazz = session.parameters.get("testClazz")?.get(0)
            val testMethod = session.parameters.get("testMethod")?.get(0)

            if(testClazz == null || testMethod == null || openPort == -1) {
                return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST,
                    "text/plain", "missing testClazz or testMethod")
            }

            val connectedDevices = listAndroidDevices(adbPath)
            val deviceName = identifyByOpenPort(adbPath, connectedDevices, openPort) ?:
                return newFixedLengthResponse(Response.Status.GONE, "text/plain",
                "Adb does not find any device with http server on port: $openPort. Connected devices=${connectedDevices.joinToString()}")

            if(session.uri.startsWith("/startRecording")) {
                recordingManager.startRecording(deviceName, testClazz, testMethod)
                return newFixedLengthResponse("ADB recording started OK")
            }else {
                val destFile = recordingManager.stopRecording(deviceName, testClazz, testMethod)
                return newFixedLengthResponse("ADB recording stopped and saved to ${destFile.absolutePath}")
            }
        }

        return newFixedLengthResponse(Response.Status.OK, WELCOME_MESSAGE, "text/plain")
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

    }
}