# ADR 0064：后台 root 调用收敛

- 状态：已接受
- 日期：2026-11-17

## 问题

每次 `ShellExecutor.run` 都会启动新的 `su -c` 进程，Magisk/KernelSU 会为每次新 root 请求显示“已授予超级用户权限”。

代码中曾存在：

- 设备策略每 15 秒先执行 `coreRunning()`，即使没有需要延迟调度的策略
- 设备 IP 跟随每 60 秒读取邻居表、配置并可能重写防火墙
- 启动时 MainActivity 与 GatewayService 重复执行 `ensure`
- GatewayService 启动后立即做完整健康检查，产生多条 root 命令
- 设置页打开时自动执行健康检查

## 调整

- 设备策略先检查是否存在需要延迟调度的策略，再决定是否调用 root
- 没有 IPv6 网关时跳过周期 IP 跟随；有则间隔至少 5 分钟
- 去除 MainActivity 的重复 ensure
- 启动健康检查改为设置页手动触发
- AssetInstaller 标记检查合并为一次 shell
- 邻居表与 DHCP 租约合并为一次 shell
- GatewayService 正常启动只执行一次
- GatewayController.start 幂等，已运行时不重复启动

## 仍存在

- 每次 root 命令仍是独立 su 进程，因此启动、手动诊断、IPv6 开启后的周期同步仍可能弹授权提示
- 彻底消除需要持久化 root shell 或 root 守护进程