# Mihomo Gateway · 设计文档

| 项 | 值 |
|---|---|
| 版本 | v0.1（设计基线） |
| 状态 | 生效中（P1 实施前） |
| 内核 | mihomo（Clash.Meta） |
| 参考 | [ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)（GPL-3.0） |

---

## 1. 背景

### 1.1 使用场景

**场景 1：热点网关**

```
客户端 ──Wi-Fi──> 手机热点(wlan2) ──iptables REDIRECT──> mihomo ──> 机场节点 ──> Internet
```

**场景 2：旁路由**

```
终端 ──> 主路由 ──LAN──> 手机(wlan0) ──iptables REDIRECT──> mihomo ──> Internet
（终端网关/DNS 指向手机 IP）
```

### 1.2 目标

- 一个原生 Android App 控制整个网关，**不依赖 WebView / 浏览器**
- 热点与 LAN 两种入口统一处理
- 支持 Clash 订阅、节点切换、延迟测速、连接查看、实时日志
- 支持 IPv4/IPv6 出站优先策略（`ip-version`）
- 开机自启、前台常驻、可干净卸载

### 1.3 非目标（首版）

- 不做 `VpnService` 模式（后续可作为可选功能）
- 不做桌面端 / iOS
- 不做多用户 / 计费 / 商业化

---

## 2. 设计原则（硬约束）

| # | 原则 | 说明 |
|---|---|---|
| P1 | **内核黑盒** | YAML 由 mihomo 解析；App 不实现 Clash 配置模型，不二次解析 |
| P2 | **控制走原生 API** | 运行状态、节点、测速、连接、日志一律走 `127.0.0.1:9090` 的 REST/WS |
| P3 | **root 最小化** | 只有启停、写配置、下发 iptables 走 `su`；查询类操作不碰 shell |
| P4 | **无适配层** | 不为复用旧代码引入中间层；新代码直接面向目标架构 |
| P5 | **单进程** | App 单进程；内核是独立子进程，通过 API 通信 |

> 违反上述任一条，必须新增 ADR 说明理由。

---

## 3. 与 CMFA 的关系

### 3.1 CMFA 模块结构（参考）

```
ClashMetaForAndroid/
├── app/        UI（Kotlin，活动页 + Compose/XML）
├── core/       内核绑定（Go-mobile / libclash）+ 配置生成
├── service/    VpnService（TUN）
├── common/     工具与解析
└── hideapi/    隐藏 API 桩
```

### 3.2 借鉴与不借鉴

| 借鉴 ✅ | 不借鉴 ❌ | 原因 |
|---|---|---|
| 页面信息架构（首页/代理/连接/日志/设置） | `VpnService` 架构 | 我们是 root 网关，不需要 TUN |
| 节点分组树与测速交互 | Go-mobile 内核嵌入 | 用独立二进制 + redir，避免 JNI 生命周期耦合 |
| 连接列表的字段与刷新策略 | `ClashConfig` 配置模型 | YAML 交给内核，App 只存文件 |
| 订阅更新的用户流程 | Room 数据库 | DataStore + 文件路径足够 |
| 错误提示与引导方式 | 多模块拆分 | 单模块足够，降低构建复杂度 |

### 3.3 参考文件（阅读顺序）

| 文件 | 用途 |
|---|---|
| `app/.../MainActivity.kt` | 首页状态与快捷操作 |
| `app/.../ProxyActivity.kt` | 分组树、节点切换、测速 |
| `app/.../ProfilesActivity.kt` | 订阅导入与更新 |
| `app/.../LogsActivity.kt` | 日志流式展示 |
| `service/...` | 观察其服务生命周期（仅借鉴，不采用） |
| `core/...` | 观察配置生成方式（仅借鉴，不采用） |

---

## 4. 总体架构

```
┌──────────────────────────── Compose UI ────────────────────────────┐
│  概览  │  代理  │  连接  │  日志  │  设置                              │
└───────┬──────────────┬───────────────┬──────────────┬──────────────┘
        │ StateFlow    │               │              │
        ▼              ▼               ▼              ▼
┌───────────────┐ ┌────────────┐ ┌─────────────┐ ┌──────────────────┐
│ GatewayVM     │ │ ProxiesVM  │ │ ConnsVM     │ │ SettingsVM       │
└───────┬───────┘ └─────┬──────┘ └──────┬──────┘ └────────┬─────────┘
        └───────────────┴───────────────┴─────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │      Repository      │
                    │  MihomoApi           │ REST + WS (OkHttp)
                    │  GatewayRepo         │ su + 脚本
                    │  ProfileRepo         │ 订阅下载/写入
                    └──────────┬───────────┘
                               │
                    ┌──────────▼───────────┐
                    │ mihomo 子进程         │
                    │ API :9090 (loopback)  │
                    │ redir :7892           │
                    │ DNS :1053             │
                    └──────────────────────┘
```

不存在的组件（刻意移除）：WebView、静态面板、9091 桥接、CORS。

---

## 5. 模块设计

### 5.1 MihomoApi

职责：封装 mihomo 的 REST 与 WebSocket。

- 单例 `OkHttpClient`：
  - `connectTimeout = 3s`、`readTimeout = 10s`
  - 连接池默认（5 idle），keep-alive
  - 不启用重试风暴：仅对幂等 GET 做 1 次重试
- 认证：`Authorization: Bearer <secret>`（secret 由 App 生成并写入 config）
- 接口封装为 suspend 函数，返回 `Result<T>` 或抛领域异常
- WebSocket：
  - `/logs?level=info`：日志流
  - `/traffic`：上下行速率（1s 推送）
  - 断线指数退避重连（1s → 2s → 4s，上限 30s）
- 探测：启动时 `GET /version` 判断内核是否就绪与版本

### 5.2 GatewayRepo

职责：网关生命周期（唯一使用 root 的模块）。

| 方法 | 行为 |
|---|---|
| `start()` | `su -c "sh /data/adb/mihomo/start.sh"`，随后轮询 `/version` 就绪 |
| `stop()` | `su -c "sh /data/adb/mihomo/stop.sh"` |
| `setMode(mode)` | `su -c "sh set-ipver.sh auto|v6|v4|dual"` |
| `applyRules()` | `su -c "sh apply.sh"`（由网络事件触发） |
| `status()` | **不调脚本**：由 API `/version` + `/configs` 推导 |

约束：
- 所有调用串行化（Mutex），避免并发启停
- 单次执行超时 15s
- 返回 stdout/stderr 供日志页展示（截断 8KB）

### 5.3 ProfileRepo

职责：订阅与配置。

- `import(url)`：
  1. OkHttp 下载（UA `clash.meta`，超时 30s）
  2. 校验包含 `proxies:` 或 `proxy-providers:`
  3. 写入 `cacheDir/config.yaml`
  4. `su -c "cp ... /data/adb/mihomo/config.yaml && sh ensure_config.sh && sh set-ipver.sh auto"`
  5. 重载内核（`PUT /configs` 或重启进程）
- 失败时保留旧配置，绝不半写入
- 订阅 URL 存入 DataStore（可选脱敏展示）

### 5.4 GatewayService

- 前台服务（`specialUse`），常驻通知
- 启动时：`AssetInstaller.ensure()` → `GatewayRepo.start()` → 注册网络事件
- 开机自启：`BOOT_COMPLETED` → `startForegroundService`
- 网络事件：`ConnectivityManager.NetworkCallback` → 防抖 2s → `applyRules()`

### 5.5 UI 层

| 页面 | 职责 | 关键组件 |
|---|---|---|
| 概览 | 运行状态、当前节点、上下行、接口信息、快捷启停 | 状态卡、流量折线 |
| 代理 | 分组树、节点列表、测速、切换 | LazyColumn、分组折叠 |
| 连接 | 活动连接、断开 | 列表 + 过滤 |
| 日志 | 实时日志、级别过滤 | 环形缓冲 + LazyColumn |
| 设置 | 订阅、地址族、IPv6 拦截、核心更新、开机自启 | 表单 |

导航：`NavigationBar` + `NavHost`（5 个 destination）。

---

## 6. 关键数据流

### 6.1 启动

```
用户点「启动」
 → GatewayVM.start()
 → GatewayRepo.start()            (su, 串行)
 → 轮询 GET /version (≤3s)
 → 成功: state = Running; 概览订阅 /traffic WS
 → 失败: state = Error("内核未就绪")
```

### 6.2 切换节点

```
代理页点击节点
 → MihomoApi.put("/proxies/{group}", {"name": node})
 → 局部刷新该分组（GET /proxies/{group}）
 → 不重启内核、不重下发规则
```

### 6.3 测速

```
点击「测速」
 → 并发 GET /proxies/{name}/delay?timeout=5000&url=...
 → 并发上限 5，协程作用域绑定页面
 → 离开页面即取消
```

### 6.4 订阅导入

见 5.3；重载后刷新 `/proxies` 与 `/configs`。

### 6.5 网络变化

```
NetworkCallback.onAvailable/onLost
 → 防抖 2s
 → GatewayRepo.applyRules()
 → 刷新概览接口信息
```

---

## 7. mihomo API 契约

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/version` | 就绪探测、版本 |
| GET | `/configs` | 当前配置（端口、模式、DNS） |
| PUT | `/configs?force=true` | 重载配置 |
| PATCH | `/configs` | 修改单项（如 mode） |
| GET | `/proxies` | 全部代理与分组 |
| PUT | `/proxies/{name}` | 选择节点（body: `{"name": "..."}`） |
| GET | `/proxies/{name}/delay` | 延迟测速（`url`、`timeout`） |
| GET | `/providers/proxies` | provider 列表 |
| GET | `/providers/proxies/{name}/healthcheck` | provider 健康检查 |
| GET | `/connections` | 活动连接（含上下行总量） |
| DELETE | `/connections/{id}` | 断开单条 |
| DELETE | `/connections` | 全部断开 |
| GET | `/rules` | 规则列表 |
| GET (WS) | `/logs?level=info` | 日志流 |
| GET (WS) | `/traffic` | 速率流 |

约定：
- 全部需要 `Authorization: Bearer <secret>`
- 仅监听 `127.0.0.1`
- JSON 解析用 `kotlinx.serialization`，字段容错（`ignoreUnknownKeys = true`）

---

## 8. 数据模型（草案）

```kotlin
data class ProxyNode(
    val name: String,
    val type: String,
    val udp: Boolean,
    val delay: Int?,          // ms，null 未测
    val alive: Boolean?
)

data class ProxyGroup(
    val name: String,
    val type: String,          // select / url-test / fallback / load-balance
    val now: String?,          // 当前选中
    val all: List<String>,     // 成员名
)

data class ConnectionInfo(
    val id: String,
    val host: String,
    val rule: String,
    val chain: List<String>,
    val upload: Long,
    val download: Long,
    val start: Long
)

data class LogEntry(val level: String, val payload: String, val time: Long)

data class Traffic(val up: Long, val down: Long)

data class GatewayState(
    val running: Boolean,
    val mode: IpMode,          // AUTO/V6/V4/DUAL
    val interfaces: List<NetIface>,
    val blockIpv6: Boolean
)

enum class IpMode { AUTO, V6, V4, DUAL }
```

---

## 9. 并发模型

- UI 状态：`StateFlow` + `collectAsStateWithLifecycle`
- 网络/IO：`Dispatchers.IO`，`supervisorScope` 隔离单点失败
- 轮询：绑定页面生命周期（`repeatOnLifecycle(STARTED)`）
- 根操作：`Mutex` 串行，避免并发启停
- WebSocket：独立协程 + 重连循环；页面离开时关闭

---

## 10. 性能约束（代码评审必查）

| 规则 | 说明 |
|---|---|
| 禁止每次请求新建 OkHttpClient | 全局单例 |
| 禁止页面不可见时轮询 | `repeatOnLifecycle` |
| 禁止 UI 线程解析大 JSON | `withContext(IO)` |
| 禁止无上限列表 | 日志 500 行、连接 500 条 |
| 禁止测速全量并发 | 上限 5，可取消 |
| 禁止在热路径调用 root | 查询类一律走 API |
| 禁止整表重组 | 稳定 key + 不可变数据 |

---

## 11. 错误处理

| 场景 | 表现 | 处理 |
|---|---|---|
| 内核未运行 | 概览显示「未启动」 | 引导点启动 |
| API 401 | 「密钥不匹配」 | 重新生成 secret 并重载 |
| su 被拒 | 「未获得 root」 | 引导 Magisk 授权 |
| 订阅下载失败 | 保留旧配置 | 提示原因，不覆盖 |
| WS 断线 | 日志页显示「重连中」 | 退避重连 |
| 规则下发失败 | 状态提示 | 重试一次，仍失败则记录 |

---

## 12. 安全

- mihomo 只监听 `127.0.0.1`
- `secret` 随机生成（16 字节 hex），写入 config，App 内保存
- root 动作白名单：仅允许调用 `/data/adb/mihomo/*.sh` 中的固定脚本
- 不把订阅 URL、节点密码写入日志
- 卸载时：停服务 → 清规则 → 删除 `/data/adb/mihomo`

---

## 13. 兼容性

| 项 | 范围 |
|---|---|
| Android | 10 – 16（minSdk 29，target 35） |
| 架构 | arm64-v8a |
| 接口名 | `wlan*` / `ap*` / `softap*` / `swlan*`，支持 `gateway.conf` 覆盖 |
| mihomo | ≥ 1.18（`ip-version` 支持） |
| Root | Magisk（`/debug_ramdisk/su`）、KernelSU 路径作为候选 |

---

## 14. 目标目录结构

```
app/src/main/java/com/mihomo/gateway/
├── MainActivity.kt
├── ui/
│   ├── nav/AppNav.kt
│   ├── overview/OverviewScreen.kt
│   ├── proxies/ProxiesScreen.kt
│   ├── connections/ConnectionsScreen.kt
│   ├── logs/LogsScreen.kt
│   └── settings/SettingsScreen.kt
├── data/
│   ├── api/MihomoApi.kt
│   ├── api/Models.kt
│   ├── repo/GatewayRepo.kt
│   ├── repo/ProfileRepo.kt
│   └── store/SettingsStore.kt
├── service/GatewayService.kt
├── service/BootReceiver.kt
├── core/Root.kt
├── core/AssetInstaller.kt
└── core/GatewayEvents.kt
```

---

## 15. 里程碑

| 里程碑 | 内容 | 验收 |
|---|---|---|
| **M1** | MihomoApi + GatewayRepo + 概览/设置页 | 启停可用，状态来自 API |
| **M2** | 代理页（分组/节点/测速/切换） | 切换即时生效，测速流畅 |
| **M3** | 连接页 + 日志页 + 规则页 | 实时刷新，可断开连接 |
| **M4** | 订阅页、开机自启、异常与多机型 | 冷启动 ≤5s，重启自恢复 |

---

## 16. 变更控制

1. 任何偏离本文档的设计，先写 ADR（`docs/adr/NNNN-*.md`）再实现
2. PR 检查清单：
   - [ ] 是否引入了适配层/中间层？理由？
   - [ ] 是否在热路径使用 root 或轮询？
   - [ ] 是否新增了 WebView/HTTP 桥接？
   - [ ] 是否更新了文档/ADR？
3. 版本：`versionName` 语义化，`versionCode` 单调递增

---

## 17. 附录：术语

| 术语 | 含义 |
|---|---|
| 网关模式 | root + iptables REDIRECT 的透明代理模式 |
| 旁路由 | 手机作为 LAN 网关，非主路由 |
| redir-port | mihomo 的透明代理入口（TCP） |
| fake-ip | DNS 返回假地址，由内核映射回域名 |
| provider | 订阅提供的节点集合 |
