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
iptables -t mangle -D FORWARD -j MIHOMO_MSS 2>/dev/null
iptables -t mangle -F MIHOMO_MSS 2>/dev/null
iptables -t mangle -X MIHOMO_MSS 2>/dev/null
iptables -t mangle -D PREROUTING -j MIHOMO_TPROXY4 2>/dev/null
iptables -t mangle -F MIHOMO_TPROXY4 2>/dev/null
iptables -t mangle -X MIHOMO_TPROXY4 2>/dev/null
ip rule del fwmark 0x1/0x1 table 100 priority 9999 2>/dev/null
ip route del local default dev lo table 100 2>/dev/null
ip6tables -D FORWARD -j MIHOMO6_FWD 2>/dev/null
ip6tables -F MIHOMO6_FWD 2>/dev/null
ip6tables -X MIHOMO6_FWD 2>/dev/null

rm -rf "$BASE"
echo cleaned
