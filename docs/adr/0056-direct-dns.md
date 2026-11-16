# ADR 0056：直连模式 DNS 绕过

- 状态：已接受
- 日期：2026-11-16

## 问题

设备直连模式下：

- TCP 已经通过 MAC RETURN 绕过代理
- 但 DNS 仍被重定向到 mihomo fake-IP
- 客户端拿到 198.18.x.x 后直连，无法访问
- PDD 使用 HTTPDNS/IP，可能正常；B 站图片/网页依赖域名 DNS，失败

## 修复

### IPv4

直连 MAC 的 RETURN 移到 MIHOMO_NAT 最前面，先于 DNS redirect。

### IPv6

6dns 增加：

`	ext
-b direct-dns.txt
-r 127.0.0.1:53
`

- 直连客户端源 IPv6 → dnsmasq 真实 DNS
- 其他客户端 → mihomo 1053 fake-IP

DevicePolicyRepository 在应用策略时写入 direct-dns.txt。

## 结果

直连设备不再收到 fake-IP；代理设备仍使用 mihomo DNS。