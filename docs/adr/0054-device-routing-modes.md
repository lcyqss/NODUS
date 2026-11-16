# ADR 0054：设备级路由模式

- 状态：已接受
- 日期：2026-11-16

## 模型

设备策略新增 mode：

- GLOBAL：设备全部流量走设备策略组（v2.53.0 前行为）
- RULE：遵循首页规则；规则未命中时走设备策略组
- DIRECT：设备全部直连

旧策略没有 mode 时迁移为 GLOBAL。

## 实现

- 模型：DeviceRoutingMode
- UI：设备策略编辑器增加三选一
- DIRECT：IPv4/IPv6 SRC-IP-CIDR 置顶到 DIRECT，不创建 MAC listener
- GLOBAL：沿用置顶 SRC-IP-CIDR + IPv6 MAC listener
- RULE：不生成置顶规则；在 MATCH 前插入设备 SRC-IP-CIDR 兜底规则
- ppliedKey 加入 mode，切换模式会重新应用

## 限制

- RULE 模式使用全局规则；只有“未命中规则”的流量使用设备策略组。
- IPv6 RULE/DIRECT 使用源地址规则，临时 IPv6 地址变化时是 best-effort。
- 首页 global/direct 模式会忽略规则表；设备策略的完整行为仍以首页 ule 模式为准。