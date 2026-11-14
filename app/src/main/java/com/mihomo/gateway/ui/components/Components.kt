package com.mihomo.gateway.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 页面顶部标题区，统一各页视觉。 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        actions()
    }
}

/** 带标题的分组卡片。 */
@Composable
fun SectionCard(
    title: String? = null,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    trailing()
                }
            }
            content()
        }
    }
}

/** 运行状态胶囊。 */
@Composable
fun StatusPill(running: Boolean, text: String? = null) {
    val color = if (running) Color(0xFF16A34A) else MaterialTheme.colorScheme.outline
    val label = text ?: if (running) "运行中" else "未启动"
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** 指标：上标签、下数值。 */
@Composable
fun Metric(label: String, value: String, accent: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent)
    }
}

/** 标签 + 值的一行。 */
@Composable
fun KeyValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** 可点击的设置项。 */
@Composable
fun NavRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
    }
}

/** 空状态。 */
@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 双线流量面积图（仿 MetaCubeXD）：平滑曲线、渐变填充、坐标轴。 */
@Composable
fun TrafficChart(
    upValues: List<Long>,
    downValues: List<Long>,
    modifier: Modifier = Modifier
) {
    val upColor = Color(0xFF0F766E)
    val downColor = Color(0xFF4F46E5)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)

    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        val n = maxOf(upValues.size, downValues.size, 2)
        val maxValue = maxOf(
            upValues.maxOrNull() ?: 0L,
            downValues.maxOrNull() ?: 0L,
            1L
        )
        val topPad = 14.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val chartHeight = size.height - topPad - bottomPad
        val chartWidth = size.width

        fun xAt(index: Int): Float = chartWidth * index / (n - 1)
        fun yAt(value: Long): Float = topPad + chartHeight - (value.toFloat() / maxValue) * chartHeight

        repeat(4) { i ->
            val y = topPad + chartHeight * i / 3f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
        }

        fun pointsOf(values: List<Long>): List<Offset> =
            values.mapIndexed { index, value -> Offset(xAt(index), yAt(value)) }

        fun smooth(points: List<Offset>): Path {
            val path = Path()
            if (points.isEmpty()) return path
            path.moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val p0 = points[maxOf(i - 1, 0)]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[minOf(i + 2, points.size - 1)]
                val c1x = p1.x + (p2.x - p0.x) / 6f
                val c1y = p1.y + (p2.y - p0.y) / 6f
                val c2x = p2.x - (p3.x - p1.x) / 6f
                val c2y = p2.y - (p3.y - p1.y) / 6f
                path.cubicTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
            }
            return path
        }

        fun drawSeries(values: List<Long>, color: Color) {
            val points = pointsOf(values)
            if (points.size < 2) return
            val line = smooth(points)
            val fill = Path().apply {
                addPath(line)
                lineTo(points.last().x, topPad + chartHeight)
                lineTo(points.first().x, topPad + chartHeight)
                close()
            }
            drawPath(
                fill,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f)),
                    startY = topPad,
                    endY = topPad + chartHeight
                )
            )
            drawPath(line, color, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(color, radius = 2.5.dp.toPx(), center = points.last())
        }

        drawSeries(downValues, downColor)
        drawSeries(upValues, upColor)

        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 9.sp.toPx()
                color = labelColor.toArgb()
            }
            canvas.nativeCanvas.drawText(formatScale(maxValue), 2f, topPad - 4.dp.toPx(), paint)
            canvas.nativeCanvas.drawText("0", 2f, topPad + chartHeight - 2.dp.toPx(), paint)
            canvas.nativeCanvas.drawText("-${n - 1}s", 2f, size.height - 4.dp.toPx(), paint)
            val nowText = "now"
            val nowWidth = paint.measureText(nowText)
            canvas.nativeCanvas.drawText(nowText, size.width - nowWidth - 2f, size.height - 4.dp.toPx(), paint)
        }
    }
}

private fun formatScale(value: Long): String {
    val kb = value / 1024.0
    return when {
        kb < 1 -> "${value}B/s"
        kb < 1024 -> String.format("%.0fKB/s", kb)
        else -> String.format("%.1fMB/s", kb / 1024.0)
    }
}
/** 简单流量折线图。 */
@Composable
fun TrafficSparkline(values: List<Long>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(64.dp)) {
        if (values.size < 2) return@Canvas
        val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1L)
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - (value.toFloat() / max) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 2.5.dp.toPx()))
        drawLine(
            color = color.copy(alpha = 0.15f),
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/** 小圆角标签。 */
@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
