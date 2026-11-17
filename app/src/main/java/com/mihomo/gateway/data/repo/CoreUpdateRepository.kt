package com.mihomo.gateway.data.repo

import com.mihomo.gateway.core.AppConfig
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

data class CoreUpdateInfo(
    val latest: String,
    val assetName: String,
    val downloadUrl: String,
    val sha256: String?
)

interface CoreUpdateRepository {
    suspend fun check(useProxy: Boolean): Result<CoreUpdateInfo>
    suspend fun download(
        info: CoreUpdateInfo,
        useProxy: Boolean,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): Result<File>
}

class CoreUpdateRepositoryImpl(
    private val context: Context
) : CoreUpdateRepository {

    private val directClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()
    private val proxyClient = directClient.newBuilder()
        .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(AppConfig.LOOPBACK, AppConfig.PROXY_PORT)))
        .build()

    override suspend fun check(useProxy: Boolean): Result<CoreUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val client = client(useProxy)
            val latestPage = Request.Builder()
                .url("https://github.com/MetaCubeX/mihomo/releases/latest")
                .header("User-Agent", "Mihomo-Gateway")
                .build()
            val finalUrl = client.newCall(latestPage).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.request.url.toString()
            }
            val tag = Regex("/tag/(v[0-9][^/?#\"]+)")
                .find(finalUrl)?.groupValues?.get(1)
                ?: error("无法识别最新版本")
            val assetsUrl = "https://github.com/MetaCubeX/mihomo/releases/expanded_assets/$tag"
            val assetsHtml = client.newCall(
                Request.Builder()
                    .url(assetsUrl)
                    .header("User-Agent", "Mihomo-Gateway")
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
            val href = Regex("href=\"([^\"]*android-arm64-v8[^\"]*\\.gz)\"")
                .find(assetsHtml)?.groupValues?.get(1)
                ?: error("未找到 Android arm64 内核")
            val url = if (href.startsWith("http")) href else "https://github.com$href"
            val name = href.substringAfterLast('/')
            Result.success(CoreUpdateInfo(tag, name, url, fetchDigest(client, tag, name)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchDigest(client: OkHttpClient, tag: String, assetName: String): String? {
        return runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/MetaCubeX/mihomo/releases/tags/$tag")
                .header("User-Agent", "Mihomo-Gateway")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val root = JSONObject(response.body?.string().orEmpty())
                val assets = root.optJSONArray("assets") ?: return null
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    if (asset.optString("name") == assetName) {
                        return asset.optString("digest")
                            .removePrefix("sha256:")
                            .ifBlank { null }
                    }
                }
                null
            }
        }.getOrNull()
    }

    override suspend fun download(
        info: CoreUpdateInfo,
        useProxy: Boolean,
        onProgress: (Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, "core-update")
        cache.deleteRecursively()
        cache.mkdirs()
        try {
            val gz = File(cache, "mihomo.gz")
            val client = client(useProxy)
            val request = Request.Builder()
                .url(info.downloadUrl)
                .header("User-Agent", "Mihomo-Gateway")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("empty body")
                val total = body.contentLength()
                var done = 0L
                body.byteStream().use { input ->
                    gz.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                }
            }
            if (info.sha256.isNullOrBlank()) error("官方未提供 SHA256")
            if (!sha256(gz).equals(info.sha256, ignoreCase = true)) error("SHA256 校验失败")
            val out = File(cache, "mihomo")
            GZIPInputStream(FileInputStream(gz)).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            if (out.length() < 10_000_000L) error("内核文件大小异常")
            Result.success(out)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun client(useProxy: Boolean): OkHttpClient = if (useProxy) proxyClient else directClient

    @Suppress("unused")
    private companion object {
        private const val TAG = "core-update"
    }
}
