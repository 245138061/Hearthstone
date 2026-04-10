package com.bgtactician.app.data.local

import android.content.Context
import com.bgtactician.app.data.model.Tribe

data class DashboardPreferences(
    val selectedTribes: Set<Tribe>,
    val manifestUrlOverride: String
)

data class OverlayPosition(
    val x: Int = 24,
    val y: Int = 260
)

data class OverlaySettings(
    val interactionEnabled: Boolean = true,
    val bubbleOpacityPercent: Int = 72
)

class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("bgtactician_prefs", Context.MODE_PRIVATE)

    fun loadDashboardPreferences(): DashboardPreferences {
        val tribeSet = prefs.getStringSet(KEY_SELECTED_TRIBES, null)
            ?.mapNotNull(Tribe::fromWireName)
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_TRIBES

        return DashboardPreferences(
            selectedTribes = tribeSet,
            manifestUrlOverride = prefs.getString(KEY_MANIFEST_URL_OVERRIDE, "")?.trim().orEmpty()
        )
    }

    fun saveDashboardPreferences(
        selectedTribes: Set<Tribe>,
        manifestUrlOverride: String
    ) {
        prefs.edit()
            .putStringSet(KEY_SELECTED_TRIBES, selectedTribes.map(Tribe::wireName).toSet())
            .putString(KEY_MANIFEST_URL_OVERRIDE, manifestUrlOverride.trim())
            .apply()
    }

    fun loadOverlayPosition(): OverlayPosition {
        return OverlayPosition(
            x = prefs.getInt(KEY_OVERLAY_X, 24),
            y = prefs.getInt(KEY_OVERLAY_Y, 260)
        )
    }

    fun saveOverlayPosition(x: Int, y: Int) {
        prefs.edit()
            .putInt(KEY_OVERLAY_X, x)
            .putInt(KEY_OVERLAY_Y, y)
            .apply()
    }

    fun loadOverlaySettings(): OverlaySettings {
        return OverlaySettings(
            interactionEnabled = prefs.getBoolean(KEY_OVERLAY_INTERACTION_ENABLED, true),
            bubbleOpacityPercent = prefs.getInt(KEY_BUBBLE_OPACITY_PERCENT, 72)
                .coerceIn(35, 100)
        )
    }

    fun saveOverlaySettings(interactionEnabled: Boolean, bubbleOpacityPercent: Int) {
        prefs.edit()
            .putBoolean(KEY_OVERLAY_INTERACTION_ENABLED, interactionEnabled)
            .putInt(KEY_BUBBLE_OPACITY_PERCENT, bubbleOpacityPercent.coerceIn(35, 100))
            .apply()
    }

    fun saveLastSync(timestampMillis: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC, timestampMillis).apply()
    }

    fun loadLastSync(): Long? {
        val value = prefs.getLong(KEY_LAST_SYNC, -1L)
        return value.takeIf { it > 0L }
    }

    fun saveLastManifestVersion(version: String) {
        prefs.edit().putString(KEY_LAST_MANIFEST_VERSION, version).apply()
    }

    fun loadLastManifestVersion(): String? = prefs.getString(KEY_LAST_MANIFEST_VERSION, null)?.trim()

    fun saveLastManifestUpdatedAt(value: String) {
        prefs.edit().putString(KEY_LAST_MANIFEST_UPDATED_AT, value).apply()
    }

    fun loadLastManifestUpdatedAt(): String? = prefs.getString(KEY_LAST_MANIFEST_UPDATED_AT, null)?.trim()

    fun saveLastCatalogHash(hash: String) {
        prefs.edit().putString(KEY_LAST_CATALOG_HASH, hash).apply()
    }

    fun loadLastCatalogHash(): String? = prefs.getString(KEY_LAST_CATALOG_HASH, null)?.trim()

    companion object {
        private val DEFAULT_TRIBES = setOf(
            Tribe.MECH,
            Tribe.DEMON,
            Tribe.UNDEAD,
            Tribe.PIRATE,
            Tribe.ELEMENTAL
        )

        private const val KEY_SELECTED_TRIBES = "selected_tribes"
        private const val KEY_MANIFEST_URL_OVERRIDE = "manifest_url_override"
        private const val KEY_OVERLAY_X = "overlay_x"
        private const val KEY_OVERLAY_Y = "overlay_y"
        private const val KEY_OVERLAY_INTERACTION_ENABLED = "overlay_interaction_enabled"
        private const val KEY_BUBBLE_OPACITY_PERCENT = "bubble_opacity_percent"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_LAST_MANIFEST_VERSION = "last_manifest_version"
        private const val KEY_LAST_MANIFEST_UPDATED_AT = "last_manifest_updated_at"
        private const val KEY_LAST_CATALOG_HASH = "last_catalog_hash"
    }
}
