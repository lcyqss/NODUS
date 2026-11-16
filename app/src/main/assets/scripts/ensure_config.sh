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

grep -q '^external-ui:' "$CFG" || sed -i "/^external-controller:/a external-ui: ui"
grep -q 'listen: 0.0.0.0:1053' "$CFG" || sed -i "s/^\( *\)enable: true/\1enable: true\n\1listen: 0.0.0.0:1053/" "$CFG"
exit 0
