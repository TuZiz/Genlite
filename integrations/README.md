# Invero 服务器主菜单

- 只保留 `invero/server-main.yml` 一个菜单，三行布局，两个功能按钮。
- 玩家执行 `/菜单`、`/cd` 或 `/servermenu` 打开同一菜单；使用 Invero 原生命令绑定，不授予管理命令权限。`/menu` 是 Invero 自带的管理命令，不可重复绑定。
- 回到主城：异步传送到 `world` 世界的当前出生点；不写死坐标，不创建或强制加载世界。
- 回到空岛：执行 `dreamskyblock:home gui`，进入 DreamSkyblock 原空岛菜单；创建、邀请、成员、设置和回岛功能继续由原插件处理。
- DreamSkyblock 的 `invero.yml`、`arcmenus.yml` 均关闭接管，避免空岛入口被重新导回 Invero。
- 原 `gui/island.yml` 第四行中心加入全息字开关；复用 `genlite:genlite holograms menu`，保存后返回原空岛菜单。需正常的 `dreamskyblock.use`、`genlite.use` 权限，不绕过原权限检查。
- 全息字默认开启、按家园保存，共同主人共享，不影响生产；不改机器、燃料、库存和玩家存档。
- 安装目录为 `plugins/Invero/workspace`。升级时备份并移走旧 `dreamskyblock-main.yml`、`dreamskyblock-island.yml`，备份必须放到 workspace 外。
- 当前本地环境：Invero 1.4.17、Java 25。DreamSkyblock 当前拒绝 Folia，不能将配置验证等同于整套 Folia 实测。
- 更新 DreamSkyblock 后正常重启生效；不热重载或自动重启。完整菜单窗口、传送和全息按钮仍待游戏内验收。
