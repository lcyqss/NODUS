# 整体回归记录（2026-11-14）

- 设备：Xiaomi Mi 10（umi），LineageOS 23.2（Android 16），Magisk root
- 版本：v2.5.3（versionCode 37）
- 内核：mihomo v1.19.34，47 节点

## 结果总览

| # | 项目 | 结果 | 证据 |
|---|---|---|---|
| T1 | 概览电源键停止 | ✅ | pid 消失、MIHOMO_NAT 清空、desired_running=false |
| T2 | 电源键启动 | ✅ | pid 恢复、4 条 nat 规则、mixed 代理 204 |
| T3 | 模式切换（规则/全局/直连） | ✅ | `/configs.mode` 依次 global/direct/rule，三种模式下代理均 204 |
| T4 | 节点地址族切换 | ✅ | `ip-version` 47 行随 v4/v6/auto 变化，内核自动重启 |
| T5 | IPv6 拦截 | ✅ | 热点开启时 MIHOMO6_FWD 有 `-i wlan2 -j DROP` |
| T6 | 订阅信息 | ⚠️ 跳过 | 当前未填订阅 URL（数据重置后未重新导入） |
| T7 | 连接列表 / 全部断开 | ✅ | 断开前 1 条 → 断开后 0 条 |
| T8 | 日志 WebSocket | ✅ | 实时输出 `match Match using 良心云[...]` |
| T9 | 重启自启 | ✅ | 重启后 app 服务、mihomo、代理均正常；规则在热点出现后自动下发 |
| T10 | 热点网关 | ✅ | `start-softap` 后自动生成 DNS/7892 重定向、UDP443 DROP、API 端口保护 |
| T11 | 通知栏「停止网关」 | ✅ | 内核停止、链清空、desired_running=false |
| T12 | 备份 / 恢复 | ⚠️ 未测 | 需要 UI 粘贴 JSON，自动化不便 |
| T13 | QS 磁贴 | ⚠️ 部分 | 服务已注册（BIND_QUICK_SETTINGS_TILE），需手动添加到通知栏 |

## 详细记录

### T1/T2 启停

```
stop  → pid: (空)   nat: 0   desired_running=false
start → pid: 19868  nat: 4   proxy 204 (0.68s)
```

### T3 模式

```
tap 全局 → mode=global  proxy=204
tap 直连 → mode=direct  proxy=204
tap 规则 → mode=rule    proxy=204
```

### T4 地址族

`set-ipver.sh` 修改每个节点的 `ip-version:`，不是全局 `ipv6` 字段：

```
auto → ipv4-prefer 47 行（当前网络无 IPv6 出口）
v6   → ipv6-prefer 47 行
v4   → ipv4-prefer 47 行
```

`/configs.ipv6=true` 保持不变属预期（该字段是内核 IPv6 支持开关）。

### T10 热点（重点）

开启热点（`cmd wifi start-softap MihomoTest wpa2 ...`，接口 wlan2，网段 10.98.81.0/24）后自动生成：

```
-A MIHOMO_NAT -i wlan2 -p udp --dport 53  -j REDIRECT --to-ports 1053
-A MIHOMO_NAT -i wlan2 -p tcp --dport 53  -j REDIRECT --to-ports 1053
-A MIHOMO_NAT -s 10.98.81.0/24 ! -d 10.98.81.0/24 -i wlan2 -p tcp -j REDIRECT --to-ports 7892
-A MIHOMO_FWD -s 10.98.81.0/24 ! -d 10.98.81.0/24 -i wlan2 -p udp --dport 443 -j DROP
-A MIHOMO_IN  -i wlan2 -p tcp --dport 9090 -j DROP
-A MIHOMO_IN  -i wlan2 -p tcp --dport 7890 -j DROP
-A MIHOMO6_FWD -i wlan2 -j DROP
net.ipv4.ip_forward=1
```

关闭热点后规则自动清空。

## 发现与说明

1. **手机自身流量不走透明代理（设计如此）**
   apply.sh 只处理 `-i <网关接口>` 的流量，没有 OUTPUT 链。手机本机 App 需要显式代理（7890）或由用户自行配置。若希望“本机也透明代理”，需要新增 OUTPUT 重定向（建议评估，避免与热点规则冲突）。

2. **重启后 adb 回到 shell 用户**
   重启后 `iptables -S` 需要 `adb root` 才能看到规则，初次读取为 0 是权限导致的假象，不是规则丢失。

3. **规则下发依赖网关接口存在**
   没有热点/局域网接口时不会生成重定向规则（符合“网关”定位）。

## 建议后续人工回归

| 项 | 方法 |
|---|---|
| 真实热点客户端 | 用另一台设备连热点，访问外网并查看连接页是否出现该设备 |
| LAN 旁路由 | 手机接局域网，另一设备网关指向手机，验证透明代理 |
| 订阅更新 | 填入真实订阅 URL，点「立即更新」，验证节点数变化与回滚 |
| 备份恢复 | 备份分享 → 清空规则 → 粘贴恢复 |
| QS 磁贴 | 通知栏编辑添加「Mihomo 网关」并开关 |
