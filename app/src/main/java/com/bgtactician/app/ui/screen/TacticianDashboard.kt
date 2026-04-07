package com.bgtactician.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Groups2
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bgtactician.app.data.model.AnomalyPreset
import com.bgtactician.app.data.model.KeyMinion
import com.bgtactician.app.data.model.StrategyComp
import com.bgtactician.app.data.model.Tribe
import com.bgtactician.app.viewmodel.DashboardUiState

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TacticianDashboard(
    modifier: Modifier = Modifier,
    uiState: DashboardUiState,
    overlayMode: Boolean,
    onToggleTribe: (Tribe) -> Unit,
    onSelectAnomaly: (String) -> Unit,
    onSetDuos: (Boolean) -> Unit,
    onSelectStrategy: (String) -> Unit,
    onClose: (() -> Unit)? = null
) {
    val selected = uiState.selectedStrategy

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderBlock(
            version = uiState.catalogVersion,
            overlayMode = overlayMode,
            onClose = onClose
        )

        FilterBlock(
            selectedTribes = uiState.selectedTribes,
            selectedAnomaly = uiState.selectedAnomaly,
            isDuos = uiState.isDuos,
            onToggleTribe = onToggleTribe,
            onSelectAnomaly = onSelectAnomaly,
            onSetDuos = onSetDuos
        )

        RecommendationBlock(
            strategies = uiState.strategies,
            selectedStrategyId = uiState.selectedStrategyId,
            onSelectStrategy = onSelectStrategy
        )

        DetailBlock(strategy = selected)
    }
}

@Composable
private fun HeaderBlock(
    version: String,
    overlayMode: Boolean,
    onClose: (() -> Unit)?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF112130))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.fillMaxWidth(0.82f)) {
                    Text(
                        text = "推荐引擎",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (overlayMode) {
                            "侧边抽屉版，适合战斗间隙快速点选"
                        } else {
                            "控制台预览版，先确认权限和推荐逻辑"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (overlayMode && onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = null)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusBadge("数据 $version")
                StatusBadge(if (overlayMode) "悬浮模式" else "主控台")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterBlock(
    selectedTribes: Set<Tribe>,
    selectedAnomaly: String,
    isDuos: Boolean,
    onToggleTribe: (Tribe) -> Unit,
    onSelectAnomaly: (String) -> Unit,
    onSetDuos: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1B28))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FilterAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("本局环境", style = MaterialTheme.typography.titleMedium)
            }

            Text(
                text = "勾选当前可用的 5 个种族。达到上限后会锁住新增，只允许取消。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = 5
            ) {
                Tribe.entries.forEach { tribe ->
                    val selected = selectedTribes.contains(tribe)
                    Surface(
                        modifier = Modifier
                            .size(width = 64.dp, height = 68.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onToggleTribe(tribe) },
                        color = if (selected) Color(0xFF1E705F) else Color(0xFF1C2835)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(
                                        if (selected) Color(0xFFF2C66D) else Color(0xFF33465A),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tribe.shortLabel,
                                    color = Color(0xFF08111A),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = tribe.label,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedAnomaly,
                    onValueChange = {},
                    modifier = Modifier
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable
                        )
                        .fillMaxWidth(),
                    readOnly = true,
                    label = { Text("畸变效果") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    AnomalyPreset.all.forEach { anomaly ->
                        DropdownMenuItem(
                            text = { Text(anomaly) },
                            onClick = {
                                onSelectAnomaly(anomaly)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeButton(
                    selected = !isDuos,
                    label = "我的阵容",
                    onClick = { onSetDuos(false) }
                )
                ModeButton(
                    selected = isDuos,
                    label = "队友阵容",
                    onClick = { onSetDuos(true) }
                )
            }
        }
    }
}

@Composable
private fun RecommendationBlock(
    strategies: List<StrategyComp>,
    selectedStrategyId: String?,
    onSelectStrategy: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1B28))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("当前推荐", style = MaterialTheme.typography.titleMedium)
            }

            if (strategies.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF152535),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = "没有匹配阵容。请调整种族或畸变条件。",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                strategies.forEach { strategy ->
                    val selected = strategy.id == selectedStrategyId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelectStrategy(strategy.id) },
                        color = if (selected) Color(0xFF183245) else Color(0xFF152535)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strategy.name,
                                    modifier = Modifier.fillMaxWidth(0.78f),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                TierPill(strategy.tier)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusBadge("难度 ${strategy.difficulty}")
                                StatusBadge(strategy.requiredTribes.joinToString(" + "))
                            }
                            Text(
                                text = strategy.overview,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun DetailBlock(strategy: StrategyComp?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1B28))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Star, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("战术详情", style = MaterialTheme.typography.titleMedium)
            }

            if (strategy == null) {
                Text(
                    text = "选择一套阵容后，这里会展示核心牌、升本节奏和站位提示。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Text(strategy.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            BattlePlanSection(strategy = strategy)
            MinionAtlasSection(strategy = strategy)
            UpgradeTimelineSection(strategy = strategy)
            PositioningBoardSection(strategy = strategy)
        }
    }
}

@Composable
private fun BattlePlanSection(strategy: StrategyComp) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("运营速览", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StrategyNoteCard(
                title = "前期节奏",
                body = strategy.earlyStrategy,
                accent = Color(0xFF4AB0C2)
            )
            StrategyNoteCard(
                title = "后期收束",
                body = strategy.lateStrategy,
                accent = Color(0xFFF2C66D)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MinionAtlasSection(strategy: StrategyComp) {
    val grouped = strategy.keyMinions.sortedBy { it.star }.groupBy { it.star }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("核心牌图鉴", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        grouped.forEach { (star, minions) ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF152535)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SlotBubble(text = star.toString(), accent = Color(0xFFF2C66D))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${star} 星阶段", fontWeight = FontWeight.Bold)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        minions.forEach { minion ->
                            KeyMinionCard(minion)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpgradeTimelineSection(strategy: StrategyComp) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("升本节奏", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        strategy.upgradeTurns.forEachIndexed { index, beat ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SlotBubble(text = (index + 1).toString(), accent = Color(0xFF4AB0C2))
                    if (index != strategy.upgradeTurns.lastIndex) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .width(2.dp)
                                .height(34.dp)
                                .background(Color(0xFF25465A))
                        )
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF152535)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("阶段 ${index + 1}", style = MaterialTheme.typography.labelLarge, color = Color(0xFF9FB1C4))
                        Text(beat, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PositioningBoardSection(strategy: StrategyComp) {
    val hintsBySlot = strategy.positioningHints.associateBy { it.slot }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("决赛圈站位", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF152535)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "从左到右对应 1-7 号位，标亮的是当前建议重点位。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 4
                ) {
                    (1..7).forEach { slot ->
                        val hint = hintsBySlot[slot]
                        PositionSlotCard(
                            slot = slot,
                            label = hint?.label ?: "机动位",
                            note = hint?.note ?: "按对位补位或保护主核。"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyMinionCard(minion: KeyMinion) {
    Surface(
        modifier = Modifier.width(120.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF152535)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF214257), Color(0xFF0D1722))
                        )
                    )
            ) {
                if (!minion.imageAsset.isNullOrBlank()) {
                    AsyncImage(
                        model = "file:///android_asset/${minion.imageAsset}",
                        contentDescription = minion.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("★${minion.star}", style = MaterialTheme.typography.headlineSmall)
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = RoundedCornerShape(99.dp),
                    color = Color(0xCC08111A)
                ) {
                    Text(
                        text = "${minion.star}★",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color(0xFFF2C66D),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(minion.name, fontWeight = FontWeight.Bold)
            Text(minion.phase, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PositionSlotCard(slot: Int, label: String, note: String) {
    val highlighted = label != "机动位"
    Surface(
        modifier = Modifier.width(86.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (highlighted) Color(0xFF1B3850) else Color(0xFF10202E)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SlotBubble(
                text = slot.toString(),
                accent = if (highlighted) Color(0xFFF2C66D) else Color(0xFF4C6073)
            )
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StrategyNoteCard(
    title: String,
    body: String,
    accent: Color
) {
    Surface(
        modifier = Modifier.width(168.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF152535)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        color = if (selected) Color(0xFFF2C66D) else Color(0xFF152535)
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
            "T0" -> Color(0xFFE16C3A)
            "T1" -> Color(0xFFF2C66D)
            "T2" -> Color(0xFF4AB0C2)
            else -> Color(0xFF485A6A)
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
        color = Color(0xFF1B2C3C)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}
