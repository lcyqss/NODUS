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
                    if (!finished) {
                        process.destroyForcibly()
                        reader.join(1000)
                        Log.w(TAG, "trace=$trace timeout cmd=$command")
                        return@withContext ShellResult(-1, output.toString(), "timeout")
                    }
                    reader.join(1000)

                    Log.i(
                        TAG,
                        "trace=$trace code=${process.exitValue()} cost=${SystemClock.elapsedRealtime() - startedAt}ms cmd=$command"
                    )
                    return@withContext ShellResult(process.exitValue(), output.toString(), "")
                } catch (_: Throwable) {
                    // try next su candidate
                }
            }

            Log.e(TAG, "trace=$trace no root available")
            ShellResult(-1, "", "no root")
        }

    companion object {
        private const val TAG = "GW-Shell"
    }
}
