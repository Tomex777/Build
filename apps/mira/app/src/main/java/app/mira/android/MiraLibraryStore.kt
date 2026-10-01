package app.mira.android

import android.content.Context
import app.mira.domain.ContentKind
import app.mira.domain.ContentRef
import app.mira.domain.ContentSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class MiraLibraryStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableItems = MutableStateFlow(load())
    val items: StateFlow<List<ContentSearchResult>> = mutableItems.asStateFlow()

    fun contains(ref: ContentRef): Boolean =
        mutableItems.value.any { it.ref == ref }

    fun add(item: ContentSearchResult) {
        val updated = (mutableItems.value.filterNot { it.ref == item.ref } + item)
            .sortedBy { it.title.lowercase() }
        mutableItems.value = updated
        persist(updated)
    }

    fun remove(ref: ContentRef) {
        val updated = mutableItems.value.filterNot { it.ref == ref }
        mutableItems.value = updated
        persist(updated)
    }

    fun toggle(item: ContentSearchResult) {
        if (contains(item.ref)) remove(item.ref) else add(item)
    }

    private fun load(): List<ContentSearchResult> {
        val raw = preferences.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.optJSONObject(index) ?: continue
                    val kind = runCatching {
                        ContentKind.valueOf(json.getString("kind"))
                    }.getOrNull() ?: continue
                    val sourceId = json.optString("sourceId").trim()
                    val contentId = json.optString("contentId").trim()
                    val title = json.optString("title").trim()
                    if (sourceId.isBlank() || contentId.isBlank() || title.isBlank()) continue
                    add(
                        ContentSearchResult(
                            ref = ContentRef(sourceId, contentId, kind),
                            title = title,
                            posterUrl = json.optNullableString("posterUrl"),
                            year = if (json.has("year") && !json.isNull("year")) json.optInt("year") else null,
                            description = json.optNullableString("description"),
                            sourceState = json.optNullableString("sourceState"),
                        ),
                    )
                }
            }.sortedBy { it.title.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun persist(items: List<ContentSearchResult>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("sourceId", item.ref.sourceId)
                    put("contentId", item.ref.sourceContentId)
                    put("kind", item.ref.kind.name)
                    put("title", item.title)
                    put("posterUrl", item.posterUrl)
                    put("year", item.year)
                    put("description", item.description)
                    put("sourceState", item.sourceState)
                },
            )
        }
        preferences.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    companion object {
        private const val PREFERENCES_NAME = "mira_library"
        private const val KEY_ITEMS = "items"
    }
}
