package com.mihomo.gateway.core

data class NeighborInfo(
    val ip: String,
    val mac: String,
    val hostname: String?
)

/**
 * 通过 root 读取 ARP/邻居表与 DHCP 租约，补齐设备的 MAC 与主机名。
 * 仅用于展示；失败时返回空表，不影响拓扑主流程。
 */
class NeighborResolver(private val shell: ShellExecutor) {

    suspend fun resolve(): Map<String, NeighborInfo> {
        val neighbors = runCatching { readNeighbors() }.getOrDefault(emptyMap())
        if (neighbors.isEmpty()) return emptyMap()
        val hostnames = runCatching { readLeases() }.getOrDefault(emptyMap())
        return neighbors.mapValues { (ip, mac) ->
            NeighborInfo(ip = ip, mac = mac, hostname = hostnames[ip])
        }
    }

    private suspend fun readNeighbors(): Map<String, String> {
        val text = shell.run("ip neigh show 2>/dev/null; ip -6 neigh show 2>/dev/null").stdout
        val map = mutableMapOf<String, String>()
        text.lineSequence().forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.isEmpty()) return@forEach
            val ip = parts[0]
            val macIndex = parts.indexOf("lladdr")
            if (macIndex >= 0) {
                val mac = parts.getOrNull(macIndex + 1) ?: return@forEach
                if (mac.matches(Regex("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}"))) {
                    map[ip] = mac.lowercase()
                }
            }
        }
        return map
    }

    /** dnsmasq 租约格式：<expiry> <mac> <ip> <hostname> <client-id> */
    private suspend fun readLeases(): Map<String, String> {
        val paths = listOf(
            "/data/misc/dhcp/dnsmasq.leases",
            "/data/misc/dnsmasq/dnsmasq.leases",
            "/data/vendor/dhcp/dnsmasq.leases"
        )
        val command = paths.joinToString("; ") { "cat $it 2>/dev/null" }
        val text = shell.run(command).stdout
        val map = mutableMapOf<String, String>()
        text.lineSequence().forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 4) {
                val ip = parts[2]
                val name = parts[3]
                if (name.isNotBlank() && name != "*") map[ip] = name
            }
        }
        return map
    }
}
