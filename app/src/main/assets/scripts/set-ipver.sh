#!/system/bin/sh
BASE=/data/adb/mihomo
CFG=$BASE/config.yaml
CONF=$BASE/gateway.conf
[ -f "$BASE/ensure_config.sh" ] && sh "$BASE/ensure_config.sh"

has_v6() {
  ping6 -c 1 -W 2 2400:3200::1 >/dev/null 2>&1
}

case "$1" in
  auto)
    IP_MODE=auto
    if has_v6; then VAL=ipv6-prefer; else VAL=ipv4-prefer; fi ;;
  dual) IP_MODE=dual; VAL="" ;;
  v6)   IP_MODE=v6;   VAL=ipv6-prefer ;;
  v4)   IP_MODE=v4;   VAL=ipv4-prefer ;;
  v6-only) IP_MODE=v6-only; VAL=ipv6 ;;
  v4-only) IP_MODE=v4-only; VAL=ipv4 ;;
  *) echo "用法: $0 auto|dual|v6|v4|v6-only|v4-only"; exit 1 ;;
esac

[ -f "$CFG" ] || { echo "找不到配置"; exit 1; }
cp -f "$CFG" "$CFG.bak.$(date +%s)"
sed -i 's/ip-version: [a-z0-9-]*, //g' "$CFG"
if [ -n "$VAL" ]; then
  sed -i "s/\(port: [0-9][0-9]*, \)/\1ip-version: $VAL, /g" "$CFG"
fi

if [ -f "$CONF" ]; then
  if grep -q '^IP_MODE=' "$CONF"; then sed -i "s/^IP_MODE=.*/IP_MODE=$IP_MODE/" "$CONF"; else echo "IP_MODE=$IP_MODE" >> "$CONF"; fi
fi

if [ -n "$VAL" ]; then
  echo "模式: $IP_MODE ($VAL)  节点数: $(grep -c "ip-version: $VAL" "$CFG")"
else
  echo "模式: dual（mihomo 默认）"
fi

ls -t "$CFG".bak.* 2>/dev/null | tail -n +6 | xargs -r rm -f 2>/dev/null

killall mihomo 2>/dev/null
sleep 2
(setsid "$BASE/mihomo" -d "$BASE" -f "$CFG" </dev/null >>"$BASE/mihomo.log" 2>&1 &)
sleep 1