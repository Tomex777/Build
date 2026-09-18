package com.night.homira.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Intent
import android.media.projection.MediaProjectionManager
import com.night.homira.call.HomiraWebRtcState
import com.night.homira.call.HomiraScreenShareService
import com.night.homira.call.HomiraWebRtcVoiceEngine
import com.night.homira.data.HomiraCallSignaling
import com.night.homira.data.HomiraLiveRepository
import com.night.homira.data.LiveProfile
import com.night.homira.data.LiveContact
import com.night.homira.data.LiveCallSession
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PhoneMissed
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class MainTab { Keypad, Recents, Contacts, Me }
private enum class OverlayScreen { None, Settings, EditProfile, Voicemail, AddContact }
private enum class CallDirection { Incoming, Outgoing, Missed, Declined, Failed }
private enum class RecentFilter { All, Missed, Voicemail }

private data class HomiraPerson(
    val id: String,
    val name: String,
    val marker: String,
    val accent: Color,
    val number: String,
    val favorite: Boolean = false
)

private data class CallEntry(
    val id: String,
    val person: HomiraPerson,
    val day: String,
    val time: String,
    val direction: CallDirection,
    val video: Boolean,
    val duration: String? = null,
    val count: Int = 1,
    val voicemailSeconds: Int? = null
)

private val mimiP = HomiraPerson("mimi", "MiMi", "✿", HomiraPink, "+234 803 124 5678", favorite = true)
private val hexP = HomiraPerson("hex", "Hex", "⚡", HomiraBlue, "+234 806 734 2011", favorite = true)
private val adaP = HomiraPerson("ada", "Ada", "A", HomiraGreen, "+234 802 440 1812")
private val tobiP = HomiraPerson("tobi", "Tobi", "T", Color(0xFFFFC46B), "+234 809 220 4300")
private val zaraP = HomiraPerson("zara", "Zara", "Z", Color(0xFFC59CFF), "+234 811 902 1414")
private val homiraContacts = listOf(mimiP, hexP, adaP, tobiP, zaraP)

private val callEntries = listOf(
    CallEntry("today-mimi-missed", mimiP, "Today", "18:23", CallDirection.Missed, video = false, count = 2, voicemailSeconds = 18),
    CallEntry("today-hex-in", hexP, "Today", "17:41", CallDirection.Incoming, video = false, duration = "28m"),
    CallEntry("today-ada-out", adaP, "Today", "16:17", CallDirection.Outgoing, video = true, duration = "12m"),
    CallEntry("yesterday-mimi-in", mimiP, "Yesterday", "08:04", CallDirection.Incoming, video = false, duration = "46m"),
    CallEntry("tuesday-tobi-declined", tobiP, "Tuesday", "18:11", CallDirection.Declined, video = false),
    CallEntry("monday-hex-out", hexP, "Monday", "12:41", CallDirection.Outgoing, video = true, duration = "1h 12m"),
    CallEntry("monday-zara-failed", zaraP, "Monday", "12:40", CallDirection.Failed, video = false),
    CallEntry("sunday-mimi-voice", mimiP, "Sunday", "18:35", CallDirection.Missed, video = false, voicemailSeconds = 32)
)

@Composable
fun HomiraProductionApp(initialProfile: LiveProfile? = null, initialContacts: List<LiveContact> = emptyList(), liveMode: Boolean = false) {
    HomiraTheme {
        val context = LocalContext.current
        val liveRepository = remember { HomiraLiveRepository() }
        val liveScope = rememberCoroutineScope()
        var tab by rememberSaveable { mutableStateOf(MainTab.Keypad) }
        var overlay by rememberSaveable { mutableStateOf(OverlayScreen.None) }
        var activePerson by remember { mutableStateOf<HomiraPerson?>(null) }
        var activeVideo by rememberSaveable { mutableStateOf(false) }
        var minimized by rememberSaveable { mutableStateOf(false) }
        var activeSession by remember { mutableStateOf<LiveCallSession?>(null) }
        var incomingSession by remember { mutableStateOf<LiveCallSession?>(null) }
        var incomingPerson by remember { mutableStateOf<HomiraPerson?>(null) }
        var voiceEngine by remember { mutableStateOf<HomiraWebRtcVoiceEngine?>(null) }
        var webRtcState by remember { mutableStateOf(HomiraWebRtcState.New) }
        var localVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
        var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
        var remoteMuted by remember { mutableStateOf(false) }
        var remoteVideoEnabled by remember { mutableStateOf(false) }
        var screenSharing by remember { mutableStateOf(false) }
        var remoteScreenSharing by remember { mutableStateOf(false) }
        var pendingOutgoingCall by remember { mutableStateOf<Pair<HomiraPerson, Boolean>?>(null) }
        var pendingIncomingAccept by remember { mutableStateOf(false) }
        var pendingVideoEnable by remember { mutableStateOf(false) }
        var micPermissionGranted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            )
        }
        var cameraPermissionGranted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        }
        val callPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            micPermissionGranted =
                results[Manifest.permission.RECORD_AUDIO] ?: micPermissionGranted
            cameraPermissionGranted =
                results[Manifest.permission.CAMERA] ?: cameraPermissionGranted

            val pendingNeedsVideo =
                pendingOutgoingCall?.second == true ||
                    (pendingIncomingAccept && incomingSession?.mediaType == "video") ||
                    pendingVideoEnable

            if (!micPermissionGranted && (pendingOutgoingCall != null || pendingIncomingAccept)) {
                pendingOutgoingCall = null
                pendingIncomingAccept = false
                Toast.makeText(
                    context,
                    "Microphone permission is required for Homira calls.",
                    Toast.LENGTH_SHORT
                ).show()
            } else if (pendingNeedsVideo && !cameraPermissionGranted) {
                pendingOutgoingCall = null
                pendingIncomingAccept = false
                pendingVideoEnable = false
                Toast.makeText(
                    context,
                    "Camera permission is required to use video.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val mediaProjectionManager = remember {
            context.getSystemService(MediaProjectionManager::class.java)
        }
        val screenShareLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val projectionData = result.data
            if (result.resultCode == Activity.RESULT_OK && projectionData != null) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, HomiraScreenShareService::class.java)
                )
                liveScope.launch {
                    val ready = withTimeoutOrNull(3_000L) {
                        HomiraScreenShareService.foregroundReady
                            .filter { it }
                            .first()
                    } != null

                    if (!ready) {
                        context.stopService(
                            Intent(context, HomiraScreenShareService::class.java)
                        )
                        Toast.makeText(
                            context,
                            "Could not start screen sharing.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    val started = voiceEngine?.startScreenShare(projectionData) == true
                    if (!started) {
                        context.stopService(
                            Intent(context, HomiraScreenShareService::class.java)
                        )
                        Toast.makeText(
                            context,
                            "Could not share your screen.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        var profileName by rememberSaveable(initialProfile?.id) {
            mutableStateOf(initialProfile?.displayName?.ifBlank { "You" } ?: "You")
        }
        var profileUsername by rememberSaveable(initialProfile?.id) {
            mutableStateOf(initialProfile?.username.orEmpty())
        }
        var profileAbout by rememberSaveable(initialProfile?.id) {
            mutableStateOf(initialProfile?.about.orEmpty())
        }
        var profileEmail by rememberSaveable(initialProfile?.id) {
            mutableStateOf(initialProfile?.email.orEmpty())
        }
        var avatarUri by rememberSaveable { mutableStateOf<String?>(null) }
        var callCardUri by rememberSaveable { mutableStateOf<String?>(null) }

        var liveContacts by remember(initialContacts) { mutableStateOf(initialContacts) }
        val appContacts = remember(liveContacts) {
            liveContacts.map { contact ->
                val visibleName = contact.localName?.takeIf { it.isNotBlank() }
                    ?: contact.displayName.takeIf { it.isNotBlank() }
                    ?: contact.username?.takeIf { it.isNotBlank() }
                    ?: contact.phoneE164
                    ?: "Homira user"
                HomiraPerson(
                    id = contact.id,
                    name = visibleName,
                    marker = visibleName.firstOrNull()?.uppercaseChar()?.toString() ?: "H",
                    accent = HomiraGreen,
                    number = contact.phoneE164.orEmpty(),
                    favorite = contact.favorite
                )
            }
        }
        val appCallEntries = if (liveMode) emptyList() else callEntries

        fun startCallNow(person: HomiraPerson, video: Boolean) {
            liveScope.launch {
                runCatching {
                    liveRepository.startCall(calleeId = person.id, video = video)
                }.onSuccess { session ->
                    activeSession = session
                    activePerson = person
                    activeVideo = video
                    minimized = false
                }.onFailure {
                    Toast.makeText(
                        context,
                        it.message ?: "Could not start the call.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        fun beginCall(person: HomiraPerson, video: Boolean) {
            if (!liveMode) {
                activePerson = person
                activeVideo = video
                minimized = false
                return
            }

            val needsMicrophone = !micPermissionGranted
            val needsCamera = video && !cameraPermissionGranted
            if (needsMicrophone || needsCamera) {
                pendingOutgoingCall = person to video
                callPermissionLauncher.launch(
                    buildList {
                        if (needsMicrophone) add(Manifest.permission.RECORD_AUDIO)
                        if (needsCamera) add(Manifest.permission.CAMERA)
                    }.toTypedArray()
                )
                return
            }

            startCallNow(person, video)
        }

        fun acceptIncomingNow() {
            val session = incomingSession
            val person = incomingPerson
            if (session != null && person != null) {
                liveScope.launch {
                    runCatching {
                        liveRepository.setCallState(session.id, "active")
                    }.onSuccess { updated ->
                        activeSession = updated
                        activePerson = person
                        activeVideo = updated.mediaType == "video"
                        minimized = false
                        incomingSession = null
                        incomingPerson = null
                    }.onFailure {
                        Toast.makeText(
                            context,
                            it.message ?: "Could not answer the call.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        LaunchedEffect(micPermissionGranted, cameraPermissionGranted, pendingOutgoingCall) {
            pendingOutgoingCall?.let { (person, video) ->
                if (micPermissionGranted && (!video || cameraPermissionGranted)) {
                    pendingOutgoingCall = null
                    startCallNow(person, video)
                }
            }
        }

        LaunchedEffect(
            micPermissionGranted,
            cameraPermissionGranted,
            pendingIncomingAccept,
            incomingSession?.mediaType
        ) {
            if (
                pendingIncomingAccept &&
                micPermissionGranted &&
                (incomingSession?.mediaType != "video" || cameraPermissionGranted)
            ) {
                pendingIncomingAccept = false
                acceptIncomingNow()
            }
        }

        LaunchedEffect(cameraPermissionGranted, pendingVideoEnable, voiceEngine) {
            if (pendingVideoEnable && cameraPermissionGranted) {
                pendingVideoEnable = false
                val enabled = voiceEngine?.setVideoEnabled(true) == true
                if (enabled) {
                    activeVideo = true
                } else {
                    Toast.makeText(
                        context,
                        "Could not start the camera.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        LaunchedEffect(liveMode, appContacts) {
            if (!liveMode) return@LaunchedEffect

            suspend fun resolvePerson(userId: String): HomiraPerson {
                appContacts.firstOrNull { it.id == userId }?.let { return it }

                val profile = liveRepository.loadProfileById(userId)
                val name = profile?.displayName?.takeIf { it.isNotBlank() }
                    ?: profile?.username?.takeIf { it.isNotBlank() }
                    ?: profile?.phoneE164
                    ?: "Homira caller"

                return HomiraPerson(
                    id = userId,
                    name = name,
                    marker = name.firstOrNull()?.uppercaseChar()?.toString() ?: "H",
                    accent = HomiraGreen,
                    number = profile?.phoneE164.orEmpty()
                )
            }

            liveRepository.loadPendingIncomingCall()?.let { pending ->
                incomingSession = pending
                incomingPerson = resolvePerson(pending.callerId)
            }

            liveRepository.observeIncomingCallChanges().collect { session ->
                when (session.state) {
                    "ringing" -> {
                        incomingSession = session
                        incomingPerson = resolvePerson(session.callerId)
                    }
                    "declined", "cancelled", "failed", "ended" -> {
                        if (incomingSession?.id == session.id) {
                            incomingSession = null
                            incomingPerson = null
                        }
                    }
                }
            }
        }

        LaunchedEffect(liveMode, activeSession?.id) {
            if (!liveMode) return@LaunchedEffect
            val callId = activeSession?.id ?: return@LaunchedEffect

            liveRepository.observeCallSession(callId).collect { session ->
                activeSession = session
                if (session.state in setOf("declined", "cancelled", "failed", "ended")) {
                    activeSession = null
                    activePerson = null
                    minimized = false
                }
            }
        }

        LaunchedEffect(liveMode, activeSession?.id, micPermissionGranted) {
            voiceEngine?.close()
            voiceEngine = null
            webRtcState = HomiraWebRtcState.New

            val session = activeSession
            val person = activePerson
            val localUserId = liveRepository.currentUserId()

            if (!liveMode || !micPermissionGranted || session == null || person == null || localUserId == null) {
                return@LaunchedEffect
            }

            val engine = HomiraWebRtcVoiceEngine(
                context = context,
                callId = session.id,
                localUserId = localUserId,
                caller = session.callerId == localUserId,
                initialVideoEnabled = activeVideo && cameraPermissionGranted,
                signaling = HomiraCallSignaling(session.id)
            )
            voiceEngine = engine

            try {
                engine.start()
                engine.state.collect { state ->
                    webRtcState = state
                    if (state == HomiraWebRtcState.Failed && activeSession?.id == session.id) {
                        runCatching {
                            liveRepository.setCallState(session.id, "failed")
                        }
                    }
                }
            } catch (error: Throwable) {
                webRtcState = HomiraWebRtcState.Failed
                if (activeSession?.id == session.id) {
                    runCatching {
                        liveRepository.setCallState(session.id, "failed")
                    }
                }
                Toast.makeText(
                    context,
                    error.message ?: "Call connection failed.",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                engine.close()
                if (voiceEngine === engine) {
                    voiceEngine = null
                }
            }
        }

        LaunchedEffect(voiceEngine) {
            localVideoTrack = null
            remoteVideoTrack = null
            remoteMuted = false
            remoteVideoEnabled = activeVideo
            screenSharing = false
            remoteScreenSharing = false
            val engine = voiceEngine ?: return@LaunchedEffect

            launch {
                engine.localVideoTrack.collect { track ->
                    localVideoTrack = track
                }
            }
            launch {
                engine.remoteVideoTrack.collect { track ->
                    remoteVideoTrack = track
                }
            }
            launch {
                engine.remoteMuted.collect { muted ->
                    remoteMuted = muted
                }
            }
            launch {
                engine.remoteVideoEnabled.collect { enabled ->
                    remoteVideoEnabled = enabled
                }
            }
            launch {
                engine.screenSharing.collect { sharing ->
                    screenSharing = sharing
                }
            }
            launch {
                engine.remoteScreenSharing.collect { sharing ->
                    remoteScreenSharing = sharing
                }
            }
        }

        when {
            incomingSession != null && incomingPerson != null && activePerson == null -> IncomingCallScreen(
                person = incomingPerson ?: mimiP,
                video = incomingSession?.mediaType == "video",
                onAccept = {
                    val needsCamera =
                        incomingSession?.mediaType == "video" && !cameraPermissionGranted
                    val needsMicrophone = !micPermissionGranted

                    if (!needsMicrophone && !needsCamera) {
                        acceptIncomingNow()
                    } else {
                        pendingIncomingAccept = true
                        callPermissionLauncher.launch(
                            buildList {
                                if (needsMicrophone) add(Manifest.permission.RECORD_AUDIO)
                                if (needsCamera) add(Manifest.permission.CAMERA)
                            }.toTypedArray()
                        )
                    }
                },
                onDecline = {
                    val session = incomingSession
                    incomingSession = null
                    incomingPerson = null
                    if (session != null) {
                        liveScope.launch {
                            runCatching { liveRepository.setCallState(session.id, "declined") }
                        }
                    }
                }
            )

            activePerson != null && !minimized -> ActiveCallScreen(
                person = activePerson ?: mimiP,
                startsWithVideo = activeVideo,
                liveState = if (liveMode) activeSession?.state else null,
                mediaState = if (liveMode) webRtcState else null,
                videoEnabled = activeVideo,
                remoteVideoEnabled = if (liveMode) remoteVideoEnabled else activeVideo,
                remoteMuted = if (liveMode) remoteMuted else false,
                screenSharing = if (liveMode) screenSharing else false,
                remoteScreenSharing = if (liveMode) remoteScreenSharing else false,
                localVideoTrack = if (liveMode) localVideoTrack else null,
                remoteVideoTrack = if (liveMode) remoteVideoTrack else null,
                eglContext = if (liveMode) voiceEngine?.eglContext() else null,
                onMuteChanged = { muted -> voiceEngine?.setMuted(muted) },
                onSpeakerChanged = { enabled -> voiceEngine?.setSpeakerEnabled(enabled) },
                onVideoChanged = { enabled ->
                    if (screenSharing) {
                        Toast.makeText(
                            context,
                            "Stop screen sharing to use the camera.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else if (!liveMode) {
                        activeVideo = enabled
                    } else if (!enabled) {
                        voiceEngine?.setVideoEnabled(false)
                        activeVideo = false
                    } else if (!cameraPermissionGranted) {
                        pendingVideoEnable = true
                        callPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                    } else {
                        val started = voiceEngine?.setVideoEnabled(true) == true
                        if (started) {
                            activeVideo = true
                        } else {
                            Toast.makeText(
                                context,
                                "Could not start the camera.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onSwitchCamera = { voiceEngine?.switchCamera() },
                onScreenShareChanged = { shouldShare ->
                    if (!liveMode) {
                        screenSharing = shouldShare
                    } else if (shouldShare) {
                        screenShareLauncher.launch(
                            mediaProjectionManager.createScreenCaptureIntent()
                        )
                    } else {
                        voiceEngine?.stopScreenShare()
                    }
                },
                onMinimize = { minimized = true },
                onEnd = {
                    val session = activeSession
                    activePerson = null
                    activeSession = null
                    minimized = false
                    if (liveMode && session != null) {
                        liveScope.launch {
                            runCatching { liveRepository.setCallState(session.id, "ended") }
                        }
                    }
                }
            )

            overlay == OverlayScreen.Settings -> SettingsScreen(
                onBack = { overlay = OverlayScreen.None },
                onVoicemail = { overlay = OverlayScreen.Voicemail }
            )

            overlay == OverlayScreen.Voicemail -> VoicemailSettingsScreen(
                onBack = { overlay = OverlayScreen.Settings }
            )

            overlay == OverlayScreen.AddContact -> AddContactScreen(
                repository = liveRepository,
                onBack = { overlay = OverlayScreen.None },
                onAdded = { added ->
                    liveContacts = (liveContacts.filterNot { it.id == added.id } + added)
                        .sortedWith(compareByDescending<LiveContact> { it.favorite }.thenBy {
                            it.localName ?: it.displayName
                        })
                    overlay = OverlayScreen.None
                }
            )

            overlay == OverlayScreen.EditProfile -> EditProfileScreen(
                name = profileName,
                username = profileUsername,
                about = profileAbout,
                email = profileEmail,
                avatarUri = avatarUri,
                callCardUri = callCardUri,
                onBack = { overlay = OverlayScreen.None },
                onSave = { name, username, about, email, avatar, card ->
                    profileName = name
                    profileUsername = username
                    profileAbout = about
                    profileEmail = email
                    avatarUri = avatar
                    callCardUri = card
                    overlay = OverlayScreen.None
                    liveScope.launch {
                        runCatching {
                            liveRepository.updateMyProfile(
                                displayName = name,
                                username = username,
                                about = about,
                                email = email
                            )
                        }.onSuccess { saved ->
                            profileName = saved.displayName.ifBlank { "You" }
                            profileUsername = saved.username.orEmpty()
                            profileAbout = saved.about
                            profileEmail = saved.email.orEmpty()
                        }
                    }
                }
            )

            else -> Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = HomiraBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = { FloatingBottomBar(tab = tab, onSelect = { tab = it }) }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    AnimatedVisibility(activePerson != null && minimized) {
                        OngoingCallBanner(
                            person = activePerson ?: mimiP,
                            onReturn = { minimized = false },
                            onEnd = {
                                val session = activeSession
                                activePerson = null
                                activeSession = null
                                minimized = false
                                if (liveMode && session != null) {
                                    liveScope.launch {
                                        runCatching { liveRepository.setCallState(session.id, "ended") }
                                    }
                                }
                            }
                        )
                    }
                    AnimatedContent(
                        targetState = tab,
                        label = "mainTabs",
                        modifier = Modifier.fillMaxSize()
                    ) { current ->
                        when (current) {
                            MainTab.Keypad -> KeypadScreen(
                                contacts = appContacts,
                                onSettings = { overlay = OverlayScreen.Settings },
                                onDial = { value ->
                                    val digits = digitsOnlyP(value)
                                    val found = appContacts.firstOrNull {
                                        digitsOnlyP(it.number).endsWith(digits.takeLast(10)) && digits.length >= 7
                                    }
                                    if (found != null) {
                                        beginCall(found, false)
                                    } else if (liveMode) {
                                        Toast.makeText(
                                            context,
                                            "Add this Homira user first, then call from Contacts.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        beginCall(
                                            HomiraPerson(
                                                id = "dial-$digits",
                                                name = value,
                                                marker = "#",
                                                accent = HomiraGreen,
                                                number = value
                                            ),
                                            false
                                        )
                                    }
                                }
                            )

                            MainTab.Recents -> RecentsScreen(
                                contacts = appContacts,
                                entries = appCallEntries,
                                onSettings = { overlay = OverlayScreen.Settings },
                                onVoiceCall = { beginCall(it, false) },
                                onVideoCall = { beginCall(it, true) }
                            )

                            MainTab.Contacts -> ContactsScreen(
                                contacts = appContacts,
                                myName = profileName,
                                onSettings = { overlay = OverlayScreen.Settings },
                                onAddContact = { overlay = OverlayScreen.AddContact },
                                onVoiceCall = { beginCall(it, false) },
                                onVideoCall = { beginCall(it, true) },
                                onOpenMe = { tab = MainTab.Me }
                            )

                            MainTab.Me -> MeScreen(
                                name = profileName,
                                username = profileUsername,
                                about = profileAbout,
                                email = profileEmail,
                                avatarUri = avatarUri,
                                callCardUri = callCardUri,
                                onEdit = { overlay = OverlayScreen.EditProfile },
                                onSettings = { overlay = OverlayScreen.Settings }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderActions(title: String, onSettings: () -> Unit, showSearch: Boolean = false) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = HomiraText,
            fontSize = 31.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (showSearch) {
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = HomiraText)
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = HomiraText)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onSettings()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Blocked people") },
                    leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null) },
                    onClick = { menuOpen = false }
                )
            }
        }
    }
}

@Composable
private fun KeypadScreen(contacts: List<HomiraPerson>, onSettings: () -> Unit, onDial: (String) -> Unit) {
    var number by rememberSaveable { mutableStateOf("") }
    val match = contacts.firstOrNull {
        val digits = digitsOnlyP(number)
        digits.length >= 7 && digitsOnlyP(it.number).endsWith(digits.takeLast(10))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = HomiraText)
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = HomiraText)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        onClick = {
                            menu = false
                            onSettings()
                        }
                    )
                }
            }
        }

        Spacer(Modifier.weight(.7f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (number.isNotBlank()) {
                Text(
                    formatDialNumberP(number),
                    color = HomiraText,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(Modifier.height(7.dp))
                if (match != null) {
                    Text("${match.name} · Homira", color = match.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                } else if (digitsOnlyP(number).length >= 7) {
                    Text("Not on Homira", color = HomiraMuted, fontSize = 13.sp)
                }
            } else {
                Spacer(Modifier.height(44.dp))
            }
        }

        Spacer(Modifier.height(26.dp))
        PlainDialPad(
            onDigit = { if (number.length < 20) number += it },
            onLongZero = { if (number.length < 20 && !number.startsWith("+")) number += "+" }
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(56.dp))
            Spacer(Modifier.width(34.dp))
            Surface(
                modifier = Modifier
                    .size(70.dp)
                    .clickable(enabled = number.isNotBlank()) { onDial(number) },
                shape = CircleShape,
                color = if (number.isBlank()) HomiraSurfaceRaised else HomiraGreen
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Call,
                        contentDescription = "Call",
                        tint = if (number.isBlank()) HomiraMuted else Color.Black,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(Modifier.width(34.dp))
            IconButton(
                onClick = { if (number.isNotEmpty()) number = number.dropLast(1) },
                enabled = number.isNotEmpty(),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Rounded.Backspace,
                    contentDescription = "Delete",
                    tint = if (number.isBlank()) Color.Transparent else HomiraText
                )
            }
        }
        Spacer(Modifier.weight(.3f))
    }
}

@Composable
private fun PlainDialPad(onDigit: (String) -> Unit, onLongZero: () -> Unit) {
    val rows = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 74.dp)
                            .clickable { onDigit(key.first) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(key.first, color = HomiraText, fontSize = 31.sp, fontWeight = FontWeight.Normal)
                            if (key.second.isNotBlank()) {
                                Text(key.second, color = HomiraMuted, fontSize = 10.sp, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentsScreen(
    contacts: List<HomiraPerson>,
    entries: List<CallEntry>,
    onSettings: () -> Unit,
    onVoiceCall: (HomiraPerson) -> Unit,
    onVideoCall: (HomiraPerson) -> Unit
) {
    var filter by rememberSaveable { mutableStateOf(RecentFilter.All) }
    var playingVoicemail by rememberSaveable { mutableStateOf<String?>(null) }

    val filtered = when (filter) {
        RecentFilter.All -> entries
        RecentFilter.Missed -> entries.filter { it.direction == CallDirection.Missed }
        RecentFilter.Voicemail -> entries.filter { it.voicemailSeconds != null }
    }
    val days = entries.map { it.day }.distinct().filter { day -> filtered.any { it.day == day } }
    val topMissed = entries.firstOrNull { it.direction == CallDirection.Missed }

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HeaderActions("Recents", onSettings, showSearch = true) }

        if (topMissed != null) {
            item {
                MissedSummaryCard(
                    call = topMissed,
                    voicemailPlaying = playingVoicemail == topMissed.id,
                    onCallBack = { onVoiceCall(topMissed.person) },
                    onVoicemail = {
                        playingVoicemail = if (playingVoicemail == topMissed.id) null else topMissed.id
                    }
                )
            }
        }

        item {
            Text("Quick call", color = HomiraMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                contacts.filter { it.favorite }.forEach { person ->
                    QuickPerson(person, onVoice = { onVoiceCall(person) }, onVideo = { onVideoCall(person) })
                }
                contacts.filterNot { it.favorite }.take(2).forEach { person ->
                    QuickPerson(person, onVoice = { onVoiceCall(person) }, onVideo = { onVideoCall(person) })
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip("All", filter == RecentFilter.All) { filter = RecentFilter.All }
                Spacer(Modifier.width(8.dp))
                FilterChip("Missed", filter == RecentFilter.Missed) { filter = RecentFilter.Missed }
                Spacer(Modifier.width(8.dp))
                FilterChip("Voicemail", filter == RecentFilter.Voicemail) { filter = RecentFilter.Voicemail }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.FilterList, contentDescription = "Filter", tint = HomiraMuted, modifier = Modifier.size(21.dp))
            }
        }

        item {
            Text(
                "Swipe right for voice · left for video",
                color = HomiraMuted.copy(alpha = .72f),
                fontSize = 11.sp
            )
        }

        days.forEach { day ->
            val entries = filtered.filter { it.day == day }
            item(key = "day-$day") {
                Column {
                    Text(day, color = HomiraMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 7.dp))
                    Card(
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = HomiraSurface)
                    ) {
                        Column {
                            entries.forEachIndexed { index, entry ->
                                SwipeCallRow(
                                    person = entry.person,
                                    onVoice = { onVoiceCall(entry.person) },
                                    onVideo = { onVideoCall(entry.person) }
                                ) {
                                    RecentEntryRow(
                                        entry = entry,
                                        voicemailPlaying = playingVoicemail == entry.id,
                                        onVoicemail = {
                                            playingVoicemail = if (playingVoicemail == entry.id) null else entry.id
                                        },
                                        onCall = { onVoiceCall(entry.person) }
                                    )
                                }
                                if (index != entries.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 66.dp, end = 18.dp),
                                        color = HomiraLine.copy(alpha = .7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun MissedSummaryCard(
    call: CallEntry,
    voicemailPlaying: Boolean,
    onCallBack: () -> Unit,
    onVoicemail: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = HomiraSurface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.PhoneMissed, contentDescription = null, tint = HomiraDanger, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                if (call.count > 1) "${call.count} missed calls from ${call.person.name}" else "Missed call from ${call.person.name}",
                color = HomiraText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onCallBack,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HomiraText, contentColor = HomiraBackground)
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Call back")
                }
                if (call.voicemailSeconds != null) {
                    TextButton(onClick = onVoicemail) {
                        Icon(
                            if (voicemailPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = HomiraGreen,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Voicemail · ${call.voicemailSeconds}s", color = HomiraGreen)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPerson(person: HomiraPerson, onVoice: () -> Unit, onVideo: () -> Unit) {
    Card(
        modifier = Modifier.width(142.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = HomiraSurface)
    ) {
        Column(Modifier.padding(14.dp)) {
            PersonAvatarP(person, 48)
            Spacer(Modifier.height(9.dp))
            Text(person.name, color = HomiraText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircleAction(Icons.Rounded.Call, person.accent, "Voice call", onVoice)
                CircleAction(Icons.Rounded.Videocam, person.accent, "Video call", onVideo)
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(99.dp),
        color = if (selected) HomiraText else HomiraSurface
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = if (selected) HomiraBackground else HomiraMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun RecentEntryRow(
    entry: CallEntry,
    voicemailPlaying: Boolean,
    onVoicemail: () -> Unit,
    onCall: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().background(HomiraSurface).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonAvatarP(entry.person, 42)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.person.name,
                    color = if (entry.direction == CallDirection.Missed) HomiraDanger else HomiraText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.count > 1) {
                    Spacer(Modifier.width(5.dp))
                    Text("(${entry.count})", color = HomiraMuted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    directionIcon(entry.direction),
                    contentDescription = null,
                    tint = directionColor(entry.direction),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    buildString {
                        append(directionLabel(entry.direction))
                        if (entry.duration != null) append(" · ${entry.duration}")
                    },
                    color = HomiraMuted,
                    fontSize = 12.sp
                )
            }
            if (entry.voicemailSeconds != null) {
                Spacer(Modifier.height(7.dp))
                Surface(
                    modifier = Modifier.clickable(onClick = onVoicemail),
                    shape = RoundedCornerShape(99.dp),
                    color = HomiraGreen.copy(alpha = .10f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (voicemailPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (voicemailPlaying) "Pause voicemail" else "Play voicemail",
                            tint = HomiraGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Voicemail · ${entry.voicemailSeconds}s", color = HomiraGreen, fontSize = 11.sp)
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(entry.time, color = HomiraMuted, fontSize = 12.sp)
            Spacer(Modifier.height(7.dp))
            Icon(
                if (entry.video) Icons.Rounded.Videocam else Icons.Rounded.Call,
                contentDescription = "Call",
                tint = HomiraMuted,
                modifier = Modifier.size(19.dp).clickable(onClick = onCall)
            )
        }
    }
}

@Composable
private fun AddContactScreen(
    repository: HomiraLiveRepository,
    onBack: () -> Unit,
    onAdded: (LiveContact) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var localName by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomiraBackground)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = HomiraText)
            }
            Text(
                "Add contact",
                color = HomiraText,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Find someone on Homira",
            color = HomiraText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "Use their exact @username or full phone number with country code.",
            color = HomiraMuted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Username or phone number") },
            placeholder = { Text("@username or +234…") },
            shape = RoundedCornerShape(18.dp)
        )

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = localName,
            onValueChange = { localName = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Name on your phone · optional") },
            shape = RoundedCornerShape(18.dp)
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error ?: "", color = HomiraDanger, fontSize = 13.sp)
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                if (query.isBlank() || busy) return@Button
                scope.launch {
                    busy = true
                    error = null
                    runCatching {
                        repository.addContact(
                            query = query.trim(),
                            localName = localName.trim().ifBlank { null }
                        )
                    }.onSuccess { contact ->
                        if (contact == null) {
                            error = "No Homira account matched that username or number."
                        } else {
                            onAdded(contact)
                        }
                    }.onFailure {
                        error = it.message ?: "Could not add this contact."
                    }
                    busy = false
                }
            },
            enabled = query.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HomiraGreen,
                contentColor = HomiraBackground
            )
        ) {
            if (busy) {
                Text("Adding…", fontWeight = FontWeight.SemiBold)
            } else {
                Text("Add to Homira", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ContactsScreen(
    contacts: List<HomiraPerson>,
    myName: String,
    onSettings: () -> Unit,
    onAddContact: () -> Unit,
    onVoiceCall: (HomiraPerson) -> Unit,
    onVideoCall: (HomiraPerson) -> Unit,
    onOpenMe: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = contacts.filter {
        it.name.contains(query, ignoreCase = true) || it.number.contains(query)
    }
    val initials = filtered.map { it.name.first().uppercaseChar() }.distinct().sorted()

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Contacts", color = HomiraText, fontSize = 31.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onAddContact) { Icon(Icons.Rounded.Add, contentDescription = "Add contact", tint = HomiraText) }
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = HomiraText) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onSettings()
                            }
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Search contacts") },
                shape = RoundedCornerShape(20.dp)
            )
        }

        if (query.isBlank()) {
            item {
                Text("Favorites", color = HomiraMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(9.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    contacts.filter { it.favorite }.forEach { person ->
                        FavoriteContact(person = person, onCall = { onVoiceCall(person) })
                    }
                    Surface(
                        modifier = Modifier.size(width = 88.dp, height = 105.dp).clickable { },
                        shape = RoundedCornerShape(24.dp),
                        color = HomiraSurface
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = HomiraMuted)
                            Spacer(Modifier.height(5.dp))
                            Text("Add", color = HomiraMuted, fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = HomiraSurface)) {
                    Column {
                        SimpleContactUtility(Icons.Rounded.Person, "My profile", myName, onOpenMe)
                        HorizontalDivider(modifier = Modifier.padding(start = 62.dp), color = HomiraLine.copy(alpha = .65f))
                        SimpleContactUtility(Icons.Rounded.Groups, "Groups", "Family, friends and more") { }
                    }
                }
            }
        }

        if (contacts.isEmpty() && query.isBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = HomiraSurface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = HomiraMuted)
                        Spacer(Modifier.height(8.dp))
                        Text("No contacts yet", color = HomiraText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Add someone by their Homira username or phone number.",
                            color = HomiraMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onAddContact) {
                            Text("Add contact", color = HomiraGreen)
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Swipe right for voice · left for video",
                color = HomiraMuted.copy(alpha = .72f),
                fontSize = 11.sp
            )
        }

        initials.forEach { initial ->
            val section = filtered.filter { it.name.first().uppercaseChar() == initial }
            item(key = "contact-$initial") {
                Column {
                    Text(initial.toString(), color = HomiraMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 7.dp))
                    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = HomiraSurface)) {
                        Column {
                            section.forEachIndexed { index, person ->
                                SwipeCallRow(
                                    person = person,
                                    onVoice = { onVoiceCall(person) },
                                    onVideo = { onVideoCall(person) }
                                ) {
                                    ContactRow(person = person, onVoice = { onVoiceCall(person) }, onVideo = { onVideoCall(person) })
                                }
                                if (index != section.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp), color = HomiraLine.copy(alpha = .65f))
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun FavoriteContact(person: HomiraPerson, onCall: () -> Unit) {
    Surface(
        modifier = Modifier.size(width = 88.dp, height = 105.dp).clickable(onClick = onCall),
        shape = RoundedCornerShape(24.dp),
        color = HomiraSurface
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box {
                PersonAvatarP(person, 52)
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = HomiraPink,
                    modifier = Modifier.size(17.dp).align(Alignment.BottomEnd)
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(person.name, color = HomiraText, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SimpleContactUtility(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = HomiraSurfaceRaised) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = HomiraText, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = HomiraText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = HomiraMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ContactRow(person: HomiraPerson, onVoice: () -> Unit, onVideo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(HomiraSurface).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonAvatarP(person, 44)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(person.name, color = HomiraText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(person.number, color = HomiraMuted, fontSize = 12.sp)
        }
        IconButton(onClick = onVoice, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Rounded.Call, contentDescription = "Voice call", tint = HomiraGreen, modifier = Modifier.size(19.dp))
        }
        IconButton(onClick = onVideo, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Rounded.Videocam, contentDescription = "Video call", tint = HomiraBlue, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SwipeCallRow(
    person: HomiraPerson,
    onVoice: () -> Unit,
    onVideo: () -> Unit,
    content: @Composable () -> Unit
) {
    var dragOffset by remember(person.id) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val threshold = with(density) { 72.dp.toPx() }
    val maxDrag = with(density) { 104.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (dragOffset >= 0f) HomiraGreen.copy(alpha = .16f) else HomiraBlue.copy(alpha = .16f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Call, contentDescription = null, tint = HomiraGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Voice", color = HomiraGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Video", color = HomiraBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Videocam, contentDescription = null, tint = HomiraBlue, modifier = Modifier.size(18.dp))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .pointerInput(person.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, amount ->
                            dragOffset = (dragOffset + amount).coerceIn(-maxDrag, maxDrag)
                        },
                        onDragEnd = {
                            when {
                                dragOffset >= threshold -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onVoice()
                                }
                                dragOffset <= -threshold -> {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onVideo()
                                }
                            }
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
private fun MeScreen(
    name: String,
    username: String,
    about: String,
    email: String,
    avatarUri: String?,
    callCardUri: String?,
    onEdit: () -> Unit,
    onSettings: () -> Unit
) {
    val avatarBitmap = rememberBitmapP(avatarUri)
    val cardBitmap = rememberBitmapP(callCardUri)

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { HeaderActions("Me", onSettings) }
        item {
            Card(shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = HomiraSurface)) {
                Column {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(190.dp).background(HomiraSurfaceRaised),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cardBitmap != null) {
                            Image(cardBitmap, contentDescription = "Call card", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = HomiraMuted, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.height(6.dp))
                                Text("Add a call card", color = HomiraMuted, fontSize = 13.sp)
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (avatarBitmap != null) {
                            Image(avatarBitmap, contentDescription = "Profile photo", modifier = Modifier.size(92.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Surface(modifier = Modifier.size(92.dp), shape = CircleShape, color = HomiraGreen.copy(alpha = .13f)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(name.take(1).uppercase(), color = HomiraGreen, fontSize = 35.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(name, color = HomiraText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text("@$username", color = HomiraMuted, fontSize = 13.sp)
                        Spacer(Modifier.height(7.dp))
                        Text(about, color = HomiraText.copy(alpha = .82f), fontSize = 14.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = onEdit,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = HomiraText, contentColor = HomiraBackground)
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Edit profile", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item {
            SectionTitleP("Your profile")
            InfoRowP(Icons.Rounded.Phone, "Phone number", "+234 ••• ••••")
            InfoRowP(Icons.Rounded.Person, "Username", "@$username")
            InfoRowP(Icons.Rounded.Email, "Email", email)
            InfoRowP(Icons.Rounded.Info, "About", about)
        }
        item {
            SectionTitleP("Profile media")
            InfoRowP(Icons.Rounded.AddAPhoto, "Profile photo", if (avatarUri == null) "Add photo" else "Photo selected", onEdit)
            InfoRowP(Icons.Rounded.CameraAlt, "Call card", if (callCardUri == null) "Add image" else "Image selected", onEdit)
        }
    }
}

@Composable
private fun EditProfileScreen(
    name: String,
    username: String,
    about: String,
    email: String,
    avatarUri: String?,
    callCardUri: String?,
    onBack: () -> Unit,
    onSave: (String, String, String, String, String?, String?) -> Unit
) {
    var editedName by rememberSaveable { mutableStateOf(name) }
    var editedUsername by rememberSaveable { mutableStateOf(username) }
    var editedAbout by rememberSaveable { mutableStateOf(about) }
    var editedEmail by rememberSaveable { mutableStateOf(email) }
    var editedAvatar by rememberSaveable { mutableStateOf(avatarUri) }
    var editedCard by rememberSaveable { mutableStateOf(callCardUri) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) editedAvatar = uri.toString()
    }
    val cardPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) editedCard = uri.toString()
    }

    val avatarBitmap = rememberBitmapP(editedAvatar)
    val cardBitmap = rememberBitmapP(editedCard)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(HomiraBackground).safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = HomiraText) }
                Text("Edit profile", color = HomiraText, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onSave(editedName, editedUsername, editedAbout, editedEmail, editedAvatar, editedCard) }) {
                    Text("Save", color = HomiraGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = HomiraSurface)) {
                Column {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(180.dp).clickable { cardPicker.launch("image/*") }.background(HomiraSurfaceRaised),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cardBitmap != null) {
                            Image(cardBitmap, contentDescription = "Call card", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = HomiraMuted)
                                Spacer(Modifier.height(6.dp))
                                Text("Choose call card image", color = HomiraMuted, fontSize = 13.sp)
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(18.dp).clickable { avatarPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarBitmap != null) {
                            Image(avatarBitmap, contentDescription = "Avatar", modifier = Modifier.size(88.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Surface(modifier = Modifier.size(88.dp), shape = CircleShape, color = HomiraGreen.copy(alpha = .13f)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = HomiraGreen)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { ProfileFieldP("Name", editedName) { editedName = it } }
        item { ProfileFieldP("Username", editedUsername) { editedUsername = it.replace(" ", "").lowercase() } }
        item { ProfileFieldP("About", editedAbout) { editedAbout = it } }
        item { ProfileFieldP("Email", editedEmail) { editedEmail = it } }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, onVoicemail: () -> Unit) {
    var lowData by rememberSaveable { mutableStateOf(false) }
    var protectIp by rememberSaveable { mutableStateOf(false) }
    var notifications by rememberSaveable { mutableStateOf(true) }
    var darkMode by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(HomiraBackground).safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = HomiraText) }
                Text("Settings", color = HomiraText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            SectionTitleP("Calls")
            SettingsToggleP(Icons.Rounded.DataSaverOn, "Use less data for calls", "Reduce bitrate on mobile data", lowData) { lowData = it }
            SettingsRowP(Icons.Rounded.PhoneInTalk, "Call quality", "Automatic") { }
            SettingsRowP(Icons.Rounded.Voicemail, "Voicemail", "Custom voice greeting") { onVoicemail() }
            SettingsRowP(Icons.Rounded.Notifications, "Ringtone", "Default") { }
        }
        item {
            SectionTitleP("Privacy")
            SettingsToggleP(Icons.Rounded.Lock, "Protect IP in calls", "Use TURN relay for private IP handling", protectIp) { protectIp = it }
            SettingsRowP(Icons.Rounded.Block, "Blocked people", "Manage") { }
            SettingsRowP(Icons.Rounded.Security, "Account security", "PIN and recovery") { }
        }
        item {
            SectionTitleP("Notifications")
            SettingsToggleP(Icons.Rounded.Notifications, "Call notifications", "Incoming, missed calls and voicemail", notifications) { notifications = it }
        }
        item {
            SectionTitleP("Appearance")
            SettingsToggleP(Icons.Rounded.Palette, "Dark appearance", "Use Homira's dark theme", darkMode) { darkMode = it }
        }
    }
}

@Composable
private fun VoicemailSettingsScreen(onBack: () -> Unit) {
    var customGreeting by rememberSaveable { mutableStateOf(true) }
    var recording by rememberSaveable { mutableStateOf(false) }
    var recordingSeconds by rememberSaveable { mutableIntStateOf(0) }
    var previewPlaying by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(recording) {
        if (recording) {
            recordingSeconds = 0
            while (recording && recordingSeconds < 30) {
                delay(1_000)
                recordingSeconds++
            }
            if (recordingSeconds >= 30) recording = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(HomiraBackground).safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = HomiraText) }
                Column {
                    Text("Voicemail", color = HomiraText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("What callers hear when you don't answer", color = HomiraMuted, fontSize = 12.sp)
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = HomiraSurface)) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(46.dp), shape = CircleShape, color = HomiraGreen.copy(alpha = .12f)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Mic, contentDescription = null, tint = HomiraGreen)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Your greeting", color = HomiraText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (customGreeting) "Custom voice greeting" else "Default Homira greeting", color = HomiraMuted, fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip("My voice", customGreeting) { customGreeting = true }
                        FilterChip("Default", !customGreeting) { customGreeting = false }
                    }

                    if (customGreeting) {
                        Spacer(Modifier.height(18.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = HomiraSurfaceRaised
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { previewPlaying = !previewPlaying }) {
                                    Icon(
                                        if (previewPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = "Preview greeting",
                                        tint = HomiraGreen
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Current greeting", color = HomiraText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("0:07", color = HomiraMuted, fontSize = 12.sp)
                                }
                                Text("My voice", color = HomiraGreen, fontSize = 12.sp)
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { recording = !recording },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (recording) HomiraDanger else HomiraText,
                                contentColor = if (recording) Color.White else HomiraBackground
                            )
                        ) {
                            Icon(if (recording) Icons.Rounded.MicOff else Icons.Rounded.Mic, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (recording) "Stop recording · 0:${recordingSeconds.toString().padStart(2, '0')}" else "Record a new greeting")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Up to 30 seconds. Callers can leave a voice message after the greeting.",
                            color = HomiraMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        item {
            SectionTitleP("When you don't answer")
            InfoRowP(Icons.Rounded.PhoneMissed, "No answer", "Offer voicemail after the ring timeout")
            InfoRowP(Icons.Rounded.PhoneInTalk, "Busy", "Offer voicemail when you're already on a call")
            InfoRowP(Icons.Rounded.Voicemail, "Message length", "Up to 2 minutes")
        }
    }
}

@Composable
private fun IncomingCallScreen(
    person: HomiraPerson,
    video: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomiraBackground)
            .safeDrawingPadding()
            .padding(horizontal = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(.7f))
            Text(
                text = if (video) "Incoming video call" else "Incoming voice call",
                color = HomiraMuted,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = person.name,
                color = HomiraText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (person.number.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(person.number, color = HomiraMuted, fontSize = 13.sp)
            }

            Spacer(Modifier.height(34.dp))
            PersonAvatarP(person, 190)
            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(72.dp).clickable(onClick = onDecline),
                        shape = CircleShape,
                        color = HomiraDanger
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.CallEnd,
                                contentDescription = "Decline",
                                tint = Color.White,
                                modifier = Modifier.size(31.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Text("Decline", color = HomiraMuted, fontSize = 12.sp)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(72.dp).clickable(onClick = onAccept),
                        shape = CircleShape,
                        color = HomiraGreen
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (video) Icons.Rounded.Videocam else Icons.Rounded.Call,
                                contentDescription = "Accept",
                                tint = HomiraBackground,
                                modifier = Modifier.size(31.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Text("Accept", color = HomiraMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(42.dp))
        }
    }
}

@Composable
private fun ActiveCallScreen(
    person: HomiraPerson,
    startsWithVideo: Boolean,
    liveState: String? = null,
    mediaState: HomiraWebRtcState? = null,
    videoEnabled: Boolean = startsWithVideo,
    remoteVideoEnabled: Boolean = startsWithVideo,
    remoteMuted: Boolean = false,
    screenSharing: Boolean = false,
    remoteScreenSharing: Boolean = false,
    localVideoTrack: VideoTrack? = null,
    remoteVideoTrack: VideoTrack? = null,
    eglContext: EglBase.Context? = null,
    onMuteChanged: (Boolean) -> Unit = {},
    onSpeakerChanged: (Boolean) -> Unit = {},
    onVideoChanged: (Boolean) -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    onScreenShareChanged: (Boolean) -> Unit = {},
    onMinimize: () -> Unit,
    onEnd: () -> Unit
) {
    var muted by rememberSaveable { mutableStateOf(false) }
    var speaker by rememberSaveable { mutableStateOf(startsWithVideo) }
    val localVideo = videoEnabled && !screenSharing
    val video = localVideo || remoteVideoEnabled || remoteScreenSharing
    var simulatedConnected by rememberSaveable { mutableStateOf(false) }
    var seconds by rememberSaveable { mutableIntStateOf(0) }
    val connected = if (liveState == null) {
        simulatedConnected
    } else {
        mediaState == HomiraWebRtcState.Connected
    }
    val statusText = when {
        connected -> formatDurationP(seconds)
        liveState == "ringing" -> "Ringing…"
        mediaState == HomiraWebRtcState.Failed -> "Connection failed"
        mediaState == HomiraWebRtcState.Disconnected -> "Reconnecting…"
        else -> "Connecting…"
    }
    var menuOpen by remember { mutableStateOf(false) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(startsWithVideo, onSpeakerChanged) {
        if (startsWithVideo) {
            onSpeakerChanged(true)
        }
    }
    LaunchedEffect(liveState) {
        if (liveState == null && !simulatedConnected) {
            delay(650)
            simulatedConnected = true
        }
    }
    LaunchedEffect(connected) {
        if (connected) {
            while (true) {
                delay(1_000)
                seconds++
            }
        }
    }
    LaunchedEffect(video, controlsVisible) {
        if (video && controlsVisible) {
            delay(3_500)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (video) Color.Black else HomiraBackground)
            .safeDrawingPadding()
            .clickable { if (video) controlsVisible = true }
    ) {
        if (video) {
            if (remoteVideoEnabled && remoteVideoTrack != null && eglContext != null) {
                WebRtcVideoSurface(
                    track = remoteVideoTrack,
                    eglContext = eglContext,
                    mirror = false,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (liveState == null) {
                        Text("Camera preview", color = HomiraMuted, fontSize = 14.sp)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PersonAvatarP(person, 128)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Waiting for video…",
                                color = Color.White.copy(alpha = .72f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (localVideo && localVideoTrack != null && eglContext != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 72.dp, end = 16.dp)
                        .width(108.dp)
                        .height(156.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = HomiraSurfaceRaised,
                    shadowElevation = 8.dp
                ) {
                    WebRtcVideoSurface(
                        track = localVideoTrack,
                        eglContext = eglContext,
                        mirror = true,
                        overlay = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(!video || controlsVisible) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMinimize) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimize", tint = HomiraText)
                    }
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = HomiraText)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (screenSharing) "Stop sharing screen" else "Share screen") },
                                leadingIcon = { Icon(Icons.Rounded.ScreenShare, contentDescription = null) },
                                onClick = {
                                    onScreenShareChanged(!screenSharing)
                                    menuOpen = false
                                }
                            )
                            if (localVideo) {
                                DropdownMenuItem(
                                    text = { Text("Switch camera") },
                                    leadingIcon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
                                    onClick = {
                                        onSwitchCamera()
                                        menuOpen = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Call info") },
                                leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                                onClick = { menuOpen = false }
                            )
                        }
                    }
                }
            }

            if (!video) {
                Spacer(Modifier.height(16.dp))
                Text(person.name, color = HomiraText, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(statusText, color = HomiraMuted, fontSize = 15.sp)
                Spacer(Modifier.weight(.32f))
                PersonAvatarP(person, 184)
                Spacer(Modifier.weight(.32f))
            } else {
                AnimatedVisibility(controlsVisible) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(person.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(statusText, color = Color.White.copy(alpha = .72f), fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
            }

            AnimatedVisibility(muted && (!video || controlsVisible)) {
                Surface(shape = RoundedCornerShape(99.dp), color = HomiraDanger.copy(alpha = .16f)) {
                    Row(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.MicOff, contentDescription = null, tint = HomiraDanger, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("You're muted", color = HomiraText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            AnimatedVisibility(remoteMuted && (!video || controlsVisible)) {
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = HomiraSurfaceRaised.copy(alpha = .92f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.MicOff,
                            contentDescription = null,
                            tint = HomiraMuted,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${person.name} is muted",
                            color = HomiraText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            AnimatedVisibility(screenSharing && (!video || controlsVisible)) {
                Text(
                    "Sharing your screen",
                    color = HomiraGreen,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            AnimatedVisibility(remoteScreenSharing && (!video || controlsVisible)) {
                Text(
                    "${person.name} is sharing their screen",
                    color = Color.White.copy(alpha = .82f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            AnimatedVisibility(!video || controlsVisible) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CallControlP(Icons.Rounded.MicOff, "Mute", muted) {
                            muted = !muted
                            onMuteChanged(muted)
                        }
                        CallControlP(Icons.Rounded.VolumeUp, "Speaker", speaker) {
                            speaker = !speaker
                            onSpeakerChanged(speaker)
                        }
                        CallControlP(Icons.Rounded.Videocam, "Video", localVideo) {
                            onVideoChanged(!localVideo)
                            controlsVisible = true
                        }
                    }
                    Spacer(Modifier.height(26.dp))
                    Surface(modifier = Modifier.size(68.dp).clickable(onClick = onEnd), shape = CircleShape, color = HomiraDanger) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CallEnd, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(30.dp))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun WebRtcVideoSurface(
    track: VideoTrack,
    eglContext: EglBase.Context,
    mirror: Boolean,
    overlay: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember(context, eglContext, mirror) {
        SurfaceViewRenderer(context).apply {
            init(eglContext, null)
            setMirror(mirror)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setZOrderMediaOverlay(overlay)
        }
    }

    DisposableEffect(track, renderer) {
        track.addSink(renderer)
        onDispose {
            track.removeSink(renderer)
        }
    }

    DisposableEffect(renderer) {
        onDispose {
            renderer.release()
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}

@Composable
private fun FloatingBottomBar(tab: MainTab, onSelect: (MainTab) -> Unit) {
    Box(Modifier.fillMaxWidth().background(HomiraBackground).navigationBarsPadding().padding(horizontal = 22.dp, vertical = 8.dp)) {
        Surface(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = HomiraSurface,
            tonalElevation = 0.dp
        ) {
            Row(Modifier.padding(5.dp)) {
                BottomPill(MainTab.Keypad, "Keypad", Icons.Rounded.Dialpad, tab == MainTab.Keypad, Modifier.weight(1f), onSelect)
                BottomPill(MainTab.Recents, "Recents", Icons.Rounded.History, tab == MainTab.Recents, Modifier.weight(1f), onSelect)
                BottomPill(MainTab.Contacts, "Contacts", Icons.Rounded.Contacts, tab == MainTab.Contacts, Modifier.weight(1f), onSelect)
                BottomPill(MainTab.Me, "Me", Icons.Rounded.Person, tab == MainTab.Me, Modifier.weight(1f), onSelect)
            }
        }
    }
}

@Composable
private fun BottomPill(
    tab: MainTab,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onSelect: (MainTab) -> Unit
) {
    Surface(
        modifier = modifier.clickable { onSelect(tab) },
        shape = RoundedCornerShape(26.dp),
        color = if (selected) HomiraSurfaceRaised else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = if (selected) HomiraText else HomiraMuted, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(3.dp))
            Text(label, color = if (selected) HomiraText else HomiraMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun OngoingCallBanner(person: HomiraPerson, onReturn: () -> Unit, onEnd: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onReturn), color = HomiraGreen.copy(alpha = .12f)) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PhoneInTalk, contentDescription = null, tint = HomiraGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Call with ${person.name}", color = HomiraText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onEnd, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.CallEnd, contentDescription = "End call", tint = HomiraDanger, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun PersonAvatarP(person: HomiraPerson, size: Int) {
    Surface(modifier = Modifier.size(size.dp), shape = CircleShape, color = person.accent.copy(alpha = .13f)) {
        Box(contentAlignment = Alignment.Center) {
            Text(person.marker, color = person.accent, fontSize = (size * .38f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CircleAction(icon: ImageVector, accent: Color, label: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.size(40.dp).clickable(onClick = onClick), shape = CircleShape, color = accent.copy(alpha = .13f)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CallControlP(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(56.dp).clickable(onClick = onClick),
            shape = CircleShape,
            color = if (active) HomiraText else HomiraSurfaceRaised
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = if (active) HomiraBackground else HomiraText, modifier = Modifier.size(23.dp))
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = HomiraMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SectionTitleP(title: String) {
    Text(title, color = HomiraMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun InfoRowP(icon: ImageVector, title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = HomiraMuted, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = HomiraText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(value, color = HomiraMuted, fontSize = 12.sp)
        }
    }
    HorizontalDivider(color = HomiraLine.copy(alpha = .6f))
}

@Composable
private fun SettingsRowP(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    InfoRowP(icon, title, subtitle, onClick)
}

@Composable
private fun SettingsToggleP(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = HomiraMuted, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = HomiraText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = HomiraMuted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
    HorizontalDivider(color = HomiraLine.copy(alpha = .6f))
}

@Composable
private fun ProfileFieldP(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = label != "About",
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun rememberBitmapP(uriString: String?) = run {
    val context = LocalContext.current
    remember(uriString, context) {
        if (uriString.isNullOrBlank()) null else runCatching {
            context.contentResolver.openInputStream(android.net.Uri.parse(uriString))?.use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }.getOrNull()
    }
}

private fun directionIcon(direction: CallDirection): ImageVector = when (direction) {
    CallDirection.Incoming -> Icons.Rounded.CallReceived
    CallDirection.Outgoing -> Icons.Rounded.CallMade
    CallDirection.Missed -> Icons.Rounded.PhoneMissed
    CallDirection.Declined -> Icons.Rounded.PhoneMissed
    CallDirection.Failed -> Icons.Rounded.PhoneMissed
}

private fun directionColor(direction: CallDirection): Color = when (direction) {
    CallDirection.Missed, CallDirection.Declined, CallDirection.Failed -> HomiraDanger
    else -> HomiraBlue
}

private fun directionLabel(direction: CallDirection): String = when (direction) {
    CallDirection.Incoming -> "Incoming"
    CallDirection.Outgoing -> "Outgoing"
    CallDirection.Missed -> "Missed"
    CallDirection.Declined -> "Declined"
    CallDirection.Failed -> "Failed"
}

private fun digitsOnlyP(value: String): String = value.filter { it.isDigit() }

private fun formatDialNumberP(value: String): String {
    val digits = value.filter { it.isDigit() }
    return when {
        value.startsWith("+") -> value
        digits.length <= 4 -> digits
        digits.length <= 7 -> "${digits.take(4)} ${digits.drop(4)}"
        digits.length <= 11 -> "${digits.take(4)} ${digits.drop(4).take(3)} ${digits.drop(7)}"
        else -> value
    }
}

private fun formatDurationP(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
