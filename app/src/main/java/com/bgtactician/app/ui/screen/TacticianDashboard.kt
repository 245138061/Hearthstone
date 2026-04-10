package com.bgtactician.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Groups2
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.bgtactician.app.data.model.CardRulesCatalog
import com.bgtactician.app.data.model.KeyMinion
import com.bgtactician.app.data.model.StrategyComp
import com.bgtactician.app.data.model.Tribe
import com.bgtactician.app.data.repository.MinionImageCache
import com.bgtactician.app.viewmodel.DashboardUiState
import kotlin.math.roundToInt

private val DashboardCard = Color(0xDD10202D)
private val DashboardRaised = Color(0xE0142837)
private val DashboardLine = Color(0x33DFF7FF)
private val DashboardMuted = Color(0xFFA4B8CA)
private val DashboardGold = Color(0xFFF3C86B)
private val DashboardMint = Color(0xFF6FD6C2)
private val DashboardIce = Color(0xFF7DDCFF)
private val DashboardCoral = Color(0xFFFF8A69)
private val OverlayDrawerShell = Color(0x88080D15)
private val OverlayDrawerCore = Color(0xA6101722)
private val OverlayDrawerInset = Color(0x7A111822)
private val OverlayDrawerStroke = Color(0x4D87A4C9)
private val OverlayDrawerStrokeSoft = Color(0x264B617F)
private val OverlayDrawerAccent = Color(0xFFF0C56D)
private val OverlayDrawerText = Color(0xFFF5F8FC)
private val OverlayDrawerSubtext = Color(0xFFB2C2D4)
private val OverlayDrawerWarning = Color(0xFFFFA27E)
private val OverlayDrawerActive = Color(0x1E6B8DB8)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TacticianDashboard(
    modifier: Modifier = Modifier,
    uiState: DashboardUiState,
    overlayMode: Boolean,
    tabOnlyMode: Boolean = false,
    onToggleTribe: (Tribe) -> Unit,
    onSelectStrategy: (String) -> Unit,
    overlayPermissionGranted: Boolean? = null,
    notificationGranted: Boolean? = null,
    overlayRunning: Boolean? = null,
    onRequestOverlayPermission: (() -> Unit)? = null,
    onRequestNotificationPermission: (() -> Unit)? = null,
    onToggleOverlay: (() -> Unit)? = null,
    onSetOverlayInteractionEnabled: ((Boolean) -> Unit)? = null,
    onSetBubbleOpacityPercent: ((Int) -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    val selected = uiState.selectedStrategy

    if (overlayMode || tabOnlyMode) {
        OverlaySidebarDashboard(
            modifier = modifier,
            uiState = uiState,
            selected = selected,
            showHandle = overlayMode,
            draggable = overlayMode,
            onToggleTribe = onToggleTribe,
            onSelectStrategy = onSelectStrategy,
            overlayPermissionGranted = overlayPermissionGranted,
            notificationGranted = notificationGranted,
            overlayRunning = overlayRunning,
            onRequestOverlayPermission = onRequestOverlayPermission,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onToggleOverlay = onToggleOverlay,
            onSetOverlayInteractionEnabled = onSetOverlayInteractionEnabled,
            onSetBubbleOpacityPercent = onSetBubbleOpacityPercent,
            onClose = onClose
        )
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DashboardHero(
            version = uiState.catalogVersion,
            strategyCount = uiState.strategies.size,
            selectedCount = uiState.selectedTribes.size,
            overlayMode = overlayMode,
            onClose = onClose
        )

        FilterBlock(
            selectedTribes = uiState.selectedTribes,
            onToggleTribe = onToggleTribe
        )

        RecommendationBlock(
            strategies = uiState.strategies,
            selectedStrategyId = uiState.selectedStrategyId,
            selectedTribes = uiState.selectedTribes,
            cardRules = uiState.cardRules,
            onSelectStrategy = onSelectStrategy
        )

        DetailBlock(
            strategy = selected,
            selectedTribes = uiState.selectedTribes,
            cardRules = uiState.cardRules
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OverlaySidebarDashboard(
    modifier: Modifier,
    uiState: DashboardUiState,
    selected: StrategyComp?,
    showHandle: Boolean,
    draggable: Boolean,
    onToggleTribe: (Tribe) -> Unit,
    onSelectStrategy: (String) -> Unit,
    overlayPermissionGranted: Boolean?,
    notificationGranted: Boolean?,
    overlayRunning: Boolean?,
    onRequestOverlayPermission: (() -> Unit)?,
    onRequestNotificationPermission: (() -> Unit)?,
    onToggleOverlay: (() -> Unit)?,
    onSetOverlayInteractionEnabled: ((Boolean) -> Unit)?,
    onSetBubbleOpacityPercent: ((Int) -> Unit)?,
    onClose: (() -> Unit)?
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            BattlegroundsDrawerOverlay(
                uiState = uiState,
                selected = selected,
                showHandle = showHandle,
                draggable = draggable,
                onToggleTribe = onToggleTribe,
                onSelectStrategy = onSelectStrategy,
                overlayPermissionGranted = overlayPermissionGranted,
                notificationGranted = notificationGranted,
                overlayRunning = overlayRunning,
                onRequestOverlayPermission = onRequestOverlayPermission,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onToggleOverlay = onToggleOverlay,
                onSetOverlayInteractionEnabled = onSetOverlayInteractionEnabled,
                onSetBubbleOpacityPercent = onSetBubbleOpacityPercent,
                onClose = onClose
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BattlegroundsDrawerOverlay(
    uiState: DashboardUiState,
    selected: StrategyComp?,
    showHandle: Boolean,
    draggable: Boolean,
    onToggleTribe: (Tribe) -> Unit,
    onSelectStrategy: (String) -> Unit,
    overlayPermissionGranted: Boolean?,
    notificationGranted: Boolean?,
    overlayRunning: Boolean?,
    onRequestOverlayPermission: (() -> Unit)?,
    onRequestNotificationPermission: (() -> Unit)?,
    onToggleOverlay: (() -> Unit)?,
    onSetOverlayInteractionEnabled: ((Boolean) -> Unit)?,
    onSetBubbleOpacityPercent: ((Int) -> Unit)?,
    onClose: (() -> Unit)?
) {
    var isExpanded by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) }
    var drawerOffset by remember { mutableStateOf(IntOffset.Zero) }
    val drawerBodyColor = if (selectedTab == 0) {
        OverlayDrawerCore.copy(alpha = 0.56f)
    } else {
        OverlayDrawerCore.copy(alpha = 0.64f)
    }
    val drawerBorderColor = if (selectedTab == 0) {
        OverlayDrawerStroke.copy(alpha = 0.82f)
    } else {
        OverlayDrawerStroke
    }
    val dragModifier = if (draggable) {
        Modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                drawerOffset = IntOffset(
                    x = drawerOffset.x + dragAmount.x.roundToInt(),
                    y = drawerOffset.y + dragAmount.y.roundToInt()
                )
            }
        }
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .offset { if (draggable) drawerOffset else IntOffset.Zero },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showHandle) {
            Spacer(modifier = Modifier.weight(1f))
        }

        AnimatedVisibility(
            visible = if (showHandle) isExpanded else true,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .then(if (showHandle) Modifier.fillMaxWidth(0.94f) else Modifier.fillMaxSize())
                    .fillMaxHeight(0.94f)
                    .background(
                        color = drawerBodyColor,
                        shape = if (showHandle) RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp) else RoundedCornerShape(10.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = drawerBorderColor,
                        shape = if (showHandle) RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp) else RoundedCornerShape(10.dp)
                    )
                    .padding(end = 1.dp, top = 1.dp, bottom = 1.dp)
            ) {
                DrawerHeaderBar()
                DrawerTabHeader(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    dragModifier = dragModifier
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    when (selectedTab) {
                        0 -> DrawerSetupTab(
                            uiState = uiState,
                            selectedTribes = uiState.selectedTribes,
                            onToggleTribe = onToggleTribe,
                            overlayPermissionGranted = overlayPermissionGranted,
                            notificationGranted = notificationGranted,
                            overlayRunning = overlayRunning,
                            onRequestOverlayPermission = onRequestOverlayPermission,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onToggleOverlay = onToggleOverlay,
                            onSetOverlayInteractionEnabled = onSetOverlayInteractionEnabled,
                            onSetBubbleOpacityPercent = onSetBubbleOpacityPercent
                        )
                        1 -> DrawerCompListTab(
                            strategies = uiState.strategies,
                            selectedStrategyId = uiState.selectedStrategyId,
                            selectedTribes = uiState.selectedTribes,
                            cardRules = uiState.cardRules,
                            onSelectStrategy = onSelectStrategy
                        )
                        else -> DrawerTacticalTab(
                            strategy = selected,
                            selectedTribes = uiState.selectedTribes,
                            cardRules = uiState.cardRules
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已筛选 ${uiState.strategies.size} 套",
                        color = OverlayDrawerSubtext,
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (onClose != null) {
                        Text(
                            text = "退出插件",
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onClose)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            color = OverlayDrawerSubtext,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (showHandle) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .fillMaxHeight(0.92f)
                    .then(dragModifier)
                    .background(
                        color = OverlayDrawerShell,
                        shape = RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = OverlayDrawerStrokeSoft,
                        shape = RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp)
                    )
                    .clickable { isExpanded = !isExpanded },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("抽", color = OverlayDrawerText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(if (isExpanded) "收" else "展", color = OverlayDrawerSubtext, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("屉", color = OverlayDrawerText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun DrawerTabHeader(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    dragModifier: Modifier = Modifier
) {
    val tabs = listOf(
        Icons.Outlined.FilterAlt to "设置",
        Icons.Outlined.AutoAwesome to "流派",
        Icons.Outlined.Star to "战术"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .then(dragModifier)
            .background(Color.Black.copy(alpha = 0.26f))
            .border(1.dp, OverlayDrawerStrokeSoft)
    ) {
        tabs.forEachIndexed { index, (icon, label) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (selectedTab == index) OverlayDrawerActive.copy(alpha = 0.85f) else Color.Transparent)
                    .border(
                        width = if (selectedTab == index) 1.dp else 0.dp,
                        color = if (selectedTab == index) OverlayDrawerAccent.copy(alpha = 0.5f) else Color.Transparent
                    )
                    .clickable { onTabSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (selectedTab == index) OverlayDrawerAccent else OverlayDrawerSubtext,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = label,
                        color = if (selectedTab == index) OverlayDrawerText else OverlayDrawerSubtext,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerSetupTab(
    uiState: DashboardUiState,
    selectedTribes: Set<Tribe>,
    onToggleTribe: (Tribe) -> Unit,
    overlayPermissionGranted: Boolean?,
    notificationGranted: Boolean?,
    overlayRunning: Boolean?,
    onRequestOverlayPermission: (() -> Unit)?,
    onRequestNotificationPermission: (() -> Unit)?,
    onToggleOverlay: (() -> Unit)?,
    onSetOverlayInteractionEnabled: ((Boolean) -> Unit)?,
    onSetBubbleOpacityPercent: ((Int) -> Unit)?
) {
    DrawerTabShell(
        title = "本局种族",
        subtitle = "锁 5 种族",
        badge = "${selectedTribes.size}/5",
        showHeader = false
    ) { bodyModifier ->
        BoxWithConstraints(modifier = bodyModifier.fillMaxSize()) {
            val compactHeight = maxHeight < 360.dp
            val useWideSummary = maxWidth > maxHeight || maxWidth >= 480.dp
            val tribeItemsPerRow = when {
                maxWidth >= 640.dp -> 5
                maxWidth >= 460.dp -> 4
                else -> 3
            }
            val tribeCardWidth = when {
                maxWidth >= 640.dp -> 92.dp
                maxWidth >= 460.dp -> 84.dp
                else -> 78.dp
            }
            if (useWideSummary) {
                DrawerLandscapeSetupGrid(
                    selectedTribes = selectedTribes,
                    onToggleTribe = onToggleTribe,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DrawerSetupProgressCard(
                        selectedCount = selectedTribes.size,
                        modifier = Modifier.fillMaxWidth(),
                        compactHeight = compactHeight
                    )
                    DrawerSelectedTribesCard(
                        selectedTribes = selectedTribes,
                        modifier = Modifier.fillMaxWidth(),
                        compactHeight = compactHeight
                    )

                    DrawerTribePickerSection(
                        selectedTribes = selectedTribes,
                        onToggleTribe = onToggleTribe,
                        modifier = Modifier.fillMaxWidth(),
                        compactHeight = compactHeight,
                        tribeCardWidth = tribeCardWidth,
                        maxItemsInEachRow = tribeItemsPerRow
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerLandscapeSetupGrid(
    selectedTribes: Set<Tribe>,
    onToggleTribe: (Tribe) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "本局种族",
                color = OverlayDrawerText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = OverlayDrawerAccent.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerAccent.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "${selectedTribes.size}/5",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = OverlayDrawerAccent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            gridItems(Tribe.entries, key = { it.wireName }) { tribe ->
                DrawerTribeToggleCard(
                    tribe = tribe,
                    selected = selectedTribes.contains(tribe),
                    onClick = { onToggleTribe(tribe) },
                    compactHeight = true,
                    width = androidx.compose.ui.unit.Dp.Unspecified,
                    fixedHeight = 50.dp,
                    fillWidth = true,
                    compactLabel = true
                )
            }
        }
    }
}

@Composable
private fun DrawerSetupProgressCard(
    selectedCount: Int,
    modifier: Modifier = Modifier,
    compactHeight: Boolean = false
) {
    Surface(
        modifier = modifier.heightIn(min = if (compactHeight) 82.dp else 96.dp),
        shape = RoundedCornerShape(12.dp),
        color = OverlayDrawerAccent.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerAccent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compactHeight) 9.dp else 11.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactHeight) 6.dp else 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "选择进度",
                        color = OverlayDrawerSubtext,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = if (selectedCount == 5) "已就绪" else "继续选择",
                        color = OverlayDrawerText,
                        style = if (compactHeight) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = "$selectedCount",
                    color = OverlayDrawerAccent,
                    style = if (compactHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
            }
            DrawerSelectionMeter(
                selectedCount = selectedCount,
                targetCount = 5,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = if (selectedCount == 5) "环境已锁定，直接切换下面的种族即可" else "补满 5 个种族后，判断会更稳",
                color = OverlayDrawerSubtext,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DrawerSelectedTribesCard(
    selectedTribes: Set<Tribe>,
    modifier: Modifier = Modifier,
    compactHeight: Boolean = false
) {
    Surface(
        modifier = modifier.heightIn(min = if (compactHeight) 82.dp else 110.dp),
        shape = RoundedCornerShape(12.dp),
        color = OverlayDrawerInset.copy(alpha = 0.34f),
        border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerStrokeSoft.copy(alpha = 0.68f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compactHeight) 9.dp else 11.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactHeight) 6.dp else 8.dp)
        ) {
            Text(
                text = if (selectedTribes.isEmpty()) "当前未选择种族" else "当前已选 ${selectedTribes.size} 个",
                color = OverlayDrawerSubtext,
                style = MaterialTheme.typography.labelMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (selectedTribes.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = OverlayDrawerInset.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerStrokeSoft.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "从下方选择可用种族",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            color = OverlayDrawerSubtext,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    selectedTribes.forEach { tribe ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = tribeOverlayAccent(tribe).copy(alpha = 0.16f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                tribeOverlayAccent(tribe).copy(alpha = 0.38f)
                            )
                        ) {
                            Text(
                                text = tribe.label,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                color = tribeOverlayAccent(tribe),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DrawerTribePickerSection(
    selectedTribes: Set<Tribe>,
    onToggleTribe: (Tribe) -> Unit,
    modifier: Modifier = Modifier,
    compactHeight: Boolean,
    tribeCardWidth: androidx.compose.ui.unit.Dp,
    maxItemsInEachRow: Int,
    fixedGridColumns: Int? = null,
    fixedGridCardHeight: androidx.compose.ui.unit.Dp = 60.dp
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = OverlayDrawerInset.copy(alpha = 0.34f),
        border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerStrokeSoft.copy(alpha = 0.68f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "可用种族",
                    color = OverlayDrawerText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "点击切换，最多保留 5 个",
                    color = OverlayDrawerSubtext,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (fixedGridColumns != null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(fixedGridColumns),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    gridItems(Tribe.entries, key = { it.wireName }) { tribe ->
                        DrawerTribeToggleCard(
                            tribe = tribe,
                            selected = selectedTribes.contains(tribe),
                            onClick = { onToggleTribe(tribe) },
                            compactHeight = compactHeight,
                            width = androidx.compose.ui.unit.Dp.Unspecified,
                            fixedHeight = fixedGridCardHeight,
                            fillWidth = true
                        )
                    }
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = maxItemsInEachRow,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Tribe.entries.forEach { tribe ->
                        DrawerTribeToggleCard(
                            tribe = tribe,
                            selected = selectedTribes.contains(tribe),
                            onClick = { onToggleTribe(tribe) },
                            compactHeight = compactHeight,
                            width = tribeCardWidth
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerTribeToggleCard(
    tribe: Tribe,
    selected: Boolean,
    onClick: () -> Unit,
    compactHeight: Boolean,
    width: androidx.compose.ui.unit.Dp,
    fixedHeight: androidx.compose.ui.unit.Dp? = null,
    fillWidth: Boolean = false,
    compactLabel: Boolean = false
) {
    val cardHeight = fixedHeight ?: if (compactHeight) 60.dp else 68.dp

    Surface(
        modifier = Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(width))
            .height(cardHeight)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = if (selected) {
            tribeOverlayAccent(tribe).copy(alpha = 0.18f)
        } else {
            OverlayDrawerInset.copy(alpha = 0.24f)
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) tribeOverlayAccent(tribe).copy(alpha = 0.64f) else OverlayDrawerStrokeSoft.copy(alpha = 0.72f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (compactLabel) 6.dp else 8.dp,
                    vertical = if (compactLabel) 6.dp else 8.dp
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = if (selected) tribeOverlayAccent(tribe).copy(alpha = 0.18f) else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selected) tribeOverlayAccent(tribe).copy(alpha = 0.52f) else OverlayDrawerStrokeSoft.copy(alpha = 0.78f)
                    )
                ) {
                    Text(
                        text = tribe.shortLabel,
                        modifier = Modifier.padding(
                            horizontal = if (compactLabel) 6.dp else 8.dp,
                            vertical = if (compactLabel) 2.dp else 4.dp
                        ),
                        color = if (selected) tribeOverlayAccent(tribe) else OverlayDrawerSubtext,
                        style = if (compactLabel) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = tribeOverlayAccent(tribe)
                    )
                }
            }
            Text(
                text = tribe.label,
                color = OverlayDrawerText,
                style = if (compactLabel) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DrawerSelectionMeter(
    selectedCount: Int,
    targetCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(targetCount) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        if (index < selectedCount) OverlayDrawerAccent.copy(alpha = 0.72f)
                        else OverlayDrawerStrokeSoft.copy(alpha = 0.55f)
                    )
            )
        }
    }
}

@Composable
private fun DrawerCompListTab(
    strategies: List<StrategyComp>,
    selectedStrategyId: String?,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    onSelectStrategy: (String) -> Unit
) {
    val sortedStrategies = sortStrategiesForDecision(strategies)

    if (strategies.isEmpty()) {
        DrawerTabShell(
            title = "流派清单",
            subtitle = "无匹配",
            badge = "0"
        ) { bodyModifier ->
            DrawerEmptyStateCard(
                modifier = bodyModifier,
                title = "当前无匹配",
                body = "改下环境再看。"
            )
        }
        return
    }

    DrawerTabShell(
        title = "流派清单",
        subtitle = "",
        badge = strategies.size.toString(),
        showHeader = false
    ) { bodyModifier ->
        BoxWithConstraints(modifier = bodyModifier.fillMaxSize()) {
            val columns = if (maxWidth >= 620.dp) 2 else 1

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gridItems(sortedStrategies, key = { it.id }) { strategy ->
                    DrawerCompItem(
                        strategy = strategy,
                        selected = strategy.id == selectedStrategyId,
                        selectedTribes = selectedTribes,
                        cardRules = cardRules,
                        onClick = { onSelectStrategy(strategy.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerCompItem(
    strategy: StrategyComp,
    selected: Boolean,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (selected) OverlayDrawerActive.copy(alpha = 0.88f) else OverlayDrawerInset,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) OverlayDrawerAccent else OverlayDrawerStrokeSoft
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strategy.name,
                    modifier = Modifier.weight(1f),
                    color = OverlayDrawerText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                RatingBadge(
                    rating = drawerStrategyRatingLabel(strategy.tier),
                    accent = if (selected) OverlayDrawerAccent else drawerRatingColor(strategy.tier)
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = OverlayDrawerAccent,
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.size(14.dp))
                }
            }
            StrategyMinionLanes(
                strategy = strategy,
                selectedTribes = selectedTribes,
                cardRules = cardRules,
                coreLabel = null,
                addOnLabel = null,
                coreIconSize = 34.dp,
                addOnIconSize = 24.dp,
                coreBorderColor = if (selected) OverlayDrawerAccent else OverlayDrawerStroke,
                addOnBorderColor = OverlayDrawerStrokeSoft,
                textColor = OverlayDrawerText,
                mutedColor = OverlayDrawerSubtext
            )
        }
    }
}

@Composable
private fun DrawerRatingHeader(
    rating: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = drawerRatingColor(rating)
            ) {
                Text(
                    text = rating,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = Color(0xFF08111A),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                text = when (rating) {
                    "S" -> "最高优先级"
                    "A" -> "稳定可转"
                    "B" -> "条件成型"
                    else -> "补充分组"
                },
                color = OverlayDrawerSubtext,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = "$count 套",
            color = OverlayDrawerSubtext,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DrawerCoreCardChip(minion: KeyMinion) {
    val chipLabel = localizedMinionTitle(minion) ?: minionStatusLabel(minion.statusRaw)
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = Color.Black.copy(alpha = 0.22f),
        border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerStrokeSoft)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MinionHeadshot(
                minion = minion,
                modifier = Modifier.size(24.dp),
                borderColor = OverlayDrawerAccent
            )
            Text(
                text = chipLabel,
                color = OverlayDrawerText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

private fun drawerStrategyRatingLabel(tier: String): String = when (tier.uppercase()) {
    "T0", "S" -> "S"
    "T1", "A" -> "A"
    "T2", "B" -> "B"
    "T3", "C" -> "C"
    else -> tier
}

private fun drawerRatingOrder(): List<String> = listOf("S", "A", "B", "C")

private fun drawerRatingColor(tierOrRating: String): Color = when (drawerStrategyRatingLabel(tierOrRating)) {
    "S" -> Color(0xFFE18C48)
    "A" -> OverlayDrawerAccent
    "B" -> DashboardMint
    "C" -> DashboardIce
    else -> OverlayDrawerSubtext
}

@Composable
private fun DrawerHeaderBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(OverlayDrawerAccent.copy(alpha = 0.78f), OverlayDrawerStroke.copy(alpha = 0.6f))
                )
            )
    )
}

@Composable
private fun DrawerFramedSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = OverlayDrawerInset.copy(alpha = 0.68f),
        border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerStrokeSoft.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun DrawerGlassSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = OverlayDrawerInset.copy(alpha = 0.42f),
        border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerStrokeSoft.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun DrawerTabShell(
    title: String,
    subtitle: String,
    badge: String,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    content: @Composable (Modifier) -> Unit
) {
    DrawerGlassSection(modifier = modifier.fillMaxSize()) {
        if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        color = OverlayDrawerText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = OverlayDrawerSubtext,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = OverlayDrawerAccent.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerAccent.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = OverlayDrawerAccent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = OverlayDrawerInset.copy(alpha = 0.2f),
            border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerStrokeSoft.copy(alpha = 0.6f))
        ) {
            content(Modifier.fillMaxSize().padding(10.dp))
        }
    }
}

private data class DrawerDecisionSummary(
    val label: String,
    val detail: String,
    val accent: Color
)

private data class StrategyDecisionProfile(
    val score: Int,
    val label: String,
    val detail: String,
    val accent: Color
)

@Composable
private fun DrawerDecisionSummaryCard(summary: DrawerDecisionSummary) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = summary.accent.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, summary.accent.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = summary.accent.copy(alpha = 0.18f),
                border = androidx.compose.foundation.BorderStroke(1.dp, summary.accent.copy(alpha = 0.34f))
            ) {
                Text(
                    text = summary.label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = summary.accent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                text = summary.detail,
                color = OverlayDrawerText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DrawerEmptyStateCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = OverlayDrawerInset.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerStrokeSoft.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = OverlayDrawerText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                text = body,
                color = OverlayDrawerSubtext,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DrawerCompReasonLine(
    strategy: StrategyComp,
    selected: Boolean
) {
    val summary = remember(strategy) { drawerDecisionSummary(strategy) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = summary.accent.copy(alpha = if (selected) 0.22f else 0.14f),
            border = androidx.compose.foundation.BorderStroke(1.dp, summary.accent.copy(alpha = 0.28f))
        ) {
            Text(
                text = summary.label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = summary.accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black
            )
        }
        Text(
            text = summary.detail,
            color = OverlayDrawerSubtext,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}

@Composable
private fun MainCompReasonLine(strategy: StrategyComp) {
    val summary = remember(strategy) { drawerDecisionSummary(strategy) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = summary.accent.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, summary.accent.copy(alpha = 0.24f))
        ) {
            Text(
                text = summary.label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = summary.accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black
            )
        }
        Text(
            text = summary.detail,
            color = DashboardMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}

private fun drawerDecisionSummary(strategies: List<StrategyComp>): DrawerDecisionSummary {
    if (strategies.isEmpty()) {
        return DrawerDecisionSummary(
            label = "尚未明确",
            detail = "当前环境没有匹配路线。",
            accent = OverlayDrawerSubtext
        )
    }
    val profiles = strategies.map(::strategyDecisionProfile)
    val top = profiles.first()
    val second = profiles.getOrNull(1)
    return when {
        second != null && top.score - second.score <= 1 -> DrawerDecisionSummary(
            label = "双路线",
            detail = "前两套都可观察，优先比较转型信号。",
            accent = DashboardIce
        )
        top.label == "通用过渡" -> DrawerDecisionSummary(
            label = "先别锁定",
            detail = "当前更像通用支撑局，等专属主核再定。",
            accent = OverlayDrawerSubtext
        )
        top.label == "方向清晰" || top.label == "主核明确" -> DrawerDecisionSummary(
            label = "方向偏明确",
            detail = top.detail,
            accent = top.accent
        )
        else -> DrawerDecisionSummary(
            label = "继续观察",
            detail = top.detail,
            accent = top.accent
        )
    }
}

private fun drawerDecisionSummary(strategy: StrategyComp): DrawerDecisionSummary {
    val profile = strategyDecisionProfile(strategy)
    return DrawerDecisionSummary(
        label = profile.label,
        detail = profile.detail,
        accent = profile.accent
    )
}

private fun drawerReasonText(strategy: StrategyComp): String {
    val signal = strategy.whenToCommit?.trim().orEmpty()
    if (signal.isNotBlank()) {
        return "等 ${localizeStrategyText(signal)}"
    }
    directionalCoreMinions(strategy.keyMinions, limit = 2)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" / ") { it.name }
        ?.let { return "看 $it" }
    if (genericSupportMinions(strategy.keyMinions, limit = 2).isNotEmpty()) {
        return "通用件偏多"
    }
    if (strategy.requiredTribes.isNotEmpty()) {
        return "需 ${localizedRequiredTribes(strategy.requiredTribes)}"
    }
    return when (strategy.difficulty) {
        "高" -> "高操作"
        "中" -> "条件平衡"
        else -> "成型平滑"
    }
}

private fun strategyDecisionProfile(strategy: StrategyComp): StrategyDecisionProfile {
    val rating = drawerStrategyRatingLabel(strategy.tier)
    val directional = directionalCoreMinions(strategy.keyMinions, limit = Int.MAX_VALUE)
    val generic = genericSupportMinions(strategy.keyMinions, limit = Int.MAX_VALUE)
    val hasSignal = !strategy.whenToCommit.isNullOrBlank()
    val baseScore = when (rating) {
        "S" -> 8
        "A" -> 5
        "B" -> 2
        else -> 0
    }
    val score = baseScore +
        (if (hasSignal) 3 else 0) +
        (directional.size * 2) -
        generic.size -
        when (strategy.difficulty) {
            "高" -> 2
            "中" -> 1
            else -> 0
        }

    val label = when {
        hasSignal && directional.size >= 2 -> "方向清晰"
        directional.size >= 2 && generic.isEmpty() -> "主核明确"
        directional.isEmpty() && generic.isNotEmpty() -> "通用过渡"
        rating == "S" || rating == "A" -> "继续观察"
        else -> "备选路线"
    }
    val detail = when {
        hasSignal -> "等 ${localizeStrategyText(strategy.whenToCommit.orEmpty())}"
        directional.isNotEmpty() -> "看 ${directional.take(2).joinToString(" / ") { it.name }}"
        generic.isNotEmpty() -> "当前更像通用支撑"
        strategy.requiredTribes.isNotEmpty() -> "需 ${localizedRequiredTribes(strategy.requiredTribes)}"
        else -> drawerReasonText(strategy)
    }
    val accent = when (label) {
        "方向清晰", "主核明确" -> drawerRatingColor(rating)
        "继续观察" -> DashboardIce
        "通用过渡" -> OverlayDrawerSubtext
        else -> OverlayDrawerSubtext
    }
    return StrategyDecisionProfile(
        score = score,
        label = label,
        detail = detail,
        accent = accent
    )
}

private fun sortStrategiesForDecision(strategies: List<StrategyComp>): List<StrategyComp> {
    return strategies.sortedWith(
        compareByDescending<StrategyComp> { strategyDecisionProfile(it).score }
            .thenBy { it.name }
    )
}

private fun tribeOverlayAccent(tribe: Tribe): Color = when (tribe) {
    Tribe.BEAST -> Color(0xFF87D37C)
    Tribe.DEMON -> Color(0xFFFF8A69)
    Tribe.DRAGON -> Color(0xFF7DDCFF)
    Tribe.ELEMENTAL -> Color(0xFFFFC663)
    Tribe.MECH -> Color(0xFF9FB7D6)
    Tribe.MURLOC -> Color(0xFF6FE3C2)
    Tribe.NAGA -> Color(0xFF80A6FF)
    Tribe.PIRATE -> Color(0xFFFFB36A)
    Tribe.QUILBOAR -> Color(0xFFD98AA7)
    Tribe.UNDEAD -> Color(0xFFC5B4FF)
}

private fun isGenericSupportMinion(minion: KeyMinion): Boolean {
    val normalized = minion.name.lowercase()
    return normalized.contains("brann") ||
        normalized.contains("bronzebeard") ||
        normalized.contains("titus") ||
        normalized.contains("rivendare") ||
        normalized.contains("rylak") ||
        normalized.contains("macaw") ||
        normalized.contains("drakkari") ||
        normalized.contains("baron")
}

private fun filterMinionsForLobby(
    minions: List<KeyMinion>,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog
): List<KeyMinion> {
    if (cardRules.isEmpty() || selectedTribes.isEmpty()) return minions
    return minions.filter { isMinionAllowedInLobby(it, selectedTribes, cardRules) }
}

private fun isMinionAllowedInLobby(
    minion: KeyMinion,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog
): Boolean {
    val cardId = minion.cardId ?: return true
    val rules = cardRules[cardId]?.bgsMinionTypesRules ?: return true
    val lobbyTypes = selectedTribes.mapTo(mutableSetOf()) { it.name }

    if (rules.needTypesInLobby.any { it !in lobbyTypes }) {
        return false
    }
    if (rules.bannedWithTypesInLobby.any { it in lobbyTypes }) {
        return false
    }
    return true
}

private fun directionalCoreMinions(
    minions: List<KeyMinion>,
    limit: Int
): List<KeyMinion> = directionalCoreMinions(minions, emptySet(), emptyMap(), limit)

private fun genericSupportMinions(
    minions: List<KeyMinion>,
    limit: Int
): List<KeyMinion> = genericSupportMinions(minions, emptySet(), emptyMap(), limit)

private fun alternativeSupportMinions(
    minions: List<KeyMinion>,
    limit: Int
): List<KeyMinion> = alternativeSupportMinions(minions, emptySet(), emptyMap(), limit)

private fun recommendedMinions(
    minions: List<KeyMinion>,
    limit: Int
): List<KeyMinion> = recommendedMinions(minions, emptySet(), emptyMap(), limit)

private fun cycleMinions(
    minions: List<KeyMinion>,
    limit: Int
): List<KeyMinion> = cycleMinions(minions, emptySet(), emptyMap(), limit)

private fun directionalCoreMinions(
    minions: List<KeyMinion>,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    limit: Int
): List<KeyMinion> {
    val available = filterMinionsForLobby(minions, selectedTribes, cardRules)
    return available
        .filter { it.statusRaw == "CORE" && !isGenericSupportMinion(it) }
        .ifEmpty { available.filter { !isGenericSupportMinion(it) } }
        .take(limit)
}

private fun genericSupportMinions(
    minions: List<KeyMinion>,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    limit: Int
): List<KeyMinion> {
    return filterMinionsForLobby(minions, selectedTribes, cardRules)
        .filter { isGenericSupportMinion(it) }
        .sortedWith(compareBy<KeyMinion> { it.star }.thenBy { it.name })
        .take(limit)
}

private fun alternativeSupportMinions(
    minions: List<KeyMinion>,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    limit: Int
): List<KeyMinion> {
    val available = filterMinionsForLobby(minions, selectedTribes, cardRules)
    val requiredIds = directionalCoreMinions(available, selectedTribes, cardRules, limit = Int.MAX_VALUE)
        .map { it.cardId ?: it.name }
        .toSet()
    return available
        .filter { !isGenericSupportMinion(it) }
        .filter { (it.cardId ?: it.name) !in requiredIds }
        .filter { it.statusRaw == "ADDON" || it.statusRaw == "RECOMMENDED" || it.statusRaw == "CYCLE" }
        .sortedWith(
            compareByDescending<KeyMinion> { it.finalBoardWeight ?: 0 }
                .thenBy { it.star }
                .thenBy { it.name }
        )
        .take(limit)
}

private fun recommendedMinions(
    minions: List<KeyMinion>,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    limit: Int
): List<KeyMinion> {
    return filterMinionsForLobby(minions, selectedTribes, cardRules)
        .filter { it.statusRaw == "RECOMMENDED" }
        .distinctBy { it.cardId ?: it.name }
        .take(limit)
}

private fun cycleMinions(
    minions: List<KeyMinion>,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    limit: Int
): List<KeyMinion> {
    return filterMinionsForLobby(minions, selectedTribes, cardRules)
        .filter { it.statusRaw == "CYCLE" }
        .distinctBy { it.cardId ?: it.name }
        .take(limit)
}

@Composable
private fun DrawerTacticalTab(
    strategy: StrategyComp?,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog
) {
    if (strategy == null) {
        DrawerTabShell(
            title = "战术详情",
            subtitle = "先选流派",
            badge = "${selectedTribes.size}/5"
        ) { bodyModifier ->
            DrawerEmptyStateCard(
                modifier = bodyModifier,
                title = "还没选流派",
                body = "先去流派页选一套。"
            )
        }
        return
    }

    DrawerTabShell(
        title = strategy.name,
        subtitle = strategy.requiredTribes.takeIf { it.isNotEmpty() }?.let(::localizedRequiredTribes) ?: "通用",
        badge = "难度 ${strategy.difficulty}"
    ) { bodyModifier ->
        Column(
            modifier = bodyModifier
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DrawerDecisionSummaryCard(
                summary = drawerDecisionSummary(strategy)
            )
            strategy.overview.takeIf { it.isNotBlank() }?.let { overview ->
                DrawerActionStrip(
                    title = "先记住",
                    body = localizeStrategyText(overview),
                    accent = OverlayDrawerSubtext
                )
            }
            strategy.whenToCommit?.takeIf { it.isNotBlank() }?.let { signal ->
                DrawerActionStrip(
                    title = "等这些牌",
                    body = localizeStrategyText(signal),
                    accent = OverlayDrawerAccent
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DrawerActionCard(
                    modifier = Modifier.weight(1f),
                    title = "现在做",
                    body = localizeStrategyText(strategy.earlyStrategy),
                    accent = OverlayDrawerAccent,
                    emphasize = true
                )
                DrawerActionCard(
                    modifier = Modifier.weight(1f),
                    title = "后续补完",
                    body = localizeStrategyText(strategy.lateStrategy),
                    accent = DashboardIce,
                    emphasize = false
                )
            }
            DrawerSectionChip(label = "优先找牌")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tacticalFocusMinions = (
                    directionalCoreMinions(strategy.keyMinions, selectedTribes, cardRules, limit = 3) +
                        genericSupportMinions(strategy.keyMinions, selectedTribes, cardRules, limit = 2)
                    )
                    .distinctBy { it.cardId ?: it.name }
                    .ifEmpty { filterMinionsForLobby(strategy.keyMinions, selectedTribes, cardRules).take(5) }
                tacticalFocusMinions.forEach { minion ->
                    DrawerMinionThumb(
                        modifier = Modifier.width(78.dp),
                        minion = minion
                    )
                }
            }
            val recommended = recommendedMinions(strategy.keyMinions, selectedTribes, cardRules, limit = 4)
            if (recommended.isNotEmpty()) {
                DrawerSectionChip(label = "推荐拿牌")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recommended.forEach { minion ->
                        DrawerMinionThumb(
                            modifier = Modifier.width(78.dp),
                            minion = minion
                        )
                    }
                }
            }
            val cycle = cycleMinions(strategy.keyMinions, selectedTribes, cardRules, limit = 4)
            if (cycle.isNotEmpty()) {
                DrawerSectionChip(label = "循环牌")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cycle.forEach { minion ->
                        DrawerMinionThumb(
                            modifier = Modifier.width(78.dp),
                            minion = minion
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionChip(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = OverlayDrawerAccent.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerAccent.copy(alpha = 0.24f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            color = OverlayDrawerAccent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun DrawerActionStrip(
    title: String,
    body: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                color = accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black
            )
            Text(
                text = body,
                color = OverlayDrawerText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DrawerActionCard(
    title: String,
    body: String,
    accent: Color,
    emphasize: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (emphasize) accent.copy(alpha = 0.12f) else OverlayDrawerInset.copy(alpha = 0.72f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (emphasize) accent.copy(alpha = 0.26f) else OverlayDrawerStrokeSoft
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = body,
                color = if (emphasize) OverlayDrawerText else OverlayDrawerSubtext,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DrawerMinionThumb(
    modifier: Modifier = Modifier,
    minion: KeyMinion
) {
    val displayName = localizedMinionTitle(minion)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = OverlayDrawerInset,
        border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerStrokeSoft)
    ) {
        Column(
            modifier = Modifier.padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                MinionHeadshot(
                    minion = minion,
                    modifier = Modifier.size(56.dp),
                    borderColor = OverlayDrawerAccent.copy(alpha = 0.9f)
                )
            }
            displayName?.let {
                Text(
                    text = it,
                    color = OverlayDrawerText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MiniMetaBadge(
                    text = minionStatusLabel(minion.statusRaw),
                    accent = if (isGenericSupportMinion(minion)) DashboardMint else OverlayDrawerAccent
                )
            }
        }
    }
}

@Composable
private fun DrawerSectionTitle(title: String) {
    Text(
        text = title,
        color = OverlayDrawerText,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black
    )
}

@Composable
private fun DrawerMetaRow(
    left: String,
    right: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(left, color = OverlayDrawerSubtext, style = MaterialTheme.typography.labelMedium)
        Text(
            right,
            color = if (right.contains("建议")) OverlayDrawerWarning else OverlayDrawerAccent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DashboardHero(
    version: String,
    strategyCount: Int,
    selectedCount: Int,
    overlayMode: Boolean,
    onClose: (() -> Unit)?
) {
    DashboardPanel(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF142F42), Color(0xFF11202C), Color(0xFF3B2418))
        ),
        borderColor = Color(0x55F3C86B)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0x30111B24),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DashboardGold.copy(alpha = 0.22f))
                    ) {
                        Text(
                            text = if (overlayMode) "悬浮" else "控制台",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = DashboardGold,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (overlayMode) "战斗侧栏" else "战术面板",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                        Text(
                            text = if (overlayMode) {
                                "只留当前局面最值钱的判断。"
                            } else {
                                "先锁环境，再看路线和成型件。"
                            },
                            color = DashboardMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    }
                }
                if (overlayMode && onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = null, tint = Color.White)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroMetric("版本", version.ifBlank { "内置" }, DashboardGold)
                HeroMetric("可用种族", "$selectedCount / 5", DashboardMint)
                HeroMetric("匹配阵容", strategyCount.toString(), DashboardIce)
                HeroMetric("模式", if (overlayMode) "悬浮" else "控制台", DashboardCoral)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterBlock(
    selectedTribes: Set<Tribe>,
    onToggleTribe: (Tribe) -> Unit
) {
    DashboardPanel(
        brush = Brush.linearGradient(colors = listOf(DashboardRaised, DashboardCard)),
        borderColor = DashboardLine
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHeader(
                icon = Icons.Outlined.FilterAlt,
                title = "本局环境",
                subtitle = "锁 5 种族，再判断路线"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    modifier = Modifier.width(228.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0x3317232D),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DashboardLine.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("环境状态", color = DashboardMuted, style = MaterialTheme.typography.labelMedium)
                            Text(
                                if (selectedTribes.size == 5) "可以开始判断流派" else "先补满 5 个种族",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        DashboardSelectionMeter(
                            selectedCount = selectedTribes.size,
                            targetCount = 5
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (selectedTribes.isEmpty()) {
                                MiniMetaBadge("选择可用种族", DashboardMuted)
                            } else {
                                selectedTribes.forEach { tribe ->
                                    MiniMetaBadge(tribe.label, tribeOverlayAccent(tribe))
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0x2214202A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DashboardLine.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                maxItemsInEachRow = 5
                            ) {
                                Tribe.entries.forEach { tribe ->
                                    DashboardTribeCard(
                                        tribe = tribe,
                                        selected = selectedTribes.contains(tribe),
                                        onClick = { onToggleTribe(tribe) }
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0x2214202A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DashboardLine.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "当前版本只按可用种族筛选，不再区分畸变和双打。",
                                color = DashboardMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationBlock(
    strategies: List<StrategyComp>,
    selectedStrategyId: String?,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    onSelectStrategy: (String) -> Unit
) {
    val sortedStrategies = sortStrategiesForDecision(strategies)

    DashboardPanel(
        brush = Brush.linearGradient(colors = listOf(DashboardRaised, DashboardCard)),
        borderColor = DashboardLine
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader(
                icon = Icons.Outlined.AutoAwesome,
                title = "阵容清单",
                subtitle = "按判断排序"
            )

            if (strategies.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0x6614202A)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("没有匹配流派", fontWeight = FontWeight.Bold)
                        Text("改下环境再看。", color = DashboardMuted)
                    }
                }
            } else {
                SignalCallout(
                    title = drawerDecisionSummary(sortedStrategies).label,
                    body = drawerDecisionSummary(sortedStrategies).detail,
                    accent = drawerDecisionSummary(sortedStrategies).accent
                )
                sortedStrategies.forEachIndexed { index, strategy ->
                    StrategyCommandCard(
                        rank = index + 1,
                        strategy = strategy,
                        selected = strategy.id == selectedStrategyId,
                        selectedTribes = selectedTribes,
                        cardRules = cardRules,
                        onClick = { onSelectStrategy(strategy.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayStrategyStack(
    strategies: List<StrategyComp>,
    selectedStrategyId: String?,
    selectedStrategy: StrategyComp?,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    onSelectStrategy: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (strategies.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xAA101B24)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "当前无匹配",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "改下环境再看。",
                        color = DashboardMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            return
        }

        strategies.take(3).forEachIndexed { index, strategy ->
            OverlayStrategyCard(
                rank = index + 1,
                strategy = strategy,
                selected = strategy.id == selectedStrategyId,
                isPrimary = strategy.id == selectedStrategy?.id,
                selectedTribes = selectedTribes,
                cardRules = cardRules,
                onClick = { onSelectStrategy(strategy.id) }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OverlayStrategyCard(
    rank: Int,
    strategy: StrategyComp,
    selected: Boolean,
    isPrimary: Boolean,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = if (selected) Color(0xE61A3950) else Color(0xCC111C26),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Color(0x55F3C86B) else Color(0x221B2630)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.84f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strategy.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                            RatingBadge(
                                rating = drawerStrategyRatingLabel(strategy.tier),
                                accent = if (selected) DashboardGold else DashboardIce
                            )
                        }
                        CoreMinionStrip(
                            minions = strategy.keyMinions,
                            selectedTribes = selectedTribes,
                            cardRules = cardRules,
                            iconSize = 30.dp,
                            borderColor = if (selected) DashboardGold else DashboardLine
                        )
                    }
                    SlotBubble(
                        text = rank.toString(),
                        accent = if (selected) DashboardGold else DashboardIce
                    )
                }
            }

            if (selected) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusBadge("难度 ${strategy.difficulty}")
                    strategy.upgradeTurns.firstOrNull()?.let { StatusBadge(it) }
                }

                strategy.whenToCommit?.takeIf { it.isNotBlank() }?.let { signal ->
                    SignalCallout(
                        title = "转型信号",
                        body = signal,
                        accent = if (isPrimary) DashboardGold else DashboardIce
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    filterMinionsForLobby(strategy.keyMinions, selectedTribes, cardRules)
                        .take(4)
                        .forEach { minion ->
                        OverlayMiniMinion(minion)
                    }
                }

                strategy.positioningHints.firstOrNull()?.let { hint ->
                    Text(
                        text = "${hint.slot}号位 ${hint.label}",
                        color = if (isPrimary) DashboardGold else DashboardIce,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DetailBlock(
    strategy: StrategyComp?,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog
) {
    DashboardPanel(
        brush = Brush.linearGradient(colors = listOf(DashboardRaised, DashboardCard)),
        borderColor = DashboardLine
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader(
                icon = Icons.Outlined.Star,
                title = "战术详情",
                subtitle = "看转型和拿牌"
            )

            if (strategy == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0x6614202A)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("还没选流派", fontWeight = FontWeight.Bold)
                        Text("先从上面选一套。", color = DashboardMuted)
                    }
                }
                return@Column
            }

            SignalCallout(
                title = drawerDecisionSummary(strategy).label,
                body = drawerDecisionSummary(strategy).detail,
                accent = drawerDecisionSummary(strategy).accent
            )

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0x6617232D)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(0.78f)) {
                            Text(strategy.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                localizeStrategyText(strategy.overview),
                                color = DashboardMuted,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2
                            )
                        }
                        TierPill(strategy.tier)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusBadge("难度 ${strategy.difficulty}")
                        if (strategy.requiredTribes.isNotEmpty()) {
                            StatusBadge(localizedRequiredTribes(strategy.requiredTribes))
                        }
                    }
                    strategy.whenToCommit?.takeIf { it.isNotBlank() }?.let { signal ->
                        SignalCallout(
                            title = "转型信号",
                            body = localizeStrategyText(signal),
                            accent = DashboardGold
                        )
                    }
                    if (strategy.powerLevel != null || strategy.sourcePatchNumber != null) {
                        Text(
                            text = buildString {
                                strategy.powerLevel?.let {
                                    append("原始评级 ")
                                    append(it)
                                }
                                strategy.sourcePatchNumber?.let {
                                    if (isNotEmpty()) append(" · ")
                                    append("补丁 ")
                                    append(it)
                                }
                            },
                            color = DashboardMuted,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            BattlePlanSection(strategy = strategy)
            MinionAtlasSection(strategy = strategy, selectedTribes = selectedTribes, cardRules = cardRules)
            RecommendedCardsSection(strategy = strategy, selectedTribes = selectedTribes, cardRules = cardRules)
            MissingChecklistSection(strategy = strategy, selectedTribes = selectedTribes, cardRules = cardRules)
        }
    }
}

@Composable
private fun BattlePlanSection(strategy: StrategyComp) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SubsectionTitle("打法")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StrategyNoteCard(
                modifier = Modifier.weight(1f),
                title = "现在做",
                body = localizeStrategyText(strategy.earlyStrategy),
                accent = DashboardIce
            )
            StrategyNoteCard(
                modifier = Modifier.weight(1f),
                title = "后续补完",
                body = localizeStrategyText(strategy.lateStrategy),
                accent = DashboardGold
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MissingChecklistSection(
    strategy: StrategyComp,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog
) {
    val required = directionalCoreMinions(strategy.keyMinions, selectedTribes, cardRules, limit = 3)
    val alternatives = alternativeSupportMinions(strategy.keyMinions, selectedTribes, cardRules, limit = 3)
    val supports = genericSupportMinions(strategy.keyMinions, selectedTribes, cardRules, limit = 3)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SubsectionTitle("成型检查单")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ChecklistPanel(
                title = "必须件",
                hint = if (required.isNotEmpty()) "先让路线真正成型" else "当前没有明确方向件",
                accent = DashboardGold,
                minions = required
            )
            ChecklistPanel(
                title = "可替代件",
                hint = if (alternatives.isNotEmpty()) "先补能撑住战力的替代件" else "当前没有额外替代件",
                accent = DashboardIce,
                minions = alternatives
            )
            ChecklistPanel(
                title = "通用支撑",
                hint = if (supports.isNotEmpty()) "方向明确后再补这些放大器" else "当前没有额外通用支撑",
                accent = DashboardMint,
                minions = supports
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MinionAtlasSection(
    strategy: StrategyComp,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog
) {
    val directional = directionalCoreMinions(strategy.keyMinions, selectedTribes, cardRules, limit = 4)
    val alternatives = alternativeSupportMinions(strategy.keyMinions, selectedTribes, cardRules, limit = 4)
    val supports = genericSupportMinions(strategy.keyMinions, selectedTribes, cardRules, limit = 3)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SubsectionTitle("关键牌优先级")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MainChecklistPanel(
                title = "必须件",
                hint = if (directional.isNotEmpty()) "优先让路线成型" else "当前没有明确方向件",
                accent = DashboardGold,
                minions = directional
            )
            MainChecklistPanel(
                title = "可替代件",
                hint = if (alternatives.isNotEmpty()) "中期先用这些撑住战力" else "当前没有额外替代件",
                accent = DashboardIce,
                minions = alternatives
            )
            MainChecklistPanel(
                title = "通用支撑",
                hint = if (supports.isNotEmpty()) "最后补这些通用放大器" else "当前没有额外通用支撑",
                accent = DashboardMint,
                minions = supports
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RecommendedCardsSection(
    strategy: StrategyComp,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog
) {
    val recommended = recommendedMinions(strategy.keyMinions, selectedTribes, cardRules, limit = 4)
    val cycle = cycleMinions(strategy.keyMinions, selectedTribes, cardRules, limit = 4)

    if (recommended.isEmpty() && cycle.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SubsectionTitle("补充拿牌")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MainChecklistPanel(
                title = "推荐牌",
                hint = if (recommended.isNotEmpty()) "成型后优先补这些收益牌" else "当前没有额外推荐牌",
                accent = DashboardMint,
                minions = recommended
            )
            MainChecklistPanel(
                title = "循环牌",
                hint = if (cycle.isNotEmpty()) "用来过牌、转店、补节奏" else "当前没有额外循环牌",
                accent = DashboardCoral,
                minions = cycle
            )
        }
    }
}

@Composable
private fun UpgradeTimelineSection(strategy: StrategyComp) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SubsectionTitle("节奏")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            strategy.upgradeTurns.forEachIndexed { index, beat ->
                Surface(
                    modifier = Modifier.width(132.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0x6614202A)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SlotBubble(text = (index + 1).toString(), accent = DashboardIce)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("阶段 ${index + 1}", style = MaterialTheme.typography.labelMedium, color = DashboardMuted)
                        }
                        Text(beat, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PositioningBoardSection(strategy: StrategyComp) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SubsectionTitle("站位")
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0x6614202A)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (strategy.positioningHints.isEmpty()) {
                    Text("当前没有额外站位提醒。", color = DashboardMuted, style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        strategy.positioningHints.forEach { hint ->
                            PositionSlotCard(
                                slot = hint.slot,
                                label = hint.label,
                                note = hint.note
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardPanel(
    brush: Brush,
    borderColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(28.dp))
    ) {
        Column(
            modifier = Modifier
                .background(brush)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = DashboardGold)
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                color = DashboardMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HeroMetric(title: String, value: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0x40111B24),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = DashboardMuted)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DashboardSelectionMeter(
    selectedCount: Int,
    targetCount: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(targetCount) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        if (index < selectedCount) DashboardGold.copy(alpha = 0.78f)
                        else DashboardLine.copy(alpha = 0.35f)
                    )
            )
        }
    }
}

@Composable
private fun DashboardTribeCard(
    tribe: Tribe,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(width = 82.dp, height = 76.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = if (selected) tribeOverlayAccent(tribe).copy(alpha = 0.18f) else Color(0xFF132432),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) tribeOverlayAccent(tribe).copy(alpha = 0.62f) else Color(0x2219222B)
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(
                        if (selected) tribeOverlayAccent(tribe) else Color(0xFF314558),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tribe.shortLabel,
                    color = Color(0xFF081117),
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tribe.label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StrategyCommandCard(
    rank: Int,
    strategy: StrategyComp,
    selected: Boolean,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        color = if (selected) Color(0xFF173648) else Color(0x6614202A),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Color(0x66F3C86B) else Color(0x221E2B36)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.86f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strategy.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                            RatingBadge(
                                rating = drawerStrategyRatingLabel(strategy.tier),
                                accent = if (selected) DashboardGold else DashboardIce
                            )
                        }
                        MainCompReasonLine(strategy = strategy)
                    }
                }
                SlotBubble(
                    text = rank.toString(),
                    accent = if (selected) DashboardGold else DashboardIce
                )
            }

            StrategyMinionLanes(
                strategy = strategy,
                selectedTribes = selectedTribes,
                cardRules = cardRules,
                coreLabel = null,
                addOnLabel = null,
                coreIconSize = 36.dp,
                addOnIconSize = 24.dp,
                coreBorderColor = if (selected) DashboardGold else DashboardLine,
                addOnBorderColor = DashboardLine.copy(alpha = 0.45f),
                textColor = MaterialTheme.colorScheme.onSurface,
                mutedColor = DashboardMuted
            )
        }
    }
}

@Composable
private fun RatingBadge(
    rating: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = accent,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.4f))
    ) {
        Text(
            text = rating,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = Color(0xFF08111A),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun StrategyGroupHeader(
    rating: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RatingBadge(
                rating = rating,
                accent = drawerRatingColor(rating)
            )
            Text(
                text = when (rating) {
                    "S" -> "高优先级"
                    "A" -> "稳定可转"
                    "B" -> "条件成型"
                    else -> "其他"
                },
                color = DashboardMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            text = "$count 套",
            color = DashboardMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CoreMinionStrip(
    minions: List<KeyMinion>,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    iconSize: androidx.compose.ui.unit.Dp,
    borderColor: Color
) {
    val available = filterMinionsForLobby(minions, selectedTribes, cardRules)
    val leadMinions = directionalCoreMinions(minions, selectedTribes, cardRules, limit = 4)
        .ifEmpty { available.filter { !isGenericSupportMinion(it) }.take(4) }
        .ifEmpty { available.take(4) }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        leadMinions.forEach { minion ->
            MinionHeadshot(
                minion = minion,
                modifier = Modifier.size(iconSize),
                borderColor = borderColor,
                innerPadding = 1.dp,
                artScale = 1.2f
            )
        }
    }
}

@Composable
private fun AddOnMinionStrip(
    minions: List<KeyMinion>,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    iconSize: androidx.compose.ui.unit.Dp,
    borderColor: Color
) {
    val available = filterMinionsForLobby(minions, selectedTribes, cardRules)
    val addOns = (
        genericSupportMinions(minions, selectedTribes, cardRules, limit = Int.MAX_VALUE) +
            available
                .filter { (it.statusRaw == "ADDON" || it.statusRaw == "RECOMMENDED") && !isGenericSupportMinion(it) }
                .sortedWith(compareBy<KeyMinion> { it.star }.thenBy { it.name })
        )
        .ifEmpty { available.dropWhile { it.statusRaw == "CORE" } }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        addOns.forEach { minion ->
            MinionHeadshot(
                minion = minion,
                modifier = Modifier.size(iconSize),
                borderColor = borderColor,
                innerPadding = 1.dp,
                artScale = 1.2f
            )
        }
    }
}

@Composable
private fun StrategyMinionLanes(
    strategy: StrategyComp,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    coreLabel: String?,
    addOnLabel: String?,
    coreIconSize: androidx.compose.ui.unit.Dp,
    addOnIconSize: androidx.compose.ui.unit.Dp,
    coreBorderColor: Color,
    addOnBorderColor: Color,
    textColor: Color,
    mutedColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1.2f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (coreLabel != null) {
                Text(
                    text = coreLabel,
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            CoreMinionStrip(
                minions = strategy.keyMinions,
                selectedTribes = selectedTribes,
                cardRules = cardRules,
                iconSize = coreIconSize,
                borderColor = coreBorderColor
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (addOnLabel != null) {
                Text(
                    text = addOnLabel,
                    color = mutedColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            AddOnMinionStrip(
                minions = strategy.keyMinions,
                selectedTribes = selectedTribes,
                cardRules = cardRules,
                iconSize = addOnIconSize,
                borderColor = addOnBorderColor
            )
        }
    }
}

@Composable
private fun KeyMinionCard(minion: KeyMinion) {
    val displayName = localizedMinionTitle(minion)
    Surface(
        modifier = Modifier.width(138.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0x6614202A)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                MinionHeadshot(
                    minion = minion,
                    modifier = Modifier.size(88.dp),
                    borderColor = DashboardGold
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 6.dp, top = 4.dp),
                    shape = RoundedCornerShape(99.dp),
                    color = Color(0xCC08111A)
                ) {
                    Text(
                        text = "${minion.star}★",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = DashboardGold,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            displayName?.let {
                Text(it, fontWeight = FontWeight.Bold, maxLines = 2)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (minion.phase.isNotBlank()) {
                    MiniMetaBadge(text = minion.phase, accent = DashboardIce)
                }
                MiniMetaBadge(text = minionStatusLabel(minion.statusRaw), accent = DashboardMint)
            }
            WeightMeter(
                weight = minion.finalBoardWeight,
                label = "终盘权重"
            )
        }
    }
}

@Composable
private fun OverlayMiniMinion(minion: KeyMinion) {
    val displayName = localizedMinionTitle(minion)
    Surface(
        modifier = Modifier.width(68.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0x80162633)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                MinionHeadshot(
                    minion = minion,
                    modifier = Modifier.size(52.dp),
                    borderColor = DashboardGold.copy(alpha = 0.9f)
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp),
                    shape = RoundedCornerShape(99.dp),
                    color = Color(0xCC08111A)
                ) {
                    Text(
                        text = "${minion.star}★",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = DashboardGold,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            displayName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun PositionSlotCard(slot: Int, label: String, note: String) {
    val highlighted = label != "机动位"
    Surface(
        modifier = Modifier.width(88.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (highlighted) Color(0xFF1A3950) else Color(0xFF11212D)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SlotBubble(
                text = slot.toString(),
                accent = if (highlighted) DashboardGold else Color(0xFF4C6073)
            )
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(note, style = MaterialTheme.typography.bodySmall, color = DashboardMuted)
        }
    }
}

@Composable
private fun StrategyNoteCard(
    modifier: Modifier = Modifier,
    title: String,
    body: String,
    accent: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color(0x6614202A)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(accent, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }
            Text(body, color = DashboardMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChecklistPanel(
    title: String,
    hint: String,
    accent: Color,
    minions: List<KeyMinion>
) {
    Surface(
        modifier = Modifier.widthIn(min = 220.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0x6614202A)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(accent, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, fontWeight = FontWeight.Bold)
                }
                Text(hint, color = DashboardMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (minions.isEmpty()) {
                Text("当前没有额外检查项。", color = DashboardMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    minions.forEach { minion ->
                        ChecklistMinionChip(minion = minion, accent = accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistMinionChip(
    minion: KeyMinion,
    accent: Color
) {
    val displayName = localizedMinionTitle(minion)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MinionHeadshot(
                minion = minion,
                modifier = Modifier.size(28.dp),
                borderColor = accent.copy(alpha = 0.85f)
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                displayName?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Text(
                    text = when {
                        isGenericSupportMinion(minion) -> "通用支撑"
                        minion.statusRaw == "CORE" -> "必须件"
                        else -> "可替代件"
                    },
                    color = DashboardMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MainChecklistPanel(
    title: String,
    hint: String,
    accent: Color,
    minions: List<KeyMinion>
) {
    Surface(
        modifier = Modifier.widthIn(min = 260.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0x6614202A)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(accent, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, fontWeight = FontWeight.Bold)
                }
                Text(hint, color = DashboardMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (minions.isEmpty()) {
                Text("当前没有额外检查项。", color = DashboardMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    minions.forEach { minion ->
                        MainChecklistMinionChip(minion = minion, accent = accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainChecklistMinionChip(
    minion: KeyMinion,
    accent: Color
) {
    val displayName = localizedMinionTitle(minion)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MinionHeadshot(
                minion = minion,
                modifier = Modifier.size(30.dp),
                borderColor = accent.copy(alpha = 0.85f)
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                displayName?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Text(
                    text = when {
                        isGenericSupportMinion(minion) -> "通用支撑"
                        minion.statusRaw == "CORE" -> "必须件"
                        else -> "可替代件"
                    },
                    color = DashboardMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SubsectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun SignalCallout(
    title: String,
    body: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.32f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MiniMetaBadge(text: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = accent.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.25f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun WeightMeter(weight: Int?, label: String) {
    val normalized = (weight ?: 1).coerceIn(1, 3)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = DashboardMuted,
            style = MaterialTheme.typography.labelSmall
        )
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (index < normalized) DashboardGold else DashboardLine.copy(alpha = 0.25f))
            )
        }
    }
}

@Composable
private fun SlotBubble(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(accent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFF08111A),
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun ModeButton(selected: Boolean, label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = if (selected) DashboardGold else Color(0x6614202A)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Groups2, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = if (selected) Color(0xFF08111A) else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TierPill(tier: String) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = when (tier) {
            "T0" -> DashboardCoral
            "T1" -> DashboardGold
            "T2" -> DashboardIce
            else -> Color(0xFF4C5D6A)
        }
    ) {
        Text(
            text = tier,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = Color(0xFF08111A),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatusBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = Color(0x3315212C),
        border = androidx.compose.foundation.BorderStroke(1.dp, DashboardLine)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

private fun localizedRequiredTribes(requiredTribes: List<String>): String {
    return requiredTribes.joinToString(" + ") { wireName ->
        Tribe.fromWireName(wireName)?.label ?: wireName
    }
}

private fun localizedMinionTitle(minion: KeyMinion): String? {
    return minion.name.takeUnless(::hasAsciiLetters)
}

private fun localizeStrategyText(text: String): String {
    if (text.isBlank()) return text

    return text
        .replace("all in", "梭哈", ignoreCase = true)
        .replace("APM", "高频操作")
        .replace("token", "衍生物", ignoreCase = true)
        .replace("buff", "增益", ignoreCase = true)
}

private fun minionStatusLabel(status: String?): String = when (status?.uppercase()) {
    "CORE" -> "核心"
    "ADDON" -> "补强"
    "RECOMMENDED" -> "推荐"
    "CYCLE" -> "经济"
    else -> "功能牌"
}

@Composable
private fun resolveMinionImageModel(minion: KeyMinion): Any? {
    return resolveMinionImageModels(minion).firstOrNull()
}

@Composable
private fun resolveMinionImageModels(minion: KeyMinion): List<Any> {
    val context = LocalContext.current.applicationContext
    val models by produceState(
        initialValue = MinionImageCache.resolveModels(context, minion),
        key1 = minion.cardId,
        key2 = minion.imageUrl,
        key3 = minion.imageAsset
    ) {
        MinionImageCache.ensureCached(context, minion)
        value = MinionImageCache.resolveModels(context, minion)
    }
    return models
}

@Composable
private fun StrategyHeadshot(
    strategy: StrategyComp,
    size: androidx.compose.ui.unit.Dp,
    borderColor: Color
) {
    val leadMinion = strategy.keyMinions.firstOrNull()

    if (leadMinion == null) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF30475C), Color(0xFF121B24))
                    )
                )
                .border(2.dp, borderColor.copy(alpha = 0.72f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = strategy.tier,
                color = borderColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black
            )
        }
        return
    }

    MinionHeadshot(
        minion = leadMinion,
        modifier = Modifier.size(size),
        borderColor = borderColor
    )
}

@Composable
private fun MinionHeadshot(
    minion: KeyMinion,
    modifier: Modifier = Modifier,
    borderColor: Color = DashboardGold,
    innerPadding: androidx.compose.ui.unit.Dp = 0.dp,
    artScale: Float = 1.24f
) {
    val models = resolveMinionImageModels(minion)
    var modelIndex by remember(models) { mutableStateOf(0) }
    val currentModel = models.getOrNull(modelIndex)

    if (currentModel == null) {
        MinionArtworkFallback(minion)
        return
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF405773), Color(0xFF16202D))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = currentModel,
                contentDescription = "随从头像",
                modifier = Modifier
                    .fillMaxSize()
                    .scale(artScale),
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

@Composable
private fun MinionArtwork(
    minion: KeyMinion,
    modifier: Modifier,
    contentScale: ContentScale
) {
    val models = resolveMinionImageModels(minion)
    var modelIndex by remember(models) { mutableStateOf(0) }
    val currentModel = models.getOrNull(modelIndex)

    if (currentModel == null) {
        MinionArtworkFallback(minion)
        return
    }

    SubcomposeAsyncImage(
        model = currentModel,
        contentDescription = minion.name,
        modifier = modifier,
        contentScale = contentScale,
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = minion.name.take(2),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "${minion.star}★",
                color = DashboardGold,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun hasAsciiLetters(text: String): Boolean = text.any { it in 'A'..'Z' || it in 'a'..'z' }
