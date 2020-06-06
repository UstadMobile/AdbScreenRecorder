package com.ustadmobile.adbscreenrecorder.client

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.junit.AssumptionViolatedException
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.IllegalStateException

class AdbScreenRecordRule(val serverUrl: String, val enabled: Boolean = true) : TestWatcher(){

    private var openPortHttpd: NanoHTTPD? = null

    private fun makeQueryParams(description: Description): String {
        return "openPort=${openPortHttpd?.listeningPort ?: -1}" +
                "&testClazz=${URLEncoder.encode(description.className, "UTF-8")}" +
                "&testMethod=${URLEncoder.encode(description.methodName, "UTF-8")}"
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

        //start recording
        sendRequest("/startRecording?" + makeQueryParams(description))

        super.starting(description)
    }

    override fun succeeded(description: Description) {
        super.succeeded(description)
        handleFinished(true, false, false, description)
    }

    override fun failed(e: Throwable?, description: Description) {
        super.failed(e, description)
        handleFinished(false, true, false, description)
    }

    override fun skipped(e: AssumptionViolatedException?, description: Description) {
        super.skipped(e, description)
        handleFinished(false, false, true, description)
    }

    private fun handleFinished(succeeded: Boolean, failed: Boolean, skipped: Boolean, description: Description) {
        sendRequest("/endRecording?" + makeQueryParams(description))
    }

    companion object {

        const val LOGTAG = "AdbScreenRecord"

    }

}