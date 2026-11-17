# NODUS

把一台已 root 的 Android 手机变成透明代理网关：热点客户端和局域网设备共享 mihomo 出口。

> **项目状态：开发 / 灰度测试阶段。**
> 热点模式已在真机验证；**旁路由模式尚未完成完整测试**，不要直接用于生产或无人值守环境。使用前请先阅读[测试状态](#测试状态)和[已知限制](#已知限制)。

- 内核：[mihomo](https://github.com/MetaCubeX/mihomo)
- 形态：原生 Kotlin + Jetpack Compose，root 直连，无 WebView
- 更新：GitHub Release 应用内检查 / 手动下载安装
- 仓库：<https://github.com/lcyqss/NODUS>

## 下载

签名 APK 从 GitHub Releases 获取：

<https://github.com/lcyqss/NODUS/releases/latest>

当前版本：**v2.63.0**（versionCode 184）。

## 测试状态

| 模式 / 功能 | 状态 | 说明 |
|---|---|---|
| 手机热点 + IPv4/iPtables REDIRECT | 已真机验证 | 热点客户端透明代理 |
| 手机热点 + IPv6/TPROXY | 已真机验证 | ULA、v6dns、TPROXY6 |
| 设备策略（MAC / 标签条件） | 已真机验证 | 每设备 listener 与子规则 |
| root 持久 shell | 已真机验证 | 复用单个 `su -c sh` |
| Geo / 内核更新 | 已真机验证 | GitHub 下载与 SHA256 |
| 应用内 APK 更新 | 代码已完成 | 需要 GitHub Release；尚未做完整发布验证 |
| **旁路由模式** | **未完整测试** | Wi-Fi 上游、LAN 网关、IPv6 RA 冲突等场景需继续验证 |

旁路由模式的问题优先记录到 [docs/BACKLOG.md](docs/BACKLOG.md)，不要在未验证的情况下默认可用。

## 功能

- 手机热点共享代理，支持 IPv4 与 IPv6
- 设备策略：按 MAC 选择节点标签，支持条件表达式、延迟调度、IP 跟随
- 分流规则：自定义规则与订阅规则
- mihomo 内核与 Geo 数据更新
- 运行健康检查：DNS、端口、代理、TPROXY、IPv6、规则加载
- 应用内更新：每日检查 GitHub Release，手动下载并安装
- 日志、连接拓扑、诊断信息导出

### 功能边界

| 模块 | 已实现 | 备注 |
|---|---|---|
| 热点网关 | IPv4 REDIRECT / IPv6 TPROXY | 真机验证 |
| 旁路由网关 | 基础脚本入口 | **未完整验证** |
| DNS | mihomo 1053 + v6dns | 热点场景验证 |
| 设备策略 | MAC、标签表达式、延迟、IP 跟随 | 真机验证 |
| 分流规则 | 自定义 / 订阅 | 真机验证 |
| 内核更新 | mihomo release + 回滚 | 真机验证 |
| Geo 更新 | GeoIP / GeoSite + SHA256 | 真机验证 |
| 应用更新 | GitHub Release + APK 校验 | 代码完成，待首次 Release 验证 |
| 诊断 | 日志、健康检查、规则快照 | 真机验证 |

## 依赖

### 运行时依赖

- Android 9+（minSdk 29），arm64
- root：Magisk / KernelSU 等
- mihomo Android arm64 二进制（从 MetaCubeX release 获取，首次安装解压到 `/data/adb/mihomo`）
- GeoIP.dat / GeoSite.dat（随 APK 打包，可从官方源更新）
- 热点或局域网网络环境

### 构建依赖

| 依赖 | 版本 | 用途 |
|---|---|---|
| JDK | 17 | 编译 |
| Android SDK | 35 | 编译 / 目标版本 |
| Gradle | 8.9 | 构建（wrapper 已包含） |
| AndroidX Core KTX | 1.13.1 | Android 基础 |
| Activity Compose | 1.9.0 | Compose 宿主 |
| Lifecycle | 2.8.3 | ViewModel / 生命周期 |
| Compose BOM | 2024.06.00 | UI |
| Material3 | BOM 管理 | UI 组件 |
| Kotlin Coroutines | 1.8.1 | 异步 |
| OkHttp | 4.12.0 | GitHub / 内核 / Geo 网络请求 |
| SnakeYAML | 1.33 | 配置处理 |

### 外部项目

| 项目 | 用途 | 许可证 |
|---|---|---|
| [mihomo](https://github.com/MetaCubeX/mihomo) | 代理内核 | GPL-3.0 |
| [meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat) | GeoIP / GeoSite 数据 | 按其仓库声明 |

## 环境要求

- Android 9+（minSdk 29），arm64 设备
- Magisk / KernelSU 等 root 环境
- JDK 17
- Android SDK 35
- Gradle 8.9（仓库已包含 wrapper）

## 构建

```powershell
# 1. 准备 mihomo 内核二进制
powershell -File scripts/fetch-core.ps1

# 2. Debug
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew.bat :app:assembleDebug

# 3. Release（需要 keystore/keystore.properties）
.\gradlew.bat :app:assembleRelease
```

Release 产物：

```text
app/build/outputs/apk/release/app-release.apk
```

`keystore/`、`local.properties` 和内核二进制不会提交到仓库。

## 运行布局

```text
/data/adb/mihomo/
├── mihomo              内核二进制
├── config.yaml         当前配置
├── gateway.conf        网关参数
├── *.sh                启停、规则、模式切换脚本
└── mihomo.log          内核日志
```

清理运行数据：

```text
App：设置 → 运行组件 → 维护 → 清理运行数据
```

或手动：

```sh
sh /data/adb/mihomo/cleanup.sh
rm -rf /data/adb/mihomo
```

## 应用更新

更新入口：

```text
设置 → 关于 → 检查应用更新
```

行为：

- 应用启动后每天最多自动检查一次
- 有新版本时首页显示提示条
- 用户手动下载并安装
- 安装前校验包名、版本号和签名

发布流程：

```bash
# 1. 修改 versionCode / versionName
# 2. 提交代码
# 3. 打 tag 并推送
git tag v2.63.0
git push origin v2.63.0
```

GitHub Actions 会使用仓库 Secrets 中的签名文件构建 `NODUS.apk`，并在 Release 中附带 `NODUS.apk.sha256`。

需要的 Secrets：

```text
KEYSTORE_BASE64
KEYSTORE_STORE_PASSWORD
KEYSTORE_KEY_ALIAS
KEYSTORE_KEY_PASSWORD
```

详细步骤见 [docs/app-update.md](docs/app-update.md)。

## 已知限制

- **旁路由模式尚未完整测试**，网络拓扑、上游 RA、IPv6 转发可能与热点模式不同。
- 必须使用 root；不同 root 管理器的 `su` 行为和授权提示可能不同。
- 应用更新必须使用与当前安装包相同的签名密钥，否则 Android 会拒绝覆盖安装。
- GitHub API 可能被限流或网络不可达；更新失败时请直接访问 Release 页面。
- 当前更新检查要求仓库和 Release 可匿名访问。
- 真机行为受 ROM、热点实现和运营商网络影响，不能只依据模拟器或单台设备判断。

## 许可证

本项目使用 **GNU General Public License v3.0**（GPL-3.0）。

原因：

- 项目分发/集成 GPL-3.0 的 mihomo 内核
- 选择 GPL-3.0 可以避免应用代码与内核组合分发时的许可证冲突
- 源码公开后，任何分发修改版的人都应继续提供对应源码

完整文本见 [LICENSE](LICENSE)。

## 文档

| 文档 | 说明 |
|---|---|
| [docs/DESIGN.md](docs/DESIGN.md) | 架构、模块、API 与约束 |
| [docs/BACKLOG.md](docs/BACKLOG.md) | 待办、已知问题和未测试项 |
| [docs/TESTING.md](docs/TESTING.md) | 热点 / 旁路由测试状态与验证清单 |
| [docs/app-update.md](docs/app-update.md) | 应用内更新、签名和 GitHub Release 流程 |
| [docs/adr/](docs/adr/) | 架构决策记录 |

## 版本

当前版本：**v2.63.0**（versionCode 184）。

近期变更：

| 版本 | 内容 |
|---|---|
| v2.63.0 | 结构优化：备份 schema 测试、常量集中化、设备策略 / 规则 / 设置页文件拆分；修复恢复备份后规则状态不同步与订阅缓存缺失 |
| v2.61.9 | 应用内更新、GitHub Release、GitHub Actions |
| v2.61.7 | 设备策略样式、策略设置返回修复 |
| v2.61.6 | 设备策略改为纯列表，设置独立入口 |
| v2.61.3 | 设备策略直接嵌入分流页 |
| v2.61.0 | 回滚 IPv6 DNS 实验，修复配置损坏 |
| v2.59.8 | 持久化 root shell |
