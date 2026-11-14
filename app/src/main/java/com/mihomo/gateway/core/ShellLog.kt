package com.mihomo.gateway.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ShellRecord(
    val trace: String,
    val command: String,
    val code: Int,
    val costMs: Long,
    val time: Long
)

/** 最近 root 命令的环形缓冲，供诊断页展示。 */
object ShellLog {

    private const val MAX = 20

    private val items = ArrayDeque<ShellRecord>()
    private val _records = MutableStateFlow<List<ShellRecord>>(emptyList())
    val records: StateFlow<List<ShellRecord>> = _records.asStateFlow()

    fun add(record: ShellRecord) {
        synchronized(this) {
            items.addLast(record)
            while (items.size > MAX) items.removeFirst()
            _records.value = items.toList()
        }
    }

    fun clear() {
        synchronized(this) {
            items.clear()
            _records.value = emptyList()
        }
    }
}
