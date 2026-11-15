package com.mihomo.gateway.model

import org.json.JSONArray
import org.json.JSONObject

/** 策略条件里对一个标签的引用；negated 表示取反（NOT）。 */
data class TagSelection(
    val tagId: String,
    val negated: Boolean = false
)

/** 设备策略的出口条件：多个标签默认 AND。 */
data class DeviceSelector(
    val tags: List<TagSelection> = emptyList()
)

/** 按源设备（MAC）生效的出口策略：优先条件 + 回落条件。 */
data class DevicePolicy(
    val id: String,
    val mac: String,
    val label: String = "",
    val primary: DeviceSelector = DeviceSelector(),
    val fallback: DeviceSelector = DeviceSelector(),
    val maxDelayMs: Int = 0,
    val enabled: Boolean = true
)

/**
 * 节点标签。
 * - 普通标签：名称包含 include 任一（OR）且不包含 exclude 任一（NOT）
 * - 内置标签：builtinKind = "type" / "region"，按节点类型/识别地区匹配，只读
 */
data class NodeTag(
    val id: String,
    val name: String,
    val include: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
    val builtin: Boolean = false,
    val builtinKind: String? = null
)

object NodeTagCodec {
    fun decode(raw: String): List<NodeTag> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optString("id")
                if (id.isBlank()) return@mapNotNull null
                NodeTag(
                    id = id,
                    name = obj.optString("name").ifBlank { id },
                    include = obj.optJSONArray("include").toStringList(),
                    exclude = obj.optJSONArray("exclude").toStringList()
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encode(list: List<NodeTag>): String {
        val array = JSONArray()
        list.forEach { tag ->
            array.put(
                JSONObject()
                    .put("id", tag.id)
                    .put("name", tag.name)
                    .put("include", JSONArray(tag.include))
                    .put("exclude", JSONArray(tag.exclude))
            )
        }
        return array.toString()
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optString(index).trim().takeIf { it.isNotBlank() }
        }
    }
}

object DevicePolicyCodec {
    fun decode(raw: String): List<DevicePolicy> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val mac = obj.optString("mac").lowercase()
                if (mac.isBlank()) return@mapNotNull null
                DevicePolicy(
                    id = obj.optString("id").ifBlank { mac },
                    mac = mac,
                    label = obj.optString("label"),
                    primary = decodeSelector(obj.optJSONObject("primary")),
                    fallback = decodeSelector(obj.optJSONObject("fallback")),
                    maxDelayMs = obj.optInt("maxDelayMs", 0),
                    enabled = obj.optBoolean("enabled", true)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun encode(list: List<DevicePolicy>): String {
        val array = JSONArray()
        list.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("mac", item.mac)
                    .put("label", item.label)
                    .put("primary", encodeSelector(item.primary))
                    .put("fallback", encodeSelector(item.fallback))
                    .put("maxDelayMs", item.maxDelayMs)
                    .put("enabled", item.enabled)
            )
        }
        return array.toString()
    }

    /** 兼容旧格式 {region, protocol}：映射成内置地区/协议标签。 */
    private fun decodeSelector(obj: JSONObject?): DeviceSelector {
        if (obj == null) return DeviceSelector()
        val tagsArray = obj.optJSONArray("tags")
        if (tagsArray != null) {
            val tags = (0 until tagsArray.length()).mapNotNull { index ->
                val item = tagsArray.optJSONObject(index) ?: return@mapNotNull null
                val tagId = item.optString("tagId")
                if (tagId.isBlank()) return@mapNotNull null
                TagSelection(tagId, item.optBoolean("negated", false))
            }
            return DeviceSelector(tags)
        }
        val legacy = mutableListOf<TagSelection>()
        val region = obj.optString("region")
        if (region.isNotBlank()) legacy += TagSelection("__region_$region")
        val protocol = obj.optString("protocol").lowercase()
        if (protocol.isNotBlank()) legacy += TagSelection("__type_$protocol")
        return DeviceSelector(legacy)
    }

    private fun encodeSelector(selector: DeviceSelector): JSONObject {
        val tags = JSONArray()
        selector.tags.forEach { selection ->
            tags.put(
                JSONObject()
                    .put("tagId", selection.tagId)
                    .put("negated", selection.negated)
            )
        }
        return JSONObject().put("tags", tags)
    }
}
