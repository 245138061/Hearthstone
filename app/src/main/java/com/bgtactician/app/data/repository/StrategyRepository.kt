package com.bgtactician.app.data.repository

import android.content.Context
import com.bgtactician.app.BuildConfig
import com.bgtactician.app.data.local.AppPreferences
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

    suspend fun loadCatalog(context: Context, ignoreMemoryCache: Boolean = false): CatalogSnapshot {
        if (!ignoreMemoryCache) {
            cachedSnapshot?.let { return it }
        }

        return withContext(Dispatchers.IO) {
            val preferences = AppPreferences(context)
            val cacheFile = cacheFile(context)

            val cached = if (cacheFile.exists()) {
                runCatching {
                    CatalogSnapshot(
                        catalog = decode(cacheFile.readText()),
                        source = StrategyDataSource.CACHE,
                        lastSyncAt = preferences.loadLastSync() ?: cacheFile.lastModified().takeIf { it > 0L }
                    )
                }.getOrNull()
            } else {
                null
            }

            val snapshot = cached ?: run {
                CatalogSnapshot(
                    catalog = decode(context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }),
                    source = StrategyDataSource.ASSET
                )
            }

            snapshot.also { cachedSnapshot = it }
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

            val snapshot = if (cacheText != null && existingHash == selectedFile.sha256) {
                CatalogSnapshot(
                    catalog = decode(cacheText),
                    source = StrategyDataSource.CACHE,
                    lastSyncAt = syncedAt
                )
            } else {
                val raw = downloadText(catalogUrl)
                val actualHash = sha256(raw)
                require(actualHash == selectedFile.sha256) {
                    "远程数据校验失败，哈希不匹配"
                }
                val catalog = decode(raw)
                writeAtomically(cacheFile, raw)
                preferences.saveLastCatalogHash(actualHash)
                CatalogSnapshot(
                    catalog = catalog,
                    source = StrategyDataSource.REMOTE,
                    lastSyncAt = syncedAt
                )
            }

            preferences.saveLastSync(syncedAt)
            preferences.saveLastManifestVersion(manifest.version)
            preferences.saveLastManifestUpdatedAt(manifest.updatedAt)

            CatalogRefreshResult(
                snapshot = snapshot.also { cachedSnapshot = it },
                wasUpdated = existingHash != selectedFile.sha256,
                manifestVersion = manifest.version,
                manifestUpdatedAt = manifest.updatedAt,
                sourceUrl = catalogUrl
            )
        }
    }

    private fun cacheFile(context: Context): File = File(context.filesDir, CACHE_FILE)

    private fun decode(raw: String): StrategyCatalog = json.decodeFromString<StrategyCatalog>(raw)

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

    companion object {
        private const val ASSET_FILE = "strategies_zerotoheroes_zhCN.json"
        private const val CACHE_FILE = "strategies_cache.json"
    }
}
