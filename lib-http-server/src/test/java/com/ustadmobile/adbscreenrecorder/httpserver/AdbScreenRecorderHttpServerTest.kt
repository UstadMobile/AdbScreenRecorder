package com.ustadmobile.adbscreenrecorder.httpserver

import com.ustadmobile.adbscreenrecorder.httpserver.AdbScreenRecorderHttpServer.Companion.identifyByOpenPort
import com.ustadmobile.adbscreenrecorder.httpserver.AdbScreenRecorderHttpServer.Companion.listAndroidDevices
import fi.iki.elonen.NanoHTTPD
import org.junit.Test
import java.nio.file.Files

class AdbScreenRecorderHttpServerTest  {

    @Test
    fun givenAdbOnPath_shouldFindDeviceList() {
        val deviceList = listAndroidDevices("/home/mike/Android/Sdk/platform-tools/adb")
        println("foo")
    }

    @Test
    fun givenAdbOnPath_shouldFindActiveDevice() {
        val deviceList = listAndroidDevices("/home/mike/Android/Sdk/platform-tools/adb")
        val activeDevice = identifyByOpenPort("/home/mike/Android/Sdk/platform-tools/adb",
            deviceList, 12345)
        println(activeDevice)
    }

    @Test
    fun givenDeviceNameCanRecordVideo() {
        val adbPath = "/home/mike/Android/Sdk/platform-tools/adb"
        val deviceList = listAndroidDevices(adbPath)
        val activeDevice = identifyByOpenPort(adbPath, deviceList, 12345)


        val tmpDir = Files.createTempDirectory("screenrecord").toFile()
        println("save to $tmpDir")
        val recordingManager = RecordingManager(adbPath, tmpDir)
        recordingManager.startRecording(activeDevice!!, "FooFragmentTest", "givenFoo")
        Thread.sleep(10000)
        val fileRecorded = recordingManager.stopRecording(activeDevice!!, "FooFragmentTest", "givenFoo")
        println("Saved ")
    }

    @Test
    fun testRunningHttpServer() {
        val tmpDir = Files.createTempDirectory("screenrecordhttp").toFile()
        val server = AdbScreenRecorderHttpServer(null, 9004,
            "/home/mike/Android/Sdk/platform-tools/adb", tmpDir)
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        println("NanoHTTPD is running on port 8081")
    }

}