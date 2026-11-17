package com.mihomo.gateway.data.config

import org.junit.Assert.assertEquals
import org.junit.Test

class DevicePolicyConfigTest {
    @Test
    fun removesMalformedAndDuplicateDnsKeys() {
        val input = """
            dns:
              ipv6: true
                listen: "[::]:1053"
                ipv6: true
                ipv6: false
        """.trimIndent()
        val output = DevicePolicyConfig.normalizeDnsListen(input)
        assertEquals(1, Regex("^\\s*ipv6:", RegexOption.MULTILINE).findAll(output).count())
        assertEquals(1, Regex("^\\s*listen:", RegexOption.MULTILINE).findAll(output).count())
    }
}