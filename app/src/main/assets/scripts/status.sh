#!/system/bin/sh
BASE=/data/adb/mihomo
. "$BASE/common.sh"
IPT=/system/bin/iptables
echo "网关接口:"
for IF in $(list_gateways); do echo "  $IF  $(iface_net "$IF")"; done
if pidof mihomo >/dev/null 2>&1; then echo "mihomo: 运行中 ($(pidof mihomo))"; else echo "mihomo: 已停止"; fi
echo "NAT 规则: $($IPT -t nat -S MIHOMO_NAT 2>/dev/null | grep -c '^-A')"
echo "FORWARD 规则: $($IPT -S MIHOMO_FWD 2>/dev/null | grep -c '^-A')"
echo "IPv6 拦截: $(ip6tables -S MIHOMO6_FWD 2>/dev/null | grep -c '^-A')"
WP=$(pgrep -f "$BASE/watchdog.sh" 2>/dev/null | head -1)
if [ -n "$WP" ]; then echo "看护进程: $WP"; else echo "看护进程: 无"; fi