package com.xyoye.common_component.storage.impl

import android.net.Uri
import com.xyoye.common_component.network.repository.CmsRepository
import com.xyoye.common_component.storage.AbstractStorage
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.storage.file.impl.CmsStorageFile
import com.xyoye.data_component.data.cms.CmsVideoData
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.entity.PlayHistoryEntity
import java.io.InputStream

/**
 * 在线番剧源（苹果CMS / MacCMS v10 采集接口）
 *
 * 一个媒体库 = 一个采集接口地址。
 * 根目录列出「最新更新」，点进番剧看选集，选集直接是 m3u8/mp4 直链，点击即播。
 *
 * 字段复用说明（为避免改动 Room 表结构）：
 *   library.url      → 采集接口地址
 *   library.account  → 自定义 User-Agent（可空）
 *   library.password → 自定义 Referer（可空，部分站点的直链需要）
 */
class CmsSourceStorage(
    library: MediaLibraryEntity
) : AbstractStorage(library) {

    private val apiUrl: String = library.url.trim()

    private val userAgent: String? = library.account?.takeIf { it.isNotBlank() }

    private val referer: String? = library.password?.takeIf { it.isNotBlank() }

    override suspend fun getRootFile(): StorageFile {
        return CmsStorageFile(
            storage = this,
            itemType = CmsStorageFile.TYPE_ROOT,
            videoId = "root",
            displayName = library.displayName.ifEmpty { "在线番剧源" },
            directory = true
        )
    }

    override suspend fun listFiles(file: StorageFile): List<StorageFile> {
        val cmsFile = file as? CmsStorageFile ?: return emptyList()
        return when (cmsFile.itemType) {
            CmsStorageFile.TYPE_ROOT -> loadLatest()
            CmsStorageFile.TYPE_SERIES -> loadEpisodes(cmsFile)
            else -> emptyList()
        }
    }

    override suspend fun openFile(file: StorageFile): InputStream? {
        return null
    }

    override suspend fun pathFile(path: String, isDirectory: Boolean): StorageFile? {
        return CmsStorageFile.parse(this, path)
    }

    override suspend fun historyFile(history: PlayHistoryEntity): StorageFile? {
        val path = history.storagePath ?: return null
        return pathFile(path, false)?.also { it.playHistory = history }
    }

    override suspend fun createPlayUrl(file: StorageFile): String? {
        val episode = file as? CmsStorageFile ?: return null
        if (episode.itemType != CmsStorageFile.TYPE_EPISODE) return null
        val identity = episode.identity
        if (identity == null) {
            // 旧记录缺少番剧及线路ID，不能刷新，也不能猜测并切换到其它集。
            directory = null
            directoryFiles = emptyList()
            com.xyoye.common_component.weight.ToastCenter.showError("旧版播放记录无法刷新，请从番剧源重新选集")
            return null
        }
        val video = loadVideo(identity.videoId) ?: return null
        val lines = com.xyoye.common_component.storage.helper.CmsEpisodeParser.lines(
            video.playUrlText, video.playFromText
        )
        val current = com.xyoye.common_component.storage.helper.CmsEpisodeParser.resolve(identity, lines)
            ?: return null
        val previous = directoryFiles.associateBy { it.uniqueKey() }
        val episodes = lines.single { it.id == identity.lineId }.episodes
        directory = toSeriesFile(video)
        directoryFiles = episodeFiles(video, episodes).onEach {
            it.playHistory = if (it.uniqueKey() == episode.uniqueKey()) episode.playHistory
                else previous[it.uniqueKey()]?.playHistory
        }
        episode.updateDisplayName("${video.name} ${current.title}".trim())
        return current.url
    }

    override fun getNetworkHeaders(): Map<String, String>? {
        val headers = mutableMapOf<String, String>()
        referer?.let { headers["Referer"] = it }
        userAgent?.let { headers["User-Agent"] = it }
        return headers.ifEmpty { null }
    }

    override fun supportSearch(): Boolean {
        return true
    }

    override suspend fun search(keyword: String): List<StorageFile> {
        if (keyword.isBlank()) {
            return emptyList()
        }
        return CmsRepository.search(apiUrl, userAgent, referer, keyword)
            .getOrNull()
            ?.list
            .orEmpty()
            .map { toSeriesFile(it) }
    }

    override suspend fun test(): Boolean {
        return CmsRepository.latest(apiUrl, userAgent, referer)
            .getOrNull()
            ?.list
            .orEmpty()
            .isNotEmpty()
    }

    private suspend fun loadLatest(): List<StorageFile> {
        return CmsRepository.latest(apiUrl, userAgent, referer)
            .getOrNull()
            ?.list
            .orEmpty()
            .map { toSeriesFile(it) }
    }

    private suspend fun loadVideo(videoId: String): CmsVideoData? {
        if (videoId.isBlank()) return null
        return CmsRepository.detail(apiUrl, userAgent, referer, videoId)
            .getOrNull()?.list?.singleOrNull { it.id == videoId }
    }

    private suspend fun loadEpisodes(seriesFile: CmsStorageFile): List<StorageFile> {
        val video = loadVideo(seriesFile.videoId) ?: return emptyList()
        val lines = com.xyoye.common_component.storage.helper.CmsEpisodeParser.lines(
            video.playUrlText, video.playFromText
        )
        return episodeFiles(video, com.xyoye.common_component.storage.helper.CmsEpisodeParser.preferred(lines))
    }

    private fun episodeFiles(
        video: CmsVideoData,
        episodes: List<com.xyoye.common_component.storage.helper.CmsEpisodeParser.Episode>
    ): List<CmsStorageFile> = episodes.map { episode ->
        CmsStorageFile(
            storage = this,
            itemType = CmsStorageFile.TYPE_EPISODE,
            videoId = video.id,
            displayName = "${video.name} ${episode.title}".trim(),
            directory = false,
            playUrl = episode.url,
            identity = com.xyoye.common_component.storage.helper.CmsEpisodeIdentity(
                video.id, episode.lineId, episode.episodeId
            )
        )
    }

    private fun toSeriesFile(video: CmsVideoData): CmsStorageFile {
        return CmsStorageFile(
            storage = this,
            itemType = CmsStorageFile.TYPE_SERIES,
            videoId = video.id,
            displayName = video.name.ifEmpty { "未知番剧" },
            directory = true,
            coverUrl = resolveUrl(video.pic)
        )
    }

    private fun resolveUrl(url: String): String? {
        if (url.isBlank()) {
            return null
        }
        if (url.startsWith("http")) {
            return url
        }
        if (url.startsWith("//")) {
            return "https:$url"
        }
        val uri = Uri.parse(apiUrl)
        val origin = "${uri.scheme}://${uri.host}"
        return "$origin/${url.trimStart('/')}"
    }

    companion object {

        private const val MULTI_SOURCE_SEPARATOR = "\$\$\$"

        /**
         * 解析 vod_play_url：
         *   单线路  第01集$https://x/1.m3u8#第02集$https://x/2.m3u8
         *   多线路  线路1$$$线路2（每条线路内部仍是上面那种格式）
         *
         * 多线路时挑「m3u8 最多」的那条，保证拿到的是在线播放地址而不是下载链。
         */
        fun parseEpisodes(raw: String): List<Pair<String, String>> {
            if (raw.isBlank()) {
                return emptyList()
            }
            var best: List<Pair<String, String>> = emptyList()
            var bestScore = -1

            raw.split(MULTI_SOURCE_SEPARATOR).forEach { group ->
                val episodes = group.split("#")
                    .mapNotNull { parseEpisodeItem(it) }
                if (episodes.isEmpty()) {
                    return@forEach
                }
                val score = episodes.count { it.second.contains(".m3u8", ignoreCase = true) } * 2 +
                    episodes.count { it.second.contains(".mp4", ignoreCase = true) }
                if (score > bestScore) {
                    best = episodes
                    bestScore = score
                }
            }
            return best
        }

        private fun parseEpisodeItem(item: String): Pair<String, String>? {
            val text = item.trim()
            if (text.isEmpty()) {
                return null
            }
            val index = text.indexOf('$')
            val rawTitle = if (index >= 0) text.substring(0, index).trim() else ""
            var url = if (index >= 0) text.substring(index + 1).trim() else text.trim()

            if (url.startsWith("//")) {
                url = "https:$url"
            }
            if (!url.startsWith("http")) {
                return null
            }

            val title = if (rawTitle.isEmpty()) "正片" else rawTitle
            return title to url
        }
    }
}
