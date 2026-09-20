package com.night.homira.call

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import com.night.homira.data.CallSignalEnvelope
import com.night.homira.data.HomiraCallSignaling
import com.night.homira.data.HomiraTurnConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
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
import org.webrtc.VideoTrack
import org.webrtc.VideoSource
import org.webrtc.SurfaceTextureHelper
import org.webrtc.EglBase
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.CameraVideoCapturer
import org.webrtc.Camera2Enumerator
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.ScreenCapturerAndroid
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
    private val initialVideoEnabled: Boolean = false,
    private val lowDataMode: Boolean = false,
    private val signaling: HomiraCallSignaling,
    private val iceServers: List<PeerConnection.IceServer> = defaultIceServers(),
    private val forceRelayOnly: Boolean = false
) {
    private val appContext = context.applicationContext
    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e("HomiraWebRTC", "Background WebRTC task failed", error)
    }
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + exceptionHandler
    )
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val originalAudioMode = audioManager.mode
    @Suppress("DEPRECATION")
    private val originalSpeakerphoneOn = audioManager.isSpeakerphoneOn
    private val originalCommunicationDeviceId =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.id
        } else {
            null
        }

    private val _state = MutableStateFlow(HomiraWebRtcState.New)
    val state: StateFlow<HomiraWebRtcState> = _state.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _remoteMuted = MutableStateFlow(false)
    val remoteMuted: StateFlow<Boolean> = _remoteMuted.asStateFlow()

    private val _remoteVideoEnabled = MutableStateFlow(initialVideoEnabled)
    val remoteVideoEnabled: StateFlow<Boolean> = _remoteVideoEnabled.asStateFlow()

    private val _screenSharing = MutableStateFlow(false)
    val screenSharing: StateFlow<Boolean> = _screenSharing.asStateFlow()

    private val _remoteScreenSharing = MutableStateFlow(false)
    val remoteScreenSharing: StateFlow<Boolean> = _remoteScreenSharing.asStateFlow()

    private val pendingRemoteIce = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false
    private var offerSent = false
    private var signalingReady = false
    private var signalJob: Job? = null

    private val eglBase = EglBase.create()
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var factory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var cameraCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoCaptureStarted = false
    private var audioSender: RtpSender? = null
    private var videoSender: RtpSender? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var screenVideoSource: VideoSource? = null
    private var screenVideoTrack: VideoTrack? = null
    private var screenTextureHelper: SurfaceTextureHelper? = null
    private var restoreCameraAfterScreenShare = false
    private var peerConnection: PeerConnection? = null

    suspend fun start() {
        if (_state.value != HomiraWebRtcState.New) return

        ensureWebRtcInitialized(appContext)
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        _state.value = HomiraWebRtcState.Signaling

        audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
            .createAudioDeviceModule()

        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(requireNotNull(audioDeviceModule))
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    eglBase.eglBaseContext,
                    true,
                    true
                )
            )
            .setVideoDecoderFactory(
                DefaultVideoDecoderFactory(eglBase.eglBaseContext)
            )
            .createPeerConnectionFactory()

        audioSource = requireNotNull(factory).createAudioSource(MediaConstraints())
        audioTrack = requireNotNull(factory).createAudioTrack("homira-audio-$callId", audioSource)

        videoSource = requireNotNull(factory).createVideoSource(false)
        videoTrack = requireNotNull(factory).createVideoTrack(
            "homira-video-$callId",
            videoSource
        ).apply {
            setEnabled(false)
        }
        _localVideoTrack.value = videoTrack

        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = if (forceRelayOnly) {
                PeerConnection.IceTransportsType.RELAY
            } else {
                PeerConnection.IceTransportsType.ALL
            }
        }

        peerConnection = requireNotNull(factory).createPeerConnection(config, peerObserver)
            ?: error("Could not create WebRTC peer connection")

        audioSender = requireNotNull(peerConnection).addTrack(
            requireNotNull(audioTrack),
            listOf("homira-$callId")
        )
        videoSender = requireNotNull(peerConnection).addTrack(
            requireNotNull(videoTrack),
            listOf("homira-$callId")
        )

        if (lowDataMode) {
            applyLowDataProfile()
        }

        if (initialVideoEnabled) {
            setVideoEnabled(true)
        }

        signaling.connect()
        signalingReady = true
        signalJob = scope.launch {
            signaling.signals.collect { signal ->
                if (signal.fromUserId != localUserId) {
                    handleSignal(signal)
                }
            }
        }

        sendLocalMediaState()

        if (!caller) {
            signaling.send(
                CallSignalEnvelope(
                    type = "ready",
                    fromUserId = localUserId
                )
            )
        }
    }

    fun eglContext(): EglBase.Context = eglBase.eglBaseContext

    private fun applyLowDataProfile() {
        audioSender?.let { sender ->
            val parameters = sender.parameters
            parameters.encodings.forEach { encoding ->
                encoding.maxBitrateBps = 24_000
                encoding.bitratePriority = 4.0
                encoding.adaptiveAudioPacketTime = true
            }
            sender.parameters = parameters
        }

        videoSender?.let { sender ->
            val parameters = sender.parameters
            parameters.encodings.forEach { encoding ->
                encoding.maxBitrateBps = 350_000
                encoding.maxFramerate = 20
                encoding.scaleResolutionDownBy = 1.5
                encoding.bitratePriority = 0.5
            }
            sender.parameters = parameters
        }
    }

    fun setMuted(muted: Boolean) {
        audioTrack?.setEnabled(!muted)
        if (signalingReady) {
            scope.launch {
                signaling.send(
                    CallSignalEnvelope(
                        type = "mute-state",
                        fromUserId = localUserId,
                        muted = muted
                    )
                )
            }
        }
    }

    fun setVideoEnabled(enabled: Boolean): Boolean {
        val track = videoTrack ?: return false

        if (!enabled) {
            track.setEnabled(false)
            stopCameraCapture()
            sendVideoState(false)
            return true
        }

        val started = startCameraCapture()
        track.setEnabled(started)
        if (started) sendVideoState(true)
        return started
    }

    private fun sendVideoState(enabled: Boolean) {
        if (!signalingReady) return
        scope.launch {
            signaling.send(
                CallSignalEnvelope(
                    type = "video-state",
                    fromUserId = localUserId,
                    videoEnabled = enabled
                )
            )
        }
    }

    private suspend fun sendLocalMediaState() {
        signaling.send(
            CallSignalEnvelope(
                type = "mute-state",
                fromUserId = localUserId,
                muted = audioTrack?.enabled() == false
            )
        )
        signaling.send(
            CallSignalEnvelope(
                type = "video-state",
                fromUserId = localUserId,
                videoEnabled = videoTrack?.enabled() == true || _screenSharing.value
            )
        )
        signaling.send(
            CallSignalEnvelope(
                type = "screen-share-state",
                fromUserId = localUserId,
                screenSharing = _screenSharing.value
            )
        )
    }

    fun switchCamera() {
        cameraCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) = Unit
            override fun onCameraSwitchError(errorDescription: String?) = Unit
        })
    }

    fun startScreenShare(permissionData: Intent): Boolean {
        if (_screenSharing.value) return true

        val peerFactory = factory ?: return false
        val sender = videoSender ?: return false
        restoreCameraAfterScreenShare = videoTrack?.enabled() == true

        return runCatching {
            if (restoreCameraAfterScreenShare) {
                videoTrack?.setEnabled(false)
                stopCameraCapture()
            }

            val source = peerFactory.createVideoSource(true)
            val helper = SurfaceTextureHelper.create(
                "HomiraScreen-$callId",
                eglBase.eglBaseContext
            )
            val capturer = ScreenCapturerAndroid(
                permissionData,
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopScreenShareInternal(
                            stopCapturer = false,
                            restoreCamera = true
                        )
                    }
                }
            )
            capturer.initialize(helper, appContext, source.capturerObserver)

            val metrics = appContext.resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(720)
            val height = metrics.heightPixels.coerceAtLeast(1280)
            capturer.startCapture(width, height, 15)

            val track = peerFactory.createVideoTrack(
                "homira-screen-$callId",
                source
            ).apply {
                setEnabled(true)
            }

            if (!sender.setTrack(track, false)) {
                runCatching { capturer.stopCapture() }
                capturer.dispose()
                helper.dispose()
                track.dispose()
                source.dispose()
                error("Could not attach screen share to the call")
            }

            screenVideoSource = source
            screenTextureHelper = helper
            screenCapturer = capturer
            screenVideoTrack = track
            _screenSharing.value = true
            sendVideoState(true)
            sendScreenShareState(true)
            true
        }.getOrElse {
            restoreCameraTrackAfterScreenShare()
            false
        }
    }

    fun stopScreenShare() {
        stopScreenShareInternal(
            stopCapturer = true,
            restoreCamera = true
        )
    }

    private fun stopScreenShareInternal(
        stopCapturer: Boolean,
        restoreCamera: Boolean
    ) {
        if (!_screenSharing.value && screenCapturer == null) return

        if (restoreCamera) {
            restoreCameraTrackAfterScreenShare()
        }

        if (stopCapturer) {
            runCatching { screenCapturer?.stopCapture() }
        }
        screenCapturer?.dispose()
        screenCapturer = null

        screenVideoTrack?.dispose()
        screenVideoTrack = null

        screenVideoSource?.dispose()
        screenVideoSource = null

        screenTextureHelper?.dispose()
        screenTextureHelper = null

        _screenSharing.value = false
        sendScreenShareState(false)

        if (!restoreCamera) {
            appContext.stopService(
                Intent(appContext, HomiraScreenShareService::class.java)
            )
        }
    }

    private fun restoreCameraTrackAfterScreenShare() {
        val cameraTrack = videoTrack ?: return
        val sender = videoSender ?: return
        val restoreCamera = restoreCameraAfterScreenShare
        restoreCameraAfterScreenShare = false

        sender.setTrack(cameraTrack, false)

        if (restoreCamera) {
            val started = startCameraCapture()
            cameraTrack.setEnabled(started)
            sendVideoState(started)
        } else {
            cameraTrack.setEnabled(false)
            sendVideoState(false)
        }

        appContext.stopService(
            Intent(appContext, HomiraScreenShareService::class.java)
        )
    }

    private fun sendScreenShareState(sharing: Boolean) {
        if (!signalingReady) return
        scope.launch {
            signaling.send(
                CallSignalEnvelope(
                    type = "screen-share-state",
                    fromUserId = localUserId,
                    screenSharing = sharing
                )
            )
        }
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val desiredType = if (enabled) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }

            val device = audioManager.availableCommunicationDevices
                .firstOrNull { it.type == desiredType }

            if (device != null) {
                audioManager.setCommunicationDevice(device)
            } else if (!enabled) {
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                audioManager.isSpeakerphoneOn = enabled
            }
        }
    }

    private fun startCameraCapture(): Boolean {
        if (videoCaptureStarted) return true

        return runCatching {
            val source = videoSource ?: error("Video source is not ready")

            if (surfaceTextureHelper == null) {
                surfaceTextureHelper = SurfaceTextureHelper.create(
                    "HomiraCamera-$callId",
                    eglBase.eglBaseContext
                )
            }

            if (cameraCapturer == null) {
                val enumerator = Camera2Enumerator(appContext)
                val frontCamera = enumerator.deviceNames
                    .firstOrNull { enumerator.isFrontFacing(it) }
                val cameraName = frontCamera
                    ?: enumerator.deviceNames.firstOrNull()
                    ?: error("No camera available")

                cameraCapturer = enumerator.createCapturer(cameraName, null)
                    ?: error("Could not open camera")

                cameraCapturer?.initialize(
                    surfaceTextureHelper,
                    appContext,
                    source.capturerObserver
                )
            }

            cameraCapturer?.startCapture(1280, 720, 30)
            videoCaptureStarted = true
            true
        }.getOrDefault(false)
    }

    private fun stopCameraCapture() {
        if (!videoCaptureStarted) return
        runCatching { cameraCapturer?.stopCapture() }
        videoCaptureStarted = false
    }

    suspend fun close() {
        if (_state.value == HomiraWebRtcState.Closed) return
        _state.value = HomiraWebRtcState.Closed
        signalingReady = false

        signalJob?.cancel()
        runCatching { signaling.close() }

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        audioSender = null
        videoSender = null

        audioTrack?.dispose()
        audioTrack = null

        audioSource?.dispose()
        audioSource = null

        stopScreenShareInternal(
            stopCapturer = true,
            restoreCamera = false
        )

        stopCameraCapture()
        cameraCapturer?.dispose()
        cameraCapturer = null

        videoTrack?.dispose()
        videoTrack = null
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null

        videoSource?.dispose()
        videoSource = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        factory?.dispose()
        factory = null

        audioDeviceModule?.release()
        audioDeviceModule = null

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val originalDevice = originalCommunicationDeviceId?.let { deviceId ->
                    audioManager.availableCommunicationDevices.firstOrNull { it.id == deviceId }
                }
                if (originalDevice != null) {
                    audioManager.setCommunicationDevice(originalDevice)
                } else {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = originalSpeakerphoneOn
            }
            audioManager.mode = originalAudioMode
        }

        eglBase.release()
        scope.cancel()
    }

    private suspend fun createAndSendOffer() {
        if (offerSent) return
        offerSent = true
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
            "ready" -> {
                if (caller) {
                    sendLocalMediaState()
                    createAndSendOffer()
                }
            }

            "mute-state" -> {
                signal.muted?.let { _remoteMuted.value = it }
            }

            "video-state" -> {
                signal.videoEnabled?.let { _remoteVideoEnabled.value = it }
            }

            "screen-share-state" -> {
                signal.screenSharing?.let { _remoteScreenSharing.value = it }
            }

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
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
            (receiver.track() as? VideoTrack)?.let { remote ->
                _remoteVideoTrack.value = remote
            }
        }
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

        fun iceServersFrom(
            config: HomiraTurnConfiguration?
        ): List<PeerConnection.IceServer> {
            val configured = config?.iceServers
                .orEmpty()
                .flatMap { server ->
                    server.urls.mapNotNull { url ->
                        val normalized = url.trim()
                        if (normalized.isBlank()) {
                            null
                        } else {
                            PeerConnection.IceServer
                                .builder(normalized)
                                .apply {
                                    server.username
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let(::setUsername)
                                    server.credential
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let(::setPassword)
                                }
                                .createIceServer()
                        }
                    }
                }

            return configured.ifEmpty { defaultIceServers() }
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
