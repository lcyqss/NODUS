#!/system/bin/sh
# 确保网关必需字段存在且取值正确：订阅配置可能把 allow-lan / bind-address 等
# 改成只监听本机的值，导致热点客户端重定向到 7892 失败（连上热点但没网）。
CFG=/data/adb/mihomo/config.yaml
SECRET_VALUE='9062b7e6cee1d259'
[ -f "$CFG" ] || exit 0

set_key() {
  key="$1"; value="$2"
  if grep -q "^$key:" "$CFG"; then
    sed -i "s|^$key:.*|$key: $value|" "$CFG"
  else
    sed -i "1i $key: $value" "$CFG"
  fi
}

set_key external-controller "'127.0.0.1:9090'"
set_key secret "'$SECRET_VALUE'"
set_key redir-port "7892"
set_key mixed-port "7890"
set_key allow-lan "true"
set_key bind-address "'*'"

grep -q '^external-ui:' "$CFG" || sed -i "/^external-controller:/a external-ui: ui" "$CFG"
# dns 下可能已有 listen（IPv6 网关开启时是 [::]:1053），不能再插一条
grep -qE '^ *listen:' "$CFG" || sed -i "s/^\( *\)enable: true/\1enable: true\n\1listen: 0.0.0.0:1053/" "$CFG"
# 修复历史重复：同时存在 [::] 和 0.0.0.0 时删掉多余的 IPv4 行
if grep -q 'listen: "\[::\]:1053"' "$CFG"; then
  sed -i '/^ *listen: 0\.0\.0\.0:1053$/d' "$CFG"
fi

# fake-ip TTL：默认 1 秒会让客户端频繁重新解析，调大到 60 秒减少 DNS 抖动
grep -q 'fake-ip-ttl:' "$CFG" || sed -i '/^dns:/a\    fake-ip-ttl: 60' "$CFG"

# fallback 改 DoH：UDP 53 经代理容易被节点/UDP 限制拖慢，TLS 443 更稳定
if grep -q '^ *fallback:' "$CFG"; then
  sed -i 's|^\( *\)fallback:.*|\1fallback: ["https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query"]|' "$CFG"
fi
exit 0
