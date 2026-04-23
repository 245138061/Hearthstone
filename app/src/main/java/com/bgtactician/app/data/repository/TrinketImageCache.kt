package com.bgtactician.app.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object TrinketImageCache {

    private const val CACHE_DIR = "trinket_image_cache"
    private val inFlightKeys = ConcurrentHashMap.newKeySet<String>()

    suspend fun ensureCached(context: Context, cardId: String): File? {
        val appContext = context.applicationContext
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            cacheImage(appContext, cardId)
            localModel(appContext, cardId)
        }
    }

    fun localModel(context: Context, cardId: String): File? {
        return cachedFile(context, cardId)
            ?.takeIf { it.exists() && it.length() > 0L }
    }

    private fun cacheImage(context: Context, cardId: String) {
        val normalizedCardId = cardId.trim()
        if (normalizedCardId.isBlank()) {
            return
        }
        val target = cachedFile(context, normalizedCardId) ?: return
        if (target.exists() && target.length() > 0L) {
            return
        }
        if (!inFlightKeys.add(normalizedCardId)) {
            return
        }

        try {
            val temp = File(target.parentFile, "${target.name}.tmp")
            val downloaded = remoteModels(normalizedCardId).any { remoteUrl ->
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
            // 饰品图缓存失败时保留远程回退，不能影响战术页渲染。
        } finally {
            inFlightKeys.remove(normalizedCardId)
        }
    }

    private fun cachedFile(context: Context, cardId: String): File? {
        val normalizedCardId = cardId.trim().takeIf { it.isNotBlank() } ?: return null
        val dir = File(context.filesDir, CACHE_DIR).apply { mkdirs() }
        return File(dir, "${sanitize(normalizedCardId)}.jpg")
    }

    private fun remoteModels(cardId: String): List<String> {
        return listOf(
            "https://static.zerotoheroes.com/hearthstone/cardart/256x/$cardId.jpg",
            "https://static.zerotoheroes.com/hearthstone/cardart/256x/$cardId",
        ).distinct()
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
}
