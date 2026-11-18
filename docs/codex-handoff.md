# Codex 接续记录 · 2026-11-18

> 当前操作边界：用户于2026-11-18明确表示“可以连接电脑调试了”，已恢复本次设备调试授权。允许通过现有 ADB 连接进行验收与必要的调试更新；保留订阅、播放配置和用户数据，不改变电视网络/安全设置，不使用 uiautomator。安装遇到系统确认时交给用户操作。下文11月17日暂停记录属于历史边界，不能覆盖这次新授权。

## 工作区

- 后续开发使用整理后的独立仓库 `ddplay-ani-tv`，不把原 `ddplay/repo` 的历史未提交改造再次混入。起点为 `db7a308`。
- GitHub 私有仓库：<https://github.com/chufeng/BoroAni>，原名 `ani-tv`，已在 GitHub 仓库设置中更名。
- `origin` 为 `https://github.com/chufeng/BoroAni.git`。本机 `gh` 尚未登录，Git HTTPS 可通过现有凭据访问远端。

## 已完成的开发

- 新增 `AniSearch.kt`，统一手动搜索与详情搜索。详情先搜短词，手动输入先搜原词；无结果后重试另一关键词及备用源。
- 每轮最多3个已启用、受支持、去重后的源，每源最多2个关键词，顺序请求；单次尝试含站点限速等待最多8秒。网络/解析错误直接转向下一源；页面总时限仍为60秒。
- 取消、返回和外层超时继续传播，不触发后续换源。保留 `AniRepository` 原有请求间隔和 HTTP 取消机制。
- 结果页显示每次实际源、关键词、条数或失败原因；命中后停止，当前源和选集/播放使用的源同步切换，焦点移到首条结果。短词可能命中其他季数，需用户核对。
- 输入法关闭改为安全类型转换并捕获异常，避免搜索提交被输入法服务异常打断。
- 更新 README、应用内帮助和使用指南；保留原有按钮描边修复、TLS 校验、minSdk 21，以及 CMS/Jellyfin 独立入口。

## 验证

- 使用交接指定的便携 JDK 11、Android SDK 33 和现有 Gradle 缓存，离线运行 `:app:testDebugUnitTest` 与 APK 构建。
- 2026-11-17 首轮构建成功，app 实际 XML 报告54项测试、0失败、0错误、0跳过；包含新增搜索测试10项。覆盖关键词处理、短词优先、原词优先、换源上限、源过滤、结果源归属、HTTP 错误、单次超时、取消及外层超时。
- 最终构建日志位于原工作区 `ddplay/out/ani-codex-search-build.log`。APK 的版本号仍为4.1.4（61），可覆盖调试安装。
- 最终源码再次离线执行 `:app:testDebugUnitTest :app:assembleDebug`，44秒成功；app XML复核仍为54项、0失败、0错误。`aapt dump badging` 确认 minSdk 21、targetSdk 29、版本4.1.4（61）。
- 交付包（原工作区 `ddplay/out/`）：`弹弹play-AniSource-TV-searchfix-armv7a.apk`，SHA256 `325e6d98cc06ca17bd417a3fee2014e38d080c25359fa74aa841800c311e2851`；`弹弹play-AniSource-TV-searchfix-arm64.apk`，SHA256 `877c3b2ac79f769d1f38cb184914491739202d54b7bc367420e29c1c6cc0cc1f`。
- 本轮未执行 common/player 模块单测；未将旧报告算作本轮结果。

## 尚待设备验收

- 本轮初次 `adb devices` 未列出电视，因此未安装 APK，也未把单测结果当成电视端实际搜索、播放或焦点验收。
- 新包同时包含交接中尚未实机验证的按钮左侧裁切修复；需要电视恢复 ADB 后检查。
- 实机建议路径：详情长片名搜索 → 查看短词与换源记录 → 核对条目季数 → 选集播放；另测搜索中返回取消、手动原词搜索、无结果/HTTP 错误提示和按钮描边。
- 电视不要使用 `uiautomator`；安装遇电视确认框需等用户确认，不修改系统网络或安全设置。

## GitHub 授权与网络备注

- `gh auth login` 直连设备授权端点超时；使用 Windows 已启用的本机代理后返回 EOF，未完成 CLI 授权。
- 用户明确授权改用已登录浏览器后，已创建私有空仓库。Git 通过既有凭据和进程级代理能读取该仓库。
- 工程初始提交和本轮搜索改进已成功推送至远端 `main`，搜索实现提交为 `e428a05c5ae9d3061cf9fcc87034017d315abfc6`；`git ls-remote` 与浏览器仓库文件列表、更新后的README均已核对。
- 网络参数只作用于单次命令，没有改系统代理、全局 Git 或 Gradle 配置；不要提取浏览器 Cookie/令牌来代替正规授权。


## 2026-11-17 续：老爷机追番、电视排版与中文优先

### 已落地

- 安装显示名、Ani 左侧品牌、电视主菜单和 MainActivity 标题改为“老爷机追番”；保留 `com.xyoye.dandanplay`、versionCode61、minSdk21、targetSdk29，以原签名覆盖并保留订阅。
- 修复滚动列表 `clipChildren=false` 导致内容画到固定页头的根因；移除焦点1.02倍缩放，底栏与左右描边不再出界；取消导航前置圆点和空格，标签统一左内边距。
- 实机另发现 Android6 赋值焦点背景会把 View padding 替换成 drawable inset，已在 `TvDesign.focus` 保存并恢复四边 padding。最终电视截图确认发现/榜单等标签与底栏文字均有内边距。
- 通用按钮22sp、导航20sp、片名21sp、标题23sp（比原版略小）；取消按钮、片名、状态文本的省略号限制，片名按实际高度换行。超长条目整卡可滚动到完整内容。
- AniList native 正确归入 original，不再冒充 chinese。加入 `AniChineseTitles.kt` 与固定版本本地片名快照：7,825个显式 AniList ID → 中文 / 原名；51个冲突 ID 跳过。无网络新增依赖，不进行模糊匹配和猜译；详见 `docs/chinese-title-attribution.md`。
- 发现、放送及榜单读取网络/缓存后都应用中文映射；评分、顺序、放送数据保持 AniList 定义。实机当季页面中文覆盖12/20，榜单19/20，缺译名保持日语。
- 搜索先穷尽中文短词/完整中文在最多3源的尝试，再以日语原名回退；网络失败源不再因换语言被重复请求。保持单次8秒、整轮60秒及取消传播。

### 本次验证

- 最终源码离线运行 `:app:testDebugUnitTest :app:assembleDebug`，BUILD SUCCESSFUL（41秒）；61项 app 测试，0失败、0错误、0跳过。日志：原 ddplay/out/ani-laoye-ui-build-verified.log。
- 新增中文映射测试4项，搜索语言顺序及网络失败回退测试3项；覆盖真实内置文件的 ID5114、中文缺失日语回退、非法 ID 和先中文备用源后日语。
- `aapt dump badging` 确认 `application-label=老爷机追番`、minSdk21、targetSdk29、version4.1.4(61)。
- 最终 ARMv7 APK 已 `adb install -r` 返回 Success，MiTV4_ANSM0 `192.168.2.7:5555` 显示最后安装时间2026-11-17 21:17:29。SplashActivity 正常进入 MainActivity，用户配置保留。
- 最终截图：原 ddplay/out/laoye-final-top.png（左导航有内边距、品牌已改）；laoye-final-bottom.png（超长中文全换行、底栏焦点两端完整、页头无穿透）。榜单中文19/20记录见 laoye-ranking-before-padding.png（该截图在最后 padding 补修前，译名与数据逻辑相同）。
- 最后准备继续验收榜单时观察到电视已进入播放器，已停止进一步遥控以免干扰用户。不能把播放器黑色截图或时长显示当作已出画/播放成功；本次未确认实际视频画面。
- 不使用 uiautomator，不清用户数据，不调整电视安全/网络设置。

### 最终产物（原 ddplay/out）

- 老爷机追番-TV-armv7a.apk，SHA256 `4297F11A74CFC1F6F6C11B85DD255465D6319A5114EFF25265EE589E48BC8DD2`，已装电视。
- 老爷机追番-TV-arm64.apk，SHA256 `D74F50D03D8FA5C0A6AB29D519CCEA4F33AD1FD477686286154A8DD042F4F0D7`。

- 用户随后明确要求使用电视，当前仅完成代码部分；未再控制电视或安装 APK。后续会话遵守本文顶部操作边界。

## 2026-11-18 续：恢复调试与播放网络诊断

- 用户重新授权连接调试；通过现有 ADB `192.168.2.7:5555` 连接 MiTV4_ANSM0。当天开始时安装的仍是11月17日21:17:29版本，未清数据。
- 已实机确认发现页联网、榜单中文19/20、中文优先搜索与选集链路。选中《葬送的芙莉莲》后，实际搜索首词为中文，一次请求得到2条结果（含第二季）；核对后进入原作选集。
- 焦点与排版观察：左导航内边距正常，滚动内容没有画到固定标题后，搜索结果底部卡片两端描边完整；播放失败后退出，焦点恢复到原第01集，再返回搜索也恢复到原条目。截图在原工作区 `ddplay/out/laoye-nov18-*.png`。
- 第01集、叽哔2线未播放成功：09:49:32日志两次 `VlcProxy UnknownHostException`。另有 Retrofit 对 `v3.tlkqc.com` 的 DNS 失败，但旧代理日志没有 host，尚不能断言两者同源，也不能据此认定应切换解码内核。未确认视频画面或音频。
- 已补充媒体代理失败分类：DNS、连接超时、TLS校验、连接失败和HTTP错误；Ani VLC播放错误框优先显示实际网络原因，读流阶段错误也记录。每次播放重置状态，旧请求的迟到错误不会污染新媒体；记录不包含完整媒体URL、签名或请求头。异常日志只输出host与类型。保持TLS校验和原播放器配置。
- 离线执行 `:player_component:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`，BUILD SUCCESSFUL（1m22s）。本轮XML报告app61项、player17项，共78项，失败/错误/跳过均0；新增诊断测试3项。日志：`ddplay/out/laoye-nov18-network-build.log`。
- 新ARMv7调试包：`ddplay/out/老爷机追番-TV-network-armv7a.apk`。已执行覆盖安装请求，电视出现系统“安装确认”对话框；**此记录写入时尚未点击、尚无Success，不视为安装完成**。不得自动操作系统安全确认或勾选“应用到全部”。待用户在电视确认后，再读取安装结果并复测同集，确认新错误提示及媒体host。
- 待续安装进程session_id为57600，可先读取其结果；如会话失效，先查包最后更新时间和当前画面，避免重复安装。旧包与订阅配置保留。当前电视前台是上述安装确认框。
- 播放仍为待验收项；网络提示改进不能证明上游域名恢复或实际播放成功。

## 2026-11-18 续：电视界面改版

- 参考 [Jellyfin Android TV](https://play.google.com/store/apps/details?id=org.jellyfin.androidtv)、[Emby for Android TV](https://emby.media/emby-for-android-tv.html) 和 [Android TV 卡片规范](https://developer.android.com/design/ui/tv/guides/components/cards)。改为深色背景、图标导航与首屏海报网格；去掉重复导航和装饰横幅。常规说明改成正文，资料来源和搜索记录在单独的对话框里查看，详情和选集的操作独立布局。
- 原月份标签按当季生成，但同季三个标签实际请求相同的 `season` 和 `seasonYear`。现在改成上一季、本季、下一季，标题显示季度和实际月份范围。默认跟随北京时间当季，手动选择后保留用户选择。分页使用 AniList `hasNextPage`，后续榜单延续名次。
- 离线非增量构建 `:app:testDebugUnitTest :app:assembleDebug` 成功，63 项单测通过，包含北京时间跨年与 AniList 分页。电脑请求 AniList 验证 2026 秋季和 2027 冬季返回不同条目，均有下一页；不等同于电视端验收。
- 新 APK 位于原工作区 `ddplay/out/老爷机追番-TV-新版界面-armv7a.apk` 和 `ddplay/out/老爷机追番-TV-新版界面-arm64.apk`。电视仍挂着上次旧网络诊断包的系统安装确认，最后更新时间仍是 11 月 17 日；本轮未提交新的安装请求，未实机验收新版。下次安装前先处理旧确认框，再在新包上检查首屏与遥控焦点、跨季、搜索和选集。

## 2026-11-18 续：透明占位图与实机验收

- Magpie GPT Image 2.6 生成复古电视占位图。APK 仅打包 192 × 192、4262 字节的透明 WebP；优先展示真实海报或缓存，生成原图仅留在本地。最终版构建成功，app 单测 63 项通过。
- 处理了遗留的旧安装提示，覆盖安装 `ddplay/out/老爷机追番-TV-透明素材-armv7a.apk` 成功。电视记录的更新时间为 14:37:07，保留订阅和设置。
- 实机确认 1080p 新番页显示海报网格与季度控件。打开《赛博朋克：边缘行者 2》，详页左侧显示海报，右侧以正文显示片名、原名、日期、评分与集数状态；现有 AniList 请求没有拉取完整剧情简介。截图在原工作区 `ddplay/out/laoye-new-season-detail.png`。
- 电视尚未实测跨季、缺封面占位和新版播放诊断。上文待确认的网络诊断包安装记录为历史状态。
