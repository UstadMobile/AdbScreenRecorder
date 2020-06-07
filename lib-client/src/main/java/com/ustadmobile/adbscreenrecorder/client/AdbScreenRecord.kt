package com.ustadmobile.adbscreenrecorder.client

@Retention(AnnotationRetention.RUNTIME)
annotation class AdbScreenRecord(val value: String, val enabled: Boolean = true) {

}