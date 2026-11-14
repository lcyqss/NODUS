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

        // 自动更新订阅：启动时检查一次，之后每 30 分钟检查
        scope.launch {
            while (isActive) {
                runCatching {
                    val current = AppContainer.settings.settings.first()
                    val interval = current.updateIntervalHours
                    if (interval > 0 && current.subscriptionUrl.isNotBlank()) {
                        val due = System.currentTimeMillis() - current.lastUpdateAt >= interval * 3600_000L
                        if (due) {
                            Log.i(TAG, "auto update subscription, interval=${interval}h")
                            AppContainer.profiles.autoUpdate()
                                .onFailure { Log.w(TAG, "auto update failed: ${it.message}") }
                        }
                    }
                }.onFailure { Log.w(TAG, "auto update loop: ${it.message}") }
                delay(30 * 60_000L)
            }
        }

        return START_STICKY
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
