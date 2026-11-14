#!/system/bin/sh
# 卸载前清理：停止网关、移除 iptables 规则、删除运行目录
BASE=/data/adb/mihomo

[ -f "$BASE/stop.sh" ] && sh "$BASE/stop.sh" 2>/dev/null
pkill -f "$BASE/watchdog.sh" 2>/dev/null
killall mihomo 2>/dev/null
sleep 1

for CHAIN in MIHOMO_NAT; do
  iptables -t nat -D PREROUTING -j "$CHAIN" 2>/dev/null
  iptables -t nat -F "$CHAIN" 2>/dev/null
  iptables -t nat -X "$CHAIN" 2>/dev/null
done
for CHAIN in MIHOMO_FWD MIHOMO_IN; do
  iptables -D FORWARD -j "$CHAIN" 2>/dev/null
  iptables -D INPUT -j "$CHAIN" 2>/dev/null
  iptables -F "$CHAIN" 2>/dev/null
  iptables -X "$CHAIN" 2>/dev/null
done
ip6tables -D FORWARD -j MIHOMO6_FWD 2>/dev/null
ip6tables -F MIHOMO6_FWD 2>/dev/null
ip6tables -X MIHOMO6_FWD 2>/dev/null

rm -rf "$BASE"
echo cleaned
