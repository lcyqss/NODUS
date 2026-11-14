package com.mihomo.gateway

import android.app.Application
import com.mihomo.gateway.di.AppContainer

class MihomoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
