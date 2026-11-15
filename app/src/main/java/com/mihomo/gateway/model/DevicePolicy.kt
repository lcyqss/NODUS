package com.mihomo.gateway.model

import org.json.JSONArray
import org.json.JSONObject

/** 策略条件的布尔表达式：标签引用 + AND/OR/NOT。 */
sealed interface TagExpr {
    data class Ref(val tagId: String, val negated: Boolean = false) : TagExpr
    data class And(val items: List<TagExpr>) : TagExpr
    data class Or(val items: List<TagExpr>) : TagExpr
    data class Not(val item: TagExpr) : TagExpr
}

/** 设备策略的出口条件；expr 为空表示任意节点。 */
data class DeviceSelector(
    val expr: TagExpr? = null
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
    val builtinKind: String? = null,
    val expr: TagExpr? = null
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
                    exclude = obj.optJSONArray("exclude").toStringList(),
                    expr = obj.optJSONObject("expr")?.let { DevicePolicyCodec.decodeExpr(it) }
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
                    .put("expr", tag.expr?.let { DevicePolicyCodec.encodeExpr(it) })
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

    /** 兼容两种旧格式：tags 列表（AND）与 {region, protocol}。 */
    private fun decodeSelector(obj: JSONObject?): DeviceSelector {
        if (obj == null) return DeviceSelector()
        obj.optJSONObject("expr")?.let { return DeviceSelector(decodeExpr(it)) }

        val tagsArray = obj.optJSONArray("tags")
        if (tagsArray != null) {
            val refs = (0 until tagsArray.length()).mapNotNull { index ->
                val item = tagsArray.optJSONObject(index) ?: return@mapNotNull null
                val tagId = item.optString("tagId")
                if (tagId.isBlank()) null else TagExpr.Ref(tagId, item.optBoolean("negated", false))
            }
            return DeviceSelector(if (refs.isEmpty()) null else TagExpr.And(refs))
        }

        val refs = mutableListOf<TagExpr>()
        val region = obj.optString("region")
        if (region.isNotBlank()) refs += TagExpr.Ref("__region_$region")
        val protocol = obj.optString("protocol").lowercase()
        if (protocol.isNotBlank()) refs += TagExpr.Ref("__type_$protocol")
        return DeviceSelector(if (refs.isEmpty()) null else TagExpr.And(refs))
    }

    private fun encodeSelector(selector: DeviceSelector): JSONObject =
        JSONObject().put("expr", selector.expr?.let { encodeExpr(it) })

    fun encodeExpr(expr: TagExpr): JSONObject = when (expr) {
        is TagExpr.Ref -> JSONObject()
            .put("type", "ref")
            .put("tagId", expr.tagId)
            .put("negated", expr.negated)

        is TagExpr.And -> JSONObject()
            .put("type", "and")
            .put("items", JSONArray(expr.items.map { encodeExpr(it) }))

        is TagExpr.Or -> JSONObject()
            .put("type", "or")
            .put("items", JSONArray(expr.items.map { encodeExpr(it) }))

        is TagExpr.Not -> JSONObject()
            .put("type", "not")
            .put("item", encodeExpr(expr.item))
    }

    fun decodeExpr(obj: JSONObject): TagExpr? = when (obj.optString("type")) {
        "ref" -> obj.optString("tagId")
            .takeIf { it.isNotBlank() }
            ?.let { TagExpr.Ref(it, obj.optBoolean("negated", false)) }

        "and" -> obj.optJSONArray("items")?.let { array ->
            val items = (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { decodeExpr(it) }
            }
            if (items.isEmpty()) null else TagExpr.And(items)
        }

        "or" -> obj.optJSONArray("items")?.let { array ->
            val items = (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { decodeExpr(it) }
            }
            if (items.isEmpty()) null else TagExpr.Or(items)
        }

        "not" -> obj.optJSONObject("item")?.let { decodeExpr(it) }?.let { TagExpr.Not(it) }
        else -> null
    }
}
