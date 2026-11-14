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
| [docs/BACKLOG.md](docs/BACKLOG.md) | 待办与暂缓事项 |

## 当前进度

| 里程碑 | 内容 | 状态 |
|---|---|---|
| M1 | 数据层 + 概览/设置页 | ✅ v0.5.0 |
| M2 | 代理页 | ✅ v0.6.0 |
| M3 | 连接/日志/规则 | ✅ v0.7.0 |
| M4 | 订阅独立页/诊断页/自启/深色模式 | ✅ v1.0.0 |
| M5 | 单一开关 / 状态恢复 / 图标体系 | ✅ v1.2.0 |
| M6 | 发布签名 / 清理卸载 / 回归 | ✅ v1.9.1 |
| M7 | 自定义规则管理（注入 + 热重载 + 回滚） | ✅ v2.0.1 |
| M8 | 拓扑图形化（Canvas 分层树） | ✅ v2.1.0 |
| M9 | 横向拓扑 + 设备聚合 + 端口层 + 图标 | ✅ v2.3.1 |
| M10 | P0 体验（模式快捷 / 通知操作 / QS 磁贴 / 隐藏不可用 / 日志搜索导出） | ✅ v2.4.0 |
| M11 | P1（今日流量 / 规则模板 / 拖拽排序 / 订阅名称节点数 / 备份恢复） | ✅ v2.5.3 |
| M12 | P2（收藏置顶 / 连接排序 / 设备过滤 / 多订阅） | ✅ v2.7.0 |

## 近期版本

| 版本 | 内容 |
|---|---|
| v2.10.1 | 节点页支持选中子组（url-test / fallback）作为父组出口，显示子组当前节点 |
| v2.10.0 | 订阅入口移到节点页（切换条 + 弹层）；导入保留地址族、支持停止状态导入 |
| v2.9.8 | 地址族切换改内核 API 热重载（不杀进程）；IPv6 判定改为全局地址 + 多点探测 |
| v2.9.7 | 节点地址族：新增「仅 IPv6 / 仅 IPv4」严格模式，修正优先/双栈文案 |
| v2.9.6 | 视觉统一：28sp 大标题、15sp 正文、卡片圆角 12、分段控件、空态带操作、Tab 淡入 |
| v2.9.5 | 设置页：插入式分组、取值弹窗、统一二级页与返回；日志工具栏不再溢出 |
| v2.9.4 | 分流页：单行汇总、＋ActionSheet、应用按钮按脏状态高亮、规则行重排 |
| v2.9.3 | 节点页：工具栏图标化、排序/筛选菜单、延迟条 |
| v2.9.2 | 首页重做：iOS 分段控件、当前节点可点、流量卡片合并 |
| v2.9.1 | Tab 重排：规则独立为「分流」，日志移入设置 → 诊断 |
| v2.7.0 | 多订阅管理：列表/单选切换/添加/删除 |
| v2.6.0 | 节点收藏置顶、连接表格排序、按设备过滤 |
| v2.5.3 | 规则拖拽排序；修复 SRC-IP-CIDR 校验 |
| v2.5.0 | 今日流量、规则模板、订阅名称/节点数、备份恢复 |
| v2.4.0 | 模式快捷 chips、通知栏操作、QS 磁贴、隐藏不可用、日志搜索/导出 |
| v2.3.1 | 端口节点 + 类型图标，画布裁剪 |
| v2.2.0 | 横向布局 + 客户端按设备聚合 |
| v2.0.1 | 自定义规则管理（独立存储、注入、回滚） |

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
## Release 构建

签名配置在 `keystore/keystore.properties`（已 gitignore，内含 keystore 路径与口令）。

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
gradle :app:assembleRelease --offline
# 产物: app/build/outputs/apk/release/app-release.apk
```

- release 版内核与 GeoSite/GeoIP 均随 APK 打包（首次运行解压到 `/data/adb/mihomo`），无需联网下载
- debug 与 release 签名不同，切换安装需先卸载旧包

## 卸载清理

App 内：**设置 → 管理 → 清理运行数据（卸载前）**

或手动：

```sh
sh /data/adb/mihomo/cleanup.sh   # 停网关 + 清 iptables
rm -rf /data/adb/mihomo
```

清理后重新打开 App 会自动从 APK assets 恢复内核与配置模板。

改动设计前先读 [docs/DESIGN.md](docs/DESIGN.md)；任何偏离都需要新增 ADR 并在 PR 中说明。
