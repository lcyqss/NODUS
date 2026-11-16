package com.mihomo.gateway

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.service.GatewayService
import com.mihomo.gateway.ui.nav.AppNav
import com.mihomo.gateway.ui.theme.GatewayTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 内容延伸到状态栏/导航栏下，系统栏保持透明，图标颜色随深色模式自动切换
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { AppContainer.core.ensure() }
            startForegroundService(Intent(this@MainActivity, GatewayService::class.java))
        }
        setContent {
            GatewayTheme {
                AppNav()
            }
        }
    }
}
