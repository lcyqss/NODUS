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
            .setContentText("网关运行中")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
        startForeground(1, notification)

        scope.launch {
            AppContainer.core.ensure().onFailure { Log.w(TAG, "install failed: ${it.message}") }
            AppContainer.gateway.start().onFailure { Log.w(TAG, "start failed: ${it.message}") }
            if (events == null) {
                events = GatewayEvents(applicationContext).also { it.start() }
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
