package com.xyoye.dandanplay.ui.main

import android.content.Context
import com.xyoye.dandanplay.ui.main.AniRules.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AniRepository(context: Context) {
    private val file = File(context.filesDir, "ani-subscription.json")
    private val prefs = context.getSharedPreferences("ani-tv", Context.MODE_PRIVATE)
    private val client = AniTls.configure(OkHttpClient.Builder(), context)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val mutex = Mutex()
    private val requests = mutableMapOf<String, Long>()
    suspend fun load(): List<AniSource> = withContext(Dispatchers.IO) {
        if (file.exists()) AniRules.subscription(file.readText()) else emptyList()
    }
    suspend fun import(url: String): List<AniSource> {
        val checked = AniRules.http(url)
        val text = fetch(checked, mapOf("User-Agent" to AniRules.UA)).first
        return withContext(Dispatchers.IO) {
            val sources = AniRules.subscription(text)
            require(sources.isNotEmpty()) { "订阅中没有源" }
            val pending = File(file.parentFile, file.name + ".pending")
            pending.writeText(text)
            check(pending.renameTo(file)) { "保存订阅失败" }
            prefs.edit().putString("subscription", checked).apply()
            sources
        }
    }
    fun subscriptionUrl(): String = prefs.getString("subscription", AniRules.DEFAULT_SUB) ?: AniRules.DEFAULT_SUB
    fun enabled(source: AniSource): Boolean = prefs.getBoolean("enabled:${source.id}", true)
    fun toggle(source: AniSource) { prefs.edit().putBoolean("enabled:${source.id}", !enabled(source)).apply() }
    fun select(source: AniSource) { prefs.edit().putString("selected", source.id).apply() }
    fun selected(): String? = prefs.getString("selected", null)
    suspend fun search(source: AniSource, keyword: String): List<AniItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val interval = (source.config["requestInterval"] as? Number)?.toLong()?.coerceIn(0, 30000) ?: 3000
            val elapsed = System.nanoTime() / 1000000 - (requests[source.id] ?: 0L)
            if (elapsed < interval) delay(interval - elapsed)
            requests[source.id] = System.nanoTime() / 1000000
        }
        AniRules.subjects(source, fetch(AniRules.searchUrl(source, keyword), AniRules.headers(source, AniRules.base(source))).first)
    }
    suspend fun episodes(source: AniSource, item: AniItem): List<AniItem> = withContext(Dispatchers.IO) {
        AniRules.episodes(source, fetch(item.url, AniRules.headers(source, AniRules.base(source))).first)
    }
    suspend fun resolve(source: AniSource, episode: AniItem): Pair<String, Map<String, String>> = withContext(Dispatchers.IO) {
        val visited = mutableSetOf<String>()
        suspend fun visit(url: String, referer: String, depth: Int): Pair<String, Map<String, String>>? {
            if (!visited.add(url) || visited.size > 7) return null
            AniRules.video(source, url)?.let { return it to AniRules.headers(source, referer) }
            val (text, finalUrl) = fetch(url, AniRules.headers(source, referer))
            require(!text.contains("widevine", true) && !text.contains("licenseServer", true)) { "不支持 DRM 内容" }
            val (candidates, frames) = AniRules.pageCandidates(finalUrl, text)
            candidates.distinct().take(500).forEach { candidate ->
                AniRules.video(source, candidate)?.let { return it to AniRules.headers(source, finalUrl) }
            }
            if (depth < 2) {
                val nestedUrls = frames + candidates.filter { it.length <= 16384 }.mapNotNull { AniRules.nestedUrl(source, it) }
                for (nested in nestedUrls.distinct().take(3)) {
                    visit(nested, finalUrl, depth + 1)?.let { return it }
                }
            }
            return null
        }
        visit(episode.url, episode.url, 0) ?: error("未找到静态 HTTP 视频：本 MVP 不支持需 WebView/JavaScript、验证码、加密或 DRM 的解析，请换源。")
    }
    private suspend fun fetch(initialUrl: String, headers: Map<String, String>): Pair<String, String> {
        var url = AniRules.http(initialUrl)
        repeat(6) {
            val result = request(url, headers)
            if (result.code in 300..399) {
                url = AniRules.resolve(url, result.location ?: error("跳转缺少 Location"))
            } else {
                require(result.code in 200..299) { "HTTP ${result.code}；站点可能需验证或暂不可用" }
                return result.text to url
            }
        }
        error("HTTP 跳转次数过多")
    }
    private data class Page(val code: Int, val location: String?, val text: String)
    private suspend fun request(url: String, headers: Map<String, String>): Page = suspendCancellableCoroutine { continuation ->
        val builder = Request.Builder().url(AniRules.http(url))
        headers.forEach { (key, value) -> builder.header(key, value) }
        val request = builder.build()
        if (com.xyoye.dandanplay.BuildConfig.DEBUG) android.util.Log.i("AniTls", "请求 host=${request.url.host}")
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val page = response.use {
                        val body = it.body
                        val bytes = body?.byteStream()?.use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = input.read(buffer); if (n < 0) break
                                require(out.size() + n <= 2 * 1024 * 1024) { "页面超过 2 MiB 限制" }
                                out.write(buffer, 0, n)
                            }; out.toByteArray()
                        } ?: ByteArray(0)
                        Page(it.code, it.header("Location"), String(bytes, body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8))
                    }
                    if (continuation.isActive) continuation.resume(page)
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }
}
