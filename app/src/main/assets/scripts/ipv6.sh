#!/system/bin/sh
# IPv6 LAN gateway module.
# Owns: ULA address/local route, RA daemon, TPROXY6, policy route, IPv6 DNS listener.

BASE=/data/adb/mihomo
. "$BASE/common.sh"
CFG=$BASE/config.yaml
CONF=$BASE/gateway.conf
IP6=/system/bin/ip6tables
RA=$BASE/v6ra
LOG=$BASE/ipv6.log
LOCK=$BASE/.ipv6.lock
DNS_BAK=$BASE/.dns-listen.bak

ULA_PREFIX=fd00:1234::/64
ULA_ADDR=fd00:1234::1
TPROXY_PORT=7895
MARK=0x1
TABLE=100

log() { echo "$(date '+%F %T') $*" >> "$LOG"; }

with_lock() {
  if ! mkdir "$LOCK" 2>/dev/null; then
    echo "ipv6: busy"
    return 1
  fi
  trap 'rmdir "$LOCK" 2>/dev/null' EXIT INT TERM
  "$@"
}

read_conf() {
  BLOCK_IPV6=$(grep -m1 '^BLOCK_IPV6=' "$CONF" 2>/dev/null | cut -d= -f2)
  [ -z "$BLOCK_IPV6" ] && BLOCK_IPV6=1
}

ensure_dns6() {
  if [ ! -f "$DNS_BAK" ]; then
    grep -m1 '^    listen:' "$CFG" > "$DNS_BAK" 2>/dev/null || true
  fi
  sed -i 's#^    listen: .*#    listen: "[::]:1053"#' "$CFG"
  reload_core >/dev/null 2>&1 || restart_core >/dev/null 2>&1 || true
}

restore_dns() {
  if [ -f "$DNS_BAK" ]; then
    LINE=$(cat "$DNS_BAK")
    [ -n "$LINE" ] && sed -i "s#^    listen: .*#$LINE#" "$CFG"
    rm -f "$DNS_BAK"
    reload_core >/dev/null 2>&1 || true
  fi
}

ensure_tproxy_port() {
  grep -q "^tproxy-port:" "$CFG" 2>/dev/null && return 0
  cp -f "$CFG" "$CFG.bak.ipv6.$(date +%s)"
  sed -i "1i tproxy-port: $TPROXY_PORT" "$CFG"
  log "added tproxy-port $TPROXY_PORT"
  reload_core >/dev/null 2>&1 || restart_core >/dev/null 2>&1 || true
}

setup_iface() {
  IF=$1
  ip -6 addr replace "$ULA_ADDR/64" dev "$IF" 2>/dev/null
  # Some Android kernels do not add the local route for a manually added address.
  ip -6 route replace local "$ULA_ADDR/128" dev "$IF" table local 2>/dev/null
  sysctl -w net.ipv6.conf."$IF".forwarding=1 >/dev/null 2>&1
  $IP6 -w -D MIHOMO6_FWD -i "$IF" -j DROP 2>/dev/null
}

setup_tproxy() {
  $IP6 -w -t mangle -N MIHOMO6_TPROXY 2>/dev/null
  $IP6 -w -t mangle -F MIHOMO6_TPROXY
  for IF in $(list_gateways); do
    $IP6 -w -t mangle -A MIHOMO6_TPROXY -i "$IF" -p tcp \
      -j TPROXY --on-port "$TPROXY_PORT" --tproxy-mark "$MARK/$MARK"
  done
  $IP6 -w -t mangle -C PREROUTING -j MIHOMO6_TPROXY 2>/dev/null ||
    $IP6 -w -t mangle -I PREROUTING 1 -j MIHOMO6_TPROXY
  ip -6 rule add fwmark "$MARK/$MARK" table "$TABLE" priority 9999 2>/dev/null
  ip -6 route add local default dev lo table "$TABLE" 2>/dev/null
}

start_ra() {
  for IF in $(list_gateways); do
    PIDFILE="$BASE/v6ra.$IF.pid"
    [ -f "$PIDFILE" ] && kill "$(cat "$PIDFILE")" 2>/dev/null
    setsid "$RA" -i "$IF" -p "$ULA_PREFIX" -d "$ULA_ADDR" \
      >"$BASE/v6ra.$IF.log" 2>&1 &
    echo $! > "$PIDFILE"
    log "ra started on $IF pid=$(cat "$PIDFILE")"
  done
}

stop_ra() {
  # Withdraw the default route before stopping the daemon.
  if [ -x "$RA" ]; then
    for IF in $(list_gateways); do
      "$RA" -i "$IF" -p "$ULA_PREFIX" -l 0 -once >/dev/null 2>&1
    done
  fi
  for P in "$BASE"/v6ra.*.pid; do
    [ -f "$P" ] || continue
    kill "$(cat "$P")" 2>/dev/null
    rm -f "$P"
  done
  killall v6ra 2>/dev/null
}

teardown() {
  stop_ra
  $IP6 -w -t mangle -D PREROUTING -j MIHOMO6_TPROXY 2>/dev/null
  $IP6 -w -t mangle -F MIHOMO6_TPROXY 2>/dev/null
  $IP6 -w -t mangle -X MIHOMO6_TPROXY 2>/dev/null
  ip -6 rule del fwmark "$MARK/$MARK" table "$TABLE" 2>/dev/null
  ip -6 route del local default dev lo table "$TABLE" 2>/dev/null
  for IF in $(list_gateways); do
    ip -6 addr del "$ULA_ADDR/64" dev "$IF" 2>/dev/null
    ip -6 route del local "$ULA_ADDR/128" dev "$IF" table local 2>/dev/null
  done
}

enable() {
  read_conf
  [ -x "$RA" ] || { echo "ipv6: missing $RA"; return 1; }
  ensure_tproxy_port
  ensure_dns6
  for IF in $(list_gateways); do setup_iface "$IF"; done
  setup_tproxy
  stop_ra
  start_ra
  if grep -q '^IPV6_GATEWAY=' "$CONF" 2>/dev/null; then
    sed -i 's/^IPV6_GATEWAY=.*/IPV6_GATEWAY=1/' "$CONF"
  else
    printf '\nIPV6_GATEWAY=1\n' >> "$CONF"
  fi
  log "enabled prefix=$ULA_PREFIX"
  status
}

disable() {
  read_conf
  teardown
  restore_dns
  if [ "$BLOCK_IPV6" = "0" ]; then
    if grep -q '^BLOCK_IPV6=' "$CONF" 2>/dev/null; then
      sed -i 's/^BLOCK_IPV6=.*/BLOCK_IPV6=1/' "$CONF"
    else
      printf '\nBLOCK_IPV6=1\n' >> "$CONF"
    fi
    for IF in $(list_gateways); do
      $IP6 -w -A MIHOMO6_FWD -i "$IF" -j DROP 2>/dev/null
    done
  fi
  if grep -q '^IPV6_GATEWAY=' "$CONF" 2>/dev/null; then
    sed -i 's/^IPV6_GATEWAY=.*/IPV6_GATEWAY=0/' "$CONF"
  fi
  log "disabled"
}

status() {
  echo "prefix=$ULA_PREFIX"
  for IF in $(list_gateways); do
    echo "-- $IF"
    ip -6 addr show dev "$IF" | grep -E 'inet6|link' || true
    ip -6 route show table local | grep -F "$ULA_ADDR" || true
  done
  $IP6 -t mangle -S MIHOMO6_TPROXY 2>/dev/null || true
  pgrep -f "$BASE/v6ra" 2>/dev/null || true
}

detect() {
  for IF in $(list_gateways); do
    if ip -6 route show table all 2>/dev/null |
      grep -Eq "^default via fe80[0-9a-f:]* dev $IF( |$)"; then
      echo "UPSTREAM_RA=1"
      echo "IFACE=$IF"
      return 0
    fi
  done
  echo "UPSTREAM_RA=0"
}

case "$1" in
  enable) with_lock enable ;;
  disable) with_lock disable ;;
  status) status ;;
  detect) detect ;;
  *) echo "usage: $0 enable|disable|status|detect"; exit 1 ;;
esac
