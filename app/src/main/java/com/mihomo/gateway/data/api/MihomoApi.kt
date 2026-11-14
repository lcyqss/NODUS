package com.mihomo.gateway.data.api

import com.mihomo.gateway.model.ConfigInfo
import com.mihomo.gateway.model.ConnectionInfo
import com.mihomo.gateway.model.LogEntry
import com.mihomo.gateway.model.ProxyNode
import com.mihomo.gateway.model.RuleInfo
import com.mihomo.gateway.model.Traffic
import com.mihomo.gateway.model.VersionInfo
import kotlinx.coroutines.flow.Flow

/**
 * mihomo 控制 API 的抽象。实现必须只访问 127.0.0.1。
 * 所有方法失败时返回 Result.failure(AppError)。
 */
interface MihomoApi {

    suspend fun version(): Result<VersionInfo>

    suspend fun configs(): Result<ConfigInfo>

    suspend fun proxies(): Result<List<ProxyNode>>

    suspend fun select(group: String, name: String): Result<Unit>

    suspend fun delay(name: String, timeoutMs: Int = 5000, url: String = DEFAULT_TEST_URL): Result<Int>

    suspend fun connections(): Result<List<ConnectionInfo>>

    suspend fun closeConnection(id: String): Result<Unit>

    suspend fun closeAllConnections(): Result<Unit>

    suspend fun rules(): Result<List<RuleInfo>>

    /** Provider 列表（节点来源）。 */
    suspend fun providers(): Result<List<com.mihomo.gateway.model.ProviderInfo>>

    /** 触发 provider 健康检查。 */
    suspend fun providerHealthCheck(name: String): Result<Unit>

    /** DNS 查询（诊断用）。 */
    suspend fun dnsQuery(name: String, type: String = "A"): Result<List<String>>

    /** 整组测速：一次请求返回该组所有节点延迟（ms）。 */    suspend fun groupDelay(group: String, timeoutMs: Int = 3000, url: String = DEFAULT_TEST_URL): Result<Map<String, Int>>

    /** 切换运行模式：rule / global / direct。 */
    suspend fun setMode(mode: String): Result<Unit>

    /** 重启内核。 */
    suspend fun restartCore(): Result<Unit>

    /** 清空 fake-ip 缓存。 */
    suspend fun flushFakeIp(): Result<Unit>

    /** 清空 DNS 缓存。 */
    suspend fun flushDns(): Result<Unit>

    fun trafficFlow(): Flow<Traffic>

    /** 内核内存占用（字节）。 */
    fun memoryFlow(): Flow<Long>

    fun logFlow(level: String = "info"): Flow<LogEntry>

    companion object {
        const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"
    }
}
