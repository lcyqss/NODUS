# ADR 0004：root 网关模式，而非 VpnService

- 状态：已接受
- 日期：2026-11-14

## 背景

Android 常规做法是 `VpnService` + TUN（CMFA、v2rayNG 等）。
本项目目标是“热点客户端 / 局域网设备共享代理”，VpnService 只能接管本机流量。

实测结论（LineageOS 23.2 / Android 16）：
- VPN Hotspot：无法注入 tether 路由规则
- 系统「对已连接的设备使用 VPN」：tether upstream 变为 null，客户端断网

## 决策

采用 **root + iptables REDIRECT** 的网关模式：

```
iptables -t nat PREROUTING -i <gw> -s <net> ! -d <net> -p tcp -j REDIRECT --to-ports 7892
iptables -t nat PREROUTING -i <gw> -p udp --dport 53 -j REDIRECT --to-ports 1053
```

mihomo 以 `redir-port` 模式运行，DNS 由内核 fake-ip 接管。

## 理由

1. 唯一能覆盖“非本机流量”的方案
2. 规则集中于自定义链（`MIHOMO_NAT` 等），可整体 flush，避免残留
3. 性能优于 TUN（内核 NAT，无用户态拷贝）
4. 与 Android 版本解耦，不依赖 tethering 内部实现

## 后果

- 必须 root（Magisk）
- 需要处理接口名差异（`wlan*` / `ap*` / `softap*`）
- 网络切换后需重下发规则（事件 + 兜底轮询）
- 首版不支持无 root 设备；后续可评估 VpnService 兼容模式
