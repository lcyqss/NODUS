#!/system/bin/sh
BASE=/data/adb/mihomo
while true; do
  SIG=$(ip -4 addr show 2>/dev/null | awk '/^[0-9]+:/{ifc=$2} /inet /{print ifc $2}' | tr '\n' ' ')
  if [ "$SIG" != "$(cat "$BASE/.netsig" 2>/dev/null)" ]; then
    echo "$SIG" > "$BASE/.netsig"
    sh "$BASE/apply.sh"
  fi
  NOW=$(date +%s)
  LAST=$(cat "$BASE/.v6ensure" 2>/dev/null || echo 0)
  if [ $((NOW - LAST)) -ge 30 ] && [ -f "$BASE/ipv6.sh" ]; then
    echo "$NOW" > "$BASE/.v6ensure"
    sh "$BASE/ipv6.sh" ensure >> "$BASE/ipv6.log" 2>&1
  fi
  sleep 10
done