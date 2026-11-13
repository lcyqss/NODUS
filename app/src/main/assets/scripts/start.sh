#!/system/bin/sh
BASE=/data/adb/mihomo
[ -f "$BASE/ensure_config.sh" ] && sh "$BASE/ensure_config.sh"
sh "$BASE/apply.sh"

pkill -f "$BASE/watchdog.sh" 2>/dev/null
sleep 1
(setsid sh "$BASE/watchdog.sh" </dev/null >/dev/null 2>&1 &)
sleep 1
pgrep -f "$BASE/watchdog.sh" 2>/dev/null | head -1 > "$BASE/watchdog.pid"
echo "$(date '+%F %T') gateway started" >> "$BASE/mihomo.log"