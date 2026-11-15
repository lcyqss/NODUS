# ADR 0037：客户端 RS 单播 RA 与 DNS 配置去重

- 状态：已接受
- 日期：2026-11-16

## 背景

iPad/Android 只拿到 fe80，未配置 ULA；Windows 可收到多播 RA。
抓包确认 RA 本身合法（checksum/prefix/flags 正常），问题在于部分 Wi-Fi 客户端不处理 AP 的多播 RA。

## 决策

1. 6ra 增加 Router Solicitation 监听：
   - 收到 RS 后，向客户端 link-local 单播 RA
   - 多播 RA 仍保留，周期改为 5 秒
2. DNS 配置写入改为 awk 归一化：
   - 无论重复 enable/disable，只保留一条 dns.listen
   - 修复重复 listen 导致 mihomo mapping key already defined 启动失败
3. 关闭时恢复原始 listen 行；保留 RA lifetime=0 撤回。

## 后果

- iPad/Android 连接后通过 RS 立即收到 ULA，不再依赖多播。
- 重复切换 IPv6 代理不会破坏 mihomo 配置。