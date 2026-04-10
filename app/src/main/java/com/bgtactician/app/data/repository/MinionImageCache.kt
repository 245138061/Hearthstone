package com.bgtactician.app.data.repository

import android.content.Context
import com.bgtactician.app.data.model.KeyMinion
import com.bgtactician.app.data.model.StrategyCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object MinionImageCache {

    private const val CACHE_DIR = "minion_image_cache"
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightKeys = ConcurrentHashMap.newKeySet<String>()

    fun schedulePrefetch(context: Context, catalog: StrategyCatalog) {
        val appContext = context.applicationContext
        prefetchScope.launch {
            prefetchCatalog(appContext, catalog)
        }
    }

    fun resolveModels(context: Context, minion: KeyMinion): List<Any> {
        val localModel = localModel(context, minion)

        return buildList {
            localModel?.let(::add)
            remoteModels(minion).forEach(::add)
            minion.imageAsset
                ?.takeIf { it.isNotBlank() }
                ?.let { add("file:///android_asset/$it") }
        }.distinct()
    }

    suspend fun ensureCached(context: Context, minion: KeyMinion): File? {
        val appContext = context.applicationContext
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            cacheImage(appContext, minion)
            localModel(appContext, minion)
        }
    }

    fun localModel(context: Context, minion: KeyMinion): File? {
        return cachedFile(context, minion)
            ?.takeIf { it.exists() && it.length() > 0L }
    }

    private fun prefetchCatalog(context: Context, catalog: StrategyCatalog) {
        catalog.comps
            .asSequence()
            .flatMap { it.keyMinions.asSequence() }
            .distinctBy { cacheKey(it) }
            .forEach { minion ->
                cacheImage(context, minion)
            }
    }

    private fun cacheImage(context: Context, minion: KeyMinion) {
        val cacheKey = cacheKey(minion) ?: return
        val target = cachedFile(context, minion) ?: return
        if (target.exists() && target.length() > 0L) {
            return
        }
        if (!inFlightKeys.add(cacheKey)) {
            return
        }

        try {
            val temp = File(target.parentFile, "${target.name}.tmp")
            val downloaded = remoteModels(minion).any { remoteUrl ->
                temp.delete()
                runCatching { downloadToFile(remoteUrl, temp) }.isSuccess && temp.length() > 0L
            }
            if (!downloaded) {
                temp.delete()
                return
            }
            if (target.exists()) {
                target.delete()
            }
            if (!temp.renameTo(target)) {
                temp.delete()
            }
        } catch (_: Exception) {
            // Keep remote fallback available; a failed prefetch should never break rendering.
        } finally {
            inFlightKeys.remove(cacheKey)
        }
    }

    private fun cachedFile(context: Context, minion: KeyMinion): File? {
        val key = cacheKey(minion) ?: return null
        val dir = File(context.filesDir, CACHE_DIR).apply { mkdirs() }
        val extension = imageExtension(minion)
        return File(dir, "$key.$extension")
    }

    private fun cacheKey(minion: KeyMinion): String? {
        val cardId = minion.cardId?.trim().orEmpty()
        if (cardId.isNotBlank()) {
            return "card_${sanitize(cardId)}"
        }
        val imageUrl = minion.imageUrl?.trim().orEmpty()
        if (imageUrl.isNotBlank()) {
            return "url_${sha256(imageUrl)}"
        }
        return null
    }

    private fun remoteModels(minion: KeyMinion): List<String> {
        val cardId = minion.cardId?.takeIf { it.isNotBlank() }
        return buildList {
            cardId?.let { add("https://art.hearthstonejson.com/v1/256x/$it.jpg") }
            minion.imageUrl?.takeIf { it.isNotBlank() }?.let(::add)
            cardId?.let { add("https://art.hearthstonejson.com/v1/render/latest/enUS/256x/$it.png") }
        }.distinct()
    }

    private fun imageExtension(minion: KeyMinion): String {
        val normalized = buildList {
            minion.imageUrl?.trim()?.let(::add)
            minion.imageAsset?.trim()?.let(::add)
        }
            .firstOrNull { !it.isNullOrBlank() }
            ?.lowercase()
            .orEmpty()

        return when {
            normalized.endsWith(".png") -> "png"
            normalized.endsWith(".svg") -> "svg"
            else -> "jpg"
        }
    }

    private fun downloadToFile(url: String, target: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            doInput = true
        }

        try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                return
            }
            target.parentFile?.mkdirs()
            stream.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sanitize(value: String): String {
        return value.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
