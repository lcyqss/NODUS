# ADR 0045：订阅间隔与 Provider 说明

- 状态：已接受
- 日期：2026-11-16

## 自动更新

- 数据字段从 utoUpdateHours 迁移为 utoUpdateMinutes
- 兼容旧 JSON：没有 utoUpdateMinutes 时按 utoUpdateHours * 60
- 保留写回 utoUpdateHours，旧版本可读
- 预设：0/10/20/30/60/120/360/720/1440 分钟
- 支持自定义，最小 10 分钟，必须为 10 的倍数
- 后台检查从 30 分钟缩短到 5 分钟；实际由于系统调度可能有延迟
- lastUpdateAt 按分钟判断是否到期

## Provider

- 良心云 订阅把节点直接写在 proxies:，没有 proxy-providers:
- 因此详情页显示未使用 Provider 是正确行为
- 已把提示改为“该订阅未使用 proxy-providers”
- Provider 健康检查只适用于 mihomo proxy-providers 型订阅