# Ani 在线源电视版交付说明

## 2026-11-17 09:10 新番源换 AniList + 闪退兜底（当前版本）

### 1) B站番剧索引确实停更

实测 `pgc/season/index` 的季度参数形同虚设（2026-10 与 2025-10 返回同一批条目与同一 total=1126），榜单前列仍是柯南、蜡笔小新等旧作，日历也长期不动。继续用它做“新番”会一直显示过时内容。

### 2) 候选源实测（不挂代理）

| 数据源 | 结果 |
|---|---|
| `yuc.wiki` | 200，可用（HTML 季度表） |
| `graphql.anilist.co` | 200，可用（JSON，含当季新番、评分、集数、状态、下次更新） |
| `s4.anilist.co`（封面） | 200，可下载 |
| `mikanani.me` / `api.anilist.co` / `api.jikan.moe` | 超时 |
| `livechart.me` | 403（Cloudflare 拦截） |

选定 **AniList GraphQL**：结构稳定、可翻页、同时能出“当季新番 / 高分榜 / 当日放送”，无需解析 HTML。实测返回 2026 秋季真实数据（薬屋のひとりごと 第3期、ブラッククローバー 第2期、転生したら剣でした 第2期 等，均 RELEASING）。

### 3) 代码改动

- 新增 `AniListRules.kt`：三个查询构造器 + 解析；标题优先日文原名，罗马音作原名；评分 0-100 折算为 10 分制；状态中文化；`nextAiringEpisode` 显示“第 N 集 时间”；封面 `http→https`、`//` 补全；GraphQL `errors` 直接抛出可读信息。
- `AniDiscoverRepository.kt`：新增 POST JSON 通道（沿用 AniTls 证书与 hostname 校验）、`anilistSeason/anilistRank/anilistSchedule`，独立缓存键与 1 小时缓存。B站与 Bangumi 方法保留但页面不再调用。
- `AniTvPage.kt`：三类列表改走 AniList；文案同步；“✓”改为“·”（电视字体无该字形，原来显示方块）；星期/季度说明改为真实语义。
- 闪退加固：`render()` 外层 try/catch，失败时显示“界面加载出错 + 原因 + 返回重试”而不是闪退；`work()` 的异常捕获从 Exception 扩到 Throwable（保留取消语义）；输入法 `showSoftInput/hideSoftInput` 全部 runCatching（这是 TV 上最常见的 BadTokenException 来源）。

### 4) 关于“点搜索闪退”

未能在电视复现：侧栏进入搜索页、输入 `jojo` 搜索、输入 `a` 搜索、从详情点“搜索资源”共四次操作，进程均存活，logcat 无 FATAL。已知最可能是输入法 `showSoftInput` 抛 BadTokenException 或渲染异常，两处都已加保护；若再次出现，界面会显示错误文案而非退出，请把该行文字发我以定位。

### 5) 构建与产物

- JDK11 + SDK，`--offline --max-workers=2 :app:testDebugUnitTest assembleDebug`：BUILD SUCCESSFUL in 56s。日志 ani-anilist-build3.log。
- app 测试 44 项全部通过（含新增 AniList 7 项、B站 6 项）。期间遇到两次缓存问题：构造签名变化后测试类未重编导致 8 个 NoSuchMethodError，已通过恢复三参数主构造（POST 改为次构造注入）解决。
- 新包 `out/弹弹play-AniSource-TV-anilist-armv7a.apk`，55690946 字节，SHA256 6533506d3e134794674c9fa3a3470f9abcb597c117c543bd61e464826d2c19ca，minSdk 21、armeabi-v7a。按要求**本轮未安装到电视**。


## 2026-11-17 01:10 实机验证：B站版已安装并联网成功

- 电视重开后 adb 恢复；此前 offline 表现为端口能连通但握手无响应，重启电视即可恢复，未修改任何系统设置。
- 安装 `out/弹弹play-AniSource-TV-bili-armv7a.apk` 返回 Success（55584245 字节，SHA256 6923e1ec66144baa319b5eee6d70a7ebca6f0bd81daf5420e7f607d654c8eec5），启动进入 MainActivity，无 AppID 弹窗。
- 实机三处真实联网，均由界面自带时间戳证明，不是内置快照：
  - 番剧索引：`已直连返回 2026-11-17 01:01:04（北京时间）`，10/11/12 月切换可用，卡片显示真实海报（青春猪头少年、JOJO、转生史莱姆、鬼灭之刃）。
  - 番剧榜：`已直连返回 2026-11-17 01:06:26`，卡片正常。
  - 每日放送：`已直连返回 2026-11-17 01:07:01`，周一至周日按钮可用。
- 详情页正常：标题、海报、副标题、评分 9.8、来源标注 `哔哩哔哩番剧 #25733`，按钮可聚焦。
- 已知小问题：①部分卡片显示“封面暂缺 原创装饰图”，是该条封面未取到，属降级占位；②星期选中标记“✓”在电视字体中显示为方块，功能不受影响；③B站 subTitle 被显示在“原名”行，实际是副标题文案。
- 源搜索：默认源 2k动漫 返回 HTTP 404；换 girigiri 后用完整中文名“青春猪头少年不会梦到兔女郎学姐”得 0 条。此前 girigiri 用短关键词可出结果并成功播放，说明差异在关键词长度而非链路断裂。**本轮未在电视上重新验证“新番→源匹配→选集→播放”的完整闭环**。
- 证据截图：out/ani-bili-launch.png、ani-bili-list.png、ani-bili-cards.png、ani-bili-detail.png、ani-bili-search.png、ani-bili-picker.png、ani-bili-giri.png、ani-bili-result3.png、ani-bili-rank.png、ani-bili-cal.png。


## 2026-11-17 发现页改用哔哩哔哩直连（以本节为准）

### 根因：Bangumi 在本网络不可直连，与代码无关

- 本机不走代理解析 api.bgm.tv 得到 4 个互相矛盾的地址：128.121.243.75、69.63.176.59、157.240.8.50、162.125.34.133；经代理查询 DoH 的真实记录为 Cloudflare 104.26.8.23 / 104.26.9.23 / 172.67.73.67。多个 DNS 给出彼此冲突的结果，属于解析污染。
- 绕过 DNS、直接用真实 Cloudflare IP 连接（--resolve，TLS SNI 仍为原域名）：104.26.8.23、104.26.9.23 均在 TLS 握手阶段被 reset；172.67.73.67 超时。说明除了解析污染，TLS 握手也被阻断，不是客户端配置错误。
- 结论：**在这台机器与这台电视所处网络下，访问 api.bgm.tv / bgm.tv 必须经由代理，直连不可用**；这也解释了此前电视“新番/推荐加载不了”，以及本地保留的安装包快照只能当离线资料。

### 对照：Ani 与弹弹play 各自的联网方式

- 弹弹play 原版只连 api.dandanplay.net（自家国内服务）与 api.bilibili.com，仓库内没有代理设置项，因此国内一直可直连。此前把发现页接到 Bangumi，等于给一个纯国内应用引入了一个国内不可达的数据源，属于本项目自加的依赖，不是弹弹play 原有行为。
- Ani（Animeko）的番剧索引、放送表、排行榜与账号收藏均建立在 Bangumi（api.bgm.tv）之上，所以在同一网络下它的资料页同样连不上；它能直连的是各第三方资源站网页（Selector 规则抓取）。本轮没有逐行审计其 Android 网络层，因此不宣称它内部有可复用的绕过手段，也不做 TLS 降级或 SNI 伪装。
- 两条可行路线：①把番剧资料源换成国内可直连的公开接口（已实施）；②让电视走局域网内代理访问 Bangumi（需要电脑常开代理并允许局域网连接，未实施）。

### 实际改动

- AniDiscoverRules.kt：新增 biliTimeline / biliRank / biliSeason 解析，映射到现有 DiscoverSubject；封面 http→https 与 // 协议相对地址归一；评分兼容“8.8分”文案；追番数格式化为万/亿；接口 code≠0 时抛错而不是当空列表。
- AniDiscoverRepository.kt：新增 loadExternal（完整 URL + 独立缓存键 + 来源文案），请求层支持绝对地址并为哔哩哔哩附加 Referer；沿用 AniTls 证书与 hostname 校验、1 小时本机缓存、失败回退缓存并标注时间。Bangumi 原有方法保留未删除，UI 不再依赖它。
- AniTvPage.kt：新番/每日放送/榜单改为调用哔哩哔哩接口，文案改为真实来源；详情页不再请求 Bangumi 详情，直接展示列表资料；帮助页同步更新。
- 新增 AniBiliRulesTest：6 项，覆盖星期筛选、空行跳过、评分后缀、封面协议归一、总数保留、接口错误码。

### 实测与产物

- 不挂代理直连哔哩哔哩接口均返回 200：时间表 12870 字节、番剧榜 73902 字节、索引 total=1126。
- JDK11 + SDK，--offline --max-workers=2 :app:testDebugUnitTest assembleDebug：BUILD SUCCESSFUL in 1m 1s，全部 app 测试通过（含新增 6 项）。日志 ani-bili-build.log。
- 新包 out/弹弹play-AniSource-TV-bili-armv7a.apk，55584245 字节，SHA256 6923e1ec66144baa319b5eee6d70a7ebca6f0bd81daf5420e7f607d654c8eec5，minSdk 21、armeabi-v7a。旧包保留。
- 电视 192.168.2.x:5555 本轮 disconnect/connect 均失败（设备离线），**尚未安装该包，也没有电视端实际联网与画面的新证据**。需电视恢复在线后验证三类列表、封面与播放。


## 2026-11-16 最终集成更新：polished v2（以本节为准）

- UI 子任务已正式交接；网络子任务因模型服务 TPM 限流失败，不视作联网修复完成。指定 DeepSeek 后错误仍引用 gpt-6.1-sol，实际模型路由未得到确认。下方等待交接记录保留为历史，不再代表本次构建状态。
- 已保留并整合 TvHome.kt、TvDesign.kt、AniTvPage.kt 的浅色侧栏、栏目选中态、遥控跨栏与返回焦点修补；没有回滚 Ani/TLS/SurfaceView 与媒体超时修复。
- 已核验最终 APK 内两张生图压缩资源 tv_coast.jpg / tv_projector.jpg，合计116439字节；另含70张真实封面快照，合计2992562字节。快照不等于电视联网成功；没有封面时仍明确显示原创装饰图。
- 本次最终离线构建日志 ani-polished-nov16-final-build.log：BUILD SUCCESSFUL in 1m 12s。app 31项测试本轮重跑、0失败/0错误（UTC 2026-11-16T15:28:16）；common 7项及player 14项为UP-TO-DATE旧报告，未冒称本轮重跑。git diff --check通过，仅原wrapper换行警告。
- 独立新包 out/弹弹play-AniSource-TV-polished-v2-armv7a.apk，55677586字节，SHA256 9ddf089293da884e7b9dfa267c59eed5d54f3a686daf006dffbd8012c69c02e4；aapt2核验minSdk 21，只有armeabi-v7a。旧polished/discover/smooth均保留。
- 电视192.168.2.x:5555当前offline；一次reconnect offline后connect失败。本轮未安装v2、未取得v2实机截图或已安装哈希，没有清数据或修改电视全局网络/安全设置。历史delivery截图仅用于检查旧布局，不冒充v2验收。
- Bangumi真实联网仍未修复。现有源码使用官方定义支持的GET年月/排名目录接口，已有缓存来源、采集时间、错误原因区分；数据层测试通过不代表设备HTTPS握手已恢复。新番/日历/榜单可以显示已有资料，但实时刷新、后续无缓存页仍受网络影响。下一步须电视恢复ADB在线后继续网络定位和v2遥控/画面验收，不能宣称全任务完成。


## 2026-11-16 新并发任务集成核查（未验收完成）

- 本次指定 UI worker `agent-c7c6431b-fdb9-4069-971a-c402b27b25f7` 与网络 worker `agent-f202baea-3864-483b-88be-5e70e219fe97` 均返回 running，尚未收到明确完成/停止写入交接。未修改它们的源码，未启动并发构建。当前工具目录没有团队 SendMessage；搜索只得到 Agent Mail 邮件，未误用邮件协调，也未读取 worker 私有日志或无限轮询。
- ADB 首次 devices -l 显示 `192.168.2.x:5555 offline`；一次 connect 返回 already connected，但随后 get-state 仍报 device offline。因此本轮未安装、启动或新截屏；这更新的是本次连接观察，不否定历史在线记录。未改全局网络或安全设置。
- out 已有 polished APK，54668511 字节，SHA256 `38f7615b017db8695879f483c692b2db8d77a3c576c2ad0ae3a13e815e91df86`。这是已有产物核验，不是本轮新构建，也未核对当前设备已安装包。旧 smooth 包 SHA256 仍为 `6290fab88b24a4a7c2b3c06d3cfad558a611165cd93e10beeaccf077717b5cff`，未覆盖。
- 已有构建日志包含成功和失败历史；delivery 日志为 BUILD SUCCESSFUL in 44s，posters 为 1m 4s。现存 XML：app 31 项、0失败/0错误，时间戳2026-11-16T15:17:28–29；common 7项时间戳2026-11-15T11:30:02，player 14项时间戳2026-11-15T13:44:23–24。后两模块日志为 UP-TO-DATE，不冒称本轮重跑。报告时间仅照录XML，不能据此断言与已有APK一一对应。
- 已用 ViewImage 检查已有 `ani-polished-delivery-home.png` 和 `ani-polished-delivery-rank-cards.png`：浅色导航、原创插画与绿色焦点可见；首页明确写“尚未确认本次联网成功”，榜单可见4列卡片与“封面暂缺 原创装饰图”。截图证明已有UI状态，不证明实时海报或 Bangumi 联网成功。当前API仍未获本轮成功证据，不能宣称新番推荐真联网已修复。
- 指定JDK实测11.0.33；git diff --check通过，仅原wrapper换行警告。后续必须先取得两个worker正式交接，再以指定三模块离线命令构建，并在电视恢复在线后 install -r、核对安装哈希、首页/新番/榜单/焦点新截图与真实请求来源；不运行uiautomator、不清数据、不降级TLS、不提交git。

## 2026-11-16 集成验收暂停点（优先于历史结论）

- 本轮尚未完成 polished 构建及实机验收。UI worker agent-d589d928-b031-4f26-a2e4-9cc44199d5fc 最近状态仍为 running；网络 worker agent-6cee7911-352d-4c49-bdb7-216fd373932d 查询返回 No task found，不能视为成功交接。未提前构建或覆盖 worker 源码。
- 当前可用工具未提供团队 SendMessage；工具搜索只返回邮件发送功能，未错误使用邮件功能协调 worker。读取网络 worker 日志被拒绝后未尝试其他方式绕过。停止重复状态轮询，后续须由原主会话提供两个 worker 明确完成与停止写入的交接。
- 指定 java.exe 实测为 OpenJDK 11.0.33；指定 adb.exe devices -l 显示 192.168.2.x:5555 为 device、MiTV4_ANSM0。这不是新包安装或 Bangumi 联网成功证据。
- 本轮重新读取官方 open-api/v0.yaml：GET /v0/subjects 明确支持 type、sort=date/rank、year、month、limit、offset。现有年月 GET 调用符合定义，不应仅因加载失败改成 POST 搜索。官方注明第一页服务端缓存24小时、后续页1小时；此类缓存属于真实 API 返回，不能称为假数据。
- 静态审查确认 AniDiscoverRepository 现有实现包含12秒连接、18秒读取、25秒总超时、取消传播、4MiB限制、NETWORK/CACHE/BUNDLED来源及错误/日期标识；8个 Repository 回归测试方法已存在，但本轮尚未运行。快照不能证明电视联网恢复，亦未确定当前网络失败唯一根因。
- UI 草稿已有浅色左导航、hero、海报加载与两张插画资源引用，但 worker 未交接，不能宣称最终布局、素材压缩、焦点/滚动或嵌图实机验收通过。未进行首页/新番/榜单/详情新截图，未安装 polished 包，没有该包测试计数或安装哈希。历史 discover/smooth 证据仅适用于历史产物。
- 未清数据、改电视系统网络、放松 TLS/hostname、运行 uiautomator 或提交 Git；SurfaceView 与媒体超时修复未改。后续按用户指定三模块测试与 assembleDebug 命令执行，日志写父目录，保留旧包；完成 install -r、设备包哈希、电视真实请求及 screencap/pull/ViewImage 后才能发布 polished 成功结论。

日期：2026-11-15。项目：ddplay/repo。未提交 Git。已追加项目日志 .workbuddy/memory/2026-11-15.md。

## 2026-11-15 发现首页与榜单交付（最新）

本节为发现页最新状态；下方 smooth 性能诊断与历史证据继续保留。未提交 Git，未清数据，未改 CMS/Jellyfin 业务代码，未运行 uiautomator，未放松 TLS 或修改电视全局网络。

### 实际实现

- TvHome.kt 将 Ani 入口改为「发现番剧 · 新番 / 每日放送 / 榜单 / 搜索」；AniTvPage.kt 默认进入发现列表，六个独立按钮为搜索番剧、当季新番、每日放送、榜单、源管理、使用帮助。前三个月份可切换，列表每页20条、加载更多；详情与帮助文字加入遥控焦点与滚动。
- 新增 AniDiscoverRules.kt：中/原名回退、季度边界、目录/日历/详情解析、真实排名展示及返回路由。新增 AniDiscoverRepository.kt：官方 API、异步可取消请求、12秒连接/18秒读取/25秒总超时、4MiB响应上限、1小时本机缓存与离线提示、最多约80个缓存文件。仅使用标准 OkHttp + 原 AniTls，保留证书与 hostname 校验，不复用 Unsafe 客户端。
- 详情显示中文/原名/首播/有则简介；一键用标题搜索已选择且已启用的真正 Ani 源，支持编辑关键词、中/原名切换及手动换源。一次只搜一个源，无自动扫描。空结果说明当前源、本次关键词、换源/换名/返回，不推断全网没有。
- AniRepository.kt、AniRules.kt、AniTls.kt 与播放器代码本轮未改；AniTvPage 保留 LinkStorage→VideoSourceManager→Player 路由、ani_tv_playback=true、媒体 callTimeout=0、原15/20秒连接读取超时与 SurfaceView 会话链路。CMS/Jellyfin 原入口保留，不要求 AppID。

### 官方数据与真实网络边界

- 官方说明 https://github.com/bangumi/api ，OpenAPI https://raw.githubusercontent.com/bangumi/api/master/open-api/v0.yaml 。实际调用 https://api.bgm.tv/calendar 与 https://api.bgm.tv/v0/subjects?type=2&sort=date&year=2026&month=10&limit=20&offset=0 （11/12月同形），综合榜 https://api.bgm.tv/v0/subjects?type=2&sort=rank&limit=20&offset=0 ，详情 /v0/subjects/{id}。不绕认证。
- 2026-11-15 约14:50 UTC通过电脑既有127.0.0.1:7890代理，标准HTTPS实际取回五份JSON：10月74964字节、11月64622字节、12月26266字节、综合排名154434字节、日历85284字节。未经伪造的原始响应及采集时间/路径/SHA256保存于 app/src/main/assets/ani-discover/manifest.json 和同目录JSON。
- 电视 api.bgm.tv 直连发生 SocketTimeoutException；电脑系统DNS与独立DNS结果不一致，但尝试独立解析地址的标准TLS直连同样超时，不能只据此确定DNS是唯一根因。未将电脑代理或静态IP写入电视/客户端。
- 为不让首页空白，最终包对上述已内置首屏优先显示真实快照，显著标注UTC采集时间及「非实时、尚未联网刷新」。这不是电视API联网成功；刷新、后续页及无内置详情仍需要API可达。快照不是自动更新数据，跨季度无对应内置快照。
- 当季为北京时间所在季度，2026-11-15对应10–12月；指本季首播/计划首播，不等于今天上线。动画目录可能含MV/短片/剧场版。放送表照录Bangumi星期，API无时刻/时区字段，不虚构时区换算；今天按钮按Asia/Shanghai选星期。榜单按sort=rank综合排名，不是实时播放榜。服务端亦可能缓存，本轮未测定其缓存时长，不承诺实时。

### 测试、构建与实机

- 使用 tools/jdk/jdk-11.0.33+10、tools/sdk，--offline --max-workers=2 :app:testDebugUnitTest :common_component:testDebugUnitTest :player_component:testDebugUnitTest assembleDebug；ani-discover-final-build.log 为 BUILD SUCCESSFUL in 57s，未新增依赖或联网补依赖。
- app本轮23项（新增发现逻辑5、AniRules13、AniTls4、示例1）0失败0错误，最终报告UTC14:54:49。common7及player14为UP-TO-DATE旧报告，不冒称重跑；git diff --check通过，原wrapper换行警告保留。
- 最终APK out/弹弹play-AniSource-TV-discover-armv7a.apk，52979823字节，SHA256=50f60627d8615af23660e76f4f44362b874e3f79cd7ff3c58b86d497aebbe8e1。电视192.168.2.x:5555 adb install -r返回Success；/data/app/com.xyoye.dandanplay-1/base.apk SHA256一致，无授权拦截。旧smooth包52979069字节，SHA256仍6290fab88b24a4a7c2b3c06d3cfad558a611165cd93e10beeaccf077717b5cff，未覆盖。
- 遥控/截图/dumpsys证据：ani-discover-final-home.png显示六入口、真实快照时间及10月20条/总139；ani-discover-detail.png显示黑暗学院 第二季 / Le Collège Noir Season 2 / 2026-10-31；ani-discover-empty-detail.png显示稀饭动漫以中文完整片名搜索返回0条及换源换名说明。真正源请求日志为dm1.xfdm.pro→xifan.moe，非Bangumi假装资源搜索。
- ani-discover-picker.png/ani-discover-girigiri-selected.png验证手动换至girigiri；其他短关键词可出现24条结果，但不等同上述新番匹配成功。未对错误或不匹配条目作内容正确性背书。
- ani-discover-rank.png显示综合排名与快照时间、20条/总9119；ani-discover-calendar.png及day-list.png显示星期入口、来源/时区说明及周日27条；使用input keyevent、screencap、logcat和dumpsys，未使用uiautomator。部分exec-out截图出现黑色/重复，后改为设备文件screencap再pull，不能把黑色截图当可见视频验收。
- 本轮观察到一次AniPlayback解码统计，但未建立同一新番→正确资源匹配→正确集数→可见画面/声音持续播放的完整证据，不宣称本轮新番播放成功；旧smooth播放结论仍以原节证据为准。电视直连刷新、更多页、离线异常/取消全组合、长时播放与CMS/Jellyfin实机回归未完成。

- 补充最终实机证据：ani-discover-help.png 已实际打开使用帮助，显示操作步骤和黄色遥控焦点；ani-discover-delivery.png 为从帮助返回发现首页后的交付截图。电视保持发现页，不停留订阅输入页。

新增用户指南：../使用指南-Ani电视版.md。项目日志追加于工作区 .workbuddy/memory/2026-11-15.md。

## 2026-11-15 21:56 卡顿诊断与小幅修复（历史）

本节覆盖此前性能结论；保留旧包与全部历史未提交修改，未扩源、未清数据、未运行 uiautomator、未安装其他 App，未修改电视全局设置或 TLS/DRM 校验。

### 根因证据

- 不能仅凭 dropping frame 判断软解：初次日志曾创建 OMX.amlogic.avc.decoder.awesome，但驱动计数长时间停在 17 帧；线程负载集中在 mediacodec_jni/JNISurfaceTexture。旧包同集重播完整日志进一步抓到 OMX InsufficientResources、StartMediaCodec failed 后使用 FFmpeg avcodec。
- 首轮诊断包冷启动日志抓到更直接链路：MediaCodec 成功打开，随后 Mali-450 的 OpenGL shader 编译失败、video output display creation failed、UpdateVout failed，再回退 avcodec。原默认 TextureView 路径是本例硬解无法稳定保持的主要兼容性问题；资源不足可能与旧播放器未及时释放有关，本轮未据此重写历史释放逻辑。
- 另有真实媒体代理问题：首轮诊断包 1461700 字节分片只读 475136 字节即在 totalMs=30006 报 InterruptedIOException: timeout，随后发生重复缓冲。媒体流复用了 30 秒整次 callTimeout；这与单次读无数据超时不同，流式消费受到解码/下游背压时会误截断。
- NanoHTTPD 2.3.1 本地字节码确认每个连接独立线程；当前 OkHttp 同步 execute 未增加分片串行锁。未发现证据需要新增预下载、整段内存缓存或大幅调整读写缓冲。本例正常 HLS 顺序取片不能直接当作代理串行瓶颈。
- 仅经当前设备内代理读取同集 9903 字节清单，并用 ffprobe 有界探测首分片：H264 High、Level 5.0、1920×1080、yuv420p、24000/1001 fps；音频 HE-AAC。VLC Track 的 profile/level 返回 -1，故以上 profile 来自 ffprobe 而非伪造 VLC 值。该清单是 media playlist，无 EXT-X-STREAM-INF，不存在已证实的多清晰度 master；未制造清晰度选项或降分辨率，未下载整视频。临时 adb forward tcp:33329 已移除。

### 实际修改（本轮五个源码文件）

- AniTvPage.kt：播放交接增加 ani_tv_playback 标记；只将注入媒体代理的 callTimeout 改为 0（无整次总时限），保留 connectTimeout=15 秒、readTimeout=20 秒、AniTls 证书/hostname 校验及禁止跨协议跳转。AniRepository 页面请求仍维持原 30 秒总时限。
- PlayerInterceptorActivity.kt：透传标记至真正 PlayerActivity。首轮诊断发现中转路由丢标记，因此首轮包 aniTv=false，不冒称 SurfaceView 已在首轮生效。
- PlayerActivity.kt：仅带 Ani 标记且确认为电视的播放会话使用 SurfaceView，不持久化全局播放器设置；VLC 硬解仍为原 AUTO，不强制软件或切换其他内核；其他入口继续尊重原配置。
- VlcProxyServer.kt：默认媒体客户端同样取消整次总时限，保留连接/读超时、TLS、path/query、UA/Referer 和资源关闭修复；DEBUG 记录无 URL/headers 的响应长度、耗时与读字节数。当前关闭可能重复记录，固定长度响应读满不一定额外读到 EOF，日志 eof=false 不单独等同传输失败，应对照 bytes 与 length。
- VlcVideoPlayer.kt：DEBUG 每 10 秒输出进度、视频参数及 decoded/displayed/lost 统计，正确释放 getMedia 引用；未改历史 Surface 恢复及释放队列。未新增纯逻辑策略，沿用已有回归测试，没有宣称新增单测。

### 对照与验收边界

- 旧包同集约 34 秒重播采样：稳定播放阶段应用 top 为 38/46/45/42%；日志含 80 条 dropping frame、208 条 picture too late。这是日志条数而非准确丢帧数，且窗口长度不同，不能当作等长帧率基准。初始 meminfo PSS=316347 KB，Native Heap PSS=199563 KB。
- 最终包日志明确 aniTv=true、VIEW_SURFACE、硬解 AUTO；OMX.amlogic.avc.decoder.awesome/mediacodec 持续工作，无 shader 编译失败或硬解回退。首个 VIDEO_RENDERING_START 为 21:52:06.129，距播放器配置 21:51:58.122 约 8 秒，包含网络初始化，不是精确可见首帧延迟。
- 最终约 102 秒采集，稳定应用 top 为 4–8%（启动阶段 11%），PSS=125421 KB，Native Heap PSS=25288 KB。80.252 秒处 displayed=1959/lost=1，后续 131.257 秒处 displayed=3172/lost=1；驱动帧数由停滞的 17 增至 3342，驱动 drop/hw error 均为 0。约 102 秒窗口只有一条 50ms late 警告、无 dropping frame，无代理总超时及启动后再次缓冲。不同层计数定义不同，不应相加。
- 最终代理分片实际完整读满，例如 1461700 字节/4425ms、2719232 字节/5806ms；并不证明任何网络都稳定，也未做人为限速或超过30秒的负载回归。保持原图像1920×1080、24000/1001 fps，未全局降画质。
- 截图 ani-smooth-final-play.png 为黑色：SurfaceFlinger 同时确认 SurfaceView 硬件合成图层、持续解码输出；设备硬件视频层可能不被 screencap 捕获，但不能据此宣称用户肉眼必然有画面。实际电视画面、声音、主观流畅度、长时间播放、切后台/重播硬件资源释放仍须用户确认；不承诺彻底不卡。

### 构建、安装与产物

- 指定 JDK11 tools/jdk/jdk-11.0.33+10 与 tools/sdk，--offline --max-workers=2 :app:testDebugUnitTest :common_component:testDebugUnitTest :player_component:testDebugUnitTest assembleDebug；最终 ani-smooth-final-build.log 显示 BUILD SUCCESSFUL in 1m21s。app18、player14 项本轮重跑，0失败/0错误；common7 项 UP-TO-DATE，报告仍为旧时间。git diff --check 通过（原 wrapper 换行警告保留）。
- 最终 APK：out/弹弹play-AniSource-TV-smooth-armv7a.apk，52979069 字节，仅 armeabi-v7a，SHA256=6290fab88b24a4a7c2b3c06d3cfad558a611165cd93e10beeaccf077717b5cff。adb install -r 返回 Success，已安装 /data/app/com.xyoye.dandanplay-1/base.apk SHA256 完全相同；启动恢复 girigiri 同条目“夜樱家的大作战 第2季 Part 2”、繁中01实测。未遇安全确认，未绕过权限。
- 原 out/弹弹play-AniSource-TV-armv7a.apk 保留，SHA256 仍为 0d8dc966aa0abf6047209c5d2c389e391052b82205c154a86099a67cbbf87b50；首轮诊断包另存 smooth-diagnostic1-armv7a。
- 主要证据：out/ani-smooth-before-system.txt、before-decoder.txt、before-threads.txt、baseline-replay.txt、baseline-top.txt、after-replay.txt（中转标记丢失及shader/总超时）、final-replay.txt、final-system.txt、final-followup.txt、final-output.txt、format-probe.txt、final-build.log，文件均带 ani-smooth- 前缀。
- 尚未修复：其他源/内核、跨域绝对分片、长期内存与旧播放器释放、网络突发劣化。若同源继续卡，先采 AniPlayback/VlcProxy 与硬解回退日志；本例没有可用多清晰度 master，需源真实提供较低码率版本才可让用户选择，不能伪装降清晰度。

## 2026-11-15 21:12 播放链路最新状态（历史）

本节覆盖下方旧包哈希及“未进入播放器”的结论；历史记录保留。未提交 Git，未回滚既有 CMS/Jellyfin/TV 改动。

- 最终 APK：out/弹弹play-AniSource-TV-armv7a.apk，52977341 字节，仅 armeabi-v7a；SHA256：0d8dc966aa0abf6047209c5d2c389e391052b82205c154a86099a67cbbf87b50。adb install -r 返回 Success，电视 /data/app/com.xyoye.dandanplay-1/base.apk 哈希一致。旧 TLS 包保存 pre-play 备份，中间静态修复包保存 static-only 备份；其他历史 APK 未覆盖。
- 实机验证仅使用 girigiri 一个源，同一条目、同一繁中首集，未扫站，未使用 uiautomator，未遇授权弹窗。关键词 a 搜索、详情选集、公开配置提取均完成。dumpsys window/activity 确认 PlayerActivity 在前台；截图实际显示视频，控制栏进度从 00:45（暂停）恢复为 00:46（播放），随后推进到 01:25 / 24:12，画面变化。证明本例进入播放器并实际解码显示且进度推进，不代表所有源、集数或片名内容准确性通过验收。
- 存在性能限制：VLC 日志出现 late video / dropping frame，不能称为流畅播放验收；声音、长时间稳定性、其他源、跨域绝对分片及不同内核未验收。未为成功而修改解码设置。

### 代码与根因

- 开始时 AniRules.pageCandidates 和测试中已存在未记录的静态解码草稿，本轮保留并实测，不将全部草稿冒称本轮新增。公开集页 /playGV27208-1-1/ 实际含 player_aaaa JSON，encrypt=2、points=0、trysee=0，url 为 Base64 包装的全百分号编码 HTTP 地址。现有静态解析可消除 JSON 斜杠/Unicode 转义，并按模式处理百分号/Base64，保留签名中的 + 与百分号；不执行 JS、不解密 DRM、不绕登录/验证码。
- 对照已有官方 SelectorMediaSourceEngine.kt 的嵌套优先与 v 捕获契约，AniRules 新增 nestedUrl，AniRepository 对嵌套候选使用已校验的捕获地址，而非只判断命中后仍访问包装页。补充全百分号配置和嵌套捕获回归。官方来源与缓存路径沿用下方记录，本轮未声称重新拉取官方 main。
- 中间静态包虽已进入 VLC，但首段即 EOF。日志显示 /0000.ts 的响应仍为 mpegurl；根因是历史 VlcProxyServer 忽略每个请求路径，固定请求主清单。该历史代理还使用 UnsafeOkHttpClient，故中间试播不是严格媒体 TLS 验收。
- 修复 VlcProxyServer：保留上游 origin，按每个本地请求的 path/query 请求真实清单或分片；保留配置 UA/Referer，客户端只转发 Range/If-Range/Accept；Accept-Encoding=identity，正确处理未知正文长度、响应头及关闭上游流，异常失败关闭。仅监听 127.0.0.1，不接受客户端任意指定上游 host。
- 移除 VLC 代理对 UnsafeOkHttpClient 的使用，默认标准 OkHttp 校验证书及 hostname；AniTvPage 在播放前向代理注入 AniTls 的系统优先/官方根补充客户端，超时 15/20/30 秒，禁止跨协议重定向。外部 HTTPS 未降级；127.0.0.1 HTTP 是原有设备内播放器代理，不是远端明文降级。全应用其他 Unsafe 调用未审计，不能据此声称全 App 已安全。
- 本轮实际编辑：AniRules.kt、AniRepository.kt、AniTvPage.kt、AniRulesTest.kt、player/utils/VlcProxyServer.kt；新增 player_component/src/test/java/com/xyoye/player_component/VlcProxyServerTest.kt。未增加 WebView。

### 测试与证据

- 指定 JDK11 tools/jdk/jdk-11.0.33+10、SDK tools/sdk，--offline --max-workers=2 :app:testDebugUnitTest :common_component:testDebugUnitTest :player_component:testDebugUnitTest assembleDebug；ani-play-proxy-build.log 最终 BUILD SUCCESSFUL in 1m39s。中途 NanoHTTPD 无 BAD_GATEWAY 枚举导致一次编译失败，已改为现有 INTERNAL_ERROR 并完整重建通过。
- 本轮重新执行 app 18 项（AniRules 13、AniTls 4、示例 1），player_component 14 项（Exo 6、VLC 恢复 5、代理 2、示例 1），全部 0 失败/0 错误；common_component 7 项 UP-TO-DATE，为旧报告，不冒充重跑。代理测试覆盖分片 path/query、签名保留、请求不能改变 origin；未覆盖完整 HTTP 代理、跨域 HLS 或实机负向 TLS。git diff --check 通过。
- 证据：out/ani-final-play-check.png（视频画面）、ani-final-controls.png（00:45 暂停）、ani-final-resume-check.png（00:46 播放）、ani-final-progress-verified.png（01:25 播放）、ani-final-window-verified.txt、ani-final-activity.txt、ani-final-player-filtered.txt（含丢帧）、ani-play-player-log.txt（中间失败）。日志保留有历史 17:35 uiautomator 异常，不是本轮执行或本轮 App 崩溃。
- 已追加工作区 .workbuddy/memory/2026-11-15.md。后续优先处理老电视解码丢帧和代理跨域清单能力，不扩大站点尝试或引入安全绕过。

## 2026-11-15 TLS 修复后的最终状态（历史）

本节覆盖下方首轮记录中旧的包哈希、TLS 阻塞与未完成选集状态；下方保留为首次实现记录。

- 最终 APK：out/弹弹play-AniSource-TV-armv7a.apk，54847449 字节，SHA256：c6d507810616e4707efb5d0d42ebaa7a97bc787efb3a11a35850414df4db0b16。电视已安装 base.apk 的 sha256sum 与此完全一致。旧 Ani 包另存 out/弹弹play-AniSource-TV-armv7a-pre-tls.apk；未覆盖 CMS/Jellyfin 包。
- 本轮代码：新增 AniTls.kt、AniTlsTest.kt 和 res/raw 下两份官方根证书；AniRepository.kt 接入独立 TLS 配置并仅在 DEBUG 记录请求 host，不记录路径、查询参数、Cookie、Authorization 或 token。未修改 AniRules/AniTvPage，不扩展解析功能。
- 仅 Android API <=23 的 AniRepository 客户端使用补充信任：先尝试系统 X509TrustManager，失败才由标准 TrustManagerFactory 校验仅含两个自签 CA 根的 KeyStore；两者均失败即拒绝。保留默认 OkHttp hostname verification、证书链与有效期校验。API >=24 保持原系统客户端。不信任叶子/中间证书，不使用 trust-all，不新增 HTTP 降级，不修改电视日期、系统证书、代理或安全设置。
- 作用域仅 Ani 页面请求。仓库原有其他模块存在 UnsafeOkHttpClient/hostnameVerifier 放行实现，非本轮引入且本轮未修改或复用；不能据此声称整个应用的所有网络链路均已安全审计。此次播放未进入播放器，不能宣称媒体 TLS 已验收。

### 定位证据

1. 设备日期 2026-11-15 19:58:41 CST，本机同时 2026-11-15 11:58:47 UTC，相差约 6 秒；auto_time=1、Asia/Shanghai。没有时间明显偏差证据。
2. 电视 http_proxy/global_http_proxy_host/global_http_proxy_port/https_proxy/global_proxy_pac_url 均 null；dumpsys connectivity 的活动 Wi-Fi 无 HTTP proxy，显示 NOT_VPN。不能因此排除网络路径上的透明代理。电脑环境 HTTP(S)_PROXY=127.0.0.1:55380、Windows 用户代理启用 127.0.0.1:7890、WinHTTP 为直连；未输出代理凭证。下面 openssl s_client 为直连，不读取这些 HTTP 代理。Google CA 官方下载通过 7890 的 HTTPS 隧道成功，全程未忽略 TLS；Let's Encrypt 根证书直连 HTTPS 获取成功。
3. 新包实机仅 host 日志证实：稀饭请求 dm1.xfdm.pro 后跳转 xifan.moe；girigiri 搜索请求 m3u8.girigirilove.com，详情与剧集页为 bg.girigirilove.com。旧订阅另含 ani.girigirilove.com，但不能把它当成本次实际搜索 host。
4. 电脑同 host 的 issuer/chain（UTC）：dm1.xfdm.pro 叶子 CN=xfdm.pro，WE1 → GTS Root R4（服务器发送 GlobalSign Root CA 交叉签名版本）；叶子有效期 2026-09-24 03:28:21 至 2026-12-23 04:28:12。xifan.moe 叶子由 YR1 → Root YR → ISRG Root X1 链签发；叶子有效期 2026-10-05 07:13:43 至 2027-01-03 07:13:42。m3u8/ani/bg.girigirilove.com 观察到同一叶子 CN=girigirilove.com，YR2 → Root YR → ISRG Root X1；叶子有效期 2026-09-15 07:39:46 至 2026-12-14 07:39:45。各 host 另用 -verify_hostname 校验，不能仅凭 CN 推断域名匹配。
5. 读取电视 /system/etc/security/cacerts 的 158 个根证书，未见 ISRG/GTS，存在 GlobalSign Root CA。将设备根导出至电脑做正常 OpenSSL 校验：dm1.xfdm.pro 可经 GlobalSign 链验证成功；m3u8.girigirilove.com 无法构建信任链。使用官方 ISRG Root X1 后 girigiri 和跳转后的 xifan.moe 均正常验证；使用官方 GTS Root R4 后 dm1 正常验证。证据支持旧系统缺少后续 host 所需信任根，不支持把稀饭初始 dm1 host 本身说成必然不可信。
6. 负向电脑校验：girigiri 配 ISRG 根但故意错误 hostname 时失败 code 62；配无关 GTS 根时失败 code 20。这是电脑 OpenSSL 结果，不冒充 Android 实机负向证书测试。

### 官方 CA 来源、指纹和移除

以下为证书 DER 的 SHA256，不是 PEM 文本文件哈希；下载后核对 subject=issuer、自签签名和 basicConstraints CA:TRUE。仅存储下列自签根，不把服务器发来的交叉签名中间证书加入信任库。

- GTS Root R4：官方 https://pki.goog/repo/certs/gtsr4.pem ，仓库 https://pki.goog/repository/ （本机访问仓库页失败，证书文件经 HTTPS 成功获取）；资源 app/src/main/res/raw/gts_root_r4.pem；指纹 34:9D:FA:40:58:C5:E2:63:12:3B:39:8A:E7:95:57:3C:4E:13:13:C8:3F:E6:8F:93:55:6C:D5:E8:03:1B:3C:7D；有效期 2016-06-22 至 2036-06-22。
- ISRG Root X1：官方 https://letsencrypt.org/certs/isrgrootx1.pem ，官方目录 https://letsencrypt.org/certificates/ ；资源 app/src/main/res/raw/isrg_root_x1.pem；指纹 96:BC:EC:06:26:49:76:F3:74:60:77:9A:CF:28:C5:A7:CF:E8:A3:C0:AA:E1:1A:8F:FC:EE:05:C0:BD:DF:08:C6；有效期 2015-06-04 至 2035-06-04。
- 移除办法：将 AniRepository 的 AniTls.configure(OkHttpClient.Builder(), context) 恢复为 OkHttpClient.Builder()，移除 AniTls.kt、AniTlsTest.kt 和两份 res/raw 根证书，重新构建并 adb install -r；仅移除其中一个根则同时移除 AniTls 列表引用。无系统证书需卸载。恢复旧包也会恢复旧 TLS 阻塞。

### 测试、安装与实机验收

- JDK11 tools/jdk/jdk-11.0.33+10、SDK tools/sdk，执行原命令 --offline --max-workers=2 :app:testDebugUnitTest :common_component:testDebugUnitTest assembleDebug；ani-tls-build.log 显示 BUILD SUCCESSFUL in 1m 38s，未联网安装依赖。
- 本轮 app 测试实际执行：AniRulesTest 9、AniTlsTest 4、ExampleUnitTest 1，全部 0 失败/0 错误。TLS 测试验证系统优先、补充回退、双拒绝不放行及不补充客户端证书信任；不是完整实机攻击面测试。common_component 7 项报告为旧时间，任务 UP-TO-DATE，不冒充本轮重跑。
- adb install -r 文件推送完成后，主机等待 90 秒超时，未收到 Success 文本；电视日志显示 dex2oat 后包更新，lastUpdateTime=2026-11-15 20:14:43。之后实际安装 base.apk SHA256 与最终包一致，SplashActivity 启动后 MainActivity 正常；未遇安装授权弹窗，未修改任何安全设置，不把主机超时误写成安装失败或 Success 返回。
- 仅用 adb input/keyevent、dumpsys、screencap；未运行 uiautomator。历史 logcat 包含 17:35 的旧 uiautomator 系统权限异常，不是本轮执行或本轮 App 崩溃。
- 稀饭：真实 naruto 搜索访问 dm1.xfdm.pro 并跳转 xifan.moe，TLS 不再报错，界面显示无结果。只证明请求/解析到空列表，不证明稀饭已搜出条目，也不能单凭通用提示断言验证码或动态 JS 原因。
- girigiri：naruto 为无结果；换关键词 a 后实机显示真实条目，包括“夜樱家的大作战 第2季 Part 2”。确认进入详情后显示“繁中 · 01/02/03/04/05”等选集。
- 确认“繁中 · 01”后实际请求 bg.girigirilove.com，但提示未找到静态 HTTP 视频；dumpsys 仍是 MainActivity，未进入播放器。没有真实视频直链或播放成功证据，不宣称全链路成功，也未扩展脚本/加密解析。下一步如继续播放适配，应单独核对该剧集页的真实返回与现有静态提取规则，不再把此问题归因于 TLS。
- 截图：out/ani-tls-before.png、ani-tls-launched.png、ani-tls-xifan-result.png、ani-tls-girigiri-result.png、ani-tls-girigiri-a.png、ani-tls-episodes.png、ani-tls-play-attempt.png。

## 首轮实现记录（以下包与状态已被本节更新）

## 实际交付

- APK：out/弹弹play-AniSource-TV-armv7a.apk，54847310 字节。
- SHA256：0276aa0f3eb2cb9c08e5d01a13a90c3b9c3bc491210a3f641ce5c7d3e1648183。
- 旧 CMS/Jellyfin/TV 安装包保留；本轮首测包另存为 out/弹弹play-AniSource-TV-armv7a-first-check.apk，不是最终推荐包。
- Git 起始状态已有大量未提交修复和 Ani 草稿。本轮在草稿基础上实际修改 AniRules.kt、AniRepository.kt、AniTvPage.kt、AniRulesTest.kt；未回滚或重写既有 CMS/Jellyfin 修复。

## 官方格式依据

核实 open-ani/animeko 官方 main 分支的下列文件（2026-11-15 访问，未锁定提交，后续可能变化）：

- https://github.com/open-ani/animeko/blob/main/app/shared/app-data/src/commonMain/kotlin/domain/mediasource/subscription/SubscriptionUpdateData.kt ：schemaVersion 当前为 2；兼容 mediaSources 与 exportedMediaSourceDataList.mediaSources。
- https://github.com/open-ani/animeko/blob/main/app/shared/app-data/src/commonMain/kotlin/data/repository/media/MediaSourceSubscriptionRepository.kt ：官方默认配置引用 https://sub.creamycake.org/v1/css1.json 。它是官方引用的第三方公开订阅，不宣称其中站点由官方运营。
- https://github.com/open-ani/animeko/blob/main/app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/SelectorSearchConfig.kt ：searchUrl、rawBaseUrl、requestInterval、userAgent、matchVideo、addHeadersToVideo、cookies 等字段。
- https://github.com/open-ani/animeko/blob/main/app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/format/SelectorSubjectFormat.kt ：a、indexed、json-path-indexed；linkTemplate 使用 {value}。
- https://github.com/open-ani/animeko/blob/main/app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/format/SelectorChannelFormat.kt ：no-channel、index-grouped；线路及剧集 CSS 字段。
- https://github.com/open-ani/animeko/blob/main/app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/SelectorMediaSourceEngine.kt ：嵌套页面优先、视频正则命名组 v，以及 UA/Referer。

本轮联网实际获取公开订阅，观察到 21 个条目，包含 web-selector version=2 和 json-api version=1。实现仅针对已核实 Selector 子集，不将 CMS 当作 Ani，不执行订阅代码。

## 实现范围

- 首次进入 Ani 页且没有本地订阅时，自动下载公开默认订阅；支持输入订阅 URL 导入/更新、保存订阅正文和 URL、源启停和选择记忆。
- 纯规则解析支持 a/indexed CSS、有限 JSONPath（点路径、数组索引、数组通配符、字段联合）、{keyword}/{value} 模板、分组/无线路选集及视频正则提取。
- 导入时检查 schema/factory/version、格式 ID、模板、CSS、JSONPath 和正则；不支持的源保留条目并明确显示原因。
- 命名捕获转换为数字捕获，避免 Android 6 依赖 Matcher.group(String)；实机发现并修复 Android 正则对未转义闭括号的兼容差异。
- 网络使用 OkHttp enqueue 和可取消协程，连接 15 秒、读取 20 秒、单请求总时长 30 秒，界面操作 60 秒上限；返回、取消或页面销毁时取消工作。
- 只接受 HTTP/HTTPS，拒绝 URL 账号密码与控制字符；限制跳转次数、正文大小和列表规模。保留规则 UA、Referer、Cookie；未安装不安全 TrustManager、未忽略证书。
- 静态扫描 video/source、页面明文 HTTP 链接、iframe 与规则匹配的嵌套页面，限制深度和候选数。
- 系统 Button/EditText/ScrollView，方向键/确认/返回，保留 TV 首页 CMS/Jellyfin 分离入口。
- 播放交接继续使用 LinkStorage.setupHttpHeader → StorageVideoSourceFactory.create → VideoSourceManager.setSource → ARouter RouteTable.Player.Player。

## 测试和构建

使用 tools/jdk/jdk-11.0.33+10、tools/sdk：

```sh
gradlew --offline --max-workers=2 :app:testDebugUnitTest :common_component:testDebugUnitTest assembleDebug
```

最终日志：ani-verified-build.log，BUILD SUCCESSFUL in 54s。未为本轮构建联网补依赖。

- AniRulesTest：9 项、0 失败、0 错误，覆盖订阅新旧封装、不支持规则、CSS、JSONPath、中文关键词编码、选集、命名捕获、嵌套优先、请求头和非法 URL。
- app 示例测试：1 项通过。
- common_component CMS 身份回归 6 项与示例 1 项已有通过结果；本轮 Gradle 判定 UP-TO-DATE，未把缓存结果说成本轮重新执行。
- git diff --check 通过（现有 wrapper 文件有换行转换警告）。

## Android 6 电视实际结果

设备：192.168.2.x:5555，读取版本 Android 6.0.1。最终包 adb install -r 返回 Success。启动 SplashActivity 成功进入 MainActivity，无强制 AppID 弹窗。

通过 input keyevent、dumpsys、screencap 验证：

1. 首页大按钮和焦点可见，确认可进入 Ani。
2. 公开订阅自动导入，重装保留应用数据后可重新加载；修复后稀饭动漫显示启用。
3. 遥控可选源、进入搜索、打开输入框、输入 naruto、提交搜索、返回选源、切换 girigiri。
4. 稀饭与 girigiri 的真实搜索请求均被设备证书验证拒绝：java.security.cert.CertPathValidatorException: Trust anchor for certification path not found。未忽略 SSL、未降级 HTTP、未修改系统证书。该错误不能单凭截图进一步归因于站点或网络。
5. 因真实搜索未通过 TLS，本轮没有验证出搜索条目、真实选集、HTTP 视频直链和电视播放成功。不能将代码路径接通、单元测试通过或 APK 启动成功描述成全链路播放成功。

截图：out/ani-tv-home.png、out/ani-tv-sources-fixed.png、out/ani-tv-search.png、out/ani-tv-search-final.png、out/ani-tv-second-source.png、out/ani-tv-second-result.png。未使用 uiautomator。

## 明确限制

- 这是规则兼容 MVP，不是完整 Animeko 引擎，不保证所有源可用。json-api、脚本源、额外模板变量和播放器白名单限制尚不支持。
- 不使用 WebView，不执行 JavaScript；需要动态 JS、验证码、加密解析或 DRM 的站点明确失败，不绕 DRM。静态解析不等于浏览器网络嗅探。
- 未复刻自动番名匹配、自动集数归类/筛选、全量 JSONPath、分页、登录验证、站点响应 CookieJar、解码嵌入式加密播放器配置等完整行为。
- JSONPath 名称/链接数不一致时拒绝，仍非完整的任意结构逐项关联引擎。
- 协程超时可取消网络等待，不是隔离执行沙箱，不能硬中断任意 Java 正则 CPU 回溯；只应导入信任的订阅。
- 设备 TLS 信任失败是当前实机阻塞；后续须在维持证书校验的前提下定位证书链/网络，才能继续端到端选集和播放验收。
