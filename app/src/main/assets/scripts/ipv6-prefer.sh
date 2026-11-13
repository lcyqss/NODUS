#!/system/bin/sh
# 为所有节点加上 IPv6 优先（ip-version: ipv6-prefer），可重复执行
BASE=/data/adb/mihomo
CFG=$BASE/config.yaml
[ -f "$CFG" ] || exit 0
cp -f "$CFG" "$CFG.bak.$(date +%s)"
sed -i 's/ip-version: ipv6-prefer, //g' "$CFG"
sed -i 's/\(port: [0-9][0-9]*, \)/\1ip-version: ipv6-prefer, /g' "$CFG"
echo "已应用 IPv6 优先: $(grep -c 'ip-version: ipv6-prefer' "$CFG") 个节点"
killall mihomo 2>/dev/null
sleep 2
sh "$BASE/start.sh"