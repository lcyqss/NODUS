# ADR 0032：局域网 IPv6 代理入口

- 状态：已接受
- 日期：2026-11-16

## 决策

1. 新增独立模块，不复用 IPv4 策略逻辑：
   - `v6ra`：小型 RA 守护进程，AF_PACKET 发送 RA（ULA 前缀、默认路由、MTU）
   - `ipv6.sh`：ULA 地址/local 路由、RA 生命周期、IPv6 TPROXY、策略路由
   - `GatewayController.setIpv6Gateway`：唯一入口
2. RA 使用 AF_PACKET 直接发 L2 帧。该 ROM 上 IPv6 multicast 发送会 `EINVAL`，普通 raw ICMPv6 socket 无法发出 RA；AF_PACKET 绕过该限制。
3. ULA 地址添加后必须显式补 `local` 路由，否则 TPROXY 无法交付。
4. IPv6 TPROXY 使用 mihomo `tproxy-port: 7895`，规则为 mangle PREROUTING + TPROXY mark + table local。
5. 开启 IPv6 代理时关闭 `BLOCK_IPV6`；关闭时恢复默认拦截，避免客户端 IPv6 绕过规则。
6. 设备策略、旁路由 RA 检测后续单独实现，本模块只负责热点侧 IPv6 入口。

## 后果

- 局域网客户端可通过 ULA + RA 访问 IPv6 字面量，并经 mihomo 节点转发。
- 域名 IPv6 仍优先由 fake-IP 处理，二者不冲突。
- 未实现 RDNSS；客户端 DNS 继续使用原 DNS，后续可单独加。
