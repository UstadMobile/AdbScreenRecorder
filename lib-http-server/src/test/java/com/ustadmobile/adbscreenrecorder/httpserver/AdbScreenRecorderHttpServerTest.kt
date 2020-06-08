package com.ustadmobile.adbscreenrecorder.httpserver

import com.ustadmobile.adbscreenrecorder.httpserver.AdbScreenRecorderHttpServer.Companion.listAndroidDevices
import fi.iki.elonen.NanoHTTPD
import org.junit.Test
import java.nio.file.Files

class AdbScreenRecorderHttpServerTest  {

    @Test
    fun givenAdbOnPath_shouldFindDeviceList() {
        val deviceList = listAndroidDevices("/home/mike/Android/Sdk/platform-tools/adb")
        println(deviceList)
    }


    @Test
    fun testRunningHttpServer() {
        val tmpDir = Files.createTempDirectory("screenrecordhttp").toFile()
        val server = AdbScreenRecorderHttpServer("emulator-5554",
            "/home/user/Android/Sdk/platform-tools/adb", tmpDir)
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        server.startPortForwarding()
        println("NanoHTTPD is running on port 8081")
    }

}