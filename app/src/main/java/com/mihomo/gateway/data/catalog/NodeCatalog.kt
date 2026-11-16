package com.mihomo.gateway.data.catalog

import android.content.Context
import com.mihomo.gateway.data.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File

/** 订阅里的一个节点（只读元数据）。 */
data class NodeInfo(
    val name: String,
    val type: String
)

/** 当前在线/曾出现过的客户端设备。 */
data class DeviceTarget(
    val ip: String,
    val mac: String,
    val hostname: String?
) {
    val display: String
        get() = buildString {
            append(hostname?.takeIf { it.isNotBlank() } ?: mac)
            if (hostname?.isNotBlank() == true) append(" · ").append(mac)
            append(" · ").append(ip)
        }
}

interface NodeCatalogRepository {
    /** 当前生效订阅里的节点（来自缓存 YAML），没有订阅时为空。 */
    suspend fun nodes(): List<NodeInfo>
}

class NodeCatalogRepositoryImpl(
    private val context: Context,
    private val settings: SettingsStore
) : NodeCatalogRepository {

    private val lock = Any()
    private var cacheKey: String? = null
    private var cached: List<NodeInfo> = emptyList()

    override suspend fun nodes(): List<NodeInfo> = withContext(Dispatchers.IO) {
        val activeId = settings.settings.first().activeSubId
        if (activeId.isBlank()) return@withContext emptyList()
        val file = File(File(context.filesDir, "profiles"), "$activeId.yaml")
        if (!file.exists()) return@withContext emptyList()
        val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
        synchronized(lock) {
            if (key == cacheKey) return@synchronized cached
            val parsed = parse(file.readText())
            cacheKey = key
            cached = parsed
            parsed
        }
    }

    private fun parse(text: String): List<NodeInfo> {
        val root = runCatching {
            Yaml(SafeConstructor()).load<Any?>(text) as? Map<*, *>
        }.getOrNull() ?: return emptyList()
        val proxies = root["proxies"] as? List<*> ?: return emptyList()
        return proxies.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val name = map["name"]?.toString()?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null
            NodeInfo(
                name = name,
                type = map["type"]?.toString()?.lowercase().orEmpty(),
            )
        }
    }
}