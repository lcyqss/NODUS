#!/system/bin/sh
# IPv6 局域网网关模块
# 职责：ULA 地址/本地路由、RA 守护进程、TPROXY6、策略路由。
# 只做 IPv6 入口；代理规则仍由 mihomo 负责。

BASE=/data/adb/mihomo
. "$BASE/common.sh"
CFG=$BASE/config.yaml
CONF=$BASE/gateway.conf
IP6=/system/bin/ip6tables
RA=$BASE/v6ra
LOG=$BASE/ipv6.log
LOCK=$BASE/.ipv6.lock

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
  # Android/部分内核不会随地址自动补 local 路由；TPROXY 依赖它。
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
    setsid "$RA" -i "$IF" -p "$ULA_PREFIX" \
      >"$BASE/v6ra.$IF.log" 2>&1 &
    echo $! > "$PIDFILE"
    log "ra started on $IF pid=$(cat "$PIDFILE")"
  done
}

stop_ra() {
  for P in "$BASE"/v6ra.*.pid; do
    [ -f "$P" ] || continue
    kill "$(cat "$P")" 2>/dev/null
    rm -f "$P"
  done
  # 兼容手工启动/旧版本残留
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
    if [ "$BLOCK_IPV6" = "1" ]; then
      $IP6 -w -A MIHOMO6_FWD -i "$IF" -j DROP 2>/dev/null
    fi
  done
}

enable() {
  read_conf
  [ -x "$RA" ] || { echo "ipv6: missing $RA"; return 1; }
  ensure_tproxy_port
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
  # 关闭 IPv6 代理后恢复默认拦截，避免客户端 IPv6 绕过规则
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

case "$1" in
  enable) with_lock enable ;;
  disable) with_lock disable ;;
  status) status ;;
  *) echo "用法: $0 enable|disable|status"; exit 1 ;;
esac
