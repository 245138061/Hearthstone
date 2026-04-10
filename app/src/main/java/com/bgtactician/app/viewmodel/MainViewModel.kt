package com.bgtactician.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgtactician.app.BuildConfig
import com.bgtactician.app.data.local.AppPreferences
import com.bgtactician.app.data.local.OverlaySettings
import com.bgtactician.app.data.model.CardRulesCatalog
import com.bgtactician.app.data.model.CatalogSnapshot
import com.bgtactician.app.data.model.StrategyComp
import com.bgtactician.app.data.model.StrategyDataSource
import com.bgtactician.app.data.model.Tribe
import com.bgtactician.app.data.repository.StrategyEngine
import com.bgtactician.app.data.repository.StrategyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val overlayInteractionEnabled: Boolean = true,
    val bubbleOpacityPercent: Int = 72,
    val cardRules: CardRulesCatalog = emptyMap(),
    val allStrategies: List<StrategyComp> = emptyList(),
    val strategies: List<StrategyComp> = emptyList(),
    val selectedStrategyId: String? = null
) {
    val selectedStrategy: StrategyComp?
        get() = strategies.firstOrNull { it.id == selectedStrategyId }
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
        val awaitingManualStrategySelection: Boolean,
        val isRefreshing: Boolean,
        val syncMessage: String?,
        val overlaySettings: OverlaySettings,
        val manifestVersion: String?,
        val cardRules: CardRulesCatalog
    )

    private val catalogFlow = MutableStateFlow<CatalogSnapshot?>(null)
    private val selectedTribesFlow = MutableStateFlow(
        setOf(Tribe.MECH, Tribe.DEMON, Tribe.UNDEAD, Tribe.PIRATE, Tribe.ELEMENTAL)
    )
    private val manifestUrlOverrideFlow = MutableStateFlow("")
    private val selectedStrategyFlow = MutableStateFlow<String?>(null)
    private val awaitingManualStrategySelectionFlow = MutableStateFlow(false)
    private val isRefreshingFlow = MutableStateFlow(false)
    private val syncMessageFlow = MutableStateFlow<String?>(null)
    private val overlaySettingsFlow = MutableStateFlow(OverlaySettings())
    private val manifestVersionFlow = MutableStateFlow<String?>(null)
    private val cardRulesFlow = MutableStateFlow<CardRulesCatalog>(emptyMap())
    private var appContext: Context? = null

    private val filterInputsFlow = combine(
        catalogFlow,
        selectedTribesFlow,
        manifestUrlOverrideFlow
    ) { snapshot, tribes, manifestUrlOverride ->
        FilterInputs(
            snapshot = snapshot,
            selectedTribes = tribes,
            manifestUrlOverride = manifestUrlOverride
        )
    }

    private val uiSelectionFlow = combine(
        selectedStrategyFlow,
        awaitingManualStrategySelectionFlow,
        isRefreshingFlow
    ) { selectedId, awaitingManualSelection, isRefreshing ->
        Triple(selectedId, awaitingManualSelection, isRefreshing)
    }

    private val uiMetaFlow = combine(
        uiSelectionFlow,
        syncMessageFlow,
        overlaySettingsFlow,
        manifestVersionFlow,
        cardRulesFlow
    ) { selection, syncMessage, overlaySettings, manifestVersion, cardRules ->
        UiMetaInputs(
            selectedStrategyId = selection.first,
            awaitingManualStrategySelection = selection.second,
            isRefreshing = selection.third,
            syncMessage = syncMessage,
            overlaySettings = overlaySettings,
            manifestVersion = manifestVersion,
            cardRules = cardRules
        )
    }

    val uiState = combine(filterInputsFlow, uiMetaFlow) { filterInputs, uiMeta ->
        val snapshot = filterInputs.snapshot
        val filtered = StrategyEngine.filter(
            allStrategies = snapshot?.catalog?.comps.orEmpty(),
            selectedTribes = filterInputs.selectedTribes
        ).sortedWith(compareBy<StrategyComp> { strategyTierRank(it.tier) }.thenBy { it.name })
        val resolvedId = uiMeta.selectedStrategyId?.takeIf { id -> filtered.any { it.id == id } }
            ?: if (uiMeta.awaitingManualStrategySelection) null else filtered.firstOrNull()?.id

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
            overlayInteractionEnabled = uiMeta.overlaySettings.interactionEnabled,
            bubbleOpacityPercent = uiMeta.overlaySettings.bubbleOpacityPercent,
            cardRules = uiMeta.cardRules,
            allStrategies = snapshot?.catalog?.comps.orEmpty(),
            strategies = filtered,
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
        overlaySettingsFlow.value = preferences.loadOverlaySettings()
        manifestVersionFlow.value = preferences.loadLastManifestVersion()

        if (catalogFlow.value != null) return

        viewModelScope.launch {
            val snapshot = repository.loadCatalog(context.applicationContext)
            cardRulesFlow.value = repository.loadCardRules(context.applicationContext)
            catalogFlow.value = snapshot
            if (hasConfiguredManifestSource()) {
                refreshCatalog(silent = true)
            }
        }
    }

    fun toggleTribe(tribe: Tribe) {
        var changed = false
        selectedTribesFlow.update { current ->
            when {
                current.contains(tribe) -> {
                    changed = true
                    current - tribe
                }
                current.size >= 5 -> current
                else -> {
                    changed = true
                    current + tribe
                }
            }
        }
        if (changed) {
            selectedStrategyFlow.value = null
            awaitingManualStrategySelectionFlow.value = true
        }
        persistDashboardPreferences()
    }

    fun updateManifestUrlOverride(value: String) {
        manifestUrlOverrideFlow.value = value
        persistDashboardPreferences()
    }

    fun setOverlayInteractionEnabled(enabled: Boolean) {
        overlaySettingsFlow.update { it.copy(interactionEnabled = enabled) }
        persistOverlaySettings()
    }

    fun setBubbleOpacityPercent(value: Int) {
        overlaySettingsFlow.update { it.copy(bubbleOpacityPercent = value.coerceIn(35, 100)) }
        persistOverlaySettings()
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
                manifestVersionFlow.value = result.manifestVersion
                syncMessageFlow.value = when {
                    result.wasUpdated -> "已同步到 ${result.snapshot.catalog.version}"
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
        awaitingManualStrategySelectionFlow.value = false
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

    private fun persistOverlaySettings() {
        val context = appContext ?: return
        val settings = overlaySettingsFlow.value
        AppPreferences(context).saveOverlaySettings(
            interactionEnabled = settings.interactionEnabled,
            bubbleOpacityPercent = settings.bubbleOpacityPercent
        )
    }

    private fun formatTimestamp(timestamp: Long?): String? {
        timestamp ?: return null
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
}
