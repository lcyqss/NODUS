package com.mihomo.gateway

import android.content.Context
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

object Subscriptions {

    private val client = OkHttpClient()

    fun import(context: Context, url: String): String {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "请输入 http(s) 订阅链接"
        }
        return try {
            val request = Request.Builder()
                .url(trimmed)
                .header("User-Agent", "clash.meta")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return "下载失败: HTTP ${response.code}"
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return "订阅内容为空"
                if (!body.contains("proxies:") && !body.contains("proxy-providers:")) {
                    return "不是 Clash 配置（缺少 proxies/proxy-providers）"
                }
                val file = File(context.cacheDir, "config.yaml")
                file.writeText(body)
                val result = Root.run(
                    "cp -f ${file.absolutePath} ${Root.BASE}/config.yaml && " +
                        "sh ${Root.BASE}/set-ipver.sh auto"
                )
                "导入成功\n$result"
            }
        } catch (t: Throwable) {
            "导入失败: ${t.message}"
        }
    }
}