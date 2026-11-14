#!/system/bin/sh
BASE=/data/adb/mihomo
. "$BASE/common.sh"
CFG=$BASE/config.yaml

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

LABEL="$IP_MODE${VAL:+ ($VAL)}"

# 内核没运行就只落盘，等下次启动生效；运行中优先热重载，失败才重启
if pidof mihomo >/dev/null 2>&1; then
  if reload_core; then
    echo "模式: $LABEL 已热重载"
  else
    echo "模式: $LABEL 热重载失败，重启内核"
    restart_core
  fi
else
  echo "模式: $LABEL 已写入配置（内核未运行）"
fi

ls -t "$CFG".bak.* 2>/dev/null | tail -n +6 | xargs -r rm -f 2>/dev/null
