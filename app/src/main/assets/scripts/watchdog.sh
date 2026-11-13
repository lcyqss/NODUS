#!/system/bin/sh
BASE=/data/adb/mihomo
while true; do
  SIG=$(ip -4 addr show 2>/dev/null | awk '/^[0-9]+:/{ifc=$2} /inet /{print ifc $2}' | tr '\n' ' ')
  if [ "$SIG" != "$(cat "$BASE/.netsig" 2>/dev/null)" ]; then
    echo "$SIG" > "$BASE/.netsig"
    sh "$BASE/apply.sh"
  fi
  sleep 10
done