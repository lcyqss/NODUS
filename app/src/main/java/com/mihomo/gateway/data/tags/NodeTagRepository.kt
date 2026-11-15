package com.mihomo.gateway.data.tags

import com.mihomo.gateway.data.catalog.NodeInfo
import com.mihomo.gateway.data.catalog.NodeRegions
import com.mihomo.gateway.data.store.SettingsStore
import com.mihomo.gateway.model.NodeTag
import com.mihomo.gateway.model.NodeTagCodec
import com.mihomo.gateway.model.TagExpr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 节点标签：内置标签（类型、识别地区）+ 用户自定义关键词标签。
 * 普通标签语义：名称包含 include 任一（OR），且不包含 exclude 任一（NOT）。
 */
interface NodeTagRepository {
    val customTags: Flow<List<NodeTag>>

    /** 内置 + 自定义，供 UI 与策略求值使用。 */
    suspend fun allTags(nodes: List<NodeInfo>): List<NodeTag>

    suspend fun save(tag: NodeTag): Result<Unit>
    suspend fun delete(id: String)

    /** 按布尔表达式求值；null 表示任意节点。 */
    fun matches(tags: List<NodeTag>, expr: TagExpr?, node: NodeInfo): Boolean
}

class NodeTagRepositoryImpl(
    private val settings: SettingsStore
) : NodeTagRepository {

    override val customTags: Flow<List<NodeTag>> =
        settings.settings.map { NodeTagCodec.decode(it.nodeTags) }

    override suspend fun allTags(nodes: List<NodeInfo>): List<NodeTag> = withContext(Dispatchers.IO) {
        val builtinTypes = nodes.map { it.type }.filter { it.isNotBlank() }.distinct().sorted()
            .map { type ->
                NodeTag(
                    id = "__type_$type",
                    name = type,
                    builtin = true,
                    builtinKind = "type"
                )
            }
        val builtinRegions = NodeRegions.all.map { region ->
            NodeTag(
                id = "__region_${region.code}",
                name = region.label,
                builtin = true,
                builtinKind = "region"
            )
        }
        val custom = NodeTagCodec.decode(settings.settings.first().nodeTags)
            .sortedBy { it.name.lowercase() }
        custom + builtinTypes + builtinRegions
    }

    override suspend fun save(tag: NodeTag): Result<Unit> = withContext(Dispatchers.IO) {
        val name = tag.name.trim()
        if (name.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("标签名不能为空"))
        }
        val cleaned = tag.copy(
            id = tag.id.ifBlank { UUID.randomUUID().toString() },
            name = name,
            include = tag.include.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            exclude = tag.exclude.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            builtin = false,
            builtinKind = null
        )
        val current = NodeTagCodec.decode(settings.settings.first().nodeTags)
        val list = current.filterNot { it.id == cleaned.id } + cleaned
        val byId = list.associateBy { it.id }
        fun cyclic(tag: NodeTag, stack: Set<String>): Boolean {
            if (tag.id in stack) return true
            val expr = tag.expr ?: return false
            return TagExprParser.ids(expr).any { refId ->
                byId[refId]?.let { cyclic(it, stack + tag.id) } ?: false
            }
        }
        if (list.any { cyclic(it, emptySet()) }) {
            return@withContext Result.failure(IllegalArgumentException("标签存在循环引用"))
        }
        settings.update { it.copy(nodeTags = NodeTagCodec.encode(list)) }
        Result.success(Unit)
    }

    override suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            val current = NodeTagCodec.decode(settings.settings.first().nodeTags)
            settings.update {
                it.copy(nodeTags = NodeTagCodec.encode(current.filterNot { tag -> tag.id == id }))
            }
        }
    }

    override fun matches(tags: List<NodeTag>, expr: TagExpr?, node: NodeInfo): Boolean =
        eval(tags, expr, node, emptySet())

    private fun eval(
        tags: List<NodeTag>,
        expr: TagExpr?,
        node: NodeInfo,
        visiting: Set<String>
    ): Boolean = when (expr) {
        null -> true
        is TagExpr.Ref -> {
            val tag = tags.firstOrNull { it.id == expr.tagId }
            val hit = tag != null && evalTag(tags, tag, node, visiting)
            if (expr.negated) !hit else hit
        }
        is TagExpr.And -> expr.items.all { eval(tags, it, node, visiting) }
        is TagExpr.Or -> expr.items.any { eval(tags, it, node, visiting) }
        is TagExpr.Not -> !eval(tags, expr.item, node, visiting)
    }

    private fun evalTag(
        tags: List<NodeTag>,
        tag: NodeTag,
        node: NodeInfo,
        visiting: Set<String>
    ): Boolean {
        if (tag.id in visiting) return false
        tag.expr?.let { return eval(tags, it, node, visiting + tag.id) }
        if (tag.builtin) {
            return when (tag.builtinKind) {
                "type" -> node.type.equals(tag.id.removePrefix("__type_"), true)
                "region" -> node.region.equals(tag.id.removePrefix("__region_"), true)
                else -> false
            }
        }
        val lower = node.name.lowercase()
        val includeOk = tag.include.isEmpty() || tag.include.any { lower.contains(it.lowercase()) }
        val excludeHit = tag.exclude.any { lower.contains(it.lowercase()) }
        return includeOk && !excludeHit
    }
}
