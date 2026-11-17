package com.xyoye.dandanplay.ui.main

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

internal data class AniSearchAttempt(
    val source: AniSource,
    val keyword: String,
    val count: Int = 0,
    val error: String? = null
)

internal data class AniSearchResult(
    val source: AniSource?,
    val keyword: String,
    val items: List<AniItem>,
    val attempts: List<AniSearchAttempt>
)

/** Sequential, cancellable fallback over at most three enabled sources and two keywords. */
internal class AniSearch(
    private val request: suspend (AniSource, String) -> List<AniItem>,
    private val timeoutMillis: Long = 8000
) {
    companion object {
        const val MAX_SOURCES = 3

        fun keywords(value: String, shortFirst: Boolean = false): List<String> {
            val full = value.trim().replace(Regex("\\s+"), " ")
            if (full.isBlank()) return emptyList()
            var short = full.replace(Regex("""[（(【\[].*?[）)】\]]"""), " ").trim()
            short = short.replace(Regex("""\s*(?:第[0-9一二三四五六七八九十百]+[季期部]|(?:Season|Part)\s*\d+|\d+(?:st|nd|rd|th)\s+Season)(?:\s.*)?$""", RegexOption.IGNORE_CASE), "").trim()
            short = short.split(Regex("[：:！!？?～~|｜—–]")).first().trim()
            short = short.replace(Regex("\\s+"), " ")
            if (short.length > 12 && short.any { it in '\u4e00'..'\u9fff' }) {
                short = short.take(8).trim()
            } else if (short.split(' ').size > 6) {
                short = short.split(' ').take(4).joinToString(" ")
            }
            // Never replace a useful title with a one-character fragment.
            if (short.length < 2) short = full
            return (if (shortFirst) listOf(short, full) else listOf(full, short)).distinct()
        }
    }

    suspend fun find(
        available: List<AniSource>,
        selected: AniSource,
        keyword: String,
        shortFirst: Boolean = false,
        onAttempt: (AniSource, String) -> Unit = { _, _ -> }
    ): AniSearchResult {
        val queries = keywords(keyword, shortFirst)
        require(queries.isNotEmpty()) { "请先输入番剧关键词" }
        val eligible = available.filter { it.unsupported == null }.distinctBy { it.id }
        val current = eligible.firstOrNull { it.id == selected.id }
            ?: error("当前源未启用或规则不受支持，请重新选源")
        val candidates = (listOf(current) + eligible.filter { it.id != current.id }).take(MAX_SOURCES)
        val attempts = mutableListOf<AniSearchAttempt>()
        for (source in candidates) {
            for (query in queries) {
                currentCoroutineContext().ensureActive()
                onAttempt(source, query)
                val items = try {
                    withTimeoutOrNull(timeoutMillis) { request(source, query) }
                        ?: throw java.io.IOException("搜索超时")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    attempts.add(AniSearchAttempt(source, query, error = e.message ?: e.javaClass.simpleName))
                    // Transport/parse errors are independent of the keyword; try the next source.
                    break
                }
                currentCoroutineContext().ensureActive()
                attempts.add(AniSearchAttempt(source, query, items.size))
                if (items.isNotEmpty()) return AniSearchResult(source, query, items, attempts.toList())
            }
        }
        return AniSearchResult(null, queries.first(), emptyList(), attempts.toList())
    }
}
