package com.mihomo.gateway.core

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

data class ShellResult(val code: Int, val stdout: String, val stderr: String)

interface ShellExecutor {
    suspend fun run(command: String, timeoutMs: Long = 15_000): ShellResult
}

/**
 * Root shell executor.
 *
 * Keeps one `su -c sh` session alive and reuses it for subsequent commands.
 * This avoids spawning a new root process (and a new superuser prompt) for
 * every polling tick.
 */
class ShellExecutorImpl : ShellExecutor {

    private val candidates = listOf(
        "/debug_ramdisk/su",
        "su",
        "/system/bin/su",
        "/system/xbin/su"
    )

    private val sessionMutex = Mutex()
    private var session: Session? = null

    override suspend fun run(command: String, timeoutMs: Long): ShellResult =
        withContext(Dispatchers.IO) {
            sessionMutex.withLock {
                val active = ensureSession()
                if (active == null) {
                    return@withLock runOneShot(command, timeoutMs)
                }
                execute(active, command, timeoutMs)
            }
        }

    private suspend fun ensureSession(): Session? {
        session?.let { if (it.isAlive()) return it else it.close() }
        session = null
        for (su in candidates) {
            val started = startSession(su) ?: continue
            session = started
            return started
        }
        return null
    }

    private suspend fun startSession(su: String): Session? {
        val process = try {
            ProcessBuilder(su, "-c", "sh")
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            return null
        }
        val writer = try {
            BufferedWriter(OutputStreamWriter(process.outputStream))
        } catch (_: Exception) {
            process.destroyForcibly()
            return null
        }
        val queue = LinkedBlockingQueue<String>()
        val readerThread = Thread {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                    for (line in lines) queue.offer(line)
                }
            } catch (_: Exception) {
                // EOF/closed
            } finally {
                queue.offer(EOF)
            }
        }.apply {
            isDaemon = true
            name = "GW-RootShell"
            start()
        }
        val session = Session(process, writer, queue, readerThread)
        try {
            writer.write("export PATH=/system/bin:/system/xbin:/data/adb/mihomo:\$PATH\n")
            writer.write("echo __GW_READY__\n")
            writer.flush()
            val deadline = SystemClock.elapsedRealtime() + 15_000
            while (SystemClock.elapsedRealtime() < deadline) {
                if (!currentCoroutineContext().isActive) {
                    session.close()
                    throw CancellationException("root shell startup cancelled")
                }
                val line = queue.poll(100, TimeUnit.MILLISECONDS)
                when {
                    line == null -> continue
                    line == EOF -> break
                    line.trim() == "__GW_READY__" -> return session
                }
            }
        } catch (_: Exception) {
            // fall through
        }
        session.close()
        return null
    }

    private suspend fun execute(session: Session, command: String, timeoutMs: Long): ShellResult {
        val trace = UUID.randomUUID().toString().take(8)
        val startedAt = SystemClock.elapsedRealtime()
        val marker = "__GW_DONE_${UUID.randomUUID().toString().replace("-", "")}__"
        val output = StringBuilder()
        try {
            session.writer.write("{\n$command\n} </dev/null 2>&1\n")
            session.writer.write("printf '\\n${marker}%d\\n' \"\$?\"\n")
            session.writer.flush()
        } catch (e: Exception) {
            session.close()
            this.session = null
            record(trace, command, -1, SystemClock.elapsedRealtime() - startedAt)
            return ShellResult(-1, "", e.message ?: "shell write failed")
        }

        val timeoutAt = startedAt + timeoutMs
        while (true) {
            if (!currentCoroutineContext().isActive) {
                session.close()
                this.session = null
                throw CancellationException("shell command cancelled")
            }
            val remaining = timeoutAt - SystemClock.elapsedRealtime()
            if (remaining <= 0) {
                session.close()
                this.session = null
                record(trace, command, -1, SystemClock.elapsedRealtime() - startedAt)
                return ShellResult(-1, output.toString(), "timeout")
            }
            val line = try {
                session.queue.poll(minOf(remaining, 250L), TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                null
            }
            if (line == null) continue
            if (line == EOF) {
                session.close()
                this.session = null
                record(trace, command, -1, SystemClock.elapsedRealtime() - startedAt)
                return ShellResult(-1, output.toString(), "shell closed")
            }
            val trimmed = line.trimEnd('\r')
            if (trimmed.startsWith(marker)) {
                val code = trimmed.substring(marker.length).toIntOrNull() ?: -1
                val cost = SystemClock.elapsedRealtime() - startedAt
                record(trace, command, code, cost)
                Log.i(TAG, "trace=$trace code=$code cost=${cost}ms cmd=${command.take(160)}")
                return ShellResult(code, output.toString(), "")
            }
            if (output.length < MAX_OUTPUT) output.appendLine(line)
        }
    }

    private fun runOneShot(command: String, timeoutMs: Long): ShellResult {
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
                        process.inputStream.bufferedReader().forEachLine {
                            if (output.length < MAX_OUTPUT) output.appendLine(it)
                        }
                    }
                }
                reader.start()
                val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                val cost = SystemClock.elapsedRealtime() - startedAt
                if (!finished) {
                    process.destroyForcibly()
                    reader.join(1000)
                    record(trace, command, -1, cost)
                    return ShellResult(-1, output.toString(), "timeout")
                }
                reader.join(1000)
                val code = process.exitValue()
                record(trace, command, code, cost)
                Log.i(TAG, "trace=$trace code=$code cost=${cost}ms cmd=${command.take(160)}")
                return ShellResult(code, output.toString(), "")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // try next
            }
        }
        record(trace, command, -1, SystemClock.elapsedRealtime() - startedAt)
        return ShellResult(-1, "", "no root")
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

    private class Session(
        val process: Process,
        val writer: BufferedWriter,
        val queue: LinkedBlockingQueue<String>,
        private val readerThread: Thread
    ) {
        fun isAlive(): Boolean = process.isAlive

        fun close() {
            runCatching { writer.close() }
            process.destroy()
            if (process.isAlive) process.destroyForcibly()
            readerThread.interrupt()
        }
    }

    companion object {
        private const val TAG = "GW-Shell"
        private const val MAX_OUTPUT = 256_000
        private const val EOF = "__GW_EOF__"
    }
}
