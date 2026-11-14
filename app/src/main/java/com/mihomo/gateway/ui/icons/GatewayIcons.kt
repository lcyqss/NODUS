package com.mihomo.gateway.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class GwIcon {
    Overview, Proxy, Connections, Logs, Settings,
    Power, Refresh, Search, Close, Speed, Download, Chevron,
    Sort, Star, StarFilled, Plus, Back
}

@Composable
fun GwIcon(
    icon: GwIcon,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current
) {
    Canvas(modifier.size(24.dp)) {
        val s = size.minDimension
        val stroke = s * 0.09f
        when (icon) {
            GwIcon.Overview -> drawOverview(s, tint)
            GwIcon.Proxy -> drawProxy(s, tint, stroke)
            GwIcon.Connections -> drawConnections(s, tint, stroke)
            GwIcon.Logs -> drawLogs(s, tint)
            GwIcon.Settings -> drawSettings(s, tint, stroke)
            GwIcon.Power -> drawPower(s, tint, stroke)
            GwIcon.Refresh -> drawRefresh(s, tint, stroke)
            GwIcon.Search -> drawSearch(s, tint, stroke)
            GwIcon.Close -> drawClose(s, tint, stroke)
            GwIcon.Speed -> drawSpeed(s, tint, stroke)
            GwIcon.Download -> drawDownload(s, tint, stroke)
            GwIcon.Chevron -> drawChevron(s, tint, stroke)
            GwIcon.Sort -> drawSort(s, tint, stroke)
            GwIcon.Star -> drawStar(s, tint, stroke, filled = false)
            GwIcon.StarFilled -> drawStar(s, tint, stroke, filled = true)
            GwIcon.Plus -> drawPlus(s, tint, stroke)
            GwIcon.Back -> drawBack(s, tint, stroke)
        }
    }
}

private fun DrawScope.drawOverview(s: Float, c: Color) {
    val gap = s * 0.12f
    val cell = (s - gap) / 2
    val r = CornerRadius(cell * 0.28f)
    drawRoundRect(c, Offset(0f, 0f), Size(cell, cell), r)
    drawRoundRect(c, Offset(cell + gap, 0f), Size(cell, cell), r)
    drawRoundRect(c, Offset(0f, cell + gap), Size(cell, cell), r)
    drawRoundRect(c, Offset(cell + gap, cell + gap), Size(cell, cell), r)
}

private fun DrawScope.drawProxy(s: Float, c: Color, w: Float) {
    val y1 = s * 0.30f
    val y2 = s * 0.70f
    drawLine(c, Offset(s * 0.12f, y1), Offset(s * 0.66f, y1), w, StrokeCap.Round)
    arrowHead(c, Offset(s * 0.90f, y1), s * 0.26f, s * 0.24f, right = true)
    drawLine(c, Offset(s * 0.88f, y2), Offset(s * 0.34f, y2), w, StrokeCap.Round)
    arrowHead(c, Offset(s * 0.10f, y2), s * 0.26f, s * 0.24f, right = false)
}

private fun DrawScope.arrowHead(c: Color, tip: Offset, len: Float, half: Float, right: Boolean) {
    val path = Path()
    path.moveTo(tip.x, tip.y)
    val dx = if (right) -len else len
    path.lineTo(tip.x + dx, tip.y - half / 2)
    path.lineTo(tip.x + dx, tip.y + half / 2)
    path.close()
    drawPath(path, c)
}

private fun DrawScope.drawConnections(s: Float, c: Color, w: Float) {
    val r = s * 0.17f
    drawCircle(c, r, Offset(s * 0.28f, s * 0.5f), style = Stroke(w))
    drawCircle(c, r, Offset(s * 0.72f, s * 0.5f), style = Stroke(w))
    drawLine(c, Offset(s * 0.45f, s * 0.5f), Offset(s * 0.55f, s * 0.5f), w, StrokeCap.Round)
}

private fun DrawScope.drawLogs(s: Float, c: Color) {
    val h = s * 0.10f
    val r = CornerRadius(h / 2)
    drawRoundRect(c, Offset(s * 0.12f, s * 0.22f), Size(s * 0.76f, h), r)
    drawRoundRect(c, Offset(s * 0.12f, s * 0.45f), Size(s * 0.52f, h), r)
    drawRoundRect(c, Offset(s * 0.12f, s * 0.68f), Size(s * 0.64f, h), r)
}

private fun DrawScope.drawSettings(s: Float, c: Color, w: Float) {
    val center = Offset(s / 2, s / 2)
    drawCircle(c, s * 0.20f, center, style = Stroke(w))
    val outer = s * 0.42f
    val inner = s * 0.26f
    repeat(6) { i ->
        val angle = Math.toRadians((i * 60).toDouble())
        val cos = kotlin.math.cos(angle).toFloat()
        val sin = kotlin.math.sin(angle).toFloat()
        drawLine(
            c,
            Offset(center.x + cos * inner, center.y + sin * inner),
            Offset(center.x + cos * outer, center.y + sin * outer),
            w * 2.0f,
            StrokeCap.Round
        )
    }
}

private fun DrawScope.drawPower(s: Float, c: Color, w: Float) {
    drawArc(
        color = c,
        startAngle = -60f,
        sweepAngle = 300f,
        useCenter = false,
        topLeft = Offset(s * 0.16f, s * 0.16f),
        size = Size(s * 0.68f, s * 0.68f),
        style = Stroke(w * 1.3f, cap = StrokeCap.Round)
    )
    drawLine(c, Offset(s / 2, s * 0.10f), Offset(s / 2, s * 0.46f), w * 1.5f, StrokeCap.Round)
}

private fun DrawScope.drawRefresh(s: Float, c: Color, w: Float) {
    drawArc(
        color = c,
        startAngle = -40f,
        sweepAngle = 280f,
        useCenter = false,
        topLeft = Offset(s * 0.18f, s * 0.18f),
        size = Size(s * 0.64f, s * 0.64f),
        style = Stroke(w, cap = StrokeCap.Round)
    )
    val tip = Offset(s * 0.78f, s * 0.20f)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - s * 0.16f, tip.y)
        lineTo(tip.x - s * 0.04f, tip.y + s * 0.16f)
        close()
    }
    drawPath(path, c)
}

private fun DrawScope.drawSearch(s: Float, c: Color, w: Float) {
    drawCircle(c, s * 0.28f, Offset(s * 0.42f, s * 0.42f), style = Stroke(w))
    drawLine(c, Offset(s * 0.62f, s * 0.62f), Offset(s * 0.86f, s * 0.86f), w * 1.2f, StrokeCap.Round)
}

private fun DrawScope.drawClose(s: Float, c: Color, w: Float) {
    drawLine(c, Offset(s * 0.24f, s * 0.24f), Offset(s * 0.76f, s * 0.76f), w * 1.1f, StrokeCap.Round)
    drawLine(c, Offset(s * 0.76f, s * 0.24f), Offset(s * 0.24f, s * 0.76f), w * 1.1f, StrokeCap.Round)
}

private fun DrawScope.drawSpeed(s: Float, c: Color, w: Float) {
    drawArc(
        color = c,
        startAngle = 150f,
        sweepAngle = 240f,
        useCenter = false,
        topLeft = Offset(s * 0.14f, s * 0.18f),
        size = Size(s * 0.72f, s * 0.72f),
        style = Stroke(w, cap = StrokeCap.Round)
    )
    val center = Offset(s / 2, s * 0.62f)
    drawLine(c, center, Offset(s * 0.70f, s * 0.34f), w * 1.2f, StrokeCap.Round)
    drawCircle(c, w * 0.9f, center)
}

private fun DrawScope.drawDownload(s: Float, c: Color, w: Float) {
    drawLine(c, Offset(s / 2, s * 0.16f), Offset(s / 2, s * 0.58f), w, StrokeCap.Round)
    val path = Path().apply {
        moveTo(s * 0.50f, s * 0.72f)
        lineTo(s * 0.34f, s * 0.50f)
        lineTo(s * 0.66f, s * 0.50f)
        close()
    }
    drawPath(path, c)
    drawLine(c, Offset(s * 0.20f, s * 0.84f), Offset(s * 0.80f, s * 0.84f), w, StrokeCap.Round)
}

private fun DrawScope.drawChevron(s: Float, c: Color, w: Float) {
    drawLine(c, Offset(s * 0.38f, s * 0.24f), Offset(s * 0.64f, s * 0.5f), w, StrokeCap.Round)
    drawLine(c, Offset(s * 0.64f, s * 0.5f), Offset(s * 0.38f, s * 0.76f), w, StrokeCap.Round)
}

/** 排序：三条递减横线 + 向下箭头。 */
private fun DrawScope.drawSort(s: Float, c: Color, w: Float) {
    val widths = listOf(0.54f, 0.40f, 0.26f)
    widths.forEachIndexed { i, width ->
        val y = s * (0.26f + i * 0.24f)
        drawLine(c, Offset(s * 0.12f, y), Offset(s * (0.12f + width), y), w, StrokeCap.Round)
    }
    drawLine(c, Offset(s * 0.80f, s * 0.22f), Offset(s * 0.80f, s * 0.66f), w, StrokeCap.Round)
    val head = Path().apply {
        moveTo(s * 0.80f, s * 0.82f)
        lineTo(s * 0.70f, s * 0.62f)
        lineTo(s * 0.90f, s * 0.62f)
        close()
    }
    drawPath(head, c)
}

/** 五角星，可描边或填充。 */
private fun DrawScope.drawStar(s: Float, c: Color, w: Float, filled: Boolean) {
    val cx = s / 2f
    val cy = s / 2f
    val outer = s * 0.44f
    val inner = outer * 0.44f
    val path = Path()
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = Math.toRadians((-90 + i * 36).toDouble())
        val x = cx + (radius * kotlin.math.cos(angle)).toFloat()
        val y = cy + (radius * kotlin.math.sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    if (filled) drawPath(path, c) else drawPath(path, c, style = Stroke(w * 0.9f, join = StrokeJoin.Round))
}

/** 加号，用于「添加」。 */
private fun DrawScope.drawPlus(s: Float, c: Color, w: Float) {
    drawLine(c, Offset(s / 2, s * 0.18f), Offset(s / 2, s * 0.82f), w, StrokeCap.Round)
    drawLine(c, Offset(s * 0.18f, s / 2), Offset(s * 0.82f, s / 2), w, StrokeCap.Round)
}

/** 返回箭头（向左的尖角）。 */
private fun DrawScope.drawBack(s: Float, c: Color, w: Float) {
    drawLine(c, Offset(s * 0.62f, s * 0.24f), Offset(s * 0.36f, s * 0.5f), w, StrokeCap.Round)
    drawLine(c, Offset(s * 0.36f, s * 0.5f), Offset(s * 0.62f, s * 0.76f), w, StrokeCap.Round)
}
