package com.mihomo.gateway.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 下拉刷新容器：内容跟手下移，顶部显示细进度环，松手触发 onRefresh。
 * 需要内部有可滚动内容（LazyColumn / verticalScroll）才能收到下拉手势。
 */
@Composable
fun PullRefreshBox(onRefresh: () -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val threshold = with(density) { 56.dp.toPx() }
    val maxPull = with(density) { 96.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    var refreshing by remember { mutableStateOf(false) }

    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y < 0f && pull > 0f) {
                    val consumed = available.y.coerceAtLeast(-pull)
                    pull = (pull + consumed).coerceAtLeast(0f)
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.Drag && available.y > 0f && !refreshing) {
                    pull = (pull + available.y * 0.55f).coerceAtMost(maxPull)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= 0f) return Velocity.Zero
                if (pull >= threshold && !refreshing) {
                    refreshing = true
                    onRefresh()
                    pull = threshold
                    delay(700)
                    refreshing = false
                }
                animate(pull, 0f) { value, _ -> pull = value }
                return Velocity(0f, available.y)
            }
        }
    }

    Box(Modifier.fillMaxSize().nestedScroll(nested)) {
        Box(Modifier.offset { IntOffset(0, pull.roundToInt()) }) { content() }
        when {
            refreshing -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            pull > 0f -> CircularProgressIndicator(
                progress = (pull / threshold).coerceIn(0f, 1f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
