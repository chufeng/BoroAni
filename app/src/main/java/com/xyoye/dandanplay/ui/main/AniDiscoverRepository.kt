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

internal enum class DiscoverSource { NETWORK, CACHE, BUNDLED }
internal data class DiscoverResult<T>(
    val value: T,
    val notice: String,
    val source: DiscoverSource = DiscoverSource.NETWORK,
    val fetchedAtMillis: Long? = null,
    val networkError: String? = null
)

internal class AniDiscoverRepository(
    private val directory: File,
    private val seed: (String) -> Pair<String, String>?,
    private val fetch: suspend (String) -> String
) {
    private var post: suspend (String, String) -> String = { _, _ -> throw IOException("该数据源不支持 POST") }

    constructor(context: Context) : this(
        File(context.cacheDir, "ani-discover"),
        { path -> bundled(context.applicationContext, path) },
        requester(context.applicationContext)
    ) {
        post = aniListPoster(context.applicationContext)
    }

    suspend fun directory(year: Int?, month: Int?, offset: Int, refresh: Boolean): DiscoverResult<DiscoverBatch> {
        require(offset in 0..10000)
        require((year == null) == (month == null)) { "年份与月份必须同时提供" }
        val query = if (year != null && month != null) {
            require(year in 1900..2200 && month in 1..12)
            "sort=date&year=$year&month=$month"
        } else "sort=rank"
        return load("/v0/subjects?type=2&$query&limit=20&offset=$offset", refresh, AniDiscoverRules::directory)
    }

    suspend fun calendar(day: Int, refresh: Boolean): DiscoverResult<DiscoverBatch> {
        require(day in 1..7)
        return load("/calendar", refresh) { AniDiscoverRules.calendar(it, day) }
    }

    suspend fun detail(id: Int, refresh: Boolean = false): DiscoverResult<DiscoverSubject> {
        require(id > 0)
        return load("/v0/subjects/$id", refresh, AniDiscoverRules::detail)
    }

    /** 哔哩哔哩番剧时间表：按星期返回当日放送。before/after 为接口允许的最大窗口（各6天）。 */
    suspend fun biliTimeline(weekday: Int, refresh: Boolean): DiscoverResult<DiscoverBatch> {
        require(weekday in 1..7)
        return loadExternal(
            "$BILI_BASE/pgc/web/timeline?types=1&before=6&after=6", "bili-timeline", "哔哩哔哩放送表", refresh,
            parse = { AniDiscoverRules.biliTimeline(it, weekday) }
        )
    }

    /** 哔哩哔哩番剧三日榜。 */
    suspend fun biliRank(refresh: Boolean): DiscoverResult<DiscoverBatch> =
        loadExternal("$BILI_BASE/pgc/web/rank/list?day=3&season_type=1", "bili-rank", "哔哩哔哩番剧榜", refresh,
            AniDiscoverRules::biliRank)

    /** 哔哩哔哩番剧索引：按放送年月筛选，order=3 为追番人数排序。 */
    suspend fun biliSeason(year: Int, month: Int, offset: Int, refresh: Boolean): DiscoverResult<DiscoverBatch> {
        require(year in 1900..2200 && month in 1..12)
        require(offset in 0..10000)
        val page = offset / PAGE_SIZE + 1
        return loadExternal(
            "$BILI_BASE/pgc/season/index/result?st=1&order=3&sort=0&page=$page&season_type=1&pagesize=$PAGE_SIZE" +
                "&type=1&season_version=-1&spoken_language_type=-1&area=-1&is_finish=-1&copyright=-1" +
                "&season_status=-1&season_month=$month&year=$year&style_id=-1",
            "bili-season-$year-$month-${offset}", "哔哩哔哩番剧索引", refresh, AniDiscoverRules::biliSeason
        )
    }

    /**
     * AniList 当季新番。B站番剧索引已长期停更，故新番默认走 AniList（国内可直连）。
     */
    suspend fun anilistSeason(year: Int, month: Int, page: Int, refresh: Boolean): DiscoverResult<DiscoverBatch> {
        require(month in 1..12)
        require(page >= 1)
        val season = AniListRules.seasonOf(month)
        return loadExternal(
            ANILIST_URL, "anilist-season-$season-$year-$page", "AniList $year $season 新番", refresh,
            AniListRules::season, jsonQuery(AniListRules.seasonQuery(page, season, year))
        )
    }

    suspend fun anilistRank(page: Int, refresh: Boolean): DiscoverResult<DiscoverBatch> {
        require(page >= 1)
        return loadExternal(
            ANILIST_URL, "anilist-rank-$page", "AniList 高分榜", refresh,
            AniListRules::rank, jsonQuery(AniListRules.rankQuery(page))
        )
    }

    suspend fun anilistSchedule(weekday: Int, page: Int, refresh: Boolean): DiscoverResult<DiscoverBatch> {
        require(weekday in 1..7)
        require(page >= 1)
        val (from, to) = AniListRules.dayRange(weekday)
        return loadExternal(
            ANILIST_URL, "anilist-schedule-$weekday-$page", "AniList 放送表", refresh,
            AniListRules::schedule, jsonQuery(AniListRules.scheduleQuery(page, from, to))
        )
    }


    private suspend fun <T> loadExternal(
        url: String, cacheKey: String, label: String, refresh: Boolean, parse: (String) -> T,
        body: String? = null
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
            val text = if (body == null) fetch(url) else post(url, body)
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

    private suspend fun <T> load(path: String, refresh: Boolean, parse: (String) -> T): DiscoverResult<T> = withContext(Dispatchers.IO) {
        val file = File(directory, path.toByteArray(Charsets.UTF_8).let { bytes ->
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        } + ".json")
        val cached = if (file.exists() && file.length() <= MAX_BODY_BYTES) runCatching { file.readText() }.getOrNull() else null
        val cachedValue = cached?.let { runCatching { parse(it) }.getOrNull() }
        val cachedAt = file.lastModified()
        val stamp = timestamp(cachedAt)
        if (!refresh && cachedValue != null && System.currentTimeMillis() - cachedAt in 0 until 3600000) {
            return@withContext DiscoverResult(cachedValue, "本机缓存 $stamp（北京时间）；可刷新", DiscoverSource.CACHE, cachedAt)
        }
        // 安装包快照不阻断首次联网，也永远不作为联网成功写入缓存。
        try {
            val text = fetch(path)
            val value = parse(text)
            val fetchedAt = System.currentTimeMillis()
            runCatching {
                directory.mkdirs()
                synchronized(cacheLock) {
                    directory.listFiles()?.filter { it.extension == "json" }
                        ?.sortedByDescending { it.lastModified() }?.drop(79)?.forEach { it.delete() }
                    val pending = File.createTempFile(file.name, ".pending", directory)
                    try {
                        pending.writeText(text)
                        if (pending.renameTo(file)) file.setLastModified(fetchedAt)
                    } finally { pending.delete() }
                }
            }
            DiscoverResult(value, "Bangumi API 已返回 ${timestamp(fetchedAt)}（北京时间）；服务端亦有缓存，并非实时榜",
                DiscoverSource.NETWORK, fetchedAt)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val failure = failureSummary(e)
            if (cachedValue != null) {
                DiscoverResult(cachedValue, "联网失败（$failure），显示离线缓存 $stamp（北京时间）；请刷新重试",
                    DiscoverSource.CACHE, cachedAt, failure)
            } else {
                val bundled = runCatching { seed(path) }.getOrNull()
                val bundledValue = bundled?.let { runCatching { parse(it.first) }.getOrNull() }
                if (bundledValue != null) DiscoverResult(bundledValue, "联网失败（$failure）；${bundled.second}",
                    DiscoverSource.BUNDLED, null, failure)
                else throw IOException("Bangumi 加载失败（$failure）；检查网络后选择刷新重试，仍可搜索已有源", e)
            }
        }
    }

    companion object {
        private const val MAX_BODY_BYTES = 4 * 1024 * 1024
        private const val PAGE_SIZE = 20
        private const val BILI_BASE = "https://api.bilibili.com"
        private const val ANILIST_URL = "https://graphql.anilist.co/"
        private const val BILI_UA =
            "Mozilla/5.0 (Linux; Android 6.0.1; MiTV4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0 Mobile Safari/537.36"
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

        private fun bundled(context: Context, path: String): Pair<String, String>? = runCatching {
            val assets = context.assets
            val manifest = AniRules.obj(AniRules.decode(assets.open("ani-discover/manifest.json").bufferedReader().use { it.readText() }))
            val entry = AniRules.obj(manifest[path])
            val name = entry["file"] as? String ?: return@runCatching null
            val captured = entry["capturedAt"] as? String ?: "未知"
            assets.open("ani-discover/$name").bufferedReader().use { it.readText() } to
                "安装包内 Bangumi 真实 API 快照 $captured（UTC）；非实时、尚未联网刷新"
        }.getOrNull()

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

        private fun requester(context: Context): suspend (String) -> String {
            val client = AniTls.configure(OkHttpClient.Builder(), context)
                .connectTimeout(12, TimeUnit.SECONDS).readTimeout(18, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
            return { path -> request(client, path) }
        }

        private suspend fun request(client: OkHttpClient, path: String): String = suspendCancellableCoroutine { continuation ->
            val absolute = path.startsWith("http://") || path.startsWith("https://")
            val url = if (absolute) path else "https://api.bgm.tv$path"
            val host = runCatching { java.net.URI(url).host }.getOrNull().orEmpty()
            val builder = Request.Builder().url(url)
                .header("User-Agent", if (absolute) BILI_UA else "ddplay-AniTV/1.0 (Android TV; https://github.com/xyoye/DanDanPlayForAndroid)")
                .header("Accept", "application/json")
            if (absolute) builder.header("Referer", "https://www.bilibili.com/")
            val request = builder.build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (com.xyoye.dandanplay.BuildConfig.DEBUG) android.util.Log.d(
                        "AniMetadata", "host=$host network=failed error=${e.javaClass.simpleName} cancelled=${call.isCanceled()}")
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
                        if (com.xyoye.dandanplay.BuildConfig.DEBUG) android.util.Log.d(
                            "AniMetadata", "host=$host network=response http=${response.code} chars=${text.length}; parsed source is shown in UI")
                        if (continuation.isActive) continuation.resume(text)
                    } catch (e: Exception) {
                        if (com.xyoye.dandanplay.BuildConfig.DEBUG) android.util.Log.d(
                            "AniMetadata", "host=$host network=failed error=${e.javaClass.simpleName}")
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
    }
}

internal class DiscoverHttpException(val statusCode: Int) : IOException("Bangumi HTTP $statusCode；不绕过认证或限流")
