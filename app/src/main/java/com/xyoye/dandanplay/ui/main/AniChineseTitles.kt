package com.xyoye.dandanplay.ui.main

/** Offline bangumi-data snapshot; join only on explicit AniList IDs, never fuzzy titles. */
internal class AniChineseTitles private constructor(private val titles: Map<Int, Pair<String, String>>) {
    fun apply(batch: DiscoverBatch): DiscoverBatch = batch.copy(items = batch.items.map { item ->
        val names = titles[item.id] ?: return@map item
        item.copy(chinese = names.first, original = item.original.ifBlank { names.second })
    })

    companion object {
        fun parse(json: String): AniChineseTitles {
            val rows = AniRules.obj(AniRules.obj(AniRules.decode(json))["titles"])
            val titles = mutableMapOf<Int, Pair<String, String>>()
            rows.forEach { (key, value) ->
                val id = key.toIntOrNull() ?: return@forEach
                val names = value as? List<*> ?: return@forEach
                val chinese = (names.getOrNull(0) as? String)?.trim().orEmpty()
                val original = (names.getOrNull(1) as? String)?.trim().orEmpty()
                if (id > 0 && chinese.isNotBlank()) titles[id] = chinese to original
            }
            return AniChineseTitles(titles)
        }
    }
}
