package com.xyoye.data_component.data.jellyfin

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

/**
 * Jellyfin 媒体库数据模型
 */

@Parcelize
@JsonClass(generateAdapter = true)
data class JellyfinItemsData(
    @Json(name = "Items")
    val items: List<JellyfinItemData> = emptyList(),

    @Json(name = "TotalRecordCount")
    val totalRecordCount: Int = 0
) : Parcelable


@Parcelize
@JsonClass(generateAdapter = true)
data class JellyfinItemData(
    @Json(name = "Id")
    val id: String = "",

    @Json(name = "Name")
    val name: String = "",

    /** Series / Season / Episode / Movie / Folder / CollectionFolder / UserView */
    @Json(name = "Type")
    val type: String = "",

    @Json(name = "CollectionType")
    val collectionType: String? = null,

    @Json(name = "SeriesName")
    val seriesName: String? = null,

    @Json(name = "SeriesId")
    val seriesId: String? = null,

    @Json(name = "ParentIndexNumber")
    val parentIndexNumber: Int? = null,

    @Json(name = "IndexNumber")
    val indexNumber: Int? = null,

    @Json(name = "RunTimeTicks")
    val runTimeTicks: Long? = null,

    @Json(name = "ProductionYear")
    val productionYear: Int? = null,

    @Json(name = "ImageTags")
    val imageTags: Map<String, String>? = null,

    @Json(name = "ChildCount")
    val childCount: Int? = null,

    @Json(name = "RecursiveItemCount")
    val recursiveItemCount: Int? = null,

    @Json(name = "MediaSources")
    val mediaSources: List<JellyfinMediaSourceData>? = null,

    @Json(name = "Path")
    val path: String? = null
) : Parcelable {

    val hasPrimaryImage: Boolean get() = imageTags?.containsKey("Primary") == true
}


@Parcelize
@JsonClass(generateAdapter = true)
data class JellyfinMediaSourceData(
    @Json(name = "Id")
    val id: String = "",

    @Json(name = "Container")
    val container: String? = null,

    @Json(name = "Path")
    val path: String? = null,

    @Json(name = "RunTimeTicks")
    val runTimeTicks: Long? = null,

    @Json(name = "SupportsDirectPlay")
    val supportsDirectPlay: Boolean = false,

    @Json(name = "SupportsDirectStream")
    val supportsDirectStream: Boolean = false,

    @Json(name = "SupportsTranscoding")
    val supportsTranscoding: Boolean = false
) : Parcelable


@Parcelize
@JsonClass(generateAdapter = true)
data class JellyfinPlaybackInfoData(
    @Json(name = "MediaSources")
    val mediaSources: List<JellyfinMediaSourceData>? = null
) : Parcelable


@Parcelize
@JsonClass(generateAdapter = true)
data class JellyfinUserData(
    @Json(name = "Id")
    val id: String = "",

    @Json(name = "Name")
    val name: String = "",

    @Json(name = "ServerId")
    val serverId: String? = null
) : Parcelable


@Parcelize
@JsonClass(generateAdapter = true)
data class JellyfinSystemInfoData(
    @Json(name = "ServerName")
    val serverName: String? = null,

    @Json(name = "Version")
    val version: String? = null,

    @Json(name = "Id")
    val id: String? = null,

    @Json(name = "OperatingSystem")
    val operatingSystem: String? = null
) : Parcelable
