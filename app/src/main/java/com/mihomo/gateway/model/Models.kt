package com.mihomo.gateway.model

enum class IpMode { AUTO, V6, V4, DUAL }

data class VersionInfo(val version: String, val meta: Boolean)

data class ConfigInfo(
    val port: Int,
    val socksPort: Int,
    val redirPort: Int,
    val mode: String,
    val logLevel: String
)

data class ProxyNode(
    val name: String,
    val type: String,
    val udp: Boolean = false,
    val delay: Int? = null,
    val alive: Boolean? = null,
    val isGroup: Boolean = false,
    val now: String? = null,
    val all: List<String> = emptyList()
)

data class ConnectionInfo(
    val id: String,
    val host: String,
    val network: String,
    val rule: String,
    val chains: List<String>,
    val upload: Long,
    val download: Long,
    val start: Long
)

data class RuleInfo(val type: String, val payload: String, val proxy: String)

data class LogEntry(val level: String, val payload: String, val time: Long)

data class Traffic(val up: Long, val down: Long)

data class NetIface(val name: String, val cidr: String)

data class GatewayState(
    val running: Boolean = false,
    val mode: IpMode = IpMode.AUTO,
    val blockIpv6: Boolean = true,
    val interfaces: List<NetIface> = emptyList(),
    val version: String = "",
    val error: String? = null
)

data class AppSettings(
    val subscriptionUrl: String = "",
    val ipMode: IpMode = IpMode.AUTO,
    val blockIpv6: Boolean = true,
    val autoStart: Boolean = true,
    val desiredRunning: Boolean = true
)

sealed class AppError(override val message: String?) : Throwable(message) {
    data object CoreDown : AppError("内核未运行")
    data object Unauthorized : AppError("密钥不匹配")
    data object RootDenied : AppError("未获得 root")
    data class Network(val error: Throwable) : AppError(error.message ?: "网络错误")
    data class Protocol(val detail: String) : AppError(detail)
}