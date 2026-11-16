package com.mihomo.gateway.data.repo

import android.content.Context
import android.util.Log
import com.mihomo.gateway.core.CoreInstaller
import com.mihomo.gateway.core.ShellExecutor
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.policy.DevicePolicyApplier
import com.mihomo.gateway.data.store.SettingsStore
import com.mihomo.gateway.model.ProviderMeta
import com.mihomo.gateway.model.SubscriptionCodec
import com.mihomo.gateway.model.SubscriptionProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import java.util.concurrent.TimeUnit

data class ProfileFetch(val nodeCount: Int, val providers: List<ProviderMeta>)

interface ProfileRepository {
    /** 下载并写入该订阅的缓存，返回解析出的节点数与 provider 元数据。 */
    suspend fun fetchToCache(profile: SubscriptionProfile): Result<ProfileFetch>

    /** 用该订阅的缓存配置热重载内核，并把它设为当前订阅。 */
    suspend fun installFromCache(profile: SubscriptionProfile): Result<Unit>

    /** 优先用缓存切换；缓存缺失或按间隔已过期时先下载。 */
    suspend fun switchTo(profile: SubscriptionProfile): Result<Unit>

    /** 按各自间隔更新所有订阅：非当前订阅只更新缓存，当前订阅额外热重载。 */
    suspend fun autoUpdateDue(): Result<Int>

    /** 读取缓存里的 provider 元数据（只读）。 */
    suspend fun readProviders(profile: SubscriptionProfile): List<ProviderMeta>

    suspend fun deleteCache(profile: SubscriptionProfile)

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
    private val devicePolicies: DevicePolicyApplier,
    private val base: String = "/data/adb/mihomo"
) : ProfileRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** 串行化配置写入：手动切换与后台自动更新可能同时触发。 */
    private val installMutex = Mutex()

    private val fetchLocks = Array(16) { Mutex() }

    override suspend fun fetchToCache(profile: SubscriptionProfile): Result<ProfileFetch> =
        withContext(Dispatchers.IO) {
            val lock = fetchLocks[Math.floorMod(profile.id.hashCode(), fetchLocks.size)]
            lock.withLock { fetchToCacheLocked(profile) }
        }

    private suspend fun fetchToCacheLocked(profile: SubscriptionProfile): Result<ProfileFetch> {
        val url = profile.url.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("请输入 http(s) 订阅链接"))
        }
        val body = download(url).getOrElse { return Result.failure(it) }
        val meta = runCatching { parseMeta(body) }.getOrElse { ProfileFetch(0, emptyList()) }
        val cache = cacheFile(profile)
        runCatching {
            cache.parentFile?.mkdirs()
            val tmp = File(cache.parentFile, "${cache.name}.tmp")
            tmp.writeText(body)
            if (cache.exists()) cache.delete()
            if (!tmp.renameTo(cache)) {
                tmp.copyTo(cache, overwrite = true)
                tmp.delete()
            }
        }.onFailure { return Result.failure(it) }
        updateProfileMeta(profile.id) {
            it.copy(lastUpdateAt = System.currentTimeMillis(), nodeCount = meta.nodeCount)
        }
        return Result.success(meta)
    }

    override suspend fun installFromCache(profile: SubscriptionProfile): Result<Unit> =
        withContext(Dispatchers.IO) {
            val cache = cacheFile(profile)
            if (!cache.exists()) {
                return@withContext Result.failure(IllegalStateException("没有可用的缓存配置，请先更新"))
            }
            val installed = install(cache)
            if (installed.isSuccess) setActive(profile.id)
            installed
        }

    override suspend fun switchTo(profile: SubscriptionProfile): Result<Unit> =
        withContext(Dispatchers.IO) {
            val cache = cacheFile(profile)
            val stale = profile.autoUpdateMinutes > 0 &&
                System.currentTimeMillis() - profile.lastUpdateAt >
                profile.autoUpdateMinutes * 60_000L
            if (cache.exists() && !stale) {
                installFromCache(profile).onSuccess { return@withContext Result.success(Unit) }
            }
            fetchToCache(profile).getOrElse { return@withContext Result.failure(it) }
            installFromCache(profile)
        }

    override suspend fun autoUpdateDue(): Result<Int> = withContext(Dispatchers.IO) {
        val current = settings.settings.first()
        val list = SubscriptionCodec.decode(current.subscriptions)
        var updated = 0
        var lastError: Throwable? = null
        for (profile in list) {
            if (profile.autoUpdateMinutes <= 0) continue
            val due = System.currentTimeMillis() - profile.lastUpdateAt >=
                profile.autoUpdateMinutes * 60_000L
            if (!due) continue
            val fetched = fetchToCache(profile)
            if (fetched.isFailure) {
                lastError = fetched.exceptionOrNull()
                continue
            }
            updated++
            if (profile.id == current.activeSubId) {
                installFromCache(profile).onFailure { lastError = it }
            }
        }
        if (updated == 0 && lastError != null) {
            Result.failure(lastError ?: IllegalStateException("自动更新失败"))
        } else {
            Result.success(updated)
        }
    }

    override suspend fun readProviders(profile: SubscriptionProfile): List<ProviderMeta> =
        withContext(Dispatchers.IO) {
            val cache = cacheFile(profile)
            if (!cache.exists()) return@withContext emptyList()
            runCatching { parseMeta(cache.readText()).providers }.getOrDefault(emptyList())
        }

    override suspend fun deleteCache(profile: SubscriptionProfile) {
        withContext(Dispatchers.IO) { cacheFile(profile).delete() }
    }

    override suspend fun reload(): Result<Unit> = withContext(Dispatchers.IO) {
        val result = shell.run("sh $base/set-ipver.sh ${currentIpModeArg()}", 60_000)
        if (result.code == 0) {
            if (coreRunning()) waitReady()
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "download failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun cacheFile(profile: SubscriptionProfile): File =
        File(File(context.filesDir, "profiles"), "${profile.id}.yaml")

    private fun parseMeta(body: String): ProfileFetch {
        val root = Yaml(SafeConstructor()).load<Any?>(body) as? Map<*, *>
            ?: return ProfileFetch(0, emptyList())
        val nodeCount = (root["proxies"] as? List<*>)?.size ?: 0
        val providers = (root["proxy-providers"] as? Map<*, *>)?.entries?.mapNotNull { entry ->
            val value = entry.value as? Map<*, *> ?: return@mapNotNull null
            ProviderMeta(
                name = entry.key?.toString().orEmpty(),
                type = value["type"]?.toString().orEmpty().ifBlank { "http" },
                url = value["url"]?.toString().orEmpty()
            )
        } ?: emptyList()
        return ProfileFetch(nodeCount, providers)
    }

    private suspend fun updateProfileMeta(
        id: String,
        block: (SubscriptionProfile) -> SubscriptionProfile
    ) {
        settings.update { current ->
            val list = SubscriptionCodec.decode(current.subscriptions)
                .map { if (it.id == id) block(it) else it }
            current.copy(subscriptions = SubscriptionCodec.encode(list))
        }
    }

    private suspend fun setActive(id: String) {
        settings.update { current ->
            val list = SubscriptionCodec.decode(current.subscriptions)
            val name = list.firstOrNull { it.id == id }?.name ?: current.subName
            current.copy(activeSubId = id, subName = name)
        }
    }

    /** 原子替换：先备份，切换订阅配置并保留用户选择的地址族；不可用则回滚。 */
    private suspend fun install(file: File): Result<Unit> = installMutex.withLock {
        val backup = "$base/config.yaml.bak.auto"
        val running = coreRunning()
        val modeArg = currentIpModeArg()

        val prepare = shell.run("cp -f $base/config.yaml $backup", 20_000)
        if (prepare.code != 0) {
            return@withLock Result.failure(IllegalStateException("备份配置失败"))
        }

        val replace = shell.run(
            "cp -f ${file.absolutePath} $base/config.yaml && " +
                "sh $base/ensure_config.sh && sh $base/set-ipver.sh $modeArg",
            90_000
        )
        if (replace.code != 0) {
            rollback(backup, modeArg, running)
            return@withLock Result.failure(IllegalStateException("应用配置失败: ${replace.stdout.take(200)}"))
        }

        // 内核运行中才需要等 API 就绪；停止状态下只落盘，等下次启动生效
        if (running && !waitReady()) {
            rollback(backup, modeArg, running)
            return@withLock Result.failure(IllegalStateException("新配置无法启动，已回滚"))
        }

        // 先写自定义规则，再写设备策略（设备规则必须排在自定义规则之前）
        rules.injectOnly().onFailure { Log.w(TAG, "inject rules failed: ${it.message}") }
        devicePolicies.applyToConfig()
            .onFailure { Log.w(TAG, "apply device policy failed: ${it.message}") }

        // 两处注入都只改文件：运行中统一热重载一次，失败回滚
        if (running) {
            val reload = api.reloadConfigs()
            if (reload.isFailure) {
                rollback(backup, modeArg, running)
                return@withLock Result.failure(
                    IllegalStateException("配置重载失败，已回滚: ${reload.exceptionOrNull()?.message}")
                )
            }
        }
        Result.success(Unit)
    }

    private suspend fun rollback(backup: String, modeArg: String, wasRunning: Boolean) {
        devicePolicies.invalidateAppliedState()
        shell.run("cp -f $backup $base/config.yaml && sh $base/set-ipver.sh $modeArg", 90_000)
        if (wasRunning) waitReady()
    }

    /** 读取 gateway.conf 中生效的地址族，切换订阅时保持用户选择，不再强制 auto。 */
    private suspend fun currentIpModeArg(): String {
        val text = shell.run("cat $base/gateway.conf 2>/dev/null").stdout
        val value = text.lineSequence()
            .firstOrNull { it.startsWith("IP_MODE=") }
            ?.substringAfter('=')
            ?.trim()
            ?.lowercase()
        return when (value) {
            "v6", "v4", "dual", "v6-only", "v4-only" -> value
            else -> "auto"
        }
    }

    private suspend fun coreRunning(): Boolean =
        shell.run("pidof mihomo >/dev/null 2>&1 && echo yes || echo no").stdout.trim() == "yes"

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
