# ADR 0058：运行组件更新

- 状态：已接受
- 日期：2026-11-16

## Phase 1

- App 启动时先执行 AssetInstaller.ensure()，再启动 GatewayService
- App 版本变化时强制替换 GeoIP.dat / GeoSite.dat
- 记录 .app_version 和 .geo_version
- mihomo 新二进制先执行 -v，失败不替换
- 替换前备份旧内核和 Geo
- “重新安装内核”变为强制重装，并重启网关
- 设置页显示内核版本、Geo 版本

## Phase 2

- 设置页增加“更新 Geo 数据”
- 从 MetaCubeX/meta-rules-dat 官方 release 下载 GeoIP/GeoSite
- 下载到缓存，大小校验
- 备份旧文件，原子替换
- 更新期间停止/重启网关
- 失败恢复旧 Geo

## 暂不做

- mihomo 内核远程自动更新
- root 脚本/守护进程远程更新
- Geo 自动更新（后续可选，默认关闭）
## Phase 5：更新记录

- AppSettings 保存上次检查时间、结果、版本和错误
- 手动/自动更新都写入记录
- 设置页显示“上次 Geo 更新”
- 点击查看上次检查时间、结果、错误
- ETag 未变化时不重启内核，并记录为“成功 · 已是最新”