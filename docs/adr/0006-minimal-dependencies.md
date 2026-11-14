# ADR 0006：首版最小依赖（无 navigation / serialization / datastore）

- 状态：已接受
- 日期：2026-11-14

## 背景

M1 实施时引入 `navigation-compose`、`kotlinx-serialization`、`datastore-preferences`，
构建环境出现以下问题：

- `dl.google.com` 对部分传递依赖（`datastore-core`、`datastore-preferences-core`）
  持续返回连接重置，Gradle 卡在下载
- `navigation-compose:2.7.7` 的 POM 同样不可达
- Gradle 守护进程尝试 IPv6（`SynSent`）导致长时间挂起

## 决策

首版**不引入**上述三个依赖：

| 原计划 | 替代 | 理由 |
|---|---|---|
| `navigation-compose` | `rememberSaveable` + `when(tab)` | 只有 2–5 个页签，无需路由栈 |
| `kotlinx-serialization` | Android 内置 `org.json` | 零依赖、无需编译器插件 |
| `datastore-preferences` | `SharedPreferences` + `MutableStateFlow` | 设置项 <10 个，DataStore 的协程事务优势不明显 |

## 理由

1. 构建可离线复现（`--offline` 通过），不依赖不稳定的制品源
2. 减少 APK 方法与体积
3. 当前 UI 复杂度不需要路由/序列化框架
4. 后续若页面增多或需要类型安全路由，再引入并新增 ADR

## 后果

- `AppNav` 用状态切换，返回栈行为需自行控制（当前场景可接受）
- JSON 解析为手写，需为每个接口维护解析函数（已有单元测试计划）
- 设置读写为同步 SharedPreferences，量小无性能问题
- 将来切换回 DataStore/Serialization 时，接口不变，仅替换实现
