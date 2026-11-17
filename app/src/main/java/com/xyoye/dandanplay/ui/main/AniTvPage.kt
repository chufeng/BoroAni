package com.xyoye.dandanplay.ui.main

import android.graphics.Color
import android.view.View
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.source.VideoSourceManager
import com.xyoye.common_component.source.factory.StorageVideoSourceFactory
import com.xyoye.common_component.storage.impl.LinkStorage
import com.xyoye.common_component.utils.TvUi
import com.xyoye.data_component.entity.MediaLibraryEntity
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
    private val season = AniDiscoverRules.currentSeason()
    private var month = season.month
    private var weekday = AniDiscoverRules.today()
    private var items = emptyList<DiscoverSubject>()
    private var offset = 0
    private var total = 0
    private var notice = ""
    private var message = "选择条目查看简介，再用片名搜索资源源。榜单不代表源内有资源。"
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

    fun start() { render(); loadList(false, false) }
    fun close() { active = false; job?.cancel() }
    fun resume() {
        if (!active || activity.currentFocus != null) return
        val saved = focus[page] ?: -1
        if (saved < 0) navigationControls.getOrNull(-saved - 1)?.requestFocus()
        else controls.getOrNull(saved)?.requestFocus()
    }
    fun back(): Boolean {
        job?.cancel(); job = null; retry = null
        val parent = AniDiscoverRules.parent(page, if (kind == "season") "discover" else "catalog", searchReturn, pickerReturn)
        if (parent == null) { close(); exit(); return true }
        if (parent == "discover" && page != "metadata" && kind != "season") { openCatalog("season"); return true }
        page = parent; message = "方向键移动，确认选择，返回上一级。"; render(); return true
    }
    private fun work(label: String, action: suspend () -> Unit) {
        job?.cancel(); message = label
        retry = { work(label, action) }
        job = activity.lifecycleScope.launch(start = CoroutineStart.LAZY) {
            try { withTimeout(60000) { action() }; message = if (page == "discover" || page == "catalog" || page == "metadata") "资料状态见下方来源与时间；确认查看，返回上一级" else "操作完成；方向键选择下一步"; retry = null }
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
        if (!more) { offset = 0; total = 0; items = emptyList(); notice = "正在请求 AniList 新番资料（国内可直连）" }
        work("正在加载番剧资料；不搜索资源站") {
            val page = offset / 20 + 1
            val result = when (kind) {
                "calendar" -> discover.anilistSchedule(weekday, page, refresh)
                "rank" -> discover.anilistRank(page, refresh)
                else -> discover.anilistSeason(season.year, month, page, refresh)
            }
            items = (if (more) items + result.value.items else result.value.items).distinctBy { it.id }
            offset += result.value.received; total = result.value.total; notice = result.notice
        }
    }
    private fun openCatalog(value: String) {
        job?.cancel(); kind = value; page = if (value == "season") "discover" else "catalog"
        focus[page] = 0; loadList(false, false)
    }
    private fun openMetadata(item: DiscoverSubject) {
        job?.cancel(); job = null; retry = null
        metadata = item; page = "metadata"; focus[page] = 1
        message = "以下为站点公开资料；资源是否可用请搜索所选源"
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
        val outcome = searcher.find(sources.filter { repository.enabled(it) }, selected, query, shortFirst) { entry, word ->
            lastSearch = "${entry.name} ·「$word」"
            message = "正在搜索 $lastSearch；无结果将尝试短词与备用源，返回可取消"
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

    private fun renderUnsafe() {
        if (!active) return
        fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
        val shell = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(32), dp(32), dp(32), dp(32)); setBackgroundColor(TvDesign.paper); clipChildren = false }
        val rail = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        shell.addView(ScrollView(activity).apply { isFillViewport = true; clipChildren = false; addView(rail) }, LinearLayout.LayoutParams(dp(156), -1).apply { rightMargin = dp(24) })
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        shell.addView(root, LinearLayout.LayoutParams(0, -1, 1f))
        rail.addView(TvDesign.text(activity, "弹弹play", 27f, true))
        rail.addView(TvDesign.text(activity, "ANIME / TV", 16f).apply { setTextColor(TvDesign.teal) })
        val navigation = mutableListOf<View>()
        val rows = mutableListOf<List<View>>()
        val views = mutableListOf<View>()
        fun nav(label: String, selected: Boolean = false, action: () -> Unit) {
            val v = TvDesign.button(activity, (if (selected) "• " else "  ") + label, action).apply { textSize = 22f; isSelected = selected }
            rail.addView(v, LinearLayout.LayoutParams(-1, dp(60)).apply { bottomMargin = dp(8) }); navigation.add(v)
        }
        nav("发现", page == "discover") { openCatalog("season") }
        nav("新番") { openCatalog("season") }
        nav("日历", page == "catalog" && kind == "calendar") { openCatalog("calendar") }
        nav("榜单", page == "catalog" && kind == "rank") { openCatalog("rank") }
        nav("搜索", page == "search") { openSearch("discover") }
        nav("源设置", page == "sources") { page = "sources"; work("读取本地订阅") { loadSources() } }
        nav("使用帮助", page == "help") { job?.cancel(); job = null; retry = null; page = "help"; render() }
        nav("主菜单") { close(); exit() }
        fun text(value: String, size: Float = 24f, target: LinearLayout = root) {
            val chunks = if (target === root) listOf(value) else value.split('\n').flatMap { it.chunked(280) }.filter { it.isNotBlank() }
            chunks.forEach { part ->
                val view = TvDesign.text(activity, part, size.coerceAtLeast(20f))
                if (target !== root) { TvDesign.focus(view); views.add(view); rows.add(listOf(view)) }
                target.addView(view)
            }
        }
        text(when (page) { "discover" -> "发现番剧 · 当季新番"; "catalog" -> if (kind == "calendar") "每日放送 · AniList" else "榜单 · AniList 高分动画"; "metadata" -> metadata?.title ?: "条目详情"; "search" -> "搜索番剧 · ${source?.name ?: "尚未选源"}"; "picker" -> "选择已启用 Ani 源"; "sources" -> "源管理 · 订阅不是番剧目录"; "help" -> "使用帮助"; else -> "选集 · ${subject?.name}" }, 25f)
        root.addView(TvDesign.text(activity, message, 20f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; setPadding(dp(6), dp(6), dp(6), dp(12)) }
        root.addView(ScrollView(activity).apply { isFillViewport = true; clipChildren = false; addView(list) }, LinearLayout.LayoutParams(-1,0,1f))
        fun button(label: String, action: () -> Unit) {
            val index = views.size
            val view = TvDesign.button(activity, label) { focus[page] = index; action() }
            views.add(view); rows.add(listOf(view)); list.addView(view, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(10) })
        }
        fun input(value: String, hintText: String, changed: (String) -> Unit): EditText = EditText(activity).apply {
            setSingleLine(); setText(value); hint = hintText; textSize = 19f; setSelectAllOnFocus(true); inputType = android.text.InputType.TYPE_CLASS_TEXT
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            TvDesign.focus(this); setHintTextColor(TvDesign.muted); setPadding(dp(16), 0, dp(16), 0); textSize = 24f
            TvDesign.editor(this)
            views.add(this); rows.add(listOf(this)); list.addView(this, LinearLayout.LayoutParams(-1,dp(64)).apply { bottomMargin = dp(12) })
        }
        when (page) {
            "discover", "catalog" -> {
                list.addView(TvDesign.hero(activity, if (kind == "calendar") "跟着日历，遇见故事" else if (kind == "rank") "值得重温的好故事" else "今天，想看什么故事？", "发现喜欢的动画，再搜索你已启用的资源源。"), LinearLayout.LayoutParams(-1, dp(168)).apply { bottomMargin = dp(12) })
                text(notice.ifBlank { "选择季度或星期查看番剧；数据来自 AniList 公开接口" }, 20f, list)
                fun filters(entries: List<Pair<String, () -> Unit>>) {
                    entries.chunked(4).forEach { group ->
                        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
                        val members = mutableListOf<View>()
                        list.addView(row, LinearLayout.LayoutParams(-1, -2))
                        group.forEach { (label, action) ->
                            val index = views.size
                            val v = TvDesign.button(activity, label) { focus[page] = index; action() }.apply { textSize = 22f }
                            views.add(v); members.add(v)
                            row.addView(v, LinearLayout.LayoutParams(0, dp(60), 1f).apply { rightMargin = dp(8); bottomMargin = dp(12) })
                        }
                        rows.add(members)
                    }
                }
                if (kind == "season") {
                    filters((season.month..season.month + 2).map { m -> "${if (m == month) "• " else ""}${m}月新番" to { month = m; loadList(false, false) } })
                    text("${season.label}季度新番 · 按热度排序；含连载与未开播，不代表源内已有资源", 20f, list)
                } else if (kind == "calendar") {
                    filters((1..7).map { day -> "${if (day == weekday) "· " else ""}周${"一二三四五六日"[day - 1]}" to { weekday = day; loadList(false, false) } })
                    button("跳到今天 · 北京时间") { weekday = AniDiscoverRules.today(); loadList(false, false) }
                    text("AniList 当日放送表 · 按北京时间归集，时刻可能因站点时区有偏差", 20f, list)
                } else text("AniList 全站高分 TV 动画 · 按评分排序，非实时播放量统计", 20f, list)
                button("刷新 / 联网重试") { loadList(false, true) }
                if (items.isEmpty() && job?.isActive != true) {
                    list.addView(TvDesign.hero(activity, "故事还在路上", "暂无条目，请刷新、切换筛选或直接搜索。"), LinearLayout.LayoutParams(-1, dp(180)))
                }
                val columns = if (activity.resources.configuration.screenWidthDp >= 900) 4 else 3
                items.chunked(columns).forEach { group ->
                    val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
                    val members = mutableListOf<View>()
                    list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })
                    group.forEach { item ->
                        val index = views.size
                        val card = LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL; setPadding(dp(7), dp(7), dp(7), dp(10)); clipChildren = false
                            contentDescription = item.title + "，确认查看详情"
                            TvDesign.focus(this)
                            addView(TvDesign.poster(activity, item.imageUrl, item.title), LinearLayout.LayoutParams(-1, -2))
                            addView(TvDesign.text(activity, item.title, 24f, true).apply { maxLines = 2; minLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
                            addView(TvDesign.text(activity, (if (kind == "rank" && item.rank > 0) "#${item.rank}  ·  " else "") + if (item.score > 0) "评分 ${item.score}" else item.date.ifBlank { "首播未提供" }, 20f))
                            setOnClickListener { focus[page] = index; openMetadata(item) }
                        }
                        views.add(card); members.add(card)
                        row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(12) })
                    }
                    repeat(columns - group.size) { row.addView(Space(activity), LinearLayout.LayoutParams(0, 1, 1f).apply { rightMargin = dp(12) }) }
                    rows.add(members)
                }
                text("已展示 ${items.size} 条 / 接口总数 $total（可能含过滤或重复条目）", 20f, list)
                if (kind != "calendar" && offset < total && offset < 10000) button("加载更多 · 每页20条") { loadList(true, false) }
            }
            "metadata" -> {
                val item = metadata
                if (item != null) {
                    val detail = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                    detail.addView(TvDesign.poster(activity, item.imageUrl, item.title), LinearLayout.LayoutParams(dp(160), dp(240)).apply { rightMargin = dp(20) })
                    detail.addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(TvDesign.text(activity, item.title, 34f, true))
                        addView(TvDesign.text(activity, item.original, 24f))
                        addView(TvDesign.text(activity, "首播/更新 ${item.date.ifBlank { "未提供" }}" + if (item.score > 0) " · 评分 ${item.score}" else "", 22f))
                        addView(TvDesign.text(activity, "AniList #${item.id} · 资料不代表源有资源", 20f))
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    TvDesign.focus(detail)
                    views.add(detail); rows.add(listOf(detail))
                    list.addView(detail, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
                }
                button("搜索资源 · 使用片名与当前已启用 Ani 源") { searchMetadata() }
                button("编辑关键词 / 中文与原名切换") { openSearch("metadata", item?.searchTitle(false).orEmpty()) }
                button("刷新当前列表") { page = if (kind == "season") "discover" else "catalog"; loadList(false, true) }
                button("返回番剧列表") { back() }
                if (item != null) {
                    text("中文：${item.chinese.ifBlank { "未提供" }}\n原名：${item.original.ifBlank { "未提供" }}\n首播/更新：${item.date.ifBlank { "未提供" }}\n来源：AniList #${item.id}\n$notice\n此处仅为资料，资源是否可用请搜索所选源。", target = list)
                    text(item.summary.ifBlank { "站点未提供简介；可用片名在已启用源中搜索。" }, 19f, list)
                }
            }
            "search" -> {
                val editor = input(keyword, "输入番剧关键词，确认打开键盘") { keyword = it }
                editor.setOnEditorActionListener { _, _, _ ->
                    runCatching {
                        (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                            ?.hideSoftInputFromWindow(editor.windowToken, 0)
                    }
                    search(); true
                }
                button("搜索 · ${source?.name ?: "请先选择源"}") { search() }
                button("换源（仅已启用受支持的 Ani 源）") { pickerReturn = "search"; page = "picker"; work("读取已启用源") { loadSources() } }
                button("使用中文名") { metadata?.let { keyword = it.searchTitle(false) }; render() }
                button("使用原名") { metadata?.let { keyword = it.searchTitle(true) }; render() }
                button("源管理 / 导入订阅") { page = "sources"; work("读取订阅") { loadSources() } }
                button("返回") { back() }
                text("当前源：${source?.name ?: "未选择"}；关键词：「$keyword」。手动搜索先用原词；详情搜索优先短词。无结果按顺序重试，最多 ${AniSearch.MAX_SOURCES} 个已启用受支持源、每源2个词，返回可取消。", target = list)
                if (searched) {
                    text(if (results.isEmpty()) "本轮未找到匹配条目，具体空结果或错误见下方记录；不表示全网无资源。可编辑关键词或手动换源。" else "本次 $lastSearch 返回 ${results.size} 条（最多500条）；请选择匹配条目并核对季数。", target = list)
                    text(searchHistory, 20f, list)
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
                button("返回搜索") { back() }
                sources.filter { it.unsupported == null && repository.enabled(it) }.forEach { entry -> button("${entry.name}${if (entry.id == source?.id) " · 当前源" else ""}") { source = entry; repository.select(entry); results = emptyList(); searched = false; page = pickerReturn; render() } }
                button("源管理") { page = "sources"; render() }
            }
            "sources" -> {
                button("返回发现首页") { back() }
                button("搜索番剧") { openSearch("discover") }
                text("订阅只提供搜索和解析规则，不是番剧目录。只有已启用、受支持的源能用于搜索；请选择你有权访问的内容。", target = list)
                input(url, "HTTP/HTTPS Ani 订阅 URL") { url = it }
                button("导入 / 更新输入的订阅") { work("正在下载并校验订阅") { sources = repository.import(url); loadSources() } }
                button("导入 Ani 官方引用的公开默认订阅") { url = AniRules.DEFAULT_SUB; work("正在导入公开订阅") { sources = repository.import(url); loadSources() } }
                sources.forEach { entry ->
                    val status = if (entry.unsupported != null) "不支持" else if (repository.enabled(entry)) "启用" else "停用"
                    button("[$status] ${entry.name}${if (repository.selected() == entry.id) " · 已选择" else ""}") {
                        when { entry.unsupported != null -> { message = entry.unsupported; render() }; !repository.enabled(entry) -> { message = "请先启用此源"; render() }; else -> { repository.select(entry); source = entry; openSearch("discover") } }
                    }
                    if (entry.unsupported == null) button("${if (repository.enabled(entry)) "停用" else "启用"} · ${entry.name}") { repository.toggle(entry); render() }
                }
            }
            "help" -> {
                button("返回发现首页") { back() }
                button("搜索番剧") { openSearch("discover") }
                button("源管理") { page = "sources"; work("读取订阅") { loadSources() } }
                text("1. 发现首页默认显示当季首月放送的番剧，可切换本季三个月、每日放送与番剧榜，并加载更多。\n2. 番剧资料来自 AniList 公开接口，国内可直接连接、不需要代理；B站番剧索引已长期停更，故不再作为新番来源。\n3. 选择条目查看名称、更新与评分，再点“搜索资源”；优先用短片名搜索，未命中再尝试完整片名与备用源。\n4. 初次使用请到源管理导入公开默认订阅或可信 Ani 订阅，选择并启用源。订阅/源不是番剧目录，CMS 与 Jellyfin 保持独立入口。\n5. 手动搜索先用输入原词。每次最多尝试3个已启用受支持源，每源最多2个词；结果显示实际源、关键词和失败原因。请核对番名与季数；未找到不表示全网没有。\n6. 方向键移动并滚动，确认选择；输入框按确认打开输入法，完成/搜索提交，返回先关闭键盘，再返回上级。请求中可取消。\n7. 当季按季度筛选，含连载与未开播，不是“今天上线”；放送表按北京时间归集；榜单是 AniList 评分排序，不是实时播放量。\n8. 本机缓存1小时，联网失败会显示上次结果的采集时间；元数据不保证源有资源；不要求 AppID。\n9. 仅支持 Ani Selector 静态子集；不执行 JS，不绕登录、验证码、付费限制或 DRM。播放保持现有 SurfaceView 链路。", 19f, list)
            }
            "episodes" -> {
                button("返回搜索结果") { back() }
                episodes.forEach { episode -> button("${episode.channel} · ${episode.name}") {
                    work("正在提取静态视频 HTTP 地址（不执行脚本）") {
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
                        ARouter.getInstance().build(RouteTable.Player.Player)
                            .withBoolean("ani_tv_playback", true).navigation(activity)
                    }
                } }
            }
        }
        if (job?.isActive == true) button("取消当前请求") { job?.cancel(); job = null; retry = null; message = "已取消，可重试或返回"; render() }
        else retry?.let { action ->
            text(message, 24f, list)
            button("重试失败操作") { action() }
        }
        TvDesign.wire(rows, navigation)
        val renderedPage = page
        shell.viewTreeObserver.addOnGlobalFocusChangeListener { _, current ->
            val index = views.indexOf(current)
            val railIndex = navigation.indexOf(current)
            if (index >= 0) focus[renderedPage] = index
            else if (railIndex >= 0) focus[renderedPage] = -railIndex - 1
        }
        val saved = focus[page] ?: -1
        controls = views
        navigationControls = navigation
        activity.setContentView(shell)
        if (saved < 0) navigation.getOrNull(-saved - 1)?.requestFocus()
        else views.getOrNull(saved.coerceAtMost(views.lastIndex))?.requestFocus()
    }
}
