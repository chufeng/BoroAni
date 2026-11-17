# Codex 接续记录 · 2026-11-17

> 当前操作边界：用户已明确要使用电视。从这条要求起，只进行代码、文档、本机构建和 Git 保存；停止电视遥控、截图、ADB 查询及 APK 安装，设备验收等待用户重新授权。最近一次设备观察与安装记录在本文末尾，均发生在该要求之前。

## 工作区

- 后续开发使用整理后的独立仓库 `ddplay-ani-tv`，不把原 `ddplay/repo` 的历史未提交改造再次混入。起点为 `db7a308`。
- GitHub 私有仓库：<https://github.com/chufeng/ani-tv>，通过用户已登录的 Chrome 创建。
- `origin` 为 `https://github.com/chufeng/ani-tv.git`。本机 `gh` 尚未登录，但 Git HTTPS 的远端读取已成功；无需为建仓再次申请连接器权限。

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
