# MetaCubeXD 功能对齐分析

- 日期：2026-11-14
- 原则：**只搬运内核（mihomo API）能直接提供的数据**，App 不自行推算、不伪造
- 当前内核：mihomo v1.19.34（android-arm64）

---

## 1. 已实测可用的内核接口

在设备上实际调用（带 `Authorization`）结果：

| 接口 | 状态 | 说明 |
|---|---|---|
| `GET /version` | 200 | 内核版本 |
| `GET /configs` | 200 | 端口、mode、log-level、allow-lan、dns 等 |
| `PATCH /configs` | **204** | 改运行模式（rule/global/direct）等，需正确 JSON body |
| `GET /proxies` | 200 | 节点与分组 |
| `PUT /proxies/{name}` | 已用于切换 | 选择节点 |
| `GET /proxies/{name}/delay` | 200 | 单节点测速 |
| **`GET /group/{name}/delay`** | **200** | **整组测速，一次返回全部节点延迟** |
| `GET /providers/proxies` | 200 | provider 列表（含 `vehicleType`、`updatedAt`、`testUrl`） |
| `GET /providers/proxies/{name}` | 200 | provider 详情 |
| `GET /providers/proxies/{name}/healthcheck` | **204** | 触发 provider 健康检查 |
| `GET /providers/rules` | 200 | 规则集 provider（当前订阅为空 `{}`） |
| `GET /rules` | 200 | 516 条规则 |
| `GET /connections` | 200 | 连接（含 `sourceIP`/`destinationIP`/`process`） |
| `DELETE /connections/{id}` | 已用 | 断开连接 |
| `GET /dns/query?name=&type=` | 200 | 真实 DNS 解析结果（返回 Answer 记录） |
| `POST /cache/fakeip/flush` | **204** | 清 fake-ip 缓存 |
| `POST /cache/dns/flush` | **204** | 清 DNS 缓存 |
| `WS /logs` | 已验证 | 实时日志 |
| `WS /traffic` | 已验证 | 上下行速率 |
| `WS /memory` | 已验证 | 内存占用 |

未实测（避免副作用）：`POST /restart`（重启内核）、`PUT /providers/proxies/{name}`（更新 provider）。

---

## 2. 尚未搬运的 MetaCubeXD 功能

| # | 功能 | 内核数据 | 我们现状 |
|---|---|---|---|
| 1 | 运行模式切换（Rule / Global / Direct） | `PATCH /configs` | ❌ 无 |
| 2 | 整组测速 | `GET /group/{name}/delay` | ⚠️ 逐个节点测（5 并发） |
| 3 | 内核重启 | `POST /restart` | ❌ 无（只能杀进程重开） |
| 4 | DNS / fake-ip 缓存清理 | `POST /cache/*/flush` | ❌ 无 |
| 5 | Provider 面板（类型/节点数/更新时间/健康检查） | `/providers/proxies*` | ❌ 无 |
| 6 | 规则集 provider 状态 | `/providers/rules` | ❌ 无 |
| 7 | DNS 查询工具 | `/dns/query` | ❌ 无 |
| 8 | 连接按客户端/进程过滤 | `connections.metadata.sourceIP/process` | ⚠️ 仅展示 |
| 9 | 日志关键字过滤 | WS 日志（客户端过滤） | ⚠️ 仅级别过滤 |
| 10 | Config 编辑器 | `GET/PATCH /configs` | ❌ 无（不建议） |
| 11 | 独立 Traffic 页 | `/traffic` | ⚠️ 概览已覆盖 |
| 12 | 内核升级 | `POST /upgrade` | ❌ 无（不建议） |
| 13 | Profiles（多订阅管理） | 无（面板自身存储） | ⚠️ 单订阅 |

---

## 3. 推荐搬运（按价值排序）

### P0（强烈建议，全部内核直出）

| 功能 | 接口 | 价值 | 工作量 |
|---|---|---|---|
| **运行模式切换** | `PATCH /configs {"mode":"rule\|global\|direct"}` | 一键全局代理/全局直连，排障和临时绕过极有用 | 0.2 天 |
| **整组测速** | `GET /group/{name}/delay` | 47 个节点从 47 次请求降到 **1 次**，速度与流量都大幅下降 | 0.3 天 |
| **内核重启** | `POST /restart` | 改坏配置/节点卡死时的救命按钮 | 0.1 天 |
| **缓存清理** | `POST /cache/fakeip/flush`、`/cache/dns/flush` | 切节点/切模式后清 DNS 污染与假 IP 残留 | 0.2 天 |

### P1（有明显价值）

| 功能 | 接口 | 价值 | 工作量 |
|---|---|---|---|
| **Provider 面板** | `/providers/proxies`、`/{name}`、`/{name}/healthcheck` | 看节点来源、节点数、更新时间；手动健康检查 | 0.5 天 |
| **DNS 查询** | `/dns/query?name=&type=` | 验证 fake-ip、分流、污染，排障利器 | 0.3 天 |
| **连接按客户端过滤** | `connections.metadata.sourceIP` / `process` | 网关场景下看“哪个客户端在跑什么” | 0.3 天 |

### P2（锦上添花）

| 功能 | 接口 | 价值 | 工作量 |
|---|---|---|---|
| 规则集 provider 状态 | `/providers/rules` | 当前订阅为空；换用 rule-provider 订阅后才有用 | 0.2 天 |
| 日志关键字过滤 | 客户端过滤 | 排障辅助 | 0.1 天 |
| Provider 手动更新 | `PUT /providers/proxies/{name}` | 订阅节点更新不及时时手动刷新（需确认接口语义） | 0.2 天 |

---

## 4. 不建议搬运

| 功能 | 原因 |
|---|---|
| Config 编辑器 | 直接改 YAML 风险高，容易把网关配置改坏；诊断页已提供只读快照 |
| 内核升级 `/upgrade` | 需要下载新内核、替换运行中二进制，风险与复杂度高，收益低（我们随 APK 发版即可） |
| 独立 Traffic 页 | 概览页已含双线图与累计统计，重复 |
| Profiles 多订阅 | 单订阅已满足；多订阅涉及配置切换与文件管理，收益低 |
| 规则命中统计 | 内核**不提供**命中计数，只能靠日志统计，数据不可靠 → 不做 |

---

## 5. 数据可得性说明

| 类别 | 含义 | 例子 |
|---|---|---|
| ✅ 内核直出 | API 直接返回，可信 | 延迟、连接、内存、provider、DNS 查询 |
| ⚠️ 需推算 | 由多条数据聚合 | “累计流量”= 各连接 upload/download 之和 |
| ❌ 不可得 | 内核没有该数据 | 规则命中次数、单客户端流量排行 |

**结论：P0/P1 全部属于 ✅ 或 ⚠️（聚合），没有需要伪造的数据。**

---

## 6. 建议实施顺序

1. 整组测速（性能收益最直接，改动小）
2. 运行模式切换（排障价值最高）
3. 内核重启 + 缓存清理（放在诊断页）
4. Provider 面板 + DNS 查询（新页面 / 诊断页子项）
5. 连接过滤

预计 **2 天** 可完成 P0 + P1。
