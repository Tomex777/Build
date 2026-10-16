package com.night.poetry

import android.content.ContentValues
import android.content.Context
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Path
import android.graphics.Typeface
import android.os.Bundle
import android.os.Build
import androidx.core.content.FileProvider
import android.os.Environment
import android.util.AtomicFile
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.io.File
import kotlin.math.roundToInt
import kotlin.random.Random

/** Interface appearance only; card backgrounds/exports are never recoloured. */
private data class PoetryAppearance(
    val id: String, val label: String, val note: String,
    val paper: Color, val white: Color, val ink: Color, val muted: Color,
    val accent: Color, val blush: Color, val border: Color, val pale: Color,
    val dark: Boolean = false
)
private val appearances = listOf(
    PoetryAppearance("ink", "Ink & Paper", "Neutral · default", Color(0xFFF8F7F4), Color.White, Color(0xFF242729), Color(0xFF777F82), Color(0xFF384B59), Color(0xFFE8EEF1), Color(0xFFE0E4E5), Color(0xFFEEF0F0)),
    PoetryAppearance("sand", "Warm Sand", "Earthy & gentle", Color(0xFFF6F0E6), Color(0xFFFFFAF0), Color(0xFF373126), Color(0xFF867B69), Color(0xFF806042), Color(0xFFEAE0CE), Color(0xFFE1D6C4), Color(0xFFEEE7DA)),
    PoetryAppearance("forest", "Forest", "Quiet green", Color(0xFFF1F4EF), Color.White, Color(0xFF23352E), Color(0xFF718177), Color(0xFF33634E), Color(0xFFDFEBE0), Color(0xFFD9E3DA), Color(0xFFE6EEE6)),
    PoetryAppearance("slate", "Blue Slate", "Cool & understated", Color(0xFFEDF1F5), Color.White, Color(0xFF233241), Color(0xFF758391), Color(0xFF456B95), Color(0xFFDFEAF5), Color(0xFFDBE3EB), Color(0xFFE6EDF4)),
    PoetryAppearance("night", "Midnight", "Low-light writing", Color(0xFF161A20), Color(0xFF222933), Color(0xFFF4F2ED), Color(0xFFABB5BF), Color(0xFF94B8D5), Color(0xFF273C49), Color(0xFF34414E), Color(0xFF202833), dark = true),
    PoetryAppearance("rose", "Rose", "Optional blush", Color(0xFFFAF8F5), Color.White, Color(0xFF27232A), Color(0xFF958A8C), Color(0xFF893B50), Color(0xFFF8E9E9), Color(0xFFEBE3DF), Color(0xFFF3EFEC))
)
private val LocalPoetryAppearance = compositionLocalOf { appearances.first() }
private val Paper: Color @Composable get() = LocalPoetryAppearance.current.paper
private val Ink: Color @Composable get() = LocalPoetryAppearance.current.ink
private val Muted: Color @Composable get() = LocalPoetryAppearance.current.muted
private val Rose: Color @Composable get() = LocalPoetryAppearance.current.accent
private val SoftRose: Color @Composable get() = LocalPoetryAppearance.current.blush
private val Border: Color @Composable get() = LocalPoetryAppearance.current.border
private val Pale: Color @Composable get() = LocalPoetryAppearance.current.pale
private val OnInk: Color @Composable get() = if (LocalPoetryAppearance.current.dark) LocalPoetryAppearance.current.paper else Color.White

private fun rgb(hex: Long) = hex.toInt()

data class Ornament(
    val id: String = UUID.randomUUID().toString(),
    val assetId: String,
    val x: Float = .40f,
    val y: Float = .15f,
    val size: Float = 1f,
    val opacity: Float = 1f,
    val rotation: Float = 0f,
    val hidden: Boolean = false,
    val locked: Boolean = false,
    val name: String = "",
    val tint: Int? = null
)

data class CardStyleData(
    val background: String = "Quiet paper",
    val color1: Int = rgb(0xFFFFFAF5),
    val color2: Int = rgb(0xFFFFFAF5),
    val textColor: Int = rgb(0xFF342E31),
    val texture: String = "None",
    val fontName: String = "Serif",
    val fontSize: Float = 19f,
    val alignment: String = "Center",
    val poemX: Float = .11f,
    val poemY: Float = .30f,
    val textWidth: Float = .78f,
    val lineHeight: Float = 1.55f,
    val elements: List<Ornament> = emptyList(),
    val decor: String = "None"
)

data class CardSlide(
    val id: String = UUID.randomUUID().toString(),
    val titleOverride: String? = null,
    val bodyOverride: String? = null,
    val authorOverride: String? = null,
    val card: CardStyleData = CardStyleData()
)

data class PoemDoc(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: String = "",
    val author: String = "",
    val updated: Long = System.currentTimeMillis(),
    val card: CardStyleData = CardStyleData(),
    val slides: List<CardSlide> = listOf(CardSlide(card = card))
)

private data class ThemePreset(
    val name: String, val first: Int, val second: Int, val text: Int, val texture: String,
    val pack: String = "Minimal", val decor: String = "None", val font: String = "Serif",
    val size: Float = 19f, val alignment: String = "Center", val y: Float = .30f
)

private val presets = listOf(
    ThemePreset("Quiet paper", rgb(0xFFFFFAF5), rgb(0xFFFFFAF5), rgb(0xFF342E31), "None", "Minimal", "None", "Serif", 18f, "Left", .28f),
    ThemePreset("Blush note", rgb(0xFFFDF1F3), rgb(0xFFFDF1F3), rgb(0xFF4D343C), "None", "Minimal", "None", "Sans", 18f, "Left", .29f),
    ThemePreset("Soft editorial", rgb(0xFFF6F1E8), rgb(0xFFF6F1E8), rgb(0xFF393335), "Grain", "Minimal", "Rule", "Serif", 19f, "Center", .31f),
    ThemePreset("Midnight note", rgb(0xFF1E2028), rgb(0xFF36323C), rgb(0xFFFAF4EB), "Grain", "Minimal", "Stars", "Serif", 18f, "Center", .34f),
    ThemePreset("Little hearts", rgb(0xFFFFFBF4), rgb(0xFFFFFBF4), rgb(0xFFBE354B), "None", "Romance", "Hearts", "Script", 21f, "Center", .35f),
    ThemePreset("Rosewater", rgb(0xFFF7DADD), rgb(0xFFFFF4EF), rgb(0xFF753D4C), "Grain", "Romance", "Petals", "Serif", 19f, "Center", .31f),
    ThemePreset("Love letter", rgb(0xFFF7EEE5), rgb(0xFFF7EEE5), rgb(0xFF6E4043), "Grain", "Romance", "Branch", "Script", 21f, "Left", .30f),
    ThemePreset("Scarlet evening", rgb(0xFF6D243F), rgb(0xFFAF5563), rgb(0xFFFFF1E9), "Grain", "Romance", "Stars", "Serif", 19f, "Center", .34f),
    ThemePreset("December hush", rgb(0xFFE7ECED), rgb(0xFFFDFCF5), rgb(0xFF45565B), "Grain", "Seasons", "Stars", "Serif", 17f, "Left", .22f),
    ThemePreset("Autumn letters", rgb(0xFFE4C9AD), rgb(0xFFFCF5E9), rgb(0xFF5B4039), "Grain", "Seasons", "Branch", "Serif", 18f, "Left", .27f),
    ThemePreset("After the rain", rgb(0xFF233645), rgb(0xFF6F8790), rgb(0xFFFFF9F5), "Grain", "Seasons", "Stars", "Serif", 18f, "Center", .34f),
    ThemePreset("First light", rgb(0xFFFCE9DC), rgb(0xFFF6C6C6), rgb(0xFF553B41), "Grain", "Seasons", "Petals", "Serif", 19f, "Center", .32f),
    ThemePreset("Open page", rgb(0xFFFFF4F7), rgb(0xFFFFF4F7), rgb(0xFF29252A), "None", "Long poems", "None", "Sans", 14f, "Left", .17f),
    ThemePreset("Reading room", rgb(0xFFF5F1E8), rgb(0xFFF5F1E8), rgb(0xFF353239), "None", "Long poems", "Rule", "Serif", 15f, "Left", .16f)
)
private val templatePacks = listOf("All", "Minimal", "Romance", "Seasons", "Long poems")

private fun CardStyleData.withPreset(t: ThemePreset) = copy(
    background = t.name, color1 = t.first, color2 = t.second,
    textColor = t.text, texture = t.texture, decor = t.decor,
    fontName = t.font, fontSize = t.size, alignment = t.alignment, poemY = t.y
)

/** Built-in SVG art converted to Android VectorDrawables; never use platform-dependent emoji glyphs. */
private data class ElementEntry(val id: String, val name: String, val category: String, val drawable: Int)
private val elementEntries = listOf(
    ElementEntry("sparkle", "Sparkle", "Stars", R.drawable.ornament_sparkle),
    ElementEntry("star", "Star", "Stars", R.drawable.ornament_star),
    ElementEntry("crescent", "Crescent moon", "Stars", R.drawable.ornament_crescent),
    ElementEntry("snowflake", "Snowflake", "Stars", R.drawable.ornament_snowflake),
    ElementEntry("flower", "Flower", "Botanical", R.drawable.ornament_flower),
    ElementEntry("petal", "Petal", "Botanical", R.drawable.ornament_petal),
    ElementEntry("leaf", "Leaf", "Botanical", R.drawable.ornament_leaf),
    ElementEntry("branch", "Branch", "Botanical", R.drawable.ornament_branch),
    ElementEntry("sprig", "Sprig", "Botanical", R.drawable.ornament_sprig),
    ElementEntry("butterfly", "Butterfly", "Nature", R.drawable.ornament_butterfly),
    ElementEntry("heart", "Heart", "Symbols", R.drawable.ornament_heart),
    ElementEntry("diamond", "Diamond", "Symbols", R.drawable.ornament_diamond),
    ElementEntry("rule", "Line", "Lines", R.drawable.ornament_rule),
    ElementEntry("wave", "Wave", "Lines", R.drawable.ornament_wave)
)
private val elementById = elementEntries.associateBy { it.id }

/** Reads legacy V3 Unicode ornaments without losing existing user designs. */
private fun normalizeAssetId(value: String): String = when (value) {
    "✦", "✧", "⋆", "⊹", "❈" -> "sparkle"
    "☾" -> "crescent"
    "✿", "✣" -> "flower"
    "❀" -> "petal"
    "❦", "❧" -> "branch"
    "♡", "♥" -> "heart"
    "◇" -> "diamond"
    "━", "—" -> "rule"
    "〜" -> "wave"
    else -> if (elementById.containsKey(value)) value else "sparkle"
}
private fun elementResource(assetId: String): Int = elementById[normalizeAssetId(assetId)]?.drawable
    ?: R.drawable.ornament_sparkle
private fun elementName(assetId: String): String = elementById[normalizeAssetId(assetId)]?.name ?: "Decoration"

@Composable
private fun VectorOrnament(assetId: String, modifier: Modifier, tint: Color) {
    Image(
        painter = painterResource(elementResource(assetId)),
        contentDescription = elementName(assetId), modifier = modifier,
        colorFilter = ColorFilter.tint(tint)
    )
}

private data class TemplateAccent(val assetId: String, val x: Float, val y: Float, val px: Float, val color: Int, val alpha: Float)

/** Shared position generator ensures preview and export use exactly the same vector accents. */
private fun templateAccents(decor: String, foreground: Int): List<TemplateAccent> {
    val kinds = when (decor) {
        "Hearts" -> listOf("heart", "heart", "heart")
        "Stars" -> listOf("sparkle", "star", "snowflake")
        "Petals" -> listOf("flower", "petal", "sprig")
        "Branch" -> listOf("branch")
        "Rule" -> listOf("rule")
        else -> emptyList()
    }
    if (kinds.isEmpty()) return emptyList()
    val count = if (decor == "Hearts") 28 else if (decor == "Rule" || decor == "Branch") 1 else 16
    return (0 until count).map { i ->
        val r = java.util.Random((i * 71 + 29).toLong())
        var x = r.nextFloat() * .91f + .02f
        var y = r.nextFloat() * .93f + .03f
        if (decor == "Hearts" && y > .3f && y < .7f && x > .1f && x < .9f) {
            y = if (i % 2 == 0) .10f + r.nextFloat() * .14f else .80f + r.nextFloat() * .13f
        }
        if (decor == "Rule") { x = .45f; y = .85f }
        if (decor == "Branch") { x = .74f; y = .12f }
        TemplateAccent(
            kinds[i % kinds.size], x, y,
            if (decor == "Branch") 145f else if (decor == "Rule") 112f else (31 + i % 5 * 4).toFloat(),
            if (decor == "Hearts") (if (i % 3 == 0) rgb(0xFFD6384F) else rgb(0xFFEFB3BF)) else foreground,
            if (decor == "Hearts") .70f else .37f
        )
    }
}
/** Private offline JSON store; slides are persisted with their own styles and content overrides. */
private class PoemStore(private val context: Context) {
    private val file get() = context.getFileStreamPath("poetry-library.json")
    private val atomicFile get() = AtomicFile(file)
    private fun readCard(c: JSONObject): CardStyleData {
        val ornaments = c.optJSONArray("elements") ?: JSONArray()
        return CardStyleData(
            background = c.optString("background", "Quiet paper"),
            color1 = c.optInt("color1", rgb(0xFFFFFAF5)),
            color2 = c.optInt("color2", rgb(0xFFFFFAF5)),
            textColor = c.optInt("textColor", rgb(0xFF342E31)),
            texture = c.optString("texture", "None"),
            fontName = c.optString("fontName", "Serif"),
            fontSize = c.optDouble("fontSize", 19.0).toFloat(),
            alignment = c.optString("alignment", "Center"),
            poemX = c.optDouble("poemX", .11).toFloat(),
            poemY = c.optDouble("poemY", .30).toFloat(),
            textWidth = c.optDouble("textWidth", .78).toFloat().coerceIn(.3f, .95f),
            lineHeight = c.optDouble("lineHeight", 1.55).toFloat().coerceIn(1.1f, 2.4f),
            decor = c.optString("decor", "None"),
            elements = (0 until ornaments.length()).map { i ->
                val o = ornaments.getJSONObject(i)
                Ornament(o.optString("id", UUID.randomUUID().toString()),
                    normalizeAssetId(o.optString("assetId", o.optString("symbol", "✦"))),
                    o.optDouble("x", .4).toFloat(), o.optDouble("y", .15).toFloat(),
                    o.optDouble("size", 1.0).toFloat(), o.optDouble("opacity", 1.0).toFloat().coerceIn(0.1f, 1f),
                    o.optDouble("rotation", 0.0).toFloat(), o.optBoolean("hidden", false),
                    o.optBoolean("locked", false), o.optString("name", ""),
                    if (o.has("tint") && !o.isNull("tint")) o.getInt("tint") else null)
            }
        )
    }
    private fun writeCard(c: CardStyleData): JSONObject = JSONObject().apply {
        put("background", c.background); put("color1", c.color1); put("color2", c.color2)
        put("textColor", c.textColor); put("texture", c.texture); put("decor", c.decor)
        put("fontName", c.fontName); put("fontSize", c.fontSize.toDouble())
        put("alignment", c.alignment); put("poemX", c.poemX.toDouble()); put("poemY", c.poemY.toDouble())
        put("textWidth", c.textWidth.toDouble()); put("lineHeight", c.lineHeight.toDouble())
        put("elements", JSONArray().also { arr -> c.elements.forEach { o ->
            arr.put(JSONObject().apply {
                put("id", o.id); put("assetId", o.assetId)
                put("x", o.x.toDouble()); put("y", o.y.toDouble()); put("size", o.size.toDouble()); put("opacity", o.opacity.toDouble())
                put("rotation", o.rotation.toDouble()); put("hidden", o.hidden); put("locked", o.locked)
                put("name", o.name); if (o.tint != null) put("tint", o.tint)
            })
        } })
    }
    fun read(): List<PoemDoc> = try {
        val content = try { atomicFile.openRead().bufferedReader().use { it.readText() } }
        catch (_: java.io.FileNotFoundException) { "[]" }
        val arr = JSONArray(content)
        (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            val oldCard = readCard(d.optJSONObject("card") ?: JSONObject())
            val slidesJson = d.optJSONArray("slides")
            val slides = if (slidesJson != null && slidesJson.length() > 0) {
                (0 until slidesJson.length()).map { j ->
                    val item = slidesJson.getJSONObject(j)
                    CardSlide(
                        id = item.optString("id", UUID.randomUUID().toString()),
                        titleOverride = if (item.has("titleOverride") && !item.isNull("titleOverride")) item.getString("titleOverride") else null,
                        bodyOverride = if (item.has("bodyOverride") && !item.isNull("bodyOverride")) item.getString("bodyOverride") else null,
                        authorOverride = if (item.has("authorOverride") && !item.isNull("authorOverride")) item.getString("authorOverride") else null,
                        card = readCard(item.optJSONObject("card") ?: JSONObject())
                    )
                }
            } else listOf(CardSlide(card = oldCard))
            PoemDoc(id = d.optString("id", UUID.randomUUID().toString()),
                title = d.optString("title"), body = d.optString("body"), author = d.optString("author"),
                updated = d.optLong("updated", System.currentTimeMillis()), card = oldCard, slides = slides)
        }
    } catch (failure: Exception) {
        // Never silently overwrite an unreadable poem library without preserving its bytes.
        // The recovered copy stays private in app storage for a future import/recovery screen.
        runCatching {
            if (file.exists()) {
                val backup = File(context.filesDir,
                    "poetry-recovery-${System.currentTimeMillis()}.json")
                file.copyTo(backup, overwrite = false)
            }
        }
        Toast.makeText(context,
            "Poetry library could not be read. An emergency copy was kept for recovery.",
            Toast.LENGTH_LONG).show()
        emptyList()
    }
    @Synchronized fun write(poems: List<PoemDoc>) {
        val arr = JSONArray()
        poems.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id); put("title", p.title); put("body", p.body)
                put("author", p.author); put("updated", p.updated)
                put("card", writeCard(p.card))
                put("slides", JSONArray().also { pages -> p.slides.forEach { sl ->
                    pages.put(JSONObject().apply {
                        put("id", sl.id); put("titleOverride", sl.titleOverride)
                        put("bodyOverride", sl.bodyOverride); put("authorOverride", sl.authorOverride)
                        put("card", writeCard(sl.card))
                    })
                } })
            })
        }
        val output = atomicFile.startWrite()
        try {
            output.write(arr.toString().toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (failure: Exception) {
            atomicFile.failWrite(output)
            throw failure
        }
    }
}

data class SavedBackground(val first: Int, val second: Int, val texture: String)

private class BackgroundCollection(context: Context) {
    private val prefs = context.getSharedPreferences("poetry-background-library", Context.MODE_PRIVATE)
    fun read(): List<SavedBackground> = try {
        val arr = JSONArray(prefs.getString("backgrounds", "[]"))
        (0 until arr.length()).map { i ->
            val item = arr.getJSONObject(i)
            SavedBackground(item.getInt("first"), item.getInt("second"), item.optString("texture", "None"))
        }
    } catch (_: Exception) { emptyList() }
    fun write(backgrounds: List<SavedBackground>) {
        val arr = JSONArray()
        backgrounds.forEach { b -> arr.put(JSONObject().apply {
            put("first", b.first); put("second", b.second); put("texture", b.texture)
        }) }
        prefs.edit().putString("backgrounds", arr.toString()).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PoetryApplication() }
    }
}

@Composable
private fun PoetryApplication() {
    val context = LocalContext.current
    val appearancePrefs = remember(context) { context.getSharedPreferences("poetry-ui-appearance", Context.MODE_PRIVATE) }
    var appearanceId by remember { mutableStateOf(appearancePrefs.getString("theme", "ink") ?: "ink") }
    val appearance = appearances.firstOrNull { it.id == appearanceId } ?: appearances.first()
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            @Suppress("DEPRECATION")
            window.statusBarColor = appearance.paper.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = appearance.paper.toArgb()
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (appearance.dark) 0 else
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }
    val repository = remember { PoemStore(context.applicationContext) }
    val bgLibrary = remember { BackgroundCollection(context.applicationContext) }
    val savedBackgrounds = remember { mutableStateListOf<SavedBackground>().apply { addAll(bgLibrary.read()) } }
    val poems = remember { mutableStateListOf<PoemDoc>().apply { addAll(repository.read()) } }
    var page by remember { mutableStateOf("home") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var panel by remember { mutableStateOf("") }
    var slideIndex by remember { mutableIntStateOf(0) }
    var safeArea by remember { mutableStateOf(false) }
    var templateReturn by remember { mutableStateOf("home") }
    var exporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    // A brief persistence debounce prevents a blocking disk write for each drag frame.
    // A lifecycle flush protects edits when the app moves into the background.
    val scheduledWrite = remember { arrayOfNulls<Job>(1) }
    fun schedulePersist() {
        val snapshot = poems.toList()
        scheduledWrite[0]?.cancel()
        scheduledWrite[0] = scope.launch {
            delay(370L)
            withContext(Dispatchers.IO) { repository.write(snapshot) }
        }
    }
    DisposableEffect(lifecycleOwner, repository) {
        val watcher = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                scheduledWrite[0]?.cancel()
                repository.write(poems.toList())
            }
        }
        lifecycleOwner.lifecycle.addObserver(watcher)
        onDispose { lifecycleOwner.lifecycle.removeObserver(watcher) }
    }
    // Simple bounded per-document undo history. Rapid drag samples are one undo step.
    val undoHistory = remember { mutableStateListOf<PoemDoc>() }
    val redoHistory = remember { mutableStateListOf<PoemDoc>() }
    var lastHistoryAt by remember { mutableLongStateOf(0L) }
    var applyingHistory by remember { mutableStateOf(false) }
    fun restore(snapshot: PoemDoc) {
        applyingHistory = true
        val position = poems.indexOfFirst { it.id == snapshot.id }
        if (position >= 0) { poems[position] = snapshot; schedulePersist() }
        applyingHistory = false
    }
    fun undoDesign() {
        if (undoHistory.isEmpty()) return
        val current = poems.firstOrNull { it.id == selectedId } ?: return
        redoHistory.add(current)
        restore(undoHistory.removeAt(undoHistory.lastIndex))
        lastHistoryAt = 0L
    }
    fun redoDesign() {
        if (redoHistory.isEmpty()) return
        val current = poems.firstOrNull { it.id == selectedId } ?: return
        undoHistory.add(current)
        restore(redoHistory.removeAt(redoHistory.lastIndex))
        lastHistoryAt = 0L
    }

    fun create(): PoemDoc {
        val doc = PoemDoc()
        poems.add(0, doc); repository.write(poems)
        selectedId = doc.id
        return doc
    }
    fun update(doc: PoemDoc) {
        val index = poems.indexOfFirst { it.id == doc.id }
        if (index >= 0) {
            if (page == "studio" && !applyingHistory && poems[index] != doc) {
                val now = System.currentTimeMillis()
                if (undoHistory.isEmpty() || now - lastHistoryAt > 650L) {
                    undoHistory.add(poems[index])
                    if (undoHistory.size > 45) undoHistory.removeAt(0)
                }
                lastHistoryAt = now
                redoHistory.clear()
            }
            poems[index] = doc.copy(updated = System.currentTimeMillis())
            schedulePersist()
        }
    }
    fun active(): PoemDoc {
        return poems.firstOrNull { it.id == selectedId } ?: create()
    }
    fun openEditor(id: String? = null) {
        selectedId = id ?: create().id
        page = "write"
    }
    fun openStudio(id: String? = null) {
        selectedId = id ?: selectedId ?: create().id
        panel = ""
        undoHistory.clear(); redoHistory.clear(); lastHistoryAt = 0L
        slideIndex = 0
        page = "studio"
    }
    fun shownDoc(): PoemDoc {
        val original = active()
        val current = original.slides.getOrNull(slideIndex) ?: original.slides.first()
        return original.copy(
            title = current.titleOverride ?: original.title,
            body = current.bodyOverride ?: original.body,
            author = current.authorOverride ?: original.author,
            card = current.card
        )
    }
    fun updateCurrent(edited: PoemDoc) {
        val original = active()
        val changed = original.slides.mapIndexed { i, sl ->
            if (i == slideIndex) sl.copy(card = edited.card) else sl
        }
        update(original.copy(slides = changed))
    }
    fun saveBackground() {
        val style = shownDoc().card
        savedBackgrounds.add(SavedBackground(style.color1, style.color2, style.texture))
        bgLibrary.write(savedBackgrounds)
        Toast.makeText(context, "Background added to your library", Toast.LENGTH_SHORT).show()
    }
    fun applyBackground(background: SavedBackground) {
        updateCurrent(shownDoc().copy(card = shownDoc().card.copy(
            background = "My background", color1 = background.first,
            color2 = background.second, texture = background.texture
        )))
    }
    fun addSlide() {
        val original = active(); val source = original.slides[slideIndex]
        val blank = source.copy(id = UUID.randomUUID().toString(), titleOverride = "",
            bodyOverride = "", authorOverride = "", card = source.card.copy(elements = emptyList()))
        update(original.copy(slides = original.slides.toMutableList().apply { add(slideIndex + 1, blank) }))
        slideIndex++
    }
    fun duplicateSlide() {
        val original = active(); val source = original.slides[slideIndex]
        val copy = source.copy(id = UUID.randomUUID().toString(), card = source.card.copy(
            elements = source.card.elements.map { it.copy(id = UUID.randomUUID().toString()) }))
        update(original.copy(slides = original.slides.toMutableList().apply { add(slideIndex + 1, copy) }))
        slideIndex++
    }
    fun deleteSlide() {
        val original = active()
        if (original.slides.size <= 1) return
        update(original.copy(slides = original.slides.filterIndexed { i, _ -> i != slideIndex }))
        slideIndex = (slideIndex - 1).coerceAtLeast(0)
    }
    fun moveSlide(direction: Int) {
        val original = active(); val j = slideIndex + direction
        if (j !in original.slides.indices) return
        val slides = original.slides.toMutableList()
        val item = slides[slideIndex]; slides[slideIndex] = slides[j]; slides[j] = item
        update(original.copy(slides = slides)); slideIndex = j
    }
    fun changeSlideText(field: String, value: String) {
        val original = active()
        val revised = original.slides.mapIndexed { i, sl ->
            if (i != slideIndex) sl else when (field) {
                "title" -> sl.copy(titleOverride = value)
                "body" -> sl.copy(bodyOverride = value)
                "author" -> sl.copy(authorOverride = value)
                else -> sl
            }
        }
        update(original.copy(slides = revised))
    }
    fun applyCurrentStyleToAll() {
        val original = active()
        val source = original.slides.getOrNull(slideIndex)?.card ?: return
        // Preserve each page's content AND individually placed decorative elements.
        val revised = original.slides.map { page ->
            page.copy(card = page.card.copy(
                background = source.background, color1 = source.color1, color2 = source.color2,
                textColor = source.textColor, texture = source.texture,
                fontName = source.fontName, fontSize = source.fontSize,
                alignment = source.alignment, textWidth = source.textWidth,
                lineHeight = source.lineHeight, decor = source.decor
            ))
        }
        update(original.copy(slides = revised))
    }
    fun splitPoem() {
        val original = active()
        val parts = splitStanzas(original.body)
        if (parts.isEmpty()) {
            Toast.makeText(context, "Write a poem before splitting it", Toast.LENGTH_SHORT).show()
            return
        }
        val base = original.slides[slideIndex].card
        val slides = parts.mapIndexed { i, body -> CardSlide(
            titleOverride = if (i == 0) original.title else "",
            bodyOverride = body,
            authorOverride = if (i == parts.lastIndex) original.author else "",
            card = base.copy(elements = emptyList(), poemY = .16f, fontSize = minOf(base.fontSize, 15f))
        ) }
        update(original.copy(slides = slides)); slideIndex = 0
    }
    BackHandler(enabled = page != "home") {
        page = when (page) { "studio" -> "write"; "maker" -> "studio"; "templates" -> templateReturn; else -> "home" }
    }

    CompositionLocalProvider(LocalPoetryAppearance provides appearance) {
        val scheme = if (appearance.dark) darkColorScheme(
            primary = appearance.accent, onPrimary = appearance.paper,
            background = appearance.paper, surface = appearance.white, onSurface = appearance.ink
        ) else lightColorScheme(
            primary = appearance.accent, onPrimary = Color.White,
            background = appearance.paper, surface = appearance.white, onSurface = appearance.ink
        )
        MaterialTheme(colorScheme = scheme) {
            Surface(color = Paper, modifier = Modifier.fillMaxSize()) {
            when (page) {
                "home" -> HomeScreen(
                    poems = poems.toList(), newPoem = { openEditor() },
                    edit = { openEditor(it) }, design = { openStudio(it) },
                    settings = { page = "settings" },
                    browseTemplates = { selectedId = null; templateReturn = "home"; page = "templates" },
                    chooseTemplate = { preset ->
                        val fresh = create()
                        update(fresh.copy(slides = fresh.slides.map { it.copy(card = it.card.withPreset(preset)) }))
                        openStudio(fresh.id)
                    },
                    delete = { id -> poems.removeAll { it.id == id }; repository.write(poems) }
                )
                "write" -> WriterScreen(
                    doc = active(), update = ::update, back = { page = "home" },
                    design = { openStudio() }
                )
                "studio" -> {
                    val displayDoc = shownDoc()
                    StudioScreen(
                        doc = displayDoc, update = ::updateCurrent, panel = panel,
                        setPanel = { panel = it }, back = { page = "write" },
                        index = slideIndex, count = active().slides.size,
                        slides = active().slides,
                        changeSlideText = ::changeSlideText,
                        changeSlide = { slideIndex = it },
                        addSlide = ::addSlide, duplicateSlide = ::duplicateSlide,
                        removeSlide = ::deleteSlide, moveSlide = ::moveSlide,
                        splitPoem = ::splitPoem,
                        applyStyleToAll = ::applyCurrentStyleToAll,
                        openMaker = { page = "maker" },
                        savedBackgrounds = savedBackgrounds.toList(),
                        applyBackground = ::applyBackground,
                        safeArea = safeArea, toggleSafe = { safeArea = !safeArea },
                        canUndo = undoHistory.isNotEmpty(), canRedo = redoHistory.isNotEmpty(),
                        undo = ::undoDesign, redo = ::redoDesign,
                        exportBusy = exporting,
                        export = { share, all, pixels ->
                            if (!exporting) {
                                exporting = true
                                val original = active()
                                val exportDocs = if (all) original.slides.map { sl -> original.copy(
                                    title = sl.titleOverride ?: original.title,
                                    body = sl.bodyOverride ?: original.body,
                                    author = sl.authorOverride ?: original.author,
                                    card = sl.card
                                ) } else listOf(displayDoc)
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val created = mutableListOf<android.net.Uri>()
                                            try {
                                                exportDocs.forEach { created += CardExporter.export(context, it, pixels) }
                                                created.toList()
                                            } catch (e: Exception) {
                                                // A carousel export is all-or-nothing; don't leave half a post in Gallery.
                                                created.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
                                                throw e
                                            }
                                        }
                                    }
                                    exporting = false
                                    result.onSuccess { uris ->
                                        if (share) {
                                            val intent = Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
                                                type = "image/png"
                                                if (uris.size > 1) putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                                else putExtra(Intent.EXTRA_STREAM, uris.first())
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share poetry cards"))
                                        } else Toast.makeText(context, if (Build.VERSION.SDK_INT >= 29)
                                            "Saved ${uris.size} card(s) to Pictures / Poetry"
                                        else "Saved ${uris.size} card(s) locally. Use Share to send them to another app.",
                                        Toast.LENGTH_LONG).show()
                                    }.onFailure { e ->
                                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    )
                }
                "maker" -> BackgroundMakerScreen(doc = shownDoc(), update = ::updateCurrent,
                    back = { page = "studio" }, save = ::saveBackground)
                "templates" -> TemplateGalleryScreen(select = { selected ->
                    if (selectedId == null) create()
                    updateCurrent(shownDoc().copy(card = shownDoc().card.withPreset(selected)))
                    panel = "Templates"; page = "studio"
                }, back = { page = templateReturn })
                "settings" -> SettingsScreen(
                    back = { page = "home" }, count = poems.size,
                    currentTheme = appearanceId,
                    onTheme = { id -> appearanceId = id; appearancePrefs.edit().putString("theme", id).apply() }
                )
            }
            }
        }
    }
}

@Composable
private fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        color = Rose, letterSpacing = 2.5.sp)
}

@Composable
private fun Heading(text: String, fontSize: TextUnit = 33.sp) {
    Text(text, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal,
        fontSize = fontSize, lineHeight = (fontSize.value + 5).sp, color = Ink)
}

@Composable
private fun SoftButton(label: String, onClick: () -> Unit, filled: Boolean = true, modifier: Modifier = Modifier) {
    if (filled) Button(onClick = onClick, modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ink),
        shape = RoundedCornerShape(15.dp)) { Text(label, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
    else OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(15.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HomeScreen(
    poems: List<PoemDoc>, newPoem: () -> Unit, edit: (String) -> Unit,
    design: (String) -> Unit, settings: () -> Unit,
    browseTemplates: () -> Unit, chooseTemplate: (ThemePreset) -> Unit, delete: (String) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    pendingDelete?.let { id ->
        AlertDialog(onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this poem?") },
            text = { Text("This removes the poem and its cards from this device.") },
            confirmButton = { TextButton(onClick = { delete(id); pendingDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } })
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VectorOrnament("diamond", Modifier.size(26.dp), Rose)
                Spacer(Modifier.width(9.dp))
                Text("poetry", fontSize = 27.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = Ink)
            }
            IconButton(onClick = settings) { Icon(Icons.Default.Tune, contentDescription = "Preferences", tint = Ink) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(18.dp))
            Eyebrow("Your writing space")
            Spacer(Modifier.height(9.dp))
            Heading("Every feeling\nhas a story.", 38.sp)
            Spacer(Modifier.height(11.dp))
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(25.dp))
                .background(SoftRose).border(1.dp, Border, RoundedCornerShape(25.dp))) {
                VectorOrnament("sparkle", Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(128.dp), Rose.copy(alpha = .13f))
                Column(Modifier.align(Alignment.CenterStart).padding(24.dp)) {
                    Text("A blank page.\nA thousand possibilities.", fontFamily = FontFamily.Serif, fontSize = 26.sp,
                        lineHeight = 30.sp, color = Ink)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Ink).clickable(onClick = newPoem)
                        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = OnInk, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Write a poem", color = OnInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(27.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Eyebrow("A little inspiration")
                    Spacer(Modifier.height(4.dp)); Heading("Made for your words", 25.sp)
                }
                Text("Explore all →", color = Rose, fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = browseTemplates).padding(4.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.filter { it.name in listOf("Quiet paper", "Soft editorial", "Open page", "Midnight note", "December hush") }
                    .forEach { preset -> TemplateTile(preset, false) { chooseTemplate(preset) } }
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Eyebrow("Your collection")
                    Spacer(Modifier.height(4.dp))
                    Heading("My poems", 27.sp)
                }
                Text("${poems.size} saved", fontSize = 12.sp, color = Muted)
            }
            Spacer(Modifier.height(13.dp))
            if (poems.isEmpty()) {
                Column(Modifier.fillMaxWidth().border(1.dp, Border, RoundedCornerShape(23.dp))
                    .padding(horizontal = 24.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LibraryBooks, contentDescription = null, tint = Rose, modifier = Modifier.size(27.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No poems yet", fontFamily = FontFamily.Serif, fontSize = 21.sp, color = Ink)
                    Text("Your saved writing will appear here.", color = Muted, fontSize = 12.sp)
                }
            } else {
                poems.sortedByDescending { it.updated }.forEach { poem ->
                    val firstCard = poem.slides.firstOrNull()?.card ?: poem.card
                    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp))
                        .background(LocalPoetryAppearance.current.white).clickable { edit(poem.id) }.padding(17.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp, 64.dp).clip(RoundedCornerShape(8.dp))
                                .background(Brush.verticalGradient(listOf(Color(firstCard.color1), Color(firstCard.color2)))))
                            { VectorOrnament("sparkle", Modifier.align(Alignment.Center).size(24.dp), Color(firstCard.textColor)) }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(poem.title.ifBlank { "Untitled poem" }, fontFamily = FontFamily.Serif, fontSize = 20.sp,
                                    color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(poem.body.ifBlank { "A little space for your thoughts…" },
                                    fontSize = 12.sp, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { design(poem.id) }) {
                                Icon(Icons.Default.Palette, contentDescription = "Design card", tint = Rose)
                            }
                            IconButton(onClick = { pendingDelete = poem.id }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete poem", tint = Muted)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun WriterScreen(doc: PoemDoc, update: (PoemDoc) -> Unit, back: () -> Unit, design: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, contentDescription = "Library") }
            Column(Modifier.weight(1f).padding(start = 9.dp)) {
                Eyebrow("Writing desk")
            }
        }
        HorizontalDivider(color = Border)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(27.dp)) {
            Spacer(Modifier.height(31.dp))
            Spacer(Modifier.height(9.dp))
            BasicTextField(
                value = doc.title, onValueChange = { update(doc.copy(title = it)) },
                modifier = Modifier.fillMaxWidth().testTag("poem-title-input"), textStyle = TextStyle(color = Ink, fontFamily = FontFamily.Serif,
                    fontSize = 34.sp, lineHeight = 40.sp),
                decorationBox = { inner -> Box { if (doc.title.isBlank()) Text("Give it a title…",
                    fontFamily = FontFamily.Serif, fontSize = 34.sp, color = Muted.copy(alpha = .55f)); inner() } }
            )
            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = Border)
            Spacer(Modifier.height(24.dp))
            BasicTextField(
                value = doc.body, onValueChange = { update(doc.copy(body = it)) },
                modifier = Modifier.fillMaxWidth().testTag("poem-body-input").defaultMinSize(minHeight = 260.dp),
                textStyle = TextStyle(color = Ink, fontFamily = FontFamily.Serif, fontSize = 20.sp, lineHeight = 34.sp),
                decorationBox = { inner -> Box {
                    if (doc.body.isBlank()) Text("Write your poem…",
                        fontFamily = FontFamily.Serif, fontSize = 20.sp, lineHeight = 34.sp, color = Muted.copy(alpha = .55f))
                    inner()
                } }
            )
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = Border)
            Spacer(Modifier.height(18.dp))
            Eyebrow("Signature · optional")
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = doc.author, onValueChange = { update(doc.copy(author = it)) },
                textStyle = TextStyle(fontSize = 15.sp, color = Ink),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner -> Box { if (doc.author.isEmpty()) Text("Your pen name", color = Muted, fontSize = 15.sp); inner() } }
            )
        }
        HorizontalDivider(color = Border)
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${doc.body.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size} words",
                modifier = Modifier.weight(1f), color = Muted, fontSize = 12.sp)
            SoftButton("Design a card  →", design)
        }
    }
}

@Composable
private fun StudioScreen(
    doc: PoemDoc, update: (PoemDoc) -> Unit, panel: String,
    setPanel: (String) -> Unit, back: () -> Unit,
    index: Int, count: Int, slides: List<CardSlide>,
    changeSlideText: (String, String) -> Unit,
    changeSlide: (Int) -> Unit,
    addSlide: () -> Unit, duplicateSlide: () -> Unit,
    removeSlide: () -> Unit, moveSlide: (Int) -> Unit, splitPoem: () -> Unit,
    applyStyleToAll: () -> Unit,
    openMaker: () -> Unit, savedBackgrounds: List<SavedBackground>,
    applyBackground: (SavedBackground) -> Unit,
    safeArea: Boolean, toggleSafe: () -> Unit,
    canUndo: Boolean, canRedo: Boolean, undo: () -> Unit, redo: () -> Unit,
    exportBusy: Boolean, export: (Boolean, Boolean, Int) -> Unit
) {
    var showExportDialog by remember { mutableStateOf(false) }
    // Artboard transforms are view-only: they never alter exported card geometry.
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var snapEnabled by remember { mutableStateOf(true) }
    var guideVertical by remember { mutableStateOf<Float?>(null) }
    var guideHorizontal by remember { mutableStateOf<Float?>(null) }
    var selectedElementId by remember(doc.id, index) { mutableStateOf<String?>(null) }
    var editTextOnCard by remember(doc.id, index) { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    // Back dismisses the active editing mode before navigating away from Studio.
    BackHandler(enabled = editTextOnCard || panel.isNotEmpty() || selectedElementId != null) {
        when {
            editTextOnCard -> editTextOnCard = false
            panel.isNotEmpty() -> setPanel("")
            else -> selectedElementId = null
        }
    }
    if (showExportDialog) {
        ExportDialog(slideCount = count, busy = exportBusy,
            dismiss = { showExportDialog = false },
            perform = { share, all, pixels ->
                showExportDialog = false
                export(share, all, pixels)
            })
    }
    val selectedElement = doc.card.elements.firstOrNull { it.id == selectedElementId }
    Column(Modifier.fillMaxSize().background(Paper)) {
        // Canva-like, but optimized for a portrait Android workspace: actions live above the artboard.
        Row(Modifier.fillMaxWidth().height(57.dp).padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to poem", tint = Ink)
            }
            Column(Modifier.weight(1f).padding(start = 3.dp)) {
                Text(doc.title.ifBlank { "Untitled poem" }, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = undo, enabled = canUndo, modifier = Modifier.size(35.dp)) {
                Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (canUndo) Ink else Muted.copy(alpha = .4f))
            }
            IconButton(onClick = redo, enabled = canRedo, modifier = Modifier.size(35.dp)) {
                Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (canRedo) Ink else Muted.copy(alpha = .4f))
            }
            Box {
                IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = "More editor tools", tint = Ink)
                }
                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                    DropdownMenuItem(text = { Text(if (snapEnabled) "Disable snapping" else "Enable snapping") }, onClick = {
                        snapEnabled = !snapEnabled; showMoreMenu = false
                        guideVertical = null; guideHorizontal = null
                    })
                    DropdownMenuItem(text = { Text(if (safeArea) "Hide TikTok guides" else "Show TikTok guides") }, onClick = {
                        toggleSafe(); showMoreMenu = false
                    })
                    DropdownMenuItem(text = { Text("Layers") }, onClick = {
                        setPanel("Layers"); showMoreMenu = false
                    })
                    DropdownMenuItem(text = { Text("Zoom to fit") }, onClick = {
                        zoom = 1f; panX = 0f; panY = 0f; showMoreMenu = false
                    })
                }
            }
            Button(onClick = { showExportDialog = true }, enabled = !exportBusy,
                shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                Text("Export", fontSize = 12.sp)
            }
        }
        HorizontalDivider(color = Border)
        // The canvas is always the focus; tool drawers are closed by default.
        Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFFE8EBED))
            .pointerInput(Unit) {
                detectTransformGestures(panZoomLock = true) { _, pan, scale, _ ->
                    val nextZoom = (zoom * scale).coerceIn(1f, 3f)
                    if (nextZoom == 1f) {
                        panX = 0f
                        panY = 0f
                    } else {
                        val maxX = size.width * (nextZoom - 1f) / 2f
                        val maxY = size.height * (nextZoom - 1f) / 2f
                        panX = (panX + pan.x).coerceIn(-maxX, maxX)
                        panY = (panY + pan.y).coerceIn(-maxY, maxY)
                    }
                    zoom = nextZoom
                }
            }) {
            Canvas(Modifier.matchParentSize()) {
                // Subtle workspace grid; never included in the actual card export.
                val pitch = 28.dp.toPx()
                val radius = .75.dp.toPx()
                var xx = pitch / 2f
                while (xx < size.width) {
                    var yy = pitch / 2f
                    while (yy < size.height) {
                        drawCircle(Color(0xFFCBD2D6), radius, androidx.compose.ui.geometry.Offset(xx, yy))
                        yy += pitch
                    }
                    xx += pitch
                }
            }
            PortraitCard(doc, update,
                Modifier.align(Alignment.Center).fillMaxHeight(.90f).aspectRatio(9f / 16f)
                    .testTag("poetry-card-stage")
                    .graphicsLayer {
                         scaleX = zoom; scaleY = zoom
                         translationX = panX; translationY = panY
                     },
                safeArea, selectedElementId = selectedElementId,
                selectElement = { selectedElementId = it; if (it == "poem") setPanel("") },
                editingText = editTextOnCard,
                onTextChanged = changeSlideText,
                snapEnabled = snapEnabled,
                showVerticalGuide = guideVertical, showHorizontalGuide = guideHorizontal,
                onGuides = { x, y ->
                    guideVertical = x; guideHorizontal = y
                })
        }
        if (editTextOnCard) {
            Row(Modifier.fillMaxWidth().background(Paper).padding(horizontal = 9.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(doc.card.fontName, color = Ink, fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            val fonts = listOf("Serif", "Sans", "Script", "Mono")
                            update(doc.copy(card = doc.card.copy(fontName = fonts[(fonts.indexOf(doc.card.fontName) + 1) % fonts.size])))
                        }.padding(vertical = 9.dp))
                    Text("A−", color = Ink, fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            update(doc.copy(card = doc.card.copy(fontSize = (doc.card.fontSize - 1f).coerceAtLeast(12f))))
                        }.padding(7.dp))
                    Text("A+", color = Ink, fontSize = 14.sp,
                        modifier = Modifier.clickable {
                            update(doc.copy(card = doc.card.copy(fontSize = (doc.card.fontSize + 1f).coerceAtMost(26f))))
                        }.padding(7.dp))
                    Text("Align", color = Ink, fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            val next = when (doc.card.alignment) { "Left" -> "Center"; "Center" -> "Right"; else -> "Left" }
                            update(doc.copy(card = doc.card.copy(alignment = next)))
                        }.padding(7.dp))
                }
                Text("Done", color = Rose, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { editTextOnCard = false }.padding(9.dp))
            }
        } else if (selectedElementId == "poem") {
            Row(Modifier.fillMaxWidth().background(Paper).horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                SoftButton("Edit poem", onClick = { editTextOnCard = true }, filled = false)
                Text("A−", modifier = Modifier.clickable {
                    update(doc.copy(card = doc.card.copy(fontSize = (doc.card.fontSize - 1f).coerceAtLeast(12f))))
                }.padding(8.dp), color = Ink, fontSize = 15.sp)
                Text("A+", modifier = Modifier.clickable {
                    update(doc.copy(card = doc.card.copy(fontSize = (doc.card.fontSize + 1f).coerceAtMost(26f))))
                }.padding(8.dp), color = Ink, fontSize = 15.sp)
                Text("Font", modifier = Modifier.clickable { setPanel("Text") }.padding(8.dp),
                    color = Ink, fontSize = 13.sp)
                Text("Align", modifier = Modifier.clickable {
                    val next = when (doc.card.alignment) { "Left" -> "Center"; "Center" -> "Right"; else -> "Left" }
                    update(doc.copy(card = doc.card.copy(alignment = next)))
                }.padding(8.dp), color = Ink, fontSize = 13.sp)
            }
        }
        // Quick controls appear for the selected element, not for every tool at once.
        if (selectedElement != null && panel != "Layers") {
            ElementSelectionBar(selectedElement,
                change = { revised -> update(doc.copy(card = doc.card.copy(elements = doc.card.elements.map {
                    if (it.id == revised.id) revised else it
                }))) },
                duplicate = {
                    update(doc.copy(card = doc.card.copy(elements = doc.card.elements + selectedElement.copy(
                        id = UUID.randomUUID().toString(), x = (selectedElement.x + .04f).coerceAtMost(.84f),
                        y = (selectedElement.y + .04f).coerceAtMost(.94f)))))
                },
                delete = {
                    update(doc.copy(card = doc.card.copy(elements = doc.card.elements.filterNot { it.id == selectedElementId })))
                    selectedElementId = null
                }, close = { selectedElementId = null },
                forward = {
                    val list = doc.card.elements.toMutableList()
                    val i = list.indexOfFirst { it.id == selectedElementId }
                    if (i >= 0 && i < list.lastIndex) {
                        val temp = list[i]; list[i] = list[i + 1]; list[i + 1] = temp
                        update(doc.copy(card = doc.card.copy(elements = list)))
                    }
                })
        }
        // Pages always visible: swipeable thumbnails are distinct from the Layers tool.
        if (count > 1 && !editTextOnCard) Row(Modifier.fillMaxWidth().height(55.dp).background(Paper).horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            slides.forEachIndexed { pageIndex, slide ->
                Row(Modifier.clip(RoundedCornerShape(8.dp))
                    .border(1.dp, if (pageIndex == index) Rose else Border, RoundedCornerShape(8.dp))
                    .clickable { selectedElementId = null; changeSlide(pageIndex) }
                    .padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 19.dp, height = 31.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.verticalGradient(listOf(Color(slide.card.color1), Color(slide.card.color2)))))
                    Text("${pageIndex + 1}", color = if (pageIndex == index) Rose else Muted,
                        fontWeight = FontWeight.Medium, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 6.dp, end = 4.dp))
                }
            }
            Text("+ Page", color = Rose, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { addSlide() }.padding(8.dp))
        }
        HorizontalDivider(color = Border)
        if (panel.isNotEmpty() && !editTextOnCard) {
            Row(Modifier.fillMaxWidth().height(36.dp).background(Paper)
                .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(panel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { setPanel("") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close tool drawer", modifier = Modifier.size(18.dp), tint = Muted)
                }
            }
            Box(Modifier.fillMaxWidth().height(190.dp).background(Paper)) {
                when (panel) {
                    "Templates" -> TemplatePanel(doc, update)
                    "Backgrounds" -> Column {
                        Row(Modifier.fillMaxWidth().padding(start = 15.dp, end = 15.dp, top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Eyebrow("Canvas background")
                            Text("+ Create", color = Rose, fontSize = 12.sp,
                                modifier = Modifier.clickable(onClick = openMaker).padding(6.dp))
                        }
                        BackgroundPanel(doc, update)
                        if (savedBackgrounds.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                savedBackgrounds.forEach { bg ->
                                    Box(Modifier.size(37.dp).clip(RoundedCornerShape(7.dp))
                                        .background(Brush.verticalGradient(listOf(Color(bg.first), Color(bg.second))))
                                        .clickable { applyBackground(bg) })
                                }
                            }
                        }
                    }
                    "Elements" -> ElementPanel(doc, update, onAdded = { id ->
                        selectedElementId = id; setPanel("")
                    })
                    "Text" -> TextPanel(doc, update, onEdit = { editTextOnCard = true })
                    "Slides" -> SlidesPanel(slides, index, changeSlide, addSlide, duplicateSlide,
                        removeSlide, moveSlide, splitPoem, applyStyleToAll, exportBusy)
                    "Layers" -> LayersPanel(doc, update,
                        selectedElementId = selectedElementId,
                        onSelect = { selectedElementId = it })
                }
            }
            HorizontalDivider(color = Border)
        }
        // Tool dock resembles a mobile design application, not a persistent settings form.
        if (!editTextOnCard) Row(Modifier.fillMaxWidth().height(63.dp).background(Paper)
            .horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            val tools = listOf(
                Triple("Templates", Icons.Default.Collections, "Design"),
                Triple("Elements", Icons.Default.AutoAwesome, "Elements"),
                Triple("Text", Icons.Default.Article, "Text"),
                Triple("Backgrounds", Icons.Default.Palette, "Background"),
                Triple("Slides", Icons.Default.GridView, "Pages")
            )
            tools.forEach { (key, toolIcon, title) ->
                val active = panel == key
                Column(Modifier.width(67.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (active) SoftRose else Color.Transparent)
                    .clickable { setPanel(if (active) "" else key) }
                    .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(toolIcon, contentDescription = null,
                        modifier = Modifier.size(20.dp), tint = if (active) Rose else Ink)
                    Spacer(Modifier.height(3.dp))
                    Text(title, color = if (active) Rose else Muted, fontSize = 10.sp)
                }
            }
        }
    }
}


/**
 * ABR: sliders must never use unstyled Android/Material defaults.
 * The native Compose control below draws a slim capsule rail, branded fill,
 * a small circular knob and a readable value badge. The entire 44dp rail
 * is touchable (tap/drag) and exposes SetProgress for accessibility.
 */
@Composable
private fun PoetryRangeControl(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Rose
    val ink = Ink
    val quiet = Muted
    val badge = SoftRose
    val latestOnChange by rememberUpdatedState(onValueChange)
    val safeValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(.0001f)

    fun valueAt(x: Float, width: Float, inset: Float): Float {
        val fraction = ((x - inset) / (width - inset * 2f).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return valueRange.start + fraction * span
    }

    Row(modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = quiet,
            maxLines = 1, modifier = Modifier.width(76.dp))
        Canvas(Modifier.weight(1f).height(44.dp)
            .semantics {
                contentDescription = "$label, $valueText"
                progressBarRangeInfo = ProgressBarRangeInfo(safeValue, valueRange)
                setProgress { requested ->
                    latestOnChange(requested.coerceIn(valueRange.start, valueRange.endInclusive))
                    true
                }
            }
            .pointerInput(valueRange.start, valueRange.endInclusive) {
                val inset = 12.dp.toPx()
                detectTapGestures { touch ->
                    latestOnChange(valueAt(touch.x, size.width.toFloat(), inset))
                }
            }
            .pointerInput(valueRange.start, valueRange.endInclusive) {
                val inset = 12.dp.toPx()
                detectDragGestures(
                    onDragStart = { start ->
                        latestOnChange(valueAt(start.x, size.width.toFloat(), inset))
                    }
                ) { change, _ ->
                    change.consume()
                    latestOnChange(valueAt(change.position.x, size.width.toFloat(), inset))
                }
            }) {
            val inset = 12.dp.toPx()
            val railWidth = (size.width - 2f * inset).coerceAtLeast(1f)
            val fraction = ((safeValue - valueRange.start) / span).coerceIn(0f, 1f)
            val centerY = size.height / 2f
            val railHeight = 5.dp.toPx()
            val corner = CornerRadius(railHeight / 2f)
            val start = Offset(inset, centerY - railHeight / 2f)
            drawRoundRect(ink.copy(alpha = .13f), topLeft = start,
                size = Size(railWidth, railHeight), cornerRadius = corner)
            if (fraction > 0f) drawRoundRect(accent, topLeft = start,
                size = Size(railWidth * fraction, railHeight), cornerRadius = corner)
            val knob = Offset(inset + railWidth * fraction, centerY)
            drawCircle(accent.copy(alpha = .12f), radius = 12.dp.toPx(), center = knob)
            drawCircle(Color.White, radius = 9.dp.toPx(), center = knob)
            drawCircle(accent, radius = 6.5.dp.toPx(), center = knob)
            drawCircle(Color.White, radius = 2.dp.toPx(), center = knob)
        }
        Box(Modifier.padding(start = 7.dp).width(56.dp)
            .clip(RoundedCornerShape(9.dp)).background(badge)
            .padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(valueText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, color = accent, textAlign = TextAlign.Center)
        }
    }
}

/** Contextual editing: only shown when an actual canvas element has been selected. */
@Composable
private fun ElementSelectionBar(element: Ornament, change: (Ornament) -> Unit,
    duplicate: () -> Unit, delete: () -> Unit, close: () -> Unit,
    forward: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Paper).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VectorOrnament(element.assetId, Modifier.size(22.dp), Rose)
            Text(elementName(element.assetId), color = Ink, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 9.dp))
            IconButton(onClick = duplicate, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate ${elementName(element.assetId)}",
                    tint = Rose, modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = forward, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Layers, contentDescription = "Bring ornament forward",
                    tint = Rose, modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = delete, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove ${elementName(element.assetId)}",
                    tint = Rose, modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = close, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close element tools", tint = Muted,
                    modifier = Modifier.size(19.dp))
            }
        }
        PoetryRangeControl("Size", element.size, .5f..2.8f,
            "%.1f×".format(java.util.Locale.US, element.size)) { change(element.copy(size = it)) }
        PoetryRangeControl("Opacity", element.opacity, .1f..1f,
            "${(element.opacity * 100f).roundToInt()}%") { change(element.copy(opacity = it)) }
        PoetryRangeControl("Rotate", element.rotation, -180f..180f,
            "${element.rotation.roundToInt()}°") { change(element.copy(rotation = it)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(if (element.locked) "Unlock" else "Lock", color = Rose, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(9.dp)).clickable {
                    change(element.copy(locked = !element.locked))
                }.padding(horizontal = 14.dp, vertical = 7.dp))
            Text(if (element.hidden) "Show" else "Hide", color = Rose, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(9.dp)).clickable {
                    change(element.copy(hidden = !element.hidden))
                }.padding(horizontal = 14.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun PortraitCard(doc: PoemDoc, update: (PoemDoc) -> Unit, modifier: Modifier = Modifier,
    safeArea: Boolean = false, selectedElementId: String? = null,
    selectElement: (String?) -> Unit = {}, snapEnabled: Boolean = true,
    editingText: Boolean = false, onTextChanged: (String, String) -> Unit = { _, _ -> },
    showVerticalGuide: Float? = null, showHorizontalGuide: Float? = null,
    onGuides: (Float?, Float?) -> Unit = { _, _ -> }) {
    val style = doc.card
    val poemFocus = remember(doc.id) { FocusRequester() }
    LaunchedEffect(editingText) {
        if (editingText) poemFocus.requestFocus()
    }
    val currentDoc by rememberUpdatedState(doc)
    val currentUpdate by rememberUpdatedState(update)
    BoxWithConstraints(modifier.clip(RoundedCornerShape(7.dp)).background(
        Brush.verticalGradient(listOf(Color(style.color1), Color(style.color2)))
    ).clickable { selectElement(null) }) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        // Scale preview typography with card width; export uses the same 1080px reference canvas.
        val fontPx = style.fontSize * 3.35f * wPx / 1080f
        val bodySp = with(density) { fontPx.toSp() }
        val titleSp = with(density) { (fontPx * 1.18f).toSp() }
        val authorSp = with(density) { (fontPx * .60f).toSp() }
        val bodyLineSp = with(density) { (fontPx * style.lineHeight).toSp() }
        val titleLineSp = with(density) { (fontPx * 1.38f).toSp() }
        val gapDp = with(density) { (fontPx * .7f).toDp() }
        if (style.texture == "Grain") {
            Canvas(Modifier.matchParentSize()) {
                val random = Random(20)
                repeat(240) {
                    drawCircle(Color.White.copy(alpha = .055f),
                        radius = random.nextInt(1, 3).toFloat(),
                        center = androidx.compose.ui.geometry.Offset(random.nextFloat() * size.width, random.nextFloat() * size.height))
                }
            }
        } else if (style.texture == "Lines") {
            Canvas(Modifier.matchParentSize()) {
                for (i in 1..15) drawLine(Color.White.copy(alpha = .06f),
                    start = androidx.compose.ui.geometry.Offset(0f, i * size.height / 16),
                    end = androidx.compose.ui.geometry.Offset(size.width, i * size.height / 16))
            }
        }
        DecorativeOverlay(style.decor, Color(style.textColor), wPx, hPx)
        Column(
            Modifier.fillMaxWidth(style.textWidth.coerceIn(.3f, .95f))
                .offset { IntOffset((style.poemX * wPx).roundToInt(), (style.poemY * hPx).roundToInt()) }
                .border(1.dp, if (selectedElementId == "poem" || editingText) Rose else Color.Transparent, RoundedCornerShape(2.dp))
                .clickable(enabled = !editingText) { selectElement("poem") }
                .pointerInput(doc.id, editingText) {
                    if (!editingText) detectDragGestures(onDragEnd = { onGuides(null, null) },
                        onDragCancel = { onGuides(null, null) }) { change, amount ->
                        change.consume()
                        val d = currentDoc
                        val c = d.card
                        val position = snapPosition(
                            c.poemX + amount.x / wPx, c.poemY + amount.y / hPx,
                            c.textWidth, .12f,
                            if (snapEnabled) listOf(0f, .5f, 1f) else emptyList(),
                            if (snapEnabled) listOf(0f, .5f, 1f) else emptyList(), .022f
                        )
                        onGuides(position.guideX, position.guideY)
                        currentUpdate(d.copy(card = c.copy(
                            poemX = position.x.coerceIn(0f, (1f - c.textWidth).coerceAtLeast(0f)),
                            poemY = position.y.coerceIn(.02f, .86f))))
                    }
                }, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val align = when (style.alignment) {
                "Left" -> TextAlign.Left
                "Right" -> TextAlign.Right
                else -> TextAlign.Center
            }
            if (editingText) {
                BasicTextField(value = doc.title, onValueChange = { onTextChanged("title", it) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = titleSp, lineHeight = titleLineSp,
                        color = Color(style.textColor), textAlign = align, fontFamily = resolveFont(style.fontName)),
                    decorationBox = { field -> Box {
                        if (doc.title.isBlank()) Text("Title", fontSize = titleSp,
                            color = Color(style.textColor).copy(alpha = .35f))
                        field()
                    } })
                Spacer(Modifier.height(gapDp))
                BasicTextField(value = doc.body, onValueChange = { onTextChanged("body", it) },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp).focusRequester(poemFocus),
                    textStyle = TextStyle(fontSize = bodySp, lineHeight = bodyLineSp,
                        color = Color(style.textColor), textAlign = align, fontFamily = resolveFont(style.fontName)),
                    decorationBox = { field -> Box {
                        if (doc.body.isBlank()) Text("Write your poem…", fontSize = bodySp,
                            color = Color(style.textColor).copy(alpha = .38f))
                        field()
                    } })
                Spacer(Modifier.height(gapDp))
                BasicTextField(value = doc.author, onValueChange = { onTextChanged("author", it) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = authorSp,
                        color = Color(style.textColor).copy(alpha = .8f), textAlign = align,
                        fontFamily = resolveFont(style.fontName)),
                    decorationBox = { field -> Box {
                        if (doc.author.isBlank()) Text("Signature (optional)", fontSize = authorSp,
                            color = Color(style.textColor).copy(alpha = .35f))
                        field()
                    } })
            } else {
                if (doc.title.isNotBlank()) {
                    Text(doc.title, fontSize = titleSp, lineHeight = titleLineSp,
                        color = Color(style.textColor), textAlign = align,
                        fontFamily = resolveFont(style.fontName), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(gapDp))
                }
                Text(doc.body.ifBlank { "Your words belong here.\n\nWrite something that stays." },
                    fontSize = bodySp, lineHeight = bodyLineSp,
                    color = Color(style.textColor), textAlign = align,
                    fontFamily = resolveFont(style.fontName), modifier = Modifier.fillMaxWidth())
                if (doc.author.isNotBlank()) {
                    Spacer(Modifier.height(gapDp))
                    Text("— ${doc.author}", color = Color(style.textColor).copy(alpha = .77f),
                        fontFamily = resolveFont(style.fontName), fontSize = authorSp)
                }
            }
        }
        style.elements.filterNot { it.hidden }.forEach { element ->
            val px = 78f * element.size * wPx / 1080f
            val size = with(density) { px.toDp() }
            val isSelected = selectedElementId == element.id
            Box(Modifier.offset { IntOffset((element.x * wPx).roundToInt(), (element.y * hPx).roundToInt()) }
                    .size(size)
                    .graphicsLayer { rotationZ = element.rotation }
                    .then(if (isSelected) Modifier.border(1.dp, Rose, RoundedCornerShape(2.dp)) else Modifier)
                    .clickable { selectElement(element.id) }
                    .pointerInput(element.id) {
                        detectDragGestures(onDragStart = { selectElement(element.id) },
                            onDragEnd = { onGuides(null, null) },
                            onDragCancel = { onGuides(null, null) }) { change, amount ->
                            change.consume()
                            val d = currentDoc
                            val c = d.card
                            val latest = c.elements.firstOrNull { it.id == element.id } ?: element
                            if (!latest.locked) {
                                val bounds = 78f * latest.size / 1080f
                                val other = c.elements.filter { it.id != element.id && !it.hidden }
                                val centersX = listOf(0f, .5f, 1f) + other.flatMap {
                                    val dw = 78f * it.size / 1080f
                                    listOf(it.x, it.x + dw / 2f, it.x + dw)
                                }
                                val centersY = listOf(0f, .5f, 1f) + other.flatMap {
                                    val dh = 78f * it.size / 1920f
                                    listOf(it.y, it.y + dh / 2f, it.y + dh)
                                }
                                val pos = snapPosition(latest.x + amount.x / wPx,
                                    latest.y + amount.y / hPx, bounds, bounds * 9f / 16f,
                                    if (snapEnabled) centersX else emptyList(),
                                    if (snapEnabled) centersY else emptyList(), .022f)
                                onGuides(pos.guideX, pos.guideY)
                                currentUpdate(d.copy(card = c.copy(elements = c.elements.map {
                                    if (it.id == element.id) it.copy(
                                        x = pos.x.coerceIn(0f, .95f),
                                        y = pos.y.coerceIn(0f, .96f)) else it
                                })))
                            }
                        }
                    }) {
                VectorOrnament(element.assetId, Modifier.fillMaxSize(),
                    Color(element.tint ?: style.textColor).copy(alpha = element.opacity))
                if (isSelected && !element.locked) {
                    // Drag this vector handle to resize directly on the card.
                    Box(Modifier.align(Alignment.BottomEnd)
                        .offset(x = 5.dp, y = 5.dp).size(14.dp)
                        .background(Color.White, CircleShape).border(2.dp, Color(0xFF268DC3), CircleShape)
                        .pointerInput(element.id) {
                            detectDragGestures { change, amount ->
                                change.consume()
                                val d = currentDoc
                                val current = d.card.elements.firstOrNull { it.id == element.id } ?: element
                                currentUpdate(d.copy(card = d.card.copy(elements = d.card.elements.map {
                                    if (it.id == current.id) it.copy(size =
                                        (it.size + amount.x * 1080f / (wPx * 78f)).coerceIn(.4f, 3.5f)) else it
                                })))
                            }
                        })
                }
            }
        }
        if (showVerticalGuide != null || showHorizontalGuide != null) {
            Canvas(Modifier.matchParentSize()) {
                val blue = Color(0xFF188FC2)
                showVerticalGuide?.let { x -> drawLine(blue,
                    androidx.compose.ui.geometry.Offset(size.width * x, 0f),
                    androidx.compose.ui.geometry.Offset(size.width * x, size.height), strokeWidth = 1.4.dp.toPx()) }
                showHorizontalGuide?.let { y -> drawLine(blue,
                    androidx.compose.ui.geometry.Offset(0f, size.height * y),
                    androidx.compose.ui.geometry.Offset(size.width, size.height * y), strokeWidth = 1.4.dp.toPx()) }
            }
        }
        if (safeArea) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().fillMaxHeight(.12f)
                .background(Rose.copy(alpha = .15f)))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.21f)
                .background(Rose.copy(alpha = .15f)))
            Box(Modifier.align(Alignment.CenterEnd).fillMaxWidth(.17f).fillMaxHeight(.65f)
                .background(Rose.copy(alpha = .10f)))
            Text("TikTok UI", color = Rose, fontSize = 7.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
        }
    }
}

@Composable
private fun DecorativeOverlay(decor: String, ink: Color, width: Float, height: Float) {
    val density = LocalDensity.current
    templateAccents(decor, ink.toArgb()).forEach { accent ->
        val dpSize = with(density) { (accent.px * width / 1080f).toDp() }
        VectorOrnament(accent.assetId,
            Modifier.offset { IntOffset((accent.x * width).roundToInt(), (accent.y * height).roundToInt()) }
                .size(dpSize),
            Color(accent.color).copy(alpha = accent.alpha))
    }
}

private fun resolveFont(name: String): FontFamily = when (name) {
    "Sans" -> FontFamily.SansSerif; "Mono" -> FontFamily.Monospace
    "Script" -> FontFamily.Cursive; else -> FontFamily.Serif
}

@Composable
private fun TemplatePanel(doc: PoemDoc, update: (PoemDoc) -> Unit) {
    var pack by remember { mutableStateOf("All") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Eyebrow("Curated template packs")
            Text("${presets.size} designs", fontSize = 10.sp, color = Rose)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, top = 7.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            templatePacks.forEach { p ->
                FilterChip(selected = pack == p, onClick = { pack = p },
                    label = { Text(p, fontSize = 10.sp) }, modifier = Modifier.height(33.dp))
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            presets.filter { pack == "All" || it.pack == pack }.forEach { preset ->
                TemplateTile(preset, doc.card.background == preset.name) {
                    update(doc.copy(card = doc.card.withPreset(preset)))
                }
            }
        }
    }
}

@Composable
private fun TemplateTile(preset: ThemePreset, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(91.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(82.dp).height(116.dp).clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Color(preset.first), Color(preset.second))))
            .border(if (selected) 2.dp else 1.dp, if (selected) Rose else Border, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center) {
            Text("where the\nwords belong.", textAlign = TextAlign.Center,
                fontFamily = resolveFont(preset.font), fontSize = 11.sp,
                lineHeight = 14.sp, color = Color(preset.text))
            if (preset.decor == "Hearts") VectorOrnament("heart",
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(14.dp), Color(0xFFD6384F))
        }
        Spacer(Modifier.height(6.dp))
        Text(preset.name, fontSize = 10.sp, maxLines = 2, textAlign = TextAlign.Center,
            lineHeight = 12.sp, color = Ink)
    }
}

@Composable
private fun TemplateGalleryScreen(select: (ThemePreset) -> Unit, back: () -> Unit) {
    var pack by remember { mutableStateOf("All") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Eyebrow("Design library")
                Heading("Poetry templates", 25.sp)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            templatePacks.forEach { p -> FilterChip(selected = p == pack, onClick = { pack = p },
                label = { Text(p, fontSize = 11.sp) }) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 13.dp)) {
            items(presets.filter { pack == "All" || it.pack == pack }.chunked(3)) { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 19.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { preset -> TemplateTile(preset, false) { select(preset) } }
                }
            }
        }
    }
}


private val swatches = listOf(
    rgb(0xFFFFFFFF), rgb(0xFFF6F0E4), rgb(0xFFB6BBB8), rgb(0xFF111315),
    rgb(0xFF342D38), rgb(0xFF263F58), rgb(0xFF92A9BA), rgb(0xFF446F6B),
    rgb(0xFFB0BBA7), rgb(0xFF87505B), rgb(0xFFF2C6BE), rgb(0xFFE8D9A9)
)

@Composable
private fun BackgroundPanel(doc: PoemDoc, update: (PoemDoc) -> Unit) {
    var editingSecond by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CREATE A BACKGROUND", fontSize = 10.sp, letterSpacing = 1.5.sp, color = Muted,
                modifier = Modifier.weight(1f))
            listOf("Start", "End").forEachIndexed { i, label ->
                val active = editingSecond == (i == 1)
                Text(label, modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (active) Ink else Pale).clickable { editingSecond = (i == 1) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 11.sp, color = if (active) OnInk else Ink)
                Spacer(Modifier.width(5.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            swatches.forEach { color ->
                Box(Modifier.size(33.dp).clip(CircleShape).background(Color(color))
                    .border(1.dp, Border, CircleShape).clickable {
                        update(doc.copy(card = if (editingSecond) doc.card.copy(color2 = color, background = "Custom")
                            else doc.card.copy(color1 = color, background = "Custom")))
                    })
            }
        }
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Texture", color = Ink, fontSize = 12.sp)
            Spacer(Modifier.width(14.dp))
            listOf("None", "Grain", "Lines").forEach { option ->
                FilterChip(selected = doc.card.texture == option,
                    onClick = { update(doc.copy(card = doc.card.copy(texture = option))) },
                    label = { Text(option, fontSize = 11.sp) }, modifier = Modifier.padding(end = 6.dp))
            }
        }
    }
}

@Composable
private fun ElementPanel(doc: PoemDoc, update: (PoemDoc) -> Unit, onAdded: (String) -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("poetry-elements", Context.MODE_PRIVATE) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var favorites by remember { mutableStateOf(preferences.getString("favorites", "")?.split(',')
        ?.filter { it.isNotBlank() }?.toSet() ?: emptySet()) }
    var recents by remember { mutableStateOf(preferences.getString("recents", "")?.split(',')
        ?.filter { it.isNotBlank() } ?: emptyList()) }
    fun add(entry: ElementEntry) {
        val element = Ornament(assetId = entry.id)
        update(doc.copy(card = doc.card.copy(elements = doc.card.elements + element)))
        onAdded(element.id)
        recents = (listOf(entry.id) + recents.filterNot { it == entry.id }).take(12)
        preferences.edit().putString("recents", recents.joinToString(",")).apply()
    }
    Column(Modifier.padding(horizontal = 15.dp, vertical = 8.dp)) {
        BasicTextField(value = query, onValueChange = { query = it }, singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp, color = Ink),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(Pale).padding(horizontal = 13.dp, vertical = 12.dp),
            decorationBox = { inner -> Box {
                if (query.isBlank()) Text("Search elements…", fontSize = 13.sp, color = Muted)
                inner()
            } })
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (listOf("All", "Recent", "Favourites") + elementEntries.map { it.category }.distinct()).forEach { item ->
                Text(item, fontSize = 10.sp, color = if (category == item) Color.White else Ink,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (category == item) Ink else Pale)
                        .clickable { category = item }
                        .padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        val matches = elementEntries.filter {
            (category == "All" || when (category) {
                "Recent" -> it.id in recents
                "Favourites" -> it.id in favorites
                else -> it.category == category
            }) && (it.name.contains(query, true) || it.category.contains(query, true))
        }
        if (matches.isEmpty()) Text("No matching elements", fontSize = 12.sp,
            color = Muted, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            matches.forEach { entry ->
                Column(Modifier.width(68.dp).clip(RoundedCornerShape(12.dp))
                    .background(LocalPoetryAppearance.current.white)
                    .clickable { add(entry) },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    VectorOrnament(entry.id, Modifier.size(33.dp).padding(top = 4.dp), Rose)
                    Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 9.sp, color = Muted)
                    IconButton(onClick = {
                        favorites = if (entry.id in favorites) favorites - entry.id else favorites + entry.id
                        preferences.edit().putString("favorites", favorites.joinToString(",")).apply()
                    }, modifier = Modifier.size(25.dp)) {
                        Icon(if (entry.id in favorites) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favourite ${entry.name}", tint = if (entry.id in favorites) Rose else Muted,
                            modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

/** Editorial, seamless text entry instead of stock boxed Material fields. */
@Composable
private fun TextPanel(doc: PoemDoc, update: (PoemDoc) -> Unit, onEdit: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 17.dp, vertical = 11.dp)) {
        SoftButton("Edit poem", onClick = onEdit, filled = false)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TYPEFACE", fontSize = 10.sp, color = Muted, letterSpacing = 1.2.sp)
            Spacer(Modifier.width(14.dp))
            listOf("Serif", "Sans", "Script", "Mono").forEach { font ->
                val selected = doc.card.fontName == font
                Text(font, modifier = Modifier.padding(end = 7.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (selected) Ink else Pale).clickable {
                        update(doc.copy(card = doc.card.copy(fontName = font)))
                    }.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (selected) OnInk else Ink, fontSize = 12.sp, fontFamily = resolveFont(font))
            }
        }
        Spacer(Modifier.height(11.dp))
        PoetryRangeControl("Font size", doc.card.fontSize, 12f..26f,
            "${doc.card.fontSize.roundToInt()} pt") {
            update(doc.copy(card = doc.card.copy(fontSize = it)))
        }
        PoetryRangeControl("Line height", doc.card.lineHeight, 1.1f..2.4f,
            "${(doc.card.lineHeight * 100).roundToInt()}%") {
            update(doc.copy(card = doc.card.copy(lineHeight = it)))
        }
        PoetryRangeControl("Text width", doc.card.textWidth, .35f.. .93f,
            "${(doc.card.textWidth * 100).roundToInt()}%") {
            update(doc.copy(card = doc.card.copy(textWidth = it,
                poemX = doc.card.poemX.coerceAtMost(1f - it))))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ALIGN", fontSize = 10.sp, color = Muted, letterSpacing = 1.2.sp)
            Spacer(Modifier.width(16.dp))
            listOf("Left", "Center", "Right").forEach { align ->
                val selected = doc.card.alignment == align
                Text(align, modifier = Modifier.padding(end = 7.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (selected) Ink else Pale).clickable {
                        update(doc.copy(card = doc.card.copy(alignment = align)))
                    }.padding(horizontal = 11.dp, vertical = 7.dp),
                    color = if (selected) OnInk else Ink, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SlidesPanel(slides: List<CardSlide>, index: Int, changeSlide: (Int) -> Unit,
    addSlide: () -> Unit, duplicate: () -> Unit, remove: () -> Unit,
    move: (Int) -> Unit, split: () -> Unit, applyStyleToAll: () -> Unit,
    exportBusy: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Poetry carousel · ${slides.size} slides")
            Text("Split poem", color = Rose, fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = split).padding(6.dp))
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            slides.forEachIndexed { i, sl ->
                Box(Modifier.width(67.dp).height(86.dp).clip(RoundedCornerShape(7.dp))
                    .background(Brush.verticalGradient(listOf(Color(sl.card.color1), Color(sl.card.color2))))
                    .border(if (i == index) 2.dp else 1.dp, if (i == index) Rose else Border, RoundedCornerShape(7.dp))
                    .clickable { changeSlide(i) }) {
                    Text(sl.bodyOverride?.take(65).orEmpty().ifBlank { "the next verse…" },
                        fontFamily = FontFamily.Serif, fontSize = 8.sp, lineHeight = 10.sp,
                        textAlign = TextAlign.Center, maxLines = 5, color = Color(sl.card.textColor),
                        modifier = Modifier.align(Alignment.Center).padding(8.dp))
                    Text("${i + 1}", fontSize = 9.sp, color = Color(sl.card.textColor),
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp))
                }
            }
            Box(Modifier.size(width = 65.dp, height = 86.dp)
                .border(1.dp, Border, RoundedCornerShape(7.dp)).clickable(onClick = addSlide),
                contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, contentDescription = "Add slide", tint = Rose) }
        }
        Spacer(Modifier.height(11.dp))
        Text("Apply current style to all pages", fontSize = 12.sp, color = Rose,
            modifier = Modifier.clickable { applyStyleToAll() }.padding(vertical = 6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Duplicate" to duplicate, "Move ←" to { move(-1) },
                "Move →" to { move(1) }, "Delete" to remove).forEach { (text, act) ->
                OutlinedButton(onClick = act, enabled = !(text == "Delete" && slides.size == 1) && !exportBusy,
                    shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 11.dp),
                    modifier = Modifier.height(35.dp)) { Text(text, fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun BackgroundMakerScreen(doc: PoemDoc, update: (PoemDoc) -> Unit, back: () -> Unit,
    save: () -> Unit) {
    var solid by remember { mutableStateOf(doc.card.color1 == doc.card.color2) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, contentDescription = "Back to studio") }
            Column(Modifier.weight(1f)) {
                Eyebrow("Background lab")
                Text("Make a background", fontSize = 19.sp, color = Ink, fontFamily = FontFamily.Serif)
            }
            SoftButton("Done", back)
        }
        HorizontalDivider(color = Border)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(23.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).height(250.dp).aspectRatio(9f / 16f)
                .background(Brush.verticalGradient(listOf(Color(doc.card.color1), Color(doc.card.color2))))
                .border(1.dp, Border), contentAlignment = Alignment.Center) {
                Text("your words, your world.", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                    color = Color(doc.card.textColor), fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(11.dp))
            }
            Spacer(Modifier.height(24.dp))
            Eyebrow("01 / The foundation")
            Spacer(Modifier.height(5.dp)); Heading("Choose your canvas", 26.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = solid, onClick = {
                    solid = true; update(doc.copy(card = doc.card.copy(color2 = doc.card.color1, background = "Custom")))
                }, label = { Text("Solid colour") })
                FilterChip(selected = !solid, onClick = { solid = false }, label = { Text("Gradient") })
            }
            Text("First colour", fontSize = 11.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                swatches.forEach { color -> Box(Modifier.size(34.dp).clip(CircleShape).background(Color(color))
                    .border(1.dp, Border, CircleShape).clickable {
                        update(doc.copy(card = doc.card.copy(color1 = color,
                            color2 = if (solid) color else doc.card.color2,
                            background = "Custom")))
                    }) }
            }
            if (!solid) {
                Spacer(Modifier.height(15.dp)); Text("Second colour", fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    swatches.forEach { color -> Box(Modifier.size(34.dp).clip(CircleShape).background(Color(color))
                        .border(1.dp, Border, CircleShape).clickable {
                            update(doc.copy(card = doc.card.copy(color2 = color, background = "Custom")))
                        }) }
                }
            }
            Spacer(Modifier.height(25.dp)); HorizontalDivider(color = Border)
            Spacer(Modifier.height(18.dp)); Eyebrow("02 / The atmosphere")
            Spacer(Modifier.height(5.dp)); Heading("Add a little texture", 26.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("None", "Grain", "Lines").forEach { t ->
                    FilterChip(selected = doc.card.texture == t,
                        onClick = { update(doc.copy(card = doc.card.copy(texture = t))) },
                        label = { Text(t, fontSize = 11.sp) })
                }
            }
            Spacer(Modifier.height(23.dp)); HorizontalDivider(color = Border)
            Spacer(Modifier.height(17.dp)); Eyebrow("03 / A finishing touch")
            Text("Your custom background is saved with this poetry card.", fontSize = 12.sp,
                lineHeight = 19.sp, color = Muted, modifier = Modifier.padding(top = 9.dp))
            Spacer(Modifier.height(16.dp))
            SoftButton("Save to my backgrounds", save, filled = false,
                modifier = Modifier.fillMaxWidth())

        }
    }
}

@Composable
private fun LayersPanel(doc: PoemDoc, update: (PoemDoc) -> Unit,
    selectedElementId: String? = null, onSelect: (String?) -> Unit) {
    val latest by rememberUpdatedState(doc)
    fun modify(id: String, action: (Ornament) -> Ornament) {
        val current = latest
        update(current.copy(card = current.card.copy(elements = current.card.elements.map {
            if (it.id == id) action(it) else it
        })))
    }
    fun shift(id: String, direction: Int) {
        val current = latest
        val list = current.card.elements.toMutableList()
        val i = list.indexOfFirst { it.id == id }; val target = i + direction
        if (i !in list.indices || target !in list.indices) return
        val moving = list[i]; list[i] = list[target]; list[target] = moving
        update(current.copy(card = current.card.copy(elements = list)))
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 7.dp)) {
        Eyebrow("Layers · front to back")
        Row(Modifier.fillMaxWidth().clickable { onSelect("poem") }.padding(vertical = 7.dp)) {
            Text("Aa", fontFamily = FontFamily.Serif, color = Ink, fontSize = 18.sp)
            Text("Poem text", fontSize = 12.sp, color = Ink, modifier = Modifier.padding(start = 12.dp))
        }
        doc.card.elements.asReversed().forEach { item ->
            Column {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    VectorOrnament(item.assetId, Modifier.size(22.dp).clickable { onSelect(item.id) }, Rose)
                    Text(item.name.ifEmpty { elementName(item.assetId) }, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = Ink,
                        modifier = Modifier.weight(1f).clickable { onSelect(item.id) }.padding(start = 6.dp))
                    Text(if (item.hidden) "Show" else "Hide", fontSize = 10.sp, color = Rose,
                        modifier = Modifier.clickable { modify(item.id) { it.copy(hidden = !it.hidden) } }.padding(4.dp))
                    Text(if (item.locked) "Unlock" else "Lock", fontSize = 10.sp, color = Rose,
                        modifier = Modifier.clickable { modify(item.id) { it.copy(locked = !it.locked) } }.padding(4.dp))
                    Icon(Icons.Default.DragHandle, contentDescription = "Drag to reorder ${item.name.ifEmpty { elementName(item.assetId) }}",
                        tint = Muted, modifier = Modifier.size(25.dp).pointerInput(item.id) {
                            var total = 0f
                            detectDragGestures(onDragEnd = { total = 0f }, onDragCancel = { total = 0f }) { change, move ->
                                change.consume(); total += move.y
                                if (kotlin.math.abs(total) > 33.dp.toPx()) {
                                    shift(item.id, if (total < 0f) 1 else -1)
                                    total = 0f
                                }
                            }
                        })
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Bring layer forward", tint = Ink,
                        modifier = Modifier.size(25.dp).clickable { shift(item.id, 1) }.padding(4.dp))
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Send layer backward", tint = Ink,
                        modifier = Modifier.size(25.dp).clickable { shift(item.id, -1) }.padding(4.dp))
                }
                if (item.id == selectedElementId) {
                    var newName by remember(item.id) { mutableStateOf(item.name) }
                    BasicTextField(newName, { newName = it; modify(item.id) { e -> e.copy(name = it) } },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(start = 27.dp, bottom = 5.dp)
                            .clip(RoundedCornerShape(9.dp)).background(Pale)
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        textStyle = TextStyle(fontSize = 12.sp, color = Ink),
                        decorationBox = { inner -> Box {
                            if (newName.isBlank()) Text("Layer name", fontSize = 12.sp, color = Muted)
                            inner()
                        } })
                }
            }
        }
        val selected = doc.card.elements.firstOrNull { it.id == selectedElementId }
        if (selected != null) {
            HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Selected layer")
                Spacer(Modifier.weight(1f))
                Text("Duplicate", color = Rose, fontSize = 11.sp,
                    modifier = Modifier.clickable {
                        val current = latest
                        update(current.copy(card = current.card.copy(elements =
                            current.card.elements + selected.copy(id = UUID.randomUUID().toString(),
                                x = (selected.x + .04f).coerceAtMost(.90f), y = (selected.y + .04f).coerceAtMost(.93f)))))
                    }.padding(6.dp))
                Text("Delete", color = Rose, fontSize = 11.sp,
                    modifier = Modifier.clickable {
                        val current = latest
                        update(current.copy(card = current.card.copy(elements =
                            current.card.elements.filterNot { it.id == selected.id })))
                        onSelect(null)
                    }.padding(6.dp))
            }
            PoetryRangeControl("Size", selected.size, .4f..3.5f,
                "%.1f×".format(java.util.Locale.US, selected.size)) { n ->
                modify(selected.id) { it.copy(size = n) }
            }
            PoetryRangeControl("Rotate", selected.rotation, -180f..180f,
                "${selected.rotation.roundToInt()}°") { n ->
                modify(selected.id) { it.copy(rotation = n) }
            }
            PoetryRangeControl("Opacity", selected.opacity, .1f..1f,
                "${(selected.opacity * 100).roundToInt()}%") { n ->
                modify(selected.id) { it.copy(opacity = n) }
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Colour", fontSize = 11.sp, color = Muted)
                val swatches = listOf<Int?>(null, rgb(0xFF33383D), rgb(0xFF778C7B), rgb(0xFF8B5265),
                    rgb(0xFFAF894E), rgb(0xFF6688A8))
                swatches.forEach { swatch ->
                    val chosen = selected.tint == swatch
                    Box(Modifier.size(23.dp).clip(CircleShape)
                        .background(Color(swatch ?: doc.card.textColor))
                        .border(if (chosen) 2.dp else 1.dp,
                            if (chosen) Color(0xFF1492C3) else Border, CircleShape)
                        .clickable { modify(selected.id) { it.copy(tint = swatch) } })
                }
            }
        }
    }
}
@Composable
private fun SettingsScreen(back: () -> Unit, count: Int, currentTheme: String, onTheme: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.width(11.dp))
            Heading("Preferences", 29.sp)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 23.dp, vertical = 16.dp)) {
            Eyebrow("Your space, your way")
            Spacer(Modifier.height(10.dp))
            Heading("Make it feel like yours.", 34.sp)
            Spacer(Modifier.height(12.dp))
            Text("Choose an interface theme. Your card designs will stay as you made them.",
                fontSize = 13.sp, color = Muted, lineHeight = 20.sp)
            Spacer(Modifier.height(23.dp))
            Heading("Appearance", 25.sp)
            Spacer(Modifier.height(10.dp))
            appearances.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { t ->
                        val selected = t.id == currentTheme
                        Column(Modifier.weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(if (selected) 2.dp else 1.dp,
                                if (selected) Rose else Border, RoundedCornerShape(16.dp))
                            .clickable { onTheme(t.id) }.padding(9.dp)) {
                            Box(Modifier.fillMaxWidth().height(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(t.paper), contentAlignment = Alignment.Center) {
                                Text("poetry.", color = t.ink, fontFamily = FontFamily.Serif, fontSize = 20.sp)
                                Text("— a line to remember", color = t.accent, fontSize = 8.sp,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(9.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(t.label + if (selected) " ✓" else "", fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold, color = Ink)
                            Text(t.note, fontSize = 10.sp, color = Muted)
                        }
                    }
                }
                Spacer(Modifier.height(11.dp))
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Border)
            Spacer(Modifier.height(20.dp))
            Heading("Private by design", 25.sp)
            Spacer(Modifier.height(10.dp))
            Text("$count poems stored on this device. No account, server or connection required.",
                fontSize = 13.sp, color = Muted, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Text("Offline writing · portrait PNG export", fontSize = 13.sp, color = Ink)
        }
    }
}

/** One export entry-point prevents a duplicate Save/Share/Export All control maze. */
@Composable
private fun ExportDialog(slideCount: Int, busy: Boolean, dismiss: () -> Unit,
    perform: (share: Boolean, all: Boolean, pixels: Int) -> Unit) {
    var pixels by remember { mutableIntStateOf(1080) }
    Dialog(onDismissRequest = dismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = Paper) {
            Column(Modifier.fillMaxWidth().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Export poetry", fontFamily = FontFamily.Serif,
                    fontSize = 25.sp, color = Ink)
                Text("PNG quality", fontSize = 12.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1080 to "HD · 1080p", 2160 to "4K · 2160p").forEach { (size, label) ->
                        val selected = pixels == size
                        OutlinedButton(onClick = { pixels = size }, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) SoftRose else Color.Transparent)) {
                            Text(label, fontSize = 11.sp, color = if (selected) Rose else Ink,
                                maxLines = 1)
                        }
                    }
                }
                Button(onClick = { perform(false, false, pixels) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text("Save this card") }
                if (slideCount > 1) {
                    OutlinedButton(onClick = { perform(false, true, pixels) }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) { Text("Save all $slideCount slides") }
                }
                OutlinedButton(onClick = { perform(true, false, pixels) }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text("Share this card") }
                if (slideCount > 1) {
                    OutlinedButton(onClick = { perform(true, true, pixels) }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) { Text("Share all slides") }
                }
                TextButton(onClick = dismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel", color = Muted)
                }
            }
        }
    }
}

/** Android MediaStore PNG rendering. Draw at logical 1080×1920, with the canvas scaled to
 *  the export bitmap. Vectors and text are rasterized at final resolution, not upscaled screenshots. */
private object CardExporter {
    private fun drawVector(context: Context, canvas: AndroidCanvas, assetId: String,
        x: Float, y: Float, px: Float, tint: Int, opacity: Float) {
        val drawable = context.getDrawable(elementResource(assetId))?.mutate() ?: return
        drawable.setTint(tint)
        drawable.alpha = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        val left = x.roundToInt(); val top = y.roundToInt(); val edge = px.roundToInt().coerceAtLeast(1)
        drawable.setBounds(left, top, left + edge, top + edge)
        drawable.draw(canvas)
    }

    fun export(context: Context, doc: PoemDoc, outputWidth: Int = 1080): android.net.Uri {
        require(outputWidth == 1080 || outputWidth == 2160) { "Unsupported export resolution" }
        val s = doc.card
        val w = 1080; val h = 1920
        val scale = outputWidth / w.toFloat()
        val bmp = Bitmap.createBitmap(outputWidth, (h * scale).toInt(), Bitmap.Config.ARGB_8888)
        try {
            val canvas = AndroidCanvas(bmp)
            canvas.scale(scale, scale)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), s.color1, s.color2, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
            if (s.texture == "Grain") {
                val random = java.util.Random(27L)
                paint.color = android.graphics.Color.argb(22, 255, 255, 255)
                repeat(9500) { canvas.drawCircle(random.nextInt(w).toFloat(), random.nextInt(h).toFloat(), 1.1f, paint) }
            } else if (s.texture == "Lines") {
                paint.color = android.graphics.Color.argb(18, 255, 255, 255)
                paint.strokeWidth = 2f
                repeat(35) { i -> val y = ((i + 1) * h / 36).toFloat(); canvas.drawLine(0f, y, w.toFloat(), y, paint) }
            }
            val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = s.textColor
                typeface = Typeface.create(when (s.fontName) {
                    "Sans" -> "sans-serif"; "Mono" -> "monospace"; "Script" -> "cursive"; else -> "serif"
                }, Typeface.NORMAL)
                textSize = s.fontSize * 3.35f
            }
            val areaWidth = (w * s.textWidth.coerceIn(.3f, .95f)).toInt()
            val alignment = when (s.alignment) {
                "Left" -> Layout.Alignment.ALIGN_NORMAL
                "Right" -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_CENTER
            }
            var y = s.poemY * h
            val x = s.poemX * w
            fun write(str: String, fontScale: Float, afterGap: Float) {
                if (str.isBlank()) return
                val oldSize = text.textSize
                text.textSize = oldSize * fontScale
                val layout = StaticLayout.Builder.obtain(str, 0, str.length, text, areaWidth)
                    .setAlignment(alignment).setIncludePad(false)
                    .setLineSpacing(0f, s.lineHeight / 1.3f).build()
                if (y + layout.height > h * .95f) {
                    throw IllegalArgumentException("Text overflows this card. Try Slides → Split poem or reduce font size.")
                }
                canvas.save(); canvas.translate(x, y); layout.draw(canvas); canvas.restore()
                y += layout.height + afterGap
                text.textSize = oldSize
            }
            templateAccents(s.decor, s.textColor).forEach { accent ->
                drawVector(context, canvas, accent.assetId,
                    accent.x * w, accent.y * h, accent.px, accent.color, accent.alpha)
            }
            write(doc.title, 1.18f, 46f)
            write(doc.body, 1f, 49f)
            write(if (doc.author.isBlank()) "" else "— ${doc.author}", .60f, 0f)
            s.elements.filterNot { it.hidden }.forEach { el ->
                val sizePx = 78f * el.size
                canvas.save()
                canvas.rotate(el.rotation,
                    el.x * w + sizePx / 2f, el.y * h + sizePx / 2f)
                drawVector(context, canvas, el.assetId,
                    el.x * w, el.y * h, sizePx, el.tint ?: s.textColor, el.opacity)
                canvas.restore()
            }
            val name = "poetry-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(7)}.png"
            // Android 10+ can save to Gallery without broad storage permissions.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Poetry")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Cannot create media file")
                try {
                    resolver.openOutputStream(uri)?.use { stream ->
                        check(bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG encoding failed" }
                    } ?: error("Cannot open image output")
                    values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
                return uri
            }
            // Android 8/9: use app-owned external storage and FileProvider to avoid
            // broad WRITE_EXTERNAL_STORAGE permissions. Exported files remain shareable.
            // They are not promised to appear in the system Gallery on API 26-28.
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: context.filesDir, "Poetry")
            check(dir.isDirectory || dir.mkdirs()) { "Cannot create Poetry export directory" }
            val file = File(dir, name)
            try {
                file.outputStream().use { stream ->
                    check(bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG encoding failed" }
                }
            } catch (e: Exception) { file.delete(); throw e }
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } finally {
            // Especially important for 2160×3840 images (~32 MB each).
            bmp.recycle()
        }
    }
}
