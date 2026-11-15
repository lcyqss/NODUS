package com.mihomo.gateway.data.policy

import com.mihomo.gateway.data.store.SettingsStore
import com.mihomo.gateway.model.ConditionTemplateCodec
import com.mihomo.gateway.model.DevicePolicyCodec
import com.mihomo.gateway.model.NodeTagCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class BackupSummary(
    val policies: Int,
    val tags: Int,
    val templates: Int
)

/**
 * 设备策略备份：导出/导入 策略、标签、条件模板。
 * 只负责设置数据；导入后由调用方触发配置重建。
 */
class PolicyBackupService(
    private val settings: SettingsStore
) {

    suspend fun export(): String = withContext(Dispatchers.IO) {
        val current = settings.settings.first()
        JSONObject()
            .put("schema", 1)
            .put("app", "mihomo-gateway")
            .put("exportedAt", System.currentTimeMillis())
            .put("policies", JSONArray(DevicePolicyCodec.encode(DevicePolicyCodec.decode(current.devicePolicies))))
            .put("tags", JSONArray(NodeTagCodec.encode(NodeTagCodec.decode(current.nodeTags))))
            .put("templates", JSONArray(ConditionTemplateCodec.encode(ConditionTemplateCodec.decode(current.conditionTemplates))))
            .toString(2)
    }

    suspend fun import(raw: String, merge: Boolean): Result<BackupSummary> =
        withContext(Dispatchers.IO) {
            val root = runCatching { JSONObject(raw) }.getOrElse {
                return@withContext Result.failure(IllegalArgumentException("不是有效的 JSON"))
            }
            if (root.optInt("schema", 1) != 1) {
                return@withContext Result.failure(IllegalArgumentException("不支持的备份版本"))
            }
            val importedPolicies = DevicePolicyCodec.decode(root.optJSONArray("policies")?.toString().orEmpty())
            val importedTags = NodeTagCodec.decode(root.optJSONArray("tags")?.toString().orEmpty())
                .filterNot { it.builtin }
            val importedTemplates = ConditionTemplateCodec.decode(
                root.optJSONArray("templates")?.toString().orEmpty()
            )

            val current = settings.settings.first()
            val policies = if (merge) {
                mergeById(
                    DevicePolicyCodec.decode(current.devicePolicies),
                    importedPolicies,
                    { it.id }
                )
            } else {
                importedPolicies
            }
            val tags = if (merge) {
                mergeById(NodeTagCodec.decode(current.nodeTags), importedTags, { it.id })
            } else {
                importedTags
            }
            val templates = if (merge) {
                mergeById(
                    ConditionTemplateCodec.decode(current.conditionTemplates),
                    importedTemplates,
                    { it.id }
                )
            } else {
                importedTemplates
            }

            settings.update {
                it.copy(
                    devicePolicies = DevicePolicyCodec.encode(policies),
                    nodeTags = NodeTagCodec.encode(tags),
                    conditionTemplates = ConditionTemplateCodec.encode(templates)
                )
            }
            Result.success(
                BackupSummary(
                    policies = importedPolicies.size,
                    tags = importedTags.size,
                    templates = importedTemplates.size
                )
            )
        }

    private fun <T> mergeById(existing: List<T>, imported: List<T>, id: (T) -> String): List<T> {
        val map = LinkedHashMap<String, T>()
        existing.forEach { map[id(it)] = it }
        imported.forEach { map[id(it)] = it }
        return map.values.toList()
    }
}
