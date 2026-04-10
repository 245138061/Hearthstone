package com.bgtactician.app.data.repository

import android.content.Context
import com.bgtactician.app.BuildConfig
import com.bgtactician.app.data.local.AppPreferences
import com.bgtactician.app.data.model.CardRuleEntry
import com.bgtactician.app.data.model.CardRulesCatalog
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

class StrategyRepository {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private var cachedSnapshot: CatalogSnapshot? = null
    private var cachedCardRules: CardRulesCatalog? = null

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

    suspend fun refreshCatalog(
        context: Context,
        manifestUrlOverride: String = ""
    ): CatalogRefreshResult {
        return withContext(Dispatchers.IO) {
            val preferences = AppPreferences(context)
            val manifestUrl = resolveManifestUrl(manifestUrlOverride)
            val manifestRaw = downloadText(manifestUrl)
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
                val raw = downloadText(catalogUrl)
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
            val supportUpdated = syncSupportFiles(
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
                wasUpdated = catalogUpdated || supportUpdated,
                manifestVersion = manifest.version,
                manifestUpdatedAt = manifest.updatedAt,
                sourceUrl = catalogUrl
            )
        }
    }

    private fun cacheFile(context: Context): File = File(context.filesDir, CACHE_FILE)

    private fun cardRulesCacheFile(context: Context): File = File(context.filesDir, CARD_RULES_CACHE_FILE)

    private fun bundledSnapshot(context: Context): CatalogSnapshot {
        return CatalogSnapshot(
            catalog = decode(context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }),
            source = StrategyDataSource.ASSET
        )
    }

    private fun decode(raw: String): StrategyCatalog = json.decodeFromString<StrategyCatalog>(raw)

    private fun decodeCardRules(raw: String): CardRulesCatalog = json.decodeFromString<Map<String, CardRuleEntry>>(raw)

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
    ): Boolean {
        val supportFile = manifest.supportFiles[CARD_RULES_RESOURCE_KEY] ?: return false
        val cacheFile = cardRulesCacheFile(context)
        val cacheText = cacheFile.takeIf(File::exists)?.readText()
        val existingHash = cacheText?.let(::sha256)

        if (existingHash == supportFile.sha256) {
            if (cachedCardRules == null) {
                cachedCardRules = runCatching { decodeCardRules(cacheText) }.getOrDefault(emptyMap())
            }
            return false
        }

        val raw = downloadText(resolveCatalogUrl(manifestUrl, supportFile))
        val actualHash = sha256(raw)
        require(actualHash == supportFile.sha256) {
            "远程 card rules 校验失败，哈希不匹配"
        }
        writeAtomically(cacheFile, raw)
        cachedCardRules = decodeCardRules(raw)
        return true
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
                throw IllegalStateException("远程更新失败: HTTP ${connection.responseCode}")
            }
            stream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
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
        private const val CACHE_FILE = "strategies_cache.json"
        private const val CARD_RULES_CACHE_FILE = "card_rules_cache.json"
        private const val CARD_RULES_RESOURCE_KEY = "cardRules"
    }
}
