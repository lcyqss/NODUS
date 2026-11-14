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

    fun trafficFlow(): Flow<Traffic>

    fun logFlow(level: String = "info"): Flow<LogEntry>

    companion object {
        const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"
    }
}
