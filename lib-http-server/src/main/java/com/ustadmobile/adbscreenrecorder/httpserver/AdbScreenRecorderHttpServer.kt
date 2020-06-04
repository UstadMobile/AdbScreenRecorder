package com.ustadmobile.adbscreenrecorder.httpserver
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class AdbScreenRecorderHttpServer(port: Int, val adbPath: String)  : NanoHTTPD(port) {



    override fun serve(session: IHTTPSession): Response {
        if(session.uri.contains("identify")) {
            val deviceList = listAndroidDevices(adbPath)
            val openPort = session.parameters.get("openPort")?.get(0)?.toInt() ?: 12345


        }


        return super.serve(session)
    }



    companion object {

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