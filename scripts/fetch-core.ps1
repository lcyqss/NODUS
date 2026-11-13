# 下载 mihomo 内核二进制到 app assets（不提交到 git）
param(
    [string]$Version = "v1.19.34"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$dest = Join-Path $root "app\src\main\assets\mihomo"

if (Test-Path $dest) {
    Write-Host "已存在，跳过: $dest"
    exit 0
}

$asset = "mihomo-android-arm64-v8-$Version.gz"
$urls = @(
    "https://github.com/MetaCubeX/mihomo/releases/download/$Version/$asset",
    "https://ghproxy.net/https://github.com/MetaCubeX/mihomo/releases/download/$Version/$asset"
)

$tmp = Join-Path $env:TEMP "mihomo-$Version.gz"
$ok = $false
foreach ($u in $urls) {
    try {
        Write-Host "下载: $u"
        & curl.exe --ssl-no-revoke -sSL --retry 3 -m 900 -o $tmp $u
        if ((Test-Path $tmp) -and ((Get-Item $tmp).Length -gt 10MB)) {
            $ok = $true
            break
        }
    } catch {
        Write-Warning "失败: $($_.Exception.Message)"
    }
}

if (-not $ok) {
    throw "内核下载失败，请检查网络或手动放置到 $dest"
}

$in = [System.IO.File]::OpenRead($tmp)
$gz = New-Object System.IO.Compression.GZipStream($in, [System.IO.Compression.CompressionMode]::Decompress)
$out = [System.IO.File]::Create($dest)
$gz.CopyTo($out)
$out.Close(); $gz.Close(); $in.Close()
Remove-Item $tmp -Force

Write-Host ("完成: {0} ({1:N1} MB)" -f $dest, ((Get-Item $dest).Length / 1MB))
