package com.mihomo.gateway.ui.subscription

/** 自动更新间隔选项（小时，0=关闭）。 */
internal val AutoUpdateOptions = listOf(0, 1, 6, 12, 24)

internal fun intervalLabel(hours: Int): String = when (hours) {
    0 -> "关闭"
    1 -> "每 1 小时"
    6 -> "每 6 小时"
    12 -> "每 12 小时"
    24 -> "每天"
    else -> "每 $hours 小时"
}

internal fun formatTime(millis: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))
