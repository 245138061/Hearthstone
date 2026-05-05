package com.bgtactician.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.foundation.layout.statusBarsPadding
import com.bgtactician.app.data.model.StrategyDataSource
import com.bgtactician.app.viewmodel.DashboardUiState

private val TavernNight = Color(0xFF09101D)
private val TavernSky = Color(0xFF17253A)
private val TavernSmoke = Color(0xFF0F1726)
private val TavernTrim = Color(0xFF3C4F71)
private val TavernLine = Color(0x33FFD45B)
private val TavernGold = Color(0xFFFFD45B)
private val TavernGoldSoft = Color(0xFFBE8F2F)
private val TavernGoldDeep = Color(0xFF6E4A12)
private val TavernIvory = Color(0xFFFFF1C9)
private val TavernMuted = Color(0xFFB7C3D8)
private val TavernBlueHint = Color(0xFF73AEFF)
private val TavernSuccess = Color(0xFF6BE0A5)
private val TavernWarning = Color(0xFFFFB96C)
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
    overlayRunning: Boolean,
    onRequestOverlayPermission: () -> Unit,
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
        overlayRunning -> "进局后手动选择 5 种族。"
        overlayPermissionGranted -> "启动后即可在局内使用。"
        else -> "先开启悬浮窗权限。"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(TavernNight, TavernSky, TavernSmoke)
                )
            )
    ) {
        BackgroundGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 34.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            MastheadSection(
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

            LaunchStageSection(
                launchLabel = launchLabel,
                launchHint = launchHint,
                overlayRunning = overlayRunning,
                enabled = launchEnabled,
                onToggleOverlay = onToggleOverlay
            )

            PermissionRail(
                overlayPermissionGranted = overlayPermissionGranted,
                onRequestOverlayPermission = onRequestOverlayPermission
            )
        }
    }
}

@Composable
private fun BackgroundGlow() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(TavernGold.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(560f, 120f),
                    radius = 1080f
                )
            )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(TavernBlueHint.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(90f, 1040f),
                    radius = 860f
                )
            )
    )
}

@Composable
private fun MastheadSection(
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SignboardOrnaments()

        Text(
            text = "BATTLEGROUNDS TACTICIAN",
            color = TavernGold,
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.6.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "酒馆助手",
            color = TavernIvory,
            style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = 1.sp),
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        StatusBanner(
            statusText = statusText,
            statusColor = statusColor
        )

        SectionRule()

        MetaRow(
            label = "版本",
            value = "v$appVersion"
        )
        MetaRow(
            label = "数据",
            value = "$dataVersion · $dataSource · $syncLabel"
        )

        syncMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Text(
                text = message,
                color = TavernBlueHint,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedButton(
            onClick = onRefreshData,
            shape = RoundedCornerShape(999.dp),
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

@Composable
private fun LaunchStageSection(
    launchLabel: String,
    launchHint: String,
    overlayRunning: Boolean,
    enabled: Boolean,
    onToggleOverlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionRule()

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

        Text(
            text = launchHint,
            color = TavernMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        SectionRule()
    }
}

@Composable
private fun PermissionRail(
    overlayPermissionGranted: Boolean,
    onRequestOverlayPermission: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(TavernGold.copy(alpha = 0.16f))
                .border(1.dp, TavernGold.copy(alpha = 0.38f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            DoorGlyph(color = TavernIvory)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "悬浮窗权限",
                color = TavernIvory,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (overlayPermissionGranted) "已开启" else "未开启",
                color = if (overlayPermissionGranted) TavernSuccess else TavernWarning,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }

        OutlinedButton(
            onClick = onRequestOverlayPermission,
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, TavernGold.copy(alpha = 0.46f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TavernIvory)
        ) {
            Text(if (overlayPermissionGranted) "查看" else "开启")
        }
    }
}

@Composable
private fun SectionRule() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, TavernLine, TavernGold.copy(alpha = 0.5f))
                    )
                )
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(TavernGold.copy(alpha = 0.88f))
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(TavernGold.copy(alpha = 0.5f), TavernLine, Color.Transparent)
                    )
                )
        )
    }
}

@Composable
private fun MetaRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TavernGold,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = value,
            color = TavernMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CoinActionButton(
    label: String,
    overlayRunning: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(0.dp, Color.Transparent),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .size(152.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            CoinShadow.copy(alpha = if (enabled) 0.82f else 0.38f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(138.dp)) {
                val outer = if (overlayRunning) CoinActiveCore else CoinIdleCore
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            CoinOuterDark,
                            outer,
                            CoinInnerDark
                        ),
                        center = center,
                        radius = size.minDimension * 0.64f
                    ),
                    radius = size.minDimension / 2f
                )
                drawCircle(
                    color = TavernGold.copy(alpha = if (enabled) 0.78f else 0.32f),
                    radius = size.minDimension * 0.38f,
                    style = Stroke(width = size.minDimension * 0.038f)
                )
            }
            Text(
                text = label,
                color = TavernIvory.copy(alpha = if (enabled) 1f else 0.52f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatusBanner(
    statusText: String,
    statusColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(statusColor.copy(alpha = 0.14f))
            .border(1.dp, statusColor.copy(alpha = 0.48f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = statusText,
            color = statusColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun SignboardOrnaments() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OrnamentShard(scale = 1f)
        OrnamentShard(scale = 0.86f)
        OrnamentShard(scale = 1f)
    }
}

@Composable
private fun OrnamentShard(scale: Float) {
    Canvas(
        modifier = Modifier.size((28 * scale).dp, (18 * scale).dp)
    ) {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(TavernGold.copy(alpha = 0.92f), TavernGoldSoft, TavernGoldDeep)
            ),
            topLeft = Offset(0f, size.height * 0.16f),
            size = Size(size.width, size.height * 0.68f),
            cornerRadius = CornerRadius(size.height * 0.4f, size.height * 0.4f)
        )
        drawLine(
            color = TavernIvory.copy(alpha = 0.88f),
            start = Offset(size.width * 0.12f, size.height * 0.5f),
            end = Offset(size.width * 0.88f, size.height * 0.5f),
            strokeWidth = size.height * 0.11f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun DoorGlyph(
    modifier: Modifier = Modifier.size(18.dp),
    color: Color
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.24f, size.height * 0.18f),
            size = Size(size.width * 0.52f, size.height * 0.66f),
            cornerRadius = CornerRadius(size.width * 0.20f, size.width * 0.20f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.50f, size.height * 0.25f),
            end = Offset(size.width * 0.50f, size.height * 0.83f),
            strokeWidth = stroke * 0.9f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = color,
            radius = stroke * 0.52f,
            center = Offset(size.width * 0.60f, size.height * 0.52f)
        )
    }
}

private fun StrategyDataSource.homeLabel(): String = when (this) {
    StrategyDataSource.ASSET -> "内置"
    StrategyDataSource.CACHE -> "缓存"
    StrategyDataSource.REMOTE -> "远程"
}
