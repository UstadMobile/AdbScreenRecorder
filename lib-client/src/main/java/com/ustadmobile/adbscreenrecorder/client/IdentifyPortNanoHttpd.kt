package com.ustadmobile.adbscreenrecorder.client

import fi.iki.elonen.NanoHTTPD

class IdentifyPortNanoHttpd(port: Int) : NanoHTTPD(port){

    override fun serve(session: IHTTPSession?): Response {
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/plain", "Hello")
    }
}