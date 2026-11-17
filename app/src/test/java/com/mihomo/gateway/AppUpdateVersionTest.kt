package com.mihomo.gateway.data.update

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdateVersionTest {
    @Test
    fun comparesSemanticVersions() {
        assertEquals(0, AppUpdateRepositoryImpl.compareVersion("v2.61.9", "2.61.9"))
        assertEquals(1, AppUpdateRepositoryImpl.compareVersion("2.62.0", "2.61.9"))
        assertEquals(-1, AppUpdateRepositoryImpl.compareVersion("2.61.9", "2.62.0"))
        assertEquals(1, AppUpdateRepositoryImpl.compareVersion("2.62", "2.61.9"))
        assertEquals(0, AppUpdateRepositoryImpl.compareVersion("2.61.9-beta", "2.61.9"))
    }
}