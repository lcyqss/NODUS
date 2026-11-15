package com.mihomo.gateway.data.config

/**
 * 设备策略写入 config.yaml 的文本块管理：
 * - 组块追加到 proxy-groups 末尾
 * - 规则块插到 rules 最前面（优先于订阅规则）
 * 两组都用注释标记包住，重复生成前先整体移除。
 */
object DevicePolicyConfig {

    const val GROUP_START = "# >>> device policy groups"
    const val GROUP_END = "# <<< device policy groups"
    const val RULE_START = "# >>> device policy rules"
    const val RULE_END = "# <<< device policy rules"

    fun strip(text: String): String =
        stripBlock(stripBlock(text, GROUP_START, GROUP_END), RULE_START, RULE_END)

    /** groupLines 是不带缩进的 YAML 列表项，例如 `- { name: __dev_x, ... }`。 */
    fun injectGroups(text: String, groupLines: List<String>): String {
        val lines = text.lines().toMutableList()
        val index = lines.indexOfFirst { it.trimEnd() == "proxy-groups:" }
        if (index < 0) {
            if (groupLines.isEmpty()) return text
            val block = buildList {
                add("proxy-groups:")
                groupLines.forEach { add("  $it") }
            }
            return (lines + block).joinToString("\n")
        }

        var end = index + 1
        while (end < lines.size) {
            val line = lines[end]
            if (line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("#")) break
            end++
        }
        val indent = blockIndent(lines, index + 1, end)
        val block = buildList {
            add("$indent$GROUP_START")
            groupLines.forEach { add("$indent$it") }
            add("$indent$GROUP_END")
        }
        lines.addAll(end, block)
        return lines.joinToString("\n")
    }

    /** ruleLines 是不带缩进和 `- ` 的规则文本。 */
    fun injectRules(text: String, ruleLines: List<String>): String {
        val lines = text.lines().toMutableList()
        val index = lines.indexOfFirst { it.trimEnd() == "rules:" }
        if (index < 0) {
            if (ruleLines.isEmpty()) return text
            val block = buildList {
                add("rules:")
                ruleLines.forEach { add("  - $it") }
            }
            return (lines + block).joinToString("\n")
        }
        val indent = blockIndent(lines, index + 1, lines.size)
        val block = buildList {
            add("$indent$RULE_START")
            ruleLines.forEach { add("$indent- $it") }
            add("$indent$RULE_END")
        }
        lines.addAll(index + 1, block)
        return lines.joinToString("\n")
    }

    private fun blockIndent(lines: List<String>, from: Int, to: Int): String {
        for (i in from until minOf(to, lines.size)) {
            val line = lines[i]
            if (line.isNotBlank() && line.trimStart().startsWith("- ")) {
                return line.takeWhile { it == ' ' }
            }
        }
        return "  "
    }

    private fun stripBlock(text: String, start: String, end: String): String {
        var skipping = false
        return text.lineSequence().filterNot { line ->
            val trimmed = line.trim()
            when {
                trimmed == start -> {
                    skipping = true
                    true
                }
                trimmed == end -> {
                    skipping = false
                    true
                }
                else -> skipping
            }
        }.joinToString("\n")
    }
}
