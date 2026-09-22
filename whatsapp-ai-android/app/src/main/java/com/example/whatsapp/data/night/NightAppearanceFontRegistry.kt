package com.example.whatsapp.data.night

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class NightAppearanceFont(
    val key: String,
    val label: String,
    val family: FontFamily,
)

/** Shared catalog for fonts that can safely be selected by the UI and appearance action. */
object NightAppearanceFontRegistry {
    private val builtIns = listOf(
        NightAppearanceFont("system", "System", FontFamily.Default),
        NightAppearanceFont("serif", "Serif", FontFamily.Serif),
        NightAppearanceFont("monospace", "Monospace", FontFamily.Monospace),
        NightAppearanceFont("cursive", "Cursive", FontFamily.Cursive),
    )
    @Volatile private var added: List<NightAppearanceFont> = emptyList()
    private const val PREFS = "night_appearance_fonts"
    private const val CATALOG = "catalog"

    fun available(): List<NightAppearanceFont> = builtIns + added

    fun find(key: String): NightAppearanceFont? = available().firstOrNull { it.key == key }

    /** Register a font after the application has loaded it from a trusted bundled/user-installed source. */
    @Synchronized
    fun register(font: NightAppearanceFont): Boolean {
        if (!font.key.matches(Regex("[A-Za-z0-9._-]{1,80}"))) return false
        if (builtIns.any { it.key == font.key }) return false
        added = (added.filterNot { it.key == font.key } + font)
        return true
    }

    fun load(context: Context) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CATALOG, "[]") ?: "[]"
        val restored = runCatching {
            val values = JSONArray(raw)
            buildList {
                for (index in 0 until values.length()) {
                    val value = values.optJSONObject(index) ?: continue
                    val key = value.optString("key")
                    val label = value.optString("label")
                    val path = value.optString("path")
                    val file = File(path)
                    if (!file.isFile || key.isBlank() || label.isBlank()) continue
                    val family = runCatching {
                        FontFamily(Typeface.createFromFile(file))
                    }.getOrNull() ?: continue
                    add(NightAppearanceFont(key, label, family))
                }
            }
        }.getOrDefault(emptyList())
        added = (added.filterNot { it.key.startsWith("user.") } + restored)
            .distinctBy { it.key }
    }

    fun addFromUri(context: Context, uri: Uri, requestedLabel: String? = null): NightAppearanceFont {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            .orEmpty()
        val extension = displayName.substringAfterLast('.', "").lowercase()
        require(extension in setOf("ttf", "otf")) { "Choose a .ttf or .otf font file." }
        val directory = File(context.filesDir, "night_fonts").apply { mkdirs() }
        val id = java.util.UUID.randomUUID().toString()
        val file = File(directory, "$id.$extension")
        try {
            resolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= 12L * 1024L * 1024L) {
                            "Font files must be smaller than 12 MB."
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: error("Night could not read that font file.")
            require(file.length() > 0L) { "That font file is empty." }
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
        val typeface = runCatching { Typeface.createFromFile(file) }
            .getOrElse {
                file.delete()
                error("That font file could not be loaded by Android.")
            }
        val label = requestedLabel?.trim()?.takeIf { it.isNotBlank() }
            ?: displayName.substringBeforeLast('.').take(48)
        val font = NightAppearanceFont("user.$id", label, FontFamily(typeface))
        added = added + font
        persist(context, file.absolutePath, font)
        return font
    }

    private fun persist(context: Context, path: String, font: NightAppearanceFont) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val values = runCatching { JSONArray(prefs.getString(CATALOG, "[]") ?: "[]") }
            .getOrElse { JSONArray() }
        values.put(
            JSONObject()
                .put("key", font.key)
                .put("label", font.label)
                .put("path", path)
        )
        prefs.edit().putString(CATALOG, values.toString()).apply()
    }

    @Synchronized
    fun unregister(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = runCatching { JSONArray(prefs.getString(CATALOG, "[]") ?: "[]") }
            .getOrElse { JSONArray() }
        val kept = JSONArray()
        for (index in 0 until old.length()) {
            val value = old.optJSONObject(index) ?: continue
            if (value.optString("key") == key) {
                File(value.optString("path")).delete()
            } else {
                kept.put(value)
            }
        }
        prefs.edit().putString(CATALOG, kept.toString()).apply()
        added = added.filterNot { it.key == key }
    }
}
