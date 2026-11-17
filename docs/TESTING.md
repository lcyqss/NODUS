# 测试状态与旁路由验证清单

## 已验证

- 手机热点 + IPv4 REDIRECT
- 手机热点 + IPv6 TPROXY
- 设备策略（MAC / 条件标签 / 延迟 / IP 跟随）
- root 持久 shell
- 内核 / Geo 更新
- 应用内更新代码路径（发布 Release 前只能验证失败态）
- 应用内更新完整链路：GitHub Release 读取、签名一致性、CI 产物覆盖安装（v2.63.0）
- 恢复备份：订阅缓存缺失时自动重新下载，规则与设备策略自动重新应用

## 未完整验证

### DIRECT 模式设备

- IPv4：已验证（2026-11-18）
  - 修复前：UDP DNS 被劫持到 fake-IP，DIRECT 设备拿到 198.18.x.x 后又绕过代理，域名访问不可用
  - 修复后：DIRECT 设备的 TCP / UDP 53 在 fake-IP 重定向之前 RETURN，DNS 走网关 dnsmasq
  - 真机结果：ip.sb 显示本地真实 IP（不是新加坡），百度正常打开，mihomo 日志不再出现该设备的新连接
- IPv6：DIRECT 设备会绕过 TPROXY，但当前内核没有 NAT66，ULA 地址无法直连公网 IPv6；
  在运营商不提供原生 IPv6 共享的设备上，DIRECT 模式实际只能保证 IPv4 可用

### 旁路由模式

当前未完成以下场景的真机回归：

- 手机上联 Wi-Fi，手机作为内网设备网关
- 上游路由器仍发送 IPv6 RA 时的冲突处理
- 旁路由下 DHCP / DNS 的指向与回退
- 上游网络切换后 iptables / TPROXY 规则重建
- 多网卡、双栈和 IPv6-only 上游

### 旁路由验证清单

```text
[ ] 手机连接上游 Wi-Fi，确认自身可上网
[ ] 关闭上游 DHCP，或把网关/DNS 指到手机
[ ] 客户端设置静态 IP，网关 = 手机 LAN IP
[ ] IPv4 TCP / UDP / DNS 测试
[ ] IPv6 ULA、RA、v6dns 测试
[ ] 上游存在 RA 时确认不会错误接管
[ ] 切换上游网络后规则自动重建
[ ] 关闭网关后客户端恢复直连
```

在完成这些验证前，不要把旁路由模式标为“稳定”或用于生产环境。

## 回归记录

每次涉及网络规则、IPv6、root 脚本的改动，至少记录：

- 设备型号 / ROM / root 管理器
- 上游网络类型
- 测试模式（热点 / 旁路由）
- IPv4 / IPv6 / DNS 结果
- 失败时的 `mihomo.log` 和 `iptables` 快照

### 2026-11-17 · v2.63.0

- 模式：手机热点 + IPv4 iptables REDIRECT + IPv6 TPROXY
- 启停网关：停止后显示「已停止」，重新启动后内核恢复运行
- 运行健康（重启后）：DNS / 端口 / 代理 204 / TPROXY / DNS 域名 / 规则加载 全部正常
- 代理连通性：204
- 连接页：35 条连接，两个热点客户端 IP 均正常，规则匹配正确
- 设备策略：1 条启用，主 13 / 回落 25 节点，无错误
- 自定义规则：`ip.sb` 已生效
- 备份导出：JSON 字段完整（subscriptions / subName / settings / rules）
- 应用更新：CI 产物签名与本地一致，可直接覆盖安装
- 备注：未覆盖旁路由模式，相关清单仍未勾选
