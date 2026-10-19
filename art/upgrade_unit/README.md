# 初级升级单元

- 生图：按用户指定接口，通过 imagegen 技能的 CLI 调用 gpt-image-2；凭据仅用于调用进程环境，不写入文件。
- 提示词：`prompt.txt`，一枚铜铁边框、青绿色向上箭头的 Minecraft 像素风升级单元，纯品红键色背景，无文字。
- 原始图：`generated.png`；上线贴图：`../../craftengine/genlite/resourcepack/assets/genlite/textures/item/basic_upgrade_unit.png`。
- 导出：移除品红背景和边缘杂色，保留四像素透明边距，最近邻缩至 32×32，24 色 RGBA；预览为 `preview.png`。
