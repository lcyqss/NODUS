# ADR 0036：Windows DHCPv6 DNS 兜底

- 状态：已接受
- 日期：2026-11-16

## 背景

Windows 10 通常忽略 RA RDNSS，仍使用原 DNS，导致：
- 域名规则在 IPv6 路径上不命中
- DNS 查询不经过 mihomo
- 域名 IPv6 解析不可控

## 决策

1. 6ra 的 RA flags 设置 O=1，要求客户端使用 DHCPv6 获取其他配置。
2. 新增 6dhcp 最小无状态 DHCPv6 服务：
   - 监听 [ff02::1:2]:547
   - 响应 Information-request/Solicit/Request/Renew/Rebind
   - 返回 Server ID、Client ID、DNS option 23
3. DHCPv6 与 RA 一样按网关接口启动，关闭时一起停止。
4. mihomo DNS 仍监听 [::]:1053，由 IPv6 入口模块维护。

## 后果

- Windows 通过 DHCPv6 获得 d00:1234::1 作为 IPv6 DNS。
- 域名查询进入 mihomo，域名规则/fake-IP 与 IPv4 路径一致。
- 关闭 IPv6 代理后 Windows 可能短暂保留 DHCPv6 DNS，但 IPv4 DNS 仍可作为兜底；网络重连会清理。