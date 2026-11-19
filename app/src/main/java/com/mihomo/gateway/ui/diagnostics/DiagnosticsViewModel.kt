package com.mihomo.gateway.ui.diagnostics

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.core.ClientIpv6Status
import com.mihomo.gateway.core.ShellExecutor
import com.mihomo.gateway.core.ShellLog
import com.mihomo.gateway.core.ShellRecord
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiagnosticsState(
    val running: Boolean = false,
    val version: String = "",
    val latencyMs: Long = -1,
    val rules: String = "",
    val snapshotAt: Long = 0,
    val error: String? = null,
    val quicRejects: Int = -1,
    val dnsFallbackFailures: Int = -1,
    val nodeName: String = "",
    val nodeDelay: Int = -1,
    val udpProxyEnabled: Boolean = false
)

class DiagnosticsViewModel(
    private val api: MihomoApi = AppContainer.api,
    private val shell: ShellExecutor = AppContainer.shell
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    val records: StateFlow<List<ShellRecord>> = ShellLog.records

    private val _clientIpv6 = MutableStateFlow<ClientIpv6Status?>(null)
    val clientIpv6: StateFlow<ClientIpv6Status?> = _clientIpv6.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            val result = api.version()
            val cost = SystemClock.elapsedRealtime() - started
            _state.update {
                it.copy(
                    running = result.isSuccess,
                    version = result.getOrNull()?.version ?: "",
                    latencyMs = if (result.isSuccess) cost else -1,
                    error = result.exceptionOrNull()?.message
                )
            }
            refreshClientIpv6()
            refreshNetworkStats()
        }
    }

    /** 网络链路指标：QUIC 拒绝计数、DNS fallback 失败、当前节点延迟。 */
    private suspend fun refreshNetworkStats() {
        val quicRaw = shell.run("iptables -vnL MIHOMO_FWD 2>/dev/null | grep 'dpt:443' | head -1").stdout
        val quic = quicRaw.trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull() ?: -1

        val dnsRaw = shell.run(
            "off=\$(cat /data/adb/mihomo/.log_offset 2>/dev/null); " +
                "case \"\$off\" in ''|*[!0-9]*) off=0;; esac; " +
                "tail -n +\$((off + 1)) /data/adb/mihomo/mihomo.log 2>/dev/null | " +
                "grep -cE '1\\.1\\.1\\.1:53|8\\.8\\.8\\.8:53'"
        ).stdout
        val dnsFailures = dnsRaw.trim().toIntOrNull() ?: -1

        val udpConf = shell.run("grep -m1 '^UDP_PROXY=' /data/adb/mihomo/gateway.conf 2>/dev/null").stdout
        val udpProxyEnabled = udpConf.contains("=1")

        var nodeName = ""
        var nodeDelay = -1
        api.proxies().onSuccess { list ->
            val group = list.firstOrNull { it.name == "良心云" }
            var current = group?.now.orEmpty()
            val currentProxy = list.firstOrNull { it.name == current }
            if (currentProxy?.isGroup == true) current = currentProxy.now.orEmpty()
            nodeName = current
            nodeDelay = list.firstOrNull { it.name == current }?.delay ?: -1
        }
        _state.update {
            it.copy(
                quicRejects = quic,
                dnsFallbackFailures = dnsFailures,
                nodeName = nodeName,
                nodeDelay = nodeDelay,
                udpProxyEnabled = udpProxyEnabled
            )
        }
    }

    /** 普通设备的 IPv6 出口状态；设备策略设备不在其中，只给出启用条数。 */
    private suspend fun refreshClientIpv6() {
        val enabledPolicies = runCatching {
            AppContainer.devicePolicies.policies.first().count { it.enabled }
        }.getOrDefault(0)
        runCatching { ClientIpv6Status.detect(shell, enabledPolicies) }
            .onSuccess { _clientIpv6.value = it }
    }

    private val _dnsAnswers = MutableStateFlow<List<String>>(emptyList())
    val dnsAnswers: StateFlow<List<String>> = _dnsAnswers.asStateFlow()

    private val _dnsBusy = MutableStateFlow(false)
    val dnsBusy: StateFlow<Boolean> = _dnsBusy.asStateFlow()

    fun queryDns(name: String, type: String = "A") {
        val target = name.trim()
        if (target.isBlank()) {
            _actionMessage.value = "请输入域名"
            return
        }
        viewModelScope.launch {
            _dnsBusy.value = true
            api.dnsQuery(target, type)
                .onSuccess {
                    _dnsAnswers.value = it
                    _actionMessage.value = "DNS 查询完成: ${it.size} 条"
                }
                .onFailure {
                    _dnsAnswers.value = emptyList()
                    _actionMessage.value = "DNS 查询失败: ${it.message}"
                }
            _dnsBusy.value = false
        }
    }

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun restartCore() {
        viewModelScope.launch {
            _actionMessage.value = "重启中…"
            api.restartCore()
                .onSuccess {
                    kotlinx.coroutines.delay(1500)
                    _actionMessage.value = "内核已重启"
                    refresh()
                }
                .onFailure { _actionMessage.value = "重启失败: ${it.message}" }
        }
    }

    fun flushFakeIp() {
        viewModelScope.launch {
            api.flushFakeIp()
                .onSuccess { _actionMessage.value = "fake-ip 缓存已清空" }
                .onFailure { _actionMessage.value = "清理失败: ${it.message}" }
        }
    }

    fun flushDns() {
        viewModelScope.launch {
            api.flushDns()
                .onSuccess { _actionMessage.value = "DNS 缓存已清空" }
                .onFailure { _actionMessage.value = "清理失败: ${it.message}" }
        }
    }

    fun snapshot() {
        viewModelScope.launch {
            val result = shell.run(
                "iptables -t nat -S MIHOMO_NAT 2>/dev/null; " +
                    "iptables -S MIHOMO_FWD 2>/dev/null; " +
                    "ip6tables -S MIHOMO6_FWD 2>/dev/null"
            )
            _state.update {
                it.copy(
                    rules = result.stdout.take(8000),
                    snapshotAt = System.currentTimeMillis()
                )
            }
        }
    }

    fun buildReport(): String {
        val s = _state.value
        return buildString {
            appendLine("NODUS 诊断报告")
            appendLine("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("内核: ${s.version.ifBlank { "-" }}  运行: ${if (s.running) "是" else "否"}")
            appendLine("API 延迟: ${if (s.latencyMs >= 0) "${s.latencyMs}ms" else "-"}")
            s.error?.let { appendLine("错误: $it") }
            _clientIpv6.value?.let { ipv6 ->
                appendLine()
                appendLine("客户端 IPv6（普通设备）:")
                ipv6.detail.lineSequence().filter { it.isNotBlank() }.forEach { appendLine(it) }
            }
            appendLine()
            appendLine("最近 root 命令:")
            records.value.takeLast(10).forEach {
                appendLine("[${it.trace}] code=${it.code} ${it.costMs}ms  ${it.command}")
            }
            if (s.rules.isNotBlank()) {
                appendLine()
                appendLine("规则快照:")
                appendLine(s.rules)
            }
        }
    }
}
