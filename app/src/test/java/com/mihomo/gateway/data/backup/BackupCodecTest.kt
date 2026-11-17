package com.mihomo.gateway.data.backup

import com.mihomo.gateway.model.AppSettings
import com.mihomo.gateway.model.CustomRule
import com.mihomo.gateway.model.IpMode
import com.mihomo.gateway.model.RuleKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun rule(
        kind: RuleKind,
        value: String,
        enabled: Boolean = true,
        noResolve: Boolean = kind.needsNoResolve
    ) = CustomRule(
        id = "id-$value",
        kind = kind,
        value = value,
        target = "DIRECT",
        enabled = enabled,
        noResolve = noResolve,
        createdAt = 1L
    )

    @Test
    fun `encode then decode keeps every settings field`() {
        val settings = AppSettings(
            subscriptionUrl = "https://example.com/sub",
            ipMode = IpMode.V6,
            blockIpv6 = false,
            ipv6Gateway = true,
            geoAutoUpdate = true,
            geoUpdateIntervalDays = 30,
            autoStart = false,
            updateIntervalHours = 6,
            subName = "主订阅",
            favoriteNodes = "[\"a\"]",
            subscriptions = "[]",
            activeSubId = "sub-1",
            devicePolicies = "[]",
            nodeTags = "[]",
            conditionTemplates = "[]",
            policyIntervalSec = 120,
            ipFollowIntervalSec = 300
        )
        val rules = listOf(
            rule(RuleKind.DOMAIN_SUFFIX, "ip.sb"),
            rule(RuleKind.IP_CIDR, "10.0.0.0/8", enabled = false)
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(settings, rules)).getOrThrow()

        assertEquals(BackupCodec.VERSION, decoded.version)
        val s = decoded.settings!!
        assertEquals("https://example.com/sub", s.subscriptionUrl)
        assertEquals("V6", s.ipMode)
        assertEquals(false, s.blockIpv6)
        assertEquals(true, s.ipv6Gateway)
        assertEquals(true, s.geoAutoUpdate)
        assertEquals(30, s.geoUpdateIntervalDays)
        assertEquals(false, s.autoStart)
        assertEquals(6, s.updateIntervalHours)
        assertEquals("主订阅", s.subName)
        assertEquals("[\"a\"]", s.favoriteNodes)
        assertEquals("sub-1", s.activeSubId)
        assertEquals("[]", s.devicePolicies)
        assertEquals("[]", s.nodeTags)
        assertEquals("[]", s.conditionTemplates)
        assertEquals(120, s.policyIntervalSec)
        assertEquals(300, s.ipFollowIntervalSec)
        assertEquals("rule", s.runtimeMode)

        assertEquals(2, decoded.rules.size)
        assertTrue(decoded.rulesPresent)
        assertTrue(decoded.rules[0].enabled)
        assertEquals("ip.sb", decoded.rules[0].value)
        assertFalse(decoded.rules[1].enabled)
        assertTrue(decoded.rules[1].noResolve)
    }

    @Test
    fun `runtime mode is preserved in backup`() {
        val encoded = BackupCodec.encode(AppSettings(), emptyList(), runtimeMode = "global")
        val decoded = BackupCodec.decode(encoded).getOrThrow()

        assertEquals("global", decoded.settings!!.runtimeMode)
    }

    @Test
    fun `legacy backup without new fields decodes with nulls`() {
        val legacy = """
            {"version":1,"settings":{"subscriptionUrl":"https://old","subName":"旧订阅"}}
        """.trimIndent()

        val decoded = BackupCodec.decode(legacy).getOrThrow()

        assertEquals(1, decoded.version)
        val s = decoded.settings!!
        assertEquals("https://old", s.subscriptionUrl)
        assertEquals("旧订阅", s.subName)
        // 旧备份没有的字段必须是 null，恢复时才能保留当前值
        assertNull(s.subscriptions)
        assertNull(s.activeSubId)
        assertNull(s.ipMode)
        assertNull(s.blockIpv6)
        assertNull(s.ipv6Gateway)
        assertNull(s.devicePolicies)
        assertNull(s.nodeTags)
        assertNull(s.policyIntervalSec)
        assertNull(s.runtimeMode)
        assertFalse(decoded.rulesPresent)
        assertTrue(decoded.rules.isEmpty())
    }

    @Test
    fun `invalid json is rejected`() {
        val result = BackupCodec.decode("不是 JSON")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("JSON"))
    }

    @Test
    fun `unknown rule kind is skipped and missing enabled defaults to true`() {
        val raw = """
            {"version":2,"settings":{},"rules":[
              {"kind":"NOT-A-RULE","value":"x","target":"DIRECT"},
              {"kind":"DOMAIN","value":"  example.com  ","target":" DIRECT "}
            ]}
        """.trimIndent()

        val decoded = BackupCodec.decode(raw).getOrThrow()

        assertEquals(1, decoded.rules.size)
        val only = decoded.rules.first()
        assertEquals(RuleKind.DOMAIN, only.kind)
        assertEquals("example.com", only.value)
        assertEquals("DIRECT", only.target)
        assertTrue(only.enabled)
    }

    @Test
    fun `active sub name prefers subscription list over cached name`() {
        val subs = JSONObject()
            .let { obj ->
                org.json.JSONArray()
                    .put(
                        JSONObject()
                            .put("id", "sub-1")
                            .put("name", "良心云test")
                            .put("url", "https://example.com")
                    )
                    .toString()
            }

        assertEquals(
            "良心云test",
            BackupCodec.activeSubName(subs, "sub-1", "旧名字")
        )
        // 找不到对应 id 时回退到备份里的 subName
        assertEquals(
            "旧名字",
            BackupCodec.activeSubName(subs, "sub-2", "旧名字")
        )
        assertEquals("兜底", BackupCodec.activeSubName("", "sub-1", "兜底"))
    }
}
