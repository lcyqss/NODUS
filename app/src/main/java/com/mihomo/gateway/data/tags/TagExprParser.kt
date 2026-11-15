package com.mihomo.gateway.data.tags

import com.mihomo.gateway.model.NodeTag
import com.mihomo.gateway.model.TagExpr

/**
 * 标签表达式：支持 ∧/&/and、∨/|/or、¬/!/not、括号，以及标签名或 tagId。
 * 例：`(香港 ∨ 台湾) ∧ ¬vless`、`__region_HK & __type_vless`。
 */
object TagExprParser {

    fun parse(text: String, tags: List<NodeTag>): Result<TagExpr?> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return Result.success(null)
        val parser = Parser(trimmed, nameIndex(tags), idIndex(tags))
        return runCatching { parser.parseAll() }
    }

    /** 表达式转可编辑文本（用标签显示名，未知标签回落 id）。 */
    fun toText(expr: TagExpr?, tags: List<NodeTag>): String {
        if (expr == null) return ""
        val names = tags.associate { it.id to it.name }
        return render(expr, names, 0)
    }

    fun ids(expr: TagExpr?): Set<String> {
        if (expr == null) return emptySet()
        return when (expr) {
            is TagExpr.Ref -> setOf(expr.tagId)
            is TagExpr.And -> expr.items.flatMap { ids(it) }.toSet()
            is TagExpr.Or -> expr.items.flatMap { ids(it) }.toSet()
            is TagExpr.Not -> ids(expr.item)
        }
    }

    private fun render(expr: TagExpr, names: Map<String, String>, parentPrec: Int): String {
        return when (expr) {
            is TagExpr.Ref -> {
                val name = names[expr.tagId] ?: expr.tagId
                if (expr.negated) "¬$name" else name
            }

            is TagExpr.And -> {
                val text = expr.items.joinToString(" ∧ ") { render(it, names, 1) }
                if (parentPrec > 1) "($text)" else text
            }

            is TagExpr.Or -> {
                val text = expr.items.joinToString(" ∨ ") { render(it, names, 0) }
                if (parentPrec > 0) "($text)" else text
            }

            is TagExpr.Not -> "¬" + render(expr.item, names, 2)
        }
    }

    private fun quoteName(name: String): String {
        val reserved = name.equals("and", true) || name.equals("or", true) || name.equals("not", true)
        val special = name.isEmpty() || name.any { it.isWhitespace() || it in "∧&∨|¬!()\"" }
        if (!reserved && !special) return name
        return "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    private fun nameIndex(tags: List<NodeTag>): Map<String, String> =
        tags.associate { it.name.lowercase() to it.id }

    private fun idIndex(tags: List<NodeTag>): Map<String, String> =
        tags.associate { it.id to it.id }

    private class Parser(
        private val text: String,
        private val names: Map<String, String>,
        private val ids: Map<String, String>
    ) {
        private var pos = 0

        fun parseAll(): TagExpr? {
            val expr = parseOr()
            skipSpace()
            if (pos < text.length) fail("无法解析: ${text.substring(pos)}")
            return expr
        }

        private fun parseOr(): TagExpr {
            val items = mutableListOf(parseAnd())
            while (true) {
                skipSpace()
                if (!consumeOr()) break
                items += parseAnd()
            }
            return if (items.size == 1) items.first() else TagExpr.Or(items)
        }

        private fun parseAnd(): TagExpr {
            val items = mutableListOf(parseUnary())
            while (true) {
                skipSpace()
                if (!consumeAnd()) break
                items += parseUnary()
            }
            return if (items.size == 1) items.first() else TagExpr.And(items)
        }

        private fun parseUnary(): TagExpr {
            skipSpace()
            if (consumeNot()) return TagExpr.Not(parseUnary())
            return parsePrimary()
        }

        private fun parsePrimary(): TagExpr {
            skipSpace()
            if (pos >= text.length) fail("表达式不完整")
            if (text[pos] == '"' || text[pos] == '\'') {
                return TagExpr.Ref(resolve(readQuoted()))
            }
            if (text[pos] == '(') {
                pos++
                val inner = parseOr()
                skipSpace()
                if (pos >= text.length || text[pos] != ')') fail("缺少右括号")
                pos++
                return inner
            }
            val name = readName()
            if (name.isBlank()) fail("缺少标签")
            val id = names[name.lowercase()] ?: ids[name]
                ?: fail("未知标签: $name")
            return TagExpr.Ref(id)
        }

        private fun resolve(name: String): String =
            names[name.lowercase()] ?: ids[name] ?: fail("未知标签: ")

        private fun readQuoted(): String {
            val quote = text[pos]
            pos++
            val builder = StringBuilder()
            while (pos < text.length) {
                val ch = text[pos]
                if (ch == '\\' && pos + 1 < text.length) {
                    builder.append(text[pos + 1])
                    pos += 2
                    continue
                }
                if (ch == quote) {
                    pos++
                    return builder.toString()
                }
                builder.append(ch)
                pos++
            }
            fail("引号未闭合")
        }

        private fun readName(): String {
            skipSpace()
            val start = pos
            while (pos < text.length) {
                val ch = text[pos]
                if (ch.isWhitespace() || ch in "∧&∨|¬!()") break
                pos++
            }
            var name = text.substring(start, pos)
            if (name.equals("and", true) || name.equals("or", true) || name.equals("not", true)) {
                pos = start
                return ""
            }
            return name
        }

        private fun consumeAnd(): Boolean = consumeChar("∧") || consumeChar("&") || consumeWord("and")
        private fun consumeOr(): Boolean = consumeChar("∨") || consumeChar("|") || consumeWord("or")
        private fun consumeNot(): Boolean = consumeChar("¬") || consumeChar("!") || consumeWord("not")

        private fun consumeChar(ch: String): Boolean {
            if (pos < text.length && text[pos].toString() == ch) {
                pos++
                return true
            }
            return false
        }

        private fun consumeWord(word: String): Boolean {
            val end = pos + word.length
            if (end <= text.length && text.substring(pos, end).equals(word, true)) {
                val beforeOk = pos == 0 || text[pos - 1].isWhitespace() || text[pos - 1] == ')'
                val afterOk = end == text.length || text[end].isWhitespace() || text[end] == '('
                if (beforeOk && afterOk) {
                    pos = end
                    return true
                }
            }
            return false
        }

        private fun skipSpace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun fail(message: String): Nothing = throw IllegalArgumentException(message)
    }
}
