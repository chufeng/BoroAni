package com.xyoye.dandanplay.ui.main

import java.util.Calendar
import java.util.TimeZone

internal data class DiscoverSubject(val id: Int, val chinese: String, val original: String, val date: String, val summary: String, val score: Double, val rank: Int, val imageUrl: String = "") {
    val title: String get() = chinese.ifBlank { original }.ifBlank { "未命名条目 #$id" }
    fun searchTitle(originalName: Boolean): String = (if (originalName) original.ifBlank { chinese } else chinese.ifBlank { original }).trim()
}
internal data class DiscoverBatch(val items: List<DiscoverSubject>, val total: Int, val received: Int, val hasNextPage: Boolean = false)
internal data class DiscoverSeason(val year: Int, val month: Int) {
    val label: String get() = "${year}年${listOf("冬", "春", "夏", "秋")[(month - 1) / 3]}季"
    val months: String get() = "${month}–${month + 2}月"

    fun shift(quarters: Int): DiscoverSeason {
        val index = year * 4 + (month - 1) / 3 + quarters
        return DiscoverSeason(index / 4, index % 4 * 3 + 1)
    }
}
internal object AniDiscoverRules {
    fun season(year: Int, month: Int): DiscoverSeason {
        require(month in 1..12)
        return DiscoverSeason(year, (month - 1) / 3 * 3 + 1)
    }
    fun currentSeason(nowMillis: Long = System.currentTimeMillis()): DiscoverSeason {
        val now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply { timeInMillis = nowMillis }
        return season(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)
    }
    fun today(): Int {
        val day = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).get(Calendar.DAY_OF_WEEK)
        return if (day == Calendar.SUNDAY) 7 else day - 1
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
