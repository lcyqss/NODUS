# ADR 0021：跨 Tab 节点定位

- 状态：已接受
- 日期：2026-11-16

## 背景

条件命中预览只能看节点名，排查时需要手动切到代理页再翻组。

## 决策

1. 新增 AppNavigator（进程内单例）：
   - tab StateFlow，AppNav 底部导航与 BackHandler 统一走它
   - pendingProxyNode：一次性跳转意图
2. 条件预览中的节点行可点击，写入 pendingProxyNode 并切到代理 Tab。
3. 代理页消费意图：
   - 等待 groups 加载后，找包含该节点的组
   - 排除 GLOBAL 与 __dev_ 组，优先 Selector 类型组
   - 选中该组，query 设为节点名，关闭搜索框（避免弹键盘）
   - 消费后清空意图
4. 找不到匹配组时仍设置过滤，显示空结果，不阻塞导航。

## 后果

- 从策略条件一键定位到具体节点。
- 导航状态从 rememberSaveable 改为进程内 StateFlow；进程重启回到首页。