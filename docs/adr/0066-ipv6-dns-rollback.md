# ADR 0066：IPv6 DNS 实验回滚

- 状态：已接受
- 日期：2026-11-17

## 问题

尝试在 IPv6 网关启用时写入 `dns.ipv6: true`，但写入缩进与现有 DNS 块不一致，产生：

```yaml
dns:
  ipv6: true
    enable: true
```

mihomo 报 `yaml: line 15: mapping values are not allowed`，核心无法启动，热点客户端因此无网络。

## 回滚

- 恢复 `ipv6.sh` / `ensure_config.sh` 的原始 DNS 行为
- 不再自动写入 `dns.ipv6`
- `GatewayHealthChecker` 的 IPv6 检查改为通过 v6dns 查询 A 记录，验证 ULA + relay 可用，不再要求 AAAA
- `DevicePolicyConfig` 清理旧版本写出的 2 空格 `ipv6` 行和重复 4 空格行

## 结果

- 配置恢复可解析，核心启动
- IPv6 健康检查不再把“没有 AAAA”误判为 IPv6 不可用