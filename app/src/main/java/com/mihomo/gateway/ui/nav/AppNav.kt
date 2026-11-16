package com.mihomo.gateway.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.ui.connections.ConnectionsScreen
import com.mihomo.gateway.ui.icons.GwIcon
import com.mihomo.gateway.ui.rules.RulesScreen
import com.mihomo.gateway.ui.overview.OverviewScreen
import com.mihomo.gateway.ui.proxies.ProxiesScreen
import com.mihomo.gateway.ui.settings.SettingsScreen

private data class NavItem(val label: String, val icon: GwIcon)

private val items = listOf(
    NavItem("首页", GwIcon.Overview),
    NavItem("代理", GwIcon.Proxy),
    NavItem("分流", GwIcon.Logs),
    NavItem("连接", GwIcon.Connections),
    NavItem("设置", GwIcon.Settings)
)

@Composable
fun AppNav() {
    val tab by AppContainer.navigator.tab.collectAsState()

    // 次级 Tab 按返回先回首页；页面内部的 BackHandler（如设置二级页、订阅管理）在组合顺序上更靠后，优先处理
    BackHandler(enabled = tab != 0) { AppContainer.navigator.selectTab(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
                ) {
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { AppContainer.navigator.selectTab(index) },
                            icon = { GwIcon(item.icon, modifier = Modifier.padding(2.dp)) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        // 200ms 淡入；不用 Crossfade，避免新旧页面同时存在导致连接页轮询被旧实例关掉
        val fade = remember { Animatable(1f) }
        LaunchedEffect(tab) {
            fade.snapTo(0f)
            fade.animateTo(1f, animationSpec = tween(durationMillis = 200))
        }
        Box(Modifier.padding(padding).alpha(fade.value)) {
            when (tab) {
                0 -> OverviewScreen(
                    onOpenNodes = { AppContainer.navigator.selectTab(1) },
                    onOpenSettings = { AppContainer.navigator.selectTab(4) }
                )
                1 -> ProxiesScreen()
                2 -> RulesScreen()
                3 -> ConnectionsScreen()
                else -> SettingsScreen()
            }
        }
    }
}
