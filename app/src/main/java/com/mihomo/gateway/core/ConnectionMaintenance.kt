package com.mihomo.gateway.core

import com.mihomo.gateway.di.AppContainer
import kotlinx.coroutines.flow.first

/**
 * 后台空闲连接清理。
 *
 * 只在设置开启、且连接总数超过阈值时，调用内核 API 关闭
 * 「空闲超过 30 分钟且没有任何流量」的连接，避免长期运行后连接表无限增长。
 */
object ConnectionMaintenance {

    suspend fun runAutoClean() {
        val cfg = runCatching { AppContainer.settings.settings.first() }.getOrNull() ?: return
        if (!cfg.autoCleanIdle) return
        val list = AppContainer.api.connections().getOrNull() ?: return
        if (list.size <= cfg.autoCleanThreshold) return
        val annotated = AppContainer.connectionActivity.annotate(list)
        AppContainer.connectionActivity.autoCleanCandidates(annotated)
            .forEach { AppContainer.api.closeConnection(it.id) }
    }
}
