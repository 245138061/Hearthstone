package com.bgtactician.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bgtactician.app.data.model.Tribe
import com.bgtactician.app.data.model.StrategyDataSource
import com.bgtactician.app.viewmodel.DashboardUiState

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    uiState: DashboardUiState,
    overlayPermissionGranted: Boolean,
    notificationGranted: Boolean,
    overlayRunning: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onToggleOverlay: () -> Unit,
    onToggleTribe: (Tribe) -> Unit,
    onSelectAnomaly: (String) -> Unit,
    onSetDuos: (Boolean) -> Unit,
    onSelectStrategy: (String) -> Unit,
    onManifestUrlOverrideChange: (String) -> Unit,
    onRefreshCatalog: () -> Unit,
    onSetOverlayInteractionEnabled: (Boolean) -> Unit,
    onSetBubbleOpacityPercent: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF071017), Color(0xFF101B27), Color(0xFF152535))
                )
            ),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ElevatedCard {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "酒馆战棋战术助手",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "先在这里授予悬浮窗权限，再启动前台服务。进入炉石后点击侧边迷你球即可展开推荐面板。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatusChip(
                            label = if (overlayPermissionGranted) "悬浮权限已就绪" else "需要悬浮权限",
                            icon = Icons.Outlined.CheckCircle
                        )
                        StatusChip(
                            label = if (notificationGranted) "通知可用" else "建议开启通知",
                            icon = Icons.Outlined.Notifications
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onRequestOverlayPermission) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                            Text("授权悬浮窗")
                        }
                        if (!notificationGranted) {
                            OutlinedButton(onClick = onRequestNotificationPermission) {
                                Icon(Icons.Outlined.Notifications, contentDescription = null)
                                Text("允许通知")
                            }
                        }
                    }
                    FilledTonalButton(
                        onClick = onToggleOverlay,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (overlayRunning) {
                                Icons.Outlined.StopCircle
                            } else {
                                Icons.Outlined.PlayCircle
                            },
                            contentDescription = null
                        )
                        Text(if (overlayRunning) "停止悬浮助手" else "启动悬浮助手")
                    }
                }
            }
        }

        item {
            ElevatedCard {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("数据更新", style = MaterialTheme.typography.titleLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatusChip(
                            label = sourceLabel(uiState.dataSource),
                            icon = Icons.Outlined.CheckCircle
                        )
                        StatusChip(
                            label = if (uiState.manifestUrlOverride.isBlank()) "固定更新源" else "调试覆盖源",
                            icon = Icons.Outlined.Notifications
                        )
                        uiState.lastSyncLabel?.let { lastSync ->
                            StatusChip(
                                label = "同步 $lastSync",
                                icon = Icons.Outlined.Notifications
                            )
                        }
                        uiState.manifestVersionLabel?.let { version ->
                            StatusChip(
                                label = "Manifest $version",
                                icon = Icons.Outlined.CheckCircle
                            )
                        }
                    }
                    OutlinedTextField(
                        value = uiState.manifestUrlOverride,
                        onValueChange = onManifestUrlOverrideChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Manifest 覆盖地址（开发调试）") },
                        singleLine = true,
                        supportingText = {
                            Text(
                                text = uiState.syncMessage
                                    ?: if (uiState.effectiveManifestUrl.isBlank()) {
                                        "尚未配置默认 manifest 地址。可先填入调试覆盖地址，或在构建时设置 BGT_REMOTE_MANIFEST_URL。"
                                    } else {
                                        "应用会优先使用固定 manifest 更新源。这里只在调试时覆盖远程地址。"
                                    }
                            )
                        }
                    )
                    Text(
                        text = if (uiState.effectiveManifestUrl.isBlank()) {
                            "当前更新源：未配置"
                        } else {
                            "当前更新源：${uiState.effectiveManifestUrl}"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = onRefreshCatalog,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isRefreshing
                    ) {
                        Text(if (uiState.isRefreshing) "正在检查..." else "检查远程更新")
                    }
                }
            }
        }

        item {
            ElevatedCard {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("悬浮设置", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "关闭交互后，迷你悬浮球会变成点击穿透，只能通过前台通知恢复操作。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatusChip(
                            label = if (uiState.overlayInteractionEnabled) "当前可交互" else "当前穿透中",
                            icon = Icons.Outlined.CheckCircle
                        )
                        StatusChip(
                            label = "透明度 ${uiState.bubbleOpacityPercent}%",
                            icon = Icons.Outlined.Notifications
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(0.78f)
                        ) {
                            Text("允许点击悬浮球", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "关闭后不拦截游戏点击，需从通知栏恢复。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.padding(4.dp))
                        Switch(
                            checked = uiState.overlayInteractionEnabled,
                            onCheckedChange = onSetOverlayInteractionEnabled
                        )
                    }
                    Column {
                        Text("悬浮球透明度", style = MaterialTheme.typography.titleMedium)
                        Slider(
                            value = uiState.bubbleOpacityPercent.toFloat(),
                            onValueChange = { onSetBubbleOpacityPercent(it.toInt()) },
                            valueRange = 35f..100f
                        )
                    }
                }
            }
        }

        item {
            TacticianDashboard(
                modifier = Modifier.fillMaxWidth(),
                uiState = uiState,
                overlayMode = false,
                onToggleTribe = onToggleTribe,
                onSelectAnomaly = onSelectAnomaly,
                onSetDuos = onSetDuos,
                onSelectStrategy = onSelectStrategy
            )
        }
    }
}

private fun sourceLabel(source: StrategyDataSource): String = when (source) {
    StrategyDataSource.ASSET -> "内置数据"
    StrategyDataSource.CACHE -> "缓存数据"
    StrategyDataSource.REMOTE -> "在线更新"
}

@Composable
private fun StatusChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(color = Color(0xFF193042)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
