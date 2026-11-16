package com.mihomo.gateway.data.repo

import android.content.Context
import com.mihomo.gateway.core.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GeoUpdateResult(
    val version: String,
    val geoIPBytes: Long,
    val geoSiteBytes: Long,
    val unchanged: Boolean = false
)

interface GeoDataRepository {
    suspend fun update(
        useProxy: Boolean = false,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): Result<GeoUpdateResult>
}

class GeoDataRepositoryImpl(
    private val context: Context,
    private val shell: ShellExecutor,
    private val base: String = "/data/adb/mihomo"
) : GeoDataRepository {

    private val directClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val proxyClient = directClient.newBuilder()
        .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890)))
        .build()

    private val updateMutex = Mutex()

    override suspend fun update(
        useProxy: Boolean,
        onProgress: (Long, Long) -> Unit
    ): Result<GeoUpdateResult> = updateMutex.withLock {
        updateLocked(useProxy, onProgress)
    }

    private suspend fun updateLocked(
        useProxy: Boolean,
        onProgress: (Long, Long) -> Unit
    ): Result<GeoUpdateResult> = withContext(Dispatchers.IO) {
        val client = if (useProxy) proxyClient else directClient
        val cache = File(context.cacheDir, "geo-update")
        cache.deleteRecursively()
        cache.mkdirs()
        try {
            val oldEtags = readStoredEtags()
            val newEtags = listOf(
                headEtag(client, GEOIP_URL),
                headEtag(client, GEOSITE_URL)
            )
            if (oldEtags.size == 2 && oldEtags == newEtags && oldEtags.all { it.isNotBlank() }) {
                return@withContext Result.success(
                    GeoUpdateResult("已是最新", 0, 0, unchanged = true)
                )
            }

            val total = contentLength(client, GEOIP_URL) + contentLength(client, GEOSITE_URL)
            var done = 0L
            val geoIP = download(client, GEOIP_URL, File(cache, "GeoIP.dat")) { n ->
                done += n
                onProgress(done, total)
            }
            val geoSite = download(client, GEOSITE_URL, File(cache, "GeoSite.dat")) { n ->
                done += n
                onProgress(done, total)
            }
            if (geoIP.length() < MIN_SIZE || geoSite.length() < MIN_SIZE) {
                return@withContext Result.failure(IllegalStateException("Geo 文件大小异常"))
            }
            val expectedIP = fetchChecksum(client, "$GEOIP_URL.sha256sum")
            val expectedSite = fetchChecksum(client, "$GEOSITE_URL.sha256sum")
            if (expectedIP.isBlank() || expectedSite.isBlank()) {
                return@withContext Result.failure(IllegalStateException("官方未提供 SHA256"))
            }
            if (!sha256(geoIP).equals(expectedIP, ignoreCase = true) ||
                !sha256(geoSite).equals(expectedSite, ignoreCase = true)
            ) {
                return@withContext Result.failure(IllegalStateException("SHA256 校验失败"))
            }

            val version = "manual " + SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            File(cache, ".geo_etag").writeText(newEtags.joinToString("\n"))
            File(cache, ".geo_sha256").writeText(expectedIP + "\n" + expectedSite)

            val c = cache.absolutePath
            val command = """
                [ -f $base/GeoIP.dat ] && cp -f $base/GeoIP.dat $base/GeoIP.dat.bak
                [ -f $base/GeoSite.dat ] && cp -f $base/GeoSite.dat $base/GeoSite.dat.bak
                cp -f $c/GeoIP.dat $base/GeoIP.dat.new && mv -f $base/GeoIP.dat.new $base/GeoIP.dat
                cp -f $c/GeoSite.dat $base/GeoSite.dat.new && mv -f $base/GeoSite.dat.new $base/GeoSite.dat
                chmod 644 $base/GeoIP.dat $base/GeoSite.dat
                echo "$version" > $base/.geo_version
                cp -f $c/.geo_etag $base/.geo_etag
                cp -f $c/.geo_sha256 $base/.geo_sha256
                echo ok
            """.trimIndent()
            val result = shell.run(command, 60_000)
            if (result.code != 0) {
                shell.run(
                    "[ -f $base/GeoIP.dat.bak ] && cp -f $base/GeoIP.dat.bak $base/GeoIP.dat; " +
                        "[ -f $base/GeoSite.dat.bak ] && cp -f $base/GeoSite.dat.bak $base/GeoSite.dat",
                    20_000
                )
                return@withContext Result.failure(
                    IllegalStateException("Geo 替换失败: ${result.stdout.take(200)}")
                )
            }
            Result.success(GeoUpdateResult(version, geoIP.length(), geoSite.length()))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            cache.deleteRecursively()
        }
    }

    private suspend fun readStoredEtags(): List<String> =
        shell.run("cat $base/.geo_etag 2>/dev/null", 10_000)
            .stdout.lines().map { it.trim() }.filter { it.isNotBlank() }

    private fun headEtag(client: OkHttpClient, url: String): String {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Mihomo-Gateway")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ""
                response.header("ETag") ?: response.header("Last-Modified") ?: ""
            }
        }.getOrDefault("")
    }

    private fun contentLength(client: OkHttpClient, url: String): Long {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Mihomo-Gateway")
                .build()
            client.newCall(request).execute().use { response ->
                response.header("Content-Length")?.toLongOrNull()
                    ?: response.body?.contentLength() ?: 0L
            }
        }.getOrDefault(0L)
    }

    private fun download(
        client: OkHttpClient,
        url: String,
        out: File,
        onBytes: (Long) -> Unit
    ): File {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mihomo-Gateway")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("empty body")
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        onBytes(n.toLong())
                    }
                }
            }
        }
        if (out.length() < MIN_SIZE) error("文件过小: ${out.name}")
        return out
    }

    private fun fetchChecksum(client: OkHttpClient, url: String): String {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mihomo-Gateway")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ""
                Regex("[0-9a-fA-F]{64}").find(response.body?.string().orEmpty())
                    ?.value.orEmpty()
            }
        }.getOrDefault("")
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

    companion object {
        private const val GEOIP_URL =
            "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.dat"
        private const val GEOSITE_URL =
            "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat"
        private const val MIN_SIZE = 1_000_000L
    }
}
