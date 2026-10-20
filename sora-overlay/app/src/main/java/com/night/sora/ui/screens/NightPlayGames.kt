package com.night.sora.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import com.night.sora.ui.theme.SoraBg
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.net.URLConnection

/**
 * Offline ports of NIGHT Play's existing authored games, NOT remotely hosted
 * previews. Each directory is bundled under assets/night-play so its scores,
 * full story manuscript, and JavaScript work without a connection.
 *
 * The online party-room games are deliberately excluded: an offline WebView
 * cannot honestly claim to provide live opponents or shared accounts.
 */
data class NightPlayGame(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val url: String,
    val icon: ImageVector,
)

val soraNightStory = NightPlayGame(
    "last-call", "The Last Call", "Interactive story",
    "A complete branching novel. Your choices, memories and hearts matter.",
    "/stories/index.html", Icons.Rounded.AutoStories,
)

val soraNightGames = listOf(
    NightPlayGame("wing-hop", "Wing Hop", "Arcade", "Flap through obstacles.", "/arcade/index.html?game=bird", Icons.Rounded.Flight),
    NightPlayGame("block-drop", "Block Drop", "Arcade", "Stack falling blocks.", "/arcade/index.html?game=blocks", Icons.Rounded.ViewModule),
    NightPlayGame("minesweeper", "Minesweeper", "Puzzle", "Find every safe square.", "/arcade/index.html?game=mines", Icons.Rounded.GridOn),
    NightPlayGame("maze-chase", "Maze Chase", "Arcade", "Escape the ghosts.", "/arcade/index.html?game=maze", Icons.Rounded.Route),
    NightPlayGame("fruit-slice", "Fruit Slice", "Action", "Slice fruit. Dodge bombs.", "/action/index.html?game=slice", Icons.Rounded.ContentCut),
    NightPlayGame("sky-jumper", "Sky Jumper", "Action", "Climb, land, and keep going.", "/action/index.html?game=jump", Icons.Rounded.North),
    NightPlayGame("endless-runner", "Endless Runner", "Action", "Jump, slide and survive.", "/action/index.html?game=runner", Icons.Rounded.DirectionsRun),
    NightPlayGame("space-shooter", "Space Shooter", "Action", "Dodge waves of enemies.", "/action/index.html?game=shooter", Icons.Rounded.RocketLaunch),
    NightPlayGame("brick-breaker", "Brick Breaker", "Action", "Keep the ball in play.", "/action/index.html?game=bricks", Icons.Rounded.Extension),
    NightPlayGame("sudoku", "Sudoku", "Puzzle", "Solve complete number grids.", "/puzzles/index.html?game=sudoku", Icons.Rounded.Apps),
    NightPlayGame("connect-four", "Connect Four", "Strategy", "Four discs in a row.", "/puzzles/index.html?game=connect", Icons.Rounded.Filter4),
    NightPlayGame("chess", "Chess", "Strategy", "A full match against the computer.", "/strategy/index.html", Icons.Rounded.SportsEsports),
    NightPlayGame("draughts", "Draughts", "Strategy", "Capture pieces and crown kings.", "/club/index.html?game=checkers", Icons.Rounded.CheckBoxOutlineBlank),
    NightPlayGame("whot", "WHOT", "Cards", "Traditional Nigerian card battles.", "/club/index.html?game=whot", Icons.Rounded.Style),
    NightPlayGame("colour-clash", "Colour Clash", "Cards", "Match colours and special cards.", "/club/index.html?game=colors", Icons.Rounded.Palette),
    NightPlayGame("naija-ludo", "Nigerian Ludo", "Strategy", "Race four tokens home.", "/club/index.html?game=ludo", Icons.Rounded.Casino),
    NightPlayGame("snakes-ladders", "Snakes & Ladders", "Board", "Roll your way to the finish.", "/club/index.html?game=snakes", Icons.Rounded.Stairs),
    NightPlayGame("night-estates", "NIGHT Estates", "Board", "Buy properties and collect rent.", "/club/index.html?game=estate", Icons.Rounded.House),

    NightPlayGame("jigsaw", "Jigsaw Puzzle", "Puzzle", "Piece together a picture.", "/jigsaw/index.html", Icons.Rounded.Panorama),
    NightPlayGame("piano-tiles", "Piano Tiles", "Rhythm", "Play the falling notes.", "/piano-tiles/index.html", Icons.Rounded.Piano),
    NightPlayGame("sling-siege", "Sling Siege", "Action", "Bring down six fortresses.", "/sling-siege/index.html", Icons.Rounded.Adjust),
    NightPlayGame("lights-out", "Lights Out", "Puzzle", "Switch off every light.", "/brain-lab/index.html?game=lights", Icons.Rounded.Lightbulb),
    NightPlayGame("sliding-puzzle", "Sliding Puzzle", "Puzzle", "Reorder the sliding tiles.", "/brain-lab/index.html?game=slide", Icons.Rounded.GridView),
    NightPlayGame("simon-says", "Simon Says", "Arcade", "Repeat each colour pattern.", "/brain-lab/index.html?game=simon", Icons.Rounded.TouchApp),
)
private const val ASSET_ORIGIN = "appassets.androidplatform.net"

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun response(context: Context, request: WebResourceRequest): WebResourceResponse? {
    val uri = request.url
    if (uri.scheme != "https" || uri.host != ASSET_ORIGIN) return null
    val path = uri.path.orEmpty().removePrefix("/")
    // Reject decoded traversal as well as backslashes. No WebView route may
    // access files outside the shipped, explicitly selected NIGHT Play subset.
    val pieces = path.split('/')
    if (pieces.any { it == ".." || it == "." || '\\' in it } || path.isBlank()) {
        return missing()
    }
    val relative = "night-play/" + pieces.joinToString("/")
    val mime = when {
        relative.endsWith(".html") -> "text/html"
        relative.endsWith(".js") || relative.endsWith(".mjs") -> "text/javascript"
        relative.endsWith(".css") -> "text/css"
        relative.endsWith(".json") -> "application/json"
        relative.endsWith(".svg") -> "image/svg+xml"
        relative.endsWith(".png") -> "image/png"
        relative.endsWith(".jpg") || relative.endsWith(".jpeg") -> "image/jpeg"
        relative.endsWith(".webp") -> "image/webp"
        relative.endsWith(".woff2") -> "font/woff2"
        else -> URLConnection.guessContentTypeFromName(relative) ?: "application/octet-stream"
    }
    return try {
        WebResourceResponse(
            mime,
            if (mime.startsWith("text/") || mime == "application/json" || mime == "image/svg+xml") "UTF-8" else null,
            context.assets.open(relative)
        )
    } catch (_: FileNotFoundException) { missing() }
      catch (_: java.io.IOException) { missing() }
}

private fun missing() = WebResourceResponse(
    "text/plain", "UTF-8", 404, "Not Found",
    mapOf("Cache-Control" to "no-store"),
    ByteArrayInputStream("Offline game resource not found".toByteArray())
)

/** Fullscreen local HTML game, with one in-game back control and Android Back. */
@Composable
fun NightPlayGameScreen(gameId: String, onClose: () -> Unit) {
    val game = (soraNightGames + soraNightStory).firstOrNull { it.id == gameId }
    if (game == null) {
        BackHandler { onClose() }
        return
    }
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        val previousFlags = activity?.window?.attributes?.flags ?: 0
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        onDispose {
            if (previousFlags and WindowManager.LayoutParams.FLAG_FULLSCREEN == 0) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
        }
    }

    val web = remember(gameId, context) {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.rgb(18, 18, 17))
            isVerticalScrollBarEnabled = false
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                mediaPlaybackRequiresUserGesture = true
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                    if (message.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                        android.util.Log.e(
                            "SoraNightPlay",
                            "${message.sourceId()}:${message.lineNumber()}: ${message.message()}"
                        )
                    }
                    return super.onConsoleMessage(message)
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // The manuscript already has its own approved dark reader.
                    // Preserve it; unify only the lighter standalone game shells.
                    if (url?.startsWith("https://$ASSET_ORIGIN/") == true &&
                        !url.contains("/stories/") && !url.contains("/sling-siege/")) {
                        view?.evaluateJavascript(
                            "(function(){if(document.getElementById('sora-game-theme'))return;" +
                                "var e=document.createElement('link');e.id='sora-game-theme';" +
                                "e.rel='stylesheet';e.href='/sora-game-theme.css';" +
                                "document.head.appendChild(e)})()", null)
                    }
                }
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? =
                    response(context, request)

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val uri = request.url
                    if (uri.host == ASSET_ORIGIN) {
                        if (uri.path in listOf("/", "/games", "/dashboard", "/index.html")) {
                            post { onClose() }
                            return true
                        }
                        return false
                    }
                    if (uri.scheme == "https" || uri.scheme == "http") {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    }
                    return true
                }
            }
            loadUrl("https://$ASSET_ORIGIN" + game.url)
        }
    }
    BackHandler { if (web.canGoBack()) web.goBack() else onClose() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { web.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { web.onResume() }
    DisposableEffect(web) {
        onDispose {
            web.stopLoading()
            web.loadUrl("about:blank")
            web.destroy()
        }
    }
    AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
}
