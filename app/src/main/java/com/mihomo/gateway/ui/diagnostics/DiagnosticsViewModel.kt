package com.mihomo.gateway.ui.diagnostics

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.core.ShellExecutor
import com.mihomo.gateway.core.ShellLog
import com.mihomo.gateway.core.ShellRecord
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiagnosticsState(
    val running: Boolean = false,
    val version: String = "",
    val latencyMs: Long = -1,
    val rules: String = "",
    val snapshotAt: Long = 0,
    val error: String? = null
)

class DiagnosticsViewModel(
    private val api: MihomoApi = AppContainer.api,
    private val shell: ShellExecutor = AppContainer.shell
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    val records: StateFlow<List<ShellRecord>> = ShellLog.records

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
            appendLine("Mihomo Gateway 诊断报告")
            appendLine("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("内核: ${s.version.ifBlank { "-" }}  运行: ${if (s.running) "是" else "否"}")
            appendLine("API 延迟: ${if (s.latencyMs >= 0) "${s.latencyMs}ms" else "-"}")
            s.error?.let { appendLine("错误: $it") }
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
