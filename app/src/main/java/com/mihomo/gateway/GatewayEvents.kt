package com.mihomo.gateway

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper

class GatewayEvents(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val applyTask = Runnable { Root.run("sh ${Root.BASE}/apply.sh") }

    fun start() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = schedule()
            override fun onLost(network: Network) = schedule()
        })
    }

    private fun schedule() {
        handler.removeCallbacks(applyTask)
        handler.postDelayed(applyTask, 2000)
    }
}