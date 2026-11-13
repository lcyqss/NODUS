# ADR 0002：原生 UI，不引入 WebView 面板

- 状态：已接受
- 日期：2026-11-14

## 背景

早期为快速验证，App 内嵌了 MetaCubeXD 静态面板（WebView + `external-ui`），
并为此增加了 9091 桥接服务、CORS、token 注入等机制。

## 决策

移除 WebView 面板，改为 **Jetpack Compose 原生多页 UI**，直接调用 mihomo API。

## 理由

1. 原生体验：无 WebView 白屏 / 掉帧 / 手势冲突
2. 去掉桥接层：少一个 HTTP 服务、少一层 CORS/token 逻辑
3. 包体减小：移除约 2.5MB 面板资源
4. 安全：不再暴露 `9090/ui` 页面
5. 控制力：网关操作与代理管理在同一导航内

## 后果

- 需要自行实现代理页（分组树、测速、切换）等，工作量增加
- 不再依赖 metacubexd 上游
- 参考 CMFA 的 UI 结构与交互，但不复制其架构
