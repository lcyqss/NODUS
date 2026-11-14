# 待办与暂缓事项（Backlog）

> 记录已明确但暂不实施的需求，避免口头约定丢失。完成一项就移出或标注日期。

## 1. 拓扑样式图形化（暂缓，待排期）

**需求**：把「连接 → 拓扑」从当前的**表格 / 缩进树**改成 MetaCubeXD 的**节点-连线图形**风格。

**参考实现**：MetaCubeXD `packages/ui/components/NetworkTopology.vue`

- d3 + SVG 绘制
- 树结构：`root → 代理组(chains[1]) → 节点(chains[0]) → 规则(rule + rulePayload) → 客户端(sourceIP:sourcePort)`
- 节点按类型着色（root/client/port/rule/group/proxy）
- 节点可折叠，支持暂停冻结
- 连接数/流量标注在节点或连线上

**我们现状**：

- 数据聚合已完成（`ConnectionsScreen.aggregate` / `buildTree`）
- 展示为表格与缩进树
- 设备标签已支持 主机名 / MAC / IP（`NeighborResolver`）

**实施要点**：

| 项 | 方案 |
|---|---|
| 渲染 | Compose `Canvas`，不用 WebView / d3 |
| 布局 | 分层树布局（按深度计算 x，按兄弟序号计算 y） |
| 连线 | 三次贝塞尔曲线，颜色区分直连/代理 |
| 折叠 | 复用现有 `collapsed` 状态，节点点击切换 |
| 缩放/平移 | `detectTransformGestures` + 画布变换矩阵 |
| 数据 | 完全复用 `/connections`，无需新接口 |
| 工作量 | 约 1 天 |

**暂缓原因**：功能优先；图形化属于视觉增强，不影响数据正确性。

---

## 2. 其他暂缓项

| 项 | 说明 | 状态 |
|---|---|---|
| 设备历史持久化 | 写入 SharedPreferences，重启后保留“曾出现过的设备” | 暂缓 |
| 设备别名 | 手动给 MAC 命名（如「客厅电视」） | 暂缓 |
| 规则集 provider 状态 | 当前订阅为内联 proxies，`/providers/rules` 为空 | 暂缓 |
| Provider 手动更新 | `PUT /providers/proxies/{name}` 语义待确认 | 暂缓 |

---

## 3. 工程收尾（未开始）

| 项 | 说明 |
|---|---|
| Release 签名包 | 压缩内核，预计 45MB 左右；需生成 keystore |
| 移除 Magisk 模块 | APK 已具备开机自启，模块可卸载避免重复启动 |
| 卸载清理 | 停服务 → 清 iptables → 删除 `/data/adb/mihomo` |
| 完整回归 | 热点开关、客户端上网、切节点、切模式、重启自恢复 |
