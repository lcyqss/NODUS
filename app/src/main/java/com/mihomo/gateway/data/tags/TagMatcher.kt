package com.mihomo.gateway.data.tags

/**
 * 统一的关键词匹配。
 *
 * - 中文/英文长词：子串匹配
 * - 2–3 位 ASCII 代码：词边界匹配，避免 "Russia" 命中 "us"、"Finland" 命中 "in"
 * - 大小写不敏感
 */
object TagMatcher {

    fun hit(name: String, keyword: String): Boolean {
        val key = keyword.trim().lowercase()
        if (key.isEmpty()) return false
        val lower = name.lowercase()
        if (!isShortAsciiCode(key)) return lower.contains(key)

        var start = lower.indexOf(key)
        while (start >= 0) {
            val before = lower.getOrNull(start - 1)
            val after = lower.getOrNull(start + key.length)
            if ((before == null || !before.isAsciiLetter()) &&
                (after == null || !after.isAsciiLetter())
            ) {
                return true
            }
            start = lower.indexOf(key, start + 1)
        }
        return false
    }

    /** 只对 2–3 位字母/数字代码做边界匹配；更长的词仍按子串。 */
    private fun isShortAsciiCode(key: String): Boolean =
        key.length in 2..3 && key.all { it in 'a'..'z' || it in '0'..'9' }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z'
}
