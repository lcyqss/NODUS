package com.mihomo.gateway.ui.connections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mihomo.gateway.model.ConnectionInfo
import com.mihomo.gateway.ui.components.EmptyState

private enum class NodeKind { ROOT, GROUP, PROXY, RULE, CLIENT }

private class GNode(
    val id: String,
    val label: String,
    val subtitle: String?,
    val kind: NodeKind
) {
    val children = mutableListOf<GNode>()
    var x = 0f
    var y = 0f
    var count = 0
    var traffic = 0L
    val hasChildren: Boolean get() = children.isNotEmpty()
}

private val NodeWidth = 168.dp
private val NodeHeight = 50.dp

@Composable
fun TopologyGraph(
    connections: List<ConnectionInfo>,
    labelOf: (String) -> String,
    modifier: Modifier = Modifier
) {
    if (connections.isEmpty()) {
        EmptyState("暂无活动连接，无法生成图谱")
        return
    }

    var collapsed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var scale by remember { mutableFloatStateOf(0.85f) }
    var offset by remember { mutableStateOf(Offset(0f, 0f)) }

    val density = LocalDensity.current
    val tree = remember(connections, collapsed) { buildGraph(connections, collapsed, labelOf) }
    val nodes = remember(tree, density) { flatten(tree, density) }

    val outline = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(modifier.fillMaxSize()) {
        fun fit(): Pair<Float, Offset> {
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            val nodeW = with(density) { NodeWidth.toPx() }
            val nodeH = with(density) { NodeHeight.toPx() }
            if (nodes.isEmpty()) return 0.85f to Offset(wPx / 2f, with(density) { 60.dp.toPx() })
            val minX = nodes.minOf { it.x - nodeW / 2f }
            val maxX = nodes.maxOf { it.x + nodeW / 2f }
            val minY = nodes.minOf { it.y }
            val maxY = nodes.maxOf { it.y + nodeH }
            val contentW = (maxX - minX).coerceAtLeast(1f)
            val contentH = (maxY - minY).coerceAtLeast(1f)
            val s = minOf(wPx / contentW, (hPx - with(density) { 56.dp.toPx() }) / contentH, 1.2f)
                .coerceIn(0.3f, 1.2f) * 0.95f
            val ox = (wPx - contentW * s) / 2f - minX * s
            val oy = (hPx - contentH * s) / 2f - minY * s
            return s to Offset(ox, oy)
        }

        var fitted by remember { mutableStateOf(false) }
        LaunchedEffect(nodes, maxWidth, maxHeight) {
            if (!fitted) {
                val (s, o) = fit()
                scale = s
                offset = o
                fitted = true
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.35f, 2.5f)
                        offset += pan
                    }
                }
                .pointerInput(nodes) {
                    detectTapGestures { position ->
                        val world = Offset(
                            (position.x - offset.x) / scale,
                            (position.y - offset.y) / scale
                        )
                        val w = NodeWidth.toPx()
                        val h = NodeHeight.toPx()
                        val hit = nodes.firstOrNull { node ->
                            world.x in (node.x - w / 2)..(node.x + w / 2) &&
                                world.y in node.y..(node.y + h)
                        }
                        if (hit != null && hit.hasChildren) {
                            collapsed = if (collapsed.contains(hit.id)) collapsed - hit.id
                            else collapsed + hit.id
                        }
                    }
                }
        ) {
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                val nodeW = NodeWidth.toPx()
                val nodeH = NodeHeight.toPx()

                nodes.forEach { parent ->
                    if (collapsed.contains(parent.id)) return@forEach
                    parent.children.forEach { child ->
                        val start = Offset(parent.x, parent.y + nodeH)
                        val end = Offset(child.x, child.y)
                        val midY = (start.y + end.y) / 2f
                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            cubicTo(start.x, midY, end.x, midY, end.x, end.y)
                        }
                        drawPath(path, lineColor, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
                    }
                }

                nodes.forEach { node ->
                    val topLeft = Offset(node.x - nodeW / 2f, node.y)
                    val color = colorFor(node.kind)
                    drawRoundRect(
                        color = surface,
                        topLeft = topLeft,
                        size = Size(nodeW, nodeH),
                        cornerRadius = CornerRadius(10.dp.toPx())
                    )
                    drawRoundRect(
                        color = color,
                        topLeft = topLeft,
                        size = Size(nodeW, nodeH),
                        cornerRadius = CornerRadius(10.dp.toPx()),
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                    drawRoundRect(
                        color = color,
                        topLeft = topLeft,
                        size = Size(5.dp.toPx(), nodeH),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )

                    drawIntoCanvas { canvas ->
                        val titlePaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            textSize = 12.sp.toPx()
                            this.color = onSurface.toArgb()
                            isFakeBoldText = node.kind == NodeKind.ROOT
                        }
                        val subPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            textSize = 9.sp.toPx()
                            this.color = labelColor.toArgb()
                        }
                        val textX = topLeft.x + 12.dp.toPx()
                        val centerY = node.y + nodeH / 2f
                        val title = ellipsize(node.label, titlePaint, nodeW - 24.dp.toPx())
                        canvas.nativeCanvas.drawText(title, textX, centerY - 3.dp.toPx(), titlePaint)

                        val sub = buildString {
                            append("${node.count} 条")
                            if (node.traffic > 0) append(" · ").append(formatTraffic(node.traffic))
                        }
                        val subText = node.subtitle?.let { "$it · $sub" } ?: sub
                        canvas.nativeCanvas.drawText(
                            ellipsize(subText, subPaint, nodeW - 24.dp.toPx()),
                            textX,
                            centerY + 12.dp.toPx(),
                            subPaint
                        )

                        if (node.hasChildren) {
                            val mark = if (collapsed.contains(node.id)) "+" else "−"
                            canvas.nativeCanvas.drawText(
                                mark,
                                topLeft.x + nodeW - 16.dp.toPx(),
                                centerY + 4.dp.toPx(),
                                titlePaint
                            )
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                val (s, o) = fit()
                scale = s
                offset = o
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) { Text("重置") }

        Text(
            "拖动平移 · 双指缩放 · 点节点折叠",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        )
    }
}

private fun colorFor(kind: NodeKind): Color = when (kind) {
    NodeKind.ROOT -> Color(0xFF4F46E5)
    NodeKind.GROUP -> Color(0xFF0F766E)
    NodeKind.PROXY -> Color(0xFF2563EB)
    NodeKind.RULE -> Color(0xFFEA580C)
    NodeKind.CLIENT -> Color(0xFF64748B)
}

private fun formatTraffic(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> String.format("%.1fKB", bytes / 1024.0)
    else -> String.format("%.1fMB", bytes / 1024.0 / 1024.0)
}

private fun ellipsize(text: String, paint: android.graphics.Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var end = text.length
    while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
        end--
    }
    return text.substring(0, end) + "…"
}

private fun buildGraph(
    connections: List<ConnectionInfo>,
    collapsed: Set<String>,
    labelOf: (String) -> String
): GNode {
    val root = GNode("root", "网关", null, NodeKind.ROOT)
    root.count = connections.size
    root.traffic = connections.sumOf { it.upload + it.download }

    connections.groupBy { it.chains.getOrNull(1) ?: "DIRECT" }.forEach { (group, groupConns) ->
        val groupNode = GNode("g:$group", groupLabel(group), null, NodeKind.GROUP)
        groupNode.count = groupConns.size
        groupNode.traffic = groupConns.sumOf { it.upload + it.download }
        root.children += groupNode
        if (collapsed.contains(groupNode.id)) return@forEach

        groupConns.groupBy { it.chains.getOrNull(0) ?: "DIRECT" }.forEach { (proxy, proxyConns) ->
            val proxyNode = GNode("p:$group/$proxy", proxy, null, NodeKind.PROXY)
            proxyNode.count = proxyConns.size
            proxyNode.traffic = proxyConns.sumOf { it.upload + it.download }
            groupNode.children += proxyNode
            if (collapsed.contains(proxyNode.id)) return@forEach

            proxyConns.groupBy {
                buildString {
                    append(it.rule.ifBlank { "Match" })
                    if (it.rulePayload.isNotBlank()) append(": ").append(it.rulePayload)
                }
            }.forEach { (rule, ruleConns) ->
                val ruleNode = GNode("r:$group/$proxy/$rule", rule, null, NodeKind.RULE)
                ruleNode.count = ruleConns.size
                ruleNode.traffic = ruleConns.sumOf { it.upload + it.download }
                proxyNode.children += ruleNode
                if (collapsed.contains(ruleNode.id)) return@forEach

                ruleConns.forEach { conn ->
                    val clientNode = GNode(
                        "c:${conn.id}",
                        labelOf(conn.sourceIP),
                        "${conn.sourceIP}:${conn.sourcePort}".trimEnd(':'),
                        NodeKind.CLIENT
                    )
                    clientNode.count = 1
                    clientNode.traffic = conn.upload + conn.download
                    ruleNode.children += clientNode
                }
            }
        }
    }
    return root
}

private fun flatten(root: GNode, density: Density): List<GNode> {
    var nextLeafX = 0f
    val rowStep = with(density) { (NodeHeight + 30.dp).toPx() }
    val colStep = with(density) { (NodeWidth + 26.dp).toPx() }

    fun layout(node: GNode, depth: Int): Float {
        node.y = depth * rowStep
        if (node.children.isEmpty()) {
            node.x = nextLeafX
            nextLeafX += colStep
            return node.x
        }
        val childXs = node.children.map { layout(it, depth + 1) }
        node.x = childXs.average().toFloat()
        return node.x
    }
    layout(root, 0)

    // 让根节点位于 x=0，方便初始居中
    val shift = root.x
    val result = mutableListOf<GNode>()
    fun shiftAndCollect(node: GNode) {
        node.x -= shift
        result += node
        node.children.forEach { shiftAndCollect(it) }
    }
    shiftAndCollect(root)
    return result
}

private fun groupLabel(group: String): String = when (group) {
    "DIRECT" -> "直连"
    "REJECT" -> "拦截"
    else -> group
}
