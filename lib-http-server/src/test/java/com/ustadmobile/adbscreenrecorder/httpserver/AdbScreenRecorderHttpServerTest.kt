package com.ustadmobile.adbscreenrecorder.httpserver

import com.ustadmobile.adbscreenrecorder.httpserver.AdbScreenRecorderHttpServer.Companion.getAndroidSdkVersion
import com.ustadmobile.adbscreenrecorder.httpserver.AdbScreenRecorderHttpServer.Companion.getWindowIdForDevice
import com.ustadmobile.adbscreenrecorder.httpserver.AdbScreenRecorderHttpServer.Companion.listAndroidDevices
import fi.iki.elonen.NanoHTTPD
import org.junit.Assert
import org.junit.Test
import java.nio.file.Files

class AdbScreenRecorderHttpServerTest  {

    @Test
    fun givenAdbOnPath_shouldFindDeviceList() {
        val deviceList = listAndroidDevices("/home/mike/Android/Sdk/platform-tools/adb")
        println(deviceList)
    }

    @Test
    fun givenRunningDevice_shouldGetSdkVersion() {
        val deviceToTry = listAndroidDevices("/home/mike/Android/Sdk/platform-tools/adb").first()
        val sdkInt = getAndroidSdkVersion("/home/mike/Android/Sdk/platform-tools/adb", deviceToTry)
        Assert.assertTrue("Got SDK Int", sdkInt > 0)
    }

    @Test
    fun givenRunningDevice_shouldGetWindowId() {
        val deviceToTry = listAndroidDevices("/home/mike/Android/Sdk/platform-tools/adb").first()
        val emulatorWindowId = getWindowIdForDevice("/usr/bin/wmctrl", deviceToTry)
        println(emulatorWindowId)
        Assert.assertNotNull(emulatorWindowId)
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