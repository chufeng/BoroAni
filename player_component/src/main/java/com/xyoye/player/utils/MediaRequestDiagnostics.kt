package com.xyoye.player.utils

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** User-facing failures never include exception text, media URLs or headers. */
internal class MediaRequestDiagnostics {
    private var generation = 0L
    private var source = ""
    private var failure: String? = null

    @Synchronized fun begin(source: String): Long {
        this.source = source
        failure = null
        return ++generation
    }

    @Synchronized fun failed(token: Long, error: Throwable) {
        if (token != generation) return
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        failure = when {
            causes.any { it is UnknownHostException } -> "媒体域名解析失败，当前网络暂时无法访问该播放地址。请返回换线路或换源后重试。"
            causes.any { it is SocketTimeoutException } -> "媒体请求超时，请返回重试，或换线路、换源。"
            causes.any { it is SSLException } -> "媒体服务器的安全连接校验失败，请返回换线路或换源。"
            causes.any { it is ConnectException } -> "无法连接媒体服务器，请返回重试，或换线路、换源。"
            else -> "读取媒体失败，请检查网络，或返回换线路、换源。"
        }
    }

    @Synchronized fun httpFailure(token: Long, code: Int) {
        if (token == generation && code >= 400) {
            failure = "媒体服务器返回 HTTP $code，请返回重试，或换线路、换源。"
        }
    }

    @Synchronized fun succeeded(token: Long) {
        if (token == generation) failure = null
    }

    @Synchronized fun messageFor(source: String): String? =
        failure.takeIf { this.source == source }
}
