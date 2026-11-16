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
    const val SUB_RULE_START = "# >>> device policy sub rules"
    const val SUB_RULE_END = "# <<< device policy sub rules"
    const val RULE_TAIL_START = "# >>> device policy tail rules"
    const val RULE_TAIL_END = "# <<< device policy tail rules"
    const val LISTENER_START = "# >>> device policy listeners"
    const val LISTENER_END = "# <<< device policy listeners"
    const val RULE_END = "# <<< device policy rules"

    fun strip(text: String): String =
        stripBlock(stripBlock(stripBlock(stripBlock(stripBlock(text, GROUP_START, GROUP_END), RULE_START, RULE_END), RULE_TAIL_START, RULE_TAIL_END), SUB_RULE_START, SUB_RULE_END), LISTENER_START, LISTENER_END)

    /** 去掉重复的 dns.listen，避免 policy 写入时把无效配置交给 mihomo。 */
    fun normalizeDnsListen(text: String): String {
        var seen = false
        var seenIpv6 = false
        return text.lineSequence().filterNot { line ->
            // Old builds inserted dns ipv6 with 2 spaces; it is invalid next to 4-space keys.
            if (line.startsWith("  ipv6:") && !line.startsWith("    ipv6:")) return@filterNot true
            if (line.startsWith("    ipv6:")) {
                if (seenIpv6) return@filterNot true
                seenIpv6 = true
            }
            if (!line.startsWith("    listen:")) return@filterNot false
            if (seen) true else {
                seen = true
                false
            }
        }.joinToString("\n")
    }

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

    /** listenerLines 是不带缩进的 YAML 列表项，例如 `- { name: dev-x, ... }`。 */
    fun injectListeners(text: String, listenerLines: List<String>): String {
        val lines = text.lines().toMutableList()
        val index = lines.indexOfFirst { it.trimEnd() == "listeners:" }
        if (index < 0) {
            if (listenerLines.isEmpty()) return text
            val block = buildList {
                add(LISTENER_START)
                add("listeners:")
                listenerLines.forEach { add("  $it") }
                add(LISTENER_END)
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
            add("$indent$LISTENER_START")
            listenerLines.forEach { add("$indent$it") }
            add("$indent$LISTENER_END")
        }
        lines.addAll(end, block)
        return lines.joinToString("\n")
    }

    /** tailRuleLines 插到 MATCH 之前，用于“规则模式”的设备兜底组。 */
    fun injectTailRules(text: String, tailRuleLines: List<String>): String {
        if (tailRuleLines.isEmpty()) return text
        val lines = text.lines().toMutableList()
        var index = lines.indexOfFirst { line ->
            val t = line.trim()
            t.startsWith("- 'MATCH,") || t.startsWith("- MATCH,")
        }
        if (index < 0) {
            index = lines.indexOfFirst { it.trimEnd() == "proxy-groups:" }
            if (index < 0) index = lines.size
        }
        val indent = lines.getOrNull(index)?.takeWhile { it == ' ' }?.ifBlank { "    " } ?: "    "
        val block = buildList {
            add("$indent$RULE_TAIL_START")
            tailRuleLines.forEach { add("$indent- $it") }
            add("$indent$RULE_TAIL_END")
        }
        lines.addAll(index, block)
        return lines.joinToString("\n")
    }

    /** blocks: 子规则名 -> 规则文本列表（不带 `- `）。 */
    fun injectSubRules(text: String, blocks: List<Pair<String, List<String>>>): String {
        if (blocks.isEmpty()) return text
        val lines = text.lines().toMutableList()
        val index = lines.indexOfFirst { it.trimEnd() == "sub-rules:" }
        if (index < 0) {
            val block = buildList {
                add(SUB_RULE_START)
                add("sub-rules:")
                blocks.forEach { (name, rules) ->
                    add("  $name:")
                    rules.forEach { add("    - '${it.replace("'", "''")}'") }
                }
                add(SUB_RULE_END)
            }
            return (lines + block).joinToString("\n")
        }
        var end = index + 1
        while (end < lines.size) {
            val line = lines[end]
            if (line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("#")) break
            end++
        }
        val block = buildList {
            add("  $SUB_RULE_START")
            blocks.forEach { (name, rules) ->
                add("  $name:")
                rules.forEach { add("    - '${it.replace("'", "''")}'") }
            }
            add("  $SUB_RULE_END")
        }
        lines.addAll(end, block)
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
