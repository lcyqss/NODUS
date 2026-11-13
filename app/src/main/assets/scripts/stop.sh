#!/system/bin/sh
BASE=/data/adb/mihomo
. "$BASE/common.sh"
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

killall mihomo 2>/dev/null
echo "$(date '+%F %T') gateway stopped" >> "$BASE/mihomo.log"