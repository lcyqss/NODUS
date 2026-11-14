#!/system/bin/sh
BASE=/data/adb/mihomo
CFG=$BASE/config.yaml
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

# ---- IPv6 可用性判定 ------------------------------------------------------
# 先要求有全局地址（排除 ULA fc00::/7），再做真实探测：
# 多个 ICMP 目标任一可达即可；ICMP 被策略拦截时用 HTTPS(强制 v6) 兜底。
# 不用 `ip -6 route show default` 作为必要条件：Android 的策略路由可能把默认路由
# 放在独立 table 里，主表看不到，但 IPv6 实际可用。
has_v6() {
  ip -6 addr show scope global 2>/dev/null | awk '/inet6 /{print $2}' | grep -vqE '^f[cd]' || return 1
  for t in 2400:3200::1 2001:4860:4860::8888 2606:4700:4700::1111; do
    ping6 -c 1 -W 1 "$t" >/dev/null 2>&1 && return 0
  done
  if command -v curl >/dev/null 2>&1; then
    curl -6 -s -o /dev/null --max-time 3 'https://www.cloudflare.com/cdn-cgi/trace' && return 0
  fi
  return 1
}

# ---- 内核重载 -------------------------------------------------------------
core_secret() {
  grep -m1 '^secret:' "$CFG" 2>/dev/null | sed "s/^secret:[[:space:]]*//; s/[\"']//g"
}

# 通过 API 热重载配置：不杀进程，连接与 UI 状态不中断（成功返回 0）。
reload_core() {
  command -v curl >/dev/null 2>&1 || return 1
  local secret code
  secret=$(core_secret)
  [ -n "$secret" ] || return 1
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 \
    -X PUT \
    -H "Authorization: Bearer $secret" \
    -H 'Content-Type: application/json' \
    -d "{\"path\":\"$CFG\"}" \
    "http://127.0.0.1:9090/configs?force=true")
  [ "$code" = "204" ] || [ "$code" = "200" ]
}

restart_core() {
  killall mihomo 2>/dev/null
  sleep 2
  (setsid "$BASE/mihomo" -d "$BASE" -f "$CFG" </dev/null >>"$BASE/mihomo.log" 2>&1 &)
  sleep 1
}
