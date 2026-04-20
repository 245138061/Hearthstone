package com.bgtactician.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgtactician.app.BuildConfig
import com.bgtactician.app.data.local.AppPreferences
import com.bgtactician.app.data.local.HeroSelectionSessionStore
import com.bgtactician.app.data.local.HeroSelectionSessionState
import com.bgtactician.app.data.local.VisionApiSettings
import com.bgtactician.app.data.local.VisionRoutingMode
import com.bgtactician.app.data.model.AutoDetectDebugInfo
import com.bgtactician.app.data.model.AutoDetectStatus
import com.bgtactician.app.data.model.BattlegroundCardMetadataCatalog
import com.bgtactician.app.data.model.BattlegroundCardStatsCatalog
import com.bgtactician.app.data.model.BattlegroundHeroNameIndex
import com.bgtactician.app.data.model.BattlegroundHeroStatsCatalog
import com.bgtactician.app.data.model.CardRulesCatalog
import com.bgtactician.app.data.model.CatalogSnapshot
import com.bgtactician.app.data.model.HeroSelectionVisionResult
import com.bgtactician.app.data.model.HeroSelectionVisionHeroOption
import com.bgtactician.app.data.model.ResolvedHeroStatOption
import com.bgtactician.app.data.model.StrategyComp
import com.bgtactician.app.data.model.StrategyDataSource
import com.bgtactician.app.data.model.Tribe
import com.bgtactician.app.data.repository.HeroSelectionRecommendationEngine
import com.bgtactician.app.data.repository.StrategyEngine
import com.bgtactician.app.data.repository.StrategyRepository
import com.bgtactician.app.vision.HeroSelectionVisionValidator
import com.bgtactician.app.overlay.OverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DashboardUiState(
    val appVersionLabel: String = BuildConfig.VERSION_NAME,
    val catalogVersion: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val selectedTribes: Set<Tribe> = emptySet(),
    val manifestUrlOverride: String = "",
    val effectiveManifestUrl: String = "",
    val dataSource: StrategyDataSource = StrategyDataSource.ASSET,
    val lastSyncLabel: String? = null,
    val manifestVersionLabel: String? = null,
    val syncMessage: String? = null,
    val autoDetectStatus: AutoDetectStatus = AutoDetectStatus.WAITING,
    val autoDetectDebugInfo: AutoDetectDebugInfo = AutoDetectDebugInfo(),
    val visionBaseUrl: String = "",
    val visionApiKey: String = "",
    val visionModel: String = "",
    val visionBackupBaseUrl: String = "",
    val visionBackupApiKey: String = "",
    val visionBackupModel: String = "",
    val visionRoutingMode: VisionRoutingMode = VisionRoutingMode.AUTO,
    val cardRules: CardRulesCatalog = emptyMap(),
    val cardMetadata: BattlegroundCardMetadataCatalog = BattlegroundCardMetadataCatalog(),
    val recognizedHeroes: List<ResolvedHeroStatOption> = emptyList(),
    val heroStatsUpdatedAtLabel: String? = null,
    val selectedHeroCardId: String? = null,
    val selectedHeroName: String? = null,
    val selectedHeroSlot: Int? = null,
    val manualSelectedStrategyId: String? = null,
    val allStrategies: List<StrategyComp> = emptyList(),
    val strategies: List<StrategyComp> = emptyList(),
    val selectedStrategyId: String? = null
) {
    val selectedStrategy: StrategyComp?
        get() = strategies.firstOrNull { it.id == selectedStrategyId }

    val liveStrategy: StrategyComp?
        get() {
            manualSelectedStrategyId?.let { id ->
                strategies.firstOrNull { it.id == id }?.let { return it }
            }
            val heroRecommendedId = selectedHero?.recommendation?.recommendedCompId
            return heroRecommendedId?.let { id -> strategies.firstOrNull { it.id == id } }
        }

    val currentTavernTier: Int?
        get() = autoDetectDebugInfo.tavernTier

    val selectedHero: ResolvedHeroStatOption?
        get() = recognizedHeroes.firstOrNull { hero ->
            (!selectedHeroCardId.isNullOrBlank() && hero.heroCardId == selectedHeroCardId) ||
                (selectedHeroSlot != null && hero.slot == selectedHeroSlot) ||
                (!selectedHeroName.isNullOrBlank() &&
                    (hero.displayName == selectedHeroName || hero.recognizedName == selectedHeroName))
        }
}

class MainViewModel(
    private val repository: StrategyRepository = StrategyRepository()
) : ViewModel() {

    private data class FilterInputs(
        val snapshot: CatalogSnapshot?,
        val selectedTribes: Set<Tribe>,
        val manifestUrlOverride: String
    )

    private data class UiMetaInputs(
        val selectedStrategyId: String?,
        val isRefreshing: Boolean,
        val syncMessage: String?,
        val manifestVersion: String?,
        val cardRules: CardRulesCatalog,
        val cardMetadata: BattlegroundCardMetadataCatalog,
        val visionApiSettings: VisionApiSettings,
        val recognizedHeroOptions: List<HeroSelectionVisionHeroOption>,
        val heroStatsCatalog: BattlegroundHeroStatsCatalog,
        val cardStatsCatalog: BattlegroundCardStatsCatalog,
        val heroNameIndex: BattlegroundHeroNameIndex,
        val autoDetectStatus: AutoDetectStatus,
        val autoDetectDebugInfo: AutoDetectDebugInfo,
        val selectedHeroCardId: String?,
        val selectedHeroName: String?,
        val selectedHeroSlot: Int?
    )

    private data class HeroMetaInputs(
        val recognizedHeroOptions: List<HeroSelectionVisionHeroOption>,
        val heroStatsCatalog: BattlegroundHeroStatsCatalog,
        val cardStatsCatalog: BattlegroundCardStatsCatalog,
        val heroNameIndex: BattlegroundHeroNameIndex
    )

    private data class CatalogMetaInputs(
        val syncMessage: String?,
        val manifestVersion: String?,
        val cardRules: CardRulesCatalog,
        val cardMetadata: BattlegroundCardMetadataCatalog
    )

    private val catalogFlow = MutableStateFlow<CatalogSnapshot?>(null)
    private val selectedTribesFlow = MutableStateFlow(
        setOf(Tribe.MECH, Tribe.DEMON, Tribe.UNDEAD, Tribe.PIRATE, Tribe.ELEMENTAL)
    )
    private val manifestUrlOverrideFlow = MutableStateFlow("")
    private val selectedStrategyFlow = MutableStateFlow<String?>(null)
    private val isRefreshingFlow = MutableStateFlow(false)
    private val syncMessageFlow = MutableStateFlow<String?>(null)
    private val manifestVersionFlow = MutableStateFlow<String?>(null)
    private val cardRulesFlow = MutableStateFlow<CardRulesCatalog>(emptyMap())
    private val cardMetadataFlow = MutableStateFlow(BattlegroundCardMetadataCatalog())
    private val visionApiSettingsFlow = MutableStateFlow(VisionApiSettings())
    private val recognizedHeroOptionsFlow = MutableStateFlow<List<HeroSelectionVisionHeroOption>>(emptyList())
    private val heroStatsCatalogFlow = MutableStateFlow(BattlegroundHeroStatsCatalog())
    private val cardStatsCatalogFlow = MutableStateFlow(BattlegroundCardStatsCatalog())
    private val heroNameIndexFlow = MutableStateFlow(BattlegroundHeroNameIndex())
    private val autoDetectStatusFlow = OverlayService.autoDetectStatus
    private val autoDetectDebugInfoFlow = OverlayService.autoDetectDebugInfo
    private val heroSelectionSessionFlow = HeroSelectionSessionStore.state
    private var appContext: Context? = null

    private val filterInputsFlow = combine(
        catalogFlow,
        selectedTribesFlow,
        manifestUrlOverrideFlow,
        heroSelectionSessionFlow
    ) { snapshot, tribes, manifestUrlOverride, heroSession ->
        FilterInputs(
            snapshot = snapshot,
            selectedTribes = heroSession.selectedTribes.takeIf { it.size == 5 } ?: tribes,
            manifestUrlOverride = manifestUrlOverride
        )
    }

    private val uiSelectionFlow = combine(
        selectedStrategyFlow,
        isRefreshingFlow
    ) { selectedId, isRefreshing ->
        selectedId to isRefreshing
    }

    private val uiCatalogMetaFlow = combine(
        syncMessageFlow,
        manifestVersionFlow,
        cardRulesFlow,
        cardMetadataFlow
    ) { syncMessage, manifestVersion, cardRules, cardMetadata ->
        CatalogMetaInputs(
            syncMessage = syncMessage,
            manifestVersion = manifestVersion,
            cardRules = cardRules,
            cardMetadata = cardMetadata
        )
    }

    private val uiHeroMetaFlow = combine(
        recognizedHeroOptionsFlow,
        heroStatsCatalogFlow,
        cardStatsCatalogFlow,
        heroNameIndexFlow
    ) { recognizedHeroOptions, heroStatsCatalog, cardStatsCatalog, heroNameIndex ->
        HeroMetaInputs(
            recognizedHeroOptions = recognizedHeroOptions,
            heroStatsCatalog = heroStatsCatalog,
            cardStatsCatalog = cardStatsCatalog,
            heroNameIndex = heroNameIndex
        )
    }

    private val uiSupportMetaFlow = combine(
        uiCatalogMetaFlow,
        visionApiSettingsFlow,
        uiHeroMetaFlow,
        autoDetectStatusFlow,
        autoDetectDebugInfoFlow
    ) { catalogMeta, visionApiSettings, heroMeta, autoDetectStatus, autoDetectDebugInfo ->
        UiMetaInputs(
            selectedStrategyId = null,
            isRefreshing = false,
            syncMessage = catalogMeta.syncMessage,
            manifestVersion = catalogMeta.manifestVersion,
            cardRules = catalogMeta.cardRules,
            cardMetadata = catalogMeta.cardMetadata,
            visionApiSettings = visionApiSettings,
            recognizedHeroOptions = heroMeta.recognizedHeroOptions,
            heroStatsCatalog = heroMeta.heroStatsCatalog,
            cardStatsCatalog = heroMeta.cardStatsCatalog,
            heroNameIndex = heroMeta.heroNameIndex,
            autoDetectStatus = autoDetectStatus,
            autoDetectDebugInfo = autoDetectDebugInfo,
            selectedHeroCardId = null,
            selectedHeroName = null,
            selectedHeroSlot = null
        )
    }

    private val uiMetaFlow = combine(
        uiSelectionFlow,
        uiSupportMetaFlow,
        heroSelectionSessionFlow
    ) { selection, support, heroSession ->
        val useSessionSnapshot = heroSession.hasSharedSnapshot()
        support.copy(
            selectedStrategyId = selection.first,
            isRefreshing = selection.second,
            recognizedHeroOptions = if (useSessionSnapshot) {
                heroSession.recognizedHeroOptions
            } else {
                support.recognizedHeroOptions
            },
            selectedHeroCardId = heroSession.selectedHeroCardId,
            selectedHeroName = heroSession.selectedHeroName,
            selectedHeroSlot = heroSession.selectedHeroSlot
        )
    }

    val uiState = combine(filterInputsFlow, uiMetaFlow) { filterInputs, uiMeta ->
        val snapshot = filterInputs.snapshot
        val filtered = StrategyEngine.filter(
            allStrategies = snapshot?.catalog?.comps.orEmpty(),
            selectedTribes = filterInputs.selectedTribes
        ).sortedWith(compareBy<StrategyComp> { strategyTierRank(it.tier) }.thenBy { it.name })
        val recognizedHeroes = resolveRecognizedHeroes(
            heroOptions = uiMeta.recognizedHeroOptions,
            heroStatsCatalog = uiMeta.heroStatsCatalog,
            cardStatsCatalog = uiMeta.cardStatsCatalog,
            heroNameIndex = uiMeta.heroNameIndex,
            selectedTribes = filterInputs.selectedTribes,
            allStrategies = snapshot?.catalog?.comps.orEmpty()
        )
        val selectedHero = recognizedHeroes.firstOrNull { hero ->
            (!uiMeta.selectedHeroCardId.isNullOrBlank() && hero.heroCardId == uiMeta.selectedHeroCardId) ||
                (uiMeta.selectedHeroSlot != null && hero.slot == uiMeta.selectedHeroSlot) ||
                (!uiMeta.selectedHeroName.isNullOrBlank() &&
                    (hero.displayName == uiMeta.selectedHeroName || hero.recognizedName == uiMeta.selectedHeroName))
        }
        val orderedStrategies = sortStrategiesForSelectedHero(filtered, selectedHero)
        val resolvedId = uiMeta.selectedStrategyId?.takeIf { id -> orderedStrategies.any { it.id == id } }
            ?: selectedHero?.recommendation?.recommendedCompId?.takeIf { id -> orderedStrategies.any { it.id == id } }
            ?: orderedStrategies.firstOrNull()?.id

        DashboardUiState(
            appVersionLabel = BuildConfig.VERSION_NAME,
            catalogVersion = snapshot?.catalog?.version.orEmpty(),
            isLoading = snapshot == null,
            isRefreshing = uiMeta.isRefreshing,
            selectedTribes = filterInputs.selectedTribes,
            manifestUrlOverride = filterInputs.manifestUrlOverride,
            effectiveManifestUrl = resolveEffectiveManifestUrl(filterInputs.manifestUrlOverride),
            dataSource = snapshot?.source ?: StrategyDataSource.ASSET,
            lastSyncLabel = formatTimestamp(snapshot?.lastSyncAt),
            manifestVersionLabel = uiMeta.manifestVersion,
            syncMessage = uiMeta.syncMessage,
            autoDetectStatus = uiMeta.autoDetectStatus,
            autoDetectDebugInfo = uiMeta.autoDetectDebugInfo,
            visionBaseUrl = uiMeta.visionApiSettings.baseUrl,
            visionApiKey = uiMeta.visionApiSettings.apiKey,
            visionModel = uiMeta.visionApiSettings.model,
            visionBackupBaseUrl = uiMeta.visionApiSettings.backupBaseUrl,
            visionBackupApiKey = uiMeta.visionApiSettings.backupApiKey,
            visionBackupModel = uiMeta.visionApiSettings.backupModel,
            visionRoutingMode = uiMeta.visionApiSettings.routingMode,
            cardRules = uiMeta.cardRules,
            cardMetadata = uiMeta.cardMetadata,
            recognizedHeroes = recognizedHeroes,
            heroStatsUpdatedAtLabel = formatIsoTimestamp(uiMeta.heroStatsCatalog.lastUpdateDate),
            selectedHeroCardId = uiMeta.selectedHeroCardId,
            selectedHeroName = uiMeta.selectedHeroName,
            selectedHeroSlot = uiMeta.selectedHeroSlot,
            manualSelectedStrategyId = uiMeta.selectedStrategyId,
            allStrategies = snapshot?.catalog?.comps.orEmpty(),
            strategies = orderedStrategies,
            selectedStrategyId = resolvedId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    fun load(context: Context) {
        appContext = context.applicationContext

        val preferences = AppPreferences(context.applicationContext)
        val dashboardPreferences = preferences.loadDashboardPreferences()
        selectedTribesFlow.value = dashboardPreferences.selectedTribes
        manifestUrlOverrideFlow.value = dashboardPreferences.manifestUrlOverride
        visionApiSettingsFlow.value = preferences.loadVisionApiSettings()
        manifestVersionFlow.value = preferences.loadLastManifestVersion()

        if (catalogFlow.value != null) return

        viewModelScope.launch {
            val snapshot = repository.loadCatalog(context.applicationContext)
            cardRulesFlow.value = repository.loadCardRules(context.applicationContext)
            cardMetadataFlow.value = repository.loadBattlegroundCardMetadata(context.applicationContext)
            heroNameIndexFlow.value = repository.loadHeroNameIndex(context.applicationContext)
            heroStatsCatalogFlow.value = repository.loadHeroStats(context.applicationContext)
            cardStatsCatalogFlow.value = repository.loadCardStats(context.applicationContext)
            catalogFlow.value = snapshot
            if (hasConfiguredManifestSource()) {
                refreshCatalog(silent = true)
            } else {
                refreshHeroStats(silent = true)
            }
        }
    }

    fun updateManifestUrlOverride(value: String) {
        manifestUrlOverrideFlow.value = value
        persistDashboardPreferences()
    }

    fun updateVisionRoutingMode(mode: VisionRoutingMode) {
        val context = appContext ?: return
        AppPreferences(context).saveVisionRoutingMode(mode)
        visionApiSettingsFlow.value = AppPreferences(context).loadVisionApiSettings()
    }

    fun refreshCatalog(silent: Boolean = false) {
        val context = appContext ?: return
        if (!hasConfiguredManifestSource()) {
            if (!silent) {
                syncMessageFlow.value = "请先配置默认 manifest 地址，或填写调试覆盖地址"
            }
            return
        }

        viewModelScope.launch {
            isRefreshingFlow.value = true
            if (!silent) {
                syncMessageFlow.value = null
            }
            runCatching {
                repository.refreshCatalog(context, manifestUrlOverrideFlow.value.trim())
            }.onSuccess { result ->
                catalogFlow.value = result.snapshot
                cardRulesFlow.value = repository.loadCardRules(context, ignoreMemoryCache = true)
                cardMetadataFlow.value = repository.loadBattlegroundCardMetadata(context, ignoreMemoryCache = true)
                heroNameIndexFlow.value = repository.loadHeroNameIndex(context, ignoreMemoryCache = true)
                runCatching {
                    repository.loadHeroStats(context, ignoreMemoryCache = true)
                }.onSuccess { heroStats ->
                    heroStatsCatalogFlow.value = heroStats
                }
                runCatching {
                    repository.loadCardStats(context, ignoreMemoryCache = true)
                }.onSuccess { cardStats ->
                    cardStatsCatalogFlow.value = cardStats
                }
                manifestVersionFlow.value = result.manifestVersion
                syncMessageFlow.value = when {
                    result.wasUpdated && result.warnings.isNotEmpty() ->
                        "已同步到 ${result.snapshot.catalog.version}；${result.warnings.first()}"
                    result.wasUpdated -> "已同步到 ${result.snapshot.catalog.version}"
                    result.warnings.isNotEmpty() ->
                        "主数据已检查完成；${result.warnings.first()}"
                    silent -> "已检查更新，当前已是最新版本"
                    else -> "当前已是最新版本"
                }
            }.onFailure { error ->
                if (!silent) {
                    syncMessageFlow.value = error.message ?: "远程刷新失败"
                }
            }
            isRefreshingFlow.value = false
        }
    }

    fun selectStrategy(strategyId: String) {
        selectedStrategyFlow.value = strategyId
    }

    fun resolvePreviewHeroes(result: HeroSelectionVisionResult): List<ResolvedHeroStatOption> {
        val detectedTribes = result.toDomainTribes()
        val effectiveSelectedTribes = detectedTribes.takeIf { it.size == 5 } ?: selectedTribesFlow.value
        return resolveRecognizedHeroes(
            heroOptions = result.selectableHeroOptions,
            heroStatsCatalog = heroStatsCatalogFlow.value,
            cardStatsCatalog = cardStatsCatalogFlow.value,
            heroNameIndex = heroNameIndexFlow.value,
            selectedTribes = effectiveSelectedTribes,
            allStrategies = catalogFlow.value?.catalog?.comps.orEmpty()
        )
    }

    fun applyVisionResult(result: HeroSelectionVisionResult) {
        val validation = HeroSelectionVisionValidator.validate(
            result,
            requireCompleteTribes = false
        )
        if (!validation.isValid) {
            syncMessageFlow.value = "AI 识图结果未通过校验：${validation.errors.firstOrNull() ?: "未知错误"}"
            return
        }

        val recognizedHeroNames = result.selectableHeroOptions
            .mapNotNull { option -> option.name?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
        val detectedTribes = result.toDomainTribes()
        val effectiveSelectedTribes = detectedTribes.takeIf { it.size == 5 } ?: selectedTribesFlow.value
        val resolvedHeroes = resolveRecognizedHeroes(
            heroOptions = result.selectableHeroOptions,
            heroStatsCatalog = heroStatsCatalogFlow.value,
            cardStatsCatalog = cardStatsCatalogFlow.value,
            heroNameIndex = heroNameIndexFlow.value,
            selectedTribes = effectiveSelectedTribes,
            allStrategies = catalogFlow.value?.catalog?.comps.orEmpty()
        )

        if (detectedTribes.size == 5) {
            selectedTribesFlow.value = detectedTribes
        }
        recognizedHeroOptionsFlow.value = result.selectableHeroOptions
        HeroSelectionSessionStore.updateVisionResult(
            selectedTribes = effectiveSelectedTribes,
            recognizedHeroOptions = result.selectableHeroOptions,
            recognizedHeroes = resolvedHeroes
        )
        selectedStrategyFlow.value = null
        syncMessageFlow.value = buildString {
            append("已应用 AI 识图结果：")
            if (detectedTribes.size == 5) {
                append(result.availableTribes.joinToString(" / ") { it.label })
            } else {
                append("5族未稳定，沿用当前环境")
            }
            if (recognizedHeroNames.isNotEmpty()) {
                append("；可用英雄：")
                append(recognizedHeroNames.joinToString(" / "))
            }
            if (resolvedHeroes.isNotEmpty()) {
                append("；统计命中 ${resolvedHeroes.count { it.hasStats }}/")
                append(resolvedHeroes.size)
            }
        }
        persistDashboardPreferences()
    }

    private fun refreshHeroStats(silent: Boolean) {
        val context = appContext ?: return
        viewModelScope.launch {
            runCatching {
                repository.refreshHeroStats(context)
            }.onSuccess { heroStats ->
                heroStatsCatalogFlow.value = heroStats
            }.onFailure { error ->
                if (!silent && syncMessageFlow.value.isNullOrBlank()) {
                    syncMessageFlow.value = error.message ?: "英雄统计同步失败"
                }
            }
        }
    }

    private fun persistDashboardPreferences() {
        val context = appContext ?: return
        AppPreferences(context).saveDashboardPreferences(
            selectedTribes = selectedTribesFlow.value,
            manifestUrlOverride = manifestUrlOverrideFlow.value
        )
    }

    private fun hasConfiguredManifestSource(): Boolean {
        return manifestUrlOverrideFlow.value.isNotBlank() || BuildConfig.DEFAULT_MANIFEST_URL.isNotBlank()
    }

    private fun strategyTierRank(tier: String): Int = when (tier.uppercase(Locale.US)) {
        "T0", "S" -> 0
        "T1", "A" -> 1
        "T2", "B" -> 2
        "T3", "C" -> 3
        else -> 4
    }

    private fun resolveEffectiveManifestUrl(override: String): String {
        return override.ifBlank { BuildConfig.DEFAULT_MANIFEST_URL }
    }

    private fun resolveRecognizedHeroes(
        heroOptions: List<HeroSelectionVisionHeroOption>,
        heroStatsCatalog: BattlegroundHeroStatsCatalog,
        cardStatsCatalog: BattlegroundCardStatsCatalog,
        heroNameIndex: BattlegroundHeroNameIndex,
        selectedTribes: Set<Tribe>,
        allStrategies: List<StrategyComp>
    ): List<ResolvedHeroStatOption> {
        return HeroSelectionRecommendationEngine.resolveRecognizedHeroes(
            heroOptions = heroOptions,
            heroStatsCatalog = heroStatsCatalog,
            cardStatsCatalog = cardStatsCatalog,
            heroNameIndex = heroNameIndex,
            selectedTribes = selectedTribes,
            allStrategies = allStrategies
        )
    }

    private fun HeroSelectionSessionState.hasSharedSnapshot(): Boolean {
        return selectedTribes.isNotEmpty() ||
            recognizedHeroOptions.isNotEmpty() ||
            recognizedHeroes.isNotEmpty() ||
            !selectedHeroCardId.isNullOrBlank() ||
            !selectedHeroName.isNullOrBlank() ||
            selectedHeroSlot != null
    }

    private fun sortStrategiesForSelectedHero(
        strategies: List<StrategyComp>,
        selectedHero: ResolvedHeroStatOption?
    ): List<StrategyComp> {
        val pinnedIds = listOfNotNull(
            selectedHero?.recommendation?.recommendedCompId,
            selectedHero?.recommendation?.fallbackCompId
        )
        if (pinnedIds.isEmpty()) return strategies
        val originalOrder = strategies.withIndex().associate { it.value.id to it.index }
        return strategies.sortedWith(
            compareByDescending<StrategyComp> { strategy ->
                when (strategy.id) {
                    pinnedIds.getOrNull(0) -> 2
                    pinnedIds.getOrNull(1) -> 1
                    else -> 0
                }
            }.thenBy { originalOrder[it.id] ?: Int.MAX_VALUE }
        )
    }

    private fun formatTimestamp(timestamp: Long?): String? {
        timestamp ?: return null
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    private fun formatIsoTimestamp(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf(String::isNotBlank) ?: return null
        return trimmed
            .replace('T', ' ')
            .removeSuffix("Z")
            .take(16)
    }

}
