package com.mihomo.gateway.core

import com.mihomo.gateway.data.api.MihomoApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class HealthReport(
    val dns: Boolean,
    val ports: Boolean,
    val proxy: Boolean,
    val ipv6: Boolean,
    val tproxy: Boolean,
    val dnsDomains: Boolean,
    val rules: Boolean,
    val detail: String
) {
    val ok: Boolean get() = dns && ports && proxy && ipv6 && tproxy && dnsDomains && rules
}

class GatewayHealthChecker(
    private val shell: ShellExecutor,
    private val api: MihomoApi
) {
    private val proxyClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890)))
        .build()

    suspend fun check(ipv6Enabled: Boolean): HealthReport = withContext(Dispatchers.IO) {
        val notes = mutableListOf<String>()
        val ports = checkPorts(ipv6Enabled)
        if (!ports) notes += "端口未监听"
        val dns = checkDns("www.gstatic.com", A)
        if (!dns) notes += "DNS 1053 无响应"
        val proxy = checkProxy()
        if (!proxy) notes += "代理 204 检查失败"
        val tproxy = checkTproxy(ipv6Enabled)
        if (!tproxy) notes += "TPROXY 链/监听不完整"
        val ipv6 = if (!ipv6Enabled) true else checkIpv6()
        if (!ipv6) notes += "IPv6 ULA/v6dns/AAAA 异常"
        val dnsDomains = checkDomains()
        if (!dnsDomains) notes += "CN/国际域名 DNS 解析失败"
        val rules = checkRules()
        if (!rules) notes += "规则表未加载"
        HealthReport(dns, ports, proxy, ipv6, tproxy, dnsDomains, rules, notes.ifEmpty { listOf("组件级检查正常") }.joinToString("；"))
    }

    private suspend fun checkPorts(ipv6Enabled: Boolean): Boolean {
        val out = shell.run("ss -ltn 2>/dev/null", 5_000).stdout
        val base = out.contains(":7890") && out.contains(":7892") && out.contains(":9090")
        return base && (!ipv6Enabled || out.contains(":7895"))
    }

    private suspend fun checkTproxy(ipv6Enabled: Boolean): Boolean {
        val v4 = shell.run("iptables -t nat -S MIHOMO_NAT 2>/dev/null", 5_000).stdout
        val v6 = shell.run("ip6tables -t mangle -S MIHOMO6_TPROXY 2>/dev/null", 5_000).stdout
        val v4ok = v4.contains("REDIRECT")
        val v6ok = !ipv6Enabled || v6.contains("TPROXY")
        return v4ok && v6ok
    }

    private suspend fun checkIpv6(): Boolean {
        val addr = shell.run("ip -6 addr show 2>/dev/null", 5_000).stdout
        if (!addr.contains("fd00:1234::1")) return false
        val relay = shell.run("pidof v6dns >/dev/null 2>&1 && echo yes", 5_000).stdout.trim()
        if (relay != "yes") return false
        return checkDns("www.gstatic.com", A, host = "fd00:1234::1")
    }

    private suspend fun checkDomains(): Boolean {
        val cn = api.dnsQuery("www.baidu.com", "A").getOrNull()?.isNotEmpty() == true
        val intl = api.dnsQuery("www.google.com", "A").getOrNull()?.isNotEmpty() == true
        return cn && intl
    }

    private suspend fun checkRules(): Boolean =
        api.rules().getOrNull()?.isNotEmpty() == true

    private fun checkDns(name: String, type: Int, host: String = "127.0.0.1"): Boolean {
        return runCatching {
            val query = buildQuery(name, type)
            DatagramSocket().use { socket ->
                socket.soTimeout = 3_000
                val address = InetAddress.getByName(host)
                socket.send(DatagramPacket(query, query.size, address, 1053))
                val buf = ByteArray(2048)
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                val response = packet.data
                if (packet.length < 12) return false
                val qr = (response[2].toInt() and 0x80) != 0
                val rcode = response[3].toInt() and 0x0F
                val answers = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
                qr && rcode == 0 && answers > 0
            }
        }.getOrDefault(false)
    }

    private fun checkProxy(): Boolean {
        return runCatching {
            val request = Request.Builder()
                .url("http://www.gstatic.com/generate_204")
                .header("Connection", "close")
                .build()
            proxyClient.newCall(request).execute().use { response ->
                response.code == 204 || response.code == 200
            }
        }.getOrDefault(false)
    }

    private fun buildQuery(name: String, type: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x12)
        out.write(0x34)
        out.write(0x01)
        out.write(0x00)
        out.write(0x00)
        out.write(0x01)
        out.write(0x00)
        out.write(0x00)
        out.write(0x00)
        out.write(0x00)
        out.write(0x00)
        out.write(0x00)
        for (part in name.split('.')) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        out.write((type shr 8) and 0xFF)
        out.write(type and 0xFF)
        out.write(0x00)
        out.write(0x01)
        return out.toByteArray()
    }

    private companion object {
        private const val A = 1
        private const val AAAA = 28
    }
}
