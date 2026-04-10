package com.bgtactician.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bgtactician.app.data.model.StrategyDataSource
import com.bgtactician.app.viewmodel.DashboardUiState

private val WorkspaceTop = Color(0xFF071019)
private val WorkspaceMid = Color(0xFF0B1621)
private val WorkspaceBottom = Color(0xFF122133)
private val HomeCard = Color(0xE6162635)
private val HomeCardBorder = Color(0x3DE4C36F)
private val HomeDivider = Color(0x1FFFFFFF)
private val HomeTextMuted = Color(0xFFAAB8C7)
private val HomeAccent = Color(0xFFE4C36F)
private val HomeSuccess = Color(0xFF4CD08D)
private val HomeWarning = Color(0xFFFFA869)
private val HomeButton = Color(0xFF1E8F73)
private val HomeButtonStop = Color(0xFF8D4335)

@Composable
fun HomeScreen(
    uiState: DashboardUiState,
    overlayPermissionGranted: Boolean,
    overlayRunning: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onToggleOverlay: () -> Unit,
    onRefreshData: () -> Unit
) {
    val permissionsReady = overlayPermissionGranted
    val serviceColor = if (overlayRunning) HomeSuccess else HomeWarning
    val serviceText = if (overlayRunning) "服务运行中..." else "服务待启动"
    val actionLabel = if (overlayRunning) "停止助手" else "启动助手"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(WorkspaceTop, WorkspaceMid, WorkspaceBottom)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(HomeAccent.copy(alpha = 0.16f), Color.Transparent),
                        radius = 900f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.widthIn(max = 520.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = HomeCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, HomeCardBorder, RoundedCornerShape(28.dp))
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "BG Helper",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "酒馆助手",
                            color = HomeAccent,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    SectionBlock(title = "状态指示") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(serviceColor)
                            )
                            Text(
                                text = serviceText,
                                color = serviceColor,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = buildString {
                                append("应用 ${uiState.appVersionLabel}")
                                append(" · ")
                                append("数据来源 ${uiState.dataSource.label()}")
                                if (uiState.manifestVersionLabel != null) {
                                    append(" · 清单 ${uiState.manifestVersionLabel}")
                                }
                                if (uiState.catalogVersion.isNotBlank()) {
                                    append(" · 数据 ${uiState.catalogVersion}")
                                }
                                if (uiState.lastSyncLabel != null) {
                                    append(" · 最近同步 ${uiState.lastSyncLabel}")
                                }
                            },
                            color = HomeTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRefreshData,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = if (uiState.isRefreshing) "正在刷新数据..." else "刷新远程数据",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        uiState.syncMessage?.let { message ->
                            Text(
                                text = message,
                                color = HomeTextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    HorizontalDivider(color = HomeDivider)

                    SectionBlock(title = "权限状态") {
                        PermissionRow(
                            label = "悬浮窗权限",
                            granted = overlayPermissionGranted,
                            onRequest = onRequestOverlayPermission
                        )
                    }

                    HorizontalDivider(color = HomeDivider)

                    Button(
                        onClick = onToggleOverlay,
                        enabled = overlayRunning || permissionsReady,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (overlayRunning) HomeButtonStop else HomeButton,
                            disabledContainerColor = Color(0xFF344554),
                            contentColor = Color.White,
                            disabledContentColor = Color(0xFFB7C3CF)
                        )
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!permissionsReady && !overlayRunning) {
                        Text(
                            text = "请先授权悬浮窗权限，再启动助手服务。",
                            color = HomeTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionBlock(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    )
}

private fun StrategyDataSource.label(): String = when (this) {
    StrategyDataSource.ASSET -> "内置"
    StrategyDataSource.CACHE -> "远程缓存"
    StrategyDataSource.REMOTE -> "远程"
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (granted) "已就绪" else "未授权",
                color = if (granted) HomeSuccess else HomeWarning,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (!granted) {
            OutlinedButton(
                onClick = onRequest,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HomeAccent)
            ) {
                Text(
                    text = "去授权",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
