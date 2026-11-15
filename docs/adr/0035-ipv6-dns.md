# ADR 0035：IPv6 DNS 与 RDNSS

- 状态：已接受
- 日期：2026-11-16

## 决策

1. 开启 IPv6 代理时，把 mihomo DNS 监听改为 [::]:1053：
   - Linux 双栈 socket，同时接受 IPv4/IPv6 查询
   - 已验证 127.0.0.1:1054 与 d00:1234::1:1054 都能收到响应
2. RA 携带 RDNSS，指向 d00:1234::1。
3. 原始 DNS listen 行备份到 .dns-listen.bak，关闭代理时恢复。
4. 关闭时先撤回 RA，再恢复 DNS 监听，避免客户端继续指向已停止的服务。

## 后果

- Android/iOS 等支持 RDNSS 的客户端会自动使用手机 DNS。
- Windows 10 通常忽略 RDNSS，仍使用原 DNS；域名解析仍可走 fake-IP。
- IPv6 字面量不依赖 DNS，保持可用。