package com.ustadmobile.adbscreenrecorder.httpserver

import com.ustadmobile.adbscreenrecorder.httpserver.AdbScreenRecorderHttpServer.Companion.getAndroidSdkVersion
import com.ustadmobile.adbscreenrecorder.httpserver.AdbScreenRecorderHttpServer.Companion.listAndroidDevices
import fi.iki.elonen.NanoHTTPD
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileWriter

class AdbScreenRecorderHttpServerTest  {

    @JvmField
    @Rule
    val temporaryFolder = TemporaryFolder()

    //@Test
    fun givenAdbOnPath_shouldFindDeviceList() {
        val deviceList = listAndroidDevices("/home/mike/Android/Sdk/platform-tools/adb")
        println(deviceList)
    }

    //@Test
    fun givenRunningDevice_shouldGetSdkVersion() {
        val deviceToTry = listAndroidDevices("/home/mike/Android/Sdk/platform-tools/adb").first()
        val sdkInt = getAndroidSdkVersion("/home/mike/Android/Sdk/platform-tools/adb", deviceToTry)
        Assert.assertTrue("Got SDK Int", sdkInt > 0)
    }

    //@Test
    fun testRunningHttpServer() {
        val tmpDir = temporaryFolder.newFolder("adbscreenrecord")
        val server = AdbScreenRecorderHttpServer("emulator-5554",
            "/home/mike/Android/Sdk/platform-tools/adb", tmpDir)
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        server.startPortForwarding()
        println("NanoHTTPD is running on port 8081")
    }

    @Test
    fun generateReport() {
        val htmlFile = File(temporaryFolder.newFolder(), "index.html")
        val fileWriter = FileWriter(htmlFile)
        fileWriter.appendHTML().html {
            head {
                link(href = "adbscreenrecord.css", rel = "stylesheet", type = "text/css")
            }

            body {
                h2 {
                    +"ADB Screen Recorder Report"
                }
            }
        }
        fileWriter.flush()
        fileWriter.close()

        val textInHtml = htmlFile.readText()
        println(textInHtml)
    }

}