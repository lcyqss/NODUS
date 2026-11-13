package com.mihomo.gateway

import java.io.File

object Root {
    const val BASE = "/data/adb/mihomo"

    private val SU_CANDIDATES = listOf(
        "/debug_ramdisk/su",
        "su",
        "/system/bin/su",
        "/system/xbin/su"
    )

    fun run(cmd: String): String {
        for (su in SU_CANDIDATES) {
            try {
                val process = ProcessBuilder(su, "-c", cmd)
                    .redirectErrorStream(true)
                    .start()
                val out = process.inputStream.bufferedReader().readText()
                val code = process.waitFor()
                if (code == 0) return out.ifBlank { "(无输出)" }
                if (out.isNotBlank()) return out
            } catch (_: Throwable) {
            }
        }
        return "无法获取 root：请确认 Magisk 已授权本应用"
    }

    fun run(cmd: String, timeoutMs: Long): String = run(cmd)
}