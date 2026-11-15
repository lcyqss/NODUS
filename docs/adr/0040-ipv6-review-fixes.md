# ADR 0040：IPv6 代码审查修复

- 状态：已接受
- 日期：2026-11-16

## 修复

1. 设置页失败不再先写本地状态：
   - IPv6 代理、IPv6 拦截都改为成功后才 store.update
2. GatewayController.setIpv6Gateway(false) 无论内核是否运行都执行 ipv6.sh disable，清理残留 RA/TPROXY/DNS。
3. 开启失败时执行清理并恢复 IPV6_GATEWAY=0、BLOCK_IPV6=1。
4. ipv6.sh enable 自身做上游 RA 检测；被脚本/pply.sh 自动调用时也不会在旁路由冲突下启动。
5. ipv6.sh enable 设置 BLOCK_IPV6=0；disable 无条件恢复 BLOCK_IPV6=1 和 drop 规则。
6. 开启结束检查 mihomo 是否仍在运行，失败返回错误。
7. 设置页初始化时从 gateway.conf 同步真实 IPv6 状态。

## 仍存限制

- 设备策略只取一个 IPv6 地址，临时地址变化可能导致规则更新。
- 仅 TPROXY TCP；IPv6 UDP/QUIC 未代理。
- Windows DHCPv6 DNS 在关闭后可能短暂保留，依赖 IPv4 DNS 兜底。
- 旁路由未实机验证。