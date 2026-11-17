package com.mihomo.gateway.data.backup

import com.mihomo.gateway.model.AppSettings
import com.mihomo.gateway.model.CustomRule
import com.mihomo.gateway.model.RuleKind
import com.mihomo.gateway.model.SubscriptionCodec
import org.json.JSONArray
import org.json.JSONObject

/**
 * 配置备份的结构与兼容规则。
 *
 * 只负责 JSON 编解码，不碰存储、不启停内核，便于单元测试覆盖：
 * - 新备份保留全部字段（schema v2）
 * - 旧备份缺失的字段解析为 null，恢复时保留当前值
 * - 规则保留 enabled / noResolve 状态
 */
object BackupCodec {

    const val VERSION = 2

    /** 备份里的一条自定义规则。 */
    data class Rule(
        val kind: RuleKind,
        val value: String,
        val target: String,
        val enabled: Boolean,
        val noResolve: Boolean
    )

    /** 备份里出现过的设置字段；null 表示旧备份没有该字段。 */
    data class Settings(
        val subscriptionUrl: String? = null,
        val subName: String? = null,
        val subscriptions: String? = null,
        val activeSubId: String? = null,
        val ipMode: String? = null,
        val blockIpv6: Boolean? = null,
        val ipv6Gateway: Boolean? = null,
        val updateIntervalHours: Int? = null,
        val autoStart: Boolean? = null,
        val geoAutoUpdate: Boolean? = null,
        val geoUpdateIntervalDays: Int? = null,
        val favoriteNodes: String? = null,
        val devicePolicies: String? = null,
        val nodeTags: String? = null,
        val conditionTemplates: String? = null,
        val policyIntervalSec: Int? = null,
        val ipFollowIntervalSec: Int? = null
    )

    data class Backup(
        val version: Int,
        val settings: Settings?,
        val rules: List<Rule>,
        /** 备份是否包含 rules 段；旧备份没有时不应清空现有规则。 */
        val rulesPresent: Boolean
    )

    fun encode(current: AppSettings, rules: List<CustomRule>): String {
        val ruleArray = JSONArray()
        rules.forEach { rule ->
            ruleArray.put(
                JSONObject()
                    .put("kind", rule.kind.wire)
                    .put("value", rule.value)
                    .put("target", rule.target)
                    .put("enabled", rule.enabled)
                    .put("noResolve", rule.noResolve)
            )
        }
        return JSONObject()
            .put("version", VERSION)
            .put(
                "settings",
                JSONObject()
                    .put("subscriptionUrl", current.subscriptionUrl)
                    .put("subName", current.subName)
                    .put("subscriptions", current.subscriptions)
                    .put("activeSubId", current.activeSubId)
                    .put("ipMode", current.ipMode.name)
                    .put("blockIpv6", current.blockIpv6)
                    .put("ipv6Gateway", current.ipv6Gateway)
                    .put("updateIntervalHours", current.updateIntervalHours)
                    .put("autoStart", current.autoStart)
                    .put("geoAutoUpdate", current.geoAutoUpdate)
                    .put("geoUpdateIntervalDays", current.geoUpdateIntervalDays)
                    .put("favoriteNodes", current.favoriteNodes)
                    .put("devicePolicies", current.devicePolicies)
                    .put("nodeTags", current.nodeTags)
                    .put("conditionTemplates", current.conditionTemplates)
                    .put("policyIntervalSec", current.policyIntervalSec)
                    .put("ipFollowIntervalSec", current.ipFollowIntervalSec)
            )
            .put("rules", ruleArray)
            .toString(2)
    }

    fun decode(raw: String): Result<Backup> {
        val root = runCatching { JSONObject(raw) }.getOrElse {
            return Result.failure(IllegalArgumentException("备份内容不是合法 JSON"))
        }
        val version = root.optInt("version", 1)
        val settings = root.optJSONObject("settings")?.let { s ->
            Settings(
                subscriptionUrl = s.stringOrNull("subscriptionUrl"),
                subName = s.stringOrNull("subName"),
                subscriptions = s.stringOrNull("subscriptions"),
                activeSubId = s.stringOrNull("activeSubId"),
                ipMode = s.stringOrNull("ipMode"),
                blockIpv6 = s.booleanOrNull("blockIpv6"),
                ipv6Gateway = s.booleanOrNull("ipv6Gateway"),
                updateIntervalHours = s.intOrNull("updateIntervalHours"),
                autoStart = s.booleanOrNull("autoStart"),
                geoAutoUpdate = s.booleanOrNull("geoAutoUpdate"),
                geoUpdateIntervalDays = s.intOrNull("geoUpdateIntervalDays"),
                favoriteNodes = s.stringOrNull("favoriteNodes"),
                devicePolicies = s.stringOrNull("devicePolicies"),
                nodeTags = s.stringOrNull("nodeTags"),
                conditionTemplates = s.stringOrNull("conditionTemplates"),
                policyIntervalSec = s.intOrNull("policyIntervalSec"),
                ipFollowIntervalSec = s.intOrNull("ipFollowIntervalSec")
            )
        }
        val rulesArray = root.optJSONArray("rules")
        val rules = buildList {
            if (rulesArray != null) {
                for (i in 0 until rulesArray.length()) {
                    val item = rulesArray.optJSONObject(i) ?: continue
                    val kind = RuleKind.fromWire(item.optString("kind")) ?: continue
                    add(
                        Rule(
                            kind = kind,
                            value = item.optString("value").trim(),
                            target = item.optString("target").trim(),
                            enabled = item.optBoolean("enabled", true),
                            noResolve = item.optBoolean("noResolve", kind.needsNoResolve)
                        )
                    )
                }
            }
        }
        return Result.success(
            Backup(
                version = version,
                settings = settings,
                rules = rules,
                rulesPresent = rulesArray != null
            )
        )
    }

    /**
     * 当前订阅名以订阅列表为准；只有旧备份没有列表时才用备份里的 subName。
     */
    fun activeSubName(subscriptions: String, activeSubId: String, fallback: String): String =
        SubscriptionCodec.decode(subscriptions)
            .firstOrNull { it.id == activeSubId }
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: fallback

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key)

    private fun JSONObject.booleanOrNull(key: String): Boolean? =
        if (!has(key) || isNull(key)) null else optBoolean(key)

    private fun JSONObject.intOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)
}
