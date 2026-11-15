package com.mihomo.gateway.ui.proxies

import androidx.compose.foundation.background
import com.mihomo.gateway.di.AppContainer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.ProxyNames
import com.mihomo.gateway.model.ProxyNode
import com.mihomo.gateway.ui.components.EmptyState
import com.mihomo.gateway.ui.components.Tag
import com.mihomo.gateway.ui.components.PullRefreshBox
import com.mihomo.gateway.ui.icons.GwIcon
import com.mihomo.gateway.ui.subscription.SubscriptionManagementPage
import com.mihomo.gateway.ui.subscription.SubscriptionViewModel
import kotlinx.coroutines.delay

private val GoodColor = Color(0xFF16A34A)
private val WarnColor = Color(0xFFEA580C)
private val BadColor = Color(0xFFDC2626)
private val FavoriteColor = Color(0xFFF59E0B)

private enum class SortMode(val label: String) {
    Default("默认顺序"),
    Delay("延迟从低到高"),
    Name("名称 A–Z")
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProxiesScreen(vm: ProxiesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val delays by vm.delays.collectAsState()
    val testing by vm.testing.collectAsState()
    val failed by vm.failed.collectAsState()
    val favorites by vm.favorites.collectAsState()

    val subVm: SubscriptionViewModel = viewModel()
    val subs by subVm.subscriptions.collectAsState()
    val subSettings by subVm.settings.collectAsState()
    val subBusy by subVm.busy.collectAsState()
    val subSwitching by subVm.switchingId.collectAsState()
    val subMessage by subVm.message.collectAsState()
    val subRefreshTick by subVm.refreshTick.collectAsState()
    var showSubPage by remember { mutableStateOf(false) }

    val currentGroup = state.groups.firstOrNull { it.name == state.selectedGroup }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.Default) }
    var hideUnavailable by remember { mutableStateOf(false) }
    var favoritesFirst by remember { mutableStateOf(true) }
    val pendingNode by AppContainer.navigator.proxyNode.collectAsState()

    // 设备策略点「命中节点」后跳到这里：选中包含该节点的组并过滤
    LaunchedEffect(pendingNode, state.groups) {
        val target = pendingNode ?: return@LaunchedEffect
        if (state.groups.isEmpty()) return@LaunchedEffect
        val candidates = state.groups.filter { g ->
            g.name != "GLOBAL" && !g.name.startsWith("__dev_") &&
                g.members.any { it.name == target }
        }
        val group = candidates.firstOrNull { it.type.equals("Selector", true) }
            ?: candidates.firstOrNull()
        if (group != null) vm.selectGroup(group.name)
        query = target
        searchOpen = false
        AppContainer.navigator.consumeProxyNode()
    }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    LaunchedEffect(searchOpen) {
        if (searchOpen) focusRequester.requestFocus() else keyboard?.hide()
    }

    // 订阅切换/更新成功后：关闭弹层并重新拉取节点（分组可能整体变化）
    // URLTest 组重载后要等首次测速才有 now：稍后补刷一次，仍为空就主动测速
    LaunchedEffect(subRefreshTick) {
        if (subRefreshTick > 0) {
            vm.load()
            delay(1600)
            vm.load()
            val snapshot = vm.state.value
            val group = snapshot.groups.firstOrNull { it.name == snapshot.selectedGroup }
            if (group != null && group.type == "URLTest" && group.now == null) {
                vm.testGroup(group)
            }
        }
    }

    // 操作提示几秒后消失
    LaunchedEffect(subMessage) {
        if (subMessage != null) {
            delay(4000)
            subVm.clearMessage()
        }
    }

    BackHandler(enabled = showSubPage || searchOpen) {
        when {
            showSubPage -> showSubPage = false
            searchOpen -> {
                searchOpen = false
                query = ""
            }
        }
    }

    if (showSubPage) {
        SubscriptionManagementPage(vm = subVm, onBack = { showSubPage = false })
        return
    }

    val members = remember(
        currentGroup, query, sortMode, delays, failed, hideUnavailable, favorites, favoritesFirst
    ) {
        val filtered = currentGroup?.members.orEmpty()
            .filter { it.name.contains(query, true) }
            .filter { !hideUnavailable || (!failed.contains(it.name) && (delays[it.name] ?: it.delay) != null) }
        val ordered = when (sortMode) {
            SortMode.Default -> filtered
            SortMode.Delay -> filtered.sortedWith(
                compareBy(
                    { delays[it.name] ?: it.delay ?: Int.MAX_VALUE },
                    { it.name }
                )
            )
            SortMode.Name -> filtered.sortedBy { it.name.lowercase() }
        }
        if (favoritesFirst) {
            ordered.sortedByDescending { favorites.contains(it.name) }
        } else {
            ordered
        }
    }

    // 排序 / 筛选 / 切换分组 / 收藏变化后回到列表顶部，避免 LazyColumn 以旧的首个可见项为锚点
    LaunchedEffect(sortMode, hideUnavailable, query, state.selectedGroup, favoritesFirst, favorites) {
        listState.scrollToItem(0)
    }

    val activeSub = subs.firstOrNull { it.id == subSettings.activeSubId }

    PullRefreshBox(onRefresh = { vm.load() }) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        SubscriptionRow(
            subsCount = subs.size,
            activeName = activeSub?.name,
            busy = subBusy,
            onClick = { showSubPage = true }
        )

        Spacer(Modifier.height(10.dp))

        when {
            state.loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                CenteredMessage("加载失败：${state.error}") {
                    OutlinedButton(onClick = { vm.load() }) { Text("重试") }
                }
            }

            state.groups.isEmpty() -> {
                CenteredMessage("没有可用的代理组，先从订阅入口添加订阅") {
                    OutlinedButton(onClick = { vm.load() }) { Text("重新加载") }
                }
            }

            else -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.groups, key = { it.name }) { group ->
                        GroupChip(
                            name = group.name,
                            selected = group.name == state.selectedGroup,
                            onClick = { vm.selectGroup(group.name) }
                        )
                    }
                }

                if (currentGroup != null) {
                    Spacer(Modifier.height(8.dp))

                    val shownCount = if (query.isNotBlank() || hideUnavailable) {
                        members.size
                    } else {
                        currentGroup.members.size
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${currentGroup.name} · $shownCount 个节点",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "当前 " + (currentGroup.now
                                    ?.takeUnless { ProxyNames.isInfoNode(it) } ?: "未选择"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        ToolIcon(GwIcon.Search, active = searchOpen) {
                            searchOpen = !searchOpen
                            if (!searchOpen) query = ""
                        }
                        Box {
                            if (testing.isNotEmpty()) {
                                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                ToolIcon(GwIcon.Speed) { currentGroup?.let(vm::testGroup) }
                            }
                        }
                        Box {
                            ToolIcon(GwIcon.Sort) { menuOpen = true }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                SortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        onClick = {
                                            sortMode = mode
                                            menuOpen = false
                                        },
                                        trailingIcon = { CheckMark(visible = sortMode == mode) }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("收藏置顶") },
                                    onClick = {
                                        favoritesFirst = !favoritesFirst
                                        menuOpen = false
                                    },
                                    trailingIcon = { CheckMark(visible = favoritesFirst) }
                                )
                                DropdownMenuItem(
                                    text = { Text("隐藏不可用") },
                                    onClick = {
                                        hideUnavailable = !hideUnavailable
                                        menuOpen = false
                                    },
                                    trailingIcon = { CheckMark(visible = hideUnavailable) }
                                )
                            }
                        }
                    }

                    if (failed.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${failed.size} 个节点测速失败，可能是节点不可达或网络受限",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (searchOpen) {
                        Spacer(Modifier.height(8.dp))
                        SearchField(
                            query = query,
                            focusRequester = focusRequester,
                            onQueryChange = { query = it },
                            onCancel = {
                                query = ""
                                searchOpen = false
                            }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (members.isEmpty()) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState("没有匹配的节点")
                            OutlinedButton(onClick = {
                                query = ""
                                hideUnavailable = false
                            }) { Text("清除筛选") }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            items(members, key = { it.name }) { node ->
                                NodeRow(
                                    node = node,
                                    selected = node.name == currentGroup.now,
                                    delay = delays[node.name],
                                    testing = testing.contains(node.name),
                                    failed = failed.contains(node.name),
                                    canSelect = currentGroup.type == "Selector",
                                    favorite = favorites.contains(node.name),
                                    onFavorite = { vm.toggleFavorite(node.name) },
                                    onClick = {
                                        if (currentGroup.type == "Selector") {
                                            vm.selectNode(currentGroup.name, node.name)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SubscriptionRow(
    subsCount: Int,
    activeName: String?,
    busy: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "订阅",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "$subsCount 个订阅",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            when {
                subsCount == 0 -> "添加 ›"
                activeName != null -> "$activeName 已启用 ›"
                else -> "未启用 ›"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
        Text(
            "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun CheckMark(visible: Boolean) {
    Text(
        if (visible) "✓" else "",
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun CenteredMessage(text: String, action: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(text)
            action()
        }
    }
}

@Composable
private fun GroupChip(name: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GwIcon(
            GwIcon.Search,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "搜索节点名称",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (query.isNotEmpty()) {
            Box(
                Modifier
                    .clickable { onQueryChange("") }
                    .padding(4.dp)
            ) {
                GwIcon(
                    GwIcon.Close,
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(2.dp))
        Text(
            "取消",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onCancel)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun NodeRow(
    node: ProxyNode,
    selected: Boolean,
    delay: Int?,
    testing: Boolean,
    failed: Boolean,
    canSelect: Boolean,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = canSelect, onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onFavorite),
            contentAlignment = Alignment.Center
        ) {
            GwIcon(
                if (favorite) GwIcon.StarFilled else GwIcon.Star,
                Modifier.size(18.dp),
                tint = if (favorite) FavoriteColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.width(2.dp))
        Column(Modifier.weight(1f)) {
            Text(
                node.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Tag(node.type)
                if (node.isGroup) {
                    Tag("组", MaterialTheme.colorScheme.secondary)
                    node.now?.takeUnless { ProxyNames.isInfoNode(it) }?.let { current ->
                        Text(
                            "→ $current",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (node.isGroup) {
            Text(
                "子组",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            DelayMeter(
                delay = delay ?: node.delay,
                testing = testing,
                failed = failed
            )
        }
    }
}

@Composable
private fun DelayMeter(delay: Int?, testing: Boolean, failed: Boolean) {
    val color = when {
        testing -> MaterialTheme.colorScheme.outline
        failed -> BadColor
        delay == null || delay <= 0 -> MaterialTheme.colorScheme.outline
        delay < 200 -> GoodColor
        delay < 500 -> WarnColor
        else -> BadColor
    }
    val text = when {
        testing -> "…"
        failed -> "失败"
        delay != null && delay > 0 -> "${delay}ms"
        else -> "—"
    }
    val fraction = if (!testing && !failed && delay != null && delay > 0) {
        (delay / 800f).coerceIn(0.12f, 1f)
    } else {
        0f
    }
    Column(
        modifier = Modifier.width(58.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = color,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(color.copy(alpha = 0.18f), RoundedCornerShape(2.dp))
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(color, RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
private fun ToolIcon(
    icon: GwIcon,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        GwIcon(
            icon,
            Modifier.size(20.dp),
            tint = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}