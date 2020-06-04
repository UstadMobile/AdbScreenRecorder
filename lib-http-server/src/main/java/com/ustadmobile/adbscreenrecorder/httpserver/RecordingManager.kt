package com.ustadmobile.adbscreenrecorder.httpserver

import java.io.File
import java.lang.reflect.Field
import java.util.concurrent.TimeUnit

class RecordingManager(val adbPath: String, val destDir: File) {

    val recordings = mutableMapOf<String, Process>()

    fun startRecording(deviceName: String, clazzName: String, testName: String) {
        val recordProcess = ProcessBuilder(listOf(adbPath, "-s", deviceName,
            "shell", "screenrecord", "/sdcard/$clazzName-$testName.mp4"))
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        recordings["$clazzName-$testName"] = recordProcess
    }

    fun stopRecording(deviceName: String, clazzName: String, testName: String): File {
        val process = recordings["$clazzName-$testName"] ?: throw IllegalStateException("no such recording")

        //Note: screenrecord itself is actually running on the device. Thus we need to send SIGINT
        // on the device, NOT to the adb process
        ProcessBuilder(listOf(adbPath, "-s", deviceName, "shell", "kill", "-SIGINT",
            "$(pidof screenrecord)")).start().also {
            it.waitFor(20, TimeUnit.SECONDS)
        }

        process.waitFor(20, TimeUnit.SECONDS)


        val deviceDestDir = File(destDir, deviceName)
        val clazzDestDir = File(deviceDestDir, clazzName)
        val destFile = File(clazzDestDir, "$testName.mp4")

        clazzDestDir.mkdirs()

        ProcessBuilder(listOf(adbPath, "-s", deviceName, "pull",
            "/sdcard/$clazzName-$testName.mp4", destFile.absolutePath))
            .start().also {
                it.waitFor(60, TimeUnit.SECONDS)
            }

        ProcessBuilder(listOf(adbPath, "-s", deviceName, "shell", "rm",
            "/sdcard/$clazzName-$testName.mp4"))
            .start().also {
                it.waitFor(60, TimeUnit.SECONDS)
            }

        recordings.remove("$clazzName-$testName")
        return destFile
    }





}