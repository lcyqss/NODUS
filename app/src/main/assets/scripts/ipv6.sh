#!/system/bin/sh
# IPv6 LAN gateway module.
# Owns: ULA address/local route, RA daemon, TPROXY6, policy route, IPv6 DNS listener.

BASE=/data/adb/mihomo
. "$BASE/common.sh"
CFG=$BASE/config.yaml
CONF=$BASE/gateway.conf
IP6=/system/bin/ip6tables
RA=$BASE/v6ra
DHCP=$BASE/v6dhcp
DNSRELAY=$BASE/v6dns
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
  # Normalize to exactly one listen line; repeated enable/disable must not duplicate it.
  awk 'BEGIN{done=0} /^    listen: / { if(!done){print "    listen: \"[::]:1053\""; done=1} next } {print}' \
    "$CFG" > "$CFG.tmp" && mv -f "$CFG.tmp" "$CFG"
  reload_core >/dev/null 2>&1 || restart_core >/dev/null 2>&1 || true
}

restore_dns() {
  if [ -f "$DNS_BAK" ]; then
    LINE=$(cat "$DNS_BAK")
    if [ -n "$LINE" ]; then
      awk -v line="$LINE" 'BEGIN{done=0} /^    listen: / { if(!done){print line; done=1} next } {print}' \
        "$CFG" > "$CFG.tmp" && mv -f "$CFG.tmp" "$CFG"
    fi
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
  # Keep DNS (v6dns) and DHCPv6 (v6dhcp) on their local services.
  $IP6 -w -t mangle -A MIHOMO6_TPROXY -p udp --dport 53 -j RETURN
  $IP6 -w -t mangle -A MIHOMO6_TPROXY -p udp --dport 547 -j RETURN
  for IF in $(list_gateways); do
    $IP6 -w -t mangle -A MIHOMO6_TPROXY -i "$IF" -p tcp \
      -j TPROXY --on-port "$TPROXY_PORT" --tproxy-mark "$MARK/$MARK"
    $IP6 -w -t mangle -A MIHOMO6_TPROXY -i "$IF" -p udp \
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
    setsid "$RA" -i "$IF" -p "$ULA_PREFIX" -d "$ULA_ADDR" -t 5s \
      >"$BASE/v6ra.$IF.log" 2>&1 &
    echo $! > "$PIDFILE"
    log "ra started on $IF pid=$(cat "$PIDFILE")"
  done
}

start_dnsrelay() {
  [ -x "$DNSRELAY" ] || return 0
  PIDFILE="$BASE/v6dns.pid"
  [ -f "$PIDFILE" ] && kill "$(cat "$PIDFILE")" 2>/dev/null
  setsid "$DNSRELAY" -l "$ULA_ADDR" -u 127.0.0.1:1053 \
    >"$BASE/v6dns.log" 2>&1 &
  echo $! > "$PIDFILE"
  log "dns relay started pid=$(cat "$PIDFILE")"
}

start_dhcp() {
  [ -x "$DHCP" ] || return 0
  for IF in $(list_gateways); do
    PIDFILE="$BASE/v6dhcp.$IF.pid"
    [ -f "$PIDFILE" ] && kill "$(cat "$PIDFILE")" 2>/dev/null
    setsid "$DHCP" -i "$IF" -d "$ULA_ADDR" \
      >"$BASE/v6dhcp.$IF.log" 2>&1 &
    echo $! > "$PIDFILE"
    log "dhcpv6 started on $IF pid=$(cat "$PIDFILE")"
  done
}

stop_ra() {
  # Withdraw the default route before stopping the daemon.
  if [ -x "$RA" ]; then
    for IF in $(list_gateways); do
      "$RA" -i "$IF" -p "$ULA_PREFIX" -l 0 -once >/dev/null 2>&1
    done
  fi
  for P in "$BASE"/v6ra.*.pid "$BASE"/v6dhcp.*.pid "$BASE/v6dns.pid"; do
    [ -f "$P" ] || continue
    kill "$(cat "$P")" 2>/dev/null
    rm -f "$P"
  done
  killall v6ra v6dhcp v6dns 2>/dev/null
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
  # Never start IPv6 gateway while an upstream router is still advertising RA.
  if detect | grep -q "UPSTREAM_RA=1"; then
    echo "ipv6: upstream RA detected; refusing to start"
    log "enable refused: upstream RA detected"
    if grep -q '^IPV6_GATEWAY=' "$CONF" 2>/dev/null; then
      sed -i 's/^IPV6_GATEWAY=.*/IPV6_GATEWAY=0/' "$CONF"
    fi
    if grep -q '^BLOCK_IPV6=' "$CONF" 2>/dev/null; then
      sed -i 's/^BLOCK_IPV6=.*/BLOCK_IPV6=1/' "$CONF"
    fi
    return 1
  fi
  if grep -q '^BLOCK_IPV6=' "$CONF" 2>/dev/null; then
    sed -i 's/^BLOCK_IPV6=.*/BLOCK_IPV6=0/' "$CONF"
  else
    printf '\nBLOCK_IPV6=0\n' >> "$CONF"
  fi
  ensure_tproxy_port
  ensure_dns6
  for IF in $(list_gateways); do setup_iface "$IF"; done
  setup_tproxy
  stop_ra
  start_dnsrelay
  start_ra
  start_dhcp
  if grep -q '^IPV6_GATEWAY=' "$CONF" 2>/dev/null; then
    sed -i 's/^IPV6_GATEWAY=.*/IPV6_GATEWAY=1/' "$CONF"
  else
    printf '\nIPV6_GATEWAY=1\n' >> "$CONF"
  fi
  if ! pidof mihomo >/dev/null 2>&1; then
    log "enable failed: mihomo is not running"
    echo "ipv6: mihomo is not running"
    return 1
  fi
  log "enabled prefix=$ULA_PREFIX"
  status
}

disable() {
  teardown
  restore_dns
  if grep -q '^BLOCK_IPV6=' "$CONF" 2>/dev/null; then
    sed -i 's/^BLOCK_IPV6=.*/BLOCK_IPV6=1/' "$CONF"
  else
    printf '\nBLOCK_IPV6=1\n' >> "$CONF"
  fi
  for IF in $(list_gateways); do
    $IP6 -w -A MIHOMO6_FWD -i "$IF" -j DROP 2>/dev/null
  done
  if grep -q '^IPV6_GATEWAY=' "$CONF" 2>/dev/null; then
    sed -i 's/^IPV6_GATEWAY=.*/IPV6_GATEWAY=0/' "$CONF"
  fi
  log "disabled"
}

ensure() {
  read_conf
  [ "$IPV6_GATEWAY" = "1" ] || return 0
  if detect | grep -q "UPSTREAM_RA=1"; then
    log "ensure: upstream RA detected, disabling"
    disable
    return 1
  fi
  NEED=0
  for IF in $(list_gateways); do
    ip -6 addr show dev "$IF" 2>/dev/null | grep -q "$ULA_ADDR/64" || NEED=1
    ip -6 route show table local 2>/dev/null | grep -q "local $ULA_ADDR " || NEED=1
  done
  $IP6 -w -t mangle -C PREROUTING -j MIHOMO6_TPROXY 2>/dev/null || NEED=1
  pidof v6ra >/dev/null 2>&1 || NEED=1
  pidof v6dns >/dev/null 2>&1 || NEED=1
  [ -x "$DHCP" ] && ! pidof v6dhcp >/dev/null 2>&1 && NEED=1
  if command -v ss >/dev/null 2>&1; then
    ss -ltn 2>/dev/null | grep -q "\[$ULA_ADDR\]:53" || NEED=1
    ss -lun 2>/dev/null | grep -q ":547" || NEED=1
    ss -ltn 2>/dev/null | grep -q ":7895" || NEED=1
  fi
  if [ "$NEED" = "1" ]; then
    log "ensure: entry incomplete, restarting"
    enable
  fi
  return 0
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
    # Any RA-derived route on the wlan (default or prefix-only) means the
    # upstream router is still advertising IPv6.
    if ip -6 route show table all 2>/dev/null |
      grep -E "dev $IF( |$)" | grep -q "proto ra"; then
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
  ensure) with_lock ensure ;;
  status) status ;;
  detect) detect ;;
  *) echo "usage: $0 enable|disable|status|detect"; exit 1 ;;
esac
