package com.xyoye.player.utils

import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Created by xyoye on 2021/5/2.
 */

class VlcProxyServer private constructor() : NanoHTTPD("127.0.0.1", randomPort()) {
    private data class MediaRequest(val url: String, val headers: Map<String, String>, val token: Long)
    @Volatile private var mediaRequest: MediaRequest? = null
    private val diagnostics = MediaRequestDiagnostics()
    @Volatile private var client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS).followSslRedirects(false).build()

    fun configureClient(client: OkHttpClient) {
        this.client = client
    }

    private object Holder {
        val instance = VlcProxyServer()
    }

    companion object {
        //随机端口
        private fun randomPort() = Random.nextInt(30000, 40000)

        internal fun upstreamUrl(base: String, path: String, query: String?): okhttp3.HttpUrl {
            require(path.startsWith("/") && !path.startsWith("//"))
            return base.toHttpUrl().newBuilder().encodedPath(path).encodedQuery(query).build()
        }

        @JvmStatic
        fun getInstance() = Holder.instance
    }

    override fun serve(session: IHTTPSession?): Response {
        session ?: return super.serve(session)

        if (session.method != Method.GET && session.method != Method.HEAD) {
            return newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, "text/plain", "不支持此请求方法")
        }
        val media = mediaRequest ?: return newFixedLengthResponse(Status.SERVICE_UNAVAILABLE, "text/plain", "尚未设置媒体")
        return try {
            val started = System.nanoTime()
            val upstream = getProxyResponse(session, media)
            if (upstream.code >= 400) diagnostics.httpFailure(media.token, upstream.code)
            else diagnostics.succeeded(media.token)
            val body = upstream.body
            val debug = com.xyoye.player_component.BuildConfig.DEBUG
            val requestId = System.identityHashCode(upstream)
            if (debug) android.util.Log.i("VlcProxy", "id=$requestId code=${upstream.code} headersMs=${(System.nanoTime() - started) / 1000000} length=${body?.contentLength()}")
            val stream = object : java.io.FilterInputStream(body?.byteStream() ?: java.io.ByteArrayInputStream(ByteArray(0))) {
                private var bytes = 0L
                private var eof = false
                private fun <T> readSafely(block: () -> T): T = try { block() } catch (e: java.io.IOException) {
                    diagnostics.failed(media.token, e)
                    throw e
                }
                override fun read(): Int = readSafely { `in`.read() }.also { if (it >= 0) bytes++ else eof = true }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    readSafely { `in`.read(buffer, offset, length) }.also { if (it >= 0) bytes += it else eof = true }
                override fun close() {
                    try { super.close() } finally {
                        upstream.close()
                        if (debug) android.util.Log.i("VlcProxy", "id=$requestId bytes=$bytes totalMs=${(System.nanoTime() - started) / 1000000} eof=$eof")
                    }
                }
            }
            val length = body?.contentLength() ?: 0L
            val status = Status.lookup(upstream.code) ?: Status.INTERNAL_ERROR
            val response = if (length >= 0) {
                newFixedLengthResponse(status, upstream.header("Content-Type"), stream, length)
            } else {
                newChunkedResponse(status, upstream.header("Content-Type"), stream)
            }
            upstream.headers.forEach { (key, value) ->
                if (key.lowercase() !in setOf("content-type", "content-length", "transfer-encoding", "connection", "keep-alive")) {
                    response.addHeader(key, value)
                }
            }
            response
        } catch (e: Exception) {
            diagnostics.failed(media.token, e)
            android.util.Log.w("VlcProxy", "上游请求失败 host=${media.url.toHttpUrl().host} type=${e.javaClass.simpleName}")
            newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "媒体请求失败")
        }
    }

    fun getInputStreamUrl(url: String, headers: Map<String, String>): String {
        val upstream = url.toHttpUrl()
        mediaRequest = MediaRequest(url, headers.toMap(), diagnostics.begin(url))
        return "http://127.0.0.1:$listeningPort${upstream.encodedPath}${upstream.encodedQuery?.let { "?$it" }.orEmpty()}"
    }

    fun failureMessageFor(url: String): String? = diagnostics.messageFor(url)

    private fun getProxyResponse(session: IHTTPSession, media: MediaRequest): okhttp3.Response {
        val requestBuilder = Request.Builder()
        session.headers.filterKeys { it.lowercase() in setOf("range", "if-range", "accept") }.forEach { (key, value) ->
            requestBuilder.header(key, value)
        }
        media.headers.forEach { (key, value) -> requestBuilder.header(key, value) }
        requestBuilder.header("Accept-Encoding", "identity")
        val target = upstreamUrl(media.url, session.uri, session.queryParameterString)
        val request = requestBuilder.url(target).method(session.method.name, null).build()
        return client.newCall(request).execute()
    }

}
