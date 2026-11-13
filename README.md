# Mihomo Gateway

把一台 root 后的 Android 手机变成**透明代理网关**：热点客户端与局域网设备共享 VPN 出口。

- 内核：[mihomo](https://github.com/MetaCubeX/mihomo)（Clash.Meta）
- 形态：原生 Kotlin + Jetpack Compose App，root 直连，无 WebView
- 场景：
  1. 手机开热点，热点客户端走代理（含 IPv4/IPv6 与分流）
  2. 手机连 Wi-Fi 作旁路由，内网设备网关指向手机

## 文档

| 文档 | 说明 |
|---|---|
| [docs/DESIGN.md](docs/DESIGN.md) | 总体设计（架构、模块、API、性能约束） |
| [docs/adr/](docs/adr/) | 架构决策记录（ADR） |

## 目录

```
app/                         Android 应用
  src/main/java/com/mihomo/gateway/
    MainActivity.kt          入口（当前单页，逐步拆分为多页）
    GatewayService.kt        前台服务 / 开机自启
    GatewayEvents.kt         网络事件 → 规则重下发
    AssetInstaller.kt        首次运行解压内核与脚本
    Root.kt                  su 调用封装
    Subscriptions.kt         订阅导入
  src/main/assets/
    mihomo                   内核二进制（不提交，见 scripts/fetch-core.ps1）
    scripts/*.sh             网关脚本（与 /data/adb/mihomo 同步）
    config.template.yaml     首次安装的模板配置
docs/                        设计文档
scripts/                     辅助脚本
```

## 构建

```powershell
# 1. 准备内核二进制
powershell -File scripts/fetch-core.ps1

# 2. 构建
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
gradle :app:assembleDebug
```

需要 JDK 17、Android SDK 35、Gradle 8.9。

## 运行时布局（设备上）

```
/data/adb/mihomo/
├── mihomo                 内核二进制
├── config.yaml            当前配置（订阅写入）
├── gateway.conf           网关参数（IFACE / BLOCK_IPV6 / IP_MODE）
├── *.sh                   启停、规则、模式切换脚本
└── mihomo.log             内核日志
```

## 变更纪律

改动设计前先读 [docs/DESIGN.md](docs/DESIGN.md)；任何偏离都需要新增 ADR 并在 PR 中说明。
