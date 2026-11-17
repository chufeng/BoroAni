package com.xyoye.common_component.storage.helper

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** 不持久化签名URL；线路名和集标识必须唯一，歧义时拒绝播放。 */
data class CmsEpisodeIdentity(val videoId: String, val lineId: String, val episodeId: String) {
    fun path(): String = "cms://play/v2/${encode(videoId)}/${encode(lineId)}/${encode(episodeId)}"

    companion object {
        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        fun parse(path: String): CmsEpisodeIdentity? = try {
            val uri = URI(path)
            val parts = uri.rawPath.orEmpty().split('/').drop(1)
            if (uri.scheme != "cms" || uri.host != "play" || uri.rawQuery != null ||
                uri.rawFragment != null || parts.size != 4 || parts[0] != "v2") {
                null
            } else {
                val ids = parts.drop(1).map { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
                if (ids.any { it.isBlank() }) null else CmsEpisodeIdentity(ids[0], ids[1], ids[2])
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: java.net.URISyntaxException) {
            null
        }
    }
}

object CmsEpisodeParser {
    data class Episode(val lineId: String, val episodeId: String, val title: String, val url: String)
    data class Line(val id: String, val episodes: List<Episode>)

    fun lines(raw: String, playFrom: String): List<Line> {
        val groups = raw.split("\$\$\$")
        val names = playFrom.split("\$\$\$")
        return groups.mapIndexedNotNull { index, group ->
            val name = names.getOrNull(index)?.trim().orEmpty()
            // 无线路名时仅单线路可安全恢复；多线路无法判断重排后的身份。
            val lineId = when {
                name.isNotEmpty() && names.count { it.trim() == name } == 1 -> "name:$name"
                groups.size == 1 -> "single"
                else -> return@mapIndexedNotNull null
            }
            val episodes = group.split('#').mapNotNull { item ->
                val text = item.trim()
                if (text.isEmpty()) return@mapNotNull null
                val separator = text.indexOf('$')
                val title = if (separator >= 0) text.substring(0, separator).trim().ifEmpty { "正片" } else "正片"
                var url = if (separator >= 0) text.substring(separator + 1).trim() else text
                if (url.startsWith("//")) url = "https:$url"
                val uri = try { URI(url) } catch (_: Exception) { return@mapNotNull null }
                if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) return@mapNotNull null
                Episode(lineId, title, title, url)
            }
            Line(lineId, episodes.filter { ep -> episodes.count { it.episodeId == ep.episodeId } == 1 })
        }
    }

    fun preferred(lines: List<Line>): List<Episode> = lines.maxByOrNull { line ->
        line.episodes.count { it.url.contains(".m3u8", true) } * 2 +
            line.episodes.count { it.url.contains(".mp4", true) }
    }?.episodes.orEmpty()

    fun resolve(identity: CmsEpisodeIdentity, lines: List<Line>): Episode? =
        lines.singleOrNull { it.id == identity.lineId }?.episodes
            ?.singleOrNull { it.episodeId == identity.episodeId }
}
