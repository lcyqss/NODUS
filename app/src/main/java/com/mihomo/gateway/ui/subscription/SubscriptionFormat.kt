package com.mihomo.gateway.ui.subscription

/** 自动更新间隔选项（分钟，0=关闭，最小 10 分钟）。 */
internal val AutoUpdateOptions = listOf(0, 10, 20, 30, 60, 120, 360, 720, 1440)

internal fun intervalLabel(minutes: Int): String = when {
    minutes <= 0 -> "关闭"
    minutes == 1440 -> "每天"
    minutes % 60 == 0 -> "每 ${minutes / 60} 小时"
    minutes > 60 -> "每 ${minutes / 60} 小时 ${minutes % 60} 分钟"
    else -> "每 $minutes 分钟"
}

internal fun formatTime(millis: Long): String =
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(millis))
