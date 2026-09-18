package com.night.sora.extension

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.night.sora.catalog.AniListCatalogClient
import com.night.sora.catalog.AniListCatalogService
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.extension.api.ExtensionDescriptor
import com.night.sora.extension.api.ExtensionPermission
import com.night.sora.extension.api.SourceDescriptor
import com.night.sora.extension.api.extensionDescriptorFromJson
import com.night.sora.extension.api.toJson
import com.night.sora.model.ContentType
import com.night.sora.model.ExtensionMediaSelection
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
    private val aniListClient = AniListCatalogClient()

    private val aniListDescriptor = ExtensionDescriptor(
        id = ANILIST_ID,
        name = "Sora Anime & Manga Catalog",
        version = "2.0.0",
        apiVersion = ExtensionContract.API_VERSION,
        author = "Sora",
        description = "Built-in Anime and Manga discovery powered by AniList.",
        contentTypes = setOf("anime", "manga"),
        capabilities = setOf("builtin", "catalog", "browse", "search", "details"),
        permissions = listOf(ExtensionPermission("network", listOf("graphql.anilist.co"))),
        sources = listOf(
            SourceDescriptor(
                id = AniListCatalogClient.ANIME_SOURCE,
                name = "AniList Anime",
                contentTypes = setOf("anime"),
                capabilities = setOf("browse", "search", "details"),
            ),
            SourceDescriptor(
                id = AniListCatalogClient.MANGA_SOURCE,
                name = "AniList Manga",
                contentTypes = setOf("manga"),
                capabilities = setOf("browse", "search", "details"),
            ),
        ),
    )

    fun discover(callback: (List<InstalledExtension>) -> Unit) {
        val pm = context.packageManager
        val intent = Intent(ExtensionContract.ACTION_BIND_EXTENSION)
        val builtInAniList = InstalledExtension(
            packageName = context.packageName,
            component = ComponentName(context, AniListCatalogService::class.java),
            declaredId = ANILIST_ID,
            declaredName = aniListDescriptor.name,
            apiVersion = ExtensionContract.API_VERSION,
            descriptor = aniListDescriptor,
        )

        val external = pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
            .mapNotNull { match ->
                val info = match.serviceInfo
                val component = ComponentName(info.packageName, info.name)
                if (component == builtInAniList.component) return@mapNotNull null

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
            callback(listOf(builtInAniList))
            return
        }

        val found = mutableListOf(builtInAniList)
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
        if (isBuiltInAniList(extension)) {
            callBuiltInAniList(method, payloadJson, callback)
        } else {
            ExtensionClient(context, extension.component).call(method, payloadJson, callback)
        }
    }

    /**
     * Resolve only AniList's explicit Source/Adaptation relationship. Legacy
     * Jikan-backed library entries are accepted by MAL id so the catalog migration does not
     * strand previously saved Anime/Manga.
     */
    fun findBuiltInAniListCounterpart(
        selection: ExtensionMediaSelection,
        callback: (Result<ExtensionMediaSelection?>) -> Unit,
    ): Boolean {
        val catalogSelection = selection.extensionPackage == context.packageName &&
            (AniListCatalogClient.isAniListSource(selection.sourceId) ||
                AniListCatalogClient.isLegacyJikanSource(selection.sourceId)) &&
            (selection.type == ContentType.ANIME || selection.type == ContentType.MANGA)
        if (!catalogSelection) return false

        val handle: (Result<AniListCatalogClient.CatalogItem?>) -> Unit = { result ->
            callback(
                result.map { item ->
                    item?.let {
                        ExtensionMediaSelection(
                            id = it.id,
                            sourceId = AniListCatalogClient.sourceFor(it.type),
                            extensionPackage = context.packageName,
                            type = it.type,
                            title = it.title,
                            subtitle = it.subtitle,
                            artworkUrl = it.artworkUrl,
                        )
                    }
                }
            )
        }

        if (AniListCatalogClient.isLegacyJikanSource(selection.sourceId)) {
            aniListClient.counterpartByMalId(selection.type, selection.id, handle)
        } else {
            aniListClient.counterpart(selection.type, selection.id, handle)
        }
        return true
    }

    private fun isBuiltInAniList(extension: InstalledExtension): Boolean =
        extension.packageName == context.packageName && extension.declaredId == ANILIST_ID

    private fun callBuiltInAniList(method: String, payloadJson: String, callback: (Result<String>) -> Unit) {
        val payload = runCatching { JSONObject(payloadJson.ifBlank { "{}" }) }
            .getOrElse {
                callback(Result.failure(IllegalArgumentException("Invalid AniList payload", it)))
                return
            }
        val type = aniListType(payload)

        when (method) {
            ExtensionContract.Method.MANIFEST -> callback(Result.success(aniListDescriptor.toJson()))
            ExtensionContract.Method.BROWSE -> {
                if (type == null) return callback(Result.failure(IllegalArgumentException("AniList supports Anime and Manga only")))
                aniListClient.browse(type, payload.optString("feed")) { result -> callback(result.map(::catalogItemsJson)) }
            }
            ExtensionContract.Method.SEARCH -> {
                if (type == null) return callback(Result.failure(IllegalArgumentException("AniList supports Anime and Manga only")))
                aniListClient.search(type, payload.optString("query")) { result -> callback(result.map(::catalogItemsJson)) }
            }
            ExtensionContract.Method.DETAILS -> {
                if (type == null) return callback(Result.failure(IllegalArgumentException("Unknown AniList source")))
                val onResult: (Result<AniListCatalogClient.CatalogDetails>) -> Unit = { result ->
                    callback(result.map(::detailsJson))
                }
                if (AniListCatalogClient.isLegacyJikanSource(payload.optString("sourceId"))) {
                    aniListClient.detailsByMalId(type, payload.optString("id"), onResult)
                } else {
                    aniListClient.details(type, payload.optString("id"), onResult)
                }
            }
            ExtensionContract.Method.EPISODES,
            ExtensionContract.Method.CHAPTERS -> callback(Result.success("[]"))
            else -> callback(Result.failure(UnsupportedOperationException("Built-in AniList is metadata-only")))
        }
    }

    private fun catalogItemsJson(items: List<AniListCatalogClient.CatalogItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("malId", item.malId ?: JSONObject.NULL)
                put("title", item.title)
                put("englishTitle", item.englishTitle)
                put("romajiTitle", item.romajiTitle)
                put("aliases", JSONArray(item.aliases))
                put("subtitle", item.subtitle)
                put("artworkUrl", item.artworkUrl ?: JSONObject.NULL)
            })
        }
        return array.toString()
    }

    private fun detailsJson(details: AniListCatalogClient.CatalogDetails): String = JSONObject().apply {
        put("id", details.id)
        put("malId", details.malId ?: JSONObject.NULL)
        put("title", details.title)
        put("alternateTitle", details.alternateTitle)
        put("aliases", JSONArray(details.aliases))
        put("subtitle", details.subtitle)
        put("description", details.synopsis)
        put("artworkUrl", details.artworkUrl ?: JSONObject.NULL)
        put("score", details.score ?: JSONObject.NULL)
        put("status", details.status)
        put("genres", JSONArray(details.genres))
        put("year", details.year ?: JSONObject.NULL)
        put("season", details.season)
        put("episodes", details.episodes ?: JSONObject.NULL)
        put("chapters", details.chapters ?: JSONObject.NULL)
        put("volumes", details.volumes ?: JSONObject.NULL)
    }.toString()

    private fun aniListType(payload: JSONObject): ContentType? {
        return when (payload.optString("type").lowercase()) {
            "anime" -> ContentType.ANIME
            "manga" -> ContentType.MANGA
            else -> when (payload.optString("sourceId")) {
                AniListCatalogClient.ANIME_SOURCE,
                AniListCatalogClient.LEGACY_JIKAN_ANIME_SOURCE -> ContentType.ANIME
                AniListCatalogClient.MANGA_SOURCE,
                AniListCatalogClient.LEGACY_JIKAN_MANGA_SOURCE -> ContentType.MANGA
                else -> null
            }
        }
    }

    companion object {
        private const val ANILIST_ID = "sora.core.anilist"
    }
}
