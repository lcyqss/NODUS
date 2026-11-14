package com.mihomo.gateway.model

/**
 * 节点出站地址族。
 *
 * - AUTO：启动/网络变化时探测本机 IPv6，自动在「IPv6 优先 / IPv4 优先」之间切换
 * - DUAL：不覆写节点 ip-version，由内核同时解析 A/AAAA 择优
 * - V6/V4：优先一种协议，另一种回落
 * - V6_ONLY/V4_ONLY：严格只解析一种协议（无该协议地址的节点不可用）
 */
enum class IpMode(val label: String, val wireArg: String) {
    AUTO("自动", "auto"),
    DUAL("双栈", "dual"),
    V6("IPv6 优先", "v6"),
    V4("IPv4 优先", "v4"),
    V6_ONLY("仅 IPv6", "v6-only"),
    V4_ONLY("仅 IPv4", "v4-only")
}

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
    val sourceIP: String,
    val sourcePort: String,
    val destinationIP: String,
    val network: String,
    val rule: String,
    val rulePayload: String,
    val chains: List<String>,
    val upload: Long,
    val download: Long,
    val start: Long
)

data class RuleInfo(val type: String, val payload: String, val proxy: String)

data class LogEntry(val level: String, val payload: String, val time: Long)

data class Traffic(val up: Long, val down: Long)

data class NetIface(val name: String, val cidr: String)

data class ProviderInfo(
    val name: String,
    val type: String,
    val vehicleType: String,
    val nodeCount: Int,
    val updatedAt: String,
    val testUrl: String
)

data class GatewayConf(val mode: IpMode = IpMode.AUTO, val blockIpv6: Boolean = true)

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
    val desiredRunning: Boolean = true,
    val updateIntervalHours: Int = 0,
    val lastUpdateAt: Long = 0L,
    val todayDate: String = "",
    val todayUp: Long = 0L,
    val todayDown: Long = 0L,
    val subName: String = "",
    val nodeCount: Int = 0,
    val favoriteNodes: String = "",
    val subscriptions: String = "",
    val activeSubId: String = ""
)

data class SubscriptionProfile(
    val id: String,
    val name: String,
    val url: String,
    val lastUpdateAt: Long = 0L,
    val nodeCount: Int = 0
)

object SubscriptionCodec {
    fun decode(raw: String): List<SubscriptionProfile> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val url = obj.optString("url")
                if (url.isBlank()) return@mapNotNull null
                SubscriptionProfile(
                    id = obj.optString("id").ifBlank { url },
                    name = obj.optString("name").ifBlank { "订阅" },
                    url = url,
                    lastUpdateAt = obj.optLong("lastUpdateAt", 0L),
                    nodeCount = obj.optInt("nodeCount", 0)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encode(list: List<SubscriptionProfile>): String {
        val array = org.json.JSONArray()
        list.forEach { item ->
            array.put(
                org.json.JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("url", item.url)
                    .put("lastUpdateAt", item.lastUpdateAt)
                    .put("nodeCount", item.nodeCount)
            )
        }
        return array.toString()
    }
}

sealed class AppError(override val message: String?) : Throwable(message) {
    data object CoreDown : AppError("内核未运行")
    data object Unauthorized : AppError("密钥不匹配")
    data object RootDenied : AppError("未获得 root")
    data class Network(val error: Throwable) : AppError(error.message ?: "网络错误")
    data class Protocol(val detail: String) : AppError(detail)
}