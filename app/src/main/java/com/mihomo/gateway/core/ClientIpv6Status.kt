package com.mihomo.gateway.core

/**
 * 普通设备（未启用设备策略）的 IPv6 出口状态。
 *
 * 只描述全局默认路径：
 * - 手机上游是否有 IPv6
 * - 热点是否通过 Android 原生共享（tethering）下发公网前缀
 * - App 是否启用了 ULA + RA + TPROXY 的 IPv6 代理
 * - 是否开启了 IPv6 拦截
 *
 * 启用设备策略的设备使用各自的 listener 与 SRC-IP 规则，
 * 不在本状态范围内，仅通过 [policyCount] 提示存在例外。
 */
data class ClientIpv6Status(
    /** 手机自身有全局 IPv6（rmnet 等上游接口）。 */
    val upstream: Boolean,
    /** 热点接口拿到非 ULA 的全局 IPv6，说明 Android 原生共享已生效。 */
    val nativeTethering: Boolean,
    /** App 的 ULA 地址在热点接口上。 */
    val ula: Boolean,
    /** v6ra / v6dns 等 IPv6 网关进程在运行。 */
    val raRunning: Boolean,
    /** gateway.conf 中 IPV6_GATEWAY=1。 */
    val proxyConfigured: Boolean,
    /** gateway.conf 中 BLOCK_IPV6=1。 */
    val blocked: Boolean,
    /** 已启用的设备策略数量（这些设备不适用本状态）。 */
    val policyCount: Int
) {

    enum class Mode { PROXY, NATIVE, BLOCKED, NONE }

    /** 代理真正生效需要配置打开且 ULA/RA 在运行。 */
    val proxyActive: Boolean get() = proxyConfigured && (ula || raRunning)

    val mode: Mode
        get() = when {
            proxyActive -> Mode.PROXY
            nativeTethering -> Mode.NATIVE
            blocked -> Mode.BLOCKED
            else -> Mode.NONE
        }

    val title: String
        get() = when (mode) {
            Mode.PROXY -> "经代理（ULA + TPROXY）"
            Mode.NATIVE -> "原生共享（运营商前缀）"
            Mode.BLOCKED -> "已拦截（客户端 IPv6 被丢弃）"
            Mode.NONE -> "无可用 IPv6"
        }

    /** 给诊断页和分享报告用的多行说明。 */
    val detail: String
        get() = buildString {
            appendLine("上游 IPv6：${if (upstream) "有" else "无"}")
            appendLine("热点原生共享：${if (nativeTethering) "可用" else "不可用"}")
            appendLine("ULA 代理：${if (proxyActive) "开启" else "关闭"}")
            appendLine("IPv6 拦截：${if (blocked) "开启" else "关闭"}")
            append("普通设备结论：$title")
            if (policyCount > 0) {
                appendLine()
                append("另有 $policyCount 条设备策略启用，策略设备按各自 listener / SRC-IP 规则处理，不在本状态内")
            }
        }

    companion object {

        private const val ULA_PREFIX = "fd00:1234::"

        suspend fun detect(shell: ShellExecutor, policyCount: Int): ClientIpv6Status =
            parse(shell.run(DETECT_COMMAND, 8_000).stdout, policyCount)

        /**
         * 解析 detect 命令的输出。与 shell 解耦，便于单元测试。
         *
         * 输出格式：
         * ```text
         * IF=wlan2
         *     inet6 fe80::... scope link
         *     inet6 fd00:1234::1/64 scope global
         * |CONF|
         * BLOCK_IPV6=0
         * IPV6_GATEWAY=0
         * |RA|
         * 1
         * |UP|
         *     inet6 2408:... scope global
         * ```
         */
        fun parse(raw: String, policyCount: Int = 0): ClientIpv6Status {
            val sections = raw.split("|CONF|")
            val gatewayPart = sections.getOrElse(0) { "" }
            val rest = sections.getOrElse(1) { "" }
            val raSplit = rest.split("|RA|")
            val confPart = raSplit.getOrElse(0) { "" }
            val afterRa = raSplit.getOrElse(1) { "" }
            val upSplit = afterRa.split("|UP|")
            val raPart = upSplit.getOrElse(0) { "" }
            val upstreamPart = upSplit.getOrElse(1) { "" }

            val gatewayAddrs = gatewayPart.lineSequence()
                .filter { it.trim().startsWith("inet6 ") }
                .map { it.trim() }
                .toList()

            val ula = gatewayAddrs.any { it.contains("$ULA_PREFIX") }
            val nativeTethering = gatewayAddrs.any { line ->
                line.contains("scope global") && !line.contains(ULA_PREFIX)
            }

            val proxyConfigured = confPart.lineSequence()
                .any { it.trim() == "IPV6_GATEWAY=1" }
            val blocked = confPart.lineSequence()
                .any { it.trim() == "BLOCK_IPV6=1" }
            val raRunning = raPart.lineSequence().any { it.trim() == "1" }

            val upstream = upstreamPart.lineSequence()
                .any { it.trim().startsWith("inet6 ") }

            return ClientIpv6Status(
                upstream = upstream,
                nativeTethering = nativeTethering,
                ula = ula,
                raRunning = raRunning,
                proxyConfigured = proxyConfigured,
                blocked = blocked,
                policyCount = policyCount
            )
        }

        /**
         * 复用 App 自己的热点接口判定（common.sh 的 list_gateways），
         * 避免在 Kotlin 里重复一套网卡名规则。
         */
        private val DETECT_COMMAND = """
            . /data/adb/mihomo/common.sh >/dev/null 2>&1
            for IF in ${'$'}(list_gateways); do
              echo "IF=${'$'}IF"
              ip -6 addr show dev "${'$'}IF" 2>/dev/null
            done
            echo '|CONF|'
            grep -E '^(BLOCK_IPV6|IPV6_GATEWAY)=' /data/adb/mihomo/gateway.conf 2>/dev/null
            echo '|RA|'
            pidof v6ra >/dev/null 2>&1 && echo 1 || echo 0
            echo '|UP|'
            ip -6 addr show scope global 2>/dev/null | grep 'inet6 ' | grep -v 'fd00:1234::'
        """.trimIndent()
    }
}
