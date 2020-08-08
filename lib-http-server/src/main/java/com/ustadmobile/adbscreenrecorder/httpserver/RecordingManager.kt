package com.ustadmobile.adbscreenrecorder.httpserver

import com.ustadmobile.adbscreenrecorder.httpserver.AdbScreenRecorderHttpServer.Companion.getWindowIdForDevice
import com.ustadmobile.adbscreenrecorder.httpserver.AdbScreenRecorderHttpServer.Companion.runProcess
import net.coobird.thumbnailator.Thumbnailator
import net.coobird.thumbnailator.Thumbnails
import java.io.File
import java.text.DateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

internal fun File.getDirForTest(className: String, methodName: String): File {
    val clazzDestDir = File(this, className)
    val methodDestDir = File(clazzDestDir, methodName)
    methodDestDir.takeIf{ !it.exists() }?.mkdirs()

    return methodDestDir
}

class RecordingManager(val adbPath: String, val destDir: File,
                       val wmctrlPath: String? = "/usr/bin/wmctrl",
                       val pgrepPath: String? = "/usr/bin/pgrep",
                       val recordMyDesktopPath: String? = "/usr/bin/recordmydesktop") {



    enum class RecordingType {
        ADB, RECORDMYDESKTOP
    }

    data class ProcessHolder(val process: Process, val recordingType: RecordingType,
                             val windowId: String? = null)

    data class RecordMyDesktopPaths(val wmctrlPath: String, val pgrepPath: String,
                                    val recordMyDesktopPath: String)

    private val recordMyDesktopPaths: RecordMyDesktopPaths? by lazy {
        val wmctrlPathVal = wmctrlPath
        val pgrepPathVal = pgrepPath
        val recordMyDesktopPathVal = recordMyDesktopPath

        if(wmctrlPathVal != null && File(wmctrlPathVal).exists() &&
            pgrepPathVal != null && File(pgrepPathVal).exists() &&
            recordMyDesktopPathVal != null && File(recordMyDesktopPathVal).exists()) {
            RecordMyDesktopPaths(wmctrlPathVal, pgrepPathVal, recordMyDesktopPathVal)
        }else {
            null
        }
    }

    val recordings = mutableMapOf<String, ProcessHolder>()

    val dateFormatter = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG)


    fun startRecording(deviceName: String, clazzName: String, testName: String) {
        println("(${dateFormatter.format(Date())}) Starting recording on $deviceName for $clazzName.$testName using ADB $adbPath")
        val sdkInt = AdbScreenRecorderHttpServer.getAndroidSdkVersion(adbPath, deviceName)
        val recordProcessHolder: ProcessHolder

        //Take a screenshot and save it
        val testMethodDir = destDir.getDirForTest(clazzName, testName)
        val screenshotDest = File(testMethodDir, "$deviceName.png")
        runProcess(listOf(adbPath, "-s", deviceName, "shell", "screencap", "/sdcard/$clazzName-$testName.png"))
        runProcess(listOf(adbPath, "-s", deviceName, "pull", "/sdcard/$clazzName-$testName.png",
            screenshotDest.absolutePath))
        runProcess(listOf(adbPath, "-s", deviceName, "shell", "rm", "/sdcard/$clazzName-$testName.png"))

        if(screenshotDest.exists()) {
            Thumbnails.of(screenshotDest)
                .size(640, 640)
                .outputFormat("jpg")
                .toFile(File(screenshotDest.parent, "${screenshotDest.nameWithoutExtension}.jpg"))
            screenshotDest.delete()
        }

        val recordPathsVal = recordMyDesktopPaths
        if(sdkInt <= 22 && deviceName.startsWith("emulator") && recordPathsVal != null) {
            val windowId = getWindowIdForDevice(recordPathsVal.wmctrlPath, deviceName)
            val videoOutFile = File(testMethodDir, "$deviceName.ogv")
            val recordProcess = ProcessBuilder(listOf(recordPathsVal.recordMyDesktopPath,
                "--windowid=$windowId",
                "--no-sound", "--output=${videoOutFile.absolutePath}", "--on-the-fly-encoding",
                "--v_quality=20", "--v_bitrate=512000", "--overwrite"))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()


            recordProcessHolder = ProcessHolder(recordProcess, RecordingType.RECORDMYDESKTOP,
                windowId)
        }else {
            val recordProcess = ProcessBuilder(listOf(adbPath, "-s", deviceName,
                "shell", "screenrecord", "/sdcard/$clazzName-$testName.mp4"))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()

            recordProcessHolder = ProcessHolder(recordProcess, RecordingType.ADB)
        }

        recordings["$clazzName-$testName"] = recordProcessHolder
    }

    fun stopRecording(deviceName: String, clazzName: String, testName: String): File {
        val processHolder = recordings["$clazzName-$testName"] ?: throw IllegalStateException("no such recording")
        println("(${dateFormatter.format(Date())}) Stopping recording recording on $deviceName for $clazzName.$testName using ADB $adbPath")

        val destVideoFile: File

        val methodDestDir = destDir.getDirForTest(clazzName, testName)

        val recordPathVals = recordMyDesktopPaths
        if(processHolder.recordingType == RecordingType.ADB) {
            //Note: screenrecord itself is actually running on the device. Thus we need to send SIGINT
            // on the device, NOT to the adb process
            ProcessBuilder(listOf(adbPath, "-s", deviceName, "shell", "kill", "-SIGINT",
                "$(pidof screenrecord)")).start().also {
                it.waitFor(20, TimeUnit.SECONDS)
            }

            processHolder.process.waitFor(20, TimeUnit.SECONDS)

            destVideoFile = File(methodDestDir, "$deviceName.mp4")

            ProcessBuilder(listOf(adbPath, "-s", deviceName, "pull",
                "/sdcard/$clazzName-$testName.mp4", destVideoFile.absolutePath))
                .start().also {
                    it.waitFor(60, TimeUnit.SECONDS)
                }

            ProcessBuilder(listOf(adbPath, "-s", deviceName, "shell", "rm",
                "/sdcard/$clazzName-$testName.mp4"))
                .start().also {
                    it.waitFor(60, TimeUnit.SECONDS)
                }

            recordings.remove("$clazzName-$testName")
        }else if(recordPathVals != null){
            val processId = runProcess(listOf(recordPathVals.pgrepPath, "-f",
                "recordmydesktop.*--windowid=${processHolder.windowId}.*"))
                .bufferedReader().readText().trim()
            println("Attempting to stop recordmydesktop PID=$processId")
            runProcess(listOf("/usr/bin/kill", "-SIGINT", processId))
            println("Waiting for recordmydesktop $processId")
            processHolder.process.waitFor(60, TimeUnit.SECONDS)

            recordings.remove("$clazzName-$testName")
            destVideoFile = File(methodDestDir, "$deviceName.ogv")
        }else {
            throw IllegalStateException("Huh? You told me this was recorded using recordmydesktop but recordPaths are null. " +
                    "This should be reported as a bug.")
        }

        return destVideoFile
    }

}