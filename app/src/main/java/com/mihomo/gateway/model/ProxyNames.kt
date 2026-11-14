package com.mihomo.gateway.model

/**
 * 机场订阅常在节点列表里混入「剩余流量 / 套餐到期」等提示节点。
 * 这些不是真正可用的代理，展示时需要过滤。
 */
object ProxyNames {

    private val INFO_KEYWORDS = listOf(
        "剩余流量", "距离下次重置", "套餐到期", "距离下次", "过期时间",
        "官网", "订阅", "到期时间", "流量重置"
    )

    fun isInfoNode(name: String): Boolean = INFO_KEYWORDS.any { name.contains(it) }
}
