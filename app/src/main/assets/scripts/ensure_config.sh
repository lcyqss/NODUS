#!/system/bin/sh
# 确保配置里包含网关必需的字段（订阅导入后可能缺失）
CFG=/data/adb/mihomo/config.yaml
SECRET_VALUE='9062b7e6cee1d259'
[ -f "$CFG" ] || exit 0

grep -q '^external-controller:' "$CFG" || sed -i "1i external-controller: '127.0.0.1:9090'" "$CFG"
grep -q '^external-ui:' "$CFG" || sed -i "/^external-controller:/a external-ui: ui" "$CFG"
if grep -q '^secret:' "$CFG"; then
  sed -i "s|^secret:.*|secret: '$SECRET_VALUE'|" "$CFG"
else
  sed -i "/^external-controller:/a secret: '$SECRET_VALUE'" "$CFG"
fi
grep -q '^redir-port:' "$CFG" || sed -i "/^external-controller:/a redir-port: 7892" "$CFG"
grep -q '^allow-lan:' "$CFG" || sed -i "1i allow-lan: true" "$CFG"
grep -q '^bind-address:' "$CFG" || sed -i "1i bind-address: '0.0.0.0'" "$CFG"
grep -q 'listen: 0.0.0.0:1053' "$CFG" || sed -i "s/^\( *\)enable: true/\1enable: true\n\1listen: 0.0.0.0:1053/" "$CFG"
exit 0