#!/system/bin/sh
BASE=/data/adb/mihomo
. "$BASE/common.sh"

# 先停 IPv6 入口，避免残留 RA/TPROXY6；只拆除、不改写 BLOCK_IPV6
[ -f "$BASE/ipv6.sh" ] && sh "$BASE/ipv6.sh" stop >/dev/null 2>&1

IPT=/system/bin/iptables
IP6=/system/bin/ip6tables

if [ -f "$BASE/watchdog.pid" ]; then
  kill "$(cat "$BASE/watchdog.pid")" 2>/dev/null
  rm -f "$BASE/watchdog.pid"
fi
pkill -f "watchdog.sh" 2>/dev/null

$IPT -w -t nat -F MIHOMO_NAT 2>/dev/null
$IPT -w -t nat -D PREROUTING -j MIHOMO_NAT 2>/dev/null
$IPT -w -t nat -X MIHOMO_NAT 2>/dev/null
$IPT -w -F MIHOMO_FWD 2>/dev/null
$IPT -w -D FORWARD -j MIHOMO_FWD 2>/dev/null
$IPT -w -X MIHOMO_FWD 2>/dev/null
$IPT -w -F MIHOMO_IN 2>/dev/null
$IPT -w -D INPUT -j MIHOMO_IN 2>/dev/null
$IPT -w -X MIHOMO_IN 2>/dev/null
$IP6 -w -F MIHOMO6_FWD 2>/dev/null
$IP6 -w -D FORWARD -j MIHOMO6_FWD 2>/dev/null
$IP6 -w -X MIHOMO6_FWD 2>/dev/null

# 清理 mangle：MSS 与 UDP TPROXY，否则停网关后客户端 UDP 443 会被指向已停止的内核
$IPT -w -t mangle -F MIHOMO_MSS 2>/dev/null
$IPT -w -t mangle -D FORWARD -j MIHOMO_MSS 2>/dev/null
$IPT -w -t mangle -X MIHOMO_MSS 2>/dev/null
$IPT -w -t mangle -F MIHOMO_TPROXY4 2>/dev/null
$IPT -w -t mangle -D PREROUTING -j MIHOMO_TPROXY4 2>/dev/null
$IPT -w -t mangle -X MIHOMO_TPROXY4 2>/dev/null
ip rule del fwmark 0x1/0x1 table 100 priority 9999 2>/dev/null
ip route del local default dev lo table 100 2>/dev/null

killall mihomo 2>/dev/null
echo "$(date '+%F %T') gateway stopped" >> "$BASE/mihomo.log"