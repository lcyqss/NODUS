package com.mihomo.gateway.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("gateway_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto_start", true)) return
        context.startForegroundService(Intent(context, GatewayService::class.java))
    }
}
