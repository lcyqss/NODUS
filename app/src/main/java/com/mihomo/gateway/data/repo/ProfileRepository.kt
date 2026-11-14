package com.mihomo.gateway.data.repo

import android.content.Context
import android.util.Log
import com.mihomo.gateway.core.CoreInstaller
import com.mihomo.gateway.core.ShellExecutor
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

interface ProfileRepository {
    suspend fun import(url: String): Result<Unit>

    /** 按已保存的订阅 URL 自动更新；先下载校验，再原子替换，失败回滚。 */
    suspend fun autoUpdate(): Result<Unit>

    suspend fun reload(): Result<Unit>
    suspend fun installCore(): Result<String>
}

class ProfileRepositoryImpl(
    private val context: Context,
    private val shell: ShellExecutor,
    private val api: MihomoApi,
    private val settings: SettingsStore,
    private val coreInstaller: CoreInstaller,
    private val rules: RuleRepository,
    private val base: String = "/data/adb/mihomo"
) : ProfileRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun import(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("请输入 http(s) 订阅链接"))
        }
        val body = download(trimmed).getOrElse { return@withContext Result.failure(it) }
        val file = writeCache(body).getOrElse { return@withContext Result.failure(it) }

        val installed = install(file)
        if (installed.isFailure) return@withContext installed

        val count = api.proxies().getOrNull()?.count { !it.isGroup } ?: 0
        settings.update {
            it.copy(
                subscriptionUrl = trimmed,
                lastUpdateAt = System.currentTimeMillis(),
                nodeCount = count
            )
        }
        Result.success(Unit)
    }

    override suspend fun autoUpdate(): Result<Unit> = withContext(Dispatchers.IO) {
        val current = settings.settings.first()
        val url = current.subscriptionUrl.trim()
        if (url.isBlank()) return@withContext Result.failure(IllegalStateException("未设置订阅"))

        val body = download(url).getOrElse { return@withContext Result.failure(it) }
        val file = writeCache(body).getOrElse { return@withContext Result.failure(it) }

        val installed = install(file)
        if (installed.isFailure) return@withContext installed

        val count = api.proxies().getOrNull()?.count { !it.isGroup } ?: 0
        settings.update {
            it.copy(lastUpdateAt = System.currentTimeMillis(), nodeCount = count)
        }
        Result.success(Unit)
    }

    override suspend fun reload(): Result<Unit> = withContext(Dispatchers.IO) {
        val result = shell.run("sh $base/set-ipver.sh auto", 60_000)
        if (result.code == 0) {
            waitReady()
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(result.stdout.take(300)))
        }
    }

    override suspend fun installCore(): Result<String> = coreInstaller.ensure()

    // ---------- 内部 ----------

    private suspend fun download(url: String): Result<String> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "clash.meta")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IllegalStateException("下载失败: HTTP ${response.code}"))
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return Result.failure(IllegalStateException("订阅内容为空"))
                if (!body.contains("proxies:") && !body.contains("proxy-providers:")) {
                    return Result.failure(IllegalStateException("不是 Clash 配置"))
                }
                Result.success(body)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "download failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun writeCache(body: String): Result<File> = runCatching {
        File(context.cacheDir, "config.yaml").apply { writeText(body) }
    }

    /** 原子替换：先备份，替换并重启；不可用则回滚。 */
    private suspend fun install(file: File): Result<Unit> {
        val backup = "$base/config.yaml.bak.auto"
        val prepare = shell.run("cp -f $base/config.yaml $backup", 20_000)
        if (prepare.code != 0) {
            return Result.failure(IllegalStateException("备份配置失败"))
        }

        val replace = shell.run(
            "cp -f ${file.absolutePath} $base/config.yaml && " +
                "sh $base/ensure_config.sh && sh $base/set-ipver.sh auto",
            90_000
        )
        if (replace.code != 0) {
            rollback(backup)
            return Result.failure(IllegalStateException("应用配置失败: ${replace.stdout.take(200)}"))
        }

        if (!waitReady()) {
            rollback(backup)
            return Result.failure(IllegalStateException("新配置无法启动，已回滚"))
        }

        // 重新注入自定义规则（订阅原文不含）
        rules.apply().onFailure { Log.w(TAG, "inject rules failed: ${it.message}") }
        return Result.success(Unit)
    }

    private suspend fun rollback(backup: String) {
        shell.run("cp -f $backup $base/config.yaml && sh $base/set-ipver.sh auto", 90_000)
        waitReady()
    }

    private suspend fun waitReady(): Boolean {
        repeat(20) {
            if (api.version().isSuccess) return true
            delay(250)
        }
        return false
    }

    companion object {
        private const val TAG = "GW-Repo"
    }
}
