/*
 * extensions-lib 17 compatibility models.
 * Licensed under Apache-2.0.
 */
package eu.kanade.tachiyomi.animesource.model

import fi.iki.elonen.NanoHTTPD

open class HttpServer : NanoHTTPD(0) {
    val url: String
        get() = "http://localhost:$listeningPort"

    fun isRunning(): Boolean = running

    @Volatile
    private var running = false

    override fun start() {
        super.start()
        running = true
    }

    override fun stop() {
        super.stop()
        running = false
    }

    companion object {
        const val PLACEHOLDER_URL = "http://localhost:1"
    }
}

open class ThumbnailInfo(
    val tileInfo: List<TileInfo>,
    val imageTileUrls: List<String>,
)

data class TileInfo(
    val imageIndex: Int,
    val timeMs: Long,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)
