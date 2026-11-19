package com.mihomo.gateway.data.repo

import com.mihomo.gateway.core.AppConfig
import android.util.Log
import com.mihomo.gateway.core.ShellExecutor
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.model.GatewayConf
import com.mihomo.gateway.model.GatewayState
import com.mihomo.gateway.model.IpMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

interface GatewayController {
    val state: StateFlow<GatewayState>
    suspend fun start(): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun setIpMode(mode: IpMode): Result<Unit>
    suspend fun setBlockIpv6(enabled: Boolean): Result<Unit>
    suspend fun setIpv6Gateway(enabled: Boolean): Result<Unit>

    /** 内网直连：把常见私网目标排除出代理重定向。 */
    suspend fun setDirectPrivateLan(enabled: Boolean): Result<Unit>

    /** UDP/QUIC 代理：开启后 UDP 443 走 TPROXY（实验特性）。 */
    suspend fun setUdpProxy(enabled: Boolean): Result<Unit>

    /** UDP 全量代理：在 UDP/QUIC 代理基础上扩展到所有 UDP（实验特性）。 */
    suspend fun setUdpProxyAll(enabled: Boolean): Result<Unit>

    /** 切换运行模式（rule/global/direct），同时写回 config.yaml，避免 reload 后回退。 */
    suspend fun setRuntimeMode(mode: String): Result<Unit>
    suspend fun detectUpstreamRa(): Boolean
    suspend fun applyRules(): Result<Unit>
    suspend fun refresh()

    /** 卸载前清理：停网关、清规则、删运行目录。 */
    suspend fun cleanup(): Result<Unit>

    /** 从 /data/adb/mihomo/gateway.conf 读取真实生效的配置（模式 / IPv6 拦截）。 */
    suspend fun readConf(): GatewayConf
}

class GatewayControllerImpl(
    private val shell: ShellExecutor,
    private val api: MihomoApi,
    private val base: String = AppConfig.BASE_DIR
) : GatewayController {

    private val _state = MutableStateFlow(GatewayState())
    override val state: StateFlow<GatewayState> = _state.asStateFlow()

    private val mutex = Mutex()

    override suspend fun start(): Result<Unit> = mutex.withLock {
        if (_state.value.running) return@withLock Result.success(Unit)
        Log.i(TAG, "start")
        val result = shell.run("sh $base/start.sh")
        if (result.code != 0) {
            _state.update { it.copy(error = "启动失败: ${result.stdout.take(200)}") }
            return Result.failure(IllegalStateException("start failed"))
        }
        val ready = waitReady()
        val conf = runCatching { readConf() }.getOrNull()
        _state.update {
            it.copy(
                running = ready != null,
                version = ready ?: "",
                mode = conf?.mode ?: it.mode,
                blockIpv6 = conf?.blockIpv6 ?: it.blockIpv6,
                ipv6Gateway = conf?.ipv6Gateway ?: it.ipv6Gateway,
                error = if (ready == null) "内核未就绪" else null
            )
        }
        if (ready == null) Result.failure(IllegalStateException("core not ready"))
        else Result.success(Unit)
    }

    override suspend fun stop(): Result<Unit> = mutex.withLock {
        Log.i(TAG, "stop")
        val result = shell.run("sh $base/stop.sh")
        _state.update { it.copy(running = false, version = "", error = null) }
        if (result.code == 0) Result.success(Unit)
        else Result.failure(IllegalStateException("stop failed"))
    }

    override suspend fun setIpMode(mode: IpMode): Result<Unit> = mutex.withLock {
        val result = shell.run("sh $base/set-ipver.sh ${mode.wireArg}")
        if (result.code == 0) {
            _state.update { it.copy(mode = mode) }
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(result.stdout.take(200)))
        }
    }

    override suspend fun setRuntimeMode(mode: String): Result<Unit> = mutex.withLock {
        if (mode !in setOf("rule", "global", "direct")) {
            return Result.failure(IllegalArgumentException("未知运行模式: $mode"))
        }
        val coreRunning = shell.run("pidof mihomo >/dev/null 2>&1 && echo yes", 5_000)
            .stdout.trim() == "yes"
        val switched = api.setMode(mode)
        if (switched.isFailure && coreRunning) {
            return Result.failure(
                IllegalStateException(switched.exceptionOrNull()?.message ?: "切换运行模式失败")
            )
        }
        // mihomo 的 PATCH /configs 只改运行时；reload 或重启会回到配置文件里的 mode。
        // 内核没运行时只写文件，等下次启动生效。
        val hasMode = shell.run("grep -q '^mode:' $base/config.yaml", 5_000).code == 0
        val written = if (hasMode) {
            shell.run("sed -i 's/^mode:.*/mode: $mode/' $base/config.yaml", 5_000)
        } else {
            shell.run("sed -i '1i mode: $mode' $base/config.yaml", 5_000)
        }
        if (written.code != 0) {
            return Result.failure(IllegalStateException("写入运行模式失败"))
        }
        Result.success(Unit)
    }

    override suspend fun setBlockIpv6(enabled: Boolean): Result<Unit> = mutex.withLock {
        val value = if (enabled) "1" else "0"
        val result = shell.run("sh $base/conf.sh BLOCK_IPV6 $value")
        if (result.code != 0) return Result.failure(IllegalStateException(result.stdout.take(200)))
        shell.run("sh $base/apply.sh")
        _state.update { it.copy(blockIpv6 = enabled) }
        Result.success(Unit)
    }

    override suspend fun detectUpstreamRa(): Boolean {
        val r = shell.run("sh $base/ipv6.sh detect", 10_000)
        return r.code == 0 && r.stdout.contains("UPSTREAM_RA=1")
    }

    override suspend fun setIpv6Gateway(enabled: Boolean): Result<Unit> = mutex.withLock {
        if (enabled && detectUpstreamRa()) {
            return Result.failure(
                IllegalStateException("检测到主路由 IPv6 RA，旁路由下无法保证 IPv6 全部代理")
            )
        }
        val coreRunning = shell.run("pidof mihomo").stdout.isNotBlank()
        if (enabled) {
            if (coreRunning) {
                val r = shell.run("sh $base/ipv6.sh enable", 30_000)
                if (r.code != 0) {
                    shell.run("sh $base/ipv6.sh disable", 30_000)
                    shell.run("sh $base/conf.sh IPV6_GATEWAY 0")
                    shell.run("sh $base/conf.sh BLOCK_IPV6 1")
                    // 脚本已回滚 conf，状态也要同步，避免 UI 显示「已开启」但实际是关闭+拦截
                    _state.update { it.copy(ipv6Gateway = false, blockIpv6 = true) }
                    return Result.failure(IllegalStateException(r.stdout.take(300)))
                }
            } else {
                shell.run("sh $base/conf.sh IPV6_GATEWAY 1")
                shell.run("sh $base/conf.sh BLOCK_IPV6 0")
            }
            _state.update { it.copy(ipv6Gateway = true, blockIpv6 = false) }
        } else {
            // Clean up even if the core is down; RA/TPROXY may still be alive.
            shell.run("sh $base/ipv6.sh disable", 30_000)
            shell.run("sh $base/conf.sh IPV6_GATEWAY 0")
            shell.run("sh $base/conf.sh BLOCK_IPV6 1")
            _state.update { it.copy(ipv6Gateway = false, blockIpv6 = true) }
        }
        Result.success(Unit)
    }

    override suspend fun setDirectPrivateLan(enabled: Boolean): Result<Unit> = mutex.withLock {
        val wrote = shell.run("sh $base/conf.sh DIRECT_PRIVATE ${if (enabled) "1" else "0"}")
        if (wrote.code != 0) {
            return Result.failure(IllegalStateException(wrote.stdout.take(200)))
        }
        val applied = shell.run("sh $base/apply.sh", 30_000)
        if (applied.code != 0) {
            return Result.failure(IllegalStateException(applied.stdout.take(200)))
        }
        Result.success(Unit)
    }

    override suspend fun setUdpProxy(enabled: Boolean): Result<Unit> = mutex.withLock {
        val wrote = shell.run("sh $base/conf.sh UDP_PROXY ${if (enabled) "1" else "0"}")
        if (wrote.code != 0) {
            return Result.failure(IllegalStateException(wrote.stdout.take(200)))
        }
        // 关闭 UDP/QUIC 代理时一并关闭全量 UDP，避免下次打开时代理范围意外扩大
        if (!enabled) {
            shell.run("sh $base/conf.sh UDP_PROXY_ALL 0")
        }
        val applied = shell.run("sh $base/apply.sh", 30_000)
        if (applied.code != 0) {
            return Result.failure(IllegalStateException(applied.stdout.take(200)))
        }
        Result.success(Unit)
    }

    override suspend fun setUdpProxyAll(enabled: Boolean): Result<Unit> = mutex.withLock {
        val wrote = shell.run("sh $base/conf.sh UDP_PROXY_ALL ${if (enabled) "1" else "0"}")
        if (wrote.code != 0) {
            return Result.failure(IllegalStateException(wrote.stdout.take(200)))
        }
        val applied = shell.run("sh $base/apply.sh", 30_000)
        if (applied.code != 0) {
            return Result.failure(IllegalStateException(applied.stdout.take(200)))
        }
        Result.success(Unit)
    }

    override suspend fun applyRules(): Result<Unit> {
        val result = shell.run("sh $base/apply.sh")
        return if (result.code == 0) Result.success(Unit)
        else Result.failure(IllegalStateException(result.stdout.take(200)))
    }

    override suspend fun readConf(): GatewayConf {
        val text = shell.run("cat $base/gateway.conf 2>/dev/null").stdout
        val map = text.lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim() else null
            }
            .toMap()
        val mode = when (map["IP_MODE"]?.lowercase()) {
            "v6" -> IpMode.V6
            "v4" -> IpMode.V4
            "dual" -> IpMode.DUAL
            "v6-only", "ipv6" -> IpMode.V6_ONLY
            "v4-only", "ipv4" -> IpMode.V4_ONLY
            else -> IpMode.AUTO
        }
        val block = map["BLOCK_IPV6"]?.trim() != "0"
        val ipv6Gateway = map["IPV6_GATEWAY"]?.trim() == "1"
        val directPrivate = map["DIRECT_PRIVATE"]?.trim() == "1"
        val udpProxy = map["UDP_PROXY"]?.trim() == "1"
        val udpProxyAll = map["UDP_PROXY_ALL"]?.trim() == "1"
        return GatewayConf(mode, block, ipv6Gateway, directPrivate, udpProxy, udpProxyAll)
    }

    override suspend fun cleanup(): Result<Unit> {
        val result = shell.run("sh $base/cleanup.sh", 30_000)
        _state.update { it.copy(running = false, version = "", error = null) }
        return if (result.code == 0) Result.success(Unit)
        else Result.failure(IllegalStateException(result.stdout.take(200)))
    }

    override suspend fun refresh() {
        val version = api.version()
        val conf = runCatching { readConf() }.getOrNull()
        _state.update {
            it.copy(
                running = version.isSuccess,
                version = version.getOrNull()?.version ?: "",
                mode = conf?.mode ?: it.mode,
                blockIpv6 = conf?.blockIpv6 ?: it.blockIpv6,
                ipv6Gateway = conf?.ipv6Gateway ?: it.ipv6Gateway,
                error = version.exceptionOrNull()?.message
            )
        }
    }

    private suspend fun waitReady(): String? = withTimeoutOrNull(8_000) {
        while (true) {
            val v = api.version()
            if (v.isSuccess) return@withTimeoutOrNull v.getOrNull()?.version
            delay(250)
        }
        null
    }

    companion object {
        private const val TAG = "GW-Repo"
    }
}
