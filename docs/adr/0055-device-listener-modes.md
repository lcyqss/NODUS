# ADR 0055：设备独立 listener 与规则子表

- 状态：已接受
- 日期：2026-11-16

## 问题

设备策略原先依赖 SRC-IP 规则和 IPv6 MAC listener；首页 global/direct 会绕过规则表，导致设备模式不一致。

## 新架构

每台设备使用独立入口：

- IPv4：iptables nat MAC → edir listener
- IPv6：ip6tables mangle MAC → 	proxy listener
- 直连：MAC RETURN，不创建 listener

### 全局模式

listener 设置：

`yaml
proxy: __dev_组
`

不受首页 global/direct 影响。

### 规则模式

listener 设置：

`yaml
rule: devrule_<id>
`

为每台规则设备生成子规则表：

- 复制当前全局规则
- DIRECT / REJECT / PASS 保持
- 其他代理目标替换成设备策略组
- MATCH 替换成设备策略组

因此规则判断独立，代理出口也由设备组控制。

### 直连模式

MAC 在 iptables/ip6tables 中直接 RETURN，不进入 mihomo。

## 端口

- IPv4 edir：7910、7912、…
- IPv6 	proxy：7911、7913、…
- 最多 40 台全局/规则设备；直连不占端口

## 清理

- 切回直连/停用：删除 listener、子规则、MAC 规则
- 规则重建通过 pply.sh 同步 IPv4/IPv6
- mac-policy.tsv 记录 MAC/模式/端口

## 限制

- 规则设备的子规则是全局规则的副本，配置体积随规则设备数增长
- 子规则中的 SUB-RULE 和复杂逻辑规则暂不重写代理目标
- IPv4 UDP 仍按原有策略不代理