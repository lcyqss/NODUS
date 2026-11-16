package com.mihomo.gateway.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.mihomo.gateway.MainActivity
import com.mihomo.gateway.core.GatewayEvents
import com.mihomo.gateway.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GatewayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var events: GatewayEvents? = null
    private var auxiliaryLoopsStarted = false

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, "Mihomo 网关", NotificationManager.IMPORTANCE_LOW)
        channel.description = "root 透明代理网关常驻通知"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                AppContainer.gateway.stop()
                AppContainer.settings.update { it.copy(desiredRunning = false) }
                withMain {
                    startForeground(NOTIFICATION_ID, buildNotification("网关已停止，服务待命", false))
                }
            }
            return START_STICKY
        }

        if (intent?.action == ACTION_START) {
            scope.launch {
                AppContainer.settings.update { it.copy(desiredRunning = true) }
                AppContainer.gateway.start()
                withMain {
                    startForeground(NOTIFICATION_ID, buildNotification("网关服务运行中", true))
                }
            }
            return START_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("网关服务运行中", true))

        scope.launch {
            AppContainer.core.ensure().onFailure { Log.w(TAG, "install failed: ${it.message}") }

            // 把 gateway.conf 中真实生效的配置同步回设置，避免界面与实际不一致
            runCatching {
                val conf = AppContainer.gateway.readConf()
                AppContainer.settings.update { it.copy(ipMode = conf.mode, blockIpv6 = conf.blockIpv6) }
            }.onFailure { Log.w(TAG, "sync conf failed: ${it.message}") }

            val desired = runCatching {
                AppContainer.settings.settings.first().desiredRunning
            }.getOrDefault(true)

            if (desired) {
                AppContainer.gateway.start().onFailure { Log.w(TAG, "start failed: ${it.message}") }
                if (events == null) {
                    events = GatewayEvents(applicationContext).also { it.start() }
                }
            } else {
                AppContainer.gateway.refresh()
            }
        }

        // 自动更新订阅：启动时检查一次，之后每 30 分钟检查；每个订阅按自己的间隔
        if (!auxiliaryLoopsStarted) {
            auxiliaryLoopsStarted = true
        scope.launch {
            while (isActive) {
                runCatching {
                    val updated = AppContainer.profiles.autoUpdateDue().getOrThrow()
                    if (updated > 0) Log.i(TAG, "auto updated $updated subscription(s)")
                }.onFailure { Log.w(TAG, "auto update loop: ${it.message}") }
                delay(5 * 60_000L)
            }
        }

        // 设备策略：IP 跟随（DHCP 变化时重建 SRC-IP 规则），间隔可设置
        scope.launch {
            while (isActive) {
                runCatching { AppContainer.devicePolicies.syncIfNeeded() }
                    .onFailure { Log.w(TAG, "device policy ip sync: ${it.message}") }
                val seconds = runCatching {
                    AppContainer.settings.settings.first().ipFollowIntervalSec
                }.getOrDefault(60).coerceIn(30, 300)
                delay(seconds * 1000L)
            }
        }

        // 设备策略：延迟阈值调度。每 15 秒 tick 一次，按设备各自的间隔决定是否评估
        scope.launch {
            while (isActive) {
                runCatching { AppContainer.devicePolicies.evaluateRouting() }
                    .onFailure { Log.w(TAG, "device policy routing: ${it.message}") }
                delay(15_000L)
            }
        }

        // Geo 数据：默认关闭，开启后按天检查；仅在到期时下载并重启内核
        scope.launch {
            delay(30_000L)
            while (isActive) {
                runCatching { maybeUpdateGeo() }
                    .onFailure { Log.w(TAG, "geo auto update: ${it.message}") }
                delay(6 * 60 * 60_000L)
            }
        }

        }
        return START_STICKY
    }

    private suspend fun maybeUpdateGeo() {
        val s = AppContainer.settings.settings.first()
        if (!s.geoAutoUpdate) return
        val intervalDays = s.geoUpdateIntervalDays.coerceIn(1, 30)
        val now = System.currentTimeMillis()
        val due = s.lastGeoUpdateAt <= 0L ||
            now - s.lastGeoUpdateAt >= intervalDays * 24L * 60L * 60_000L
        if (!due) return

        val wasRunning = AppContainer.gateway.state.value.running
        val result = AppContainer.geoData.update(useProxy = wasRunning)
        if (wasRunning && result.getOrNull()?.unchanged != true) {
            AppContainer.gateway.stop()
            AppContainer.gateway.start()
        }
        result.onSuccess {
            AppContainer.settings.update { it.copy(lastGeoUpdateAt = now) }
            Log.i(TAG, "geo updated: ${it.version}")
        }.onFailure {
            Log.w(TAG, "geo update failed: ${it.message}")
        }
    }

    private fun buildNotification(text: String, running: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, GatewayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL)
            .setContentTitle("Mihomo Gateway")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(openIntent)
            .setOngoing(true)
        if (running) {
            builder.addAction(
                Notification.Action.Builder(null, "停止网关", stopIntent).build()
            )
        } else {
            val startIntent = PendingIntent.getService(
                this, 2,
                Intent(this, GatewayService::class.java).setAction(ACTION_START),
                PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                Notification.Action.Builder(null, "启动网关", startIntent).build()
            )
        }
        builder.addAction(
            Notification.Action.Builder(null, "打开", openIntent).build()
        )
        return builder.build()
    }

    private suspend fun withMain(block: () -> Unit) {
        kotlinx.coroutines.withContext(Dispatchers.Main) { block() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        events?.stop()
        events = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "mihomo_gateway"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "GW-Service"
        const val ACTION_STOP = "com.mihomo.gateway.STOP"
        const val ACTION_START = "com.mihomo.gateway.START"
    }
}
