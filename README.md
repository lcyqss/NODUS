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
| [docs/DESIGN.md](docs/DESIGN.md) | 总体设计（架构、模块、API、性能约束、里程碑） |
| [docs/adr/](docs/adr/) | 架构决策记录（ADR） |

## 当前进度

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M1 | 数据层 + 概览/设置页 | ✅ v0.5.0 |
| M2 | 代理页 | ✅ v0.6.0 |
| M3 | 连接/日志/规则 | ✅ v0.7.0 |
| M4 | 订阅独立页/诊断页/自启/深色模式 | ✅ v1.0.0 |

## 目录

```
app/src/main/java/com/mihomo/gateway/
├── MainActivity.kt          入口
├── MihomoApp.kt             Application（组合根初始化）
├── di/AppContainer.kt       组合根（唯一知道实现的地方）
├── model/Models.kt          领域模型 + AppError
├── core/
│   ├── ShellExecutor.kt     su 执行封装（带 traceId）
│   ├── AssetInstaller.kt    首次解压内核/脚本
│   └── GatewayEvents.kt     网络事件 → 规则重下发
├── data/
│   ├── api/                 MihomoApi 接口 + 实现（OkHttp + org.json）
│   ├── repo/                GatewayController / ProfileRepository
│   └── store/               SettingsStore（SharedPreferences）
├── service/                 前台服务 / 开机自启
└── ui/
    ├── nav/AppNav.kt        底部导航
    ├── overview/            概览
    └── settings/            设置
app/src/main/assets/
├── mihomo                   内核二进制（不提交，见 scripts/fetch-core.ps1）
├── scripts/*.sh             网关脚本
├── config.template.yaml     模板配置
└── gateway.conf             网关参数
```

## 构建

```powershell
# 1. 准备内核二进制
powershell -File scripts/fetch-core.ps1

# 2. 构建（离线也可，依赖已在缓存）
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
gradle :app:assembleDebug --offline
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
