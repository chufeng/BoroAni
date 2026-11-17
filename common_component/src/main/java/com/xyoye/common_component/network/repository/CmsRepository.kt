package com.xyoye.common_component.network.repository

import com.xyoye.common_component.network.Retrofit

/**
 * 苹果CMS 采集接口仓库
 */

object CmsRepository : BaseRepository() {

    private fun buildHeaders(userAgent: String?, referer: String?): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        if (!userAgent.isNullOrBlank()) {
            headers["User-Agent"] = userAgent
        }
        if (!referer.isNullOrBlank()) {
            headers["Referer"] = referer
        }
        return headers
    }

    /**
     * 最新更新
     */
    suspend fun latest(
        apiUrl: String,
        userAgent: String?,
        referer: String?,
        page: Int = 1
    ) = request()
        .doGet {
            Retrofit.cmsService.query(
                apiUrl.trim(),
                mapOf("ac" to "detail", "pg" to page.toString()),
                buildHeaders(userAgent, referer)
            )
        }

    /**
     * 关键字搜索
     */
    suspend fun search(
        apiUrl: String,
        userAgent: String?,
        referer: String?,
        keyword: String
    ) = request()
        .doGet {
            Retrofit.cmsService.query(
                apiUrl.trim(),
                mapOf("ac" to "detail", "wd" to keyword),
                buildHeaders(userAgent, referer)
            )
        }

    /**
     * 按 ID 取详情（选集列表）
     */
    suspend fun detail(
        apiUrl: String,
        userAgent: String?,
        referer: String?,
        videoId: String
    ) = request()
        .doGet {
            Retrofit.cmsService.query(
                apiUrl.trim(),
                mapOf("ac" to "detail", "ids" to videoId),
                buildHeaders(userAgent, referer)
            )
        }
}
