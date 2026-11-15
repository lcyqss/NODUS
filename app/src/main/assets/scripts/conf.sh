#!/system/bin/sh
# 写入/更新 gateway.conf 的单个键值
# 用法: conf.sh KEY VALUE
CONF=/data/adb/mihomo/gateway.conf
[ -f "$CONF" ] || : > "$CONF"
if grep -q "^$1=" "$CONF" 2>/dev/null; then
  sed -i "s|^$1=.*|$1=$2|" "$CONF"
else
  printf '\n%s=%s\n' "$1" "$2" >> "$CONF"
fi
grep "^$1=" "$CONF"
