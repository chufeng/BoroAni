# 老爷机追番

面向低配 Android 电视的追番界面。发现、榜单和搜索优先中文，缺失时使用日语原名。中文译名来源和修改说明见 [资料署名](docs/chinese-title-attribution.md)。

## 项目来源

> 基于开源项目 [DanDanPlayForAndroid](https://github.com/xyoye/DanDanPlayForAndroid)（弹弹play 概念版）改造的 **Android TV 看番客户端**。
> 目标是把「选源 → 搜番 → 选集 → 在线播放」这条链路搬到电视遥控器上，而不只是给手机播放器加接口。

上游项目采用 Apache License 2.0，本项目代码同样以 Apache-2.0 发布，详见 [LICENSE](LICENSE)；内置中文片名数据单独遵循 CC BY 4.0，详见[资料署名](docs/chinese-title-attribution.md)；上游原始说明保留在 [README.upstream.md](README.upstream.md)。

![hero](docs/assets/hero.png)

## 这是做什么的

原本的弹弹play 概念版是手机端本地/网络播放器，界面按触屏设计。本改造面向**老电视盒子**（实测 Xiaomi MiTV4，Android 6.0.1，32 位 ARM）：

- **电视原生界面**：左侧图标导航、深色海报网格、遥控方向键焦点，启动不弹 AppID。
- **Ani 规则源**：兼容 Animeko（open-ani/animeko）订阅中的 Selector 静态子集，导入订阅后可选源、搜索、选集、在线播放。
- **番剧资料**：AniList 提供当季新番、高分榜、每日放送（国内可直连）。
- **播放链路**：沿用项目自带的 ExoPlayer + VLC，HLS/MP4 直链，UA/Referer 透传到播放器；电视会话切换 SurfaceView 以稳定硬解。
- **可选**：Jellyfin 媒体库、苹果 CMS 采集源（早期方案，保留但非主线）。

最低支持 **Android 5.0（minSdk 21）**，主要面向 armeabi-v7a。

## 功能一览

| 模块 | 能力 |
|---|---|
| 发现 | 北京时间当季新番，支持上一季、本季、下一季；每日放送、高分榜与海报分页 |
| 源管理 | 导入 Ani 订阅 URL（或官方引用的公开订阅）、启用/停用、选择当前源 |
| 搜索 | 详情先中文短词及完整中文、再日语；手动输入先搜原词；最多3个已启用源，显示逐次结果与错误 |
| 播放 | 选集、直链提取、UA/Referer 透传、续播、电视硬解 |
| 弹幕 | 播放页手动搜索绑定（自动按流哈希匹配在流媒体上不适用，已排除） |
| 附加 | Jellyfin 媒体库、CMS 采集源、播放历史 |

## 构建

环境：JDK 11、Android SDK Platform 33、Build-Tools 33.0.2、Gradle 7.4（随 wrapper）。

```bash
# 1. 指定 SDK 路径
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 2. 编译
./gradlew assembleDebug

# 产物
app/build/outputs/apk/debug/dandanplay_v4.1.4_armeabi-v7a-debug.apk
```

单元测试（解析与规则逻辑，不需要设备）：

```bash
./gradlew :app:testDebugUnitTest
```

## 使用

1. 安装 APK，打开即进入电视首页（无需弹弹play 账号）。
2. 进入「发现番剧」→ 左侧选「新番 / 日历 / 榜单」浏览，或用「搜索」。
3. 首次使用到「源设置」导入 Ani 订阅（可点「导入官方引用的公开默认订阅」），启用并选择一个源。
4. 在番剧详情点「搜索资源」→ 选匹配条目 → 选集 → 播放。

更详细的按键说明与常见问题见 [docs/user-guide.md](docs/user-guide.md)。

## 目录与主要改动

新增（`app/src/main/java/com/xyoye/dandanplay/ui/main/`）：

| 文件 | 作用 |
|---|---|
| `TvHome.kt` | 电视首页与侧栏导航 |
| `TvDesign.kt` | 电视设计系统：配色、卡片、焦点、海报、输入框 |
| `AniTvPage.kt` | 发现/搜索/选集/播放的主页面与状态机 |
| `AniRules.kt` | Ani Selector 订阅解析、搜索/详情/选集/视频提取 |
| `AniRepository.kt` | 订阅加载、搜索、选集、直链解析（含静态配置解码） |
| `AniSearch.kt` | 片名缩短、顺序换源、超时取消与搜索尝试记录 |
| `AniTls.kt` | 旧安卓缺失根证书的补充信任（保留完整链与 hostname 校验） |
| `AniDiscoverRules.kt` / `AniDiscoverRepository.kt` | 番剧资料解析与缓存 |
| `AniListRules.kt` | AniList GraphQL 新番/榜单/放送表 |

播放相关改动：`PlayerActivity`（电视 SurfaceView 会话）、`PlayerInterceptorActivity`（标记透传）、`VlcProxyServer`（按 path/query 转发、严格 TLS、取消总超时）、`VlcVideoPlayer`（调试统计与释放）。

`docs/dev-notes.md` 记录了逐轮诊断过程、实测数据与踩过的坑，包括：

- Bangumi（api.bgm.tv）在实测网络中 DNS 污染 + TLS 握手被阻断，故番剧资料改用 AniList；
- B站番剧索引季度参数失效（长期停更），不适合做新番来源；
- 旧电视硬解失败回退软解的定位（TextureView → SurfaceView）；
- 媒体代理 30 秒总超时截断分片导致反复缓冲的修复；
- 卡片左侧描边被 View 边界裁切的原因与自绘方案。

## 已知限制

- **Ani 规则仅支持静态子集**：CSS/正则/有限 JSONPath、无线路与分组选集。`json-api`、脚本源、需要 WebView 动态解析、验证码、登录或 DRM 的站点明确不支持，也不做绕过。
- **不内置任何内容源**，不提供、不托管视频；订阅与站点由使用者自行添加，请只访问你有权访问的内容。
- 番剧资料（AniList）与资源源是两条链路：资料有条目不代表源里有片。
- 剧集匹配依赖片名；详情搜索优先核心词，手动搜索优先输入原词。每轮最多尝试3个已启用受支持源、每源2个关键词，单次请求含限速等待最多8秒。命中后停止并使用实际命中源选集；请自行核对番名与季数。
- 未实现：自动番名匹配、弹幕按名称自动匹配、多清晰度切换、跨域分片 HLS。

## 致谢与合规

- 上游：[DanDanPlayForAndroid](https://github.com/xyoye/DanDanPlayForAndroid)（Apache-2.0），播放器、弹幕、存储抽象等基础能力来自该项目。
- 规则格式参考：[open-ani/animeko](https://github.com/open-ani/animeko) 的 Selector 源定义。
- 番剧元数据：[AniList](https://anilist.co) 公开 GraphQL API。
- 本项目仅做客户端与界面改造，不包含任何受版权保护的视频内容，不绕过任何付费、登录或 DRM 机制。
