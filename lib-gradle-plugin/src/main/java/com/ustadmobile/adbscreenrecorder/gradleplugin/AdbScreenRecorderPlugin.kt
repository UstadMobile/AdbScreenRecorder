package com.ustadmobile.adbscreenrecorder.gradleplugin

import com.ustadmobile.adbscreenrecorder.httpserver.AdbScreenRecorderHttpServer
import fi.iki.elonen.NanoHTTPD
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.lang.IllegalStateException
import java.nio.file.Paths
import java.util.*



class AdbScreenRecorderPlugin : Plugin<Project> {

    private fun adbPathFromSdkDir(sdkDir: String) : String {
        var path = Paths.get(sdkDir, "platform-tools", "adb").toFile().absolutePath
        if(System.getProperty("os.name").contains("Windows")) {
            path += ".exe"
        }
        return path
    }

    override fun apply(project: Project) {
        val extension = project.extensions.create("adbScreenRecord", AdbScreenRecorderExtension::class.java)

        val destDir = extension.destDir ?: "${project.buildDir.absolutePath}${File.separator}reports${File.separator}adbScreenRecord"
        val destination = project.file(destDir)
        destination.takeIf { !it.exists() }?.mkdirs()

        var server: AdbScreenRecorderHttpServer? = null

        val startTask = project.task("startAdbScreenRecordServer") {
            it.doLast {
                var adbPath = extension.adbPath
                val localPropertiesFile = File(project.rootDir, "local.properties")
                if(adbPath == null && localPropertiesFile.exists()) {
                    //try and find this using the local.properties file
                    val localProperties = localPropertiesFile.inputStream().use {fileIn ->
                        Properties().apply {
                            load(fileIn)
                        }
                    }

                    if(localProperties.containsKey("sdk.dir")) {
                        adbPath = adbPathFromSdkDir(localProperties.getProperty("sdk.dir"))
                    }
                }

                if(adbPath == null) {
                    val androidHome = System.getenv("ANDROID_HOME")
                    if(androidHome != null)
                        adbPath = adbPathFromSdkDir(androidHome)

                }

                if(adbPath == null)
                    throw IllegalStateException("AdbScreenRecorderPlugin cannot find adb. " +
                            "Please specify it in the config block, local.properties or set ANDROID_HOME environment")
                if(adbPath.isBlank() || !File(adbPath).exists()) {
                    throw IllegalStateException("ADBPath found $adbPath is not valid!")
                }

                server = AdbScreenRecorderHttpServer(null, extension.port,
                    adbPath ?: "", destination)

                server?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
            }
        }

        val stopTask = project.task("stopAdbScreenRecordServer") {
            it.doLast {
                server?.also {
                    it.generateReport(projectName = "${project.displayName} ")
                }
                server?.stop()
                server = null
            }
        }

        project.tasks.whenTaskAdded {task ->
            if(task.name.toLowerCase().endsWith("androidtest") ||
                task.name.toLowerCase().endsWith("connectedcheck")) {
                task.dependsOn(startTask)
                task.finalizedBy(stopTask)
            }
        }
    }
}