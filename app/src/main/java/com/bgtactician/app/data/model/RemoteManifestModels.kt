package com.bgtactician.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteManifest(
    @SerialName("manifest_format")
    val manifestFormat: String,
    @SerialName("schema_version")
    val schemaVersion: Int,
    val channel: String = "stable",
    val version: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("default_locale")
    val defaultLocale: String = "zhCN",
    val files: Map<String, RemoteCatalogFile>,
    @SerialName("support_files")
    val supportFiles: Map<String, RemoteCatalogFile> = emptyMap()
)

@Serializable
data class RemoteCatalogFile(
    val path: String,
    val url: String,
    @SerialName("catalog_version")
    val catalogVersion: String,
    val sha256: String,
    @SerialName("size_bytes")
    val sizeBytes: Long = 0L
)

data class CatalogRefreshResult(
    val snapshot: CatalogSnapshot,
    val wasUpdated: Boolean,
    val manifestVersion: String,
    val manifestUpdatedAt: String,
    val sourceUrl: String,
    val warnings: List<String> = emptyList()
)
