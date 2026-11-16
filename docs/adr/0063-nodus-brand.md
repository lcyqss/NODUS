# ADR 0063：NODUS 品牌与图标

- 状态：已接受
- 日期：2026-11-16

## 名称

App 显示名从 `Mihomo Gateway` 改为 `NODUS`。

保留包名 `com.mihomo.gateway` 和内部类名，避免破坏升级、root 路径和用户数据。

## 图标

- 深色渐变背景
- 白色 N 主体
- 青色轨道环
- 四个端点节点 + 中心节点
- Android 自适应图标：背景 / 前景 / monochrome
- 兼容旧 launcher 的 PNG mipmap
- 通知栏和快捷开关使用白色 N 小图标

## 资源

- `res/mipmap-anydpi-v26/ic_launcher.xml`
- `res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `res/drawable/ic_launcher_background.xml`
- `res/drawable/ic_launcher_foreground.xml`
- `res/drawable/ic_launcher_monochrome.xml`
- `res/drawable/ic_notification.xml`
- `res/mipmap-*/ic_launcher.png`
- `design/nodus-icon.png`