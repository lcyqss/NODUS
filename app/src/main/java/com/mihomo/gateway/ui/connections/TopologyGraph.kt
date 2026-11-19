package com.mihomo.gateway.ui.connections

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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

private enum class NodeKind { ROOT, GROUP, PROXY, RULE, CLIENT, PORT }

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
    var idleCount = 0
    var traffic = 0L
    var portCount = 0
    val allIdle: Boolean get() = count > 0 && idleCount >= count
    val hasChildren: Boolean get() = children.isNotEmpty() || portCount > 0
}

private val NodeWidth = 172.dp
private val NodeHeight = 50.dp

@Composable
fun TopologyGraph(
    connections: List<ConnectionInfo>,
    labelOf: (String) -> String,
    modifier: Modifier = Modifier
) {
    if (connections.isEmpty()) {
        EmptyState("暂无活动连接，无法生成拓扑")
        return
    }

    var collapsed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedClients by remember { mutableStateOf<Set<String>>(emptySet()) }
    var scale by remember { mutableFloatStateOf(0.9f) }
    var offset by remember { mutableStateOf(Offset(0f, 0f)) }
    var portsOpen by remember { mutableStateOf(false) }
    var requestFit by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val tree = remember(connections, collapsed, expandedClients) {
        buildGraph(connections, collapsed, expandedClients, labelOf)
    }
    val nodes = remember(tree, density) { flatten(tree, density) }

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
            val margin = with(density) { 10.dp.toPx() }
            if (nodes.isEmpty()) return 0.9f to Offset(margin, margin)

            val minX = nodes.minOf { it.x }
            val maxX = nodes.maxOf { it.x + nodeW }
            val minY = nodes.minOf { it.y - nodeH / 2f }
            val maxY = nodes.maxOf { it.y + nodeH / 2f }
            val contentW = (maxX - minX).coerceAtLeast(1f)
            val contentH = (maxY - minY).coerceAtLeast(1f)

            val s = ((wPx - margin * 2f) / contentW).coerceIn(0.25f, 1.15f) * 0.98f
            val ox = if (contentW * s > wPx - margin * 2f) {
                margin - minX * s
            } else {
                (wPx - contentW * s) / 2f - minX * s
            }
            val oy = if (contentH * s <= hPx - margin * 2f) {
                (hPx - contentH * s) / 2f - minY * s
            } else {
                margin - minY * s
            }
            return s to Offset(ox, oy)
        }

        var fitted by remember { mutableStateOf(false) }
        LaunchedEffect(nodes, maxWidth, maxHeight, requestFit) {
            if (!fitted || requestFit) {
                val (s, o) = fit()
                scale = s
                offset = o
                fitted = true
                requestFit = false
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.3f, 2.5f)
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
                            world.x in node.x..(node.x + w) &&
                                world.y in (node.y - h / 2)..(node.y + h / 2)
                        }
                        if (hit != null && hit.hasChildren) {
                            if (hit.kind == NodeKind.CLIENT) {
                                expandedClients = if (expandedClients.contains(hit.id)) {
                                    expandedClients - hit.id
                                } else {
                                    expandedClients + hit.id
                                }
                            } else {
                                collapsed = if (collapsed.contains(hit.id)) {
                                    collapsed - hit.id
                                } else {
                                    collapsed + hit.id
                                }
                            }
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
                val iconSize = 15.dp.toPx()

                nodes.forEach { parent ->
                    if (collapsed.contains(parent.id)) return@forEach
                    parent.children.forEach { child ->
                        val start = Offset(parent.x + nodeW, parent.y)
                        val end = Offset(child.x, child.y)
                        val midX = (start.x + end.x) / 2f
                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            cubicTo(midX, start.y, midX, end.y, end.x, end.y)
                        }
                        drawPath(
                            path,
                            if (child.allIdle) lineColor.copy(alpha = 0.12f) else lineColor,
                            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                nodes.forEach { node ->
                    val alpha = if (node.allIdle) 0.35f else 1f
                    val topLeft = Offset(node.x, node.y - nodeH / 2f)
                    val color = colorFor(node.kind).copy(alpha = alpha)
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
                    drawNodeIcon(
                        node.kind,
                        Offset(topLeft.x + 12.dp.toPx(), node.y - iconSize / 2f),
                        iconSize,
                        color
                    )

                    drawIntoCanvas { canvas ->
                        val titlePaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            textSize = 11.5.sp.toPx()
                            this.color = onSurface.toArgb()
                            isFakeBoldText = node.kind == NodeKind.ROOT
                        }
                        val subPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            textSize = 9.sp.toPx()
                            this.color = labelColor.toArgb()
                        }
                        val paintAlpha = (255 * alpha).toInt().coerceIn(0, 255)
                        titlePaint.alpha = paintAlpha
                        subPaint.alpha = paintAlpha
                        val textX = topLeft.x + 34.dp.toPx()
                        val title = ellipsize(node.label, titlePaint, nodeW - 50.dp.toPx())
                        canvas.nativeCanvas.drawText(title, textX, node.y - 2.dp.toPx(), titlePaint)

                        val sub = buildString {
                            append("${node.count} 条")
                            if (node.idleCount > 0) append(" · ${node.idleCount} 空闲")
                            if (node.traffic > 0) append(" · ").append(formatTraffic(node.traffic))
                        }
                        val subText = node.subtitle?.let { "$it · $sub" } ?: sub
                        canvas.nativeCanvas.drawText(
                            ellipsize(subText, subPaint, nodeW - 50.dp.toPx()),
                            textX,
                            node.y + 13.dp.toPx(),
                            subPaint
                        )

                        if (node.hasChildren) {
                            val expanded = if (node.kind == NodeKind.CLIENT) {
                                expandedClients.contains(node.id)
                            } else {
                                !collapsed.contains(node.id)
                            }
                            canvas.nativeCanvas.drawText(
                                if (expanded) "−" else "+",
                                topLeft.x + nodeW - 16.dp.toPx(),
                                node.y + 4.dp.toPx(),
                                titlePaint
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val clientIds = remember(nodes) {
                nodes.filter { it.kind == NodeKind.CLIENT && it.portCount > 0 }.map { it.id }
            }
            OutlinedButton(
                onClick = {
                    portsOpen = !portsOpen
                    expandedClients = if (portsOpen) clientIds.toSet() else emptySet()
                    requestFit = true
                }
            ) { Text(if (portsOpen) "收起端口" else "展开端口") }
            OutlinedButton(
                onClick = {
                    val (s, o) = fit()
                    scale = s
                    offset = o
                }
            ) { Text("重置") }
        }

        Text(
            "拖动平移 · 双指缩放 · 点节点展开/折叠",
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
    NodeKind.PORT -> Color(0xFF94A3B8)
}

private fun DrawScope.drawNodeIcon(kind: NodeKind, topLeft: Offset, s: Float, color: Color) {
    val stroke = 1.4.dp.toPx()
    when (kind) {
        NodeKind.ROOT -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(topLeft.x, topLeft.y + s * 0.2f),
                size = Size(s, s * 0.65f),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = Stroke(stroke)
            )
            drawLine(
                color,
                Offset(topLeft.x + s / 2, topLeft.y),
                Offset(topLeft.x + s / 2, topLeft.y + s * 0.45f),
                stroke
            )
        }

        NodeKind.GROUP -> {
            val a = Offset(topLeft.x + s / 2, topLeft.y + s * 0.15f)
            val b = Offset(topLeft.x + s * 0.15f, topLeft.y + s * 0.85f)
            val c = Offset(topLeft.x + s * 0.85f, topLeft.y + s * 0.85f)
            drawLine(color, a, b, stroke)
            drawLine(color, a, c, stroke)
            drawLine(color, b, c, stroke)
            drawCircle(color, s * 0.12f, a)
            drawCircle(color, s * 0.12f, b)
            drawCircle(color, s * 0.12f, c)
        }

        NodeKind.PROXY -> {
            drawRoundRect(
                color,
                topLeft,
                Size(s, s * 0.4f),
                CornerRadius(2.dp.toPx()),
                style = Stroke(stroke)
            )
            drawRoundRect(
                color,
                Offset(topLeft.x, topLeft.y + s * 0.55f),
                Size(s, s * 0.4f),
                CornerRadius(2.dp.toPx()),
                style = Stroke(stroke)
            )
        }

        NodeKind.RULE -> {
            val path = Path().apply {
                moveTo(topLeft.x, topLeft.y + s * 0.1f)
                lineTo(topLeft.x + s, topLeft.y + s * 0.1f)
                lineTo(topLeft.x + s * 0.62f, topLeft.y + s * 0.55f)
                lineTo(topLeft.x + s * 0.62f, topLeft.y + s)
                lineTo(topLeft.x + s * 0.38f, topLeft.y + s)
                lineTo(topLeft.x + s * 0.38f, topLeft.y + s * 0.55f)
                close()
            }
            drawPath(path, color)
        }

        NodeKind.CLIENT -> {
            drawRoundRect(
                color,
                topLeft,
                Size(s, s * 0.62f),
                CornerRadius(2.dp.toPx()),
                style = Stroke(stroke)
            )
            drawLine(
                color,
                Offset(topLeft.x + s * 0.35f, topLeft.y + s),
                Offset(topLeft.x + s * 0.65f, topLeft.y + s),
                stroke
            )
            drawLine(
                color,
                Offset(topLeft.x + s / 2, topLeft.y + s * 0.62f),
                Offset(topLeft.x + s / 2, topLeft.y + s),
                stroke
            )
        }

        NodeKind.PORT -> {
            drawLine(color, Offset(topLeft.x + s * 0.3f, topLeft.y), Offset(topLeft.x + s * 0.3f, topLeft.y + s), stroke)
            drawLine(color, Offset(topLeft.x + s * 0.7f, topLeft.y), Offset(topLeft.x + s * 0.7f, topLeft.y + s), stroke)
            drawLine(color, Offset(topLeft.x, topLeft.y + s * 0.35f), Offset(topLeft.x + s, topLeft.y + s * 0.35f), stroke)
            drawLine(color, Offset(topLeft.x, topLeft.y + s * 0.7f), Offset(topLeft.x + s, topLeft.y + s * 0.7f), stroke)
        }
    }
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
    expandedClients: Set<String>,
    labelOf: (String) -> String
): GNode {
    val root = GNode("root", "网关", null, NodeKind.ROOT)
    root.count = connections.size
    root.idleCount = connections.count { it.inactive }
    root.traffic = connections.sumOf { it.upload + it.download }

    connections.groupBy { it.chains.getOrNull(1) ?: "DIRECT" }.forEach { (group, groupConns) ->
        val groupNode = GNode("g:$group", groupLabel(group), null, NodeKind.GROUP)
        groupNode.count = groupConns.size
        groupNode.idleCount = groupConns.count { it.inactive }
        groupNode.traffic = groupConns.sumOf { it.upload + it.download }
        root.children += groupNode
        if (collapsed.contains(groupNode.id)) return@forEach

        groupConns.groupBy { it.chains.getOrNull(0) ?: "DIRECT" }.forEach { (proxy, proxyConns) ->
            val proxyNode = GNode("p:$group/$proxy", proxy, null, NodeKind.PROXY)
            proxyNode.count = proxyConns.size
            proxyNode.idleCount = proxyConns.count { it.inactive }
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
                ruleNode.idleCount = ruleConns.count { it.inactive }
                ruleNode.traffic = ruleConns.sumOf { it.upload + it.download }
                proxyNode.children += ruleNode
                if (collapsed.contains(ruleNode.id)) return@forEach

                // 同一设备合并；端口作为子节点，默认折叠
                ruleConns.groupBy { it.sourceIP }.forEach { (ip, clientConns) ->
                    val clientId = "c:$group/$proxy/$rule/$ip"
                    val clientNode = GNode(clientId, labelOf(ip), ip, NodeKind.CLIENT)
                    clientNode.count = clientConns.size
                    clientNode.idleCount = clientConns.count { it.inactive }
                    clientNode.traffic = clientConns.sumOf { it.upload + it.download }
                    clientNode.portCount = clientConns.map { it.sourcePort }.distinct().size
                    ruleNode.children += clientNode

                    if (expandedClients.contains(clientId)) {
                        clientConns.groupBy { it.sourcePort }.forEach { (port, portConns) ->
                            val portNode = GNode(
                                "t:$group/$proxy/$rule/$ip/$port",
                                if (port.isBlank()) "未知端口" else "端口 $port",
                                null,
                                NodeKind.PORT
                            )
                            portNode.count = portConns.size
                            portNode.idleCount = portConns.count { it.inactive }
                            portNode.traffic = portConns.sumOf { it.upload + it.download }
                            clientNode.children += portNode
                        }
                    }
                }
            }
        }
    }
    return root
}

private fun flatten(root: GNode, density: Density): List<GNode> {
    var nextLeafY = 0f
    val colStep = with(density) { (NodeWidth + 56.dp).toPx() }
    val rowStep = with(density) { (NodeHeight + 16.dp).toPx() }

    fun layout(node: GNode, depth: Int): Float {
        node.x = depth * colStep
        if (node.children.isEmpty()) {
            node.y = nextLeafY
            nextLeafY += rowStep
            return node.y
        }
        val childYs = node.children.map { layout(it, depth + 1) }
        node.y = childYs.average().toFloat()
        return node.y
    }
    layout(root, 0)

    val shift = root.y
    val result = mutableListOf<GNode>()
    fun shiftAndCollect(node: GNode) {
        node.y -= shift
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
