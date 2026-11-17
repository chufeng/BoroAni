package com.xyoye.common_component.network.service

import com.xyoye.data_component.data.cms.CmsVideoListData
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.QueryMap
import retrofit2.http.Url

/**
 * 苹果CMS（MacCMS v10）采集接口
 *
 * 同一个接口通过 ac=detail 区分用途：
 *   ac=detail                 → 最新更新（分页 pg）
 *   ac=detail&wd=关键字        → 搜索
 *   ac=detail&ids=视频ID       → 详情（含选集列表）
 */

interface CmsService {

    @GET
    suspend fun query(
        @Url url: String,
        @QueryMap params: Map<String, String>,
        @HeaderMap headers: Map<String, String>
    ): CmsVideoListData
}
