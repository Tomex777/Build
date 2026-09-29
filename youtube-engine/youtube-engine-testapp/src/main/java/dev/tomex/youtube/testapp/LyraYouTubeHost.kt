package dev.tomex.youtube.testapp

import dev.tomex.youtube.api.MediaChunk
import dev.tomex.youtube.api.MediaFormat
import dev.tomex.youtube.api.MediaTransferCheckpoint
import dev.tomex.youtube.api.TransportProof
import dev.tomex.youtube.api.VerifiedMedia
import dev.tomex.youtube.api.YouTubeEngine

/**
 * Thin host-side adapter matching Lyra's ownership boundary.
 *
 * Lyra owns MediaSession, player/cache/download state and durable checkpoints. All YouTube
 * discovery, player-script transforms, stable-format refresh and transport policy stay inside
 * [YouTubeEngine]; this adapter deliberately imports no core parser or resolver implementation.
 */
class LyraYouTubeHost(private val engine: YouTubeEngine) {
    suspend fun prepareAudio(
        videoId: String,
        preferredContainer: String? = "mp4"
    ): LyraAudioSource {
        val verified = engine.resolveVerifiedAudio(videoId, preferredContainer)
        require(verified.format.hasAudio && !verified.format.hasVideo)
        return verified.toSource(videoId)
    }

    suspend fun refresh(source: LyraAudioSource): LyraAudioSource =
        engine.refreshMediaVerified(source.videoId, source.format.stableIdentity)
            .also {
                require(it.format.stableIdentity == source.format.stableIdentity)
                require(it.format.hasAudio && !it.format.hasVideo)
            }
            .toSource(source.videoId)

    suspend fun read(
        source: LyraAudioSource,
        startByte: Long,
        byteLimit: Int = 256 * 1024
    ): MediaChunk =
        engine.fetchChunkWithRefresh(source.videoId, source.format, startByte, byteLimit)

    suspend fun resume(
        checkpoint: MediaTransferCheckpoint,
        byteLimit: Int = 256 * 1024
    ): MediaChunk =
        engine.fetchChunkFromCheckpoint(checkpoint, byteLimit)

    private fun VerifiedMedia.toSource(videoId: String) =
        LyraAudioSource(videoId, format, proof)
}

data class LyraAudioSource(
    val videoId: String,
    val format: MediaFormat,
    val proof: TransportProof
)
