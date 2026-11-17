package com.xyoye.common_component.storage.file.impl

import android.net.Uri
import com.xyoye.common_component.storage.file.AbstractStorageFile
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.storage.impl.JellyfinStorage

/**
 * Jellyfin 媒体库文件
 *
 * 路径格式：jf://{itemType}/{itemId}/{urlEncode(fileName)}
 */
class JellyfinStorageFile(
    storage: JellyfinStorage,
    val itemType: String,
    val itemId: String,
    private val displayName: String,
    private val directory: Boolean,
    private val seriesName: String? = null,
    private val indexNumber: Int? = null,
    private val seasonNumber: Int? = null,
    private val runtimeTicks: Long = 0L,
    private val imageId: String? = null
) : AbstractStorageFile(storage) {

    companion object {
        const val TYPE_ROOT = "root"
        const val TYPE_VIEW = "view"
        const val TYPE_FOLDER = "folder"
        const val TYPE_SERIES = "series"
        const val TYPE_SEASON = "season"
        const val TYPE_EPISODE = "episode"
        const val TYPE_MOVIE = "movie"

        private const val SCHEME = "jf"

        fun buildPath(itemType: String, itemId: String, name: String): String {
            return "$SCHEME://$itemType/$itemId/${Uri.encode(name)}"
        }

        fun parse(storage: JellyfinStorage, path: String): JellyfinStorageFile? {
            val uri = Uri.parse(path)
            if (uri.scheme != SCHEME) {
                return null
            }
            val type = uri.host ?: return null
            val segments = uri.pathSegments
            val id = segments.getOrNull(0) ?: ""
            val name = segments.getOrNull(1) ?: ""
            val isDirectory = type in arrayOf(TYPE_ROOT, TYPE_VIEW, TYPE_FOLDER, TYPE_SERIES, TYPE_SEASON)
            return JellyfinStorageFile(
                storage = storage,
                itemType = type,
                itemId = id,
                displayName = name,
                directory = isDirectory
            )
        }
    }

    fun storageImpl(): JellyfinStorage = storage as JellyfinStorage

    override fun getRealFile(): Any {
        return this
    }

    override fun filePath(): String {
        return buildPath(itemType, itemId, displayName)
    }

    override fun fileUrl(): String {
        return filePath()
    }

    override fun fileCover(): String? {
        val coverId = imageId?.takeIf { it.isNotEmpty() }
            ?: itemId.takeIf { it.isNotEmpty() }
            ?: return null
        if (itemType == TYPE_ROOT) {
            return null
        }
        return storageImpl().imageUrl(coverId)
    }

    override fun isDirectory(): Boolean {
        return directory
    }

    override fun fileName(): String {
        return displayName
    }

    override fun fileLength(): Long {
        return 0L
    }

    override fun clone(): StorageFile {
        return JellyfinStorageFile(
            storage = storage as JellyfinStorage,
            itemType = itemType,
            itemId = itemId,
            displayName = displayName,
            directory = directory,
            seriesName = seriesName,
            indexNumber = indexNumber,
            seasonNumber = seasonNumber,
            runtimeTicks = runtimeTicks,
            imageId = imageId
        ).also {
            it.playHistory = playHistory
        }
    }

    override fun isRootFile(): Boolean {
        return itemType == TYPE_ROOT
    }

    override fun isVideoFile(): Boolean {
        return itemType == TYPE_EPISODE || itemType == TYPE_MOVIE
    }

    override fun videoDuration(): Long {
        return if (runtimeTicks > 0) runtimeTicks / 10000 else 0L
    }

    /**
     * 弹幕检索用的剧集名，形如「葬送的芙莉莲 S01E02」
     */
    fun danmuTitle(): String {
        val title = seriesName ?: displayName
        val season = seasonNumber ?: return title
        val episode = indexNumber ?: return title
        return String.format("%s S%02dE%02d", title, season, episode)
    }
}
