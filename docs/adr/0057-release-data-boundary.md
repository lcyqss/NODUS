# ADR 0057：Release 数据边界

- 状态：已接受
- 日期：2026-11-16

## 原则

Release APK 只包含：

- 代码、脚本、mihomo、GeoIP/GeoSite
- 中性 config.template.yaml
- UI 默认值
- 地区预设标签（首次启动种入）

Release APK 不包含：

- 用户订阅 URL / 订阅名称
- 主力手机等设备策略
- 用户 MAC / IP
- 自定义标签和地区标签修改
- 条件模板
- 流量统计、日志、备份
- 测试热点名、测试账号、测试订阅

## 存放位置

用户数据只在设备私有存储：

- SharedPreferences gateway_settings
- /data/data/com.mihomo.gateway/files/profiles
- /data/adb/mihomo/*.yaml、mac-policy.tsv、direct-dns.txt 等运行时文件

不会写回 APK 或 ssets。

## 模板

config.template.yaml 已移除示例 proxy-providers 和假订阅 URL；未导入订阅时只有 DIRECT 规则。

## APK 审计

Release APK 中未发现：

- 订阅 URL
- 设备 MAC/IP
- 设备策略
- 测试热点名
- 测试账号

GeoSite.dat 中的 airport 等字符串属于公开域名库，不是用户数据。

## 发布前检查

1. 全新安装启动，确认只有地区预设标签
2. 确认没有订阅、设备策略、条件模板
3. 确认 config.template.yaml 不含真实 URL
4. 确认 APK 内无测试 MAC/IP/订阅名
5. 确认备份导出只在使用者主动操作时发生