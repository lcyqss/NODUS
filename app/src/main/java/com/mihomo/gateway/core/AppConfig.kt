package com.mihomo.gateway.core

/**
 * 全局常量：路径、端口、仓库地址只在这里定义一次。
 *
 * 这些值同时被 Kotlin 代码和 /data/adb/mihomo 下的 shell 脚本使用，
 * 修改时必须同步 `app/src/main/assets/` 里的脚本，否则会出现
 * 「Kotlin 连的是新端口、脚本写的是旧端口」这类不一致。
 */
object AppConfig {

    /** 运行数据目录：内核、脚本、配置、Geo 数据。 */
    const val BASE_DIR = "/data/adb/mihomo"

    /** mihomo 外部控制 API（REST / WebSocket）。 */
    const val API_PORT = 9090

    /** 混合代理端口；应用内下载更新时使用。 */
    const val PROXY_PORT = 7890

    /** 网关内 DNS 监听端口。 */
    const val DNS_PORT = 1053

    /** 本机回环地址（API / 代理只监听本机）。 */
    const val LOOPBACK = "127.0.0.1"

    /** API 鉴权密钥，需与 config.yaml 中的 external-controller secret 一致。 */
    const val SECRET = "9062b7e6cee1d259"

    /** 应用包名：安装校验、FileProvider 使用。 */
    const val PACKAGE_NAME = "com.mihomo.gateway"

    /** 每设备 redir 监听端口的起始值（第 i 台设备为 base + 2i）。 */
    const val DEVICE_REDIR_PORT_BASE = 7910

    /** 每设备 tproxy 监听端口比 redir 大 1。 */
    const val DEVICE_TPROXY_OFFSET = 1

    /** GitHub Release 仓库，应用内更新检查使用。 */
    const val GITHUB_OWNER = "lcyqss"
    const val GITHUB_REPO = "NODUS"

    /** 最新 Release API。 */
    const val RELEASES_API =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
}
