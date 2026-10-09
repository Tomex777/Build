package com.tomex777.annie

import org.json.JSONObject

/**
 * First-party registry for structured script messages.
 *
 * Scripts provide data only. Annie owns this registry and the native Compose renderers that
 * correspond to each kind, so adding a message type never exposes arbitrary UI execution.
 */
internal enum class ScriptMessageKind {
    TEXT,
    CODE,
    COPY,
    IMAGE,
    MUSIC,
    VIDEO,
    FILE,
    SEASON_LIST,
    EPISODE_LIST,
    CONTINUE_WATCHING,
    MATCHES,
    OPTIONS,
    BROWSER,
    PROGRESS,
    FORM,
    UNKNOWN,
}

internal data class NativeMessageType(
    val wireName: String,
    val kind: ScriptMessageKind,
)

internal object MessageTypeRegistry {
    private val registered = linkedMapOf(
        "text" to NativeMessageType("text", ScriptMessageKind.TEXT),
        "code" to NativeMessageType("code", ScriptMessageKind.CODE),
        "copy" to NativeMessageType("copy", ScriptMessageKind.COPY),
        "image" to NativeMessageType("image", ScriptMessageKind.IMAGE),
        "music" to NativeMessageType("music", ScriptMessageKind.MUSIC),
        "video" to NativeMessageType("video", ScriptMessageKind.VIDEO),
        "file" to NativeMessageType("file", ScriptMessageKind.FILE),
        "season_list" to NativeMessageType("season_list", ScriptMessageKind.SEASON_LIST),
        "episode_list" to NativeMessageType("episode_list", ScriptMessageKind.EPISODE_LIST),
        "continue_watching" to NativeMessageType("continue_watching", ScriptMessageKind.CONTINUE_WATCHING),
        "matches" to NativeMessageType("matches", ScriptMessageKind.MATCHES),
        "options" to NativeMessageType("options", ScriptMessageKind.OPTIONS),
        "browser" to NativeMessageType("browser", ScriptMessageKind.BROWSER),
        "progress" to NativeMessageType("progress", ScriptMessageKind.PROGRESS),
        "form" to NativeMessageType("form", ScriptMessageKind.FORM),
    )

    fun resolve(payload: JSONObject): NativeMessageType =
        registered[payload.optString("type").trim().lowercase()]
            ?: NativeMessageType(payload.optString("type").trim().lowercase(), ScriptMessageKind.UNKNOWN)

    fun supportedWireNames(): Set<String> = registered.keys.toSet()
}

internal object ScriptVideoLayout {
    const val DEFAULT_ASPECT_RATIO = 16f / 9f

    fun aspectRatio(payload: JSONObject): Float {
        val explicit = payload.optDouble("aspectRatio", Double.NaN)
        if (explicit.isFinite() && explicit > 0.0) return explicit.toFloat().coerceIn(0.35f, 3.0f)

        val width = payload.optDouble("width", Double.NaN)
        val height = payload.optDouble("height", Double.NaN)
        if (width.isFinite() && height.isFinite() && width > 0.0 && height > 0.0) {
            return (width / height).toFloat().coerceIn(0.35f, 3.0f)
        }
        return DEFAULT_ASPECT_RATIO
    }

    fun previewHeightDp(containerWidthDp: Float, payload: JSONObject): Float {
        if (containerWidthDp <= 0f) return 190f
        return (containerWidthDp / aspectRatio(payload)).coerceIn(150f, 360f)
    }
}

