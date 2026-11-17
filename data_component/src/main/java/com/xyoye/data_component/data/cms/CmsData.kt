package com.xyoye.data_component.data.cms

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 苹果CMS（MacCMS v10）采集接口数据模型
 *
 * 接口形如：https://站点/api.php/provide/vod/?ac=detail&wd=关键字
 *
 * 注意：不同站点返回的类型不统一（同一个字段有时是字符串、有时是数字），
 * 所以统一按 Any 接收再转字符串，避免解析直接失败。
 */

@JsonClass(generateAdapter = true)
data class CmsVideoListData(
    @Json(name = "code")
    val code: Any? = null,

    @Json(name = "msg")
    val msg: Any? = null,

    @Json(name = "page")
    val page: Any? = null,

    @Json(name = "pagecount")
    val pageCount: Any? = null,

    @Json(name = "total")
    val total: Any? = null,

    @Json(name = "list")
    val list: List<CmsVideoData> = emptyList()
)


@JsonClass(generateAdapter = true)
data class CmsVideoData(
    @Json(name = "vod_id")
    val vodId: Any? = null,

    @Json(name = "vod_name")
    val vodName: Any? = null,

    @Json(name = "vod_pic")
    val vodPic: Any? = null,

    @Json(name = "vod_remarks")
    val vodRemarks: Any? = null,

    @Json(name = "type_name")
    val typeName: Any? = null,

    @Json(name = "vod_year")
    val vodYear: Any? = null,

    @Json(name = "vod_area")
    val vodArea: Any? = null,

    @Json(name = "vod_time")
    val vodTime: Any? = null,

    @Json(name = "vod_play_from")
    val playFrom: Any? = null,

    @Json(name = "vod_play_url")
    val playUrl: Any? = null,

    @Json(name = "vod_content")
    val content: Any? = null
) {

    val id: String get() = vodId.asText()

    val name: String get() = vodName.asText()

    val pic: String get() = vodPic.asText()

    val remarks: String get() = vodRemarks.asText()

    val typeNameText: String get() = typeName.asText()

    val year: String get() = vodYear.asText()

    val area: String get() = vodArea.asText()

    val updateTime: String get() = vodTime.asText()

    val playFromText: String get() = playFrom.asText()

    val playUrlText: String get() = playUrl.asText()

    val contentText: String get() = content.asText()

    /**
     * 列表副标题：类型 · 地区 · 年份 · 更新备注
     */
    val subtitle: String
        get() = listOf(typeNameText, area, year, remarks)
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
}


private fun Any?.asText(): String {
    return when (this) {
        null -> ""
        is String -> this
        // Moshi 把 JSON 数字解析成 Double，整数要还原成不带 .0 的形式
        is Double -> if (this % 1.0 == 0.0) this.toLong().toString() else this.toString()
        is Float -> if (this % 1f == 0f) this.toLong().toString() else this.toString()
        else -> this.toString()
    }
}
