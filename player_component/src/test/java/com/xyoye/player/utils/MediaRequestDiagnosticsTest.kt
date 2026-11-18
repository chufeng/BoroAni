package com.xyoye.player.utils

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class MediaRequestDiagnosticsTest {
    @Test fun explainsDnsFailureWithoutLeakingSignedUrl() {
        val state = MediaRequestDiagnostics()
        val url = "https://example.org/video?secret=private"
        state.failed(state.begin(url), IOException(UnknownHostException("private-host")))
        assertTrue(state.messageFor(url)!!.contains("域名解析失败"))
        assertFalse(state.messageFor(url)!!.contains("private"))
        assertNull(state.messageFor("other"))
    }

    @Test fun ignoresOldMediaAndResetsWhenReplayingSameUrl() {
        val state = MediaRequestDiagnostics()
        val old = state.begin("video")
        val current = state.begin("video")
        state.failed(old, UnknownHostException())
        state.httpFailure(old, 403)
        assertNull(state.messageFor("video"))
        state.httpFailure(current, 403)
        assertTrue(state.messageFor("video")!!.contains("HTTP 403"))
        state.succeeded(old)
        assertNotNull(state.messageFor("video"))
        state.succeeded(current)
        assertNull(state.messageFor("video"))
    }

    @Test fun classifiesTransportFailures() {
        val state = MediaRequestDiagnostics()
        listOf(SocketTimeoutException() to "超时",
            SSLHandshakeException("private") to "安全连接校验失败",
            ConnectException() to "无法连接媒体服务器",
            IOException() to "读取媒体失败").forEach { (error, expected) ->
            state.failed(state.begin("video"), error)
            assertTrue(state.messageFor("video")!!.contains(expected))
        }
    }
}
