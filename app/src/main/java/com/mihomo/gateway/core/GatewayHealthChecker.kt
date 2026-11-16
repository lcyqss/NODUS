package com.mihomo.gateway.core

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
    val detail: String
) {
    val ok: Boolean get() = dns && ports && proxy
}

class GatewayHealthChecker(
    private val shell: ShellExecutor
) {
    private val proxyClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890)))
        .build()

    suspend fun check(): HealthReport = withContext(Dispatchers.IO) {
        val notes = mutableListOf<String>()
        val ports = checkPorts()
        if (!ports) notes += "端口未监听"
        val dns = checkDns()
        if (!dns) notes += "DNS 1053 无响应"
        val proxy = checkProxy()
        if (!proxy) notes += "代理 204 检查失败"
        HealthReport(dns, ports, proxy, notes.ifEmpty { listOf("正常") }.joinToString("；"))
    }

    private suspend fun checkPorts(): Boolean {
        val out = shell.run("ss -ltn 2>/dev/null", 5_000).stdout
        return out.contains(":7890") && out.contains(":7892") && out.contains(":9090")
    }

    private fun checkDns(): Boolean {
        return runCatching {
            val query = buildQuery("www.gstatic.com")
            DatagramSocket().use { socket ->
                socket.soTimeout = 3_000
                val address = InetAddress.getByName("127.0.0.1")
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

    private fun buildQuery(name: String): ByteArray {
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
        out.write(0x00)
        out.write(0x01)
        out.write(0x00)
        out.write(0x01)
        return out.toByteArray()
    }
}
