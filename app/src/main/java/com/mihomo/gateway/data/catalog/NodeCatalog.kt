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
    val type: String,
    val region: String
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

data class Region(val code: String, val label: String, val keywords: List<String>)

/** 机场节点命名里的地区识别；识别不到返回空串。 */
object NodeRegions {
    val all = listOf(
        Region("HK", "香港", listOf("香港", "hong kong", "hongkong", "hk")),
        Region("TW", "台湾", listOf("台湾", "台灣", "taiwan", "tw")),
        Region("SG", "新加坡", listOf("新加坡", "singapore", "sg")),
        Region("JP", "日本", listOf("日本", "japan", "tokyo", "osaka", "jp")),
        Region("KR", "韩国", listOf("韩国", "韓國", "korea", "seoul", "kr")),
        Region("US", "美国", listOf("美国", "美國", "united states", "los angeles", "seattle", "us")),
        Region("UK", "英国", listOf("英国", "英國", "united kingdom", "london", "uk")),
        Region("DE", "德国", listOf("德国", "德國", "germany", "de")),
        Region("AU", "澳大利亚", listOf("澳大利亚", "澳洲", "australia", "au")),
        Region("CA", "加拿大", listOf("加拿大", "canada", "ca")),
        Region("IN", "印度", listOf("印度", "india", "in")),
        Region("TR", "土耳其", listOf("土耳其", "turkey", "tr")),
        Region("RU", "俄罗斯", listOf("俄罗斯", "russia", "ru"))
    )

    fun detect(name: String): String {
        val lower = name.lowercase()
        val match = all.firstOrNull { region -> region.keywords.any { lower.contains(it) } }
        return match?.code ?: ""
    }

    fun label(code: String): String =
        if (code.isBlank()) "任意地区" else all.firstOrNull { it.code == code }?.label ?: code
}

interface NodeCatalogRepository {
    /** 当前生效订阅里的节点（来自缓存 YAML），没有订阅时为空。 */
    suspend fun nodes(): List<NodeInfo>
}

class NodeCatalogRepositoryImpl(
    private val context: Context,
    private val settings: SettingsStore
) : NodeCatalogRepository {

    override suspend fun nodes(): List<NodeInfo> = withContext(Dispatchers.IO) {
        val activeId = settings.settings.first().activeSubId
        if (activeId.isBlank()) return@withContext emptyList()
        val file = File(File(context.filesDir, "profiles"), "$activeId.yaml")
        if (!file.exists()) return@withContext emptyList()
        parse(file.readText())
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
                region = NodeRegions.detect(name)
            )
        }
    }
}
