package com.mihomo.gateway.model

import org.json.JSONArray
import org.json.JSONObject

/** 节点筛选条件：地区代码（HK/SG/...，空=任意）+ 协议（vless/hysteria2/...，空=任意）。 */
data class DeviceSelector(
    val region: String = "",
    val protocol: String = ""
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
                    primary = obj.optJSONObject("primary")?.let {
                        DeviceSelector(it.optString("region"), it.optString("protocol"))
                    } ?: DeviceSelector(),
                    fallback = obj.optJSONObject("fallback")?.let {
                        DeviceSelector(it.optString("region"), it.optString("protocol"))
                    } ?: DeviceSelector(),
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
                    .put(
                        "primary",
                        JSONObject()
                            .put("region", item.primary.region)
                            .put("protocol", item.primary.protocol)
                    )
                    .put(
                        "fallback",
                        JSONObject()
                            .put("region", item.fallback.region)
                            .put("protocol", item.fallback.protocol)
                    )
                    .put("maxDelayMs", item.maxDelayMs)
                    .put("enabled", item.enabled)
            )
        }
        return array.toString()
    }
}
