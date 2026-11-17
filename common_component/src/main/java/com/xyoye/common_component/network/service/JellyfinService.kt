package com.xyoye.common_component.network.service

import com.xyoye.data_component.data.jellyfin.JellyfinItemsData
import com.xyoye.data_component.data.jellyfin.JellyfinPlaybackInfoData
import com.xyoye.data_component.data.jellyfin.JellyfinSystemInfoData
import com.xyoye.data_component.data.jellyfin.JellyfinUserData
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Jellyfin 服务端接口
 *
 * 说明：所有请求都使用 @Url 传入完整地址，避免反向代理带子路径（如 https://host/jellyfin）时被
 * DynamicBaseUrlInterceptor 只替换 host 而丢掉子路径。
 */

interface JellyfinService {

    companion object {
        const val HEADER_TOKEN = "X-Emby-Token"

        const val FIELD_ITEM = "PrimaryImageAspectRatio,MediaSources,Overview,ChildCount,RecursiveItemCount,ProductionYear"
    }

    @GET
    suspend fun getSystemInfo(
        @Url url: String,
        @Header(HEADER_TOKEN) token: String?
    ): JellyfinSystemInfoData

    @GET
    suspend fun getUsers(
        @Url url: String,
        @Header(HEADER_TOKEN) token: String?
    ): List<JellyfinUserData>

    @GET
    suspend fun getViews(
        @Url url: String,
        @Header(HEADER_TOKEN) token: String?
    ): JellyfinItemsData

    @GET
    suspend fun getItems(
        @Url url: String,
        @Header(HEADER_TOKEN) token: String?,
        @Query("ParentId") parentId: String,
        @Query("IncludeItemTypes") includeItemTypes: String,
        @Query("Recursive") recursive: Boolean = false,
        @Query("Fields") fields: String = FIELD_ITEM,
        @Query("SortBy") sortBy: String? = null,
        @Query("SortOrder") sortOrder: String? = null,
        @Query("Limit") limit: Int? = null,
        @Query("ImageTypeLimit") imageTypeLimit: Int? = 1,
        @Query("EnableImageTypes") enableImageTypes: String = "Primary"
    ): JellyfinItemsData

    @GET
    suspend fun searchItems(
        @Url url: String,
        @Header(HEADER_TOKEN) token: String?,
        @Query("searchTerm") searchTerm: String,
        @Query("IncludeItemTypes") includeItemTypes: String = "Series,Movie,BoxSet",
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = FIELD_ITEM,
        @Query("Limit") limit: Int = 40,
        @Query("ImageTypeLimit") imageTypeLimit: Int? = 1,
        @Query("EnableImageTypes") enableImageTypes: String = "Primary"
    ): JellyfinItemsData

    @GET
    suspend fun getPlaybackInfo(
        @Url url: String,
        @Header(HEADER_TOKEN) token: String?
    ): JellyfinPlaybackInfoData
}
