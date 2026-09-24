package com.example.whatsapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.whatsapp.presentation.chatscreen.NightChatMediaItem
import com.example.whatsapp.presentation.chatscreen.NightMediaViewerScreen
import com.example.whatsapp.ui.theme.WhatsappTheme
import java.io.File
import java.io.FileOutputStream

private const val NIGHT_PREVIEW_VIDEO_PATH = "night.preview.videoPath"
private const val NIGHT_PREVIEW_RENDERER = "night.preview.renderer"

private class PlatformTextureVideoView(
    context: Context,
    private val videoFile: File,
) : TextureView(context) {
    private var mediaPlayer: MediaPlayer? = null
    private var videoSurface: Surface? = null

    init {
        surfaceTextureListener =
            object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surfaceTexture: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) {
                    startPlayback(surfaceTexture, width, height)
                }

                override fun onSurfaceTextureSizeChanged(
                    surfaceTexture: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) = Unit

                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                    releasePlayer()
                    return true
                }

                override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
            }
    }

    private fun startPlayback(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int,
    ) {
        releasePlayer()
        val outputSurface = Surface(surfaceTexture)
        val newPlayer = MediaPlayer()
        videoSurface = outputSurface
        mediaPlayer = newPlayer

        newPlayer.setOnPreparedListener { prepared ->
            prepared.isLooping = true
            Log.i(
                "NightVideoProbe",
                "Android MediaPlayer prepared TextureView fixture (viewSize=${width}x${height}).",
            )
            prepared.start()
        }
        newPlayer.setOnErrorListener { _, what, extra ->
            Log.e(
                "NightVideoProbe",
                "Android TextureView MediaPlayer failed (what=$what, extra=$extra).",
            )
            true
        }

        runCatching {
            newPlayer.setDataSource(context, Uri.fromFile(videoFile))
            newPlayer.setSurface(outputSurface)
            newPlayer.prepareAsync()
        }.onFailure { failure ->
            Log.e(
                "NightVideoProbe",
                "Android TextureView MediaPlayer setup failed.",
                failure,
            )
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.let { player ->
            player.setOnPreparedListener(null)
            player.setOnErrorListener(null)
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        mediaPlayer = null
        videoSurface?.let { surface -> runCatching { surface.release() } }
        videoSurface = null
    }

    override fun onDetachedFromWindow() {
        releasePlayer()
        super.onDetachedFromWindow()
    }
}

class MediaViewerPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val requestedVideo =
            intent.getStringExtra(NIGHT_PREVIEW_VIDEO_PATH)
                ?.let(::File)
                ?.takeIf { it.isFile && it.length() > 0L }

        val fallback = File(cacheDir, "night-player-preview.jpg")
        if (requestedVideo == null && !fallback.exists()) {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.bilal)
            FileOutputStream(fallback).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)
            }
            bitmap.recycle()
        }

        val items =
            if (requestedVideo != null) {
                listOf(
                    NightChatMediaItem(
                        id = "preview-real-video-1",
                        localPath = requestedVideo.absolutePath,
                        mimeType = "video/mp4",
                        caption = "Night real video playback fixture",
                        sender = "Night Video 1",
                    ),
                    NightChatMediaItem(
                        id = "preview-real-video-2",
                        localPath = requestedVideo.absolutePath,
                        mimeType = "video/mp4",
                        caption = "Night real video playback fixture",
                        sender = "Night Video 2",
                    ),
                )
            } else {
                listOf(
                    NightChatMediaItem(
                        id = "preview-video-1",
                        localPath = fallback.absolutePath,
                        mimeType = "video/mp4",
                        caption = "Night full-screen video player",
                        time = "20:31",
                        sender = "Night",
                        duration = "0:10",
                    ),
                    NightChatMediaItem(
                        id = "preview-image-1",
                        localPath = fallback.absolutePath,
                        mimeType = "image/jpeg",
                        caption = "Swipe left and right through chat media.",
                        time = "20:32",
                        sender = "You",
                    ),
                )
            }

        val rendererMode = intent.getStringExtra(NIGHT_PREVIEW_RENDERER)
        val platformVideo = requestedVideo?.takeIf { rendererMode == "platform" }
        val platformTextureVideo =
            requestedVideo?.takeIf { rendererMode == "platform-texture" }

        setContent {
            WhatsappTheme(darkTheme = true) {
                when {
                    platformTextureVideo != null -> {
                        AndroidView(
                            factory = { viewContext ->
                                PlatformTextureVideoView(viewContext, platformTextureVideo)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    platformVideo != null -> {
                        AndroidView(
                            factory = { viewContext ->
                                VideoView(viewContext).apply {
                                    setOnPreparedListener { mediaPlayer ->
                                        mediaPlayer.isLooping = true
                                        Log.i("NightVideoProbe", "Android VideoView prepared fixture.")
                                        start()
                                    }
                                    setOnErrorListener { _, what, extra ->
                                        Log.e(
                                            "NightVideoProbe",
                                            "Android VideoView failed (what=$what, extra=$extra).",
                                        )
                                        true
                                    }
                                    setVideoURI(Uri.fromFile(platformVideo))
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        NightMediaViewerScreen(
                            items = items,
                            initialIndex = 0,
                            onBack = { finish() },
                            onEdit = { item ->
                                startActivity(
                                    Intent(
                                        this@MediaViewerPreviewActivity,
                                        MediaEditorPreviewActivity::class.java,
                                    ).putExtra(
                                        NIGHT_PREVIEW_VIDEO_PATH,
                                        item.localPath,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
