package com.mihomo.gateway.data.update

import com.mihomo.gateway.core.AppConfig
import android.content.Context
import android.util.Log
import com.mihomo.gateway.model.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class AppUpdateRepositoryImpl(
    private val context: Context
) : AppUpdateRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun latest(currentVersion: String): Result<AppUpdateInfo?> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(RELEASES_API)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "NODUS/${currentVersion}")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val message = when (response.code) {
                            404 -> "GitHub 还没有 Release"
                            403 -> "GitHub API 限流"
                            else -> "GitHub HTTP ${response.code}"
                        }
                        return@withContext Result.failure(IllegalStateException(message))
                    }
                    val json = JSONObject(response.body?.string().orEmpty())
                    val tag = json.optString("tag_name")
                    val version = tag.removePrefix("v")
                    if (version.isBlank()) {
                        return@withContext Result.failure(IllegalStateException("Release 缺少 tag"))
                    }
                    if (compareVersion(version, currentVersion) <= 0) {
                        return@withContext Result.success(null)
                    }
                    val assets = json.optJSONArray("assets")
                    var apkAsset: JSONObject? = null
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            val name = asset.optString("name")
                            if (!name.endsWith(".apk", ignoreCase = true)) continue
                            if (apkAsset == null || name.contains("NODUS", ignoreCase = true)) {
                                apkAsset = asset
                            }
                        }
                    }
                    val asset = apkAsset
                        ?: return@withContext Result.failure(IllegalStateException("Release 中没有 APK"))
                    Result.success(
                        AppUpdateInfo(
                            version = version,
                            tag = tag,
                            notes = json.optString("body").orEmpty(),
                            apkUrl = asset.optString("browser_download_url"),
                            size = asset.optLong("size"),
                            digest = asset.optString("digest")
                                .takeIf { it.isNotBlank() && it != "null" }
                                .orEmpty()
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun download(
        info: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(info.apkUrl)
                .header("User-Agent", "NODUS")
                .build()
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "NODUS-${info.version}.apk")
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("下载失败 HTTP ${response.code}")
                    )
                }
                val body = response.body ?: return@withContext Result.failure(
                    IllegalStateException("下载内容为空")
                )
                val total = body.contentLength().takeIf { it > 0 } ?: info.size
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var written = 0L
                        while (input.read(buffer).also { read = it } > 0) {
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                onProgress((written * 100 / total).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
            }
            val digest = info.digest.removePrefix("sha256:").trim()
            if (digest.isNotBlank()) {
                val actual = sha256(target)
                if (!actual.equals(digest, ignoreCase = true)) {
                    target.delete()
                    return@withContext Result.failure(IllegalStateException("APK SHA256 校验失败"))
                }
            }
            Result.success(target)
        } catch (e: Exception) {
            Log.w(TAG, "download failed", e)
            Result.failure(e)
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } > 0) md.update(buffer, 0, read)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "GW-Update"
        val RELEASES_API = AppConfig.RELEASES_API

        /** 语义化版本比较，忽略前缀和预发布后缀。 */
        fun compareVersion(a: String, b: String): Int {
            val left = a.removePrefix("v").split("-", "+").first().split(".").mapNotNull { it.toIntOrNull() }
            val right = b.removePrefix("v").split("-", "+").first().split(".").mapNotNull { it.toIntOrNull() }
            val size = maxOf(left.size, right.size)
            for (i in 0 until size) {
                val l = left.getOrElse(i) { 0 }
                val r = right.getOrElse(i) { 0 }
                if (l != r) return l.compareTo(r)
            }
            return 0
        }
    }
}
