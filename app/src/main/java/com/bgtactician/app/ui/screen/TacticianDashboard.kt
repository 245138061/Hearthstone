package com.bgtactician.app.ui.screen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Groups2
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.bgtactician.app.data.model.AutoDetectDebugInfo
import com.bgtactician.app.data.model.AutoDetectStatus
import com.bgtactician.app.data.model.BattlegroundCardMetadataCatalog
import com.bgtactician.app.data.model.CardRulesCatalog
import com.bgtactician.app.data.model.HeroRecommendationTier
import com.bgtactician.app.data.model.HeroStatsMatchSource
import com.bgtactician.app.data.model.KeyMinion
import com.bgtactician.app.data.model.ResolvedHeroStatOption
import com.bgtactician.app.data.model.StrategyComp
import com.bgtactician.app.data.model.StrategyCatalog
import com.bgtactician.app.data.model.StrategyDataSource
import com.bgtactician.app.data.model.Tribe
import com.bgtactician.app.data.repository.MinionImageCache
import com.bgtactician.app.data.repository.MinionLobbyFilter
import com.bgtactician.app.data.repository.RealtimeMinionRecommendationEngine
import com.bgtactician.app.viewmodel.DashboardUiState
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt
import java.util.Locale

private val DashboardCard = Color(0xDD10202D)
private val DashboardRaised = Color(0xE0142837)
private val DashboardLine = Color(0x33DFF7FF)
private val DashboardMuted = Color(0xFFA4B8CA)
private val DashboardGold = Color(0xFFF3C86B)
private val DashboardMint = Color(0xFF6FD6C2)
private val DashboardIce = Color(0xFF7DDCFF)
private val DashboardCoral = Color(0xFFFF8A69)
private val OverlayDrawerShell = Color(0xFF111929)
private val OverlayDrawerCore = Color(0xFF1C2940)
private val OverlayDrawerInset = Color(0xFF162134)
private val OverlayDrawerStroke = Color(0x7AFFD45B)
private val OverlayDrawerStrokeSoft = Color(0x4D667DA1)
private val OverlayDrawerAccent = Color(0xFFFFD45B)
private val OverlayDrawerText = Color(0xFFFFF1C9)
private val OverlayDrawerSubtext = Color(0xFFB3C2DA)
private val OverlayDrawerWarning = Color(0xFFFFA36B)
private val OverlayDrawerActive = Color(0x3D8462)
private val DashboardJson = Json { ignoreUnknownKeys = true }

private const val BUNDLED_STRATEGY_ASSET = "strategies_zerotoheroes_zhCN.json"
private const val HERO_FRAME_IMAGE_URL =
    "https://static.zerotoheroes.com/hearthstone/asset/firestone/images/bgs_hero_frame.png"
private const val HERO_CARD_ART_URL_PREFIX =
    "https://static.zerotoheroes.com/hearthstone/cardart/256x/"

private val StrategyTextAliasMap = mapOf(
    "Fleet Admiral Tethys" to "舰队上将特塞斯",
    "Tethys" to "舰队上将特塞斯",
    "Brann Bronzebeard" to "布莱恩·铜须",
    "Brann" to "布莱恩·铜须",
    "Titus Rivendare" to "提图斯·瑞文戴尔",
    "Titus" to "提图斯·瑞文戴尔",
    "Rylak Metalhead" to "重金属双头飞龙",
    "Rylak" to "重金属双头飞龙",
    "Drakkari Enchanter" to "达卡莱附魔师",
    "Drakkari" to "达卡莱附魔师",
    "Monstrous Macaw" to "巨大的金刚鹦鹉",
    "Macaw" to "巨大的金刚鹦鹉",
    "Hand of Deios" to "迪奥斯之手",
    "Hand" to "迪奥斯之手",
    "Deios" to "迪奥斯之手",
    "Deep Blue Crooner" to "深沉蓝调歌手",
    "Deep Blue" to "深沉蓝调歌手",
    "Bluesy Siren" to "蓝调海妖",
    "Flaming Enforcer" to "炽焰执行者",
    "Leeroy Jenkins" to "火车王里诺艾",
    "Leeroy" to "火车王里诺艾",
    "Deadly Spore" to "致命的孢子",
    "Spore" to "致命的孢子",
    "Silithid Burrower" to "异种虫潜伏者",
    "Silithid" to "异种虫潜伏者",
    "Goldrinn" to "戈德林",
    "Stuntdrake" to "迷雾幼龙",
    "Twilight Broodmother" to "暮光巢母",
    "Twilight Hatchling" to "暮光龙崽",
    "Twilight Hatching" to "暮光龙崽",
    "Timewarped Poet" to "时光诗人",
    "Timewarped Vaelastrasz" to "时光瓦拉斯塔兹",
    "Timewarped Prismscale" to "时光棱彩鳞片",
    "Timewarped Duskmaw" to "时光暮牙",
    "Photobomb" to "摄影炸弹",
    "Charging Czarina" to "蓄能女沙皇",
    "Czarina" to "蓄能女沙皇",
    "Whirling Lass-o-Matic" to "自动漩涡套索装置",
    "Lass-O-Matic" to "自动漩涡套索装置",
    "Shadow Dancer" to "影舞者",
    "Insatiable Ur'zul" to "贪食的乌祖尔",
    "Ur'zul" to "贪食的乌祖尔",
    "Felfire Conjurer" to "邪火咒龙",
    "Conjurer" to "邪火咒龙",
    "Tranquil Meditative" to "宁静的冥想者",
    "Meditative" to "宁静的冥想者",
    "Mrglin' Burglar" to "鱼人蟊贼",
    "Burglar" to "鱼人蟊贼",
    "Primalfin Lookout" to "蛮鱼斥候",
    "Primalfin" to "蛮鱼斥候",
    "Tad" to "塔德",
    "Azerite Empowerment" to "艾泽里特强化",
    "Time Management" to "时间管理",
    "Shiny Ring" to "闪亮的戒指",
    "Land Lubber" to "旱地元素",
    "Lubber" to "旱地元素",
    "Snow Elemental" to "冰雪元素",
    "Strike Oil" to "钻探原油",
    "Oil" to "钻探原油",
    "Ballers" to "投球手",
    "Lord of the Ruins" to "废墟领主",
    "Lord of Ruins" to "废墟领主",
    "Lord" to "废墟领主",
    "Ruins" to "废墟领主",
    "Burgeoning Whelp" to "茁壮幼龙",
    "Whelp" to "萌芽雏龙",
    "Foodie" to "美食家",
    "Nalaa the Redeemer" to "救赎者娜拉",
    "Nalaa" to "救赎者娜拉",
    "Mutanus the Devourer" to "吞噬者穆坦努斯",
    "Mutanus" to "吞噬者穆坦努斯",
    "Fire-forged Evoker" to "火铸唤魔师",
    "Evoker" to "火铸唤魔师",
    "Amalgam" to "融合怪",
    "Arid Atrocity" to "旱地凶怪",
    "Atrocity" to "旱地凶怪",
    "Famished Felbat" to "饥饿的魔蝠",
    "Felbat" to "饥饿的魔蝠",
    "Nightmare Par-Tea Guest" to "梦魇茶客",
    "Par-Tea Guest" to "梦魇茶客",
    "Iridescent Skyblazer" to "炫彩灼天者",
    "Skyblazer" to "炫彩灼天者",
    "Spiked Savior" to "尖角救星",
    "Apexis" to "埃匹希斯",
    "Magicfin" to "魔鳍鱼"
)

private object BundledCardNameRegistry {
    @Volatile
    private var cachedNames: Map<String, String>? = null

    fun get(context: Context): Map<String, String> {
        cachedNames?.let { return it }
        return synchronized(this) {
            cachedNames?.let { return@synchronized it }
            val parsed = runCatching {
                DashboardJson.decodeFromString<StrategyCatalog>(
                    context.assets.open(BUNDLED_STRATEGY_ASSET).bufferedReader().use { it.readText() }
                )
                    .comps
                    .flatMap { it.keyMinions }
                    .mapNotNull { minion ->
                        minion.cardId
                            ?.takeIf { it.isNotBlank() }
                            ?.let { cardId -> cardId to minion.name }
                    }
                    .toMap()
            }.getOrDefault(emptyMap())
            cachedNames = parsed
            parsed
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TacticianDashboard(
    modifier: Modifier = Modifier,
    uiState: DashboardUiState,
    overlayMode: Boolean,
    tabOnlyMode: Boolean = false,
    onSelectStrategy: (String) -> Unit,
    onSelectHero: ((ResolvedHeroStatOption) -> Unit)? = null,
    onApplySessionTribes: ((Set<Tribe>) -> Unit)? = null,
    onTriggerAutoDetect: (() -> Unit)? = null,
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
            onSelectStrategy = onSelectStrategy,
            onSelectHero = onSelectHero,
            onApplySessionTribes = onApplySessionTribes,
            onTriggerAutoDetect = onTriggerAutoDetect,
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
            autoDetectStatus = uiState.autoDetectStatus,
            autoDetectDebugInfo = uiState.autoDetectDebugInfo,
            recognizedHeroes = uiState.recognizedHeroes,
            heroStatsUpdatedAtLabel = uiState.heroStatsUpdatedAtLabel
        )

        RecommendationBlock(
            strategies = uiState.strategies,
            selectedStrategyId = uiState.selectedStrategyId,
            selectedHero = uiState.selectedHero,
            selectedTribes = uiState.selectedTribes,
            cardRules = uiState.cardRules,
            cardMetadata = uiState.cardMetadata,
            onSelectStrategy = onSelectStrategy
        )

        DetailBlock(
            strategy = selected,
            selectedTribes = uiState.selectedTribes,
            cardRules = uiState.cardRules,
            cardMetadata = uiState.cardMetadata
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
    onSelectStrategy: (String) -> Unit,
    onSelectHero: ((ResolvedHeroStatOption) -> Unit)?,
    onApplySessionTribes: ((Set<Tribe>) -> Unit)?,
    onTriggerAutoDetect: (() -> Unit)?,
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
                onSelectStrategy = onSelectStrategy,
                onSelectHero = onSelectHero,
                onApplySessionTribes = onApplySessionTribes,
                onTriggerAutoDetect = onTriggerAutoDetect,
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
    onSelectStrategy: (String) -> Unit,
    onSelectHero: ((ResolvedHeroStatOption) -> Unit)?,
    onApplySessionTribes: ((Set<Tribe>) -> Unit)?,
    onTriggerAutoDetect: (() -> Unit)?,
    onClose: (() -> Unit)?
) {
    var isExpanded by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) }
    var drawerOffset by remember { mutableStateOf(IntOffset.Zero) }
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .offset { if (draggable) drawerOffset else IntOffset.Zero }
    ) {
        val isLandscape = maxWidth > maxHeight
        val drawerModifier = when {
            !showHandle -> Modifier.fillMaxSize()
            isLandscape -> Modifier
                .fillMaxWidth(0.46f)
                .widthIn(min = 360.dp, max = 560.dp)
                .fillMaxHeight(0.96f)
            else -> Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.94f)
        }
        Row(
            modifier = Modifier.fillMaxSize(),
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
                        .then(drawerModifier)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    OverlayDrawerAccent.copy(alpha = 0.12f),
                                    OverlayDrawerCore,
                                    OverlayDrawerInset
                                )
                            ),
                            shape = if (showHandle) RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp) else RoundedCornerShape(24.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = OverlayDrawerStroke,
                            shape = if (showHandle) RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp) else RoundedCornerShape(24.dp)
                        )
                        .padding(end = 1.dp, top = 1.dp, bottom = 1.dp)
                ) {
                    DrawerHeaderBar(
                        status = uiState.autoDetectStatus,
                        selectedTab = selectedTab
                    )
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
                                showRecognizedHeroesCard = !showHandle,
                                onSelectHero = onSelectHero,
                                onApplySessionTribes = onApplySessionTribes,
                                onTriggerAutoDetect = onTriggerAutoDetect
                            )
                            1 -> DrawerCompListTab(
                                strategies = uiState.strategies,
                                selectedStrategyId = uiState.selectedStrategyId,
                                selectedHero = uiState.selectedHero,
                                selectedTribes = uiState.selectedTribes,
                                cardRules = uiState.cardRules,
                                cardMetadata = uiState.cardMetadata,
                                onSelectStrategy = onSelectStrategy
                            )
                            else -> DrawerTacticalTab(
                                strategy = selected,
                                selectedTribes = uiState.selectedTribes,
                                selectedHero = uiState.selectedHero,
                                autoDetectDebugInfo = uiState.autoDetectDebugInfo,
                                cardRules = uiState.cardRules,
                                cardMetadata = uiState.cardMetadata
                            )
                        }
                    }
                }
            }

            if (showHandle) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .fillMaxHeight(0.88f)
                        .then(dragModifier)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    OverlayDrawerAccent.copy(alpha = 0.32f),
                                    OverlayDrawerShell,
                                    OverlayDrawerInset
                                )
                            ),
                            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = OverlayDrawerStroke,
                            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                        )
                        .clickable { isExpanded = !isExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(OverlayDrawerAccent.copy(alpha = 0.24f))
                                .border(1.dp, OverlayDrawerAccent.copy(alpha = 0.52f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("酒", color = OverlayDrawerAccent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(if (isExpanded) "收" else "展", color = OverlayDrawerText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("柜", color = OverlayDrawerSubtext, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("台", color = OverlayDrawerSubtext, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
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
    val tabs = listOf("设置", "流派", "战术")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .then(dragModifier)
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(OverlayDrawerShell.copy(alpha = 0.64f))
    ) {
        tabs.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selectedTab == index) {
                            Brush.verticalGradient(
                                colors = listOf(
                                    OverlayDrawerAccent.copy(alpha = 0.12f),
                                    OverlayDrawerActive.copy(alpha = 0.72f)
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Transparent)
                            )
                        }
                    )
                    .clickable { onTabSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selectedTab == index) OverlayDrawerText else OverlayDrawerSubtext,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selectedTab == index) FontWeight.Black else FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DrawerSetupTab(
    uiState: DashboardUiState,
    selectedTribes: Set<Tribe>,
    showRecognizedHeroesCard: Boolean,
    onSelectHero: ((ResolvedHeroStatOption) -> Unit)?,
    onApplySessionTribes: ((Set<Tribe>) -> Unit)?,
    onTriggerAutoDetect: (() -> Unit)?
) {
    DrawerTabShell(
        title = "种族",
        subtitle = "点击高亮切换",
        badge = "${selectedTribes.size}/5",
        showHeader = false
    ) { bodyModifier ->
        Column(
            modifier = bodyModifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DrawerSimpleTribeSelector(
                selectedTribes = selectedTribes,
                onApplySessionTribes = onApplySessionTribes,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            DrawerSetupAiResultFooter(
                autoDetectStatus = uiState.autoDetectStatus,
                autoDetectDebugInfo = uiState.autoDetectDebugInfo,
                recognizedHeroes = uiState.recognizedHeroes,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DrawerSimpleTribeSelector(
    selectedTribes: Set<Tribe>,
    onApplySessionTribes: ((Set<Tribe>) -> Unit)?,
    modifier: Modifier = Modifier
) {
    var draftTribes by remember(selectedTribes) { mutableStateOf(selectedTribes) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = OverlayDrawerInset.copy(alpha = 0.92f)
    ) {
        val tribeScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DashboardIce.copy(alpha = 0.06f),
                            OverlayDrawerCore.copy(alpha = 0.12f)
                        )
                    )
                )
                .verticalScroll(tribeScrollState)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "高亮表示已选，再点取消",
                color = OverlayDrawerSubtext,
                style = MaterialTheme.typography.labelSmall
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Tribe.entries.forEach { tribe ->
                    val selected = tribe in draftTribes
                    Surface(
                        modifier = Modifier.clickable {
                            val nextDraft = when {
                                selected -> draftTribes - tribe
                                draftTribes.size >= 5 -> draftTribes
                                else -> draftTribes + tribe
                            }
                            draftTribes = nextDraft
                            if (nextDraft.size == 5) {
                                onApplySessionTribes?.invoke(nextDraft)
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) {
                            tribeOverlayAccent(tribe).copy(alpha = 0.20f)
                        } else {
                            OverlayDrawerCore.copy(alpha = 0.78f)
                        }
                    ) {
                        Text(
                            text = tribe.label,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = if (selected) tribeOverlayAccent(tribe) else OverlayDrawerSubtext,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = "${draftTribes.size}/5",
                color = if (draftTribes.size == 5) DashboardMint else OverlayDrawerWarning,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DrawerSetupAiResultFooter(
    autoDetectStatus: AutoDetectStatus,
    autoDetectDebugInfo: AutoDetectDebugInfo,
    recognizedHeroes: List<ResolvedHeroStatOption>,
    modifier: Modifier = Modifier
) {
    val tribeText = autoDetectDebugInfo.recognizedTribesLabel
        ?.takeIf { it.isNotBlank() }
        ?: if (autoDetectStatus == AutoDetectStatus.SCANNING) {
            "识别中"
        } else {
            "未识别"
        }
    val aiHeroText = autoDetectDebugInfo.aiHeroesLabel
        ?.split(" / ")
        ?.map { segment ->
            segment
                .substringAfter(":", segment)
                .substringBefore(" [")
                .trim()
        }
        ?.filter { it.isNotBlank() }
        ?.joinToString(" / ")
        ?.takeIf { it.isNotBlank() }
    val heroText = aiHeroText
        ?: recognizedHeroes
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" / ") { it.displayName }
        ?: if (autoDetectStatus == AutoDetectStatus.SCANNING) {
            "识别中"
        } else {
            "未识别"
        }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = OverlayDrawerCore.copy(alpha = 0.34f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI 识别",
                color = OverlayDrawerText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "种族 $tribeText",
                modifier = Modifier.weight(1f),
                color = OverlayDrawerSubtext,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "英雄 $heroText",
                modifier = Modifier.weight(1.2f),
                color = OverlayDrawerSubtext,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HeroSelectionFloatingOverlay(
    heroes: List<ResolvedHeroStatOption>,
    selectedHero: ResolvedHeroStatOption?,
    onClose: (() -> Unit)?,
    onSelectHero: ((ResolvedHeroStatOption) -> Unit)?,
    previewMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val overlayWidthPx = with(density) { maxWidth.roundToPx() }
        val overlayHeightPx = with(density) { maxHeight.roundToPx() }
        val isLandscape = maxWidth > maxHeight
        val cardWidth = when {
            previewMode && isLandscape -> (maxWidth * 0.095f).coerceIn(64.dp, 86.dp)
            previewMode -> (maxWidth * 0.16f).coerceIn(70.dp, 92.dp)
            // 正式浮层优先不挡住英雄名和重掷按钮，横屏卡片进一步压窄。
            isLandscape -> (maxWidth * 0.102f).coerceIn(88.dp, 108.dp)
            else -> (maxWidth * 0.22f).coerceIn(112.dp, 150.dp)
        }
        val cardWidthPx = with(density) { cardWidth.roundToPx() }
        val anchorFractions = if (isLandscape) {
            // 英雄选择页 4 个框位是固定的，这里按实机截图重新校正四个槽位中心点。
            listOf(0.229f, 0.402f, 0.598f, 0.772f)
        } else {
            listOf(0.19f, 0.395f, 0.605f, 0.81f)
        }
        // 上提一点，避免正式浮层继续压住英雄名和底部交互区。
        val topFraction = if (isLandscape) 0.458f else 0.5f
        val closeYOffset = if (isLandscape) 92.dp else 110.dp

        Box(modifier = Modifier.fillMaxSize()) {
            heroes.sortedBy { it.slot }.forEach { hero ->
                val slotIndex = hero.slot.coerceIn(0, anchorFractions.lastIndex)
                val centerX = (overlayWidthPx * anchorFractions[slotIndex]).toInt()
                val topY = (overlayHeightPx * topFraction).toInt()
                HeroSelectionFloatingCard(
                    hero = hero,
                    selected = selectedHero?.slot == hero.slot,
                    previewMode = previewMode,
                    onClick = onSelectHero?.let { callback -> { callback(hero) } },
                    modifier = Modifier
                        .absoluteOffset {
                            IntOffset(
                                x = (centerX - cardWidthPx / 2).coerceIn(0, (overlayWidthPx - cardWidthPx).coerceAtLeast(0)),
                                y = topY
                            )
                        }
                        .width(cardWidth)
                )
            }

            if (onClose != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = closeYOffset, end = 22.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onClose),
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xCC15120F),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFF4DA))
                ) {
                    Text(
                        text = "关闭",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = Color(0xFFF8F0DF),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroSelectionFloatingCard(
    hero: ResolvedHeroStatOption,
    selected: Boolean,
    previewMode: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val accent = when {
        selected -> Color(0xFF58D67E)
        hero.hasStats -> Color(0xFF4FC3F7)
        else -> Color(0xFFB0BEC5)
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() }),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xE62A231C),
        border = androidx.compose.foundation.BorderStroke(2.dp, accent.copy(alpha = 0.95f)),
        tonalElevation = 8.dp,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (previewMode) 6.dp else 8.dp,
                vertical = if (previewMode) 5.dp else 7.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (previewMode) 3.dp else 4.dp)
        ) {
            Text(
                text = hero.displayName,
                color = Color(0xFFF8F0DF),
                style = if (previewMode) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = heroRecommendationLabel(hero),
                color = recommendationTone(hero),
                style = if (previewMode) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = heroFloatingPrimaryLine(hero),
                color = Color(0xFFEFE7D7),
                style = if (previewMode) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = if (previewMode) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!previewMode) {
                heroFloatingDetailLine(hero)?.let { detailLine ->
                    Text(
                        text = detailLine,
                        color = Color(0xFFF3C86B),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStatsSummaryCard(
    recognizedHeroes: List<ResolvedHeroStatOption>,
    heroStatsUpdatedAtLabel: String?
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("英雄推荐", color = DashboardMuted, style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = if (recognizedHeroes.isEmpty()) "等待英雄识别" else "已识别 ${recognizedHeroes.size} 个英雄",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black
                    )
                }
                heroStatsUpdatedAtLabel?.let { updatedAt ->
                    Text(
                        text = updatedAt,
                        color = DashboardMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (recognizedHeroes.isEmpty()) {
                Text(
                    text = "识别到英雄后，这里会根据当前五族和可玩阵容，给出直接可执行的英雄推荐。",
                    color = DashboardMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                val bestScore = recognizedHeroes.maxOfOrNull { it.recommendation?.score ?: Int.MIN_VALUE }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recognizedHeroes.forEach { hero ->
                        HeroStatsSummaryItem(
                            hero = hero,
                            highlight = hero.recommendation?.score == bestScore
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStatsSummaryItem(
    hero: ResolvedHeroStatOption,
    highlight: Boolean
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (highlight) DashboardMint.copy(alpha = 0.08f) else Color(0x331A2530),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (highlight) DashboardMint.copy(alpha = 0.28f) else DashboardLine.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SlotBubble(
                        text = (hero.slot + 1).toString(),
                        accent = if (highlight) DashboardMint else DashboardIce
                    )
                    Text(
                        text = hero.displayName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
                Text(
                    text = heroRecommendationLabel(hero),
                    color = recommendationTone(hero),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = hero.recommendation?.summary ?: "等待推荐结果",
                color = DashboardMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = buildString {
                    append(hero.recommendation?.reason ?: "缺少足够数据")
                    hero.recommendation?.recommendedCompName?.let {
                        append(" · ")
                        append(it)
                    }
                },
                color = if (highlight) DashboardIce else DashboardMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun heroRecommendationLabel(hero: ResolvedHeroStatOption): String {
    return when (hero.recommendation?.tier) {
        HeroRecommendationTier.TOP_PICK -> "首选"
        HeroRecommendationTier.GOOD_PICK -> "可选"
        HeroRecommendationTier.NICHE -> "偏功能"
        HeroRecommendationTier.AVOID -> "不推荐"
        null -> "待定"
    }
}

private fun recommendationTone(hero: ResolvedHeroStatOption): Color {
    return when (hero.recommendation?.tier) {
        HeroRecommendationTier.TOP_PICK -> Color(0xFF7CFF96)
        HeroRecommendationTier.GOOD_PICK -> Color(0xFF67D6FF)
        HeroRecommendationTier.NICHE -> Color(0xFFFFE082)
        HeroRecommendationTier.AVOID -> Color(0xFFFF8A80)
        null -> Color(0xFFB0BEC5)
    }
}

private fun environmentSyncLabel(
    selectedTribes: Set<Tribe>,
    autoDetectStatus: AutoDetectStatus
): String {
    return when {
        autoDetectStatus == AutoDetectStatus.LOCKED && selectedTribes.size == 5 -> "本轮已同步"
        selectedTribes.size == 5 -> "沿用上次环境"
        autoDetectStatus == AutoDetectStatus.SCANNING -> "识别进行中"
        else -> "等待识别同步"
    }
}

private fun environmentSyncHint(
    selectedTribes: Set<Tribe>,
    autoDetectStatus: AutoDetectStatus
): String {
    return when {
        autoDetectStatus == AutoDetectStatus.LOCKED && selectedTribes.size == 5 ->
            "当前推荐会严格按本轮已同步的 5 个种族过滤，不再提供手动改种族入口。"
        selectedTribes.size == 5 ->
            "本轮识别尚未锁定，当前推荐暂时沿用上次已保存的 5 个种族。"
        autoDetectStatus == AutoDetectStatus.SCANNING ->
            "正在采集当前画面，识别完成后会自动更新当前 5 个种族。"
        else ->
            "当前还没有同步到稳定的 5 个种族。请使用 AI 识别入口完成同步。"
    }
}

private fun heroFallbackLine(hero: ResolvedHeroStatOption): String? {
    return hero.recommendation?.fallbackCompName?.takeIf { it.isNotBlank() }?.let { "备选：$it" }
}

private fun heroFloatingPrimaryLine(hero: ResolvedHeroStatOption): String {
    val summary = hero.recommendation?.summary?.trim().orEmpty()
    if (summary.isNotBlank()) return summary

    val reason = hero.recommendation?.reason?.trim().orEmpty()
    if (reason.isNotBlank()) return reason

    return hero.recommendation?.recommendedCompName?.takeIf { it.isNotBlank() } ?: "等待推荐"
}

private fun heroFloatingDetailLine(hero: ResolvedHeroStatOption): String? {
    heroFallbackLine(hero)?.let { return it }
    val reason = hero.recommendation?.reason?.trim().orEmpty()
    return reason.takeIf { it.isNotBlank() }
}

@Composable
private fun DrawerCompListTab(
    strategies: List<StrategyComp>,
    selectedStrategyId: String?,
    selectedHero: ResolvedHeroStatOption?,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    cardMetadata: BattlegroundCardMetadataCatalog,
    onSelectStrategy: (String) -> Unit
) {
    val sortedStrategies = sortStrategiesForDecision(strategies, selectedHero)

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
        LazyColumn(
            modifier = bodyModifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            lazyItems(sortedStrategies, key = { it.id }) { strategy ->
                DrawerCompItem(
                    strategy = strategy,
                    selected = strategy.id == selectedStrategyId,
                    selectedTribes = selectedTribes,
                    cardRules = cardRules,
                    cardMetadata = cardMetadata,
                    onClick = { onSelectStrategy(strategy.id) }
                )
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
    cardMetadata: BattlegroundCardMetadataCatalog,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = if (selected) OverlayDrawerActive.copy(alpha = 0.94f) else OverlayDrawerInset.copy(alpha = 0.94f)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = if (selected) {
                            listOf(OverlayDrawerAccent.copy(alpha = 0.10f), OverlayDrawerCore.copy(alpha = 0.18f))
                        } else {
                            listOf(OverlayDrawerAccent.copy(alpha = 0.05f), OverlayDrawerCore.copy(alpha = 0.12f))
                        }
                    )
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strategy.name,
                            modifier = Modifier.weight(1f, fill = false),
                            color = OverlayDrawerText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selected) {
                            MiniMetaBadge(
                                text = "当前",
                                accent = OverlayDrawerAccent
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        strategy.requiredTribes.takeIf { it.isNotEmpty() }?.firstOrNull()?.let {
                            MiniMetaBadge(
                                text = localizedRequiredTribes(listOf(it)),
                                accent = DashboardIce
                            )
                        }
                        strategy.difficulty.takeIf { it.isNotBlank() }?.let {
                            MiniMetaBadge(
                                text = "难度${it}",
                                accent = OverlayDrawerWarning
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RatingBadge(
                        rating = drawerStrategyRatingLabel(strategy.tier),
                        accent = if (selected) OverlayDrawerAccent else drawerRatingColor(strategy.tier)
                    )
                    CoreMinionStrip(
                        minions = strategy.keyMinions,
                        selectedTribes = selectedTribes,
                        cardRules = cardRules,
                        cardMetadata = cardMetadata,
                        iconSize = 24.dp,
                        borderColor = if (selected) OverlayDrawerAccent else OverlayDrawerStroke
                    )
                }
            }
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

private fun drawerRatingColor(tierOrRating: String): Color = when (drawerStrategyRatingLabel(tierOrRating)) {
    "S" -> Color(0xFFE18C48)
    "A" -> OverlayDrawerAccent
    "B" -> DashboardMint
    "C" -> DashboardIce
    else -> OverlayDrawerSubtext
}

@Composable
private fun DrawerHeaderBar(
    status: AutoDetectStatus,
    selectedTab: Int
) {
    val sectionLabel = when (selectedTab) {
        0 -> "设置"
        1 -> "流派"
        else -> "战术"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = sectionLabel,
            color = OverlayDrawerSubtext.copy(alpha = 0.92f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        DrawerAutoDetectMicroLamp(status = status)
    }
}

private data class AutoDetectVisualState(
    val label: String,
    val hint: String,
    val color: Color,
    val pulse: Boolean
)

private fun autoDetectVisualState(status: AutoDetectStatus): AutoDetectVisualState = when (status) {
    AutoDetectStatus.WAITING -> AutoDetectVisualState(
        label = "待识别",
        hint = "酒馆等级后台同步中；点击后才会短时采集英雄选择页",
        color = DashboardIce,
        pulse = false
    )
    AutoDetectStatus.SCANNING -> AutoDetectVisualState(
        label = "识别中",
        hint = "正在采集当前画面，请保持在英雄选择界面",
        color = OverlayDrawerAccent,
        pulse = true
    )
    AutoDetectStatus.LOCKED -> AutoDetectVisualState(
        label = "已锁定",
        hint = "已同步当前种族",
        color = DashboardMint,
        pulse = false
    )
    AutoDetectStatus.NEEDS_ATTENTION -> AutoDetectVisualState(
        label = "需重试",
        hint = "本轮识别未锁定，请点击重新识别",
        color = OverlayDrawerWarning,
        pulse = false
    )
}

@Composable
private fun autoDetectLampAlpha(status: AutoDetectStatus): Float {
    val pulse = autoDetectVisualState(status).pulse
    if (!pulse) return 1f
    val transition = rememberInfiniteTransition(label = "auto-detect-lamp")
    return transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auto-detect-lamp-alpha"
    ).value
}

@Composable
private fun DrawerAutoDetectMicroLamp(status: AutoDetectStatus) {
    val visual = autoDetectVisualState(status)
    val alpha = autoDetectLampAlpha(status)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(visual.color.copy(alpha = 0.08f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(visual.color.copy(alpha = alpha))
        )
        Text(
            text = visual.label,
            color = OverlayDrawerText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DashboardAutoDetectStatusBadge(status: AutoDetectStatus) {
    val visual = autoDetectVisualState(status)
    val alpha = autoDetectLampAlpha(status)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(visual.color.copy(alpha = alpha))
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = visual.label,
                color = visual.color,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = visual.hint,
                color = DashboardMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AutoDetectDebugSummary(
    debugInfo: AutoDetectDebugInfo,
    compact: Boolean
) {
    val lines = buildList {
        debugInfo.tavernTierLabel?.let { add("酒馆: $it") }
        debugInfo.recognizedTribesLabel?.let { add("识别: $it") }
        debugInfo.rawText?.takeIf { it.isNotBlank() }?.let { add("详情: $it") }
        debugInfo.lastUpdatedLabel?.let { add("时间: $it") }
        debugInfo.viewportLabel?.let { add("视口: $it") }
        debugInfo.roiRectLabel?.let { add("ROI: $it") }
        debugInfo.headerLabel?.let { add("头部: $it") }
        debugInfo.latestDumpPath?.let { add("Dump: $it") }
    }
    if (lines.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = OverlayDrawerInset.copy(alpha = 0.28f),
        border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerStrokeSoft.copy(alpha = 0.52f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "识别调试",
                color = OverlayDrawerSubtext,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            lines.take(if (compact) 3 else 6).forEach { line ->
                Text(
                    text = line,
                    color = OverlayDrawerText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (compact) 1 else 2
                )
            }
        }
    }
}

@Composable
private fun DrawerGlassSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = OverlayDrawerInset.copy(alpha = 0.96f),
        border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerStroke.copy(alpha = 0.32f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            OverlayDrawerAccent.copy(alpha = 0.08f),
                            OverlayDrawerCore.copy(alpha = 0.22f),
                            OverlayDrawerInset.copy(alpha = 0.18f)
                        )
                    )
                )
                .border(
                    1.dp,
                    OverlayDrawerStrokeSoft.copy(alpha = 0.38f),
                    RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                OverlayDrawerAccent.copy(alpha = 0.10f),
                                OverlayDrawerShell
                            )
                        )
                    )
                    .border(1.dp, OverlayDrawerStrokeSoft.copy(alpha = 0.42f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    color = OverlayDrawerAccent.copy(alpha = 0.14f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerAccent.copy(alpha = 0.38f))
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = OverlayDrawerText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            color = OverlayDrawerInset.copy(alpha = 0.86f),
            border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerStrokeSoft.copy(alpha = 0.44f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                OverlayDrawerAccent.copy(alpha = 0.05f),
                                OverlayDrawerCore.copy(alpha = 0.20f)
                            )
                        )
                    )
            ) {
                content(Modifier.fillMaxSize().padding(12.dp))
            }
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
private fun DrawerEmptyStateCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = OverlayDrawerInset.copy(alpha = 0.64f),
        border = androidx.compose.foundation.BorderStroke(1.dp, OverlayDrawerStrokeSoft.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(OverlayDrawerAccent.copy(alpha = 0.06f), Color.Transparent)
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
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
            text = localizedStrategyText(summary.detail, strategy),
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

private fun sortStrategiesForDecision(
    strategies: List<StrategyComp>,
    selectedHero: ResolvedHeroStatOption? = null
): List<StrategyComp> {
    val pinnedIds = listOfNotNull(
        selectedHero?.recommendation?.recommendedCompId,
        selectedHero?.recommendation?.fallbackCompId
    )
    val originalOrder = strategies.withIndex().associate { it.value.id to it.index }
    return strategies.sortedWith(
        compareByDescending<StrategyComp> {
            when (it.id) {
                pinnedIds.getOrNull(0) -> 2
                pinnedIds.getOrNull(1) -> 1
                else -> 0
            }
        }.thenByDescending { strategyDecisionProfile(it).score }
            .thenBy { originalOrder[it.id] ?: Int.MAX_VALUE }
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
    cardRules: CardRulesCatalog,
    cardMetadata: BattlegroundCardMetadataCatalog = BattlegroundCardMetadataCatalog()
): List<KeyMinion> {
    return MinionLobbyFilter.filterMinionsForLobby(
        minions = minions,
        selectedTribes = selectedTribes,
        cardRules = cardRules,
        cardMetadata = cardMetadata
    )
}

private fun directionalCoreMinions(
    minions: List<KeyMinion>,
    limit: Int
): List<KeyMinion> = directionalCoreMinions(
    minions = minions,
    selectedTribes = emptySet(),
    cardRules = emptyMap(),
    cardMetadata = BattlegroundCardMetadataCatalog(),
    limit = limit
)

private fun genericSupportMinions(
    minions: List<KeyMinion>,
    limit: Int
): List<KeyMinion> = genericSupportMinions(
    minions = minions,
    selectedTribes = emptySet(),
    cardRules = emptyMap(),
    cardMetadata = BattlegroundCardMetadataCatalog(),
    limit = limit
)

private fun alternativeSupportMinions(
    minions: List<KeyMinion>,
    limit: Int
): List<KeyMinion> = alternativeSupportMinions(
    minions = minions,
    selectedTribes = emptySet(),
    cardRules = emptyMap(),
    cardMetadata = BattlegroundCardMetadataCatalog(),
    limit = limit
)

private fun recommendedMinions(
    minions: List<KeyMinion>,
    limit: Int
): List<KeyMinion> = recommendedMinions(
    minions = minions,
    selectedTribes = emptySet(),
    cardRules = emptyMap(),
    cardMetadata = BattlegroundCardMetadataCatalog(),
    limit = limit
)

private fun cycleMinions(
    minions: List<KeyMinion>,
    limit: Int
): List<KeyMinion> = cycleMinions(
    minions = minions,
    selectedTribes = emptySet(),
    cardRules = emptyMap(),
    cardMetadata = BattlegroundCardMetadataCatalog(),
    limit = limit
)

private fun directionalCoreMinions(
    minions: List<KeyMinion>,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    cardMetadata: BattlegroundCardMetadataCatalog,
    limit: Int
): List<KeyMinion> {
    val available = filterMinionsForLobby(minions, selectedTribes, cardRules, cardMetadata)
    return available
        .filter { it.statusRaw == "CORE" && !isGenericSupportMinion(it) }
        .ifEmpty { available.filter { !isGenericSupportMinion(it) } }
        .take(limit)
}

private fun genericSupportMinions(
    minions: List<KeyMinion>,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    cardMetadata: BattlegroundCardMetadataCatalog,
    limit: Int
): List<KeyMinion> {
    return filterMinionsForLobby(minions, selectedTribes, cardRules, cardMetadata)
        .filter { isGenericSupportMinion(it) }
        .sortedWith(compareBy<KeyMinion> { it.techLevel }.thenBy { it.name })
        .take(limit)
}

private fun alternativeSupportMinions(
    minions: List<KeyMinion>,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    cardMetadata: BattlegroundCardMetadataCatalog,
    limit: Int
): List<KeyMinion> {
    val available = filterMinionsForLobby(minions, selectedTribes, cardRules, cardMetadata)
    val requiredIds = directionalCoreMinions(
        minions = available,
        selectedTribes = selectedTribes,
        cardRules = cardRules,
        cardMetadata = cardMetadata,
        limit = Int.MAX_VALUE
    )
        .map { it.cardId ?: it.name }
        .toSet()
    return available
        .filter { !isGenericSupportMinion(it) }
        .filter { (it.cardId ?: it.name) !in requiredIds }
        .filter { it.statusRaw == "ADDON" || it.statusRaw == "RECOMMENDED" || it.statusRaw == "CYCLE" }
        .sortedWith(
            compareByDescending<KeyMinion> { it.finalBoardWeight ?: 0 }
                .thenBy { it.techLevel }
                .thenBy { it.name }
        )
        .take(limit)
}

private fun recommendedMinions(
    minions: List<KeyMinion>,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    cardMetadata: BattlegroundCardMetadataCatalog,
    limit: Int
): List<KeyMinion> {
    return filterMinionsForLobby(minions, selectedTribes, cardRules, cardMetadata)
        .filter { it.statusRaw == "RECOMMENDED" }
        .distinctBy { it.cardId ?: it.name }
        .take(limit)
}

private fun cycleMinions(
    minions: List<KeyMinion>,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    cardMetadata: BattlegroundCardMetadataCatalog,
    limit: Int
): List<KeyMinion> {
    return filterMinionsForLobby(minions, selectedTribes, cardRules, cardMetadata)
        .filter { it.statusRaw == "CYCLE" }
        .distinctBy { it.cardId ?: it.name }
        .take(limit)
}

private fun tacticalNextTierTargets(
    strategy: StrategyComp?,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    cardMetadata: BattlegroundCardMetadataCatalog,
    currentTavernTier: Int?,
    selectedHeroCardId: String?
): List<KeyMinion> {
    strategy ?: return emptyList()
    val targetTier = ((currentTavernTier ?: return emptyList()) + 1).coerceAtMost(6)
    return MinionLobbyFilter.filterMinionsForLobby(
        minions = strategy.keyMinions,
        selectedTribes = selectedTribes,
        cardRules = cardRules,
        cardMetadata = cardMetadata,
        selectedHeroCardId = selectedHeroCardId
    )
        .filter { it.techLevel == targetTier }
        .filter { it.statusRaw == "CORE" || it.statusRaw == "RECOMMENDED" || it.statusRaw == "ADDON" }
        .distinctBy { it.cardId ?: it.name }
        .sortedWith(
            compareBy<KeyMinion>(
                { when (it.statusRaw?.uppercase()) {
                    "CORE" -> 0
                    "RECOMMENDED" -> 1
                    "ADDON" -> 2
                    else -> 3
                } },
                { -(it.finalBoardWeight ?: 0) },
                { it.name }
            )
        )
        .take(4)
}

@Composable
private fun DrawerTacticalTab(
    strategy: StrategyComp?,
    selectedTribes: Set<Tribe>,
    selectedHero: ResolvedHeroStatOption?,
    autoDetectDebugInfo: AutoDetectDebugInfo,
    cardRules: CardRulesCatalog,
    cardMetadata: BattlegroundCardMetadataCatalog
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
        badge = autoDetectDebugInfo.tavernTier?.let { "${it}本" } ?: "酒馆未同步"
    ) { bodyModifier ->
        val liveRecommendations = remember(
            strategy,
            selectedTribes,
            cardRules,
            cardMetadata,
            autoDetectDebugInfo.tavernTier,
            selectedHero?.heroCardId
        ) {
            RealtimeMinionRecommendationEngine.recommend(
                strategy = strategy,
                selectedTribes = selectedTribes,
                cardRules = cardRules,
                cardMetadata = cardMetadata,
                tavernTier = autoDetectDebugInfo.tavernTier,
                selectedHeroCardId = selectedHero?.heroCardId
            )
        }
        val nextTierTargets = remember(
            strategy,
            selectedTribes,
            cardRules,
            cardMetadata,
            autoDetectDebugInfo.tavernTier,
            selectedHero?.heroCardId
        ) {
            tacticalNextTierTargets(
                strategy = strategy,
                selectedTribes = selectedTribes,
                cardRules = cardRules,
                cardMetadata = cardMetadata,
                currentTavernTier = autoDetectDebugInfo.tavernTier,
                selectedHeroCardId = selectedHero?.heroCardId
            )
        }
        Column(
            modifier = bodyModifier
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DrawerSectionTitle(title = "当前酒馆先买")
            if (liveRecommendations.primaryChoices.isEmpty()) {
                DrawerEmptyStateCard(
                    modifier = Modifier.fillMaxWidth(),
                    title = "当前没有可直接买的主目标",
                    body = if (autoDetectDebugInfo.tavernTier == null) {
                        "先同步酒馆等级，再给你更准的买牌清单。"
                    } else {
                        "这套在当前本数没有明确主抓牌，先稳经济或等升本。"
                    }
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    liveRecommendations.primaryChoices.forEachIndexed { index, minion ->
                        DrawerShopBuyRow(
                            minion = minion,
                            rank = index + 1,
                            accent = tacticalMinionAccent(minion),
                            badge = if (index == 0) "首抓" else "优先",
                            currentTavernTier = autoDetectDebugInfo.tavernTier
                        )
                    }
                }
            }

            if (liveRecommendations.secondaryChoices.isNotEmpty()) {
                DrawerSectionTitle(title = "补强候选")
                DrawerShopAvatarShelf(
                    minions = liveRecommendations.secondaryChoices,
                    emptyLabel = "当前没有补强牌",
                    badgeLabel = { if (isGenericSupportMinion(it)) "功能" else "补强" }
                )
            }

            if (nextTierTargets.isNotEmpty()) {
                DrawerSectionTitle(title = "下本盯牌")
                DrawerShopAvatarShelf(
                    minions = nextTierTargets,
                    emptyLabel = "下本还没有明确目标",
                    badgeLabel = { "下本" }
                )
            }

            DrawerActionStrip(
                title = "当前打法",
                body = localizedStrategyText(strategy.earlyStrategy, strategy),
                accent = OverlayDrawerAccent
            )

            strategy.whenToCommit?.takeIf { it.isNotBlank() }?.let { signal ->
                DrawerActionStrip(
                    title = "成型信号",
                    body = localizedStrategyText(signal, strategy),
                    accent = OverlayDrawerWarning
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DrawerShopAvatarShelf(
    minions: List<KeyMinion>,
    emptyLabel: String,
    badgeLabel: (KeyMinion) -> String
) {
    if (minions.isEmpty()) {
        DrawerEmptyStateCard(
            modifier = Modifier.fillMaxWidth(),
            title = emptyLabel,
            body = "当前先按上面的主抓顺序买牌。"
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        minions.forEach { minion ->
            DrawerAvatarShelfCard(
                minion = minion,
                badge = badgeLabel(minion),
                accent = tacticalMinionAccent(minion)
            )
        }
    }
}

@Composable
private fun DrawerAvatarShelfCard(
    minion: KeyMinion,
    badge: String,
    accent: Color
) {
    val displayName = localizedMinionTitle(minion) ?: minion.name
    Surface(
        modifier = Modifier.width(104.dp),
        shape = RoundedCornerShape(16.dp),
        color = OverlayDrawerInset.copy(alpha = 0.94f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.34f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.10f), OverlayDrawerCore.copy(alpha = 0.14f))
                    )
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                MinionHeadshot(
                    minion = minion,
                    modifier = Modifier.size(64.dp),
                    borderColor = accent
                )
                Surface(
                    modifier = Modifier.offset(x = 4.dp, y = (-4).dp),
                    shape = RoundedCornerShape(999.dp),
                    color = accent.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.28f))
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Text(
                text = displayName,
                color = OverlayDrawerText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            MiniMetaBadge(
                text = "${minion.techLevel}本",
                accent = DashboardIce
            )
        }
    }
}

@Composable
private fun DrawerShopBuyRow(
    minion: KeyMinion,
    rank: Int,
    accent: Color,
    badge: String,
    currentTavernTier: Int?
) {
    val displayName = localizedMinionTitle(minion) ?: minion.name
    val reason = tacticalMinionReason(
        minion = minion,
        currentTavernTier = currentTavernTier,
        rank = rank
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = OverlayDrawerInset.copy(alpha = 0.94f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.38f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(accent.copy(alpha = 0.10f), OverlayDrawerCore.copy(alpha = 0.14f))
                    )
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = accent.copy(alpha = 0.14f),
                border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.28f))
            ) {
                Text(
                    text = rank.toString(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black
                )
            }
            Box(contentAlignment = Alignment.BottomEnd) {
                MinionHeadshot(
                    minion = minion,
                    modifier = Modifier.size(66.dp),
                    borderColor = accent
                )
                Surface(
                    modifier = Modifier.offset(x = 4.dp, y = 2.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = OverlayDrawerShell.copy(alpha = 0.92f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DashboardIce.copy(alpha = 0.34f))
                ) {
                    Text(
                        text = "${minion.techLevel}本",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = DashboardIce,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black
                    )
                }
            }
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
                        text = displayName,
                        color = OverlayDrawerText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accent.copy(alpha = 0.14f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.26f))
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = accent,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Text(
                    text = reason,
                    color = OverlayDrawerSubtext,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MiniMetaBadge(text = minionStatusLabel(minion.statusRaw), accent = accent)
                    if (minion.phase.isNotBlank()) {
                        MiniMetaBadge(text = minion.phase, accent = DashboardMint)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerActionStrip(
    title: String,
    body: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(accent.copy(alpha = 0.14f), OverlayDrawerCore.copy(alpha = 0.18f))
                    )
                )
                .padding(horizontal = 12.dp, vertical = 11.dp),
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
                color = OverlayDrawerText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DrawerSectionTitle(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(8.dp)
                .width(8.dp)
                .clip(CircleShape)
                .background(OverlayDrawerAccent.copy(alpha = 0.8f))
        )
        Text(
            text = title,
            color = OverlayDrawerText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            OverlayDrawerAccent.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
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
    autoDetectStatus: AutoDetectStatus,
    autoDetectDebugInfo: AutoDetectDebugInfo,
    recognizedHeroes: List<ResolvedHeroStatOption>,
    heroStatsUpdatedAtLabel: String?
) {
    DashboardPanel(
        brush = Brush.linearGradient(colors = listOf(DashboardRaised, DashboardCard)),
        borderColor = DashboardLine
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHeader(
                icon = Icons.Outlined.FilterAlt,
                title = "本局环境",
                subtitle = "由 AI 自动同步"
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
                                text = environmentSyncLabel(
                                    selectedTribes = selectedTribes,
                                    autoDetectStatus = autoDetectStatus
                                ),
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        DashboardAutoDetectStatusBadge(status = autoDetectStatus)
                        AutoDetectDebugSummary(
                            debugInfo = autoDetectDebugInfo,
                            compact = false
                        )
                        DashboardSelectionMeter(
                            selectedCount = selectedTribes.size,
                            targetCount = 5
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (selectedTribes.isEmpty()) {
                                MiniMetaBadge("等待 AI 识别", DashboardMuted)
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
                    HeroStatsSummaryCard(
                        recognizedHeroes = recognizedHeroes,
                        heroStatsUpdatedAtLabel = heroStatsUpdatedAtLabel
                    )

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
                                text = environmentSyncHint(
                                    selectedTribes = selectedTribes,
                                    autoDetectStatus = autoDetectStatus
                                ),
                                color = DashboardMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
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
                                text = "如果识别结果不对，请重新触发 AI 识别；阵容判断不再支持手动修正种族。",
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
    selectedHero: ResolvedHeroStatOption?,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    cardMetadata: BattlegroundCardMetadataCatalog,
    onSelectStrategy: (String) -> Unit
) {
    val sortedStrategies = sortStrategiesForDecision(strategies, selectedHero)

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
                val overviewSummary = drawerDecisionSummary(sortedStrategies)
                SignalCallout(
                    title = overviewSummary.label,
                    body = localizedStrategyText(overviewSummary.detail, sortedStrategies.firstOrNull()),
                    accent = overviewSummary.accent
                )
                sortedStrategies.forEachIndexed { index, strategy ->
                    StrategyCommandCard(
                        rank = index + 1,
                        strategy = strategy,
                        selected = strategy.id == selectedStrategyId,
                        selectedTribes = selectedTribes,
                        cardRules = cardRules,
                        cardMetadata = cardMetadata,
                        onClick = { onSelectStrategy(strategy.id) }
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
    cardRules: CardRulesCatalog,
    cardMetadata: BattlegroundCardMetadataCatalog
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

            val tacticalSummary = drawerDecisionSummary(strategy)
            SignalCallout(
                title = tacticalSummary.label,
                body = localizedStrategyText(tacticalSummary.detail, strategy),
                accent = tacticalSummary.accent
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
                                localizedStrategyText(strategy.overview, strategy),
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
                            body = localizedStrategyText(signal, strategy),
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
            MinionAtlasSection(
                strategy = strategy,
                selectedTribes = selectedTribes,
                cardRules = cardRules,
                cardMetadata = cardMetadata
            )
            RecommendedCardsSection(
                strategy = strategy,
                selectedTribes = selectedTribes,
                cardRules = cardRules,
                cardMetadata = cardMetadata
            )
            MissingChecklistSection(
                strategy = strategy,
                selectedTribes = selectedTribes,
                cardRules = cardRules,
                cardMetadata = cardMetadata
            )
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
                body = localizedStrategyText(strategy.earlyStrategy, strategy),
                accent = DashboardIce
            )
            StrategyNoteCard(
                modifier = Modifier.weight(1f),
                title = "后续补完",
                body = localizedStrategyText(strategy.lateStrategy, strategy),
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
    cardRules: CardRulesCatalog,
    cardMetadata: BattlegroundCardMetadataCatalog
) {
    val required = directionalCoreMinions(
        minions = strategy.keyMinions,
        selectedTribes = selectedTribes,
        cardRules = cardRules,
        cardMetadata = cardMetadata,
        limit = 3
    )
    val alternatives = alternativeSupportMinions(
        minions = strategy.keyMinions,
        selectedTribes = selectedTribes,
        cardRules = cardRules,
        cardMetadata = cardMetadata,
        limit = 3
    )
    val supports = genericSupportMinions(
        minions = strategy.keyMinions,
        selectedTribes = selectedTribes,
        cardRules = cardRules,
        cardMetadata = cardMetadata,
        limit = 3
    )

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
    cardRules: CardRulesCatalog,
    cardMetadata: BattlegroundCardMetadataCatalog
) {
    val directional = directionalCoreMinions(
        minions = strategy.keyMinions,
        selectedTribes = selectedTribes,
        cardRules = cardRules,
        cardMetadata = cardMetadata,
        limit = 4
    )
    val alternatives = alternativeSupportMinions(
        minions = strategy.keyMinions,
        selectedTribes = selectedTribes,
        cardRules = cardRules,
        cardMetadata = cardMetadata,
        limit = 4
    )
    val supports = genericSupportMinions(
        minions = strategy.keyMinions,
        selectedTribes = selectedTribes,
        cardRules = cardRules,
        cardMetadata = cardMetadata,
        limit = 3
    )

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
    cardRules: CardRulesCatalog,
    cardMetadata: BattlegroundCardMetadataCatalog
) {
    val recommended = recommendedMinions(
        minions = strategy.keyMinions,
        selectedTribes = selectedTribes,
        cardRules = cardRules,
        cardMetadata = cardMetadata,
        limit = Int.MAX_VALUE
    )
    val cycle = cycleMinions(
        minions = strategy.keyMinions,
        selectedTribes = selectedTribes,
        cardRules = cardRules,
        cardMetadata = cardMetadata,
        limit = Int.MAX_VALUE
    )

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
private fun StrategyCommandCard(
    rank: Int,
    strategy: StrategyComp,
    selected: Boolean,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    cardMetadata: BattlegroundCardMetadataCatalog,
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
                cardMetadata = cardMetadata,
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
        color = accent
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
private fun CoreMinionStrip(
    minions: List<KeyMinion>,
    selectedTribes: Set<Tribe>,
    cardRules: CardRulesCatalog,
    cardMetadata: BattlegroundCardMetadataCatalog,
    iconSize: androidx.compose.ui.unit.Dp,
    borderColor: Color
) {
    val available = filterMinionsForLobby(minions, selectedTribes, cardRules, cardMetadata)
    val leadMinions = directionalCoreMinions(
        minions = minions,
        selectedTribes = selectedTribes,
        cardRules = cardRules,
        cardMetadata = cardMetadata,
        limit = 4
    )
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
    cardMetadata: BattlegroundCardMetadataCatalog,
    iconSize: androidx.compose.ui.unit.Dp,
    borderColor: Color
) {
    val available = filterMinionsForLobby(minions, selectedTribes, cardRules, cardMetadata)
    val addOns = (
        available
            .filter { it.statusRaw == "ADDON" && isGenericSupportMinion(it) }
            .sortedWith(compareBy<KeyMinion> { it.techLevel }.thenBy { it.name }) +
            available
                .filter { it.statusRaw == "ADDON" && !isGenericSupportMinion(it) }
                .sortedWith(compareBy<KeyMinion> { it.techLevel }.thenBy { it.name })
        )

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
    cardMetadata: BattlegroundCardMetadataCatalog,
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
                cardMetadata = cardMetadata,
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
                cardMetadata = cardMetadata,
                iconSize = addOnIconSize,
                borderColor = addOnBorderColor
            )
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
        color = accent.copy(alpha = 0.14f)
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

@Composable
private fun localizedMinionTitle(minion: KeyMinion): String? {
    val context = LocalContext.current.applicationContext
    val bundledNames = remember(context) { BundledCardNameRegistry.get(context) }
    return minion.cardId
        ?.let(bundledNames::get)
        ?.takeIf { it.isNotBlank() }
        ?: minion.name.takeUnless(::hasAsciiLetters)
}

private fun localizeStrategyText(
    text: String,
    strategy: StrategyComp? = null,
    bundledNames: Map<String, String> = emptyMap()
): String {
    if (text.isBlank()) return text

    val replacements = buildStrategyTextReplacements(strategy, bundledNames, text)
    var localized = text
    replacements.forEach { (english, chinese) ->
        localized = localized.replace(english, chinese, ignoreCase = true)
    }

    StrategyTextAliasMap.forEach { (english, chinese) ->
        localized = localized.replace(english, chinese, ignoreCase = true)
    }

    return localized
        .replace("all in", "梭哈", ignoreCase = true)
        .replace("APM", "高频操作")
        .replace("token", "衍生物", ignoreCase = true)
        .replace("buff", "增益", ignoreCase = true)
}

@Composable
private fun localizedStrategyText(text: String, strategy: StrategyComp? = null): String {
    val context = LocalContext.current.applicationContext
    val bundledNames = remember(context) { BundledCardNameRegistry.get(context) }
    return remember(text, strategy, bundledNames) {
        localizeStrategyText(text, strategy, bundledNames)
    }
}

private fun buildStrategyTextReplacements(
    strategy: StrategyComp?,
    bundledNames: Map<String, String>,
    text: String
): Map<String, String> {
    if (strategy == null || bundledNames.isEmpty()) return emptyMap()

    val replacements = linkedMapOf<String, String>()
    val aliasCandidates = linkedMapOf<String, MutableSet<String>>()
    val lowerText = text.lowercase()
    val aliasStopwords = setOf("of", "the", "and")

    strategy.keyMinions.forEach { minion ->
        val englishName = minion.name.takeIf(::hasAsciiLetters) ?: return@forEach
        val localizedName = minion.cardId?.let(bundledNames::get)?.takeIf { it.isNotBlank() } ?: return@forEach
        replacements.putIfAbsent(englishName, localizedName)

        val parts = englishName.split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (parts.size >= 2 && parts[1].lowercase() !in aliasStopwords) {
            aliasCandidates.getOrPut(parts.take(2).joinToString(" ")) { linkedSetOf() }.add(localizedName)
        }
        parts.firstOrNull()
            ?.takeIf { it.length >= 5 }
            ?.let { aliasCandidates.getOrPut(it) { linkedSetOf() }.add(localizedName) }
    }

    aliasCandidates.forEach { (alias, localizedValues) ->
        if (localizedValues.size == 1 && alias.lowercase() in lowerText && alias !in replacements) {
            replacements[alias] = localizedValues.first()
        }
    }

    return replacements.entries
        .sortedByDescending { it.key.length }
        .associate { it.toPair() }
}

private fun minionStatusLabel(status: String?): String = when (status?.uppercase()) {
    "CORE" -> "核心"
    "ADDON" -> "补强"
    "RECOMMENDED" -> "推荐"
    "CYCLE" -> "经济"
    else -> "功能牌"
}

private fun tacticalMinionAccent(minion: KeyMinion): Color = when (minion.statusRaw?.uppercase()) {
    "CORE" -> OverlayDrawerAccent
    "RECOMMENDED" -> DashboardMint
    "ADDON" -> DashboardIce
    "CYCLE" -> OverlayDrawerWarning
    else -> OverlayDrawerSubtext
}

private fun tacticalMinionReason(
    minion: KeyMinion,
    currentTavernTier: Int?,
    rank: Int
): String = when {
    rank == 1 && minion.statusRaw?.uppercase() == "CORE" -> "当前最先抓的主核"
    minion.statusRaw?.uppercase() == "CORE" && currentTavernTier != null && minion.techLevel == currentTavernTier ->
        "本层已经到点，看到就拿"
    minion.statusRaw?.uppercase() == "RECOMMENDED" ->
        "当前最顺的节奏补强"
    minion.statusRaw?.uppercase() == "ADDON" && isGenericSupportMinion(minion) ->
        "功能件先留，后面放大主核"
    minion.statusRaw?.uppercase() == "ADDON" ->
        "先补战力，再接主核"
    minion.statusRaw?.uppercase() == "CYCLE" ->
        "过渡兼经济，继续找牌"
    isGenericSupportMinion(minion) ->
        "功能位可先留一张"
    else -> "适合当前回合先拿"
}

private fun StrategyDataSource.label(): String = when (this) {
    StrategyDataSource.ASSET -> "内置"
    StrategyDataSource.CACHE -> "远程缓存"
    StrategyDataSource.REMOTE -> "远程"
}

@Composable
private fun resolveMinionImageModels(minion: KeyMinion): List<Any> {
    val context = LocalContext.current.applicationContext
    val models by produceState(
        initialValue = listOfNotNull(
            MinionImageCache.localModel(context, minion),
            minion.imageAsset?.takeIf { it.isNotBlank() }?.let { "file:///android_asset/$it" }
        ),
        key1 = minion.cardId,
        key2 = minion.imageUrl,
        key3 = minion.imageAsset
    ) {
        val localFile = MinionImageCache.ensureCached(context, minion)
        value = listOfNotNull(
            localFile,
            minion.imageAsset?.takeIf { it.isNotBlank() }?.let { "file:///android_asset/$it" }
        )
    }
    return models
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
                text = "${minion.techLevel}★",
                color = DashboardGold,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun hasAsciiLetters(text: String): Boolean = text.any { it in 'A'..'Z' || it in 'a'..'z' }
