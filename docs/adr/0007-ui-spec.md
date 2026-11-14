# ADR 0007：UI 规范（单一开关 / 状态恢复 / 图标体系）

- 状态：已接受
- 日期：2026-11-14
- 参考：Clash for Android / CMFA、v2rayNG、MetaCubeXD

## 背景

v1.1.0 完成后，页面视觉仍偏"工具表单"，与成熟客户端差距明显：

- 启停是两个并列按钮，不符合"连接开关"的通用心智
- 底部导航用文字符号（◎ ⇄ ⇅ ≡ ⚙），风格不统一
- App 每次启动都强制拉起网关，用户主动"停止"后会被再次启动

## 决策

### 1. 单一电源开关（概览页）

- 位置：概览页顶部状态卡内，大圆形按钮（CMFA 风格）
- 语义：**开关只控制网关（mihomo + 规则），不控制前台服务**
- 状态机：`Stopped → Starting → Running → Stopping → Error`
- 移除原有的「启动 / 停止」两个按钮，刷新改为标题栏图标

### 2. 恢复上次运行状态

- `SettingsStore` 新增 `desiredRunning`
- App/服务启动时：`desiredRunning == true` 才拉起网关，否则保持停止
- 开机自启仍是独立开关（`autoStart`），两者关系：
  - `autoStart` 决定开机是否启动服务
  - `desiredRunning` 决定服务启动后是否拉起网关
- 首次安装默认 `desiredRunning = true`，保证开箱可用

### 3. 图标：Material 风格（填充），但手绘实现

- 不引入 `androidx.compose.material:material-icons-*` 依赖（构建网络不稳定、包体大）
- 在 `ui/icons/GatewayIcons.kt` 用 Compose Canvas 绘制 12 个图标
- 使用 `LocalContentColor` 着色，自动适配深色模式
- 图标语义参考 Material Symbols，视觉为填充/粗线风格

### 4. 导航结构保持 5 个 Tab

`概览 / 代理 / 连接 / 日志 / 设置`，不采用 MetaCubeXD 的 10+ 页结构。

### 5. 视觉参考但不复制代码

- MetaCubeXD：布局层级、信息密度（MIT，可参考）
- CMFA / v2rayNG：开关交互、列表样式
- 不复制任何 Vue 组件代码；仅借鉴设计

## 后果

- 状态语义更清晰，但也意味着"服务在跑、网关停止"是合法状态，诊断页需能区分
- 首次安装默认启动，用户停止后需手动再开（符合"恢复上次状态"）
- 手绘图标维护成本低，但视觉一致性需人工保证
- 后续若引入 Material Symbols 字体或矢量资源，可平滑替换 `GatewayIcons`
