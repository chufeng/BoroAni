package com.xyoye.dandanplay.ui.main

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class DiscoverSource { NETWORK, CACHE }
internal data class DiscoverResult<T>(
    val value: T,
    val notice: String,
    val source: DiscoverSource = DiscoverSource.NETWORK,
    val fetchedAtMillis: Long? = null,
    val networkError: String? = null
)

internal class AniDiscoverRepository(
    private val directory: File,
    private val localize: (DiscoverBatch) -> DiscoverBatch = { it },
    private val post: suspend (String, String) -> String
) {
    constructor(context: Context) : this(
        File(context.cacheDir, "ani-discover"),
        chineseNames(context.applicationContext),
        aniListPoster(context.applicationContext)
    )

    suspend fun anilistSeason(quarter: DiscoverSeason, page: Int, refresh: Boolean): DiscoverResult<DiscoverBatch> {
        require(page >= 1)
        val season = AniListRules.seasonOf(quarter.month)
        val year = quarter.year
        return load(
            "anilist-season-$season-$year-$page", "AniList $year $season 新番", refresh,
            AniListRules::season, jsonQuery(AniListRules.seasonQuery(page, season, year))
        ).withChineseNames()
    }

    suspend fun anilistRank(page: Int, refresh: Boolean): DiscoverResult<DiscoverBatch> {
        require(page >= 1)
        return load(
            "anilist-rank-$page", "AniList 高分榜", refresh,
            { AniListRules.rank(it, page) }, jsonQuery(AniListRules.rankQuery(page))
        ).withChineseNames()
    }

    suspend fun anilistSchedule(weekday: Int, page: Int, refresh: Boolean): DiscoverResult<DiscoverBatch> {
        require(weekday in 1..7)
        require(page >= 1)
        val (from, to) = AniListRules.dayRange(weekday)
        return load(
            "anilist-schedule-$weekday-$page", "AniList 放送表", refresh,
            AniListRules::schedule, jsonQuery(AniListRules.scheduleQuery(page, from, to))
        ).withChineseNames()
    }

    private suspend fun DiscoverResult<DiscoverBatch>.withChineseNames(): DiscoverResult<DiscoverBatch> =
        withContext(Dispatchers.IO) {
            copy(value = localize(value))
        }

    private suspend fun <T> load(
        cacheKey: String, label: String, refresh: Boolean, parse: (String) -> T, body: String
    ): DiscoverResult<T> = withContext(Dispatchers.IO) {
        val file = File(directory, cacheKey.toByteArray(Charsets.UTF_8).let { bytes ->
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        } + ".json")
        val cached = if (file.exists() && file.length() <= MAX_BODY_BYTES) runCatching { file.readText() }.getOrNull() else null
        val cachedValue = cached?.let { runCatching { parse(it) }.getOrNull() }
        val cachedAt = file.lastModified()
        val stamp = timestamp(cachedAt)
        if (!refresh && cachedValue != null && System.currentTimeMillis() - cachedAt in 0 until 3600000) {
            return@withContext DiscoverResult(cachedValue, "$label 本机缓存 $stamp（北京时间）；可刷新", DiscoverSource.CACHE, cachedAt)
        }
        try {
            val text = post(ANILIST_URL, body)
            val value = parse(text)
            val fetchedAt = System.currentTimeMillis()
            runCatching {
                directory.mkdirs()
                synchronized(cacheLock) {
                    val pending = File.createTempFile(file.name, ".pending", directory)
                    try {
                        pending.writeText(text)
                        if (pending.renameTo(file)) file.setLastModified(fetchedAt)
                    } finally { pending.delete() }
                }
            }
            DiscoverResult(value, "$label 已直连返回 ${timestamp(fetchedAt)}（北京时间）；内容为站点公开数据，非实时榜单缓存",
                DiscoverSource.NETWORK, fetchedAt)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val failure = failureSummary(e)
            if (cachedValue != null) {
                DiscoverResult(cachedValue, "联网失败（$failure），显示 $label 离线缓存 $stamp（北京时间）；请刷新重试",
                    DiscoverSource.CACHE, cachedAt, failure)
            } else throw IOException("$label 加载失败（$failure）；检查网络后刷新重试，仍可用搜索直接找番", e)
        }
    }

    companion object {
        private const val MAX_BODY_BYTES = 4 * 1024 * 1024
        private const val ANILIST_URL = "https://graphql.anilist.co/"
        private const val ANILIST_UA = "ddplay-AniTV/1.0 (Android TV)"

        private fun jsonQuery(query: String): String {
            val escaped = query
                .replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
            return "{\"query\":\"$escaped\"}"
        }
        private val cacheLock = Any()

        private fun timestamp(millis: Long): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
            .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") }.format(java.util.Date(millis))

        private fun failureSummary(error: Exception): String = when (error) {
            is java.net.UnknownHostException -> "DNS 解析失败"
            is java.net.ConnectException -> "无法建立网络连接"
            is javax.net.ssl.SSLPeerUnverifiedException -> "TLS 证书或域名校验失败"
            is javax.net.ssl.SSLException -> "TLS 握手或传输失败"
            is java.net.SocketTimeoutException -> "网络连接或读取超时"
            is java.io.InterruptedIOException -> "网络请求超时或中断"
            is DiscoverHttpException -> "HTTP ${error.statusCode}"
            else -> error.javaClass.simpleName
        }

        private fun chineseNames(context: Context): (DiscoverBatch) -> DiscoverBatch {
            val titles by lazy {
                AniChineseTitles.parse(context.assets.open("ani-discover/chinese-titles.json")
                    .bufferedReader().use { it.readText() })
            }
            return { titles.apply(it) }
        }

        private fun aniListPoster(context: Context): suspend (String, String) -> String {
            val client = AniTls.configure(OkHttpClient.Builder(), context)
                .connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
            return { url, body -> postJson(client, url, body) }
        }

        private suspend fun postJson(
            client: OkHttpClient, url: String, json: String
        ): String = suspendCancellableCoroutine { continuation ->
            val request = Request.Builder().url(url)
                .header("User-Agent", ANILIST_UA)
                .header("Accept", "application/json")
                .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (com.xyoye.dandanplay.BuildConfig.DEBUG) android.util.Log.d(
                        "AniMetadata", "host=graphql.anilist.co network=failed error=${e.javaClass.simpleName}")
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use {
                            if (!it.isSuccessful) throw DiscoverHttpException(it.code)
                            val input = it.body?.byteStream() ?: throw IOException("空响应")
                            val out = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val n = input.read(buffer); if (n < 0) break
                                require(out.size() + n <= MAX_BODY_BYTES) { "接口响应超过 4 MiB" }
                                out.write(buffer, 0, n)
                            }
                            out.toString("UTF-8")
                        }
                        if (continuation.isActive) continuation.resume(text)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
    }
}

internal class DiscoverHttpException(val statusCode: Int) : IOException("AniList HTTP $statusCode")
