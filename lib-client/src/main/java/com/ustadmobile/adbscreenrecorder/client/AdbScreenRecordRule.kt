package com.ustadmobile.adbscreenrecorder.client

import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.ustadmobile.adbscreenrecorder.DEFAULT_DEVICE_PORT
import com.ustadmobile.adbscreenrecorder.DeviceInfo
import com.ustadmobile.adbscreenrecorder.TestInfo
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

class AdbScreenRecordRule(val localPort: Int = DEFAULT_DEVICE_PORT, val enabled: Boolean = true) : TestWatcher(){

    private val gson = Gson()

    private fun makeQueryParams(description: Description): String {
        return "testClazz=${URLEncoder.encode(description.className, "UTF-8")}" +
                "&testMethod=${URLEncoder.encode(description.methodName, "UTF-8")}" +
                "&deviceInfo=${URLEncoder.encode(gson.toJson(buildDeviceInfo()), "UTF-8")}"
    }

    private fun sendRequest(path: String) {
        var urlConnection: HttpURLConnection? = null
        val url = URL(URL("http://localhost:$localPort"), path)
        try {
            urlConnection = url.openConnection() as HttpURLConnection
            if(urlConnection.responseCode != 200) {
                Log.e(LOGTAG, "ERR: Got response ${urlConnection.responseCode} for request: $url")
                throw IllegalStateException("Connected, Non-200 response code: ${urlConnection.responseCode} AdbScreenRecord cannot connect to server http://localhost:$localPort")
            }
        }catch(e: Exception) {
            e.printStackTrace()
            Log.e(LOGTAG, "ERR: Exception sending AdbScreenRecord request to $url : " +
                    "please check the server url and make sure this server is accessible from the device")
            throw IllegalStateException("AdbScreenRecord cannot connect to server http://localhost:$localPort", e)
        }finally {
            urlConnection?.disconnect()
        }
    }

    override fun starting(description: Description) {
        if(enabled) {
            //start recording
            sendRequest("/startRecording?" + makeQueryParams(description))
        }
    }

    override fun succeeded(description: Description) {
        if (enabled) {
            handleFinished(TestInfo.STATUS_PASS, description)
        }
    }

    override fun failed(e: Throwable?, description: Description) {
        if (enabled) {
            handleFinished(TestInfo.STATUS_FAIL, description)
        }
    }

    override fun skipped(e: AssumptionViolatedException?, description: Description) {
        if (enabled) {
            handleFinished(TestInfo.STATUS_SKIPPED, description)
        }
    }

    private fun handleFinished(status: Int, description: Description) {
        sendRequest("/endRecording?${makeQueryParams(description)}" +
                "&testInfo=${URLEncoder.encode(gson.toJson(description.toTestInfo(status)), "UTF-8")}")
    }

    companion object {

        const val LOGTAG = "AdbScreenRecord"

    }

}