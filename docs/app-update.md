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
v2.61.9  ↔  versionName = "2.61.9"
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
3. 打 tag：git tag v2.61.9
4. 推送 tag：git push origin v2.61.9
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