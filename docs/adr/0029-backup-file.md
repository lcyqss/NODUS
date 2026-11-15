# ADR 0029：备份文件导入导出

- 状态：已接受
- 日期：2026-11-16

## 决策

1. 备份对话框增加：
   - 导出文件：CreateDocument("application/json")，默认名 mihomo-gateway-backup.json
   - 从文件导入：OpenDocument，读取 JSON 到导入框，再选择合并/覆盖
2. 使用系统文件选择器（SAF），不申请存储权限。
3. 文件操作失败在对话框内提示；导出/导入结果仍用 backupMessage 常驻。

## 后果

- 备份可离开剪贴板，作为文件保存/传输。
- 导入仍然需要用户确认合并或覆盖。