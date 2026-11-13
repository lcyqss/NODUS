#!/system/bin/sh
BASE=/data/adb/mihomo
CONF=$BASE/gateway.conf
[ -f "$CONF" ] && . "$CONF"

list_gateways() {
  for i in $(ls /sys/class/net 2>/dev/null); do
    case "$i" in
      lo|rmnet*|tun*|dummy*|sit*|ip6*|p2p*|bond*) continue ;;
    esac
    case "$i" in
      wlan*|ap*|softap*|swlan*) ;;
      *) continue ;;
    esac
    [ -n "$IFACE" ] && [ "$i" != "$IFACE" ] && continue
    ip -4 addr show dev "$i" 2>/dev/null | grep -q "inet " || continue
    echo "$i"
  done
}

iface_net() {
  ip route show dev "$1" scope link 2>/dev/null | awk '{print $1}' | grep '/' | head -1
}