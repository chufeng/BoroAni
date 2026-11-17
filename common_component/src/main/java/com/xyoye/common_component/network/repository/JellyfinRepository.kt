package com.xyoye.common_component.network.repository

import com.xyoye.common_component.network.Retrofit

/**
 * Jellyfin 接口仓库
 */

object JellyfinRepository : BaseRepository() {

    fun baseUrl(url: String): String = url.trim().trimEnd('/')

    private fun url(baseUrl: String, path: String): String {
        return "${baseUrl(baseUrl)}/${path.trimStart('/')}"
    }

    /**
     * 获取服务器信息，用于测试连接
     */
    suspend fun getSystemInfo(baseUrl: String, token: String) = request()
        .doGet {
            Retrofit.jellyfinService.getSystemInfo(
                url(baseUrl, "System/Info"),
                token
            )
        }

    /**
     * 获取用户列表，用于拿 UserId
     */
    suspend fun getUsers(baseUrl: String, token: String) = request()
        .doGet {
            Retrofit.jellyfinService.getUsers(
                url(baseUrl, "Users"),
                token
            )
        }

    /**
     * 获取媒体库视图（电影、电视剧、动漫等）
     */
    suspend fun getViews(baseUrl: String, token: String, userId: String) = request()
        .doGet {
            Retrofit.jellyfinService.getViews(
                url(baseUrl, "Users/$userId/Views"),
                token
            )
        }

    /**
     * 获取指定父级下的子项
     */
    suspend fun getItems(
        baseUrl: String,
        token: String,
        parentId: String,
        includeItemTypes: String,
        recursive: Boolean = false,
        sortBy: String? = null,
        limit: Int? = null
    ) = request()
        .doGet {
            Retrofit.jellyfinService.getItems(
                url(baseUrl, "Items"),
                token,
                parentId,
                includeItemTypes,
                recursive,
                sortBy = sortBy,
                sortOrder = if (sortBy == null) null else "Ascending",
                limit = limit
            )
        }

    /**
     * 搜索剧集 / 电影
     */
    suspend fun search(baseUrl: String, token: String, keyword: String) = request()
        .doGet {
            Retrofit.jellyfinService.searchItems(
                url(baseUrl, "Items"),
                token,
                keyword
            )
        }

    /**
     * 获取播放信息（直链 / 转码判断）
     */
    suspend fun getPlaybackInfo(
        baseUrl: String,
        token: String,
        userId: String,
        itemId: String
    ) = request()
        .doGet {
            Retrofit.jellyfinService.getPlaybackInfo(
                url(baseUrl, "Items/$itemId/PlaybackInfo?UserId=$userId"),
                token
            )
        }
}
