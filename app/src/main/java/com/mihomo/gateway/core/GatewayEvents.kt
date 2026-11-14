package com.mihomo.gateway.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mihomo.gateway.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 网络变化 → 防抖 → 重下发规则。
 * 只负责触发；网关未运行时不做任何事（避免把已停止的内核重新拉起）。
 */
class GatewayEvents(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    private val applyTask = Runnable {
        scope.launch {
            if (!AppContainer.gateway.state.value.running) {
                Log.i(TAG, "skip apply: gateway not running")
                return@launch
            }
            val result = AppContainer.gateway.applyRules()
            if (result.isFailure) {
                Log.w(TAG, "apply rules failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun start() {
        handler.post {
            runCatching {
                val connectivity = context.getSystemService(ConnectivityManager::class.java)
                connectivity?.registerDefaultNetworkCallback(
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) = schedule()
                        override fun onLost(network: Network) = schedule()
                    }
                )
            }.onFailure {
                Log.w(TAG, "register network callback failed: ${it.message}")
            }
        }
    }

    private fun schedule() {
        handler.removeCallbacks(applyTask)
        handler.postDelayed(applyTask, 2000)
    }

    companion object {
        private const val TAG = "GW-Events"
    }
}
