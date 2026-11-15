# ADR 0034：旁路由 IPv6 RA 检测

- 状态：已接受
- 日期：2026-11-16

## 决策

1. ipv6.sh detect 检查 wlan 接口上是否存在来自上游的 IPv6 默认路由：
   default via fe80... dev <wlan>。
2. 热点模式下 wlan2 没有这样的路由，检测为 0。
3. 旁路由模式下主路由 RA 未关闭时检测为 1；此时：
   - 设置页 IPv6 代理开关置灰
   - 已开启的允许关闭
   - GatewayController.setIpv6Gateway 直接拒绝开启
4. 设置页打开时重新检测，避免状态过期。

## 后果

- 避免在旁路由模式下与主路由 RA 竞争，造成 IPv6 泄漏。
- 检测依赖 Android 是否把上游 RA 默认路由放进路由表；若 ROM 不接收 RA，检测可能假阴性，后续可用 AF_PACKET 嗅探补强。