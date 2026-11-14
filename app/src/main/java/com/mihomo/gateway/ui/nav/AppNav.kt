package com.mihomo.gateway.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mihomo.gateway.ui.connections.ConnectionsScreen
import com.mihomo.gateway.ui.logs.LogsScreen
import com.mihomo.gateway.ui.overview.OverviewScreen
import com.mihomo.gateway.ui.proxies.ProxiesScreen
import com.mihomo.gateway.ui.settings.SettingsScreen

private data class NavItem(val label: String, val icon: String)

private val items = listOf(
    NavItem("概览", "◎"),
    NavItem("代理", "⇄"),
    NavItem("连接", "⇅"),
    NavItem("日志", "≡"),
    NavItem("设置", "⚙")
)

@Composable
fun AppNav() {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Text(item.icon) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> OverviewScreen()
                1 -> ProxiesScreen()
                2 -> ConnectionsScreen()
                3 -> LogsScreen()
                else -> SettingsScreen()
            }
        }
    }
}
