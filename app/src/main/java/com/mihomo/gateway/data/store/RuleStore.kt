package com.mihomo.gateway.data.store

import android.content.Context
import com.mihomo.gateway.model.CustomRule
import com.mihomo.gateway.model.RuleKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 自定义规则持久化：独立 JSON 文件，不写入订阅原文。
 */
class RuleStore(context: Context) {

    private val file = File(context.filesDir, "custom_rules.json")
    private val _rules = MutableStateFlow(load())
    val rules: StateFlow<List<CustomRule>> = _rules.asStateFlow()

    fun snapshot(): List<CustomRule> = _rules.value

    fun add(rule: CustomRule): List<CustomRule> {
        val next = _rules.value + rule
        save(next)
        return next
    }

    fun update(rule: CustomRule): List<CustomRule> {
        val next = _rules.value.map { if (it.id == rule.id) rule else it }
        save(next)
        return next
    }

    fun delete(id: String): List<CustomRule> {
        val next = _rules.value.filterNot { it.id == id }
        save(next)
        return next
    }

    fun move(id: String, up: Boolean): List<CustomRule> {
        val list = _rules.value.toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return list
        val target = if (up) index - 1 else index + 1
        if (target !in list.indices) return list
        val item = list.removeAt(index)
        list.add(target, item)
        save(list)
        return list
    }

    fun newRule(
        kind: RuleKind,
        value: String,
        target: String,
        noResolve: Boolean
    ): CustomRule = CustomRule(
        id = UUID.randomUUID().toString(),
        kind = kind,
        value = value,
        target = target,
        enabled = true,
        noResolve = noResolve
    )

    private fun save(list: List<CustomRule>) {
        val array = JSONArray()
        list.forEach { rule ->
            array.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("kind", rule.kind.wire)
                    .put("value", rule.value)
                    .put("target", rule.target)
                    .put("enabled", rule.enabled)
                    .put("noResolve", rule.noResolve)
                    .put("createdAt", rule.createdAt)
            )
        }
        file.writeText(array.toString())
        _rules.value = list
    }

    private fun load(): List<CustomRule> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val kind = RuleKind.fromWire(obj.optString("kind")) ?: return@mapNotNull null
                CustomRule(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    kind = kind,
                    value = obj.optString("value"),
                    target = obj.optString("target"),
                    enabled = obj.optBoolean("enabled", true),
                    noResolve = obj.optBoolean("noResolve", kind.needsNoResolve),
                    createdAt = obj.optLong("createdAt", 0L)
                )
            }
        }.getOrDefault(emptyList())
    }
}
