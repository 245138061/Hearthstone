package com.bgtactician.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgtactician.app.BuildConfig
import com.bgtactician.app.data.local.AppPreferences
import com.bgtactician.app.data.local.OverlaySettings
import com.bgtactician.app.data.model.AnomalyPreset
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
    val catalogVersion: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val selectedTribes: Set<Tribe> = emptySet(),
    val selectedAnomaly: String = AnomalyPreset.NONE,
    val isDuos: Boolean = false,
    val manifestUrlOverride: String = "",
    val effectiveManifestUrl: String = "",
    val dataSource: StrategyDataSource = StrategyDataSource.ASSET,
    val lastSyncLabel: String? = null,
    val manifestVersionLabel: String? = null,
    val syncMessage: String? = null,
    val overlayInteractionEnabled: Boolean = true,
    val bubbleOpacityPercent: Int = 72,
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
        val selectedAnomaly: String,
        val isDuos: Boolean,
        val manifestUrlOverride: String
    )

    private data class UiMetaInputs(
        val selectedStrategyId: String?,
        val isRefreshing: Boolean,
        val syncMessage: String?,
        val overlaySettings: OverlaySettings,
        val manifestVersion: String?
    )

    private val catalogFlow = MutableStateFlow<CatalogSnapshot?>(null)
    private val selectedTribesFlow = MutableStateFlow(
        setOf(Tribe.MECH, Tribe.DEMON, Tribe.UNDEAD, Tribe.PIRATE, Tribe.ELEMENTAL)
    )
    private val selectedAnomalyFlow = MutableStateFlow(AnomalyPreset.NONE)
    private val duosModeFlow = MutableStateFlow(false)
    private val manifestUrlOverrideFlow = MutableStateFlow("")
    private val selectedStrategyFlow = MutableStateFlow<String?>(null)
    private val isRefreshingFlow = MutableStateFlow(false)
    private val syncMessageFlow = MutableStateFlow<String?>(null)
    private val overlaySettingsFlow = MutableStateFlow(OverlaySettings())
    private val manifestVersionFlow = MutableStateFlow<String?>(null)
    private var appContext: Context? = null

    private val filterInputsFlow = combine(
        catalogFlow,
        selectedTribesFlow,
        selectedAnomalyFlow,
        duosModeFlow,
        manifestUrlOverrideFlow
    ) { snapshot, tribes, anomaly, duos, manifestUrlOverride ->
        FilterInputs(
            snapshot = snapshot,
            selectedTribes = tribes,
            selectedAnomaly = anomaly,
            isDuos = duos,
            manifestUrlOverride = manifestUrlOverride
        )
    }

    private val uiMetaFlow = combine(
        selectedStrategyFlow,
        isRefreshingFlow,
        syncMessageFlow,
        overlaySettingsFlow,
        manifestVersionFlow
    ) { selectedId, isRefreshing, syncMessage, overlaySettings, manifestVersion ->
        UiMetaInputs(
            selectedStrategyId = selectedId,
            isRefreshing = isRefreshing,
            syncMessage = syncMessage,
            overlaySettings = overlaySettings,
            manifestVersion = manifestVersion
        )
    }

    val uiState = combine(filterInputsFlow, uiMetaFlow) { filterInputs, uiMeta ->
        val snapshot = filterInputs.snapshot
        val filtered = StrategyEngine.filter(
            allStrategies = snapshot?.catalog?.comps.orEmpty(),
            selectedTribes = filterInputs.selectedTribes,
            selectedAnomaly = filterInputs.selectedAnomaly,
            isDuos = filterInputs.isDuos
        )
        val resolvedId = uiMeta.selectedStrategyId?.takeIf { id -> filtered.any { it.id == id } }
            ?: filtered.firstOrNull()?.id

        DashboardUiState(
            catalogVersion = snapshot?.catalog?.version.orEmpty(),
            isLoading = snapshot == null,
            isRefreshing = uiMeta.isRefreshing,
            selectedTribes = filterInputs.selectedTribes,
            selectedAnomaly = filterInputs.selectedAnomaly,
            isDuos = filterInputs.isDuos,
            manifestUrlOverride = filterInputs.manifestUrlOverride,
            effectiveManifestUrl = resolveEffectiveManifestUrl(filterInputs.manifestUrlOverride),
            dataSource = snapshot?.source ?: StrategyDataSource.ASSET,
            lastSyncLabel = formatTimestamp(snapshot?.lastSyncAt),
            manifestVersionLabel = uiMeta.manifestVersion,
            syncMessage = uiMeta.syncMessage,
            overlayInteractionEnabled = uiMeta.overlaySettings.interactionEnabled,
            bubbleOpacityPercent = uiMeta.overlaySettings.bubbleOpacityPercent,
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
        selectedAnomalyFlow.value = dashboardPreferences.selectedAnomaly
        duosModeFlow.value = dashboardPreferences.isDuos
        manifestUrlOverrideFlow.value = dashboardPreferences.manifestUrlOverride
        overlaySettingsFlow.value = preferences.loadOverlaySettings()
        manifestVersionFlow.value = preferences.loadLastManifestVersion()

        if (catalogFlow.value != null) return

        viewModelScope.launch {
            val snapshot = repository.loadCatalog(context.applicationContext)
            catalogFlow.value = snapshot
            selectedStrategyFlow.value = snapshot.catalog.comps.firstOrNull()?.id
            if (hasConfiguredManifestSource()) {
                refreshCatalog(silent = true)
            }
        }
    }

    fun toggleTribe(tribe: Tribe) {
        selectedTribesFlow.update { current ->
            when {
                current.contains(tribe) -> current - tribe
                current.size >= 5 -> current
                else -> current + tribe
            }
        }
        persistDashboardPreferences()
    }

    fun selectAnomaly(anomaly: String) {
        selectedAnomalyFlow.value = anomaly
        persistDashboardPreferences()
    }

    fun setDuosMode(enabled: Boolean) {
        duosModeFlow.value = enabled
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
                selectedStrategyFlow.value = result.snapshot.catalog.comps.firstOrNull()?.id
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
    }

    private fun persistDashboardPreferences() {
        val context = appContext ?: return
        AppPreferences(context).saveDashboardPreferences(
            selectedTribes = selectedTribesFlow.value,
            selectedAnomaly = selectedAnomalyFlow.value,
            isDuos = duosModeFlow.value,
            manifestUrlOverride = manifestUrlOverrideFlow.value
        )
    }

    private fun hasConfiguredManifestSource(): Boolean {
        return manifestUrlOverrideFlow.value.isNotBlank() || BuildConfig.DEFAULT_MANIFEST_URL.isNotBlank()
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
