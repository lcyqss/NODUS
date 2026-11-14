package com.mihomo.gateway.data.api

import android.util.Log
import com.mihomo.gateway.model.AppError
import com.mihomo.gateway.model.ConfigInfo
import com.mihomo.gateway.model.ConnectionInfo
import com.mihomo.gateway.model.LogEntry
import com.mihomo.gateway.model.ProviderInfo
import com.mihomo.gateway.model.ProxyNode
import com.mihomo.gateway.model.RuleInfo
import com.mihomo.gateway.model.Traffic
import com.mihomo.gateway.model.VersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.util.concurrent.TimeUnit

class MihomoApiImpl(
    private val baseUrl: String = "http://127.0.0.1:9090",
    private val secret: String
) : MihomoApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val wsBase = baseUrl.replaceFirst("http", "ws")

    // ---------- REST ----------

    private suspend fun <T> getJson(
        path: String,
        parser: (JSONObject) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer $secret")
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.code == 401) return@withContext Result.failure(AppError.Unauthorized)
                if (!response.isSuccessful) {
                    return@withContext Result.failure(AppError.Protocol("HTTP ${response.code}"))
                }
                Result.success(parser(JSONObject(text)))
            }
        } catch (e: ConnectException) {
            Result.failure(AppError.CoreDown)
        } catch (e: IOException) {
            Log.w(TAG, "io error $path: ${e.message}")
            Result.failure(AppError.CoreDown)
        } catch (e: Throwable) {
            Result.failure(AppError.Protocol(e.message ?: "parse"))
        }
    }

    private suspend fun requestUnit(method: String, path: String, body: String? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder()
                    .url(baseUrl + path)
                    .header("Authorization", "Bearer $secret")
                when (method) {
                    "PUT" -> builder.put((body ?: "").toRequestBody(JSON_MEDIA))
                    "PATCH" -> builder.patch((body ?: "").toRequestBody(JSON_MEDIA))
                    "POST" -> builder.post((body ?: "").toRequestBody(JSON_MEDIA))
                    "DELETE" -> builder.delete()
                    else -> builder.get()
                }
                client.newCall(builder.build()).execute().use { response ->
                    if (response.code == 401) return@withContext Result.failure(AppError.Unauthorized)
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(AppError.Protocol("HTTP ${response.code}"))
                    }
                    Result.success(Unit)
                }
            } catch (e: ConnectException) {
                Result.failure(AppError.CoreDown)
            } catch (e: IOException) {
                Result.failure(AppError.CoreDown)
            }
        }

    override suspend fun version(): Result<VersionInfo> = getJson("/version") {
        VersionInfo(it.optString("version"), it.optBoolean("meta"))
    }

    override suspend fun configs(): Result<ConfigInfo> = getJson("/configs") {
        ConfigInfo(
            port = it.optInt("port"),
            socksPort = it.optInt("socks-port"),
            redirPort = it.optInt("redir-port"),
            mode = it.optString("mode"),
            logLevel = it.optString("log-level")
        )
    }

    override suspend fun proxies(): Result<List<ProxyNode>> = getJson("/proxies") { root ->
        val proxies = root.optJSONObject("proxies") ?: JSONObject()
        val result = mutableListOf<ProxyNode>()
        val keys = proxies.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            val p = proxies.optJSONObject(name) ?: continue
            val type = p.optString("type")
            val all = p.optJSONArray("all")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }
            } ?: emptyList()
            val delay = p.optJSONArray("history")?.let { history ->
                if (history.length() == 0) null
                else history.optJSONObject(history.length() - 1)?.optInt("delay")?.takeIf { it > 0 }
            }
            result += ProxyNode(
                name = name,
                type = type,
                udp = p.optBoolean("udp"),
                delay = delay,
                alive = if (p.has("alive")) p.optBoolean("alive") else null,
                isGroup = all.isNotEmpty() || type in GROUP_TYPES,
                now = p.optStringOrNull("now"),
                all = all
            )
        }
        result
    }

    override suspend fun select(group: String, name: String): Result<Unit> {
        val body = JSONObject().put("name", name).toString()
        return requestUnit("PUT", "/proxies/" + group.encodePath(), body)
    }

    override suspend fun delay(name: String, timeoutMs: Int, url: String): Result<Int> =
        withContext(Dispatchers.IO) {
            val httpUrl = (baseUrl + "/proxies/" + name.encodePath() + "/delay").toHttpUrl()
                .newBuilder()
                .addQueryParameter("timeout", timeoutMs.toString())
                .addQueryParameter("url", url)
                .build()
            try {
            val request = Request.Builder()
                .url(httpUrl)
                .header("Authorization", "Bearer $secret")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 401) return@withContext Result.failure(AppError.Unauthorized)
                if (!response.isSuccessful) {
                    return@withContext Result.failure(AppError.Protocol("HTTP ${response.code}"))
                }
                val obj = JSONObject(response.body?.string().orEmpty())
                val delay = obj.optInt("delay")
                if (delay > 0) Result.success(delay)
                else Result.failure(AppError.Protocol(obj.optString("message", "timeout")))
            }
            } catch (e: IOException) {
                Result.failure(AppError.Network(e))
            }
        }

    override suspend fun groupDelay(group: String, timeoutMs: Int, url: String): Result<Map<String, Int>> =
        withContext(Dispatchers.IO) {
            val httpUrl = (baseUrl + "/group/" + group.encodePath() + "/delay").toHttpUrl()
                .newBuilder()
                .addQueryParameter("timeout", timeoutMs.toString())
                .addQueryParameter("url", url)
                .build()
            try {
                val request = Request.Builder()
                    .url(httpUrl)
                    .header("Authorization", "Bearer $secret")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 401) return@withContext Result.failure(AppError.Unauthorized)
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(AppError.Protocol("HTTP ${response.code}"))
                    }
                    val obj = JSONObject(response.body?.string().orEmpty())
                    val map = mutableMapOf<String, Int>()
                    obj.keys().forEach { key -> map[key] = obj.optInt(key, 0) }
                    Result.success(map)
                }
            } catch (e: IOException) {
                Result.failure(AppError.Network(e))
            }
        }

    override suspend fun setMode(mode: String): Result<Unit> =
        requestUnit("PATCH", "/configs", JSONObject().put("mode", mode).toString())

    override suspend fun restartCore(): Result<Unit> = requestUnit("POST", "/restart")

    override suspend fun flushFakeIp(): Result<Unit> = requestUnit("POST", "/cache/fakeip/flush")

    override suspend fun flushDns(): Result<Unit> = requestUnit("POST", "/cache/dns/flush")
    override suspend fun providers(): Result<List<ProviderInfo>> = getJson("/providers/proxies") { root ->
        val providers = root.optJSONObject("providers") ?: JSONObject()
        val list = mutableListOf<ProviderInfo>()
        val keys = providers.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val p = providers.optJSONObject(key) ?: continue
            list += ProviderInfo(
                name = p.optString("name").ifBlank { key },
                type = p.optString("type"),
                vehicleType = p.optString("vehicleType"),
                nodeCount = p.optJSONArray("proxies")?.length() ?: 0,
                updatedAt = p.optString("updatedAt"),
                testUrl = p.optString("testUrl")
            )
        }
        list
    }

    override suspend fun providerHealthCheck(name: String): Result<Unit> =
        requestUnit("GET", "/providers/proxies/" + name.encodePath() + "/healthcheck")

    override suspend fun dnsQuery(name: String, type: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            val httpUrl = (baseUrl + "/dns/query").toHttpUrl()
                .newBuilder()
                .addQueryParameter("name", name)
                .addQueryParameter("type", type)
                .build()
            try {
                val request = Request.Builder()
                    .url(httpUrl)
                    .header("Authorization", "Bearer $secret")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 401) return@withContext Result.failure(AppError.Unauthorized)
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(AppError.Protocol("HTTP " + response.code))
                    }
                    val obj = JSONObject(response.body?.string().orEmpty())
                    val arr = obj.optJSONArray("Answer")
                    val answers = if (arr == null) emptyList() else (0 until arr.length())
                        .mapNotNull { index -> arr.optJSONObject(index)?.optString("data") }
                    Result.success(answers)
                }
            } catch (e: IOException) {
                Result.failure(AppError.Network(e))
            }
        }


    override suspend fun connections(): Result<List<ConnectionInfo>> = getJson("/connections") { root ->
        val arr = root.optJSONArray("connections") ?: return@getJson emptyList()
        (0 until arr.length()).map { index ->
            val c = arr.optJSONObject(index) ?: JSONObject()
            val meta = c.optJSONObject("metadata") ?: JSONObject()
            val chains = c.optJSONArray("chains")?.let { ch ->
                (0 until ch.length()).map { ch.optString(it) }
            } ?: emptyList()
            ConnectionInfo(
                id = c.optString("id"),
                host = meta.optString("host").ifBlank { meta.optString("destinationIP") },
                sourceIP = meta.optString("sourceIP"),
                sourcePort = meta.optString("sourcePort"),
                destinationIP = meta.optString("destinationIP"),
                network = meta.optString("network"),
                rule = c.optString("rule"),
                rulePayload = c.optString("rulePayload"),
                chains = chains,
                upload = c.optLong("upload"),
                download = c.optLong("download"),
                start = c.optLong("start")
            )
        }
    }

    override suspend fun closeConnection(id: String): Result<Unit> =
        requestUnit("DELETE", "/connections/" + id.encodePath())

    override suspend fun closeAllConnections(): Result<Unit> =
        requestUnit("DELETE", "/connections")

    override suspend fun rules(): Result<List<RuleInfo>> = getJson("/rules") { root ->
        val arr = root.optJSONArray("rules") ?: return@getJson emptyList()
        (0 until arr.length()).map { index ->
            val r = arr.optJSONObject(index) ?: JSONObject()
            RuleInfo(r.optString("type"), r.optString("payload"), r.optString("proxy"))
        }
    }

    // ---------- WebSocket ----------

    override fun trafficFlow(): Flow<Traffic> = callbackFlow {
        val request = Request.Builder()
            .url("$wsBase/traffic")
            .header("Authorization", "Bearer $secret")
            .build()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return
                trySend(Traffic(obj.optLong("up"), obj.optLong("down")))
            }
        })
        awaitClose { socket.cancel() }
    }

    override fun memoryFlow(): Flow<Long> = callbackFlow {
        val request = Request.Builder()
            .url("$wsBase/memory")
            .header("Authorization", "Bearer $secret")
            .build()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return
                trySend(obj.optLong("inuse"))
            }
        })
        awaitClose { socket.cancel() }
    }

    override fun logFlow(level: String): Flow<LogEntry> = callbackFlow {
        val request = Request.Builder()
            .url("$wsBase/logs?level=$level")
            .header("Authorization", "Bearer $secret")
            .build()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return
                trySend(
                    LogEntry(
                        level = obj.optString("type"),
                        payload = obj.optString("payload"),
                        time = System.currentTimeMillis()
                    )
                )
            }
        })
        awaitClose { socket.cancel() }
    }

    private fun String.encodePath(): String = android.net.Uri.encode(this)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    companion object {
        private const val TAG = "GW-Api"
        private val JSON_MEDIA = "application/json".toMediaType()
        private val GROUP_TYPES = setOf(
            "Selector", "URLTest", "Fallback", "LoadBalance", "Relay", "Compatible"
        )
    }
}
