package com.bgtactician.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgtactician.app.BuildConfig
import com.bgtactician.app.data.local.AppPreferences
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
    val dataSource: StrategyDataSource = StrategyDataSource.ASSET,
    val lastSyncLabel: String? = null,
    val manifestVersionLabel: String? = null,
    val syncMessage: String? = null,
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

    private val catalogFlow = MutableStateFlow<CatalogSnapshot?>(null)
    private val selectedTribesFlow = MutableStateFlow(AppPreferences.DEFAULT_TRIBES)
    private val manifestUrlOverrideFlow = MutableStateFlow("")
    private val selectedStrategyFlow = MutableStateFlow<String?>(null)
    private val isRefreshingFlow = MutableStateFlow(false)
    private val syncMessageFlow = MutableStateFlow<String?>(null)
    private val manifestVersionFlow = MutableStateFlow<String?>(null)
    private var appContext: Context? = null

    private val selectionFlow = combine(
        catalogFlow,
        selectedTribesFlow,
        selectedStrategyFlow
    ) { snapshot, selectedTribes, selectedStrategyId ->
        Triple(snapshot, selectedTribes, selectedStrategyId)
    }

    private val metaFlow = combine(
        isRefreshingFlow,
        syncMessageFlow,
        manifestVersionFlow
    ) { isRefreshing, syncMessage, manifestVersion ->
        Triple(isRefreshing, syncMessage, manifestVersion)
    }

    val uiState = combine(selectionFlow, metaFlow) { selection, meta ->
        val snapshot = selection.first
        val selectedTribes = selection.second
        val selectedStrategyId = selection.third
        val isRefreshing = meta.first
        val syncMessage = meta.second
        val manifestVersion = meta.third
        val filteredStrategies = StrategyEngine.filter(
            allStrategies = snapshot?.catalog?.comps.orEmpty(),
            selectedTribes = selectedTribes
        )
        val resolvedStrategyId = selectedStrategyId
            ?.takeIf { id -> filteredStrategies.any { it.id == id } }
            ?: filteredStrategies.firstOrNull()?.id

        DashboardUiState(
            appVersionLabel = BuildConfig.VERSION_NAME,
            catalogVersion = snapshot?.catalog?.version.orEmpty(),
            isLoading = snapshot == null,
            isRefreshing = isRefreshing,
            selectedTribes = selectedTribes,
            dataSource = snapshot?.source ?: StrategyDataSource.ASSET,
            lastSyncLabel = formatTimestamp(snapshot?.lastSyncAt),
            manifestVersionLabel = manifestVersion,
            syncMessage = syncMessage,
            allStrategies = snapshot?.catalog?.comps.orEmpty(),
            strategies = filteredStrategies,
            selectedStrategyId = resolvedStrategyId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    fun load(context: Context) {
        appContext = context.applicationContext
        syncLocalState(context.applicationContext)

        if (catalogFlow.value != null) return

        viewModelScope.launch {
            catalogFlow.value = repository.loadCatalog(context.applicationContext)
            if (hasConfiguredManifestSource()) {
                refreshCatalog(silent = true)
            }
        }
    }

    fun syncLocalState(context: Context) {
        val preferences = AppPreferences(context.applicationContext)
        val dashboardPreferences = preferences.loadDashboardPreferences()
        selectedTribesFlow.value = dashboardPreferences.selectedTribes
        manifestUrlOverrideFlow.value = dashboardPreferences.manifestUrlOverride
        manifestVersionFlow.value = preferences.loadLastManifestVersion()
    }

    fun refreshCatalog(silent: Boolean = false) {
        val context = appContext ?: return
        if (!hasConfiguredManifestSource()) {
            if (!silent) {
                syncMessageFlow.value = "当前没有配置远程数据源，正在使用内置流派数据"
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
                manifestVersionFlow.value = result.manifestVersion
                syncMessageFlow.value = when {
                    result.wasUpdated && result.warnings.isNotEmpty() ->
                        "已同步到 ${result.snapshot.catalog.version}；${result.warnings.first()}"
                    result.wasUpdated ->
                        "已同步到 ${result.snapshot.catalog.version}"
                    result.warnings.isNotEmpty() ->
                        "主数据已检查完成；${result.warnings.first()}"
                    silent ->
                        "已检查更新，当前已是最新版本"
                    else ->
                        "当前已是最新版本"
                }
            }.onFailure { error ->
                if (!silent) {
                    syncMessageFlow.value = error.message ?: "远程刷新失败"
                }
            }
            isRefreshingFlow.value = false
        }
    }

    fun updateSelectedTribes(tribes: Set<Tribe>) {
        if (tribes.size > 5) return
        selectedTribesFlow.value = tribes
        selectedStrategyFlow.value = null
        persistDashboardPreferences()
    }

    fun selectStrategy(strategyId: String) {
        selectedStrategyFlow.value = strategyId
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

    private fun formatTimestamp(timestamp: Long?): String? {
        timestamp ?: return null
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }
}
