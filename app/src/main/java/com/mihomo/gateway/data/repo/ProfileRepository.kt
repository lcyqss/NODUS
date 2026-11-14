package com.mihomo.gateway.data.repo

import android.content.Context
import android.util.Log
import com.mihomo.gateway.core.CoreInstaller
import com.mihomo.gateway.core.ShellExecutor
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

interface ProfileRepository {
    suspend fun import(url: String): Result<Unit>
    suspend fun reload(): Result<Unit>
    suspend fun installCore(): Result<String>
}

class ProfileRepositoryImpl(
    private val context: Context,
    private val shell: ShellExecutor,
    private val api: MihomoApi,
    private val settings: SettingsStore,
    private val coreInstaller: CoreInstaller,
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

        val body = try {
            val request = Request.Builder()
                .url(trimmed)
                .header("User-Agent", "clash.meta")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("下载失败: HTTP ${response.code}"))
                }
                response.body?.string().orEmpty()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "download failed: ${e.message}")
            return@withContext Result.failure(e)
        }

        if (body.isBlank()) return@withContext Result.failure(IllegalStateException("订阅内容为空"))
        if (!body.contains("proxies:") && !body.contains("proxy-providers:")) {
            return@withContext Result.failure(IllegalStateException("不是 Clash 配置"))
        }

        val file = File(context.cacheDir, "config.yaml")
        file.writeText(body)

        // 原子替换 + 自愈 + 重启内核
        val result = shell.run(
            "cp -f ${file.absolutePath} $base/config.yaml && " +
                "sh $base/ensure_config.sh && sh $base/set-ipver.sh auto",
            60_000
        )
        if (result.code != 0) {
            return@withContext Result.failure(IllegalStateException(result.stdout.take(300)))
        }

        settings.update { it.copy(subscriptionUrl = trimmed) }
        waitReady()
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

    private suspend fun waitReady() {
        repeat(20) {
            if (api.version().isSuccess) return
            kotlinx.coroutines.delay(250)
        }
    }

    companion object {
        private const val TAG = "GW-Repo"
    }
}
