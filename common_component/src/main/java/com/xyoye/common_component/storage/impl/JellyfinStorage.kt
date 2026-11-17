package com.xyoye.common_component.storage.impl

import com.xyoye.common_component.network.repository.JellyfinRepository
import com.xyoye.common_component.storage.AbstractStorage
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.storage.file.impl.JellyfinStorageFile
import com.xyoye.data_component.data.jellyfin.JellyfinItemData
import com.xyoye.data_component.data.jellyfin.JellyfinMediaSourceData
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.entity.PlayHistoryEntity
import java.io.InputStream

/**
 * Jellyfin / Emby 媒体库
 *
 * 层级：媒体库视图(View) → 剧集(Series) → 季(Season) → 集(Episode)
 * 播放：优先直连流，容器不友好时回落到 HLS 转码，保证老盒子也能播。
 */
class JellyfinStorage(
    library: MediaLibraryEntity
) : AbstractStorage(library) {

    private val baseUrl: String by lazy { JellyfinRepository.baseUrl(library.url) }

    private val token: String by lazy { library.password.orEmpty() }

    private var userId: String = ""

    override suspend fun getRootFile(): StorageFile {
        return JellyfinStorageFile(
            storage = this,
            itemType = JellyfinStorageFile.TYPE_ROOT,
            itemId = "root",
            displayName = library.displayName.ifEmpty { "Jellyfin" },
            directory = true
        )
    }

    override suspend fun listFiles(file: StorageFile): List<StorageFile> {
        val jfFile = file as? JellyfinStorageFile ?: return emptyList()
        val uid = requireUserId() ?: return emptyList()

        return when (jfFile.itemType) {
            JellyfinStorageFile.TYPE_ROOT -> loadViews(uid)
            JellyfinStorageFile.TYPE_VIEW,
            JellyfinStorageFile.TYPE_FOLDER -> loadChildren(uid, jfFile.itemId)

            JellyfinStorageFile.TYPE_SERIES -> loadSeasons(uid, jfFile.itemId)
            JellyfinStorageFile.TYPE_SEASON -> loadEpisodes(uid, jfFile.itemId, false)
            else -> emptyList()
        }
    }

    override suspend fun openFile(file: StorageFile): InputStream? {
        return null
    }

    override suspend fun pathFile(path: String, isDirectory: Boolean): StorageFile? {
        return JellyfinStorageFile.parse(this, path)
    }

    override suspend fun historyFile(history: PlayHistoryEntity): StorageFile? {
        val path = history.storagePath ?: return null
        return pathFile(path, false)?.also { it.playHistory = history }
    }

    override suspend fun createPlayUrl(file: StorageFile): String? {
        val jfFile = file as? JellyfinStorageFile ?: return null
        val uid = requireUserId() ?: return null
        val itemId = jfFile.itemId
        if (itemId.isEmpty()) {
            return null
        }

        val mediaSource = JellyfinRepository.getPlaybackInfo(baseUrl, token, uid, itemId)
            .getOrNull()
            ?.mediaSources
            ?.firstOrNull()

        val mediaSourceId = mediaSource?.id?.takeIf { it.isNotEmpty() } ?: itemId

        return if (mediaSource != null && isDirectPlayable(mediaSource)) {
            "$baseUrl/Videos/$itemId/stream?static=true&MediaSourceId=$mediaSourceId&api_key=$token"
        } else {
            "$baseUrl/Videos/$itemId/master.m3u8?MediaSourceId=$mediaSourceId" +
                "&VideoCodec=h264&AudioCodec=aac,mp3&MaxAudioChannels=2&MaxWidth=1920&api_key=$token"
        }
    }

    override fun supportSearch(): Boolean {
        return true
    }

    override suspend fun search(keyword: String): List<StorageFile> {
        if (keyword.isBlank()) {
            return emptyList()
        }
        return JellyfinRepository.search(baseUrl, token, keyword)
            .getOrNull()
            ?.items
            .orEmpty()
            .map { toStorageFile(it) }
    }

    override suspend fun test(): Boolean {
        val info = JellyfinRepository.getSystemInfo(baseUrl, token).getOrNull() ?: return false
        return !info.serverName.isNullOrEmpty() || !info.id.isNullOrEmpty()
    }

    fun imageUrl(itemId: String): String {
        return "$baseUrl/Items/$itemId/Images/Primary?maxHeight=360&maxWidth=640&quality=80&api_key=$token"
    }

    private suspend fun loadViews(userId: String): List<StorageFile> {
        return JellyfinRepository.getViews(baseUrl, token, userId)
            .getOrNull()
            ?.items
            .orEmpty()
            .map { toStorageFile(it) }
    }

    private suspend fun loadChildren(userId: String, parentId: String): List<StorageFile> {
        return JellyfinRepository.getItems(
            baseUrl = baseUrl,
            token = token,
            parentId = parentId,
            includeItemTypes = "Series,Movie,BoxSet,Folder,Video",
            recursive = false,
            sortBy = "SortName"
        )
            .getOrNull()
            ?.items
            .orEmpty()
            .map { toStorageFile(it) }
    }

    private suspend fun loadSeasons(userId: String, seriesId: String): List<StorageFile> {
        val seasons = JellyfinRepository.getItems(
            baseUrl = baseUrl,
            token = token,
            parentId = seriesId,
            includeItemTypes = "Season",
            recursive = false,
            sortBy = "SortName"
        )
            .getOrNull()
            ?.items
            .orEmpty()
            .map { toStorageFile(it) }

        // 没有季结构的剧集（剧场版、OVA 合集），直接列集
        return seasons.ifEmpty { loadEpisodes(userId, seriesId, true) }
    }

    private suspend fun loadEpisodes(userId: String, parentId: String, recursive: Boolean): List<StorageFile> {
        return JellyfinRepository.getItems(
            baseUrl = baseUrl,
            token = token,
            parentId = parentId,
            includeItemTypes = "Episode",
            recursive = recursive,
            sortBy = "ParentIndexNumber,IndexNumber"
        )
            .getOrNull()
            ?.items
            .orEmpty()
            .map { toStorageFile(it) }
    }

    private fun toStorageFile(item: JellyfinItemData): JellyfinStorageFile {
        val type = when (item.type) {
            "Series" -> JellyfinStorageFile.TYPE_SERIES
            "Season" -> JellyfinStorageFile.TYPE_SEASON
            "Episode" -> JellyfinStorageFile.TYPE_EPISODE
            "Movie", "Video", "MusicVideo", "Trailer" -> JellyfinStorageFile.TYPE_MOVIE
            else -> JellyfinStorageFile.TYPE_FOLDER
        }
        val directory = type != JellyfinStorageFile.TYPE_EPISODE && type != JellyfinStorageFile.TYPE_MOVIE

        return JellyfinStorageFile(
            storage = this,
            itemType = type,
            itemId = item.id,
            displayName = buildDisplayName(item, type),
            directory = directory,
            seriesName = item.seriesName,
            indexNumber = item.indexNumber,
            seasonNumber = item.parentIndexNumber,
            runtimeTicks = item.runTimeTicks ?: 0L,
            imageId = item.id.takeIf { item.hasPrimaryImage }
        )
    }

    /**
     * 剧集显示名统一成「剧名 SxxEyy」，便于在播放历史里辨认，也方便手动搜弹幕
     */
    private fun buildDisplayName(item: JellyfinItemData, type: String): String {
        if (type != JellyfinStorageFile.TYPE_EPISODE) {
            return item.name
        }
        val series = item.seriesName
        val season = item.parentIndexNumber
        val index = item.indexNumber
        if (!series.isNullOrEmpty() && season != null && index != null) {
            return String.format("%s S%02dE%02d", series, season, index)
        }
        if (!series.isNullOrEmpty() && index != null) {
            return "$series - $index"
        }
        return item.name
    }

    private suspend fun requireUserId(): String? {
        if (userId.isNotEmpty()) {
            return userId
        }
        val users = JellyfinRepository.getUsers(baseUrl, token).getOrNull().orEmpty()
        userId = users.firstOrNull()?.id ?: return null
        return userId
    }

    /**
     * 判断能否直接播放原始文件
     */
    private fun isDirectPlayable(source: JellyfinMediaSourceData): Boolean {
        val container = source.container?.lowercase() ?: return false
        if (container !in arrayOf("mp4", "m4v", "webm", "mov")) {
            return false
        }
        return source.supportsDirectPlay || source.supportsDirectStream
    }
}
