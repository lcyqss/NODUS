# ADR 0060：远程内核检查与安装

- 状态：已接受
- 日期：2026-11-16

## 目标

允许高级用户手动检查 mihomo 官方 Android arm64 版本，但保持手动确认和失败回退。

## 流程

1. 通过本机 7890（网关运行时）访问 GitHub latest release
2. 解析 release tag
3. 读取 expanded assets，匹配 ndroid-arm64-v8*.gz
4. 比较当前 .core_version
5. 下载 gzip 到缓存并解压
6. 停止网关
7. CoreInstaller.installBinary：
   - -v
   - -t -d /data/adb/mihomo -f config.yaml
   - 版本不同则保留上一内核
   - 替换
8. 启动网关
9. 启动失败则回退上一内核

## 限制

- 只更新 mihomo 内核，不更新脚本/守护进程/Geo
- 不做自动内核更新
- 依赖 GitHub release 页面结构
- 没有上游签名/SHA256 强校验