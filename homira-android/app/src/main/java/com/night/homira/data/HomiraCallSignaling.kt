package com.night.homira.data

import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CallSignalEnvelope(
    val type: String,
    @SerialName("from_user_id") val fromUserId: String,
    val sdp: String? = null,
    @SerialName("sdp_type") val sdpType: String? = null,
    val candidate: String? = null,
    @SerialName("sdp_mid") val sdpMid: String? = null,
    @SerialName("sdp_mline_index") val sdpMLineIndex: Int? = null,
    val muted: Boolean? = null,
    @SerialName("video_enabled") val videoEnabled: Boolean? = null
)

class HomiraCallSignaling(
    callId: String
) {
    private val client = HomiraSupabase.client
    private val channel = client.channel("call:$callId") {
        isPrivate = true
        broadcast {
            acknowledgeBroadcasts = true
            receiveOwnBroadcasts = false
        }
    }

    val signals: Flow<CallSignalEnvelope> =
        channel.broadcastFlow<CallSignalEnvelope>(event = "signal")

    suspend fun connect() {
        client.realtime.connect()
        channel.subscribe(blockUntilSubscribed = true)
    }

    suspend fun send(signal: CallSignalEnvelope) {
        channel.broadcast(event = "signal", message = signal)
    }

    suspend fun close() {
        runCatching { channel.unsubscribe() }
    }
}
