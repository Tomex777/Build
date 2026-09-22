package com.tomex.securerenderlab

import android.media.MediaCodecList
import android.media.MediaDrm
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import java.nio.charset.StandardCharsets

@androidx.annotation.OptIn(UnstableApi::class)
class DrmLabActivity : BaseCaptureActivity() {

    companion object {
        private const val CLEARKEY_MPD =
            "https://media.axprod.net/TestVectors/v7-MultiDRM-SingleKey/Manifest_1080p_ClearKey.mpd"

        private const val CLEARKEY_RESPONSE =
            "{\"keys\":[{\"kty\":\"oct\",\"kid\":\"nrQFDeRLSAKTLifXUIPiZg\",\"k\":\"FmY0xnWCPCNaSpRG-tUuTQ\"}],\"type\":\"temporary\"}"
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var playbackStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (scroll, root) = screenScroll()
        root.addView(heading("DRM + protected-path inspector"))
        root.addView(
            bodyText(
                "This screen separates two ideas that are often mixed together: DRM key negotiation and protected rendering. ClearKey gives us a legal public test stream for real MediaDrm playback; Widevine diagnostics show what security level the device reports."
            )
        )

        root.addCard(drmInfoCard())
        root.addCard(clearKeyPlayerCard())

        val meaning = card().apply {
            addView(heading("Interpret the result", 18f))
            addView(
                bodyText(
                    "ClearKey is DRM, but it is primarily useful here to expose the license/decryption path. It does not prove hardware-secure Widevine L1 rendering. If the ClearKey video appears in an observer frame, that is expected on many devices and demonstrates that 'DRM protected' and 'uncapturable secure surface' are not synonyms."
                )
            )
        }
        root.addCard(meaning)
        root.addCard(capturePanel())

        setContentView(scroll)
    }

    private fun drmInfoCard(): LinearLayout {
        val widevineSupported = MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID)
        val clearKeySupported = MediaDrm.isCryptoSchemeSupported(C.CLEARKEY_UUID)

        return card().apply {
            addView(heading("Device DRM inspector", 18f))
            addView(
                bodyText(
                    "Widevine supported: " + yesNo(widevineSupported) +
                        "\nClearKey supported: " + yesNo(clearKeySupported) +
                        "\n\n" + widevineDetails()
                )
            )

            addView(
                bodyText(
                    "\nSecure video decoders reported by MediaCodecList:\n" +
                        secureDecoderSummary()
                )
            )
        }
    }

    private fun clearKeyPlayerCard(): LinearLayout =
        card().apply {
            addView(heading("Public ClearKey playback", 18f))
            addView(
                bodyText(
                    "Source: DASH-IF/Axinom public ClearKey test vector. The key response is bundled only for that public test content."
                )
            )

            playerView = PlayerView(this@DrmLabActivity).apply {
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
            addView(
                playerView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(240)
                )
            )

            addView(
                labButton("Start ClearKey test") {
                    startClearKeyPlayback()
                }
            )

            addView(
                labButton("Stop playback") {
                    stopPlayback()
                    playbackStatus.text = "Playback stopped."
                }
            )

            playbackStatus = statusText("Player idle.")
            addView(playbackStatus)
        }

    private fun startClearKeyPlayback() {
        stopPlayback()

        playbackStatus.text = "Creating ClearKey MediaDrm session…"

        try {
            val localKeys =
                LocalMediaDrmCallback(
                    CLEARKEY_RESPONSE.toByteArray(StandardCharsets.UTF_8)
                )

            val drmSessionManager =
                DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(
                        C.CLEARKEY_UUID,
                        FrameworkMediaDrm.DEFAULT_PROVIDER
                    )
                    .build(localKeys)

            val mediaSourceFactory =
                DefaultMediaSourceFactory(this)
                    .setDrmSessionManagerProvider { drmSessionManager }

            val exoPlayer =
                ExoPlayer.Builder(this)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build()

            player = exoPlayer
            playerView.player = exoPlayer

            exoPlayer.addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        playbackStatus.text =
                            when (playbackState) {
                                Player.STATE_BUFFERING ->
                                    "MediaDrm session created · buffering encrypted DASH…"
                                Player.STATE_READY ->
                                    "READY · encrypted samples are being decrypted by ClearKey."
                                Player.STATE_ENDED ->
                                    "Playback ended."
                                else ->
                                    "Player state: " + playbackState
                            }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        playbackStatus.text =
                            "Playback error " + error.errorCodeName +
                                "\n" + (error.message ?: error.javaClass.simpleName)
                    }
                }
            )

            val item =
                MediaItem.Builder()
                    .setUri(CLEARKEY_MPD)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()

            exoPlayer.setMediaItem(item)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (t: Throwable) {
            playbackStatus.text =
                "Could not start ClearKey playback: " +
                    t.javaClass.simpleName + ": " + (t.message ?: "unknown error")
        }
    }

    private fun stopPlayback() {
        playerView.player = null
        player?.release()
        player = null
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    private fun widevineDetails(): String {
        if (!MediaDrm.isCryptoSchemeSupported(C.WIDEVINE_UUID)) {
            return "Widevine plugin is not available on this device."
        }

        val drm =
            try {
                MediaDrm(C.WIDEVINE_UUID)
            } catch (t: Throwable) {
                return "Could not create MediaDrm: " +
                    t.javaClass.simpleName + ": " + (t.message ?: "unknown error")
            }

        return try {
            val vendor = safeProperty(drm, MediaDrm.PROPERTY_VENDOR)
            val version = safeProperty(drm, MediaDrm.PROPERTY_VERSION)
            val description = safeProperty(drm, MediaDrm.PROPERTY_DESCRIPTION)
            val algorithms = safeProperty(drm, MediaDrm.PROPERTY_ALGORITHMS)

            var security = "Session security level: unavailable on API < 28"
            if (Build.VERSION.SDK_INT >= 28) {
                security =
                    try {
                        val session = drm.openSession()
                        try {
                            "Session security level: " +
                                securityLevelName(drm.getSecurityLevel(session))
                        } finally {
                            drm.closeSession(session)
                        }
                    } catch (t: Throwable) {
                        "Session security level: could not open a session (" +
                            t.javaClass.simpleName + ")"
                    }
            }

            "Widevine vendor: " + vendor +
                "\nPlugin version: " + version +
                "\nDescription: " + description +
                "\nAlgorithms: " + algorithms +
                "\n" + security
        } finally {
            @Suppress("DEPRECATION")
            drm.release()
        }
    }

    private fun safeProperty(drm: MediaDrm, name: String): String =
        try {
            drm.getPropertyString(name)
        } catch (_: Throwable) {
            "unavailable"
        }

    private fun securityLevelName(level: Int): String =
        when (level) {
            MediaDrm.SECURITY_LEVEL_HW_SECURE_ALL -> "HW_SECURE_ALL"
            MediaDrm.SECURITY_LEVEL_HW_SECURE_DECODE -> "HW_SECURE_DECODE"
            MediaDrm.SECURITY_LEVEL_HW_SECURE_CRYPTO -> "HW_SECURE_CRYPTO"
            MediaDrm.SECURITY_LEVEL_SW_SECURE_DECODE -> "SW_SECURE_DECODE"
            MediaDrm.SECURITY_LEVEL_SW_SECURE_CRYPTO -> "SW_SECURE_CRYPTO"
            else -> "UNKNOWN (" + level + ")"
        }

    private fun secureDecoderSummary(): String {
        return try {
            val names =
                MediaCodecList(MediaCodecList.ALL_CODECS)
                    .codecInfos
                    .asSequence()
                    .filter { !it.isEncoder }
                    .filter { info ->
                        info.supportedTypes.any { type ->
                            type.equals("video/avc", ignoreCase = true) ||
                                type.equals("video/hevc", ignoreCase = true)
                        }
                    }
                    .map { it.name }
                    .filter { name ->
                        name.contains("secure", ignoreCase = true)
                    }
                    .distinct()
                    .take(8)
                    .toList()

            if (names.isEmpty()) {
                "No decoder with \"secure\" in its codec name was exposed. Codec naming is vendor-specific, so this alone does not prove protected decoding is unavailable."
            } else {
                names.joinToString(separator = "\n")
            }
        } catch (t: Throwable) {
            "Codec inspection failed: " + t.javaClass.simpleName
        }
    }

    private fun yesNo(value: Boolean): String =
        if (value) "yes" else "no"
}
