package com.mihomo.gateway

import android.app.Application
import com.mihomo.gateway.di.AppContainer
import kotlinx.coroutines.launch

class MihomoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { AppContainer.appUpdates.checkIfDue() }
        }
    }
}
