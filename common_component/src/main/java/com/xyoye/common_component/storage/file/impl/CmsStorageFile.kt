package com.xyoye.common_component.storage.file.impl

import android.net.Uri
import com.xyoye.common_component.storage.file.AbstractStorageFile
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.storage.impl.CmsSourceStorage
import com.xyoye.common_component.storage.helper.CmsEpisodeIdentity

/**
 * 在线番剧源文件
 *
 * 路径格式：
 *   剧集目录  cms://series/{videoId}/{urlEncode(番名)}
 *   单集文件  cms://play/v2/{videoId}/{lineId}/{episodeId}（各段编码）
 *   旧版URL路径仅用于识别，缺少身份时提示重新选集，不尝试猜测。
 */
class CmsStorageFile(
    storage: CmsSourceStorage,
    val itemType: String,
    val videoId: String,
    private var displayName: String,
    private val directory: Boolean,
    val playUrl: String = "",
    private val coverUrl: String? = null,
    val identity: CmsEpisodeIdentity? = null
) : AbstractStorageFile(storage) {

    companion object {
        const val TYPE_ROOT = "root"
        const val TYPE_SERIES = "series"
        const val TYPE_EPISODE = "play"

        private const val SCHEME = "cms"

        fun seriesPath(videoId: String, name: String): String {
            return "$SCHEME://$TYPE_SERIES/$videoId/${Uri.encode(name)}"
        }

        fun episodePath(url: String, name: String): String {
            return "$SCHEME://$TYPE_EPISODE/${Uri.encode(url)}/${Uri.encode(name)}"
        }

        fun parse(storage: CmsSourceStorage, path: String): CmsStorageFile? {
            val uri = Uri.parse(path)
            if (uri.scheme != SCHEME) {
                return null
            }
            val type = uri.host ?: return null
            val segments = uri.pathSegments
            return when (type) {
                TYPE_ROOT -> CmsStorageFile(
                    storage, TYPE_ROOT, "root", "在线番剧源", true
                )

                TYPE_SERIES -> CmsStorageFile(
                    storage,
                    TYPE_SERIES,
                    segments.getOrNull(0) ?: "",
                    segments.getOrNull(1) ?: "",
                    true
                )

                TYPE_EPISODE -> {
                    val identity = CmsEpisodeIdentity.parse(path)
                    if (identity != null) {
                        CmsStorageFile(storage, TYPE_EPISODE, identity.videoId,
                            identity.episodeId, false, identity = identity)
                    } else if (segments.size == 2 && segments[0] != "v2") {
                        // 旧历史只有URL和番名，不能据此猜测番剧/线路身份。
                        CmsStorageFile(storage, TYPE_EPISODE, "", segments[1], false,
                            playUrl = segments[0])
                    } else null
                }

                else -> null
            }
        }
    }

    fun updateDisplayName(name: String) {
        displayName = name
    }

    fun storageImpl(): CmsSourceStorage = storage as CmsSourceStorage

    override fun getRealFile(): Any {
        return this
    }

    override fun filePath(): String {
        return when (itemType) {
            TYPE_EPISODE -> identity?.path() ?: episodePath(playUrl, displayName)
            TYPE_SERIES -> seriesPath(videoId, displayName)
            else -> "cms://$TYPE_ROOT/root/${Uri.encode(displayName)}"
        }
    }

    override fun fileUrl(): String {
        return filePath()
    }

    override fun fileCover(): String? {
        return coverUrl?.takeIf { it.isNotEmpty() }
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
        return CmsStorageFile(
            storage = storage as CmsSourceStorage,
            itemType = itemType,
            videoId = videoId,
            displayName = displayName,
            directory = directory,
            playUrl = playUrl,
            coverUrl = coverUrl,
            identity = identity
        ).also {
            it.playHistory = playHistory
        }
    }

    override fun isRootFile(): Boolean {
        return itemType == TYPE_ROOT
    }

    override fun isVideoFile(): Boolean {
        return itemType == TYPE_EPISODE
    }
}
