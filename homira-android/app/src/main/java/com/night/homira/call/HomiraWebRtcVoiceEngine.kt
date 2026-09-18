package com.night.homira.call

import android.content.Context
import com.night.homira.data.CallSignalEnvelope
import com.night.homira.data.HomiraCallSignaling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class HomiraWebRtcState {
    New,
    Signaling,
    Connecting,
    Connected,
    Disconnected,
    Failed,
    Closed
}

class HomiraWebRtcVoiceEngine(
    context: Context,
    private val callId: String,
    private val localUserId: String,
    private val caller: Boolean,
    private val signaling: HomiraCallSignaling,
    private val iceServers: List<PeerConnection.IceServer> = defaultIceServers()
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(HomiraWebRtcState.New)
    val state: StateFlow<HomiraWebRtcState> = _state.asStateFlow()

    private val pendingRemoteIce = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false
    private var signalJob: Job? = null

    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var factory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var peerConnection: PeerConnection? = null

    suspend fun start() {
        if (_state.value != HomiraWebRtcState.New) return

        ensureWebRtcInitialized(appContext)
        _state.value = HomiraWebRtcState.Signaling

        audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
            .createAudioDeviceModule()

        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(requireNotNull(audioDeviceModule))
            .createPeerConnectionFactory()

        audioSource = requireNotNull(factory).createAudioSource(MediaConstraints())
        audioTrack = requireNotNull(factory).createAudioTrack("homira-audio-$callId", audioSource)

        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = requireNotNull(factory).createPeerConnection(config, peerObserver)
            ?: error("Could not create WebRTC peer connection")

        requireNotNull(peerConnection).addTrack(
            requireNotNull(audioTrack),
            listOf("homira-$callId")
        )

        signaling.connect()
        signalJob = scope.launch {
            signaling.signals.collect { signal ->
                if (signal.fromUserId != localUserId) {
                    handleSignal(signal)
                }
            }
        }

        if (caller) {
            createAndSendOffer()
        }
    }

    fun setMuted(muted: Boolean) {
        audioTrack?.setEnabled(!muted)
    }

    suspend fun close() {
        if (_state.value == HomiraWebRtcState.Closed) return
        _state.value = HomiraWebRtcState.Closed

        signalJob?.cancel()
        runCatching { signaling.close() }

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null

        audioTrack?.dispose()
        audioTrack = null

        audioSource?.dispose()
        audioSource = null

        factory?.dispose()
        factory = null

        audioDeviceModule?.release()
        audioDeviceModule = null

        scope.cancel()
    }

    private suspend fun createAndSendOffer() {
        val pc = requireNotNull(peerConnection)
        val offer = pc.createOfferAwait()
        pc.setLocalDescriptionAwait(offer)
        signaling.send(
            CallSignalEnvelope(
                type = "offer",
                fromUserId = localUserId,
                sdp = offer.description,
                sdpType = offer.type.canonicalForm()
            )
        )
    }

    private suspend fun createAndSendAnswer() {
        val pc = requireNotNull(peerConnection)
        val answer = pc.createAnswerAwait()
        pc.setLocalDescriptionAwait(answer)
        signaling.send(
            CallSignalEnvelope(
                type = "answer",
                fromUserId = localUserId,
                sdp = answer.description,
                sdpType = answer.type.canonicalForm()
            )
        )
    }

    private suspend fun handleSignal(signal: CallSignalEnvelope) {
        val pc = peerConnection ?: return

        when (signal.type) {
            "offer" -> {
                val sdp = signal.sdp ?: return
                pc.setRemoteDescriptionAwait(
                    SessionDescription(SessionDescription.Type.OFFER, sdp)
                )
                remoteDescriptionSet = true
                flushPendingIce()
                createAndSendAnswer()
            }

            "answer" -> {
                val sdp = signal.sdp ?: return
                pc.setRemoteDescriptionAwait(
                    SessionDescription(SessionDescription.Type.ANSWER, sdp)
                )
                remoteDescriptionSet = true
                flushPendingIce()
            }

            "ice" -> {
                val candidateSdp = signal.candidate ?: return
                val candidate = IceCandidate(
                    signal.sdpMid,
                    signal.sdpMLineIndex ?: 0,
                    candidateSdp
                )
                if (remoteDescriptionSet) {
                    pc.addIceCandidate(candidate)
                } else {
                    pendingRemoteIce += candidate
                }
            }
        }
    }

    private fun flushPendingIce() {
        val pc = peerConnection ?: return
        pendingRemoteIce.forEach(pc::addIceCandidate)
        pendingRemoteIce.clear()
    }

    private val peerObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            _state.value = when (newState) {
                PeerConnection.IceConnectionState.NEW -> HomiraWebRtcState.Signaling
                PeerConnection.IceConnectionState.CHECKING -> HomiraWebRtcState.Connecting
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> HomiraWebRtcState.Connected
                PeerConnection.IceConnectionState.DISCONNECTED -> HomiraWebRtcState.Disconnected
                PeerConnection.IceConnectionState.FAILED -> HomiraWebRtcState.Failed
                PeerConnection.IceConnectionState.CLOSED -> HomiraWebRtcState.Closed
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit

        override fun onIceCandidate(candidate: IceCandidate) {
            scope.launch {
                signaling.send(
                    CallSignalEnvelope(
                        type = "ice",
                        fromUserId = localUserId,
                        candidate = candidate.sdp,
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex
                    )
                )
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(dataChannel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) = Unit
    }

    companion object {
        private val initialized = AtomicBoolean(false)

        private fun ensureWebRtcInitialized(context: Context) {
            if (initialized.compareAndSet(false, true)) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions
                        .builder(context)
                        .createInitializationOptions()
                )
            }
        }

        fun defaultIceServers(): List<PeerConnection.IceServer> = listOf(
            PeerConnection.IceServer
                .builder("stun:stun.l.google.com:19302")
                .createIceServer()
        )
    }
}

private suspend fun PeerConnection.createOfferAwait(): SessionDescription =
    suspendCancellableCoroutine { continuation ->
        createOffer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                continuation.resume(description)
            }

            override fun onSetSuccess() = Unit

            override fun onCreateFailure(error: String) {
                continuation.resumeWithException(IllegalStateException(error))
            }

            override fun onSetFailure(error: String) = Unit
        }, MediaConstraints())
    }

private suspend fun PeerConnection.createAnswerAwait(): SessionDescription =
    suspendCancellableCoroutine { continuation ->
        createAnswer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                continuation.resume(description)
            }

            override fun onSetSuccess() = Unit

            override fun onCreateFailure(error: String) {
                continuation.resumeWithException(IllegalStateException(error))
            }

            override fun onSetFailure(error: String) = Unit
        }, MediaConstraints())
    }

private suspend fun PeerConnection.setLocalDescriptionAwait(description: SessionDescription) {
    suspendCancellableCoroutine<Unit> { continuation ->
        setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) = Unit

            override fun onSetSuccess() {
                continuation.resume(Unit)
            }

            override fun onCreateFailure(error: String) = Unit

            override fun onSetFailure(error: String) {
                continuation.resumeWithException(IllegalStateException(error))
            }
        }, description)
    }
}

private suspend fun PeerConnection.setRemoteDescriptionAwait(description: SessionDescription) {
    suspendCancellableCoroutine<Unit> { continuation ->
        setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) = Unit

            override fun onSetSuccess() {
                continuation.resume(Unit)
            }

            override fun onCreateFailure(error: String) = Unit

            override fun onSetFailure(error: String) {
                continuation.resumeWithException(IllegalStateException(error))
            }
        }, description)
    }
}
