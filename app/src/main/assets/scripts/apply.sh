#!/system/bin/sh
BASE=/data/adb/mihomo
. "$BASE/common.sh"
BIN=$BASE/mihomo
CFG=$BASE/config.yaml
LOG=$BASE/mihomo.log
IPT=/system/bin/iptables
IP6=/system/bin/ip6tables
MACFILE=$BASE/mac-policy.tsv

if ! pidof mihomo >/dev/null 2>&1; then
  echo "$(date '+%F %T') starting mihomo" >> "$LOG"
  (setsid "$BIN" -d "$BASE" -f "$CFG" </dev/null >>"$LOG" 2>&1 &)
  sleep 2
fi

sysctl -w net.ipv4.ip_forward=1 >/dev/null 2>&1

jump4() {
  local tbl=$1 parent=$2 chain=$3
  $IPT -w -t "$tbl" -N "$chain" 2>/dev/null
  $IPT -w -t "$tbl" -C "$parent" -j "$chain" 2>/dev/null || $IPT -w -t "$tbl" -I "$parent" 1 -j "$chain"
}
jump6() {
  local parent=$1 chain=$2
  $IP6 -w -N "$chain" 2>/dev/null
  $IP6 -w -C "$parent" -j "$chain" 2>/dev/null || $IP6 -w -I "$parent" 1 -j "$chain"
}

jump4 nat PREROUTING MIHOMO_NAT
jump4 filter FORWARD MIHOMO_FWD
jump4 filter INPUT MIHOMO_IN
jump6 FORWARD MIHOMO6_FWD

$IPT -w -t nat -F MIHOMO_NAT
$IPT -w -F MIHOMO_FWD
$IPT -w -F MIHOMO_IN
$IP6 -w -F MIHOMO6_FWD

for IF in $(list_gateways); do
  NET=$(iface_net "$IF")
  [ -z "$NET" ] && continue
  if [ -f "$MACFILE" ]; then
    TAB=$(printf '\t')
    while IFS="$TAB" read -r MAC MODE P4 P6 IP4; do
      [ -z "$MAC" ] && continue
      [ "$MODE" = "direct" ] || continue
      $IPT -w -t nat -A MIHOMO_NAT -i "$IF" -p tcp -m mac --mac-source "$MAC" -j RETURN
      $IPT -w -A MIHOMO_FWD -i "$IF" -m mac --mac-source "$MAC" -p udp --dport 443 -j RETURN
    done < "$MACFILE"
  fi
  $IPT -w -t nat -A MIHOMO_NAT -i "$IF" -p udp --dport 53 -j REDIRECT --to-ports 1053
  $IPT -w -t nat -A MIHOMO_NAT -i "$IF" -p tcp --dport 53 -j REDIRECT --to-ports 1053
  if [ -f "$MACFILE" ]; then
    TAB=$(printf '\t')
    while IFS="$TAB" read -r MAC MODE P4 P6 IP4; do
      [ -z "$MAC" ] && continue
      case "$MODE" in
        direct)
          $IPT -w -t nat -A MIHOMO_NAT -i "$IF" -p tcp -m mac --mac-source "$MAC" -j RETURN
          $IPT -w -A MIHOMO_FWD -i "$IF" -m mac --mac-source "$MAC" -p udp --dport 443 -j RETURN
          ;;
        global|rule)
          case "$P4" in ''|*[!0-9]*) continue ;; esac
          $IPT -w -t nat -A MIHOMO_NAT -i "$IF" -p tcp -m mac --mac-source "$MAC" \
            -j REDIRECT --to-ports "$P4"
          ;;
      esac
    done < "$MACFILE"
  fi
  $IPT -w -t nat -A MIHOMO_NAT -i "$IF" -s "$NET" ! -d "$NET" -p tcp -j REDIRECT --to-ports 7892
  $IPT -w -A MIHOMO_FWD -i "$IF" -s "$NET" ! -d "$NET" -p udp --dport 443 -j DROP
  $IPT -w -A MIHOMO_IN -i "$IF" -p tcp --dport 9090 -j DROP
  $IPT -w -A MIHOMO_IN -i "$IF" -p tcp --dport 7890 -j DROP
  if [ "$BLOCK_IPV6" = "1" ] && [ "$IPV6_GATEWAY" != "1" ]; then
    $IP6 -w -A MIHOMO6_FWD -i "$IF" -j DROP
  fi
done

if [ "$IP_MODE" = "auto" ]; then
  NOW=$(date +%s)
  LAST=$(cat "$BASE/.v6check" 2>/dev/null || echo 0)
  if [ $((NOW - LAST)) -gt 300 ]; then
    echo "$NOW" > "$BASE/.v6check"
    if has_v6; then WANT=ipv6-prefer; else WANT=ipv4-prefer; fi
    CUR=$(grep -m1 -o 'ip-version: [a-z0-9-]*' "$CFG" 2>/dev/null | head -1 | awk '{print $2}')
    if [ "$CUR" != "$WANT" ]; then
      sh "$BASE/set-ipver.sh" auto >> "$LOG" 2>&1
    fi
  fi
fi
# IPv6 局域网网关：配置开启时在应用规则后启动入口
if [ "$IPV6_GATEWAY" = "1" ] && [ -x "$BASE/ipv6.sh" ]; then
  sh "$BASE/ipv6.sh" enable >> "$LOG" 2>&1
fi
