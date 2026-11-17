# ADR 0069：页面拆分与常量集中化

- 状态：已接受
- 日期：2026-11-17
- 关联：v2.63.0

## 背景

功能迭代期间多次叠加需求，出现：

- 单文件过大：`RulesScreen` 825 行、`SettingsScreen` 664 行、`SettingsViewModel` 593 行
- 一个 ViewModel 同时负责网关设置、健康检查、内核更新、Geo 更新、备份恢复、应用更新
- 路径 / 端口 / 密钥 / 仓库地址散落在十几个文件里，改一处要全局搜索

## 决策

### 1. UI 按「编排 / 分区 / 弹窗 / 文本」四层拆分

| 页面 | 拆分结果 |
|---|---|
| 分流 · 规则 | `RulesScreen`（编排）+ `RuleLists`（列表）+ `RuleDialogs`（弹窗） |
| 分流 · 设备策略 | `DevicePolicyScreen`（列表）+ `DevicePolicySettingsPage`（设置页） |
| 设置 | `SettingsScreen`（编排）+ `SettingsSections`（五个分区）+ `SettingsDialogs` + `SettingsFormat` |

约束：

- 拆分只移动代码，不改交互与布局
- 分区函数只接受显式状态与回调，不再直接持有 ViewModel
- 每次拆分单独提交，并在真机上逐项回归

### 2. ViewModel 按「是否中断内核」拆分

- `SettingsViewModel`：网关设置、地址族、IPv6、运行健康、备份恢复、应用更新状态
- `ComponentUpdateViewModel`：内核检查/下载/安装/回退、Geo 更新

两个 ViewModel 的 `message` / `busy` 在页面层合并：提示只显示一条，任一操作进行中统一禁用设置项。

### 3. 常量集中到 `AppConfig`

`core/AppConfig` 统一定义：

```text
BASE_DIR / API_PORT / PROXY_PORT / DNS_PORT / LOOPBACK / SECRET
PACKAGE_NAME / DEVICE_REDIR_PORT_BASE / GITHUB_OWNER / GITHUB_REPO
```

Kotlin 侧不再出现字面量；shell 脚本仍使用同一组端口，修改时必须同步 `assets/`。

## 结果

```text
RulesScreen    825 → 274 行（其余分布在 RuleLists / RuleDialogs）
SettingsScreen 664 → 315 行（其余分布在 Sections / Dialogs / Format）
SettingsViewModel 593 → 298 行（组件更新拆到 ComponentUpdateViewModel 260 行）
```

## 权衡

- 拆分会引入更多参数传递，但换来了单文件可读性和可单独回归
- 常量集中后，修改端口仍需同步 shell 脚本，这是当前架构的固有限制
