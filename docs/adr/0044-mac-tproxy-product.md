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
## 实机验证（2026-11-16）

主力手机（MAC `4e:fa:61:9d:f7:3c`）访问 IPv6 字面量后：

```text
tcp fd00:1234::70ae:da76:4abe:c791 -> 2606:4700:4700::1111
inbound=dev-aad4491b, inboundPort=7910
chains=香港高速01|BGP|CMCU, __dev_aad4491b2c
```

同一时刻 Windows 无策略流量：

```text
tcp fd00:1234::2539:2958:a7ca:ccba -> 2404:6800:400b:c01b::bc
inbound=DEFAULT-TPROXY, inboundPort=7895
chains=日本高速01|BGP|CUCM, 良心云
```

说明 MAC listener 已优先命中，且不依赖客户端当前 IPv6 地址。
## 生命周期验证（2026-11-16）

1. 新增第二台设备（Windows MAC `ac:d5...`）：
   - listener 7911 生成
   - TSV 增加一行
   - ip6tables 增加对应 MAC 规则
2. 删除该策略：
   - listener 7911 消失
   - TSV 恢复为只有主力手机
   - ip6tables 的 7911 规则消失
   - 主力手机 7910 保留
3. `ipv6.sh ensure` 对健康 listener 不重复重启。