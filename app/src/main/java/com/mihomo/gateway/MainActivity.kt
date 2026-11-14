package com.mihomo.gateway

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mihomo.gateway.service.GatewayService
import com.mihomo.gateway.ui.nav.AppNav
import com.mihomo.gateway.ui.theme.GatewayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, GatewayService::class.java))
        setContent {
            GatewayTheme {
                AppNav()
            }
        }
    }
}
