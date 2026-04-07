package com.bgtactician.app.data.model

enum class StrategyDataSource {
    ASSET,
    CACHE,
    REMOTE
}

data class CatalogSnapshot(
    val catalog: StrategyCatalog,
    val source: StrategyDataSource,
    val lastSyncAt: Long? = null
)
