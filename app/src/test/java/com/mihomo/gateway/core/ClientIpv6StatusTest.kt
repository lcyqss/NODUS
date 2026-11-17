package com.mihomo.gateway.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientIpv6StatusTest {

    private fun raw(
        gatewayAddr: String = "    inet6 fe80::1/64 scope link",
        block: String = "0",
        gateway: String = "0",
        ra: String = "0",
        upstream: String = "    inet6 2408:8557:3270:da80::1/64 scope global"
    ) = """
        IF=wlan2
$gatewayAddr
        |CONF|
        BLOCK_IPV6=$block
        IPV6_GATEWAY=$gateway
        |RA|
        $ra
        |UP|
$upstream
    """.trimIndent()

    @Test
    fun `both switches off means clients have no ipv6`() {
        val status = ClientIpv6Status.parse(raw(), policyCount = 0)

        assertTrue(status.upstream)
        assertFalse(status.nativeTethering)
        assertFalse(status.proxyActive)
        assertEquals(ClientIpv6Status.Mode.NONE, status.mode)
        assertEquals("无可用 IPv6", status.title)
    }

    @Test
    fun `ula plus ra plus configured means proxied`() {
        val status = ClientIpv6Status.parse(
            raw(
                gatewayAddr = "    inet6 fe80::1/64 scope link\n    inet6 fd00:1234::1/64 scope global",
                gateway = "1",
                ra = "1"
            )
        )

        assertTrue(status.ula)
        assertTrue(status.raRunning)
        assertTrue(status.proxyActive)
        assertEquals(ClientIpv6Status.Mode.PROXY, status.mode)
    }

    @Test
    fun `configured but neither ula nor ra falls back to none`() {
        val status = ClientIpv6Status.parse(raw(gateway = "1", ra = "0"))

        assertFalse(status.proxyActive)
        assertEquals(ClientIpv6Status.Mode.NONE, status.mode)
    }

    @Test
    fun `native tethering wins over blocked state`() {
        val status = ClientIpv6Status.parse(
            raw(
                gatewayAddr = "    inet6 2408:8557:3270:da80:1::1/64 scope global\n" +
                    "    inet6 fe80::1/64 scope link",
                block = "1"
            )
        )

        assertTrue(status.nativeTethering)
        assertEquals(ClientIpv6Status.Mode.NATIVE, status.mode)
    }

    @Test
    fun `blocking without proxy is reported as blocked`() {
        val status = ClientIpv6Status.parse(raw(block = "1"))

        assertTrue(status.blocked)
        assertEquals(ClientIpv6Status.Mode.BLOCKED, status.mode)
    }

    @Test
    fun `detail mentions policy devices only when some are enabled`() {
        val none = ClientIpv6Status.parse(raw(), policyCount = 0)
        assertFalse(none.detail.contains("设备策略"))

        val withPolicies = ClientIpv6Status.parse(raw(), policyCount = 2)
        assertTrue(withPolicies.detail.contains("2 条设备策略"))
    }

    @Test
    fun `missing upstream means no phone ipv6`() {
        val status = ClientIpv6Status.parse(raw(upstream = ""), policyCount = 0)
        assertFalse(status.upstream)
    }
}
