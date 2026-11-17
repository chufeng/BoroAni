# 中文片名资料来源

本项目内置的中文片名来源为 bangumi-data 维护者及贡献者的公开数据，依 CC BY 4.0 使用。

- 项目：https://github.com/bangumi-data/bangumi-data
- 固定版本：6f73ef56c4be3971a9617b0f43b15647136d22f7
- 原始文件：https://github.com/bangumi-data/bangumi-data/blob/6f73ef56c4be3971a9617b0f43b15647136d22f7/dist/data.json
- 许可：https://creativecommons.org/licenses/by/4.0/
- 采集日期：2026-11-17；这是随安装包提供的译名快照，不是实时翻译服务。
- 修改：只保留显式 AniList ID、首个简体中文译名（无简体时首个繁体译名）及原名；中文原创条目允许使用原始中文名称。忽略空译名、无效 ID，以及同一个 AniList ID 对应多个不一致名称的51项。
- 生成文件：`app/src/main/assets/ani-discover/chinese-titles.json`，共7,825条；原始文件 SHA256：`1770784755f646add9589d1cd1d84a9ff2cd854773f5d167653105b661320484`。

只按明确的跨站 AniList ID 关联片名，不用模糊匹配、机器猜测或相近季数代替。排序、评分和放送时间仍来自 AniList；中文译名缺失时显示日语原名，原名缺失才显示罗马字。应用的列表来源行显示本页中文覆盖数及资料署名。
