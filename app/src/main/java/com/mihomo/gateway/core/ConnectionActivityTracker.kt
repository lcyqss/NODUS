package com.mihomo.gateway.core

import com.mihomo.gateway.model.ConnectionInfo

/**
 * 连接活跃度跟踪。
 *
 * 内核只返回连接的累计上下行字节，不提供「最后活动时间」，也不提供 TCP 状态。
 * 这里在每次轮询时对比累计字节，推断每条连接最后一次有流量的时间，
 * 用于区分「活跃 / 空闲」（空闲即界面上灰色显示的连接）。
 */
class ConnectionActivityTracker {

    private class Snapshot(var bytes: Long, var lastActive: Long)

    private val snapshots = HashMap<String, Snapshot>()

    /** 给连接列表补齐 idleSeconds / inactive；同一个 id 多次传入是幂等的。 */
    fun annotate(list: List<ConnectionInfo>, now: Long = System.currentTimeMillis()): List<ConnectionInfo> {
        synchronized(snapshots) {
            val seen = HashSet<String>(list.size)
            val result = ArrayList<ConnectionInfo>(list.size)
            for (c in list) {
                seen += c.id
                val bytes = c.upload + c.download
                val snap = snapshots[c.id]
                if (snap == null) {
                    // 第一次见到这条连接：零流量的老连接直接按 start 计算空闲时长，
                    // 有流量的连接先视为活跃，之后靠差分判断。
                    val initial = if (bytes == 0L && c.start > 0L) c.start else now
                    snapshots[c.id] = Snapshot(bytes, initial)
                } else if (bytes > snap.bytes) {
                    snap.bytes = bytes
                    snap.lastActive = now
                }
                val idleSeconds = ((now - snapshots[c.id]!!.lastActive) / 1000L).coerceAtLeast(0L)
                result += c.copy(
                    idleSeconds = idleSeconds,
                    inactive = idleSeconds >= IDLE_AFTER_SEC
                )
            }
            if (snapshots.size > list.size) {
                snapshots.keys.retainAll(seen)
            }
            return result
        }
    }

    /** 当前应被「清理空闲」关闭的连接：空闲超过 10 分钟且完全没有流量。 */
    fun cleanCandidates(list: List<ConnectionInfo>): List<ConnectionInfo> =
        list.filter {
            it.inactive &&
                it.idleSeconds >= MANUAL_CLEAN_IDLE_SEC &&
                it.upload == 0L &&
                it.download == 0L
        }

    /** 自动清理使用的更保守候选：空闲超过 30 分钟且完全没有流量。 */
    fun autoCleanCandidates(list: List<ConnectionInfo>): List<ConnectionInfo> =
        list.filter {
            it.inactive &&
                it.idleSeconds >= AUTO_CLEAN_IDLE_SEC &&
                it.upload == 0L &&
                it.download == 0L
        }

    companion object {
        /** 超过 2 分钟没有流量就视为「空闲」，界面上灰色显示。 */
        const val IDLE_AFTER_SEC = 120L

        /** 手动清理：空闲超过 10 分钟。 */
        const val MANUAL_CLEAN_IDLE_SEC = 600L

        /** 自动清理：空闲超过 30 分钟。 */
        const val AUTO_CLEAN_IDLE_SEC = 1_800L
    }
}
