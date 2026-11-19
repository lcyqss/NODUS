# 应用内更新与 GitHub Release

## 仓库

```text
https://github.com/lcyqss/NODUS
```

更新检查读取：

```text
https://api.github.com/repos/lcyqss/NODUS/releases/latest
```

要求：

- 仓库和 Release 对匿名访问可见
- 最新 Release 不是 prerelease
- Release 中至少有一个 `.apk` 资产

## 发布资产

GitHub Actions 会生成：

```text
NODUS.apk
NODUS.apk.sha256
```

Tag 必须和 `versionName` 一致：

```text
v2.63.2  ↔  versionName = "2.63.2"
```

每次发布前递增 `versionCode`。

## GitHub Actions Secrets

在仓库 Settings → Secrets and variables → Actions 中添加：

| Secret | 内容 |
|---|---|
| `KEYSTORE_BASE64` | `release.jks` 的 Base64 |
| `KEYSTORE_STORE_PASSWORD` | keystore 密码 |
| `KEYSTORE_KEY_ALIAS` | key alias |
| `KEYSTORE_KEY_PASSWORD` | key 密码 |

生成 Base64（PowerShell）：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore/release.jks"))
```

不要把 `release.jks` 或 `keystore.properties` 提交到仓库。

## 发布流程

```text
1. 修改 versionCode / versionName
2. 提交代码
3. 打 tag：git tag v2.63.2
4. 推送 tag：git push origin v2.63.2
5. GitHub Actions 构建并创建 Release
```

## 应用行为

- 应用启动后每天最多自动检查一次
- 有新版本时首页显示提示条
- 用户进入“设置 → 关于 → 检查应用更新”手动下载
- 下载后校验包名、版本号和签名，再调起系统安装器
- 不静默安装，不自动下载

## 已知限制

- 私有仓库需要 Token，当前实现面向公开仓库
- GitHub API 可能被限流或网络不可达
- 更新必须使用与当前安装包相同的签名密钥，否则 Android 会拒绝安装

## 发布验证记录

v2.63.2（2026-11-19）已验证：

- Actions 产物 `NODUS.apk` 49,276,140 字节，包含 mihomo 内核
- `sha256`：`a0c1f8d2…ab5b`，与 Release 附带的 `.sha256` 一致
- CI 产物签名证书 SHA-256 与本机 keystore 一致（`0ab12892…296a`）
- CI 产物可直接覆盖安装本地构建（同 versionCode 210 / 同签名）

v2.63.1（2026-11-18）已验证：

- Actions 产物 `NODUS.apk` 约 46.9 MB，包含 mihomo 内核
- CI 产物与本机 keystore 的签名证书 SHA-256 一致（`0ab12892…296a`）
- CI 产物可直接覆盖安装本地构建（同 versionCode / 同签名）
- 应用内「检查应用更新」能读到该 Release，并显示「当前已是最新版本」