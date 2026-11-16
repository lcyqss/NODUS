# ADR 0062：上游校验与深度健康检查

- 状态：已接受
- 日期：2026-11-16

## Geo SHA256

- 同时下载 geoip.dat.sha256sum / geosite.dat.sha256sum
- 解析官方 SHA256
- 下载完成后计算本地 SHA256
- 不一致时拒绝替换
- 官方未提供或下载失败时拒绝更新
- .geo_sha256 保存官方校验值

## 内核 SHA256

- 通过 GitHub release API 读取目标资源的 digest
- 下载 .gz 后校验压缩包 SHA256
- 官方未提供 digest 时拒绝安装
- 仍保留 -v / -t 校验和上一内核回退

## 深度健康检查

- DNS 1053 A 查询
- 端口 7890 / 7892 / 9090（IPv6 时 7895）
- 代理 204
- IPv4 NAT / IPv6 TPROXY 链
- IPv6 ULA + v6dns + AAAA
- CN / 国际域名 DNS 查询
- 规则表加载
- 界面明确标注为组件级检查，不代表端到端流量验证

## 限制

- 没有官方签名时只能做 SHA256
- 内核 digest 依赖 GitHub API 可用性，API 限流时会拒绝安装
- TPROXY 只检查链/监听，不构造真实客户端流量