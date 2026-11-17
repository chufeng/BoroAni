# Codex 接续记录 · 2026-11-17

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
