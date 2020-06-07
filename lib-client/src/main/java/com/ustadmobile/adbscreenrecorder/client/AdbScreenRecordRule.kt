package com.ustadmobile.adbscreenrecorder.client

import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.ustadmobile.adbscreenrecorder.DeviceInfo
import com.ustadmobile.adbscreenrecorder.TestInfo
import fi.iki.elonen.NanoHTTPD
import org.junit.AssumptionViolatedException
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.IllegalStateException

fun buildDeviceInfo() = DeviceInfo(Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.MODEL, Build.BRAND)

fun Description.toTestInfo(status: Int): TestInfo {
    val clazzAnnotation = testClass.getAnnotation(AdbScreenRecord::class.java)
    val clazzDesc = clazzAnnotation?.value ?: className

    val methodAnnotation = getAnnotation(AdbScreenRecord::class.java)
    val methodDesc = methodAnnotation?.value ?: methodName

    return TestInfo(clazzDesc, methodDesc, status)
}

class AdbScreenRecordRule(val serverUrl: String, val enabled: Boolean = true) : TestWatcher(){

    private var openPortHttpd: NanoHTTPD? = null

    private val gson = Gson()

    private fun makeQueryParams(description: Description): String {
        return "openPort=${openPortHttpd?.listeningPort ?: -1}" +
                "&testClazz=${URLEncoder.encode(description.className, "UTF-8")}" +
                "&testMethod=${URLEncoder.encode(description.methodName, "UTF-8")}" +
                "&deviceInfo=${URLEncoder.encode(gson.toJson(buildDeviceInfo()), "UTF-8")}"
    }

    private fun sendRequest(path: String) {
        var urlConnection: HttpURLConnection? = null
        val url = URL(URL(serverUrl), path)
        try {
            urlConnection = url.openConnection() as HttpURLConnection
            if(urlConnection.responseCode != 200) {
                Log.e(LOGTAG, "ERR: Got response ${urlConnection.responseCode} for request: $url")
                throw IllegalStateException("Connected, Non-200 response code: ${urlConnection.responseCode} AdbScreenRecord cannot connect to server $serverUrl")
            }
        }catch(e: Exception) {
            e.printStackTrace()
            Log.e(LOGTAG, "ERR: Exception sending AdbScreenRecord request to $url : " +
                    "please check the server url and make sure this server is accessible from the device")
            throw IllegalStateException("AdbScreenRecord cannot connect to server $serverUrl", e)
        }finally {
            urlConnection?.disconnect()
        }
    }

    override fun starting(description: Description) {
        openPortHttpd = IdentifyPortNanoHttpd(0).also {
            it.start()
        }
        val descriptionInfo = description.toTestInfo(TestInfo.STATUS_NOT_RUN)
        println(descriptionInfo)

        //start recording
        sendRequest("/startRecording?" + makeQueryParams(description))

        super.starting(description)
    }

    override fun succeeded(description: Description) {
        super.succeeded(description)
        handleFinished(TestInfo.STATUS_PASS, description)
    }

    override fun failed(e: Throwable?, description: Description) {
        super.failed(e, description)
        handleFinished(TestInfo.STATUS_FAIL, description)
    }

    override fun skipped(e: AssumptionViolatedException?, description: Description) {
        super.skipped(e, description)
        handleFinished(TestInfo.STATUS_SKIPPED, description)
    }

    private fun handleFinished(status: Int, description: Description) {
        sendRequest("/endRecording?${makeQueryParams(description)}" +
                "&testInfo=${URLEncoder.encode(gson.toJson(description.toTestInfo(status)), "UTF-8")}")
    }

    companion object {

        const val LOGTAG = "AdbScreenRecord"

    }

}