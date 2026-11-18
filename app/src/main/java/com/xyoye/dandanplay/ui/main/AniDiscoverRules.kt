package com.xyoye.dandanplay.ui.main

import com.xyoye.dandanplay.ui.main.AniRules.str
import java.util.Calendar
import java.util.TimeZone

internal data class DiscoverSubject(val id: Int, val chinese: String, val original: String, val date: String, val summary: String, val score: Double, val rank: Int, val imageUrl: String = "") {
    val title: String get() = chinese.ifBlank { original }.ifBlank { "未命名条目 #$id" }
    fun searchTitle(originalName: Boolean): String = (if (originalName) original.ifBlank { chinese } else chinese.ifBlank { original }).trim()
}
internal data class DiscoverBatch(val items: List<DiscoverSubject>, val total: Int, val received: Int, val hasNextPage: Boolean = false)
internal data class DiscoverSeason(val year: Int, val month: Int) {
    val label: String get() = "${year}年${month}–${month + 2}月"
}
internal object AniDiscoverRules {
    fun season(year: Int, month: Int): DiscoverSeason {
        require(month in 1..12)
        return DiscoverSeason(year, (month - 1) / 3 * 3 + 1)
    }
    fun currentSeason(): DiscoverSeason {
        val now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        return season(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)
    }
    fun today(): Int {
        val day = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).get(Calendar.DAY_OF_WEEK)
        return if (day == Calendar.SUNDAY) 7 else day - 1
    }
    private fun subject(value: Any?): DiscoverSubject? {
        val obj = AniRules.obj(value)
        val id = (obj["id"] as? Number)?.toInt() ?: return null
        if (id <= 0 || (obj["type"] as? Number)?.toInt()?.let { it != 2 } == true || obj["nsfw"] == true) return null
        val rating = AniRules.obj(obj["rating"])
        return DiscoverSubject(id, obj.str("name_cn").trim(), obj.str("name").trim(), obj.str("date").ifBlank { obj.str("air_date") },
            obj.str("summary"), (rating["score"] as? Number)?.toDouble() ?: 0.0, (rating["rank"] as? Number)?.toInt() ?: 0,
            AniRules.obj(obj["images"]).let { images -> images.str("common").ifBlank { images.str("medium") }.ifBlank { images.str("small") } })
    }
    fun directory(text: String): DiscoverBatch {
        val obj = AniRules.obj(AniRules.decode(text))
        val rows = obj["data"] as? List<*> ?: error("Bangumi 目录格式变化：缺少 data")
        val total = (obj["total"] as? Number)?.toInt() ?: error("Bangumi 目录缺少 total")
        return DiscoverBatch(rows.mapNotNull { subject(it) }.distinctBy { it.id }, total, rows.size)
    }
    fun calendar(text: String, weekday: Int): DiscoverBatch {
        require(weekday in 1..7)
        val days = AniRules.decode(text) as? List<*> ?: error("Bangumi 放送表格式变化")
        val day = days.map { AniRules.obj(it) }.firstOrNull { (AniRules.obj(it["weekday"])["id"] as? Number)?.toInt() == weekday }
            ?: error("放送表缺少该星期，请刷新")
        val rows = day["items"] as? List<*> ?: error("放送表缺少 items")
        return DiscoverBatch(rows.mapNotNull { subject(it) }.distinctBy { it.id }, rows.size, rows.size)
    }
    fun detail(text: String): DiscoverSubject = subject(AniRules.decode(text)) ?: error("条目不可公开浏览或数据格式变化")

    // ===== 哔哩哔哩番剧接口（国内可直连，用于替换被网络阻断的 Bangumi 目录） =====
    private fun biliCover(raw: String): String {
        val value = raw.trim()
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://") -> "https://" + value.removePrefix("http://")
            else -> value
        }
    }
    private fun biliScore(raw: String): Double = raw.replace("分", "").trim().toDoubleOrNull() ?: 0.0
    private fun biliFollow(count: Long?): String = when {
        count == null || count <= 0 -> ""
        count >= 100000000L -> String.format(java.util.Locale.CHINA, "%.1f亿追番", count / 100000000.0)
        count >= 10000L -> String.format(java.util.Locale.CHINA, "%.1f万追番", count / 10000.0)
        else -> "$count 追番"
    }
    private fun biliRoot(text: String): Map<String, Any?> {
        val root = AniRules.obj(AniRules.decode(text))
        val code = (root["code"] as? Number)?.toInt() ?: 0
        require(code == 0) { "哔哩哔哩接口返回 code=$code${root.str("message").takeIf { it.isNotBlank() }?.let { "（$it）" } ?: ""}" }
        return root
    }
    private fun joinNotice(vararg parts: String): String = parts.filter { it.isNotBlank() }.joinToString(" · ")

    /** weekday: 1=周一 … 7=周日，与页面按钮序号一致；接口 day_of_week 同义。 */
    fun biliTimeline(text: String, weekday: Int): DiscoverBatch {
        require(weekday in 1..7)
        val root = biliRoot(text)
        val days = root["result"] as? List<*> ?: error("哔哩哔哩放送表格式变化：缺少 result")
        val day = days.map { AniRules.obj(it) }.firstOrNull { (it["day_of_week"] as? Number)?.toInt() == weekday }
            ?: return DiscoverBatch(emptyList(), 0, 0)
        val date = day.str("date")
        val rows = day["episodes"] as? List<*> ?: emptyList<Any?>()
        val items = rows.mapNotNull { value ->
            val obj = AniRules.obj(value)
            val id = (obj["season_id"] as? Number)?.toInt() ?: (obj["episode_id"] as? Number)?.toInt() ?: return@mapNotNull null
            val title = obj.str("title").trim()
            if (id <= 0 || title.isBlank()) return@mapNotNull null
            DiscoverSubject(
                id = id, chinese = title, original = "",
                date = listOf(date, obj.str("pub_time")).filter { it.isNotBlank() }.joinToString(" "),
                summary = joinNotice(obj.str("pub_index"), biliFollow((obj["follows"] as? Number)?.toLong())),
                score = 0.0, rank = 0, imageUrl = biliCover(obj.str("cover"))
            )
        }.distinctBy { it.id }
        return DiscoverBatch(items, items.size, rows.size)
    }

    fun biliRank(text: String): DiscoverBatch {
        val root = biliRoot(text)
        val result = AniRules.obj(root["result"])
        val rows = result["list"] as? List<*> ?: error("哔哩哔哩榜单格式变化：缺少 list")
        val items = rows.mapNotNull { value ->
            val obj = AniRules.obj(value)
            val id = (obj["season_id"] as? Number)?.toInt() ?: return@mapNotNull null
            val title = obj.str("title").trim()
            if (id <= 0 || title.isBlank()) return@mapNotNull null
            DiscoverSubject(
                id = id, chinese = title, original = "", date = "",
                summary = joinNotice(AniRules.obj(obj["new_ep"]).str("index_show"), biliFollow((AniRules.obj(obj["stat"])["follow"] as? Number)?.toLong())),
                score = biliScore(obj.str("rating")), rank = (obj["rank"] as? Number)?.toInt() ?: 0,
                imageUrl = biliCover(obj.str("cover"))
            )
        }.distinctBy { it.id }
        return DiscoverBatch(items, items.size, rows.size)
    }

    fun biliSeason(text: String): DiscoverBatch {
        val root = biliRoot(text)
        val data = AniRules.obj(root["data"])
        val rows = data["list"] as? List<*> ?: error("哔哩哔哩番剧索引格式变化：缺少 list")
        val total = (data["total"] as? Number)?.toInt() ?: rows.size
        val items = rows.mapNotNull { value ->
            val obj = AniRules.obj(value)
            val id = (obj["season_id"] as? Number)?.toInt() ?: return@mapNotNull null
            val title = obj.str("title").trim()
            if (id <= 0 || title.isBlank()) return@mapNotNull null
            DiscoverSubject(
                id = id, chinese = title, original = obj.str("subTitle").trim(), date = "",
                summary = joinNotice(obj.str("index_show"), obj.str("order"), obj.str("badge")),
                score = biliScore(obj.str("score")), rank = 0, imageUrl = biliCover(obj.str("cover"))
            )
        }.distinctBy { it.id }
        return DiscoverBatch(items, total, rows.size)
    }
    fun parent(page: String, listPage: String, searchReturn: String, pickerReturn: String): String? = when (page) {
        "episodes" -> "search"
        "search" -> searchReturn
        "metadata" -> listPage
        "picker" -> pickerReturn
        "sources", "help", "catalog" -> "discover"
        else -> null
    }
}
