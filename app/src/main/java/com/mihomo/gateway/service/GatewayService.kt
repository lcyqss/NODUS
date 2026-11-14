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
import kotlinx.coroutines.flow.first
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
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Mihomo Gateway")
            .setContentText("网关服务运行中")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
        startForeground(1, notification)

        scope.launch {
            AppContainer.core.ensure().onFailure { Log.w(TAG, "install failed: ${it.message}") }

            // 把 gateway.conf 中真实生效的配置同步回设置，避免界面与实际不一致
            runCatching {
                val conf = AppContainer.gateway.readConf()
                AppContainer.settings.update { it.copy(ipMode = conf.mode, blockIpv6 = conf.blockIpv6) }
            }.onFailure { Log.w(TAG, "sync conf failed: ${it.message}") }

            // 恢复上次运行状态：只有 desiredRunning 为 true 才拉起网关
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

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "mihomo_gateway"
        private const val TAG = "GW-Service"
    }
}
