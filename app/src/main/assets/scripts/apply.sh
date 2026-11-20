#!/system/bin/sh
BASE=/data/adb/mihomo
. "$BASE/common.sh"
BIN=$BASE/mihomo
CFG=$BASE/config.yaml
LOG=$BASE/mihomo.log
IPT=/system/bin/iptables
IP6=/system/bin/ip6tables
MACFILE=$BASE/mac-policy.tsv

# 串行化 apply：设置切换、策略保存、网络事件可能同时触发，
# 并发执行会把 iptables 链刷成半成品（DNS 劫持有了、TCP 重定向没加）。
LOCK="$BASE/.apply.lock"
acquire_lock() {
  i=0
  while ! mkdir "$LOCK" 2>/dev/null; do
    old=$(cat "$LOCK/pid" 2>/dev/null)
    if [ -n "$old" ] && ! kill -0 "$old" 2>/dev/null; then
      rm -rf "$LOCK"
      continue
    fi
    i=$((i + 1))
    if [ "$i" -ge 50 ]; then
      echo "apply: another apply is running" >&2
      return 1
    fi
    sleep 0.2
  done
  echo $$ > "$LOCK/pid"
  trap 'rm -rf "$LOCK"' EXIT INT TERM
  return 0
}
acquire_lock || exit 1

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
jump4 mangle FORWARD MIHOMO_MSS
jump4 mangle PREROUTING MIHOMO_TPROXY4
jump6 FORWARD MIHOMO6_FWD

$IPT -w -t nat -F MIHOMO_NAT
$IPT -w -t mangle -F MIHOMO_MSS
$IPT -w -t mangle -F MIHOMO_TPROXY4
$IPT -w -F MIHOMO_FWD
$IPT -w -F MIHOMO_IN
$IP6 -w -F MIHOMO6_FWD

HAS_IF=0
for IF in $(list_gateways); do
  NET=$(iface_net "$IF")
  [ -z "$NET" ] && continue
  HAS_IF=1
  # 蜂窝网络下 PMTU 黑洞会让大包卡住；按路径 MTU 收敛 MSS（对直连流量有效）
  $IPT -w -t mangle -A MIHOMO_MSS -i "$IF" -p tcp --tcp-flags SYN,RST SYN \
    -j TCPMSS --clamp-mss-to-pmtu 2>/dev/null
  # UDP 代理的固定排除项：DNS / DHCP / 本网段 / 组播广播
  if [ "$UDP_PROXY" = "1" ]; then
    $IPT -w -t mangle -A MIHOMO_TPROXY4 -i "$IF" -p udp --dport 53 -j RETURN
    $IPT -w -t mangle -A MIHOMO_TPROXY4 -i "$IF" -p udp --dport 67 -j RETURN
    $IPT -w -t mangle -A MIHOMO_TPROXY4 -i "$IF" -p udp --dport 68 -j RETURN
    $IPT -w -t mangle -A MIHOMO_TPROXY4 -i "$IF" -d "$NET" -j RETURN
    $IPT -w -t mangle -A MIHOMO_TPROXY4 -i "$IF" -d 224.0.0.0/4 -j RETURN
    $IPT -w -t mangle -A MIHOMO_TPROXY4 -i "$IF" -d 255.255.255.255 -j RETURN
  fi
  if [ -f "$MACFILE" ]; then
    TAB=$(printf '\t')
    while IFS="$TAB" read -r MAC MODE P4 P6 IP4; do
      [ -z "$MAC" ] && continue
      [ "$MODE" = "direct" ] || continue
      # DIRECT 设备不进代理：DNS 也不能被劫持到 fake-IP，
      # 否则它拿到 198.18.x.x 后又绕过代理，会直接连到不可路由地址。
      $IPT -w -t nat -A MIHOMO_NAT -i "$IF" -p tcp -m mac --mac-source "$MAC" -j RETURN
      $IPT -w -t nat -A MIHOMO_NAT -i "$IF" -p udp -m mac --mac-source "$MAC" --dport 53 -j RETURN
      $IPT -w -A MIHOMO_FWD -i "$IF" -m mac --mac-source "$MAC" -p udp --dport 443 -j RETURN
      if [ "$UDP_PROXY" = "1" ]; then
        $IPT -w -t mangle -A MIHOMO_TPROXY4 -i "$IF" -p udp -m mac --mac-source "$MAC" -j RETURN
      else
        $IPT -w -t mangle -A MIHOMO_TPROXY4 -i "$IF" -p udp -m mac --mac-source "$MAC" --dport 443 -j RETURN
      fi
    done < "$MACFILE"
  fi
  $IPT -w -t nat -A MIHOMO_NAT -i "$IF" -p udp --dport 53 -j REDIRECT --to-ports 1053
  $IPT -w -t nat -A MIHOMO_NAT -i "$IF" -p tcp --dport 53 -j REDIRECT --to-ports 1053
  # 内网直连：私网目标不进代理。iptables legacy 不允许一条规则写多个 -d，
  # 所以每个网段单独加 RETURN，放在 per-MAC / 全局重定向之前。
  if [ "$DIRECT_PRIVATE" = "1" ]; then
    for D in 10.0.0.0/8 172.16.0.0/12 192.168.0.0/16 100.64.0.0/10 169.254.0.0/16; do
      $IPT -w -t nat -A MIHOMO_NAT -i "$IF" -s "$NET" -d "$D" -p tcp -j RETURN
    done
  fi
  if [ -f "$MACFILE" ]; then
    TAB=$(printf '\t')
    while IFS="$TAB" read -r MAC MODE P4 P6 IP4; do
      [ -z "$MAC" ] && continue
      case "$MODE" in
        direct)
          $IPT -w -t nat -A MIHOMO_NAT -i "$IF" -p tcp -m mac --mac-source "$MAC" -j RETURN
          $IPT -w -t nat -A MIHOMO_NAT -i "$IF" -p udp -m mac --mac-source "$MAC" --dport 53 -j RETURN
          $IPT -w -A MIHOMO_FWD -i "$IF" -m mac --mac-source "$MAC" -p udp --dport 443 -j RETURN
          ;;
        global|rule)
          case "$P4" in ''|*[!0-9]*) continue ;; esac
          $IPT -w -t nat -A MIHOMO_NAT -i "$IF" -p tcp -m mac --mac-source "$MAC" \
            -j REDIRECT --to-ports "$P4"
          if [ "$UDP_PROXY" = "1" ]; then
            case "$P6" in ''|*[!0-9]*) ;; *)
              if [ "$UDP_PROXY_ALL" = "1" ]; then
                $IPT -w -t mangle -A MIHOMO_TPROXY4 -i "$IF" -p udp -m mac --mac-source "$MAC" \
                  -j TPROXY --on-port "$P6" --tproxy-mark 0x1/0x1
              else
                $IPT -w -t mangle -A MIHOMO_TPROXY4 -i "$IF" -p udp -m mac --mac-source "$MAC" --dport 443 \
                  -j TPROXY --on-port "$P6" --tproxy-mark 0x1/0x1
              fi
              ;;
            esac
          fi
          ;;
      esac
    done < "$MACFILE"
  fi
  $IPT -w -t nat -A MIHOMO_NAT -i "$IF" -s "$NET" ! -d "$NET" -p tcp -j REDIRECT --to-ports 7892
  # QUIC 无法走 redir 代理；默认用 REJECT 让客户端立刻回落 TCP。
  # 开启 UDP/QUIC 代理后改为 TPROXY 到 7895，由内核按规则转发。
  if [ "$UDP_PROXY" = "1" ]; then
    if [ "$UDP_PROXY_ALL" = "1" ]; then
      $IPT -w -t mangle -A MIHOMO_TPROXY4 -i "$IF" -s "$NET" ! -d "$NET" -p udp \
        -j TPROXY --on-port 7895 --tproxy-mark 0x1/0x1
    else
      $IPT -w -t mangle -A MIHOMO_TPROXY4 -i "$IF" -s "$NET" ! -d "$NET" -p udp --dport 443 \
        -j TPROXY --on-port 7895 --tproxy-mark 0x1/0x1
    fi
  else
    $IPT -w -A MIHOMO_FWD -i "$IF" -s "$NET" ! -d "$NET" -p udp --dport 443 -j REJECT --reject-with icmp-port-unreachable
  fi
  $IPT -w -A MIHOMO_IN -i "$IF" -p tcp --dport 9090 -j DROP
  $IPT -w -A MIHOMO_IN -i "$IF" -p tcp --dport 7890 -j DROP
  if [ "$BLOCK_IPV6" = "1" ] && [ "$IPV6_GATEWAY" != "1" ]; then
    $IP6 -w -A MIHOMO6_FWD -i "$IF" -j DROP
  fi
done

# IPv4 UDP/QUIC TPROXY 的策略路由：只处理标记为 0x1 的 UDP 443
if [ "$UDP_PROXY" = "1" ]; then
  ip rule del fwmark 0x1/0x1 table 100 priority 9999 2>/dev/null
  ip rule add fwmark 0x1/0x1 table 100 priority 9999 2>/dev/null
  ip route replace local default dev lo table 100 2>/dev/null
else
  ip rule del fwmark 0x1/0x1 table 100 priority 9999 2>/dev/null
  ip route del local default dev lo table 100 2>/dev/null
fi

# 关键规则缺失时明确失败，避免「DNS 劫持成功但 TCP 没进代理」导致客户端断网
if [ "$HAS_IF" = "1" ]; then
  if ! $IPT -w -t nat -S MIHOMO_NAT 2>/dev/null | grep -q 'to-ports 7892'; then
    echo "apply: global redirect rule missing" >&2
    exit 1
  fi
  if [ "$UDP_PROXY" = "1" ] && ! $IPT -w -t mangle -S MIHOMO_TPROXY4 2>/dev/null | grep -q 'TPROXY'; then
    echo "apply: udp tproxy rule missing" >&2
    exit 1
  fi
fi

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
# IPv6 局域网网关：配置开启时在应用规则后启动入口。
# 已就绪时只同步每设备 TPROXY 规则（syncmac），
# 避免每次 apply 都撤销/重发 RA、重启 DNS relay 和 DHCPv6。
ipv6_ready() {
  pidof v6ra >/dev/null 2>&1 || return 1
  for IF in $(list_gateways); do
    ip -6 addr show dev "$IF" 2>/dev/null | grep -q "fd00:1234::1/64" || return 1
  done
  grep -q '^tproxy-port:' "$CFG" 2>/dev/null || return 1
  $IP6 -w -t mangle -C PREROUTING -j MIHOMO6_TPROXY >/dev/null 2>&1 || return 1
  return 0
}

if [ "$IPV6_GATEWAY" = "1" ] && [ -x "$BASE/ipv6.sh" ]; then
  # 运营商可能只下发 IPv6 地址而没有默认路由；此时继续开 ULA/TPROXY
  # 会让客户端优先走 IPv6 然后全部 network unreachable。
  if ip -6 route show default 2>/dev/null | grep -q '^default'; then
    if ipv6_ready; then
      sh "$BASE/ipv6.sh" syncmac >> "$LOG" 2>&1
    else
      sh "$BASE/ipv6.sh" enable >> "$LOG" 2>&1
    fi
  else
    sh "$BASE/ipv6.sh" stop >> "$LOG" 2>&1
    echo "$(date '+%F %T') ipv6 gateway skipped: no default route" >> "$LOG"
  fi
fi

# 日志裁剪 + 记录偏移：诊断页只统计“本次应用之后”的 DNS fallback 失败。
# 用 cat 覆盖同一 inode，保持 mihomo 以 O_APPEND 打开的文件描述符仍然有效。
if [ -f "$LOG" ]; then
  LINES=$(wc -l < "$LOG" 2>/dev/null || echo 0)
  if [ "$LINES" -gt 50000 ]; then
    tail -n 20000 "$LOG" > "$LOG.trim" 2>/dev/null && cat "$LOG.trim" > "$LOG" && rm -f "$LOG.trim"
    LINES=$(wc -l < "$LOG" 2>/dev/null || echo 0)
  fi
  echo "$LINES" > "$BASE/.log_offset" 2>/dev/null
fi
