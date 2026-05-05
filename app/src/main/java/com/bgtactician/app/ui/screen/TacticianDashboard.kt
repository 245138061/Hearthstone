package com.bgtactician.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.bgtactician.app.data.model.KeyMinion
import com.bgtactician.app.data.model.PositioningHint
import com.bgtactician.app.data.model.StrategyComp
import com.bgtactician.app.data.model.Tribe
import com.bgtactician.app.data.repository.StrategyEngine
import com.bgtactician.app.viewmodel.DashboardUiState

private val DashboardCard = Color(0xDD10202D)
private val DashboardRaised = Color(0xE0142837)
private val DashboardLine = Color(0x33DFF7FF)
private val DashboardGold = Color(0xFFF3C86B)
private val DashboardMint = Color(0xFF6FD6C2)
private val DashboardIce = Color(0xFF7DDCFF)
private val DashboardCoral = Color(0xFFFF8A69)
private val OverlayDrawerShell = Color(0xFF111929)
private val OverlayDrawerCore = Color(0xFF1C2940)
private val OverlayDrawerInset = Color(0xFF162134)
private val OverlayDrawerStroke = Color(0x7AFFD45B)
private val OverlayDrawerAccent = Color(0xFFFFD45B)
private val OverlayDrawerText = Color(0xFFFFF1C9)
private val OverlayDrawerSubtext = Color(0xFFB3C2DA)

@Composable
fun TacticianDashboard(
    modifier: Modifier = Modifier,
    uiState: DashboardUiState,
    overlayMode: Boolean,
    onSelectStrategy: (String) -> Unit,
    onUpdateTribes: (Set<Tribe>) -> Unit,
    onClose: (() -> Unit)? = null
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var workingTribes by remember { mutableStateOf(uiState.selectedTribes) }
    var tribesDirty by remember { mutableStateOf(false) }
    var workingSelectedStrategyId by remember { mutableStateOf(uiState.selectedStrategyId) }
    var strategyDirty by remember { mutableStateOf(false) }
    val displayedStrategies = remember(uiState.allStrategies, workingTribes) {
        StrategyEngine.filter(
            allStrategies = uiState.allStrategies,
            selectedTribes = workingTribes
        )
    }
    val selectedStrategy = displayedStrategies.firstOrNull { it.id == workingSelectedStrategyId }
        ?: displayedStrategies.firstOrNull()

    LaunchedEffect(uiState.selectedTribes) {
        if (!tribesDirty || uiState.selectedTribes == workingTribes) {
            workingTribes = uiState.selectedTribes
            tribesDirty = false
        }
    }

    LaunchedEffect(uiState.selectedStrategyId, displayedStrategies) {
        val externalId = uiState.selectedStrategyId
        when {
            !strategyDirty -> {
                workingSelectedStrategyId = externalId
            }
            externalId == workingSelectedStrategyId -> {
                strategyDirty = false
            }
        }
        if (displayedStrategies.none { it.id == workingSelectedStrategyId }) {
            workingSelectedStrategyId = displayedStrategies.firstOrNull()?.id
            strategyDirty = false
        }
    }

    val shellModifier = if (overlayMode) {
        modifier
            .fillMaxSize()
            .padding(
                start = 6.dp,
                end = 6.dp,
                top = 24.dp,
                bottom = 6.dp
            )
    } else {
        modifier.fillMaxSize()
    }

    BoxWithConstraints(
        modifier = shellModifier,
        contentAlignment = if (overlayMode) Alignment.Center else Alignment.TopStart
    ) {
        val landscapeShell = overlayMode && maxWidth > maxHeight

        Card(
            modifier = if (overlayMode) {
                if (landscapeShell) {
                    Modifier
                        .fillMaxWidth(0.97f)
                        .fillMaxHeight(0.94f)
                        .widthIn(min = 560.dp, max = 1040.dp)
                } else {
                    Modifier
                        .fillMaxHeight(0.94f)
                        .fillMaxWidth(0.96f)
                        .widthIn(min = 360.dp, max = 580.dp)
                }
            } else {
                Modifier.fillMaxSize()
            },
            shape = RoundedCornerShape(if (overlayMode) 28.dp else 0.dp),
            colors = CardDefaults.cardColors(containerColor = OverlayDrawerInset),
            elevation = CardDefaults.cardElevation(defaultElevation = if (overlayMode) 14.dp else 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                OverlayDrawerAccent.copy(alpha = 0.12f),
                                OverlayDrawerCore,
                                OverlayDrawerInset
                            )
                        )
                    )
                    .border(1.dp, OverlayDrawerStroke, RoundedCornerShape(if (overlayMode) 28.dp else 0.dp))
                    .padding(
                        horizontal = if (landscapeShell) 16.dp else 14.dp,
                        vertical = if (landscapeShell) 12.dp else 14.dp
                    )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (landscapeShell) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DashboardTabs(
                                modifier = Modifier.weight(1f),
                                selectedTab = selectedTab,
                                compactMode = true,
                                onSelectTab = { selectedTab = it }
                            )
                            if (overlayMode && onClose != null) {
                                IconButton(onClick = onClose) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = null,
                                        tint = OverlayDrawerText
                                    )
                                }
                            }
                        }
                    } else {
                        DashboardHeader(
                            selectedTribeCount = workingTribes.size,
                            strategyCount = displayedStrategies.size,
                            overlayMode = overlayMode,
                            onClose = onClose
                        )
                        DashboardTabs(
                            selectedTab = selectedTab,
                            compactMode = false,
                            onSelectTab = { selectedTab = it }
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        when (selectedTab) {
                            0 -> ManualSetupTab(
                                selectedTribes = workingTribes,
                                onUpdateTribes = { tribes ->
                                    workingTribes = tribes
                                    tribesDirty = true
                                    workingSelectedStrategyId = null
                                    strategyDirty = false
                                    onUpdateTribes(tribes)
                                }
                            )
                            1 -> StrategyListTab(
                                strategies = displayedStrategies,
                                selectedStrategyId = selectedStrategy?.id,
                                selectedTribeCount = workingTribes.size,
                                onSelectStrategy = { strategyId ->
                                    workingSelectedStrategyId = strategyId
                                    strategyDirty = true
                                    onSelectStrategy(strategyId)
                                }
                            )
                            else -> StrategyDetailTab(
                                strategy = selectedStrategy
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(
    selectedTribeCount: Int,
    strategyCount: Int,
    overlayMode: Boolean,
    onClose: (() -> Unit)?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "手动流派台",
                    color = OverlayDrawerText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "先选 5 种族，再看流派。",
                    color = OverlayDrawerSubtext,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (overlayMode && onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = OverlayDrawerText
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricChip("已选", "$selectedTribeCount/5", DashboardGold)
            MetricChip("流派", strategyCount.toString(), DashboardMint)
        }
    }
}

@Composable
private fun DashboardTabs(
    modifier: Modifier = Modifier,
    selectedTab: Int,
    compactMode: Boolean,
    onSelectTab: (Int) -> Unit
) {
    val tabs = listOf("种族", "流派", "详情")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compactMode) 40.dp else 44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(OverlayDrawerShell.copy(alpha = 0.78f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) OverlayDrawerAccent.copy(alpha = 0.22f) else Color.Transparent
                    )
                    .border(
                        1.dp,
                        if (selected) OverlayDrawerAccent.copy(alpha = 0.46f) else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectTab(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) OverlayDrawerText else OverlayDrawerSubtext,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Black else FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualSetupTab(
    selectedTribes: Set<Tribe>,
    onUpdateTribes: (Set<Tribe>) -> Unit
) {
    val selectedCount = selectedTribes.size

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val isWideLayout = maxWidth >= 620.dp
        val statusText = when {
            selectedCount == 0 -> if (isWideLayout) "未选" else "未选择种族"
            selectedCount < 5 -> if (isWideLayout) "还差 ${5 - selectedCount} 个" else "还需选择 ${5 - selectedCount} 个种族"
            else -> if (isWideLayout) "已选满" else "已选满 5 个种族"
        }
        val summaryText = if (selectedTribes.isEmpty()) {
            if (isWideLayout) "点选 5 个种族，再点一次取消。" else "点一下选中，再点一下取消。"
        } else {
            Tribe.entries
                .filter { it in selectedTribes }
                .joinToString(" / ") { it.label }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(24.dp),
            color = DashboardRaised,
            border = androidx.compose.foundation.BorderStroke(1.dp, DashboardLine.copy(alpha = 0.28f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (isWideLayout) 16.dp else 16.dp, vertical = if (isWideLayout) 14.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(if (isWideLayout) 12.dp else 16.dp)
            ) {
                if (isWideLayout) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricChip(
                            title = "已选",
                            value = "$selectedCount/5",
                            accent = if (selectedCount == 5) DashboardMint else DashboardCoral
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "选择 5 种族",
                                color = OverlayDrawerText,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = statusText,
                                color = if (selectedCount == 5) DashboardMint else DashboardGold,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        MetricChip(
                            title = "已选",
                            value = "$selectedCount/5",
                            accent = if (selectedCount == 5) DashboardMint else DashboardCoral
                        )
                    }
                }

                Text(
                    text = summaryText,
                    color = OverlayDrawerSubtext,
                    style = if (isWideLayout) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    maxLines = if (isWideLayout) 1 else 3,
                    overflow = TextOverflow.Ellipsis
                )

                FlowRow(
                    maxItemsInEachRow = if (isWideLayout) 5 else 3,
                    horizontalArrangement = Arrangement.spacedBy(if (isWideLayout) 8.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isWideLayout) 8.dp else 10.dp)
                ) {
                    Tribe.entries.forEach { tribe ->
                        val selected = tribe in selectedTribes
                        Surface(
                            modifier = Modifier
                                .widthIn(min = if (isWideLayout) 88.dp else 92.dp)
                                .clickable {
                                    val next = when {
                                        selected -> selectedTribes - tribe
                                        selectedTribes.size >= 5 -> selectedTribes
                                        else -> selectedTribes + tribe
                                    }
                                    if (next != selectedTribes) {
                                        onUpdateTribes(next)
                                    }
                                },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) {
                                tribeAccent(tribe).copy(alpha = 0.20f)
                            } else {
                                OverlayDrawerShell.copy(alpha = 0.74f)
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) tribeAccent(tribe).copy(alpha = 0.76f) else DashboardLine.copy(alpha = 0.24f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = if (isWideLayout) 12.dp else 14.dp,
                                    vertical = if (isWideLayout) 10.dp else 13.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(if (isWideLayout) 6.dp else 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isWideLayout) 7.dp else 8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) {
                                                tribeAccent(tribe)
                                            } else {
                                                DashboardLine.copy(alpha = 0.42f)
                                            }
                                        )
                                )
                                Text(
                                    text = tribe.label,
                                    color = if (selected) tribeAccent(tribe) else OverlayDrawerText,
                                    style = if (isWideLayout) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (!isWideLayout) {
                    Text(
                        text = if (selectedCount == 5) "已按这 5 个种族过滤流派。" else "选满 5 个后显示流派。",
                        color = if (selectedCount == 5) DashboardMint else OverlayDrawerSubtext,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun StrategyListTab(
    strategies: List<StrategyComp>,
    selectedStrategyId: String?,
    selectedTribeCount: Int,
    onSelectStrategy: (String) -> Unit
) {
    if (strategies.isEmpty()) {
        DashboardEmptyState(
            title = if (selectedTribeCount < 5) "先选满 5 个种族" else "当前没有匹配流派",
            detail = if (selectedTribeCount < 5) {
                "当前已选 $selectedTribeCount/5。"
            } else {
                "这 5 个种族下暂时没有可展示流派。"
            }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        items(strategies, key = StrategyComp::id) { strategy ->
            val selected = strategy.id == selectedStrategyId
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectStrategy(strategy.id) },
                shape = RoundedCornerShape(20.dp),
                color = if (selected) DashboardRaised else DashboardCard,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (selected) OverlayDrawerAccent.copy(alpha = 0.6f) else DashboardLine.copy(alpha = 0.26f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = strategy.name,
                                color = OverlayDrawerText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        TierBadge(strategy.tier)
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        strategy.requiredTribes
                            .mapNotNull(Tribe::fromWireName)
                            .forEach { tribe ->
                                DetailChip(tribe.label, tribeAccent(tribe))
                            }
                    }

                    if (strategy.keyMinions.isNotEmpty()) {
                        MinionStrip(
                            minions = strategy.keyMinions.take(5),
                            borderColor = if (selected) OverlayDrawerAccent else DashboardLine
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StrategyDetailTab(
    strategy: StrategyComp?
) {
    if (strategy == null) {
        DashboardEmptyState(
            title = "先选一套流派",
            detail = "选择后显示关键卡。"
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DashboardPanel(
            title = strategy.name,
            icon = Icons.AutoMirrored.Outlined.MenuBook
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TierBadge(strategy.tier)
                strategy.requiredTribes
                    .mapNotNull(Tribe::fromWireName)
                    .forEach { tribe ->
                        DetailChip(tribe.label, tribeAccent(tribe))
                    }
            }
        }

        if (strategy.keyMinions.isNotEmpty()) {
            DashboardPanel(
                title = "关键卡",
                icon = Icons.AutoMirrored.Outlined.MenuBook
            ) {
                strategy.keyMinions.forEach { minion ->
                    KeyMinionLine(minion)
                }
            }
        }
    }
}

@Composable
private fun DashboardPanel(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = DashboardCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, DashboardLine.copy(alpha = 0.32f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(OverlayDrawerAccent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = OverlayDrawerAccent
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            color = OverlayDrawerText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        subtitle?.takeIf(String::isNotBlank)?.let {
                            Text(
                                text = it,
                                color = OverlayDrawerSubtext,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                content()
            }
        )
    }
}

@Composable
private fun DashboardEmptyState(
    title: String,
    detail: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = DashboardCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, DashboardLine.copy(alpha = 0.28f))
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = OverlayDrawerText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = detail,
                    color = OverlayDrawerSubtext,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun MetricChip(
    title: String,
    value: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = OverlayDrawerSubtext,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = value,
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun DetailChip(
    label: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.38f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TierBadge(tier: String) {
    val accent = when (tier.uppercase()) {
        "T0", "S" -> DashboardGold
        "T1", "A" -> DashboardMint
        "T2", "B" -> DashboardIce
        else -> DashboardCoral
    }
    DetailChip(tier.uppercase(), accent)
}

@Composable
private fun KeyMinionLine(minion: KeyMinion) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = OverlayDrawerShell.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(1.dp, DashboardLine.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MinionHeadshot(
                minion = minion,
                modifier = Modifier.size(48.dp),
                borderColor = DashboardGold
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = minion.name,
                        color = OverlayDrawerText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    DetailChip("${minion.techLevel} 本", DashboardGold)
                }
                DetailChip(minion.phase, DashboardMint)
            }
        }
    }
}

@Composable
private fun MinionStrip(
    minions: List<KeyMinion>,
    borderColor: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        minions.forEach { minion ->
            MinionHeadshot(
                minion = minion,
                modifier = Modifier.size(34.dp),
                borderColor = borderColor
            )
        }
    }
}

@Composable
private fun MinionHeadshot(
    minion: KeyMinion,
    modifier: Modifier = Modifier,
    borderColor: Color = DashboardGold
) {
    val models = remember(minion.cardId, minion.imageUrl, minion.imageAsset) {
        buildList {
            minion.cardId?.trim()?.takeIf(String::isNotBlank)?.let {
                add("https://art.hearthstonejson.com/v1/256x/$it.jpg")
            }
            minion.imageUrl?.trim()?.takeIf(String::isNotBlank)?.let(::add)
            minion.imageAsset?.trim()?.takeIf(String::isNotBlank)?.let { add("file:///android_asset/$it") }
        }
    }
    var modelIndex by remember(models) { mutableStateOf(0) }
    val currentModel = models.getOrNull(modelIndex)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .border(2.dp, borderColor.copy(alpha = 0.76f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF405773), Color(0xFF16202D))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (currentModel == null) {
                MinionArtworkFallback(minion)
            } else {
                SubcomposeAsyncImage(
                    model = currentModel,
                    contentDescription = minion.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.28f),
                    contentScale = ContentScale.Crop,
                    loading = { MinionArtworkFallback(minion) },
                    error = {
                        val nextIndex = modelIndex + 1
                        if (nextIndex < models.size) {
                            LaunchedEffect(nextIndex) {
                                modelIndex = nextIndex
                            }
                        } else {
                            MinionArtworkFallback(minion)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MinionArtworkFallback(minion: KeyMinion) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF21384C), Color(0xFF0D1721))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = minion.name.take(2),
                color = OverlayDrawerText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = minion.techLevel.toString(),
                color = DashboardGold,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun strategyModeLabel(mode: String): String = when (mode.uppercase()) {
    "BOTH" -> "通用"
    "DUOS" -> "双打"
    "SOLOS" -> "单排"
    else -> mode
}

private fun tribeAccent(tribe: Tribe): Color = when (tribe) {
    Tribe.BEAST -> Color(0xFFB7F07A)
    Tribe.DEMON -> Color(0xFFFF8A69)
    Tribe.DRAGON -> Color(0xFF7ED1FF)
    Tribe.ELEMENTAL -> Color(0xFFFFC86E)
    Tribe.MECH -> Color(0xFF8FF3E8)
    Tribe.MURLOC -> Color(0xFF69E1B8)
    Tribe.NAGA -> Color(0xFF8EA6FF)
    Tribe.PIRATE -> Color(0xFFFFB37A)
    Tribe.QUILBOAR -> Color(0xFFFF8EB0)
    Tribe.UNDEAD -> Color(0xFFC4B5FF)
}
