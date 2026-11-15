# ADR 0043：MAC 级 IPv6 TPROXY POC

- 状态：已验证，待产品化
- 日期：2026-11-16

## POC

- 为单台设备增加 mihomo listener：
  - 	ype: tproxy
  - port: 7900
  - proxy: __dev_aad4491b2c
  - udp: true
- ip6tables 在通用 TPROXY 规则前增加：
  - -m mac --mac-source <MAC> -j TPROXY --on-port 7900
  - DNS 53 / DHCPv6 547 仍先 RETURN
- 同一台 Windows 的 IPv6 字面量流量，mihomo 日志从默认节点变为：
  __dev_aad4491b2c[新加坡高速05]

## 结论

- MAC 级分流可行，不依赖客户端 IPv6 地址。
- 性能开销：每设备一个 listener + 一条 MAC 规则，家庭规模可忽略。
- 下一步产品化：
  1. 配置生成器按策略生成 listeners
  2. ip6tables 按策略 MAC 同步规则
  3. 保留 SRC-IP/默认规则兜底
  4. 生命周期/端口回收/看护