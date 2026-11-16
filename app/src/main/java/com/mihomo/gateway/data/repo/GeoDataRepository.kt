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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GeoUpdateResult(
    val version: String,
    val geoIPBytes: Long,
    val geoSiteBytes: Long
)

interface GeoDataRepository {
    suspend fun update(): Result<GeoUpdateResult>
}

class GeoDataRepositoryImpl(
    private val context: Context,
    private val shell: ShellExecutor,
    private val base: String = "/data/adb/mihomo"
) : GeoDataRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val updateMutex = Mutex()

    override suspend fun update(): Result<GeoUpdateResult> = updateMutex.withLock {
        updateLocked()
    }

    private suspend fun updateLocked(): Result<GeoUpdateResult> = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, "geo-update")
        cache.deleteRecursively()
        cache.mkdirs()
        try {
            val geoIP = download(GEOIP_URL, File(cache, "GeoIP.dat"))
            val geoSite = download(GEOSITE_URL, File(cache, "GeoSite.dat"))
            if (geoIP.length() < MIN_SIZE || geoSite.length() < MIN_SIZE) {
                return@withContext Result.failure(IllegalStateException("Geo 文件大小异常"))
            }
            val version = "manual " + SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val c = cache.absolutePath
            val command = """
                [ -f $base/GeoIP.dat ] && cp -f $base/GeoIP.dat $base/GeoIP.dat.bak
                [ -f $base/GeoSite.dat ] && cp -f $base/GeoSite.dat $base/GeoSite.dat.bak
                cp -f $c/GeoIP.dat $base/GeoIP.dat.new && mv -f $base/GeoIP.dat.new $base/GeoIP.dat
                cp -f $c/GeoSite.dat $base/GeoSite.dat.new && mv -f $base/GeoSite.dat.new $base/GeoSite.dat
                chmod 644 $base/GeoIP.dat $base/GeoSite.dat
                echo "$version" > $base/.geo_version
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

    private fun download(url: String, out: File): File {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mihomo-Gateway")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("empty body")
            body.byteStream().use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (out.length() < MIN_SIZE) error("文件过小: ${out.name}")
        return out
    }

    companion object {
        private const val GEOIP_URL =
            "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.dat"
        private const val GEOSITE_URL =
            "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat"
        private const val MIN_SIZE = 1_000_000L
    }
}
