# ADR 0042：IPv6 UDP/QUIC 验证

- 状态：已接受
- 日期：2026-11-16

## 验证

红米通过测试手机访问 https://cloudflare-quic.com/ 后，mihomo 连接中出现：

`	ext
network=udp
source=fd00:1234::...
destination=2404:6800:...
chain=日本高速01, 良心云
`

说明 UDP TPROXY 已把 QUIC 接入 mihomo，并由节点转发。

## 观察

连接源地址 d00:1234::2539... 是一个已从邻居表消失的旧临时地址，
当前策略规则只覆盖邻居表中仍存在的地址，因此该连接走了默认规则。

## 结论

- UDP TPROXY 可用。
- 设备策略 IPv6 仍是 best-effort：临时地址轮换后，旧连接可能不命中当前 SRC-IP 规则。
- 如需严格按设备策略，需固定客户端 IPv6 地址或改用 DHCPv6 分配稳定地址。