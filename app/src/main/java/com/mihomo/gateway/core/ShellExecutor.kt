package com.mihomo.gateway.core

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ShellResult(val code: Int, val stdout: String, val stderr: String)

interface ShellExecutor {
    suspend fun run(command: String, timeoutMs: Long = 15_000): ShellResult
}

class ShellExecutorImpl : ShellExecutor {

    private val candidates = listOf(
        "/debug_ramdisk/su",
        "su",
        "/system/bin/su",
        "/system/xbin/su"
    )

    override suspend fun run(command: String, timeoutMs: Long): ShellResult =
        withContext(Dispatchers.IO) {
            val trace = UUID.randomUUID().toString().take(8)
            val startedAt = SystemClock.elapsedRealtime()

            for (su in candidates) {
                try {
                    val process = ProcessBuilder(su, "-c", command)
                        .redirectErrorStream(true)
                        .start()

                    val output = StringBuilder()
                    val reader = Thread {
                        runCatching {
                            process.inputStream.bufferedReader().forEachLine { output.appendLine(it) }
                        }
                    }
                    reader.start()

                    val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                    val cost = SystemClock.elapsedRealtime() - startedAt
                    if (!finished) {
                        process.destroyForcibly()
                        reader.join(1000)
                        record(trace, command, -1, cost)
                        Log.w(TAG, "trace=$trace timeout cmd=$command")
                        return@withContext ShellResult(-1, output.toString(), "timeout")
                    }
                    reader.join(1000)

                    val code = process.exitValue()
                    record(trace, command, code, cost)
                    Log.i(TAG, "trace=$trace code=$code cost=${cost}ms cmd=$command")
                    return@withContext ShellResult(code, output.toString(), "")
                } catch (_: Throwable) {
                    // try next su candidate
                }
            }

            record(trace, command, -1, SystemClock.elapsedRealtime() - startedAt)
            Log.e(TAG, "trace=$trace no root available")
            ShellResult(-1, "", "no root")
        }

    private fun record(trace: String, command: String, code: Int, costMs: Long) {
        ShellLog.add(
            ShellRecord(
                trace = trace,
                command = command.take(200),
                code = code,
                costMs = costMs,
                time = System.currentTimeMillis()
            )
        )
    }

    companion object {
        private const val TAG = "GW-Shell"
    }
}
