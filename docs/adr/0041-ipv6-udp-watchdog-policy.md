# ADR 0041：IPv6 UDP、看护与多地址策略

- 状态：已接受
- 日期：2026-11-16

## 决策

1. TPROXY6 增加 UDP：
   - 53（v6dns）和 547（v6dhcp）直接 RETURN
   - 其余 UDP 进入 mihomo tproxy，覆盖 QUIC 等
2. ipv6.sh ensure：
   - 检查 ULA、local 路由、TPROXY 跳转、v6ra/v6dns/v6dhcp
   - 缺失或上游 RA 出现时自动重建/关闭
   - watchdog 每 30 秒调用一次
3. 设备策略按 MAC 收集全部非 fe80 IPv6 地址：
   - 每台设备可生成多条 SRC-IP-CIDR /128
   - 已应用映射按 v4 + 全部 v6 组合
   - 减少临时 IPv6 地址变化造成的策略失效

## 后果

- IPv6 UDP/QUIC 也走代理；DNS/DHCPv6 保持本地服务。
- 守护进程崩溃后最多约 30 秒恢复。
- 策略对临时 IPv6 地址更稳，但规则数量会增加。