package com.night.sora.extension

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.night.sora.catalog.JikanCatalogClient
import com.night.sora.catalog.JikanCatalogService
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.extension.api.ExtensionDescriptor
import com.night.sora.extension.api.ExtensionPermission
import com.night.sora.extension.api.SourceDescriptor
import com.night.sora.extension.api.extensionDescriptorFromJson
import com.night.sora.extension.api.toJson
import com.night.sora.model.ContentType
import org.json.JSONArray
import org.json.JSONObject

data class InstalledExtension(
    val packageName: String,
    val component: ComponentName,
    val declaredId: String,
    val declaredName: String,
    val apiVersion: Int,
    val descriptor: ExtensionDescriptor? = null,
    val error: String? = null,
)

class ExtensionManager(private val context: Context) {
    private val jikanClient = JikanCatalogClient()

    private val jikanDescriptor = ExtensionDescriptor(
        id = JIKAN_ID,
        name = "Sora Anime & Manga Catalog",
        version = "1.0.0",
        apiVersion = ExtensionContract.API_VERSION,
        author = "Sora",
        description = "Built-in Anime and Manga metadata powered by Jikan.",
        contentTypes = setOf("anime", "manga"),
        capabilities = setOf("builtin", "catalog", "browse", "search", "details"),
        permissions = listOf(ExtensionPermission("network", listOf("api.jikan.moe"))),
        sources = listOf(
            SourceDescriptor(
                id = JikanCatalogClient.ANIME_SOURCE,
                name = "Jikan Anime",
                contentTypes = setOf("anime"),
                capabilities = setOf("browse", "search", "details"),
            ),
            SourceDescriptor(
                id = JikanCatalogClient.MANGA_SOURCE,
                name = "Jikan Manga",
                contentTypes = setOf("manga"),
                capabilities = setOf("browse", "search", "details"),
            ),
        ),
    )

    fun discover(callback: (List<InstalledExtension>) -> Unit) {
        val pm = context.packageManager
        val intent = Intent(ExtensionContract.ACTION_BIND_EXTENSION)
        val builtInJikan = InstalledExtension(
            packageName = context.packageName,
            component = ComponentName(context, JikanCatalogService::class.java),
            declaredId = JIKAN_ID,
            declaredName = jikanDescriptor.name,
            apiVersion = ExtensionContract.API_VERSION,
            descriptor = jikanDescriptor,
        )

        val external = pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
            .mapNotNull { match ->
                val info = match.serviceInfo
                val component = ComponentName(info.packageName, info.name)
                if (component == builtInJikan.component) return@mapNotNull null

                val metadata = info.metaData
                InstalledExtension(
                    packageName = info.packageName,
                    component = component,
                    declaredId = metadata?.getString(ExtensionContract.META_EXTENSION_ID).orEmpty(),
                    declaredName = metadata?.getString(ExtensionContract.META_EXTENSION_NAME)
                        ?: info.loadLabel(pm).toString(),
                    apiVersion = metadata?.getInt(ExtensionContract.META_API_VERSION, -1) ?: -1,
                )
            }

        if (external.isEmpty()) {
            callback(listOf(builtInJikan))
            return
        }

        val found = mutableListOf(builtInJikan)
        var remaining = external.size

        fun finishOne(extension: InstalledExtension) {
            found += extension
            remaining--
            if (remaining == 0) {
                callback(
                    found.sortedWith(
                        compareBy<InstalledExtension> { it.packageName.contains(".demo") }
                            .thenBy { it.declaredName.lowercase() }
                    )
                )
            }
        }

        external.forEach { base ->
            if (base.apiVersion != ExtensionContract.API_VERSION) {
                finishOne(base.copy(error = "Unsupported API v${base.apiVersion}"))
            } else {
                ExtensionClient(context, base.component).call(ExtensionContract.Method.MANIFEST) { result ->
                    result.fold(
                        onSuccess = { raw ->
                            runCatching { extensionDescriptorFromJson(raw) }
                                .onSuccess { finishOne(base.copy(descriptor = it)) }
                                .onFailure { finishOne(base.copy(error = "Invalid manifest: ${it.message}")) }
                        },
                        onFailure = { finishOne(base.copy(error = it.message ?: "Connection failed")) },
                    )
                }
            }
        }
    }

    fun call(extension: InstalledExtension, method: String, payloadJson: String, callback: (Result<String>) -> Unit) {
        if (isBuiltInJikan(extension)) {
            callBuiltInJikan(method, payloadJson, callback)
        } else {
            ExtensionClient(context, extension.component).call(method, payloadJson, callback)
        }
    }

    private fun isBuiltInJikan(extension: InstalledExtension): Boolean =
        extension.packageName == context.packageName && extension.declaredId == JIKAN_ID

    private fun callBuiltInJikan(method: String, payloadJson: String, callback: (Result<String>) -> Unit) {
        val payload = runCatching { JSONObject(payloadJson.ifBlank { "{}" }) }
            .getOrElse {
                callback(Result.failure(IllegalArgumentException("Invalid Jikan payload", it)))
                return
            }
        val type = jikanType(payload)

        when (method) {
            ExtensionContract.Method.MANIFEST -> callback(Result.success(jikanDescriptor.toJson()))
            ExtensionContract.Method.BROWSE -> {
                if (type == null) return callback(Result.failure(IllegalArgumentException("Jikan supports Anime and Manga only")))
                jikanClient.browse(type) { result -> callback(result.map(::catalogItemsJson)) }
            }
            ExtensionContract.Method.SEARCH -> {
                if (type == null) return callback(Result.failure(IllegalArgumentException("Jikan supports Anime and Manga only")))
                jikanClient.search(type, payload.optString("query")) { result -> callback(result.map(::catalogItemsJson)) }
            }
            ExtensionContract.Method.DETAILS -> {
                if (type == null) return callback(Result.failure(IllegalArgumentException("Unknown Jikan source")))
                jikanClient.details(type, payload.optString("id")) { result ->
                    callback(
                        result.map { details ->
                            JSONObject().apply {
                                put("id", details.id)
                                put("title", details.title)
                                put("subtitle", details.subtitle)
                                put("description", details.synopsis)
                                put("artworkUrl", details.artworkUrl ?: JSONObject.NULL)
                                put("score", details.score ?: JSONObject.NULL)
                                put("status", details.status)
                                put("genres", JSONArray(details.genres))
                            }.toString()
                        }
                    )
                }
            }
            ExtensionContract.Method.EPISODES,
            ExtensionContract.Method.CHAPTERS -> callback(Result.success("[]"))
            else -> callback(Result.failure(UnsupportedOperationException("Built-in Jikan is metadata-only")))
        }
    }

    private fun catalogItemsJson(items: List<JikanCatalogClient.CatalogItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("subtitle", item.subtitle)
                put("artworkUrl", item.artworkUrl ?: JSONObject.NULL)
            })
        }
        return array.toString()
    }

    private fun jikanType(payload: JSONObject): ContentType? {
        return when (payload.optString("type").lowercase()) {
            "anime" -> ContentType.ANIME
            "manga" -> ContentType.MANGA
            else -> when (payload.optString("sourceId")) {
                JikanCatalogClient.ANIME_SOURCE -> ContentType.ANIME
                JikanCatalogClient.MANGA_SOURCE -> ContentType.MANGA
                else -> null
            }
        }
    }

    companion object {
        private const val JIKAN_ID = "sora.core.jikan"
    }
}
