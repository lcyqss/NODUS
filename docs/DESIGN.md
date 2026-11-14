# Mihomo Gateway · 设计文档

| 项 | 值 |
|---|---|
| 版本 | v0.2（模块化修订） |
| 状态 | 生效中（M1 实施前） |
| 内核 | mihomo（Clash.Meta） |
| 参考 | [ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)（GPL-3.0） |

> 修订说明：v0.2 在 v0.1 基础上明确**模块边界、依赖方向、接口契约、诊断能力、测试策略与功能调整路径**，用于防止后续耦合与补丁式开发。

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

- 原生 Android App 控制整个网关，**不依赖 WebView / 浏览器**
- 热点与 LAN 两种入口统一处理
- 支持 Clash 订阅、节点切换、延迟测速、连接查看、实时日志
- 支持 IPv4/IPv6 出站优先策略（`ip-version`）
- 开机自启、前台常驻、可干净卸载

### 1.3 非目标（首版）

- 不做 `VpnService` 模式
- 不做桌面端 / iOS
- 不做多用户 / 计费

---

## 2. 设计原则（硬约束）

| # | 原则 | 说明 |
|---|---|---|
| P1 | **内核黑盒** | YAML 由 mihomo 解析；App 不实现 Clash 配置模型 |
| P2 | **控制走原生 API** | 状态/节点/测速/连接/日志一律走 `127.0.0.1:9090` |
| P3 | **root 最小化** | 只有启停、写配置、下发规则使用 `su` |
| P4 | **无适配层** | 不为复用旧代码引入中间层 |
| P5 | **单向依赖** | 依赖只能从 UI 指向数据层，禁止反向与跨层直连 |
| P6 | **单一职责** | 每个模块只做一件事，跨模块只通过接口通信 |

> 违反上述任一条，必须新增 ADR 说明理由。

---

## 3. 与 CMFA 的关系

### 3.1 CMFA 模块结构（参考）

```
ClashMetaForAndroid/
├── app/        UI（Kotlin）
├── core/       内核绑定（Go-mobile）+ 配置生成
├── service/    VpnService（TUN）
├── common/     工具与解析
└── hideapi/    隐藏 API 桩
```

### 3.2 借鉴与不借鉴

| 借鉴 ✅ | 不借鉴 ❌ | 原因 |
|---|---|---|
| 页面信息架构 | `VpnService` 架构 | 我们是 root 网关 |
| 分组树与测速交互 | Go-mobile 内核嵌入 | 独立二进制 + redir，避免 JNI 耦合 |
| 连接列表字段与刷新策略 | `ClashConfig` 配置模型 | 内核解析 YAML |
| 订阅更新流程 | Room 数据库 | DataStore 足够 |
| 错误提示方式 | 多模块拆分 | 单模块 + 包级分层足够 |

### 3.3 参考文件（阅读顺序）

`app/.../MainActivity.kt` → `ProxyActivity.kt` → `ProfilesActivity.kt` → `LogsActivity.kt` → `service/`（只看生命周期）→ `core/`（只看配置生成）。

---

## 4. 总体架构与模块边界

### 4.1 分层

```
┌─────────────────────────────────────────────────────────┐
│ L2  UI 层                                                │
│    screen / viewmodel / components                       │
│    只依赖 L1 的接口，不接触 Android root、不解析 JSON     │
├─────────────────────────────────────────────────────────┤
│ L1  数据层（接口 + 实现）                                 │
│    api:    MihomoApi（REST/WS）                           │
│    repo:   GatewayRepo / ProfileRepo                      │
│    store:  SettingsStore                                  │
│    只依赖 L0 的 ShellExecutor 接口，不依赖 UI             │
├─────────────────────────────────────────────────────────┤
│ L0  基础设施层                                            │
│    ShellExecutor（su）、AssetInstaller、GatewayEvents     │
│    不依赖任何上层，不包含业务判断                         │
└─────────────────────────────────────────────────────────┘
```

### 4.2 依赖规则（评审必查）

| 模块 | 允许依赖 | 禁止依赖 |
|---|---|---|
| `ui/**` | `data/**` 的接口、`model/**` | `core/**`、`service/**`、`okhttp`、`java.io.File` 直接操作 |
| `data/api/**` | `model/**`、`core/ShellExecutor`（仅注入） | `ui/**`、`service/**` |
| `data/repo/**` | `data/api`、`core/ShellExecutor`、`model/**` | `ui/**`、`service/**` |
| `data/store/**` | Android DataStore | `ui/**`、`data/repo` |
| `service/**` | `data/repo` 接口、`core/**` | `ui/**` |
| `core/**` | Android SDK | `data/**`、`ui/**`、`service/**` |
| `di/**` | 全部（仅组装） | 被业务代码反向依赖 |

> 唯一允许“知道所有实现”的地方是 `di/AppContainer`（组合根）。

### 4.3 模块清单

| 模块 | 职责（做什么） | 不做什么 | 对外接口 |
|---|---|---|---|
| `MihomoApi` | 封装 mihomo REST/WS，DTO ↔ 领域模型 | 不启停进程、不写文件 | `MihomoApi` |
| `GatewayRepo` | 启停、模式切换、规则下发、状态推导 | 不解析节点、不碰 UI | `GatewayController` |
| `ProfileRepo` | 订阅下载/校验/落盘/重载 | 不管理节点 UI 状态 | `ProfileRepository` |
| `SettingsStore` | 持久化用户设置 | 不执行动作 | `SettingsStore` |
| `ShellExecutor` | 执行 su 命令，返回 stdout/stderr | 不做业务判断 | `ShellExecutor` |
| `AssetInstaller` | 首次解压内核/脚本 | 不启动服务 | `AssetInstaller` |
| `GatewayEvents` | 网络事件 → 触发规则重下发 | 不决定业务策略 | `GatewayEvents` |
| `GatewayService` | 前台服务、开机自启、生命周期 | 不直接操作 UI | — |
| `ui/*` | 展示与交互 | 不直接 `su`、不直接 HTTP | — |

### 4.4 关键接口（Kotlin 草案）

```kotlin
// L0
interface ShellExecutor {
    suspend fun run(command: String, timeoutMs: Long = 15_000): ShellResult
}
data class ShellResult(val code: Int, val stdout: String, val stderr: String)

// L1 - 内核 API
interface MihomoApi {
    suspend fun version(): Result<VersionInfo>
    suspend fun configs(): Result<ConfigInfo>
    suspend fun proxies(): Result<Map<String, ProxyNode>>
    suspend fun select(group: String, name: String): Result<Unit>
    suspend fun delay(name: String, timeoutMs: Int, url: String): Result<Int>
    suspend fun connections(): Result<List<ConnectionInfo>>
    suspend fun closeConnection(id: String): Result<Unit>
    suspend fun rules(): Result<List<RuleInfo>>
    fun trafficFlow(): Flow<Traffic>
    fun logFlow(level: String): Flow<LogEntry>
}

// L1 - 网关控制
interface GatewayController {
    suspend fun start(): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun setIpMode(mode: IpMode): Result<Unit>
    suspend fun applyRules(): Result<Unit>
    fun observe(): StateFlow<GatewayState>
}

// L1 - 订阅
interface ProfileRepository {
    suspend fun import(url: String): Result<Unit>
    suspend fun reload(): Result<Unit>
}

// L1 - 设置（实现：SharedPreferences + MutableStateFlow，见 ADR 0006）
interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun update(block: (AppSettings) -> AppSettings)
}
```

### 4.5 组合根

`di/AppContainer` 负责创建实现并注入，不引入 DI 框架：

```kotlin
object AppContainer {
    fun init(context: Context) {
        shell = ShellExecutorImpl()
        api = MihomoApiImpl(baseUrl = "http://127.0.0.1:9090", secret = ...)
        gateway = GatewayControllerImpl(shell, api)
        profiles = ProfileRepositoryImpl(context, api, shell)
        settings = SettingsStoreImpl(context)
    }
}
```

---

## 5. 模块设计（细化）

### 5.1 MihomoApi

- 单例 `OkHttpClient`（连接池、keep-alive、3s/10s 超时）
- 认证：`Authorization: Bearer <secret>`
- 解析与领域模型分离：`MihomoApiImpl` 内手写 `org.json` 解析，`model/` 放 UI 用的不可变数据
- 错误映射：HTTP 401 → `Unauthorized`；连接失败 → `CoreDown`；超时 → `Timeout`
- WS：`/logs`、`/traffic`，指数退避重连，暴露 `StateFlow<WsState>`

### 5.2 GatewayRepo

- 所有 su 调用串行（Mutex），超时 15s
- 命令白名单：只允许 `start.sh` / `stop.sh` / `apply.sh` / `set-ipver.sh` / `ensure_config.sh`
- 状态来源：`/version`（是否运行）+ `gateway.conf`（模式）推导，不解析 shell 文本
- 每次调用生成 `traceId`，记录到诊断日志

### 5.3 ProfileRepo

- 下载（UA `clash.meta`，30s 超时）→ 粗校验 → 临时文件 → 原子替换 → 重载
- 失败保留旧配置
- 订阅 URL 只存 DataStore，不写日志

### 5.4 GatewayService

- 前台服务（`specialUse`），`START_STICKY`
- 启动顺序：`AssetInstaller` → `GatewayController.start()` → `GatewayEvents.start()`
- 开机广播触发同一路径

### 5.5 UI

| 页面 | 依赖 | 说明 |
|---|---|---|
| 概览 | `GatewayController`、`MihomoApi`（traffic） | 状态卡 + 流量图 |
| 代理 | `MihomoApi` | 分组树、测速、切换 |
| 连接 | `MihomoApi` | 列表 + 断开 |
| 日志 | `MihomoApi`（logFlow） | 环形缓冲 500 行 |
| 设置 | `SettingsStore`、`ProfileRepository`、`GatewayController` | 订阅、模式、开关 |
| 诊断（隐藏） | 全部只读 | 排障专用 |

---

## 6. 关键数据流

### 6.1 启动

```
点「启动」→ VM → GatewayController.start()
  → ShellExecutor.run("sh start.sh")
  → 轮询 /version（≤3s）
  → Running → 订阅 trafficFlow
```

### 6.2 切换节点

```
点节点 → api.select(group, name) → 局部刷新该分组
（不重启内核、不重下发规则）
```

### 6.3 测速

```
并发 api.delay(name)（≤5）→ 协程绑定页面 → 离开即取消
```

### 6.4 订阅导入

```
ProfileRepo.import(url)
  → 下载 → 校验 → 原子替换 config.yaml
  → shell: ensure_config.sh + set-ipver.sh
  → 重载（PUT /configs 或重启进程）
```

### 6.5 网络变化

```
GatewayEvents → 防抖 2s → GatewayController.applyRules()
```

---

## 7. mihomo API 契约

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/version` | 就绪探测 |
| GET | `/configs` | 当前配置 |
| PUT | `/configs?force=true` | 重载 |
| PATCH | `/configs` | 修改单项 |
| GET | `/proxies` | 代理与分组 |
| PUT | `/proxies/{name}` | 选择节点 |
| GET | `/proxies/{name}/delay` | 测速 |
| GET | `/providers/proxies` | provider |
| GET | `/providers/proxies/{name}/healthcheck` | provider 健康检查 |
| GET | `/connections` | 连接列表 |
| DELETE | `/connections/{id}` | 断开单条 |
| DELETE | `/connections` | 全部断开 |
| GET | `/rules` | 规则 |
| WS | `/logs?level=info` | 日志 |
| WS | `/traffic` | 速率 |

约定：全部带 `Authorization`；仅 `127.0.0.1`；JSON `ignoreUnknownKeys`。

---

## 8. 数据模型

```kotlin
data class ProxyNode(val name: String, val type: String, val udp: Boolean,
                     val delay: Int?, val alive: Boolean?)
data class ProxyGroup(val name: String, val type: String, val now: String?, val all: List<String>)
data class ConnectionInfo(val id: String, val host: String, val rule: String,
                          val chain: List<String>, val upload: Long, val download: Long, val start: Long)
data class RuleInfo(val type: String, val payload: String, val proxy: String)
data class LogEntry(val level: String, val payload: String, val time: Long)
data class Traffic(val up: Long, val down: Long)
data class GatewayState(val running: Boolean, val mode: IpMode,
                        val interfaces: List<NetIface>, val blockIpv6: Boolean)
enum class IpMode { AUTO, V6, V4, DUAL }
```

---

## 9. 并发模型

- UI 状态：`StateFlow` + `collectAsStateWithLifecycle`
- IO：`Dispatchers.IO` + `supervisorScope`
- 轮询：`repeatOnLifecycle(STARTED)` 绑定页面
- root：`Mutex` 串行
- WS：独立协程 + 重连循环，页面离开即取消

---

## 10. 性能约束（评审必查）

| 规则 | 说明 |
|---|---|
| 单例 OkHttpClient | 禁止每次请求新建 |
| 页面不可见不轮询 | `repeatOnLifecycle` |
| 大 JSON 不在 UI 线程解析 | `withContext(IO)` |
| 列表限长 | 日志 500、连接 500 |
| 测速并发 ≤5 | 可取消 |
| 热路径禁止 root | 查询走 API |
| 稳定 key + 不可变数据 | 避免整表重组 |

---

## 11. 错误处理

| 场景 | 表现 | 处理 |
|---|---|---|
| 内核未运行 | 概览「未启动」 | 引导启动 |
| API 401 | 「密钥不匹配」 | 重新生成并重载 |
| su 被拒 | 「未获得 root」 | 引导 Magisk |
| 订阅失败 | 保留旧配置 | 提示原因 |
| WS 断线 | 「重连中」 | 退避重连 |
| 规则下发失败 | 状态提示 | 重试一次并记录 |

统一错误模型：

```kotlin
sealed class AppError : Throwable() {
    object CoreDown : AppError()
    object Unauthorized : AppError()
    object RootDenied : AppError()
    data class Network(val cause: Throwable) : AppError()
    data class Protocol(val message: String) : AppError()
}
```

---

## 12. 诊断与可观测性（新增）

### 12.1 日志规范

| 层 | Tag | 级别 | 内容 |
|---|---|---|---|
| Shell | `GW-Shell` | I/W | traceId、命令、耗时、退出码（不含订阅 URL） |
| API | `GW-Api` | I/W | 方法、路径、状态码、耗时 |
| WS | `GW-Ws` | I/W | 连接/断开/重连次数 |
| Repo | `GW-Repo` | I | 状态迁移（Stopped→Starting→Running） |

### 12.2 诊断页（隐藏入口：设置页连点版本号 5 次）

只读展示：
- 内核版本、PID、API 延迟（`/version` 往返）
- WS 状态（logs/traffic 是否 connected）
- 最近 20 条 root 命令（traceId、命令、耗时、退出码）
- 当前规则快照（`iptables -S MIHOMO_*`）
- 当前接口与网段
- 一键导出诊断包（脱敏后的日志 + 配置 + 设备信息）

### 12.3 排查路径（文档化）

| 现象 | 先看 | 再看 |
|---|---|---|
| 客户端没网 | 概览：mihomo 是否 Running | 诊断页：规则是否存在、接口是否正确 |
| 能连不能翻 | 代理页：当前节点/延迟 | 日志页：`[TCP]` 是否命中预期策略 |
| 切换节点无效 | `/proxies` 返回 | 是否该分组为 `url-test`（不可手动选） |
| 订阅导入失败 | 下载状态码 | 内容是否含 `proxies:` |

---

## 13. 测试策略（新增）

| 层 | 方式 | 覆盖 |
|---|---|---|
| DTO 解析 | 单元测试（固定 JSON 样例） | 字段缺失/新增字段兼容 |
| 错误映射 | 单元测试 | 401/超时/连接拒绝 |
| GatewayController 状态机 | 单元测试（Fake ShellExecutor） | 启动/停止/失败回滚 |
| ProfileRepo | 单元测试（MockWebServer） | 正常/超大/非法内容 |
| ViewModel | 单元测试（Fake Repo） | 状态流转、去抖 |
| 端到端 | 真机手工矩阵 | 热点开/关、Wi-Fi 切换、模式切换、重启 |

---

## 14. 功能调整指南（新增）

新增一个能力时，按下表决定落点，避免到处打补丁：

| 变更类型 | 落点 | 示例 |
|---|---|---|
| 新增用户可调项 | `SettingsStore` + `SettingsScreen` + 脚本参数 | IPv6 拦截开关 |
| 新增内核能力 | `MihomoApi` 接口 + DTO + 页面 | 规则命中统计 |
| 新增系统动作 | `GatewayController` + 脚本 | 重启内核 |
| 新增只读展示 | 页面 + 现有接口 | 版本信息 |
| 修改分流行为 | 只改 `config.yaml` 模板或脚本 | 规则集 |

规则：**UI 不直接拼命令/URL；数据层不碰界面状态；脚本不写业务判断。**

---

## 15. 安全

- mihomo 仅监听 `127.0.0.1`
- `secret` 随机生成，写入 config
- root 白名单脚本
- 不记录订阅 URL、节点密码
- 卸载：停服务 → 清规则 → 删 `/data/adb/mihomo`

---

## 16. 兼容性

| 项 | 范围 |
|---|---|
| Android | 10 – 16（minSdk 29，target 35） |
| 架构 | arm64-v8a |
| 接口名 | `wlan*` / `ap*` / `softap*` / `swlan*`，支持 `gateway.conf` 覆盖 |
| mihomo | ≥ 1.18 |
| Root | Magisk 优先，KernelSU 路径作为候选 |

---

## 17. 目录结构（目标）

```
app/src/main/java/com/mihomo/gateway/
├── MainActivity.kt
├── di/AppContainer.kt
├── model/                    # 领域模型（无 Android 依赖）
├── core/                     # L0：ShellExecutor / AssetInstaller / GatewayEvents
├── data/
│   ├── api/                  # MihomoApi 接口 + 实现 + dto
│   ├── repo/                 # GatewayController / ProfileRepository 接口 + 实现
│   └── store/                # SettingsStore
├── service/                  # GatewayService / BootReceiver
└── ui/
    ├── nav/AppNav.kt
    ├── overview/  proxies/  connections/  logs/  settings/  diagnostics/
    └── components/
```

---

## 18. 里程碑

| 里程碑 | 内容 | 验收 | 状态 |
|---|---|---|---|
| **M1** | ShellExecutor + MihomoApi + GatewayController + 概览/设置页 | 启停可用，状态来自 API | ✅ 已完成（v0.5.0） |
| **M2** | 代理页（分组/节点/测速/切换） | 切换即时，测速流畅 | ✅ 已完成（v0.6.0） |
| **M3** | 连接 + 日志 + 规则页 | 实时刷新，可断开 | ✅ 已完成（v0.7.0） |
| **M4** | 订阅页、诊断页、开机自启、异常与多机型 | 冷启动 ≤5s，重启自恢复 | ✅ 已完成（v1.0.0） |

---

## 19. 变更控制

1. 偏离本文档先写 ADR（`docs/adr/NNNN-*.md`）
2. PR 检查清单：
   - [ ] 是否新增跨层依赖？（对照 4.2）
   - [ ] 是否在 UI 层直接 root/HTTP？
   - [ ] 是否热路径轮询或新建 OkHttpClient？
   - [ ] 是否更新文档/ADR？
3. `versionName` 语义化，`versionCode` 单调递增
4. 每个里程碑一个提交，信息格式：`feat(Mx): ...`

---

## 20. 附录：术语

| 术语 | 含义 |
|---|---|
| 网关模式 | root + iptables REDIRECT 的透明代理 |
| 旁路由 | 手机作为 LAN 网关 |
| redir-port | mihomo 透明代理入口（TCP） |
| fake-ip | DNS 返回假地址，由内核映射域名 |
| provider | 订阅提供的节点集合 |
| 组合根 | 唯一负责组装依赖的位置（AppContainer） |
