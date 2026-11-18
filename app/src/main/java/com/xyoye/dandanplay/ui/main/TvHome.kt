package com.xyoye.dandanplay.ui.main

import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.dandanplay.R
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.enums.MediaType
import com.xyoye.user_component.ui.dialog.DeveloperAuthenticateDialog

internal class TvHome(private val activity: MainActivity) {
    private var aniPage: AniTvPage? = null
    private var page = "home"
    private var libraries = emptyList<MediaLibraryEntity>()
    private var restoreIndex = 0
    private var controls = emptyList<View>()
    private var dialog: android.app.Dialog? = null

    fun start() {
        render()
        DatabaseManager.instance.getMediaLibraryDao().getAll().observe(activity) {
            libraries = it.toList()
            if (page == "sources" && aniPage == null) render()
        }
        aniPage = AniTvPage(activity) { aniPage = null; render() }.also { it.start() }
    }

    fun back(): Boolean {
        aniPage?.let { return it.back() }
        if (page == "home") return false
        page = "home"
        restoreIndex = 0
        render()
        return true
    }

    fun close() { aniPage?.close(); dialog?.dismiss() }

    private fun render() {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val shell = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(32), dp(24), dp(32), dp(20))
            setBackgroundColor(TvDesign.paper)
            clipChildren = true
        }
        val rail = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = true }
        shell.addView(ScrollView(activity).apply { isVerticalScrollBarEnabled = false; addView(rail) }, LinearLayout.LayoutParams(dp(148), -1).apply { rightMargin = dp(28) })
        rail.addView(TvDesign.brand(activity))
        val navigation = mutableListOf<View>()
        fun nav(label: String, icon: Int, selected: Boolean, action: () -> Unit) {
            val view = TvDesign.destination(activity, label, icon, selected, action)
            navigation.add(view)
            rail.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        }
        nav("主菜单", R.drawable.ic_main_home, page == "home") { page = "home"; restoreIndex = 0; render() }
        nav("发现番剧", android.R.drawable.ic_media_play, false) {
            aniPage = AniTvPage(activity) { aniPage = null; render() }.also { it.start() }
        }
        nav("媒体库", R.drawable.ic_main_media, page == "sources") { page = "sources"; restoreIndex = 2; render() }
        nav("设置", android.R.drawable.ic_menu_preferences, page == "settings") { page = "settings"; restoreIndex = 3; render() }
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = true }
        shell.addView(root, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(TextView(activity).apply {
            text = when (page) {
                "sources" -> "媒体库"
                "settings" -> "设置"
                else -> "主菜单"
            }
            textSize = 28f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(TvDesign.ink)
            setPadding(0, 0, 0, dp(12))
        })
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = true; setPadding(dp(6), dp(6), dp(6), dp(12)) }
        root.addView(ScrollView(activity).apply {
            isFillViewport = true
            addView(list)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        val buttons = mutableListOf<View>()
        fun button(label: String, action: () -> Unit) {
            val index = navigation.size + buttons.size
            val view = TvDesign.button(activity, label, flat = true) { restoreIndex = index; action() }.apply {
                gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
            }
            buttons.add(view)
            list.addView(view, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            })
        }
        when (page) {
            "sources" -> {
                button("＋ 新增 CMS 在线源") { addSource(MediaType.CMS_SOURCE) }
                button("＋ 新增 Jellyfin 媒体库") { addSource(MediaType.JELLYFIN_SERVER) }
                libraries.filter { it.mediaType == MediaType.CMS_SOURCE || it.mediaType == MediaType.JELLYFIN_SERVER }.forEach { library ->
                    val type = if (library.mediaType == MediaType.CMS_SOURCE) "CMS" else "Jellyfin"
                    button("[$type] ${library.displayName}") {
                        ARouter.getInstance().build(RouteTable.Stream.StorageFile)
                            .withParcelable("storageLibrary", library).navigation(activity)
                    }
                    button("编辑 [$type] ${library.displayName}") { addSource(library.mediaType, library) }
                }
                button("返回电视首页") { back() }
            }
            "settings" -> {
                button("弹弹play 官方 API 凭证（可选）") {
                    dialog = DeveloperAuthenticateDialog(activity) {}.also { it.show() }
                }
                button("凭证说明") {
                    dialog = AlertDialog.Builder(activity)
                        .setTitle("官方 API 凭证")
                        .setMessage("仅使用弹弹play官方API的功能需要自行申请并配置AppID和AppSecret。电视首页不会自动调用番剧首页、云过滤或自动登录。Ani入口支持官方Selector静态规则子集；动态JS、验证码、DRM不支持。观看视频不需要官方API凭据，弹幕等官方API功能可选。")
                        .setPositiveButton("返回", null).create().also { it.show() }
                }
                button("返回电视首页") { back() }
            }
            else -> {
                button("发现番剧") {
                    aniPage = AniTvPage(activity) { aniPage = null; render() }.also { it.start() }
                }
                button("媒体库") { page = "sources"; restoreIndex = 2; render() }
                button("设置") { page = "settings"; restoreIndex = 3; render() }
                button("返回 / 退出") { activity.onBackPressed() }
            }
        }
        TvDesign.wire(buttons.map { listOf(it) }, navigation)
        controls = navigation + buttons
        val saved = restoreIndex
        shell.viewTreeObserver.addOnGlobalFocusChangeListener { _, current ->
            val index = controls.indexOf(current)
            if (index >= 0) restoreIndex = index
        }
        activity.setContentView(shell)
        controls.getOrNull(saved.coerceIn(0, controls.lastIndex))?.requestFocus()
    }

    fun resume() {
        aniPage?.let { it.resume(); return }
        if (activity.currentFocus == null) controls.getOrNull(restoreIndex)?.requestFocus()
    }

    private fun addSource(type: MediaType, library: MediaLibraryEntity? = null) {
        ARouter.getInstance().build(RouteTable.Stream.StoragePlus)
            .withSerializable("mediaType", type)
            .withParcelable("editData", library).navigation(activity)
    }
}
