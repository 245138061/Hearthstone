package com.bgtactician.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bgtactician.app.data.model.StrategyDataSource
import com.bgtactician.app.viewmodel.DashboardUiState

private val TavernNight = Color(0xFF0A1020)
private val TavernSky = Color(0xFF182338)
private val TavernBoard = Color(0xFF223149)
private val TavernBoardDeep = Color(0xFF1B263A)
private val TavernTrim = Color(0xFF3C4F71)
private val TavernGold = Color(0xFFFFD45B)
private val TavernGoldSoft = Color(0xFFBE8F2F)
private val TavernGoldDeep = Color(0xFF6E4A12)
private val TavernIvory = Color(0xFFFFF1C9)
private val TavernMuted = Color(0xFFBBC7DB)
private val TavernMutedSoft = Color(0xFF91A4C4)
private val TavernBlueHint = Color(0xFF73AEFF)
private val TavernSuccess = Color(0xFF6BE0A5)
private val TavernWarning = Color(0xFFFFB96C)
private val TavernDanger = Color(0xFFFF8166)
private val TavernFire = Color(0xFFFF8E3C)
private val CoinOuterDark = Color(0xFF754B12)
private val CoinInnerDark = Color(0xFF4F2B0D)
private val CoinIdleCore = Color(0xFFC58A29)
private val CoinActiveCore = Color(0xFFB95D28)
private val CoinShadow = Color(0x88241204)

@Composable
fun HomeScreen(
    uiState: DashboardUiState,
    overlayPermissionGranted: Boolean,
    screenCaptureGranted: Boolean,
    overlayRunning: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestScreenCapturePermission: () -> Unit,
    onToggleOverlay: () -> Unit,
    onRefreshData: () -> Unit
) {
    val statusColor = if (overlayRunning) TavernSuccess else TavernFire
    val statusText = if (overlayRunning) "柜台已营业" else "柜台待点亮"
    val dataVersion = uiState.catalogVersion.ifBlank { uiState.manifestVersionLabel ?: "内置资源" }
    val syncLabel = when {
        uiState.isRefreshing -> "同步中"
        uiState.lastSyncLabel != null -> uiState.lastSyncLabel
        else -> "未同步"
    }
    val launchEnabled = overlayRunning || overlayPermissionGranted
    val launchLabel = if (overlayRunning) "停止助手" else "启动助手"
    val launchHint = when {
        overlayRunning && screenCaptureGranted -> "酒馆侧栏已经就位，进入对局后可直接查看当前战术。"
        overlayRunning -> "柜台已经开门，但录屏许可还没补齐，自动识别会暂时保持等待。"
        overlayPermissionGranted && screenCaptureGranted -> "两项许可都备妥了，点亮硬币就能让柜台开门。"
        !overlayPermissionGranted && !screenCaptureGranted -> "还差两项入场许可，先补齐再点亮鲍勃的硬币。"
        !overlayPermissionGranted -> "先允许悬浮窗出现，否则柜台无法在局内展开。"
        else -> "录屏许可还没补齐，但你已经可以先让柜台亮起来。"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(TavernNight, TavernSky, Color(0xFF10192A))
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(TavernGold.copy(alpha = 0.24f), Color.Transparent),
                        center = Offset(520f, 120f),
                        radius = 1100f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(TavernBlueHint.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(120f, 980f),
                        radius = 900f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            HeroSignboardCard(
                modifier = Modifier.widthIn(max = 560.dp),
                statusText = statusText,
                statusColor = statusColor,
                appVersion = uiState.appVersionLabel,
                dataVersion = dataVersion,
                dataSource = uiState.dataSource.homeLabel(),
                syncLabel = syncLabel,
                syncMessage = uiState.syncMessage,
                refreshing = uiState.isRefreshing,
                onRefreshData = onRefreshData
            )

            LaunchCoinCard(
                modifier = Modifier.widthIn(max = 560.dp),
                launchLabel = launchLabel,
                launchHint = launchHint,
                overlayRunning = overlayRunning,
                enabled = launchEnabled,
                onToggleOverlay = onToggleOverlay
            )

            PermissionPlaqueCard(
                modifier = Modifier.widthIn(max = 560.dp),
                overlayPermissionGranted = overlayPermissionGranted,
                screenCaptureGranted = screenCaptureGranted,
                onRequestOverlayPermission = onRequestOverlayPermission,
                onRequestScreenCapturePermission = onRequestScreenCapturePermission
            )
        }
    }
}

@Composable
private fun HeroSignboardCard(
    modifier: Modifier = Modifier,
    statusText: String,
    statusColor: Color,
    appVersion: String,
    dataVersion: String,
    dataSource: String,
    syncLabel: String,
    syncMessage: String?,
    refreshing: Boolean,
    onRefreshData: () -> Unit
) {
    PlaqueCard(
        modifier = modifier,
        capTitle = "酒馆招牌",
        capAccent = TavernGold,
        glowColor = TavernGold
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SignboardOrnaments()

            Text(
                text = "酒馆助手",
                color = TavernIvory,
                style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = 1.1.sp),
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = "BATTLEGROUNDS TACTICIAN",
                color = TavernGold,
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            StatusBanner(
                statusText = statusText,
                statusColor = statusColor
            )

            LedgerPlate(
                headline = "牌桌记录",
                detail = "v$appVersion  ·  数据 $dataVersion  ·  来源 $dataSource  ·  最近同步 $syncLabel"
            )

            syncMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    color = TavernBlueHint,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onRefreshData,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, TavernGoldSoft.copy(alpha = 0.56f)),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (refreshing) "牌库同步中" else "刷新牌库",
                    color = TavernIvory,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LaunchCoinCard(
    modifier: Modifier = Modifier,
    launchLabel: String,
    launchHint: String,
    overlayRunning: Boolean,
    enabled: Boolean,
    onToggleOverlay: () -> Unit
) {
    PlaqueCard(
        modifier = modifier,
        capTitle = null,
        capAccent = if (overlayRunning) TavernFire else TavernGold,
        glowColor = TavernGold
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "点亮柜台",
                color = TavernIvory,
                style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 0.8.sp),
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            CoinActionButton(
                label = launchLabel,
                overlayRunning = overlayRunning,
                enabled = enabled,
                onClick = onToggleOverlay
            )

            HintRibbon(
                text = launchHint,
                accent = if (overlayRunning) TavernSuccess else TavernWarning
            )
        }
    }
}

@Composable
private fun PermissionPlaqueCard(
    modifier: Modifier = Modifier,
    overlayPermissionGranted: Boolean,
    screenCaptureGranted: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestScreenCapturePermission: () -> Unit
) {
    PlaqueCard(
        modifier = modifier,
        capTitle = null,
        capAccent = TavernBlueHint,
        glowColor = TavernBlueHint
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "先补齐两项许可，再让侧栏进场。",
                color = TavernMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            PermissionPlaqueItem(
                title = "悬浮窗权限",
                description = "允许酒馆战术侧栏在对局中浮在最上层。",
                granted = overlayPermissionGranted,
                accent = TavernGold,
                onRequest = onRequestOverlayPermission,
                icon = { DoorGlyph(color = TavernIvory) }
            )

            PermissionPlaqueItem(
                title = "录屏权限",
                description = "让系统读取牌桌画面，用于自动识别当前对局信息。",
                granted = screenCaptureGranted,
                accent = TavernBlueHint,
                onRequest = onRequestScreenCapturePermission,
                icon = { VisionGlyph(color = TavernIvory) }
            )
        }
    }
}

@Composable
private fun PlaqueCard(
    modifier: Modifier = Modifier,
    capTitle: String? = null,
    capAccent: Color,
    glowColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(34.dp),
            colors = CardDefaults.cardColors(containerColor = TavernBoardDeep),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.18f),
                                TavernBoard.copy(alpha = 0.94f),
                                TavernBoardDeep
                            )
                        )
                    )
                    .border(1.dp, TavernTrim.copy(alpha = 0.92f), RoundedCornerShape(34.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(5.dp)
                        .border(1.dp, TavernGoldSoft.copy(alpha = 0.18f), RoundedCornerShape(28.dp))
                )
                Rivet(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 10.dp, y = 10.dp)
                )
                Rivet(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-10).dp, y = 10.dp)
                )
                Rivet(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 10.dp, y = (-10).dp)
                )
                Rivet(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-10).dp, y = (-10).dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    content = content
                )
            }
        }

        if (!capTitle.isNullOrBlank()) {
            TopCap(
                title = capTitle,
                accent = capAccent,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun TopCap(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .offset(y = (-10).dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(TavernGoldDeep, accent.copy(alpha = 0.96f), TavernGoldDeep)
                )
            )
            .border(1.dp, TavernGold.copy(alpha = 0.68f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CapWing()
        Text(
            text = title,
            color = Color(0xFF2A1705),
            style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.3.sp),
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        CapWing()
    }
}

@Composable
private fun CapWing() {
    Canvas(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(18.dp)
            .height(10.dp)
    ) {
        val centerY = size.height / 2f
        drawLine(
            color = Color(0xFF47270B),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color(0xFF47270B),
            radius = 2.6f,
            center = Offset(size.width / 2f, centerY)
        )
    }
}

@Composable
private fun Rivet(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(TavernGoldSoft.copy(alpha = 0.28f))
            .border(1.dp, TavernGold.copy(alpha = 0.44f), CircleShape)
    )
}

@Composable
private fun SignboardOrnaments() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OrnamentLine(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(TavernGold.copy(alpha = 0.15f))
                .border(1.dp, TavernGoldSoft.copy(alpha = 0.48f), CircleShape)
        )
        OrnamentLine(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun OrnamentLine(
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .height(12.dp)
            .padding(horizontal = 4.dp)
    ) {
        val centerY = size.height / 2f
        drawLine(
            color = TavernGoldSoft.copy(alpha = 0.86f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = TavernGold.copy(alpha = 0.7f),
            radius = 2.3f,
            center = Offset(size.width * 0.18f, centerY)
        )
        drawCircle(
            color = TavernGold.copy(alpha = 0.7f),
            radius = 2.3f,
            center = Offset(size.width * 0.82f, centerY)
        )
    }
}

@Composable
private fun StatusBanner(
    statusText: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(TavernGold.copy(alpha = 0.10f), statusColor.copy(alpha = 0.16f))
                )
            )
            .border(1.dp, statusColor.copy(alpha = 0.44f), RoundedCornerShape(28.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.18f))
                .border(1.dp, TavernGoldSoft.copy(alpha = 0.56f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            DoorGlyph(color = TavernIvory)
        }

        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "柜台状态",
                color = TavernMutedSoft,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = statusText,
                color = statusColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun CoinActionButton(
    label: String,
    overlayRunning: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(250.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = TavernIvory,
            disabledContentColor = TavernMutedSoft
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(CoinShadow)
                .padding(top = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = if (overlayRunning) {
                            listOf(Color(0xFFFFD980), Color(0xFFCD742F), CoinOuterDark)
                        } else {
                            listOf(TavernGold, Color(0xFFC78B27), CoinOuterDark)
                        }
                    )
                )
                .border(2.dp, TavernGold.copy(alpha = 0.86f), CircleShape)
                    .padding(10.dp)
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val ringInset = size.minDimension * 0.06f
                    drawCircle(
                        color = TavernGold.copy(alpha = 0.78f),
                        radius = size.minDimension * 0.43f,
                        style = Stroke(width = size.minDimension * 0.016f)
                    )
                    drawCircle(
                        color = TavernGoldDeep.copy(alpha = 0.34f),
                        radius = size.minDimension * 0.37f,
                        style = Stroke(width = size.minDimension * 0.05f)
                    )
                    drawArc(
                        color = Color.White.copy(alpha = 0.20f),
                        startAngle = 206f,
                        sweepAngle = 70f,
                        useCenter = false,
                        topLeft = Offset(ringInset, ringInset),
                        size = Size(size.width - ringInset * 2f, size.height - ringInset * 2f),
                        style = Stroke(width = size.minDimension * 0.045f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = TavernGoldDeep.copy(alpha = 0.52f),
                        startAngle = 24f,
                        sweepAngle = 132f,
                        useCenter = false,
                        topLeft = Offset(ringInset, ringInset),
                        size = Size(size.width - ringInset * 2f, size.height - ringInset * 2f),
                        style = Stroke(width = size.minDimension * 0.04f, cap = StrokeCap.Round)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (overlayRunning) {
                                    listOf(Color(0xFFFFD07D), Color(0xFFBB6D2D), CoinInnerDark)
                                } else {
                                    listOf(Color(0xFFFFE59C), CoinIdleCore, CoinInnerDark)
                                }
                            )
                        )
                        .border(2.dp, TavernGoldSoft.copy(alpha = 0.74f), CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = if (overlayRunning) {
                                        listOf(Color(0xFFFFE6AB), Color(0xFFD48A42), CoinActiveCore)
                                    } else {
                                        listOf(Color(0xFFFFF0C2), Color(0xFFE2AE54), CoinIdleCore)
                                    }
                                )
                            )
                            .border(1.dp, TavernGold.copy(alpha = 0.66f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DoorGlyph(
                                modifier = Modifier.size(42.dp),
                                color = Color(0xFF2D1A08)
                            )
                            Text(
                                text = label,
                                color = Color(0xFF2A1706),
                                style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 0.4.sp),
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (overlayRunning) "收起柜台" else "点亮硬币",
                                color = Color(0xFF51320D),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerPlate(
    headline: String,
    detail: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(TavernBoard.copy(alpha = 0.92f), TavernBoardDeep)
                )
            )
            .border(1.dp, TavernGoldSoft.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = headline,
            color = TavernGold,
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.5.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
        text = detail,
        color = TavernMuted,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}
}

@Composable
private fun HintRibbon(
    text: String,
    accent: Color
) {
    Text(
        text = text,
        color = TavernIvory,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(accent.copy(alpha = 0.14f), TavernGold.copy(alpha = 0.10f))
                )
            )
            .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    )
}

@Composable
private fun PermissionPlaqueItem(
    title: String,
    description: String,
    granted: Boolean,
    accent: Color,
    onRequest: () -> Unit,
    icon: @Composable () -> Unit
) {
    val stateColor = if (granted) TavernSuccess else TavernDanger
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(TavernBoard.copy(alpha = 0.96f), TavernBoardDeep)
                ),
                shape = RoundedCornerShape(26.dp)
            )
            .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(26.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .border(1.dp, accent.copy(alpha = 0.52f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = TavernIvory,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (granted) "已备妥" else "未补齐",
                    color = stateColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = description,
            color = TavernMuted,
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedButton(
            onClick = onRequest,
            modifier = Modifier.align(Alignment.End),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.46f)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (granted) "重新检查" else "去授权",
                color = TavernIvory,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DoorGlyph(
    modifier: Modifier = Modifier.size(20.dp),
    color: Color
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.10f
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.22f, size.height * 0.18f),
            size = Size(size.width * 0.56f, size.height * 0.68f),
            cornerRadius = CornerRadius(size.width * 0.22f, size.width * 0.22f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.24f),
            end = Offset(size.width * 0.5f, size.height * 0.84f),
            strokeWidth = stroke * 0.95f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = color,
            radius = stroke * 0.56f,
            center = Offset(size.width * 0.6f, size.height * 0.52f)
        )
    }
}

@Composable
private fun VisionGlyph(
    modifier: Modifier = Modifier.size(20.dp),
    color: Color
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.10f
        drawCircle(
            color = color,
            radius = size.minDimension * 0.34f,
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.12f
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.18f, size.height * 0.82f),
            end = Offset(size.width * 0.82f, size.height * 0.18f),
            strokeWidth = stroke * 0.82f,
            cap = StrokeCap.Round
        )
    }
}

private fun StrategyDataSource.homeLabel(): String = when (this) {
    StrategyDataSource.ASSET -> "内置"
    StrategyDataSource.CACHE -> "远程缓存"
    StrategyDataSource.REMOTE -> "远程"
}
