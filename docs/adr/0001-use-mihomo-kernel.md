# ADR 0001：内核选用 mihomo

- 状态：已接受
- 日期：2026-11-14

## 背景

需要为 root Android 网关选择一个转发内核，候选：mihomo、sing-box、Xray。

## 决策

选用 **mihomo**（Clash.Meta）。

## 理由

1. **订阅生态**：机场订阅以 Clash YAML 为主，mihomo 原生解析，无需转换
2. **面板/API**：Clash 兼容 REST API，便于 App 直接控制
3. **透明代理**：原生支持 `redir-port` / `tproxy` / `tun`
4. **DNS**：内置 fake-ip，防污染
5. **多协议**：VLESS/Reality、Hysteria2、TUIC、SS、Trojan、WireGuard
6. **地址族控制**：`ip-version` 支持 IPv4/IPv6 优先

## 后果

- 常驻内存约 100MB（可接受）
- GPL-3.0：若分发修改后的内核需开源；自用无影响
- 后续如需切换内核，通过 `CoreAdapter` 抽象（暂不实现）
