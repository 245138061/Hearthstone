package com.bgtactician.app.data.repository

import android.content.Context
import com.bgtactician.app.BuildConfig
import com.bgtactician.app.data.local.AppPreferences
import com.bgtactician.app.data.model.BattlegroundCardMetadataCatalog
import com.bgtactician.app.data.model.BattlegroundCardStatsCatalog
import com.bgtactician.app.data.model.CardRuleEntry
import com.bgtactician.app.data.model.CardRulesCatalog
import com.bgtactician.app.data.model.BattlegroundHeroNameIndex
import com.bgtactician.app.data.model.BattlegroundHeroStatsCatalog
import com.bgtactician.app.data.model.CatalogRefreshResult
import com.bgtactician.app.data.model.CatalogSnapshot
import com.bgtactician.app.data.model.RemoteCatalogFile
import com.bgtactician.app.data.model.RemoteManifest
import com.bgtactician.app.data.model.StrategyCatalog
import com.bgtactician.app.data.model.StrategyDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.min

class StrategyRepository {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private var cachedSnapshot: CatalogSnapshot? = null
    private var cachedCardMetadata: BattlegroundCardMetadataCatalog? = null
    private var cachedCardRules: CardRulesCatalog? = null
    private var cachedCardStats: BattlegroundCardStatsCatalog? = null
    private var cachedHeroStats: BattlegroundHeroStatsCatalog? = null
    private var cachedHeroNameIndex: BattlegroundHeroNameIndex? = null

    suspend fun loadCatalog(context: Context, ignoreMemoryCache: Boolean = false): CatalogSnapshot {
        if (!ignoreMemoryCache) {
            cachedSnapshot?.let { return it }
        }

        return withContext(Dispatchers.IO) {
            val preferences = AppPreferences(context)
            val cacheFile = cacheFile(context)
            val assetSnapshot = bundledSnapshot(context)

            val cached = if (cacheFile.exists()) {
                runCatching {
                    val catalog = decode(cacheFile.readText())
                    catalog.takeIf(::hasRenderableMinionMetadata)?.let {
                        CatalogSnapshot(
                            catalog = it,
                            source = StrategyDataSource.CACHE,
                            lastSyncAt = preferences.loadLastSync() ?: cacheFile.lastModified().takeIf { it > 0L }
                        )
                    }
                }.getOrNull()
            } else {
                null
            }

            val snapshot = cached ?: assetSnapshot

            MinionImageCache.schedulePrefetch(context, snapshot.catalog)
            snapshot.also { cachedSnapshot = it }
        }
    }

    suspend fun loadCardRules(context: Context, ignoreMemoryCache: Boolean = false): CardRulesCatalog {
        if (!ignoreMemoryCache) {
            cachedCardRules?.let { return it }
        }

        return withContext(Dispatchers.IO) {
            val cacheFile = cardRulesCacheFile(context)
            val snapshot = if (cacheFile.exists()) {
                runCatching {
                    decodeCardRules(cacheFile.readText())
                }.getOrDefault(emptyMap())
            } else {
                emptyMap()
            }
            snapshot.also { cachedCardRules = it }
        }
    }

    suspend fun loadBattlegroundCardMetadata(
        context: Context,
        ignoreMemoryCache: Boolean = false
    ): BattlegroundCardMetadataCatalog {
        if (!ignoreMemoryCache) {
            cachedCardMetadata?.let { return it }
        }

        return withContext(Dispatchers.IO) {
            val cacheFile = cardMetadataCacheFile(context)
            val snapshot = when {
                cacheFile.exists() -> runCatching {
                    decodeCardMetadata(cacheFile.readText())
                }.getOrElse {
                    bundledCardMetadata(context)
                }

                else -> bundledCardMetadata(context)
            }
            snapshot.also { cachedCardMetadata = it }
        }
    }

    suspend fun loadHeroStats(
        context: Context,
        ignoreMemoryCache: Boolean = false
    ): BattlegroundHeroStatsCatalog {
        if (!ignoreMemoryCache) {
            cachedHeroStats?.let { return it }
        }

        return withContext(Dispatchers.IO) {
            val cacheFile = heroStatsCacheFile(context)
            val snapshot = if (cacheFile.exists()) {
                runCatching {
                    decodeHeroStats(cacheFile.readText())
                }.getOrDefault(BattlegroundHeroStatsCatalog())
            } else {
                BattlegroundHeroStatsCatalog()
            }
            snapshot.also { cachedHeroStats = it }
        }
    }

    suspend fun loadCardStats(
        context: Context,
        ignoreMemoryCache: Boolean = false
    ): BattlegroundCardStatsCatalog {
        if (!ignoreMemoryCache) {
            cachedCardStats?.let { return it }
        }

        return withContext(Dispatchers.IO) {
            val cacheFile = cardStatsCacheFile(context)
            val snapshot = if (cacheFile.exists()) {
                runCatching {
                    decodeCardStats(cacheFile.readText())
                }.getOrDefault(BattlegroundCardStatsCatalog())
            } else {
                BattlegroundCardStatsCatalog()
            }
            snapshot.also { cachedCardStats = it }
        }
    }

    suspend fun refreshHeroStats(context: Context): BattlegroundHeroStatsCatalog {
        return withContext(Dispatchers.IO) {
            val raw = downloadTextWithRetry(HERO_STATS_URL)
            val decoded = decodeHeroStats(raw)
            writeAtomically(heroStatsCacheFile(context), raw)
            decoded.also { cachedHeroStats = it }
        }
    }

    suspend fun loadHeroNameIndex(
        context: Context,
        ignoreMemoryCache: Boolean = false
    ): BattlegroundHeroNameIndex {
        if (!ignoreMemoryCache) {
            cachedHeroNameIndex?.let { return it }
        }

        return withContext(Dispatchers.IO) {
            val snapshot = decodeHeroNameIndex(
                context.assets.open(HERO_NAME_INDEX_ASSET).bufferedReader().use { it.readText() }
            )
            snapshot.also { cachedHeroNameIndex = it }
        }
    }

    suspend fun refreshCatalog(
        context: Context,
        manifestUrlOverride: String = ""
    ): CatalogRefreshResult {
        return withContext(Dispatchers.IO) {
            val preferences = AppPreferences(context)
            val manifestUrl = resolveManifestUrl(manifestUrlOverride)
            val manifestRaw = downloadTextWithRetry(manifestUrl)
            val manifest = decodeManifest(manifestRaw)
            require(manifest.manifestFormat == "bgtactician.pages.v1") {
                "远程 manifest 格式不受支持"
            }
            require(manifest.schemaVersion == 1) {
                "远程 manifest schema 版本不受支持"
            }
            val selectedLocale = preferredLocale(manifest)
            val selectedFile = manifest.files[selectedLocale]
                ?: manifest.files[manifest.defaultLocale]
                ?: manifest.files["enUS"]
                ?: manifest.files.values.firstOrNull()
                ?: throw IllegalStateException("远程 manifest 中没有可用的数据文件")
            val catalogUrl = resolveCatalogUrl(manifestUrl, selectedFile)
            val cacheFile = cacheFile(context)
            val cacheText = when {
                cacheFile.exists() -> cacheFile.readText()
                else -> null
            }
            val existingHash = when {
                cacheText != null -> sha256(cacheText)
                else -> preferences.loadLastCatalogHash()
            }
            val syncedAt = System.currentTimeMillis()
            val catalogUpdated = existingHash != selectedFile.sha256

            val snapshot = if (cacheText != null && existingHash == selectedFile.sha256) {
                val catalog = decode(cacheText)
                if (hasRenderableMinionMetadata(catalog)) {
                    CatalogSnapshot(
                        catalog = catalog,
                        source = StrategyDataSource.CACHE,
                        lastSyncAt = syncedAt
                    )
                } else {
                    bundledSnapshot(context)
                }
            } else {
                val raw = downloadTextWithRetry(catalogUrl)
                val actualHash = sha256(raw)
                require(actualHash == selectedFile.sha256) {
                    "远程数据校验失败，哈希不匹配"
                }
                val catalog = decode(raw)
                if (hasRenderableMinionMetadata(catalog)) {
                    writeAtomically(cacheFile, raw)
                    preferences.saveLastCatalogHash(actualHash)
                    CatalogSnapshot(
                        catalog = catalog,
                        source = StrategyDataSource.REMOTE,
                        lastSyncAt = syncedAt
                    )
                } else {
                    bundledSnapshot(context)
                }
            }
            val supportSync = syncSupportFiles(
                context = context,
                manifest = manifest,
                manifestUrl = manifestUrl
            )

            preferences.saveLastSync(syncedAt)
            preferences.saveLastManifestVersion(manifest.version)
            preferences.saveLastManifestUpdatedAt(manifest.updatedAt)
            MinionImageCache.schedulePrefetch(context, snapshot.catalog)

            CatalogRefreshResult(
                snapshot = snapshot.also { cachedSnapshot = it },
                wasUpdated = catalogUpdated || supportSync.updated,
                manifestVersion = manifest.version,
                manifestUpdatedAt = manifest.updatedAt,
                sourceUrl = catalogUrl,
                warnings = supportSync.warnings
            )
        }
    }

    private fun cacheFile(context: Context): File = File(context.filesDir, CACHE_FILE)

    private fun cardRulesCacheFile(context: Context): File = File(context.filesDir, CARD_RULES_CACHE_FILE)

    private fun cardMetadataCacheFile(context: Context): File = File(context.filesDir, CARD_METADATA_CACHE_FILE)

    private fun cardStatsCacheFile(context: Context): File = File(context.filesDir, CARD_STATS_CACHE_FILE)

    private fun heroStatsCacheFile(context: Context): File = File(context.filesDir, HERO_STATS_CACHE_FILE)

    private fun bundledSnapshot(context: Context): CatalogSnapshot {
        return CatalogSnapshot(
            catalog = decode(context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }),
            source = StrategyDataSource.ASSET
        )
    }

    private fun bundledCardMetadata(context: Context): BattlegroundCardMetadataCatalog {
        return decodeCardMetadata(
            context.assets.open(CARD_METADATA_ASSET_FILE).bufferedReader().use { it.readText() }
        )
    }

    private fun decode(raw: String): StrategyCatalog = json.decodeFromString<StrategyCatalog>(raw)

    private fun decodeCardRules(raw: String): CardRulesCatalog = json.decodeFromString<Map<String, CardRuleEntry>>(raw)

    private fun decodeCardMetadata(raw: String): BattlegroundCardMetadataCatalog =
        json.decodeFromString<BattlegroundCardMetadataCatalog>(raw)

    private fun decodeCardStats(raw: String): BattlegroundCardStatsCatalog =
        json.decodeFromString<BattlegroundCardStatsCatalog>(raw)

    private fun decodeHeroStats(raw: String): BattlegroundHeroStatsCatalog =
        json.decodeFromString<BattlegroundHeroStatsCatalog>(raw)

    private fun decodeHeroNameIndex(raw: String): BattlegroundHeroNameIndex =
        json.decodeFromString<BattlegroundHeroNameIndex>(raw)

    private fun decodeManifest(raw: String): RemoteManifest = json.decodeFromString<RemoteManifest>(raw)

    private fun resolveManifestUrl(manifestUrlOverride: String): String {
        val candidate = manifestUrlOverride.trim().ifBlank { BuildConfig.DEFAULT_MANIFEST_URL.trim() }
        require(candidate.isNotBlank()) {
            "请先配置默认 manifest 地址，或在调试模式下填写覆盖地址"
        }
        validateHttps(candidate)
        if (!BuildConfig.DEBUG) {
            val defaultHost = BuildConfig.DEFAULT_MANIFEST_URL.trim()
                .takeIf { it.isNotBlank() }
                ?.let { URI(it).host }
            if (defaultHost != null) {
                require(URI(candidate).host == defaultHost) {
                    "正式版只允许访问固定更新源"
                }
            }
        }
        return candidate
    }

    private fun resolveCatalogUrl(manifestUrl: String, file: RemoteCatalogFile): String {
        val resolved = URI(manifestUrl).resolve(file.url).toString()
        validateHttps(resolved)
        return resolved
    }

    private fun preferredLocale(manifest: RemoteManifest): String {
        val language = Locale.getDefault().language.lowercase(Locale.US)
        return when {
            language.startsWith("zh") && manifest.files.containsKey("zhCN") -> "zhCN"
            manifest.files.containsKey("enUS") -> "enUS"
            else -> manifest.defaultLocale
        }
    }

    private fun validateHttps(url: String) {
        require(url.startsWith("https://")) {
            "远程更新地址必须使用 https://"
        }
    }

    private fun syncSupportFiles(
        context: Context,
        manifest: RemoteManifest,
        manifestUrl: String
    ): SupportSyncResult {
        var updated = false
        val warnings = mutableListOf<String>()

        manifest.supportFiles[CARD_RULES_RESOURCE_KEY]?.let { supportFile ->
            val result = syncSupportFile(
                cacheFile = cardRulesCacheFile(context),
                manifestUrl = manifestUrl,
                supportFile = supportFile,
                resourceLabel = "卡牌规则"
            )
            warnings += result.warnings
            if (result.raw != null) {
                cachedCardRules = decodeCardRules(result.raw)
                updated = updated || result.updated
            } else if (cachedCardRules == null) {
                cachedCardRules = runCatching {
                    decodeCardRules(cardRulesCacheFile(context).readText())
                }.getOrDefault(emptyMap())
            }
        }

        manifest.supportFiles[CARD_METADATA_RESOURCE_KEY]?.let { supportFile ->
            val result = syncSupportFile(
                cacheFile = cardMetadataCacheFile(context),
                manifestUrl = manifestUrl,
                supportFile = supportFile,
                resourceLabel = "战棋卡牌元数据"
            )
            warnings += result.warnings
            if (result.raw != null) {
                cachedCardMetadata = decodeCardMetadata(result.raw)
                updated = updated || result.updated
            } else if (cachedCardMetadata == null) {
                cachedCardMetadata = runCatching {
                    if (cardMetadataCacheFile(context).exists()) {
                        decodeCardMetadata(cardMetadataCacheFile(context).readText())
                    } else {
                        bundledCardMetadata(context)
                    }
                }.getOrElse {
                    bundledCardMetadata(context)
                }
            }
        }

        manifest.supportFiles[CARD_STATS_RESOURCE_KEY]?.let { supportFile ->
            val result = syncSupportFile(
                cacheFile = cardStatsCacheFile(context),
                manifestUrl = manifestUrl,
                supportFile = supportFile,
                resourceLabel = "卡牌统计"
            )
            warnings += result.warnings
            if (result.raw != null) {
                cachedCardStats = decodeCardStats(result.raw)
                updated = updated || result.updated
            } else if (cachedCardStats == null) {
                cachedCardStats = runCatching {
                    decodeCardStats(cardStatsCacheFile(context).readText())
                }.getOrDefault(BattlegroundCardStatsCatalog())
            }
        }

        manifest.supportFiles[HERO_STATS_RESOURCE_KEY]?.let { supportFile ->
            val result = syncSupportFile(
                cacheFile = heroStatsCacheFile(context),
                manifestUrl = manifestUrl,
                supportFile = supportFile,
                resourceLabel = "英雄统计"
            )
            warnings += result.warnings
            if (result.raw != null) {
                cachedHeroStats = decodeHeroStats(result.raw)
                updated = updated || result.updated
            } else if (cachedHeroStats == null) {
                cachedHeroStats = runCatching {
                    decodeHeroStats(heroStatsCacheFile(context).readText())
                }.getOrDefault(BattlegroundHeroStatsCatalog())
            }
        }

        return SupportSyncResult(
            updated = updated,
            warnings = warnings
        )
    }

    private fun syncSupportFile(
        cacheFile: File,
        manifestUrl: String,
        supportFile: RemoteCatalogFile,
        resourceLabel: String
    ): SupportFileSyncResult {
        val cacheText = cacheFile.takeIf(File::exists)?.readText()
        val existingHash = cacheText?.let(::sha256)

        if (existingHash == supportFile.sha256) {
            return SupportFileSyncResult()
        }

        val supportUrl = resolveCatalogUrl(manifestUrl, supportFile)
        return runCatching {
            val raw = downloadTextWithRetry(supportUrl)
            val actualHash = sha256(raw)
            require(actualHash == supportFile.sha256) {
                "远程 $resourceLabel 校验失败，哈希不匹配"
            }
            writeAtomically(cacheFile, raw)
            SupportFileSyncResult(
                raw = raw,
                updated = true
            )
        }.getOrElse { error ->
            val fallbackAction = if (cacheFile.exists()) "已保留本地缓存" else "本次跳过"
            SupportFileSyncResult(
                warnings = listOf(
                    "$resourceLabel 更新失败：${error.message ?: "未知错误"}；$fallbackAction"
                )
            )
        }
    }

    private fun downloadText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            requestMethod = "GET"
            doInput = true
        }

        return try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                throw IllegalStateException("远程更新失败: HTTP ${connection.responseCode} ($url)")
            }
            stream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadTextWithRetry(url: String, attempts: Int = 3): String {
        var lastError: Throwable? = null

        repeat(attempts) { attempt ->
            try {
                return downloadText(url)
            } catch (error: Throwable) {
                lastError = error
                if (attempt < attempts - 1) {
                    Thread.sleep(min(2_500L, 600L * (attempt + 1)))
                }
            }
        }

        throw IllegalStateException(
            "远程下载失败: ${lastError?.message ?: url}",
            lastError
        )
    }

    private fun writeAtomically(target: File, raw: String) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(raw)
        if (target.exists() && !target.delete()) {
            temp.delete()
            throw IllegalStateException("写入本地缓存失败")
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IllegalStateException("写入本地缓存失败")
        }
    }

    private fun sha256(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun hasRenderableMinionMetadata(catalog: StrategyCatalog): Boolean {
        val minions = catalog.comps.flatMap { it.keyMinions }
        if (minions.isEmpty()) return false

        val withImageMetadata = minions.count {
            !it.cardId.isNullOrBlank() || !it.imageUrl.isNullOrBlank()
        }
        val withStatusMetadata = minions.count { !it.statusRaw.isNullOrBlank() }

        return withImageMetadata > 0 && withStatusMetadata > 0
    }

    companion object {
        private const val ASSET_FILE = "strategies_zerotoheroes_zhCN.json"
        private const val CARD_METADATA_ASSET_FILE = "bgs_card_metadata.json"
        private const val HERO_NAME_INDEX_ASSET = "bgs_hero_name_index.json"
        private const val CACHE_FILE = "strategies_cache.json"
        private const val CARD_RULES_CACHE_FILE = "card_rules_cache.json"
        private const val CARD_METADATA_CACHE_FILE = "bgs_card_metadata_cache.json"
        private const val CARD_STATS_CACHE_FILE = "card_stats_cache.json"
        private const val HERO_STATS_CACHE_FILE = "hero_stats_cache.json"
        private const val CARD_RULES_RESOURCE_KEY = "cardRules"
        private const val CARD_METADATA_RESOURCE_KEY = "cardMetadata"
        private const val CARD_STATS_RESOURCE_KEY = "cardStats"
        private const val HERO_STATS_RESOURCE_KEY = "heroStats"
        private const val HERO_STATS_URL =
            "https://static.zerotoheroes.com/api/bgs/hero-stats/mmr-100/all-time/overview-from-hourly.gz.json"
    }

    private data class SupportSyncResult(
        val updated: Boolean = false,
        val warnings: List<String> = emptyList()
    )

    private data class SupportFileSyncResult(
        val raw: String? = null,
        val updated: Boolean = false,
        val warnings: List<String> = emptyList()
    )
}
