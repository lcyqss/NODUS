# ADR 0044：MAC 级 IPv6 设备策略产品化

- 状态：已接受
- 日期：2026-11-16

## 实现

1. DevicePolicyConfig 增加 device policy listeners 块：
   - 每个启用且有组的策略生成一个 mihomo tproxy listener
   - 端口 7910 + 序号，最多 80 台
   - listener 的 proxy 指向该设备的 __dev_* 组
2. DevicePolicyRepository 写入 mac-policy.tsv：
   - MAC<TAB>PORT<TAB>GROUP
   - 配置写入/无变化时都同步
3. ipv6.sh：
   - setup_tproxy 在 DNS/DHCPv6 RETURN 之后、全局 7895 之前插入 MAC 规则
   - syncmac 根据 TSV 重建规则
   - ensure 检查每个 listener 端口
4. 保留 7895 + SRC-IP 规则作为兜底。

## 结果

同一 ULA 流量按 MAC 命中独立 listener，mihomo 日志显示对应 __dev_* 组，不再依赖临时 IPv6 地址。