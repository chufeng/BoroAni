package com.xyoye.dandanplay.ui.main

import com.squareup.moshi.Moshi
import org.jsoup.Jsoup
import okio.ByteString.Companion.decodeBase64
import java.net.URI
import java.net.URLEncoder
import java.util.regex.Pattern

internal data class AniSource(val id: String, val name: String, val config: Map<String, Any?>, val unsupported: String? = null)
internal data class AniItem(val name: String, val url: String, val channel: String = "")

/** 独立实现官方 web-selector v2 数据契约子集，不执行订阅中的代码。 */
internal object AniRules {
    const val DEFAULT_SUB = "https://sub.creamycake.org/v1/css1.json"
    const val UA = "Mozilla/5.0 (Linux; Android 6.0; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36"
    private val json = Moshi.Builder().build().adapter(Any::class.java)
    private const val DEFAULT_VIDEO = """(^http(s)?://(?!.*http(s)?://).+((\.mp4)|(\.mkv)|(m3u8)).*(\?.+)?)|(akamaized)|(bilivideo.com)"""
    private const val DEFAULT_NESTED = """^.+(m3u8|vip|xigua\.php).+\?"""
    private val pathToken = Regex("""^([.]([A-Za-z_][A-Za-z0-9_]*)|\[(\*|[0-9]+|'[^']+'(?:\s*,\s*'[^']+')*)\])""")

    @Suppress("UNCHECKED_CAST")
    fun obj(value: Any?): Map<String, Any?> = value as? Map<String, Any?> ?: emptyMap()
    fun Map<String, Any?>.str(key: String, default: String = "") = this[key] as? String ?: default
    fun decode(text: String): Any? = json.fromJson(text)
    fun http(value: String): String {
        require(value.length <= 16384 && value.none { it == '\r' || it == '\n' || it == '\u0000' }) { "链接过长或包含控制字符" }
        val uri = URI(value.trim())
        require(uri.scheme?.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null) { "只允许不含账号密码的 HTTP/HTTPS 地址" }
        return uri.toASCIIString()
    }
    fun resolve(base: String, value: String): String {
        require(value.isNotBlank()) { "规则提取到空链接" }
        return http(URI(http(base)).resolve(value.trim()).toString())
    }
    fun subscription(text: String): List<AniSource> {
        val root = obj(decode(text))
        val schema = root["schemaVersion"] ?: 1
        require(schema is Number && schema.toDouble() in listOf(1.0, 2.0)) { "不支持该订阅 schemaVersion" }
        val list = (root["mediaSources"] ?: obj(root["exportedMediaSourceDataList"])["mediaSources"]) as? List<*>
            ?: error("不是 Ani 订阅：缺少 mediaSources")
        require(list.size <= 500) { "订阅源数量过多" }
        return list.mapIndexed { index, value ->
            val row = obj(value); val args = obj(row["arguments"]); val config = obj(args["searchConfig"])
            val problem = runCatching {
                require(row.str("factoryId") == "web-selector") { "不支持 factoryId=${row.str("factoryId")}（非 Selector）" }
                require((row["version"] as? Number)?.toDouble() == 2.0) { "只支持 web-selector version=2" }
                require(config.str("subjectFormatId", "a") in listOf("a", "indexed", "json-path-indexed")) { "不支持条目格式" }
                require(config.str("channelFormatId", "no-channel") in listOf("no-channel", "index-grouped")) { "不支持线路格式" }
                val template = config.str("searchUrl")
                require(template.contains("{keyword}")) { "搜索规则缺少 {keyword}" }
                require(!Regex("""\{[^}]+\}""").containsMatchIn(template.replace("{keyword}", ""))) { "不支持额外搜索模板参数" }
                http(template.replace("{keyword}", "test"))
                if (config.str("rawBaseUrl").isNotBlank()) http(config.str("rawBaseUrl"))
                require((config["onlySupportsPlayers"] as? List<*>)?.isNotEmpty() != true) { "规则限制播放器，本 MVP 不支持" }
                val source = AniSource("", "", config)
                val subject = subjectRule(source)
                require(!Regex("""\{[^}]+\}""").containsMatchIn(subject.str("linkTemplate").replace("{value}", ""))) { "不支持链接模板参数" }
                if (config.str("subjectFormatId", "a") == "json-path-indexed") {
                    path(null, subject.str("selectNames", "$[*]['title','name']"))
                    path(null, subject.str("selectLinks", "$[*]['url','link']"))
                } else {
                    listOf("selectLists", "selectNames", "selectLinks").map { subject.str(it) }.filter { it.isNotBlank() }.forEach { Jsoup.parse("").select(it) }
                }
                val channel = channelRule(source)
                channel.filterKeys { it.startsWith("select") }.values.filterIsInstance<String>().filter { it.isNotBlank() }.forEach { Jsoup.parse("").select(it) }
                if (channel.str("matchChannelName").isNotEmpty()) capturePattern(channel.str("matchChannelName"), "ch")
                val match = obj(config["matchVideo"])
                capturePattern(match.str("matchVideoUrl", DEFAULT_VIDEO), "v")
                if (match["enableNestedUrl"] != false) capturePattern(match.str("matchNestedUrl", DEFAULT_NESTED), "v")
                headers(source, base(source))
            }.exceptionOrNull()?.let { "规则不支持或无效：${it.message ?: it.javaClass.simpleName}" }
            AniSource(row.str("id", args.str("name", index.toString())), args.str("name", "未命名源"), config, problem)
        }
    }
    private fun subjectRule(source: AniSource) = obj(source.config[when(source.config.str("subjectFormatId", "a")) {
        "a" -> "selectorSubjectFormatA"; "indexed" -> "selectorSubjectFormatIndexed"; else -> "selectorSubjectFormatJsonPathIndexed"
    }])
    private fun channelRule(source: AniSource) = obj(source.config[if(source.config.str("channelFormatId", "no-channel") == "index-grouped") "selectorChannelFormatFlattened" else "selectorChannelFormatNoChannel"])
    fun searchUrl(source: AniSource, keyword: String): String = http(source.config.str("searchUrl").replace("{keyword}", URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")))
    fun base(source: AniSource): String = source.config.str("rawBaseUrl").ifBlank { URI(searchUrl(source, "x")).let { "${it.scheme}://${it.rawAuthority}/" } }
    fun subjects(source: AniSource, text: String): List<AniItem> {
        val id = source.config.str("subjectFormatId", "a"); val rule = subjectRule(source); val template = rule.str("linkTemplate")
        fun item(name: String, link: String) = AniItem(name, resolve(base(source), if(template.isBlank()) link else template.replace("{value}", link)))
        if(id == "json-path-indexed") {
            val data = decode(text)
            val names = path(data, rule.str("selectNames", "$[*]['title','name']"))
            val links = path(data, rule.str("selectLinks", "$[*]['url','link']"))
            require(names.size == links.size) { "JSON 名称与链接数量不一致，拒绝错配" }
            return names.zip(links) { n, u -> item(n, u) }.take(500)
        }
        val doc = Jsoup.parse(text)
        return if(id == "a") doc.select(rule.str("selectLists", "div.video-info-header > a")).toList().filter { it.attr("href").isNotBlank() && (it.attr("title").isNotBlank() || it.text().isNotBlank()) }
            .take(500).map { item(it.attr("title").ifBlank { it.text() }, it.attr("href")) }
        else doc.select(rule.str("selectNames", ".search-box .thumb-content > .thumb-txt")).map { it.text() }.filter { it.isNotBlank() }
            .zip(doc.select(rule.str("selectLinks", ".search-box .thumb-menu > a")).map { it.attr("href") }.filter { it.isNotBlank() }) { n, u -> item(n,u) }.take(500)
    }
    fun episodes(source: AniSource, text: String): List<AniItem> {
        val doc = Jsoup.parse(text); val grouped = source.config.str("channelFormatId", "no-channel") == "index-grouped"; val rule = channelRule(source)
        val lists = if(grouped) doc.select(rule.str("selectEpisodeLists", ".anthology-list-box")).toList() else listOf(doc)
        val labels = if(grouped) doc.select(rule.str("selectChannelNames", ".anthology-tab > .swiper-wrapper a")).map { it.text().trim() } else listOf("")
        return lists.zip(labels).flatMap { (list, label) ->
            val channelPattern = rule.str("matchChannelName", """^(?<ch>.+?)(\d+)?$""")
            val channel = if(!grouped || channelPattern.isEmpty()) label else capture(channelPattern, "ch", label) ?: return@flatMap emptyList<AniItem>()
            val selector = rule.str(if(grouped) "selectEpisodesFromList" else "selectEpisodes", if(grouped) "a" else "#glist-1 > div.module-blocklist.scroll-box.scroll-box-y > div > a")
            val linksSelector = rule.str(if(grouped) "selectEpisodeLinksFromList" else "selectEpisodeLinks")
            val links = if(linksSelector.isBlank()) null else list.select(linksSelector).map { it.attr("href") }.filter { it.isNotBlank() }
            list.select(selector).mapIndexedNotNull { index, e ->
                val href = links?.getOrNull(index) ?: e.attr("href")
                if (href.isBlank() || e.text().isBlank()) null else AniItem(e.text(), resolve(base(source), href), channel)
            }
        }.take(2000)
    }
    /** 支持点路径、数组下标、[*] 和字段联合；拒绝过滤、递归与脚本表达式。 */
    fun path(root: Any?, expression: String): List<String> {
        require(expression.startsWith("$") && expression.length <= 2048) { "JSONPath 必须以 $ 开始且不超过 2048 字符" }
        var rest = expression.drop(1); var values = listOf(root)
        while(rest.isNotEmpty()) {
            val m = pathToken.find(rest) ?: error("不支持 JSONPath 子集：$expression")
            values = values.filterNotNull().flatMap { value ->
                when { m.groupValues[2].isNotEmpty() -> listOf(obj(value)[m.groupValues[2]])
                    m.groupValues[3] == "*" -> value as? List<*> ?: error("JSONPath 通配符目标不是数组")
                    m.groupValues[3].toIntOrNull() != null -> listOf((value as? List<*>)?.getOrNull(m.groupValues[3].toInt()))
                    else -> listOf(Regex("'([^']+)'").findAll(m.groupValues[3]).mapNotNull { obj(value)[it.groupValues[1]] }.firstOrNull()) }
            }; rest = rest.drop(m.value.length)
        }
        return values.filterNotNull().map {
            require(it is String || it is Number || it is Boolean) { "JSONPath 未解析为标量" }
            if(it is Number && it.toDouble() == it.toLong().toDouble()) it.toLong().toString() else it.toString()
        }.filter { it.isNotBlank() }
    }
    // Android 6 不依赖 Matcher.group(String)，只转换实际命名捕获并按数字读取。
    private fun capturePattern(expression: String, name: String): Pair<Pattern, Int?> {
        require(expression.length <= 4096 && listOf("""\k<""", """\Q""", """\E""", "(?x", "(?-", "(?#").none { expression.contains(it) }) { "不支持此正则扩展或正则过长" }
        var count = 0; var target: Int? = null; var escaped = false; var inClass = false; var i = 0
        val compatible = StringBuilder()
        while (i < expression.length) {
            val c = expression[i]
            if (escaped) { compatible.append(c); escaped = false; i++; continue }
            if (c == '\\') { compatible.append(c); escaped = true; i++; continue }
            if (c == '[') inClass = true
            if (c == ']') inClass = false
            if (c == '(' && !inClass) {
                val tail = expression.substring(i)
                if (!tail.startsWith("(?")) count++
                else if (tail.startsWith("(?<") && !tail.startsWith("(?<=") && !tail.startsWith("(?<!")) {
                    val end = expression.indexOf('>', i + 3)
                    require(end > i + 3) { "命名捕获不完整" }
                    count++; if (expression.substring(i + 3, end) == name) target = count
                    compatible.append('('); i = end + 1; continue
                }
            }
            compatible.append(c); i++
        }
        return Pattern.compile(compatible.toString()) to target
    }
    private fun capture(expression: String, name: String, text: String): String? {
        require(text.length <= 16384) { "正则匹配链接或标签过长" }
        val (pattern, index) = capturePattern(expression, name); val matcher = pattern.matcher(text)
        if (!matcher.find()) return null
        return index?.let { matcher.group(it) } ?: text
    }
    /** 仅解析公开 JSON 数据；不求值 JavaScript，不解密或请求授权接口。 */
    fun pageCandidates(pageUrl: String, body: String): Pair<List<String>, List<String>> {
        val text = if (body.trimStart().startsWith("\"")) {
            (runCatching { decode(body) }.getOrNull() as? String) ?: body
        } else body
        val doc = Jsoup.parse(text)
        val media = mutableListOf<String>()
        val frames = mutableListOf<String>()
        fun add(target: MutableList<String>, value: String) {
            if (target.size < 500) runCatching { resolve(pageUrl, value) }.getOrNull()?.let { target.add(it) }
        }
        doc.select("video[src],source[src]").take(100).forEach { add(media, it.attr("src")) }
        doc.select("iframe[src]").take(3).forEach { add(frames, it.attr("src")) }
        doc.select("script").take(100).forEach { script ->
            val data = script.data()
            Regex("\\b(?:var\\s+|let\\s+|const\\s+)?player_[A-Za-z0-9_]+\\s*=\\s*").findAll(data).take(8).forEach { assignment ->
                val start = assignment.range.last + 1
                jsonObject(data, start)?.let { raw ->
                    val config = obj(runCatching { decode(raw) }.getOrNull())
                    val mode = (config["encrypt"] as? Number)?.toInt() ?: config.str("encrypt").toIntOrNull() ?: 0
                    val restricted = listOf("points", "trysee").any { (config[it] as? Number)?.toDouble()?.let { n -> n > 0 } == true }
                    require(!restricted) { "不解析付费或试看限制内容" }
                    runCatching {
                        val value = config.str("url")
                        when (mode) {
                            0 -> value
                            1 -> percentDecode(value)
                            2 -> percentDecode(value.decodeBase64()?.utf8() ?: error("无效 Base64"))
                            else -> error("不支持该播放器编码")
                        }
                    }.getOrNull()?.let { add(media, it) }
                }
            }
        }
        // 先按 JSON 字符串语法消除斜杠和 Unicode 转义，保留签名中的 + 和百分号。
        Regex("\"(?:[^\"\\\\\r\n]|\\\\.){1,16384}\"").findAll(text).take(500).forEach { token ->
            val value = runCatching { decode(token.value) }.getOrNull() as? String
            if (value != null && (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("//"))) add(media, value)
        }
        Regex("https?://[^\\s\"'<>\\\\]+").findAll(text).take(500).forEach { add(media, it.value) }
        return media.distinct() to frames.distinct()
    }
    private fun percentDecode(value: String): String {
        require(value.length <= 16384) { "编码链接过长" }
        return java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    }
    private fun jsonObject(text: String, start: Int): String? {
        if (text.getOrNull(start) != '{') return null
        var depth = 0; var quoted = false; var escaped = false
        for (i in start until minOf(text.length, start + 65536)) {
            val c = text[i]
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }
    fun nestedUrl(source: AniSource, candidate: String): String? {
        val rule = obj(source.config["matchVideo"])
        if (rule["enableNestedUrl"] == false) return null
        val extracted = capture(rule.str("matchNestedUrl", DEFAULT_NESTED), "v", candidate) ?: return null
        return runCatching { http(extracted) }.getOrNull()
    }
    fun nested(source: AniSource, candidate: String): Boolean = nestedUrl(source, candidate) != null
    fun video(source: AniSource, candidate: String): String? {
        if (nested(source, candidate)) return null
        val extracted = capture(obj(source.config["matchVideo"]).str("matchVideoUrl", DEFAULT_VIDEO), "v", candidate) ?: return null
        return runCatching { http(extracted) }.getOrNull()
    }
    fun headers(source: AniSource, referer: String): Map<String,String> {
        val match = obj(source.config["matchVideo"]); val headers = obj(match["addHeadersToVideo"])
        return linkedMapOf("User-Agent" to source.config.str("userAgent").ifBlank { headers.str("userAgent", UA) },
            "Referer" to headers.str("referer").ifBlank { http(referer) }).apply {
            val cookies = match.str("cookies", "quality=1080")
            if (cookies.isNotBlank()) put("Cookie", cookies)
            values.forEach { value -> require(value.length <= 8192 && value.all { it == '\t' || it in ' '..'~' }) { "不支持含非法字符的 HTTP 请求头" } }
            http(getValue("Referer"))
        }
    }
}
