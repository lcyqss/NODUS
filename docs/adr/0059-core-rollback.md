# ADR 0059：内核上一版本与回退

- 状态：已接受
- 日期：2026-11-16

## 目标

核心更新可能影响 DNS、TPROXY、配置解析和稳定性。保留上一可用内核，失败时可恢复。

## 文件

- mihomo：当前内核
- mihomo.prev：上一可用内核
- .core_version：当前版本
- .core_prev_version：上一版本
- mihomo.failed：回退前的失败内核，便于排查

## 安装

- 新内核先执行 mihomo.new -v
- 再用 mihomo.new -t -d /data/adb/mihomo -f config.yaml 验证当前配置
- 当前版本与新版本不同时，才把当前内核保存为 mihomo.prev
- 同版本强制重装不覆盖已有的上一版本

## 自动回退

GatewayService 启动时如果刚安装了新内核且启动失败：

`	ext
安装新内核
→ 启动失败
→ 读取 .core_prev_version
→ 回退 mihomo.prev
→ 再次启动
`

## 手动回退

设置 → 运行组件：

- 显示“内核版本”
- 显示“上一内核”
- “回退上一内核”需要确认
- 回退时停止网关、替换内核、重启
- 回退成功后删除 mihomo.prev 和版本标记，避免重复回退

## 限制

- 只回退内核，不回退脚本/守护进程/Geo
- 脚本仍随 APK 更新
- 回退前用 -t 验证当前配置，配置无效时不会回退