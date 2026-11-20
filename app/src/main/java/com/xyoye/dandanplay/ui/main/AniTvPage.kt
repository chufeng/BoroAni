package com.xyoye.dandanplay.ui.main

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.dandanplay.R
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.source.VideoSourceManager
import com.xyoye.common_component.source.factory.StorageVideoSourceFactory
import com.xyoye.common_component.storage.impl.LinkStorage
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.player_component.ui.activities.player.PlayerActivity
import kotlinx.coroutines.*

internal class AniTvPage(private val activity: MainActivity, private val exit: () -> Unit) {
    private val repository = AniRepository(activity)
    private val searcher = AniSearch(repository::search)
    private val discover = AniDiscoverRepository(activity)
    private var sources = emptyList<AniSource>()
    private var source: AniSource? = null
    private var results = emptyList<AniItem>()
    private var episodes = emptyList<AniItem>()
    private var subject: AniItem? = null
    private var metadata: DiscoverSubject? = null
    private var page = "discover"
    private var kind = "season"
    private var season = AniDiscoverRules.currentSeason()
    private var followCurrentSeason = true
    private var weekday = AniDiscoverRules.today()
    private var items = emptyList<DiscoverSubject>()
    private var loadedPages = 0
    private var hasMore = false
    private var notice = ""
    private var message = ""
    private var keyword = ""
    private var url = repository.subscriptionUrl()
    private var job: Job? = null
    private var active = true
    private var searchReturn = "discover"
    private var pickerReturn = "search"
    private var searched = false
    private var lastSearch = ""
    private var searchHistory = ""
    private var focusSearchResult = false
    private var retry: (() -> Unit)? = null
    private val focus = mutableMapOf<String, Int>()
    private var controls = emptyList<View>()
    private var navigationControls = emptyList<View>()
    private var activeEditor: TvSearchEditText? = null
    private var renderedPage: String? = null
    private var renderPending = false

    fun start() { loadList(false, false) }
    fun close() {
        active = false
        job?.cancel()
        TvDesign.releaseImageMemory("close")
        TvDesign.cleanupPosterDisk(activity, force = true)
    }
    fun resume() {
        if (!active) return
        if (page == "discover" && followCurrentSeason && season != AniDiscoverRules.currentSeason() && job?.isActive != true) {
            loadList(false, false)
            return
        }
        if (activity.currentFocus != null) return
        val saved = focus[page] ?: -1
        if (saved < 0) navigationControls.getOrNull(-saved - 1)?.requestFocus()
        else controls.getOrNull(saved)?.requestFocus()
    }
    fun back(): Boolean {
        if (activeEditor?.finishEditing() == true) return true
        job?.cancel(); job = null; retry = null
        val parent = AniDiscoverRules.parent(page, if (kind == "season") "discover" else "catalog", searchReturn, pickerReturn)
        if (parent == null) { close(); exit(); return true }
        if (parent == "discover" && page != "metadata" && kind != "season") { openCatalog("season"); return true }
        page = parent; message = ""; render(); return true
    }
    private fun work(label: String, action: suspend () -> Unit) {
        job?.cancel(); message = label
        retry = { work(label, action) }
        job = activity.lifecycleScope.launch(start = CoroutineStart.LAZY) {
            try { withTimeout(60000) { action() }; message = ""; retry = null }
            catch (e: TimeoutCancellationException) { message = "操作超时，请重试或返回" }
            catch (e: CancellationException) { throw e }
            catch (e: Throwable) { message = e.message ?: "操作失败（${e.javaClass.simpleName}），请重试" }
            finally { if (active && currentCoroutineContext().isActive) { job = null; render() } }
        }
        job?.start(); render()
    }
    private suspend fun loadSources() {
        sources = repository.load()
        source = sources.firstOrNull { it.id == repository.selected() && it.unsupported == null && repository.enabled(it) }
            ?: sources.firstOrNull { it.unsupported == null && repository.enabled(it) }
    }
    private fun loadList(more: Boolean, refresh: Boolean) {
        if (!more) {
            TvDesign.releaseImageMemory("catalog_reload")
            if (kind == "season" && followCurrentSeason) season = AniDiscoverRules.currentSeason()
            loadedPages = 0; hasMore = false; items = emptyList(); notice = ""
        }
        work("正在加载番剧…") {
            val page = loadedPages + 1
            val result = when (kind) {
                "calendar" -> discover.anilistSchedule(weekday, page, refresh)
                "rank" -> discover.anilistRank(page, refresh)
                else -> discover.anilistSeason(season, page, refresh)
            }
            items = (if (more) items + result.value.items else result.value.items).distinctBy { it.id }
            loadedPages = page; hasMore = result.value.hasNextPage && loadedPages < 500; notice = result.notice
        }
    }
    private fun openCatalog(value: String) {
        job?.cancel(); kind = value; page = if (value == "season") "discover" else "catalog"
        focus[page] = when (value) { "season" -> -1; "calendar" -> -2; else -> -3 }
        loadList(false, false)
    }
    private fun openMetadata(item: DiscoverSubject) {
        job?.cancel(); job = null; retry = null
        metadata = item; page = "metadata"; focus[page] = 0
        message = ""
        render()
    }
    private fun openSearch(from: String, title: String = keyword) {
        searchReturn = from; keyword = title; results = emptyList(); searched = false; lastSearch = ""; searchHistory = ""; page = "search"; focus[page] = 0
        work("读取已启用 Ani 源") { loadSources() }
        // 自动搜索由独立按钮触发，避免加载源与搜索并发。
    }
    private fun search() {
        if (keyword.isBlank()) { message = "请先输入番剧关键词"; render(); return }
        val selected = source
        if (selected == null || !repository.enabled(selected)) { message = "尚未选择可用 Ani 源，请选择源或到源管理导入订阅"; render(); return }
        val query = keyword.trim(); results = emptyList(); searched = false; searchHistory = ""; lastSearch = "${selected.name} ·「$query」"
        work("正在搜索 $lastSearch") {
            performSearch(selected, query, false)
        }
    }
    private suspend fun performSearch(selected: AniSource, query: String, shortFirst: Boolean) {
        val alternate = metadata?.takeIf { searchReturn == "metadata" && it.chinese.isNotBlank() && query == it.searchTitle(false) }
            ?.searchTitle(true)?.takeIf { it.isNotBlank() && it != query }
        val outcome = searcher.find(sources.filter { repository.enabled(it) }, selected, query, shortFirst, listOfNotNull(alternate)) { entry, word ->
            lastSearch = "${entry.name} ·「$word」"
            message = "正在搜索 $lastSearch"
            render()
        }
        results = outcome.items
        outcome.source?.let { matched ->
            source = matched; repository.select(matched)
            lastSearch = "${matched.name} ·「${outcome.keyword}」"
        }
        searchHistory = outcome.attempts.joinToString("\n") { attempt ->
            "${attempt.source.name} ·「${attempt.keyword}」：${attempt.error?.let { "失败（$it）" } ?: "${attempt.count} 条"}"
        }
        searched = true
        focusSearchResult = results.isNotEmpty()
        focus["search"] = 1
    }
    private fun searchMetadata() {
        val item = metadata ?: return
        searchReturn = "metadata"; keyword = item.searchTitle(false); results = emptyList(); searched = false; searchHistory = ""
        page = "search"; focus[page] = 0
        work("用片名在所选 Ani 源搜索") {
            loadSources()
            val selected = source ?: error("没有已启用的受支持 Ani 源；请到源管理导入或启用")
            require(keyword.isNotBlank()) { "条目没有可搜索片名，请编辑关键词" }
            performSearch(selected, keyword.trim(), true)
        }
    }
    private fun render() {
        if (!active) return
        if (renderedPage == page && activeEditor?.isEditing == true) {
            renderPending = true
            return
        }
        if (isPosterGrid(renderedPage) != isPosterGrid(page)) {
            TvDesign.releaseImageMemory("poster_transition")
        }
        renderPending = false
        activeEditor = null
        renderedPage = page
        try {
            renderUnsafe()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            android.util.Log.e("AniTvPage", "render failed at page=$page kind=$kind", e)
            runCatching {
                val fallback = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(48, 48, 48, 48)
                    setBackgroundColor(TvDesign.paper)
                }
                fallback.addView(TvDesign.text(activity, "界面加载出错", 34f, true))
                fallback.addView(TvDesign.text(activity, e.message ?: e.javaClass.simpleName, 22f))
                val retry = TvDesign.button(activity, "返回发现首页重试") { page = "discover"; kind = "season"; render() }
                TvDesign.focus(retry)
                fallback.addView(retry, LinearLayout.LayoutParams(-1, -2))
                activity.setContentView(fallback)
                retry.requestFocus()
            }
        }
    }
    private fun isPosterGrid(value: String?): Boolean = value == "discover" || value == "catalog"

    private fun renderUnsafe() {
        if (!active) return
        fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
        val shell = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(32), dp(24), dp(32), dp(20)); setBackgroundColor(TvDesign.paper); clipChildren = true }
        val rail = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = true }
        shell.addView(ScrollView(activity).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; addView(rail) }, LinearLayout.LayoutParams(dp(148), -1).apply { rightMargin = dp(28) })
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = true }
        shell.addView(root, LinearLayout.LayoutParams(0, -1, 1f))
        rail.addView(TvDesign.brand(activity))
        val navigation = mutableListOf<View>()
        val rows = mutableListOf<List<View>>()
        val views = mutableListOf<View>()
        fun nav(label: String, icon: Int, selected: Boolean = false, action: () -> Unit) {
            val view = TvDesign.destination(activity, label, icon, selected, action)
            rail.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
            navigation.add(view)
        }
        val inCatalog = page in setOf("discover", "catalog", "metadata")
        nav("新番", R.drawable.ic_main_home, inCatalog && kind == "season") { openCatalog("season") }
        nav("日历", android.R.drawable.ic_menu_my_calendar, inCatalog && kind == "calendar") { openCatalog("calendar") }
        nav("榜单", android.R.drawable.btn_star_big_on, inCatalog && kind == "rank") { openCatalog("rank") }
        nav("搜索", android.R.drawable.ic_menu_search, page in setOf("search", "picker", "episodes")) { openSearch("discover") }
        nav("源设置", android.R.drawable.ic_menu_preferences, page == "sources") { page = "sources"; work("读取本地订阅") { loadSources() } }
        rail.addView(TvDesign.divider(activity))
        nav("主菜单", R.drawable.ic_main_media) { close(); exit() }
        val title = when (page) {
            "discover" -> "新番"
            "catalog" -> if (kind == "calendar") "放送日历" else "高分榜"
            "metadata" -> "番剧详情"
            "search" -> "搜索番剧"
            "picker" -> "选择来源"
            "sources" -> "源设置"
            else -> "选集"
        }
        root.addView(TvDesign.text(activity, title, 28f, true), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = true; setPadding(dp(2), 0, dp(8), dp(16)) }
        root.addView(ScrollView(activity).apply { isFillViewport = true; clipChildren = true; isVerticalScrollBarEnabled = false; addView(list) }, LinearLayout.LayoutParams(-1,0,1f))
        fun text(value: String, size: Float = 16f, muted: Boolean = true, target: LinearLayout = list, gap: Int = 10) {
            if (value.isBlank()) return
            target.addView(TvDesign.text(activity, value, size, !muted).apply {
                if (muted) setTextColor(TvDesign.muted)
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(gap) })
        }
        fun actionView(label: String, flat: Boolean, primary: Boolean, selected: Boolean, action: () -> Unit): TextView {
            val index = views.size
            return TvDesign.button(activity, label, flat, primary) { focus[page] = index; action() }.apply {
                isSelected = selected
                views.add(this)
            }
        }
        fun actions(target: LinearLayout = list, build: (LinearLayout, MutableList<View>) -> Unit) {
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            val members = mutableListOf<View>()
            build(row, members)
            if (members.isEmpty()) return
            rows.add(members)
            target.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        fun chip(row: LinearLayout, members: MutableList<View>, label: String, selected: Boolean = false, primary: Boolean = false, weight: Float = 0f, action: () -> Unit) {
            val view = actionView(label, false, primary, selected, action)
            members.add(view)
            row.addView(view, LinearLayout.LayoutParams(if (weight > 0) 0 else -2, -2, weight).apply { rightMargin = dp(8) })
        }
        fun button(label: String, action: () -> Unit) {
            val view = actionView(label, flat = true, primary = false, selected = false, action = action)
            view.gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
            rows.add(listOf(view))
            list.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(3) })
        }
        fun information(title: String, info: String) {
            AlertDialog.Builder(activity).setTitle(title).setMessage(info).setPositiveButton("关闭", null).show()
        }
        fun changeSeason(quarter: DiscoverSeason, followCurrent: Boolean = false) {
            season = quarter
            followCurrentSeason = followCurrent
            loadList(false, false)
        }
        val busy = job?.isActive == true
        if (message.isNotBlank()) text(message, gap = 6)
        fun input(value: String, hintText: String, changed: (String) -> Unit): TvSearchEditText = TvSearchEditText(activity).apply {
            setSingleLine(); setText(value); hint = hintText; textSize = 19f; setSelectAllOnFocus(true); inputType = android.text.InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            TvDesign.focus(this); setHintTextColor(TvDesign.muted); setPadding(dp(16), 0, dp(16), 0); textSize = 22f
            activeEditor = this
            onEditingEnded = {
                val editor = this
                // A background load can finish while the user is choosing characters.
                post { if (activeEditor === editor && renderPending) render() }
            }
            views.add(this); rows.add(listOf(this)); list.addView(this, LinearLayout.LayoutParams(-1,dp(52)).apply { bottomMargin = dp(12) })
        }
        when (page) {
            "discover", "catalog" -> {
                when (kind) {
                    "season" -> {
                        val current = AniDiscoverRules.currentSeason()
                        text("${season.label}  ·  ${season.months}", 21f, false, gap = 6)
                        actions { row, members ->
                            chip(row, members, "‹  上一季") { changeSeason(season.shift(-1)) }
                            chip(row, members, "本季", selected = season == current) { changeSeason(current, true) }
                            chip(row, members, "下一季  ›") { changeSeason(season.shift(1)) }
                            chip(row, members, "刷新") { loadList(false, true) }
                        }
                    }
                    "calendar" -> {
                        actions { row, members ->
                            (1..7).forEach { day ->
                                val label = if (day == AniDiscoverRules.today()) "今天" else "周${"一二三四五六日"[day - 1]}"
                                chip(row, members, label, selected = day == weekday, weight = 1f) { weekday = day; loadList(false, false) }
                            }
                        }
                    }
                    else -> text("AniList  ·  TV 动画评分排行", gap = 12)
                }
                if (items.isEmpty() && !busy && message.isBlank()) text("暂无番剧", 19f)
                val widthDp = activity.resources.configuration.screenWidthDp
                val columns = if (widthDp >= 1100) 6 else if (widthDp >= 850) 5 else 3
                items.chunked(columns).forEach { group ->
                    val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = true }
                    val members = mutableListOf<View>()
                    list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })
                    group.forEach { item ->
                        val index = views.size
                        val card = LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL; setPadding(dp(5), dp(5), dp(5), dp(7)); clipChildren = true
                            contentDescription = item.title + "，确认查看详情"
                            TvDesign.focus(this, flat = true)
                            addView(TvDesign.poster(activity, item.imageUrl, item.title, "anilist:${item.id}"), LinearLayout.LayoutParams(-1, -2))
                            addView(TvDesign.text(activity, item.title, 16f, true).apply { setPadding(dp(2), dp(7), dp(2), dp(2)) })
                            val caption = listOfNotNull(
                                item.rank.takeIf { kind == "rank" && it > 0 }?.let { "#${it}" },
                                if (kind != "calendar" && item.score > 0) "★ ${item.score}" else item.date.takeIf { it.isNotBlank() }
                            ).joinToString("  ·  ")
                            if (caption.isNotBlank()) addView(TvDesign.text(activity, caption, 14f).apply {
                                setTextColor(if (kind == "rank") TvDesign.rating else TvDesign.muted)
                                setPadding(dp(2), 0, dp(2), 0)
                            })
                            setOnClickListener { focus[page] = index; openMetadata(item) }
                        }
                        views.add(card); members.add(card)
                        row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
                    }
                    repeat(columns - group.size) { row.addView(Space(activity), LinearLayout.LayoutParams(0, 1, 1f).apply { rightMargin = dp(8) }) }
                    rows.add(members)
                }
                if (!busy) actions { row, members ->
                    if (hasMore) chip(row, members, "加载更多") { loadList(true, false) }
                    if (kind != "season") chip(row, members, "刷新") { loadList(false, true) }
                    if (notice.isNotBlank()) chip(row, members, "资料来源") {
                        information("资料来源", "$notice\n\n译名：bangumi-data · CC BY 4.0 · 2026-11-17 快照。片名优先中文，缺译名时显示日语原名。")
                    }
                }
            }
            "metadata" -> {
                val item = metadata
                if (item != null) {
                    val detail = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                    detail.addView(TvDesign.poster(activity, item.imageUrl, item.title, "anilist:${item.id}"), LinearLayout.LayoutParams(dp(148), dp(222)).apply { rightMargin = dp(22) })
                    val details = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    detail.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                    text(item.title, 26f, false, details, 4)
                    if (item.original != item.title) text(item.original, 17f, target = details)
                    text(listOfNotNull(item.date.takeIf { it.isNotBlank() }, item.score.takeIf { it > 0 }?.let { "★ $it" }).joinToString("   ·   "), target = details)
                    text(item.summary, 17f, target = details, gap = 14)
                    actions(details) { row, members ->
                        chip(row, members, "搜索资源", primary = true) { searchMetadata() }
                        chip(row, members, "编辑关键词") { openSearch("metadata", item.searchTitle(false)) }
                    }
                    list.addView(detail, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
                }
                actions { row, members -> chip(row, members, "返回列表") { back() } }
            }
            "search" -> {
                val editor = input(keyword, "番剧名称") { keyword = it }
                editor.setOnEditorActionListener { _, action, event ->
                    val enterPressed = event?.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
                        (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                    if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE || enterPressed) {
                        editor.finishEditing()
                        search()
                        true
                    } else {
                        false
                    }
                }
                actions { row, members ->
                    chip(row, members, "搜索", primary = true) { search() }
                    chip(row, members, "换源") { pickerReturn = "search"; page = "picker"; focus[page] = 0; work("读取来源…") { loadSources() } }
                    val names = metadata?.takeIf { searchReturn == "metadata" && it.chinese.isNotBlank() && it.original.isNotBlank() && it.chinese != it.original }
                    if (names != null) {
                        chip(row, members, "中文", selected = keyword == names.searchTitle(false)) { keyword = names.searchTitle(false); render() }
                        chip(row, members, "原名", selected = keyword == names.searchTitle(true)) { keyword = names.searchTitle(true); render() }
                    }
                }
                text("来源：${source?.name ?: "未选择"}")
                if (searched) {
                    text(if (results.isEmpty()) "没有匹配结果" else "搜索结果  ·  ${results.size} 条", 20f, false)
                    if (searchHistory.isNotBlank()) actions { row, members ->
                        chip(row, members, "搜索记录") { information("搜索记录", searchHistory) }
                    }
                }
                if (focusSearchResult && results.isNotEmpty()) {
                    focus["search"] = views.size
                    focusSearchResult = false
                }
                results.forEach { item -> button(item.name) {
                    subject = item
                    work("获取当前源详情与选集") { episodes = repository.episodes(source ?: error("请重新选源"), item); require(episodes.isNotEmpty()) { "当前源未解析出剧集，请返回换源或重试；不支持动态脚本/验证页面" }; page = "episodes"; focus[page] = 0 }
                } }
            }
            "picker" -> {
                val available = sources.filter { it.unsupported == null && repository.enabled(it) }
                if (available.isEmpty() && !busy) text("暂无已启用的来源")
                available.forEach { entry -> button("${entry.name}${if (entry.id == source?.id) "  ·  当前" else ""}") {
                    source = entry; repository.select(entry); results = emptyList(); searched = false; page = pickerReturn; render()
                } }
                actions { row, members -> chip(row, members, "源设置") { page = "sources"; focus[page] = 0; render() } }
            }
            "sources" -> {
                text("Ani 订阅", 20f, false)
                input(url, "订阅地址") { url = it }
                actions { row, members ->
                    chip(row, members, "导入订阅", primary = true) { work("正在导入订阅…") { repository.import(url); loadSources() } }
                    chip(row, members, "导入默认订阅") { url = AniRules.DEFAULT_SUB; work("正在导入订阅…") { repository.import(url); loadSources() } }
                    chip(row, members, "关于来源") {
                        information("关于来源", "Ani 订阅提供搜索与解析规则；仅已启用、受支持的来源可用于搜索。\n\n番剧资料：AniList。中文译名：bangumi-data（CC BY 4.0）。")
                    }
                }
                if (sources.isNotEmpty()) text("来源列表", 20f, false)
                sources.forEach { entry ->
                    val enabled = repository.enabled(entry)
                    val status = when {
                        entry.unsupported != null -> "不支持"
                        repository.selected() == entry.id && enabled -> "当前来源"
                        enabled -> "已启用"
                        else -> "已停用"
                    }
                    actions { row, members ->
                        row.addView(LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(TvDesign.text(activity, entry.name, 18f, true))
                            addView(TvDesign.text(activity, status, 14f).apply { setTextColor(TvDesign.muted) })
                        }, LinearLayout.LayoutParams(0, -2, 1f))
                        if (entry.unsupported != null) chip(row, members, "详情") { information(entry.name, entry.unsupported) }
                        else {
                            if (enabled) chip(row, members, "选择") { repository.select(entry); source = entry; openSearch("discover") }
                            chip(row, members, if (enabled) "停用" else "启用") { repository.toggle(entry); render() }
                        }
                    }
                }
            }
            "episodes" -> {
                text(subject?.name.orEmpty(), 24f, false)
                fun play(episode: AniItem) {
                    work("正在连接媒体…") {
                        val (link, headers) = repository.resolve(source ?: error("请重新选源"), episode)
                        val video = withContext(Dispatchers.IO) {
                            val storage = LinkStorage(MediaLibraryEntity.STREAM.copy(url = link))
                            storage.setupHttpHeader(headers)
                            StorageVideoSourceFactory.create(storage.getRootFile()) ?: error("播放器初始化失败")
                        }
                        com.xyoye.player.utils.VlcProxyServer.getInstance().configureClient(
                            AniTls.configure(okhttp3.OkHttpClient.Builder(), activity)
                                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                                .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
                                .followSslRedirects(false).build()
                        )
                        VideoSourceManager.getInstance().setSource(video)
                        val playbackTitle = listOfNotNull(subject?.name, episode.name)
                            .filter { it.isNotBlank() }.distinct().joinToString(" · ")
                        ARouter.getInstance().build(RouteTable.Player.Player)
                            .withBoolean("ani_tv_playback", true)
                            .withString(PlayerActivity.EXTRA_PLAYBACK_TITLE, playbackTitle)
                            .navigation(activity)
                    }
                }
                val columns = if (activity.resources.configuration.screenWidthDp >= 850) 5 else 3
                episodes.groupBy { it.channel }.forEach { (channel, entries) ->
                    text(channel, 17f)
                    entries.chunked(columns).forEach { group ->
                        actions { row, members ->
                            group.forEach { episode -> chip(row, members, episode.name, weight = 1f) { play(episode) } }
                            repeat(columns - group.size) { row.addView(Space(activity), LinearLayout.LayoutParams(0, 1, 1f).apply { rightMargin = dp(8) }) }
                        }
                    }
                }
            }
        }
        if (busy) actions(root) { row, members ->
            chip(row, members, "取消") { job?.cancel(); job = null; retry = null; message = "已取消"; render() }
        } else retry?.let { action -> actions(root) { row, members -> chip(row, members, "重试") { action() } } }
        TvDesign.wire(rows, navigation)
        val renderedPage = page
        fun trackFocus(view: View) {
            val existing = view.onFocusChangeListener
            view.setOnFocusChangeListener { current, hasFocus ->
                existing?.onFocusChange(current, hasFocus)
                if (!hasFocus) return@setOnFocusChangeListener
                val index = views.indexOf(current)
                val railIndex = navigation.indexOf(current)
                if (index >= 0) focus[renderedPage] = index
                else if (railIndex >= 0) focus[renderedPage] = -railIndex - 1
            }
        }
        views.forEach(::trackFocus)
        navigation.forEach(::trackFocus)
        val saved = focus[page] ?: -1
        controls = views
        navigationControls = navigation
        activity.setContentView(shell)
        if (saved < 0) navigation.getOrNull(-saved - 1)?.requestFocus()
        else views.getOrNull(saved.coerceAtMost(views.lastIndex))?.requestFocus()
    }
}
