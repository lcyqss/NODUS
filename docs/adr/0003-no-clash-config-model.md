# ADR 0003：不在 App 内实现 Clash 配置模型

- 状态：已接受
- 日期：2026-11-14

## 背景

CMFA 在 `core` 模块中用 Kotlin/Go 实现了完整的 Clash 配置模型（ClashConfig、Proxy、ProxyGroup、Rule 等），
用于配置生成、校验与 UI 展示。

## 决策

**不在 App 内实现 Clash 配置模型**。App 只负责：

1. 下载订阅原文
2. 校验是否包含 `proxies:` / `proxy-providers:`
3. 写入 `/data/adb/mihomo/config.yaml`
4. 通过 API 读取内核解析后的结果（`/proxies`、`/rules`、`/configs`）

## 理由

1. YAML 解析、校验、默认值、规则语义都由内核负责，重复实现必然与内核行为不一致
2. 避免“配置模型 → 生成 YAML → 内核再解析”的绕路
3. 降低维护成本：内核升级时 App 不需要跟着改模型
4. UI 所需的节点/分组/规则数据，API 已经提供结构化结果

## 后果

- App 不做配置合法性深度校验（只做粗校验），错误由内核日志暴露
- 需要依赖 mihomo API 的稳定性
- 订阅内容原样落盘，便于用户排查
