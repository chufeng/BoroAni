package com.xyoye.dandanplay.ui.main

import com.xyoye.dandanplay.ui.main.AniRules.str
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * AniList GraphQL 解析。AniList 在国内可直连，B站番剧索引已长期停更，故新番与榜单以 AniList 为主。
 * 只在后台线程调用，不涉及 Android API。
 */
internal object AniListRules {
    /** season: WINTER/SPRING/SUMMER/FALL；year 为播出年。 */
    fun seasonQuery(page: Int, season: String, year: Int): String =
        "query{Page(page:$page,perPage:20){media(type:ANIME,season:$season,seasonYear:$year," +
            "sort:POPULARITY_DESC,isAdult:false){id title{romaji native} coverImage{large} " +
            "averageScore episodes status startDate{year month day} nextAiringEpisode{airingAt episode}}}}"

    fun rankQuery(page: Int): String =
        "query{Page(page:$page,perPage:20){media(type:ANIME,sort:SCORE_DESC,format:TV,isAdult:false){" +
            "id title{romaji native} coverImage{large} averageScore episodes status " +
            "startDate{year month day} popularity}}}"

    fun scheduleQuery(page: Int, fromEpoch: Long, toEpoch: Long): String =
        "query{Page(page:$page,perPage:40){airingSchedules(airingAt_greater:$fromEpoch," +
            "airingAt_lesser:$toEpoch,sort:TIME){airingAt episode media{id title{romaji native} " +
            "coverImage{large} averageScore episodes status}}}}"

    fun seasonOf(month: Int): String = when (month) {
        in 1..3 -> "WINTER"
        in 4..6 -> "SPRING"
        in 7..9 -> "SUMMER"
        else -> "FALL"
    }

    fun seasonYear(now: Calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))): Int =
        now.get(Calendar.YEAR)

    fun dayRange(dayIndex: Int): Pair<Long, Long> {
        require(dayIndex in 1..7)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        val today = if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7 else cal.get(Calendar.DAY_OF_WEEK) - 1
        cal.add(Calendar.DAY_OF_MONTH, dayIndex - today)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val from = cal.timeInMillis / 1000
        return from to (from + 86400)
    }

    private fun cover(raw: String): String = when {
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("http://") -> "https://" + raw.removePrefix("http://")
        else -> raw
    }

    private fun score(value: Any?): Double {
        val raw = (value as? Number)?.toDouble() ?: return 0.0
        return if (raw <= 0) 0.0 else Math.round(raw / 10.0 * 10) / 10.0
    }

    private fun startDate(obj: Map<String, Any?>): String {
        val d = AniRules.obj(obj["startDate"])
        val y = (d["year"] as? Number)?.toInt() ?: return ""
        val m = (d["month"] as? Number)?.toInt() ?: return y.toString()
        val day = (d["day"] as? Number)?.toInt()
        return if (day == null) String.format(Locale.CHINA, "%04d-%02d", y, m)
        else String.format(Locale.CHINA, "%04d-%02d-%02d", y, m, day)
    }

    private fun subject(value: Any?, rank: Int, nextAiring: Boolean): DiscoverSubject? {
        val obj = AniRules.obj(value)
        val id = (obj["id"] as? Number)?.toInt() ?: return null
        if (id <= 0) return null
        val title = AniRules.obj(obj["title"])
        val native = title.str("native").trim()
        val romaji = title.str("romaji").trim()
        if (native.isBlank() && romaji.isBlank()) return null
        val episodes = (obj["episodes"] as? Number)?.toInt() ?: 0
        val status = obj.str("status").trim()
        val next = AniRules.obj(obj["nextAiringEpisode"])
        val nextEp = (next["episode"] as? Number)?.toInt() ?: 0
        val nextAt = (next["airingAt"] as? Number)?.toLong() ?: 0L
        val summary = buildString {
            if (episodes > 0) append("全 $episodes 集")
            if (status.isNotBlank()) { if (isNotEmpty()) append(" · "); append(statusText(status)) }
            if (nextAiring && nextEp > 0 && nextAt > 0) {
                if (isNotEmpty()) append(" · ")
                append("第 ").append(nextEp).append(" 集 ").append(timeText(nextAt))
            }
        }
        return DiscoverSubject(
            id = id, chinese = "", original = native.ifBlank { romaji },
            date = startDate(obj), summary = summary, score = score(obj["averageScore"]),
            rank = rank, imageUrl = cover(AniRules.obj(obj["coverImage"]).str("large"))
        )
    }

    private fun statusText(status: String): String = when (status) {
        "RELEASING" -> "连载中"
        "FINISHED" -> "已完结"
        "NOT_YET_RELEASED" -> "未开播"
        "CANCELLED" -> "已取消"
        "HIATUS" -> "停更中"
        else -> status
    }

    private fun timeText(epoch: Long): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply { timeInMillis = epoch * 1000 }
        return String.format(Locale.CHINA, "%02d-%02d %02d:%02d",
            cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    private fun root(text: String): Map<String, Any?> {
        val obj = AniRules.obj(AniRules.decode(text))
        val errors = obj["errors"] as? List<*>
        if (!errors.isNullOrEmpty()) {
            val message = AniRules.obj(errors.first()).str("message").ifBlank { "接口返回错误" }
            error("AniList：$message")
        }
        return AniRules.obj(AniRules.obj(obj["data"])["Page"])
    }

    fun season(text: String): DiscoverBatch {
        val rows = root(text)["media"] as? List<*> ?: error("AniList 新番格式变化：缺少 media")
        return DiscoverBatch(rows.mapNotNull { subject(it, 0, true) }.distinctBy { it.id }, rows.size, rows.size)
    }

    fun rank(text: String): DiscoverBatch {
        val rows = root(text)["media"] as? List<*> ?: error("AniList 榜单格式变化：缺少 media")
        val page = mutableListOf<DiscoverSubject>()
        rows.forEachIndexed { index, value ->
            subject(value, index + 1, false)?.let { page.add(it) }
        }
        val items = page.distinctBy { it.id }
        return DiscoverBatch(items, items.size, rows.size)
    }

    fun schedule(text: String): DiscoverBatch {
        val rows = root(text)["airingSchedules"] as? List<*> ?: error("AniList 放送表格式变化：缺少 airingSchedules")
        val items = rows.mapNotNull { value ->
            val obj = AniRules.obj(value)
            val media = AniRules.obj(obj["media"])
            val base = subject(media, 0, false) ?: return@mapNotNull null
            val episode = (obj["episode"] as? Number)?.toInt() ?: 0
            val at = (obj["airingAt"] as? Number)?.toLong() ?: 0L
            base.copy(
                date = if (at > 0) timeText(at) else base.date,
                summary = listOf("第 $episode 集", base.summary).filter { it.isNotBlank() }.joinToString(" · ")
            )
        }.distinctBy { it.id }
        return DiscoverBatch(items, items.size, rows.size)
    }
}
