# 隐藏 TAB 延迟图标

将此目录复制到 `plugins/CraftEngine/resources/hide-tab-ping/`。
通过现有 CraftEngine 工作流重新生成、校验并发送资源包后生效。
当前服务器的工作流名称为 `default`，命令为 `/ce workflow default`。

覆盖原版 `assets/minecraft/textures/gui/sprites/icon/` 下的
`ping_1.png` 至 `ping_5.png` 和 `ping_unknown.png`，均为 10×8 全透明 RGBA PNG。
路径及尺寸依据本地 Minecraft 26.1 客户端 JAR 核对。

接受服务器资源包的玩家将看不到 TAB 的全部延迟图标，包括普通玩家的信号格。
图标原来占用的布局宽度仍由客户端保留。本修改不更改实际延迟和 TAB 排序。

恢复：移除此目录，再执行同一工作流重新生成并发送资源包。
