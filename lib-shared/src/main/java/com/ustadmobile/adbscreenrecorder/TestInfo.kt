package com.ustadmobile.adbscreenrecorder


data class TestInfo(var clazzDesc: String?, var methodDesc: String?, var status: Int) {


    companion object {

        val STATUS_NOT_RUN = 0

        val STATUS_PASS = 1

        val STATUS_FAIL = 2

        val STATUS_SKIPPED = 3

    }
}