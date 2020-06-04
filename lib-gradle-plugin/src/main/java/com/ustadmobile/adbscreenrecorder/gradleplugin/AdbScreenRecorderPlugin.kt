package com.ustadmobile.adbscreenrecorder.gradleplugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class AdbScreenRecorderPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("adbScreenRecord", AdbScreenRecorderExtension::class.java)

        project.task("startAdbScreenRecordServer") {
            it.doLast {
                println("Start server on port ${extension.port}")
            }
        }

        project.task("stopAdbScreenRecordServer") {
            it.doLast {
                println("Stop server")
            }
        }
    }
}