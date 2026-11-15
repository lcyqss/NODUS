package com.mihomo.gateway.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨 Tab 导航：设备策略里点命中节点 → 代理页定位。
 * 只保存一次性意图，消费后清空。
 */
class AppNavigator {

    private val _tab = MutableStateFlow(0)
    val tab: StateFlow<Int> = _tab.asStateFlow()

    private val _proxyNode = MutableStateFlow<String?>(null)
    val proxyNode: StateFlow<String?> = _proxyNode.asStateFlow()

    fun selectTab(index: Int) {
        _tab.value = index
    }

    fun openProxyNode(name: String) {
        _proxyNode.value = name
        _tab.value = 1
    }

    fun consumeProxyNode() {
        _proxyNode.value = null
    }
}
