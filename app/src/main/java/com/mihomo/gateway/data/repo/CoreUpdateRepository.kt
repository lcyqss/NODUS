package com.mihomo.gateway.data.repo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

data class CoreUpdateInfo(
    val latest: String,
    val assetName: String,
    val downloadUrl: String
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
        .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890)))
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
            Result.success(CoreUpdateInfo(tag, name, url))
        } catch (e: Exception) {
            Result.failure(e)
        }
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

    private fun client(useProxy: Boolean): OkHttpClient = if (useProxy) proxyClient else directClient

    @Suppress("unused")
    private companion object {
        private const val TAG = "core-update"
    }
}
