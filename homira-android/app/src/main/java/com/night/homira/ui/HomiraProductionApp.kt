package com.night.homira.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.night.homira.BuildConfig
import com.night.homira.call.HomiraWebRtcState
import com.night.homira.call.HomiraAudioRecorder
import com.night.homira.call.HomiraAudioPlayer
import com.night.homira.call.HomiraCallUiState
import com.night.homira.call.HomiraScreenShareService
import com.night.homira.call.HomiraIncomingCallNotifier
import com.night.homira.call.HomiraPushBootstrap
import com.night.homira.call.HomiraWebRtcVoiceEngine
import com.night.homira.call.HomiraTelecomBridge
import com.night.homira.call.HomiraTelecomPlatformEvent
import com.night.homira.data.HomiraCallSignaling
import com.night.homira.data.HomiraLiveRepository
import com.night.homira.data.HomiraSettingsStore
import com.night.homira.data.HomiraCallHistoryStore
import com.night.homira.data.LocalCallHistoryRecord
import com.night.homira.data.LiveProfile
import com.night.homira.data.LiveContact
import com.night.homira.data.LiveCallSession
import com.night.homira.data.LiveVoicemail
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class MainTab { Keypad, Recents, Contacts, Me }
private enum class OverlayScreen { None, EditProfile, Voicemail, AddContact, BlockedPeople }
private enum class CallDirection { Incoming, Outgoing, Missed, Declined, Cancelled, Failed }
private enum class RecentFilter { All, Missed, Rejected, Outgoing, Incoming, Voicemail }

private const val HOMIRA_RING_WINDOW_MS = 30_000L

private fun remainingRingWindowMs(session: LiveCallSession): Long {
    val nowMs = System.currentTimeMillis()

    fun parseMillis(value: String): Long? =
        runCatching {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        }.recoverCatching {
            Instant.parse(value).toEpochMilli()
        }.getOrNull()

    val createdDeadline = parseMillis(session.createdAt)
        ?.plus(HOMIRA_RING_WINDOW_MS)
    val serverDeadline = parseMillis(session.expiresAt)

    val deadline = listOfNotNull(
        createdDeadline,
        serverDeadline
    ).minOrNull() ?: (nowMs + HOMIRA_RING_WINDOW_MS)

    return (deadline - nowMs)
        .coerceIn(0L, HOMIRA_RING_WINDOW_MS)
}

private data class HomiraPerson(
    val id: String,
    val name: String,
    val marker: String,
    val accent: Color,
    val number: String,
    val favorite: Boolean = false,
    val avatarUri: String? = null,
    val callCardUri: String? = null
)

private data class VoicemailOffer(
    val person: HomiraPerson,
    val callSessionId: String,
    val wasVideo: Boolean,
    val greetingPath: String?
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
    val voicemailSeconds: Int? = null,
    val voicemailId: String? = null,
    val voicemailListened: Boolean = true,
    val callSessionId: String? = null,
    val timestampMillis: Long = 0L
)

private fun normalizeDirectDialP(value: String): String? {
    val trimmed = value.trim()
    if (!trimmed.startsWith("+")) return null

    val digits = trimmed.drop(1).filter(Char::isDigit)
    if (digits.length !in 7..15) return null

    return "+$digits"
}

private fun prepareProfileJpeg(
    context: Context,
    uriString: String,
    maxDimension: Int,
    quality: Int = 88
): ByteArray {
    val uri = android.net.Uri.parse(uriString)
    val bitmap = context.contentResolver
        .openInputStream(uri)
        ?.use(BitmapFactory::decodeStream)
        ?: error("Could not read the selected image")

    val largest = maxOf(bitmap.width, bitmap.height)
    val output = if (largest > maxDimension) {
        val scale = maxDimension.toFloat() / largest.toFloat()
        android.graphics.Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    } else {
        bitmap
    }

    return try {
        ByteArrayOutputStream().use { stream ->
            check(
                output.compress(
                    android.graphics.Bitmap.CompressFormat.JPEG,
                    quality.coerceIn(70, 95),
                    stream
                )
            ) {
                "Could not encode the selected image"
            }
            stream.toByteArray()
        }
    } finally {
        if (output !== bitmap) output.recycle()
        bitmap.recycle()
    }
}

private suspend fun cacheProfileMediaP(
    context: Context,
    repository: HomiraLiveRepository,
    storagePath: String?,
    cacheKey: String
): String? {
    if (storagePath.isNullOrBlank()) return null

    return runCatching {
        val bytes = repository.downloadProfileMedia(storagePath)
        withContext(Dispatchers.IO) {
            val safeKey = cacheKey
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .take(80)
            File(
                context.cacheDir,
                "homira-profile-$safeKey.jpg"
            ).apply {
                writeBytes(bytes)
            }
        }
    }.getOrNull()?.let { file ->
        android.net.Uri.fromFile(file).toString()
    }
}

private fun CallEntry.voicemailPlaybackKey(): String = voicemailId ?: id

private fun voicemailDateParts(createdAt: String): Pair<String, String> =
    runCatching {
        val local = OffsetDateTime.parse(createdAt)
            .atZoneSameInstant(ZoneId.systemDefault())
        val date = local.toLocalDate()
        val today = LocalDate.now(local.zone)
        val day = when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.dayOfWeek.name
                .lowercase()
                .replaceFirstChar { it.uppercase() }
        }
        day to local.format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("Recent" to "")

private fun callDateParts(timestampMillis: Long): Pair<String, String> {
    val local = Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
    val date = local.toLocalDate()
    val today = LocalDate.now(local.zone)
    val day = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.dayOfWeek.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    }
    return day to local.format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun formatHistoryDuration(seconds: Long?): String? {
    val value = seconds ?: return null
    return when {
        value < 60 -> "${value}s"
        value < 3600 -> "${value / 60}m"
        else -> {
            val hours = value / 3600
            val minutes = (value % 3600) / 60
            if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
        }
    }
}

private fun LocalCallHistoryRecord.toCallEntry(): CallEntry {
    val (day, time) = callDateParts(startedAt)
    val callDirection = when (outcome) {
        HomiraCallHistoryStore.OUTCOME_MISSED -> CallDirection.Missed
        HomiraCallHistoryStore.OUTCOME_DECLINED -> CallDirection.Declined
        HomiraCallHistoryStore.OUTCOME_CANCELLED -> CallDirection.Cancelled
        HomiraCallHistoryStore.OUTCOME_FAILED -> CallDirection.Failed
        else -> if (direction == HomiraCallHistoryStore.DIRECTION_INCOMING) {
            CallDirection.Incoming
        } else {
            CallDirection.Outgoing
        }
    }

    return CallEntry(
        id = "history-$id",
        person = HomiraPerson(
            id = peerUserId,
            name = peerName,
            marker = peerName.firstOrNull()?.uppercaseChar()?.toString() ?: "H",
            accent = HomiraGreen,
            number = peerNumber
        ),
        day = day,
        time = time,
        direction = callDirection,
        video = mediaType == "video",
        duration = formatHistoryDuration(durationSeconds),
        callSessionId = id,
        timestampMillis = startedAt
    )
}

private fun voicemailTimestampMillis(createdAt: String): Long =
    runCatching {
        OffsetDateTime.parse(createdAt).toInstant().toEpochMilli()
    }.getOrDefault(System.currentTimeMillis())

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
fun HomiraProductionApp(
    repository: HomiraLiveRepository? = null,
    initialProfile: LiveProfile? = null,
    initialContacts: List<LiveContact> = emptyList(),
    liveMode: Boolean = false,
    backendReady: Boolean = true,
    requestedCallId: String? = null,
    requestedAnswerCall: Boolean = false,
    onSignedOut: () -> Unit = {}
) {
    HomiraTheme {
        val context = LocalContext.current
        val fallbackRepository = remember { HomiraLiveRepository() }
        val liveRepository = repository ?: fallbackRepository
        val liveBackendReady = liveMode && backendReady
        val liveScope = rememberCoroutineScope()
        val settingsStore = remember(context) { HomiraSettingsStore(context) }
        var localSettings by remember { mutableStateOf(settingsStore.load()) }
        val incomingCallNotifier = remember(context) {
            runCatching {
                HomiraIncomingCallNotifier(context)
            }.getOrNull()
        }
        var telecomBridge by remember(context) {
            mutableStateOf<HomiraTelecomBridge?>(null)
        }

        LaunchedEffect(liveBackendReady) {
            if (!liveBackendReady) return@LaunchedEffect
            HomiraPushBootstrap.requestRegistration(context)
            runCatching {
                HomiraPushBootstrap.syncStoredToken(
                    context = context,
                    repository = liveRepository
                )
            }
        }
        var notificationPermissionGranted by remember {
            mutableStateOf(
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
            )
        }
        val notificationPermissionLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                notificationPermissionGranted = granted
                settingsStore.setNotificationPermissionRequested(true)
                if (!granted) {
                    settingsStore.setCallNotifications(false)
                }
                localSettings = settingsStore.load()
            }

        val ringtonePickerLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    @Suppress("DEPRECATION")
                    val pickedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        result.data?.getParcelableExtra(
                            RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                            Uri::class.java
                        )
                    } else {
                        result.data?.getParcelableExtra(
                            RingtoneManager.EXTRA_RINGTONE_PICKED_URI
                        )
                    }
                    settingsStore.setRingtoneUri(pickedUri?.toString())
                    localSettings = settingsStore.load()
                }
            }

        val ringtoneTitle = remember(localSettings.ringtoneUri) {
            val uri = localSettings.ringtoneUri
                ?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_RINGTONE
                )

            runCatching {
                RingtoneManager.getRingtone(context, uri)
                    ?.getTitle(context)
            }.getOrNull()
                ?: "System default"
        }

        LaunchedEffect(
            liveBackendReady,
            localSettings.callNotifications,
            localSettings.notificationPermissionRequested,
            notificationPermissionGranted
        ) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                liveBackendReady &&
                localSettings.callNotifications &&
                !localSettings.notificationPermissionRequested &&
                !notificationPermissionGranted
            ) {
                runCatching {
                    notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }
            }
        }
        val historyOwnerKey =
            if (liveMode) {
                liveRepository.currentUserId() ?: initialProfile?.id ?: "signed-out"
            } else {
                "demo"
            }
        val callHistoryStore = remember(context, historyOwnerKey) {
            HomiraCallHistoryStore(
                context = context,
                ownerKey = historyOwnerKey
            )
        }
        var localCallHistory by remember {
            mutableStateOf<List<LocalCallHistoryRecord>>(emptyList())
        }
        var tab by rememberSaveable { mutableStateOf(MainTab.Keypad) }
        val tabStateHolder = rememberSaveableStateHolder()
        var overlay by rememberSaveable { mutableStateOf(OverlayScreen.None) }
        var activePerson by remember { mutableStateOf<HomiraPerson?>(null) }
        var activeVideo by rememberSaveable { mutableStateOf(false) }
        var minimized by rememberSaveable { mutableStateOf(false) }
        var resolvingDial by remember { mutableStateOf(false) }
        var activeSession by remember { mutableStateOf<LiveCallSession?>(null) }
        var incomingSession by remember { mutableStateOf<LiveCallSession?>(null) }
        var incomingPerson by remember { mutableStateOf<HomiraPerson?>(null) }
        var voicemailOffer by remember { mutableStateOf<VoicemailOffer?>(null) }
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

        LaunchedEffect(
            activePerson?.id,
            activeVideo,
            remoteVideoEnabled,
            remoteScreenSharing
        ) {
            HomiraCallUiState.videoCallActive =
                activePerson != null &&
                    (
                        activeVideo ||
                            remoteVideoEnabled ||
                            remoteScreenSharing
                    )
        }

        DisposableEffect(Unit) {
            onDispose {
                HomiraCallUiState.videoCallActive = false
            }
        }

        BackHandler(
            enabled =
                (activePerson != null && !minimized) ||
                    voicemailOffer != null ||
                    overlay != OverlayScreen.None
        ) {
            when {
                activePerson != null && !minimized -> {
                    minimized = true
                }

                voicemailOffer != null -> {
                    voicemailOffer = null
                    tab = MainTab.Recents
                }

                overlay == OverlayScreen.Voicemail ||
                    overlay == OverlayScreen.BlockedPeople -> {
                    overlay = OverlayScreen.None
                    tab = MainTab.Me
                }

                overlay != OverlayScreen.None -> {
                    overlay = OverlayScreen.None
                }
            }
        }

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
                val foregroundStarted = runCatching {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, HomiraScreenShareService::class.java)
                    )
                }.isSuccess

                if (!foregroundStarted) {
                    Toast.makeText(
                        context,
                        "Could not start screen sharing.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@rememberLauncherForActivityResult
                }

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
        var profilePhone by rememberSaveable(initialProfile?.id) {
            mutableStateOf(initialProfile?.phoneE164.orEmpty())
        }
        var voicemailEnabled by rememberSaveable(initialProfile?.id) {
            mutableStateOf(initialProfile?.voicemailEnabled ?: true)
        }
        var voicemailGreetingMode by rememberSaveable(initialProfile?.id) {
            mutableStateOf(initialProfile?.voicemailGreetingMode ?: "default")
        }
        var voicemailGreetingPath by rememberSaveable(initialProfile?.id) {
            mutableStateOf(initialProfile?.voicemailGreetingPath)
        }
        var avatarStoragePath by rememberSaveable(initialProfile?.id) {
            mutableStateOf(initialProfile?.avatarPath)
        }
        var callCardStoragePath by rememberSaveable(initialProfile?.id) {
            mutableStateOf(initialProfile?.callCardPath)
        }
        var avatarUri by rememberSaveable(initialProfile?.id) {
            mutableStateOf<String?>(null)
        }
        var callCardUri by rememberSaveable(initialProfile?.id) {
            mutableStateOf<String?>(null)
        }
        var profileSaving by remember { mutableStateOf(false) }

        LaunchedEffect(liveBackendReady, initialProfile?.id) {
            if (!liveBackendReady) return@LaunchedEffect
            val ownerId = initialProfile?.id ?: return@LaunchedEffect

            avatarUri = cacheProfileMediaP(
                context = context,
                repository = liveRepository,
                storagePath = initialProfile.avatarPath,
                cacheKey = "$ownerId-avatar"
            )
            callCardUri = null
        }

        var liveContacts by remember(initialContacts) { mutableStateOf(initialContacts) }
        var contactMediaUris by remember {
            mutableStateOf<Map<String, Pair<String?, String?>>>(emptyMap())
        }
        var blockedUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }
        var liveVoicemails by remember { mutableStateOf<List<LiveVoicemail>>(emptyList()) }
        var liveVoicemailEntries by remember { mutableStateOf<List<CallEntry>>(emptyList()) }
        val receivedVoicemailPlayer = remember { HomiraAudioPlayer() }
        var playingVoicemailId by rememberSaveable { mutableStateOf<String?>(null) }
        var voicemailPlaybackFile by remember { mutableStateOf<File?>(null) }

        LaunchedEffect(liveBackendReady, liveContacts) {
            if (!liveBackendReady) return@LaunchedEffect

            val media = linkedMapOf<String, Pair<String?, String?>>()
            liveContacts.forEach { contact ->
                val avatar = cacheProfileMediaP(
                    context = context,
                    repository = liveRepository,
                    storagePath = contact.avatarPath,
                    cacheKey = "${contact.id}-avatar"
                )
                media[contact.id] = avatar to null
            }
            contactMediaUris = media
        }

        val appContacts = remember(liveContacts, contactMediaUris) {
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
                    favorite = contact.favorite,
                    avatarUri = contactMediaUris[contact.id]?.first,
                    callCardUri = contactMediaUris[contact.id]?.second
                )
            }
        }
        val localCallEntries = remember(localCallHistory, appContacts) {
            val currentPeople = appContacts.associateBy { it.id }
            localCallHistory.map { record ->
                val entry = record.toCallEntry()
                val currentPerson = currentPeople[entry.person.id]
                if (currentPerson != null) {
                    entry.copy(person = currentPerson)
                } else {
                    entry
                }
            }
        }

        val appCallEntries = if (liveMode) {
            val voicemailBySession = liveVoicemailEntries
                .filter { it.callSessionId != null }
                .associateBy { it.callSessionId }

            val enrichedHistory = localCallEntries.map { historyEntry ->
                val voicemail = voicemailBySession[historyEntry.callSessionId]
                if (voicemail == null) {
                    historyEntry
                } else {
                    historyEntry.copy(
                        voicemailSeconds = voicemail.voicemailSeconds,
                        voicemailId = voicemail.voicemailId,
                        voicemailListened = voicemail.voicemailListened
                    )
                }
            }

            val matchedSessions = localCallEntries
                .mapNotNull { it.callSessionId }
                .toSet()

            val orphanVoicemails = liveVoicemailEntries.filter {
                it.callSessionId == null || it.callSessionId !in matchedSessions
            }

            (enrichedHistory + orphanVoicemails)
                .sortedByDescending { it.timestampMillis }
        } else {
            callEntries
        }

        LaunchedEffect(liveBackendReady) {
            if (!liveBackendReady) return@LaunchedEffect
            runCatching {
                callHistoryStore.normalizeInterruptedRinging()
                callHistoryStore.listRecent()
            }.onSuccess { history ->
                localCallHistory = history
            }.onFailure {
                Log.e("HomiraStartup", "Call history startup failed", it)
            }
        }

        LaunchedEffect(liveBackendReady) {
            if (!liveBackendReady) return@LaunchedEffect
            blockedUserIds = runCatching {
                liveRepository.listBlockedUserIds()
            }.getOrDefault(emptySet())
        }

        LaunchedEffect(
            liveBackendReady,
            appContacts,
            requestedCallId,
            requestedAnswerCall
        ) {
            if (!liveBackendReady) return@LaunchedEffect

            runCatching {
                val knownPeople = appContacts.associateBy { it.id }

            suspend fun toRecentEntry(voicemail: LiveVoicemail): CallEntry {
                val person = knownPeople[voicemail.senderId] ?: run {
                    val profile = liveRepository.loadProfileById(voicemail.senderId)
                    val visibleName = profile?.displayName?.takeIf { it.isNotBlank() }
                        ?: profile?.username?.takeIf { it.isNotBlank() }
                        ?: profile?.phoneE164
                        ?: "Homira caller"

                    HomiraPerson(
                        id = voicemail.senderId,
                        name = visibleName,
                        marker = visibleName.firstOrNull()?.uppercaseChar()?.toString() ?: "H",
                        accent = HomiraGreen,
                        number = profile?.phoneE164.orEmpty(),
                        avatarUri = cacheProfileMediaP(
                            context,
                            liveRepository,
                            profile?.avatarPath,
                            "${voicemail.senderId}-avatar"
                        ),
                        callCardUri = null
                    )
                }

                val (day, time) = voicemailDateParts(voicemail.createdAt)
                return CallEntry(
                    id = "voicemail-${voicemail.id}",
                    person = person,
                    day = day,
                    time = time,
                    direction = CallDirection.Missed,
                    video = false,
                    voicemailSeconds = (voicemail.durationMs / 1000.0)
                        .roundToInt()
                        .coerceAtLeast(1),
                    voicemailId = voicemail.id,
                    voicemailListened = voicemail.listenedAt != null,
                    callSessionId = voicemail.callSessionId,
                    timestampMillis = voicemailTimestampMillis(voicemail.createdAt)
                )
            }

            val messages = runCatching {
                liveRepository.listReceivedVoicemails()
            }.getOrDefault(emptyList())

            liveVoicemails = messages
            liveVoicemailEntries = messages.map { toRecentEntry(it) }

            liveRepository.observeReceivedVoicemailChanges().collect { voicemail ->
                val existingMessageIndex = liveVoicemails.indexOfFirst { it.id == voicemail.id }
                liveVoicemails = if (existingMessageIndex >= 0) {
                    liveVoicemails.toMutableList().apply {
                        this[existingMessageIndex] = voicemail
                    }
                } else {
                    listOf(voicemail) + liveVoicemails
                }

                val entry = toRecentEntry(voicemail)
                val existingEntryIndex =
                    liveVoicemailEntries.indexOfFirst { it.voicemailId == voicemail.id }

                liveVoicemailEntries = if (existingEntryIndex >= 0) {
                    liveVoicemailEntries.toMutableList().apply {
                        this[existingEntryIndex] = entry
                    }
                } else {
                    listOf(entry) + liveVoicemailEntries
                }
            }
            }.onFailure {
                Log.e("HomiraRealtime", "Voicemail sync stopped", it)
            }
        }

        fun toggleVoicemailPlayback(entry: CallEntry) {
            val playbackKey = entry.voicemailPlaybackKey()

            if (playingVoicemailId == playbackKey) {
                receivedVoicemailPlayer.stop()
                voicemailPlaybackFile?.delete()
                voicemailPlaybackFile = null
                playingVoicemailId = null
                return
            }

            if (!liveMode || entry.voicemailId == null) {
                playingVoicemailId = playbackKey
                return
            }

            val voicemail = liveVoicemails.firstOrNull { it.id == entry.voicemailId }
                ?: return

            liveScope.launch {
                runCatching {
                    liveRepository.downloadVoicemail(voicemail)
                }.onSuccess { audioBytes ->
                    receivedVoicemailPlayer.stop()
                    voicemailPlaybackFile?.delete()

                    val file = File.createTempFile(
                        "homira-voicemail-",
                        ".m4a",
                        context.cacheDir
                    ).apply {
                        writeBytes(audioBytes)
                    }

                    voicemailPlaybackFile = file
                    playingVoicemailId = playbackKey

                    receivedVoicemailPlayer.play(file) {
                        if (playingVoicemailId == playbackKey) {
                            playingVoicemailId = null
                        }
                        if (voicemailPlaybackFile == file) {
                            voicemailPlaybackFile = null
                        }
                        file.delete()
                    }

                    if (voicemail.listenedAt == null) {
                        runCatching {
                            liveRepository.markVoicemailListened(voicemail.id)
                        }.onSuccess { updated ->
                            liveVoicemails = liveVoicemails.map {
                                if (it.id == updated.id) updated else it
                            }
                            liveVoicemailEntries = liveVoicemailEntries.map {
                                if (it.voicemailId == updated.id) {
                                    it.copy(voicemailListened = true)
                                } else {
                                    it
                                }
                            }
                        }
                    }
                }.onFailure {
                    playingVoicemailId = null
                    Toast.makeText(
                        context,
                        it.message ?: "Could not play this voicemail.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                receivedVoicemailPlayer.stop()
                voicemailPlaybackFile?.delete()
            }
        }

        fun startCallNow(person: HomiraPerson, video: Boolean) {
            liveScope.launch {
                runCatching {
                    liveRepository.startCall(calleeId = person.id, video = video)
                }.onSuccess { session ->
                    val pushResult = runCatching {
                        withTimeoutOrNull(6_000L) {
                            liveRepository.requestIncomingCallPush(session.id)
                        }
                    }.onFailure {
                        Log.e(
                            "HomiraPush",
                            "Incoming-call push request failed for ${session.id}",
                            it
                        )
                    }.getOrNull()

                    if (pushResult == null) {
                        Log.w(
                            "HomiraPush",
                            "Incoming-call push timed out for ${session.id}"
                        )
                    } else if (pushResult.delivered <= 0) {
                        Log.w(
                            "HomiraPush",
                            "Incoming-call push was not accepted: " +
                                "reason=${pushResult.reason} error=${pushResult.error}"
                        )
                    }

                    val refreshedPerson =
                        if (person.avatarUri == null) {
                            val profile = liveRepository.loadProfileById(person.id)
                            if (profile == null) {
                                person
                            } else {
                                val refreshedName = profile.displayName
                                    .takeIf { it.isNotBlank() }
                                    ?: profile.username
                                    ?: profile.phoneE164
                                    ?: person.name

                                person.copy(
                                    name = refreshedName,
                                    marker = refreshedName
                                        .firstOrNull()
                                        ?.uppercaseChar()
                                        ?.toString()
                                        ?: person.marker,
                                    number = profile.phoneE164
                                        ?: person.number,
                                    avatarUri = cacheProfileMediaP(
                                        context,
                                        liveRepository,
                                        profile.avatarPath,
                                        "${person.id}-avatar"
                                    ),
                                    callCardUri = null
                                )
                            }
                        } else {
                            person
                        }

                    callHistoryStore.recordRinging(
                        id = session.id,
                        peerUserId = refreshedPerson.id,
                        peerName = refreshedPerson.name,
                        peerNumber = refreshedPerson.number,
                        direction = HomiraCallHistoryStore.DIRECTION_OUTGOING,
                        mediaType = session.mediaType
                    )
                    localCallHistory = callHistoryStore.listRecent()

                    activeSession = session
                    activePerson = refreshedPerson
                    activeVideo = video
                    minimized = false
                    incomingCallNotifier?.showOngoing(
                        callId = session.id,
                        mediaType = session.mediaType,
                        peerName = refreshedPerson.name,
                        calling = true
                    )
                }.onFailure {
                    Toast.makeText(
                        context,
                        it.message ?: "Could not start the call.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        fun finishLiveCall(session: LiveCallSession?) {
            if (!liveMode || session == null) return

            liveScope.launch {
                val localUserId = liveRepository.currentUserId()
                val terminalState = when (session.state) {
                    "ringing", "connecting" -> {
                        if (session.callerId == localUserId) {
                            "cancelled"
                        } else {
                            "declined"
                        }
                    }
                    else -> "ended"
                }

                runCatching { telecomBridge?.disconnect() }

                val ended = runCatching {
                    liveRepository.setCallState(
                        session.id,
                        terminalState
                    )
                }.getOrNull()

                if (
                    ended != null &&
                    terminalState == "cancelled" &&
                    session.callerId == localUserId
                ) {
                    runCatching {
                        liveRepository.requestMissedCallPush(
                            session.id
                        )
                    }.onFailure {
                        Log.e(
                            "HomiraPush",
                            "Cancelled-call push failed for ${session.id}",
                            it
                        )
                    }
                }

                incomingCallNotifier?.cancel(session.id)
            }
        }

        fun beginCall(person: HomiraPerson, video: Boolean) {
            if (person.id in blockedUserIds) {
                Toast.makeText(
                    context,
                    "Unblock ${person.name} before calling.",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

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
                val previousSession = activeSession
                    ?.takeIf { it.id != session.id }

                liveScope.launch {
                    runCatching {
                        if (previousSession != null) {
                            runCatching {
                                voiceEngine?.close()
                            }
                            voiceEngine = null

                            runCatching {
                                telecomBridge?.disconnect()
                            }

                            val localUserId =
                                liveRepository.currentUserId()
                            val previousState = when (
                                previousSession.state
                            ) {
                                "ringing", "connecting" -> {
                                    if (
                                        previousSession.callerId ==
                                        localUserId
                                    ) {
                                        "cancelled"
                                    } else {
                                        "declined"
                                    }
                                }

                                else -> "ended"
                            }

                            runCatching {
                                liveRepository.setCallState(
                                    previousSession.id,
                                    previousState
                                )
                            }

                            incomingCallNotifier?.cancel(
                                previousSession.id
                            )

                            callHistoryStore.markTerminal(
                                previousSession.id,
                                if (
                                    previousSession.state == "active"
                                ) {
                                    HomiraCallHistoryStore
                                        .OUTCOME_ANSWERED
                                } else if (
                                    previousState == "cancelled"
                                ) {
                                    HomiraCallHistoryStore
                                        .OUTCOME_CANCELLED
                                } else {
                                    HomiraCallHistoryStore
                                        .OUTCOME_DECLINED
                                }
                            )
                        }

                        liveRepository.setCallState(
                            session.id,
                            "active"
                        )
                    }.onSuccess { updated ->
                        incomingCallNotifier?.cancel(updated.id)
                        callHistoryStore.markAnswered(updated.id)
                        localCallHistory =
                            callHistoryStore.listRecent()

                        activeSession = updated
                        activePerson = person
                        activeVideo =
                            updated.mediaType == "video"
                        minimized = false
                        incomingSession = null
                        incomingPerson = null
                    }.onFailure {
                        Toast.makeText(
                            context,
                            it.message
                                ?: "Could not answer the call.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        val telecomSession = activeSession ?: incomingSession
        val telecomPerson = activePerson ?: incomingPerson

        LaunchedEffect(
            liveBackendReady,
            telecomSession?.id,
            telecomPerson?.id,
            micPermissionGranted,
            cameraPermissionGranted
        ) {
            val session = telecomSession
            val person = telecomPerson
            if (
                !liveBackendReady ||
                session == null ||
                person == null
            ) {
                return@LaunchedEffect
            }

            val localUserId =
                liveRepository.currentUserId() ?: return@LaunchedEffect

            val isIncoming = session.calleeId == localUserId
            val missingRequiredPermission =
                !micPermissionGranted ||
                    (
                        session.mediaType == "video" &&
                            !cameraPermissionGranted
                    )

            if (isIncoming && missingRequiredPermission) {
                return@LaunchedEffect
            }

            val bridge = telecomBridge ?: runCatching {
                HomiraTelecomBridge(context)
            }.getOrNull()?.also {
                telecomBridge = it
            } ?: return@LaunchedEffect

            runCatching {
                bridge.registerCall(
                    callId = session.id,
                    peerName = person.name,
                    peerAddress = person.number.ifBlank {
                        person.id
                    },
                    incoming = isIncoming,
                    video = session.mediaType == "video"
                )
            }
        }

        LaunchedEffect(liveBackendReady, telecomBridge) {
            if (!liveBackendReady) return@LaunchedEffect

            val bridge = telecomBridge ?: return@LaunchedEffect
            bridge.platformEvents.collect { event ->
                val session = activeSession ?: incomingSession

                when (event) {
                    is HomiraTelecomPlatformEvent.AnswerRequested -> {
                        val pending = incomingSession
                        if (
                            pending != null &&
                            pending.state == "ringing"
                        ) {
                            val needsMic = !micPermissionGranted
                            val needsCamera =
                                pending.mediaType == "video" &&
                                    !cameraPermissionGranted

                            if (!needsMic && !needsCamera) {
                                acceptIncomingNow()
                            } else {
                                pendingIncomingAccept = true
                            }
                        }
                    }

                    is HomiraTelecomPlatformEvent.DisconnectRequested -> {
                        finishLiveCall(session)
                    }

                    HomiraTelecomPlatformEvent.ActivateRequested -> {
                        // Telecom is asking Homira to resume/activate an
                        // already-established system call. The server
                        // session remains the source of truth.
                    }

                    HomiraTelecomPlatformEvent.InactivateRequested -> {
                        // Hold is not exposed by Homira yet, so there is
                        // no server-side hold state to publish.
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

        LaunchedEffect(liveBackendReady, appContacts) {
            if (!liveBackendReady) return@LaunchedEffect

            runCatching {
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
                    number = profile?.phoneE164.orEmpty(),
                    avatarUri = cacheProfileMediaP(
                        context,
                        liveRepository,
                        profile?.avatarPath,
                        "$userId-avatar"
                    ),
                    callCardUri = null
                )
            }

            val pending = requestedCallId
                ?.let { liveRepository.loadIncomingCallById(it) }
                ?: liveRepository.loadPendingIncomingCall()

            pending?.let {
                val person = resolvePerson(it.callerId)
                callHistoryStore.recordRinging(
                    id = it.id,
                    peerUserId = person.id,
                    peerName = person.name,
                    peerNumber = person.number,
                    direction = HomiraCallHistoryStore.DIRECTION_INCOMING,
                    mediaType = it.mediaType
                )
                localCallHistory = callHistoryStore.listRecent()
                incomingSession = it
                incomingPerson = person
                incomingCallNotifier?.show(
                    session = it,
                    callerName = person.name,
                    notificationsEnabled = localSettings.callNotifications,
                    ringtoneUri = localSettings.ringtoneUri
                )

                if (requestedAnswerCall && requestedCallId == it.id) {
                    pendingIncomingAccept = true

                    val needsMicrophone = !micPermissionGranted
                    val needsCamera =
                        it.mediaType == "video" && !cameraPermissionGranted

                    if (needsMicrophone || needsCamera) {
                        runCatching {
                            callPermissionLauncher.launch(
                                buildList {
                                    if (needsMicrophone) {
                                        add(Manifest.permission.RECORD_AUDIO)
                                    }
                                    if (needsCamera) {
                                        add(Manifest.permission.CAMERA)
                                    }
                                }.toTypedArray()
                            )
                        }
                    }
                }
            }

            liveRepository.observeIncomingCallChanges().collect { session ->
                when (session.state) {
                    "ringing" -> {
                        val person = resolvePerson(session.callerId)
                        callHistoryStore.recordRinging(
                            id = session.id,
                            peerUserId = person.id,
                            peerName = person.name,
                            peerNumber = person.number,
                            direction = HomiraCallHistoryStore.DIRECTION_INCOMING,
                            mediaType = session.mediaType
                        )
                        localCallHistory = callHistoryStore.listRecent()
                        incomingSession = session
                        incomingPerson = person
                        incomingCallNotifier?.show(
                            session = session,
                            callerName = person.name,
                            notificationsEnabled = localSettings.callNotifications,
                            ringtoneUri = localSettings.ringtoneUri
                        )
                    }

                    "missed", "declined", "cancelled", "failed", "ended" -> {
                        incomingCallNotifier?.cancel(session.id)
                        runCatching { telecomBridge?.disconnect() }
                        val outcome = when (session.state) {
                            "missed" -> HomiraCallHistoryStore.OUTCOME_MISSED
                            "declined" -> HomiraCallHistoryStore.OUTCOME_DECLINED
                            "cancelled" -> HomiraCallHistoryStore.OUTCOME_MISSED
                            "failed" -> HomiraCallHistoryStore.OUTCOME_FAILED
                            else -> HomiraCallHistoryStore.OUTCOME_MISSED
                        }
                        callHistoryStore.markTerminal(session.id, outcome)
                        localCallHistory = callHistoryStore.listRecent()

                        if (incomingSession?.id == session.id) {
                            incomingSession = null
                            incomingPerson = null
                        }
                    }
                }
            }
            }.onFailure {
                Log.e("HomiraRealtime", "Incoming-call sync stopped", it)
            }
        }

        LaunchedEffect(
            liveBackendReady,
            incomingSession?.id,
            incomingSession?.state
        ) {
            if (!liveBackendReady) return@LaunchedEffect

            val session = incomingSession ?: return@LaunchedEffect
            if (session.state != "ringing") return@LaunchedEffect

            delay(remainingRingWindowMs(session))

            val current = incomingSession
            if (
                current?.id == session.id &&
                current.state == "ringing"
            ) {
                runCatching {
                    liveRepository.setCallState(session.id, "missed")
                }.onSuccess { missed ->
                    incomingCallNotifier?.cancel(missed.id)
                    runCatching { telecomBridge?.disconnect() }
                    callHistoryStore.markTerminal(
                        missed.id,
                        HomiraCallHistoryStore.OUTCOME_MISSED
                    )
                    localCallHistory = callHistoryStore.listRecent()
                    incomingSession = null
                    incomingPerson = null
                }
            }
        }

        LaunchedEffect(liveBackendReady, activeSession?.id) {
            if (!liveBackendReady) return@LaunchedEffect
            val callId = activeSession?.id ?: return@LaunchedEffect
            val localUserId = liveRepository.currentUserId()

            runCatching {
                liveRepository.observeCallSession(callId).collect { session ->
                activeSession = session

                if (session.state == "active") {
                    val peer = activePerson
                    if (peer != null) {
                        incomingCallNotifier?.showOngoing(
                            callId = session.id,
                            mediaType = session.mediaType,
                            peerName = peer.name,
                            calling = false
                        )
                    } else {
                        incomingCallNotifier?.cancel(session.id)
                    }

                    callHistoryStore.markAnswered(session.id)
                    localCallHistory = callHistoryStore.listRecent()
                }

                if (
                    session.state in setOf(
                        "missed",
                        "declined",
                        "cancelled",
                        "failed",
                        "ended"
                    )
                ) {
                    val outcome = when (session.state) {
                        "missed" -> HomiraCallHistoryStore.OUTCOME_MISSED
                        "declined" -> HomiraCallHistoryStore.OUTCOME_DECLINED
                        "cancelled" -> {
                            if (session.callerId == localUserId) {
                                HomiraCallHistoryStore.OUTCOME_CANCELLED
                            } else {
                                HomiraCallHistoryStore.OUTCOME_MISSED
                            }
                        }
                        "failed" -> HomiraCallHistoryStore.OUTCOME_FAILED
                        "ended" -> HomiraCallHistoryStore.OUTCOME_ANSWERED
                        else -> HomiraCallHistoryStore.OUTCOME_FAILED
                    }
                    incomingCallNotifier?.cancel(session.id)
                    runCatching { telecomBridge?.disconnect() }
                    callHistoryStore.markTerminal(session.id, outcome)
                    localCallHistory = callHistoryStore.listRecent()

                    if (
                        session.state == "missed" &&
                        session.callerId == localUserId
                    ) {
                        val person = activePerson
                        if (person != null) {
                            val profile = liveRepository.loadProfileById(person.id)
                            if (profile?.voicemailEnabled == true) {
                                voicemailOffer = VoicemailOffer(
                                    person = person,
                                    callSessionId = session.id,
                                    wasVideo = activeVideo,
                                    greetingPath = if (
                                        profile.voicemailGreetingMode == "voice"
                                    ) {
                                        profile.voicemailGreetingPath
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    }

                    activeSession = null
                    activePerson = null
                    minimized = false
                }
            }
            }.onFailure {
                Log.e("HomiraRealtime", "Active-call sync stopped", it)
            }
        }

        LaunchedEffect(
            liveBackendReady,
            activeSession?.id,
            activeSession?.state,
            telecomBridge
        ) {
            if (!liveBackendReady) return@LaunchedEffect

            val session = activeSession ?: return@LaunchedEffect
            if (session.state != "active") return@LaunchedEffect

            val localUserId =
                liveRepository.currentUserId() ?: return@LaunchedEffect
            val bridge = telecomBridge ?: return@LaunchedEffect

            val telecomReady = withTimeoutOrNull(5_000L) {
                bridge.ready
                    .filter { it }
                    .first()
            } != null

            if (!telecomReady) return@LaunchedEffect

            if (session.calleeId == localUserId) {
                runCatching {
                    bridge.answer(
                        session.mediaType == "video"
                    )
                }
            } else {
                runCatching { bridge.markActive() }
            }
        }

        LaunchedEffect(
            liveBackendReady,
            activeSession?.id,
            activeSession?.state
        ) {
            if (!liveBackendReady) return@LaunchedEffect

            val session = activeSession ?: return@LaunchedEffect
            val localUserId = liveRepository.currentUserId() ?: return@LaunchedEffect

            if (
                session.callerId == localUserId &&
                session.state == "ringing"
            ) {
                delay(remainingRingWindowMs(session))

                val current = activeSession
                if (
                    current?.id == session.id &&
                    current.state == "ringing"
                ) {
                    val missed = runCatching {
                        liveRepository.setCallState(
                            session.id,
                            "missed"
                        )
                    }.getOrNull()

                    if (missed != null) {
                        activeSession = missed

                        runCatching {
                            liveRepository.requestMissedCallPush(
                                missed.id
                            )
                        }.onFailure {
                            Log.e(
                                "HomiraPush",
                                "Missed-call push failed for ${missed.id}",
                                it
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(liveBackendReady, activeSession?.id, micPermissionGranted) {
            runCatching { voiceEngine?.close() }
            voiceEngine = null
            webRtcState = HomiraWebRtcState.New

            val session = activeSession
            val person = activePerson
            val localUserId = liveRepository.currentUserId()

            if (!liveBackendReady || !micPermissionGranted || session == null || person == null || localUserId == null) {
                return@LaunchedEffect
            }

            val turnConfiguration = runCatching {
                liveRepository.loadTurnConfiguration()
            }.getOrNull()
            val iceServers = HomiraWebRtcVoiceEngine.iceServersFrom(
                turnConfiguration
            )

            val engine = runCatching {
                HomiraWebRtcVoiceEngine(
                    context = context,
                    callId = session.id,
                    localUserId = localUserId,
                    caller = session.callerId == localUserId,
                    initialVideoEnabled = activeVideo && cameraPermissionGranted,
                    lowDataMode = localSettings.lowDataCalls,
                    signaling = HomiraCallSignaling(session.id),
                    iceServers = iceServers,
                    forceRelayOnly = BuildConfig.HOMIRA_FORCE_TURN_RELAY
                )
            }.getOrElse { error ->
                webRtcState = HomiraWebRtcState.Failed
                Toast.makeText(
                    context,
                    error.message ?: "Could not initialize the call engine.",
                    Toast.LENGTH_SHORT
                ).show()
                return@LaunchedEffect
            }
            voiceEngine = engine

            try {
                engine.start()
                engine.state.collect { state ->
                    webRtcState = state
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
                runCatching { engine.close() }
                if (voiceEngine === engine) {
                    voiceEngine = null
                }
            }
        }

        LaunchedEffect(
            activeSession?.id,
            webRtcState
        ) {
            val session = activeSession
                ?: return@LaunchedEffect

            if (
                webRtcState != HomiraWebRtcState.Disconnected &&
                webRtcState != HomiraWebRtcState.Failed
            ) {
                return@LaunchedEffect
            }

            delay(10_000)

            if (
                activeSession?.id == session.id &&
                (
                    webRtcState == HomiraWebRtcState.Disconnected ||
                        webRtcState == HomiraWebRtcState.Failed
                )
            ) {
                runCatching {
                    liveRepository.setCallState(
                        session.id,
                        "failed"
                    )
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
            incomingSession != null && incomingPerson != null -> IncomingCallScreen(
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
                        incomingCallNotifier?.cancel(session.id)
                        liveScope.launch {
                            runCatching {
                                liveRepository.setCallState(session.id, "declined")
                            }
                        }
                    }
                }
            )

            voicemailOffer != null -> LeaveVoicemailScreen(
                offer = requireNotNull(voicemailOffer),
                repository = liveRepository,
                onCallAgain = { offer ->
                    voicemailOffer = null
                    beginCall(offer.person, offer.wasVideo)
                },
                onDismiss = {
                    voicemailOffer = null
                    tab = MainTab.Recents
                },
                onSent = {
                    voicemailOffer = null
                    tab = MainTab.Recents
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
                        runCatching {
                            callPermissionLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA)
                            )
                        }
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
                        runCatching {
                            screenShareLauncher.launch(
                                mediaProjectionManager.createScreenCaptureIntent()
                            )
                        }.onFailure {
                            Toast.makeText(
                                context,
                                "Could not request screen sharing.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
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
                    finishLiveCall(session)
                }
            )

            overlay == OverlayScreen.Voicemail -> VoicemailSettingsScreen(
                repository = liveRepository,
                liveMode = liveMode,
                initialMode = voicemailGreetingMode,
                initialPath = voicemailGreetingPath,
                onGreetingChanged = { mode, path ->
                    voicemailGreetingMode = mode
                    voicemailGreetingPath = path
                },
                onBack = {
                    overlay = OverlayScreen.None
                    tab = MainTab.Me
                }
            )

            overlay == OverlayScreen.BlockedPeople -> BlockedPeopleScreen(
                contacts = appContacts,
                blockedUserIds = blockedUserIds,
                onBack = {
                    overlay = OverlayScreen.None
                    tab = MainTab.Me
                },
                onBlockChanged = { person, blocked ->
                    if (!liveMode) {
                        blockedUserIds = if (blocked) {
                            blockedUserIds + person.id
                        } else {
                            blockedUserIds - person.id
                        }
                    } else {
                        liveScope.launch {
                            runCatching {
                                if (blocked) {
                                    liveRepository.blockUser(person.id)
                                } else {
                                    liveRepository.unblockUser(person.id)
                                }
                            }.onSuccess {
                                blockedUserIds = if (blocked) {
                                    blockedUserIds + person.id
                                } else {
                                    blockedUserIds - person.id
                                }
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    it.message ?: "Could not update blocked people.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
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
                phone = profilePhone,
                about = profileAbout,
                email = profileEmail,
                avatarUri = avatarUri,
                callCardUri = callCardUri,
                saving = profileSaving,
                onBack = { if (!profileSaving) overlay = OverlayScreen.None },
                onSave = { name, username, phone, about, email, avatar, card ->
                    if (!liveMode) {
                        profileName = name
                        profileUsername = username
                        profilePhone = phone
                        profileAbout = about
                        profileEmail = email
                        avatarUri = avatar
                        callCardUri = card
                        overlay = OverlayScreen.None
                    } else if (!profileSaving) {
                        val oldAvatarUri = avatarUri
                        val oldCardUri = callCardUri
                        val oldAvatarPath = avatarStoragePath
                        val oldCardPath = callCardStoragePath
                        val avatarChanged = avatar != oldAvatarUri
                        val cardChanged = card != oldCardUri

                        profileSaving = true
                        liveScope.launch {
                            var uploadedAvatarPath: String? = null
                            var uploadedCardPath: String? = null

                            runCatching {
                                var nextAvatarPath = oldAvatarPath
                                var nextCardPath = oldCardPath

                                if (avatarChanged) {
                                    nextAvatarPath = if (avatar == null) {
                                        null
                                    } else {
                                        val bytes = withContext(Dispatchers.IO) {
                                            prepareProfileJpeg(
                                                context = context,
                                                uriString = avatar,
                                                maxDimension = 768,
                                                quality = 90
                                            )
                                        }
                                        liveRepository.uploadProfileMedia(
                                            jpegBytes = bytes,
                                            kind = "avatar"
                                        ).also {
                                            uploadedAvatarPath = it
                                        }
                                    }
                                }

                                if (cardChanged) {
                                    nextCardPath = if (card == null) {
                                        null
                                    } else {
                                        val bytes = withContext(Dispatchers.IO) {
                                            prepareProfileJpeg(
                                                context = context,
                                                uriString = card,
                                                maxDimension = 1600,
                                                quality = 88
                                            )
                                        }
                                        liveRepository.uploadProfileMedia(
                                            jpegBytes = bytes,
                                            kind = "call-card"
                                        ).also {
                                            uploadedCardPath = it
                                        }
                                    }
                                }

                                val normalizedPhone = normalizeDirectDialP(phone)
                                    ?: error("Use the full phone number with country code, like +234…")

                                liveRepository.updateMyProfile(
                                    displayName = name,
                                    username = username,
                                    phoneE164 = normalizedPhone,
                                    about = about,
                                    email = email,
                                    avatarPath = nextAvatarPath,
                                    callCardPath = nextCardPath
                                )
                            }.onSuccess { saved ->
                                if (
                                    avatarChanged &&
                                    !oldAvatarPath.isNullOrBlank() &&
                                    oldAvatarPath != saved.avatarPath
                                ) {
                                    runCatching {
                                        liveRepository.deleteProfileMedia(oldAvatarPath)
                                    }
                                }
                                if (
                                    cardChanged &&
                                    !oldCardPath.isNullOrBlank() &&
                                    oldCardPath != saved.callCardPath
                                ) {
                                    runCatching {
                                        liveRepository.deleteProfileMedia(oldCardPath)
                                    }
                                }

                                profileName = saved.displayName.ifBlank { "You" }
                                profileUsername = saved.username.orEmpty()
                                profilePhone = saved.phoneE164.orEmpty()
                                profileAbout = saved.about
                                profileEmail = saved.email.orEmpty()
                                avatarStoragePath = saved.avatarPath
                                callCardStoragePath = saved.callCardPath
                                avatarUri = avatar
                                callCardUri = card
                                overlay = OverlayScreen.None
                            }.onFailure { error ->
                                uploadedAvatarPath?.let {
                                    runCatching { liveRepository.deleteProfileMedia(it) }
                                }
                                uploadedCardPath?.let {
                                    runCatching { liveRepository.deleteProfileMedia(it) }
                                }
                                Toast.makeText(
                                    context,
                                    error.message ?: "Could not save your profile.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            profileSaving = false
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
                                finishLiveCall(session)
                            }
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        tabStateHolder.SaveableStateProvider(tab.name) {
                            when (tab) {
                            MainTab.Keypad -> KeypadScreen(
                                contacts = appContacts,
                                resolvingDial = resolvingDial,
                                lastDialedNumber = appCallEntries
                                    .firstOrNull {
                                        it.direction == CallDirection.Outgoing
                                    }
                                    ?.person
                                    ?.number
                                    ?.takeIf { it.isNotBlank() },
                                onSearchContacts = { tab = MainTab.Contacts },
                                onDial = dial@{ value ->
                                    if (resolvingDial) return@dial

                                    val digits = digitsOnlyP(value)
                                    val found = appContacts.firstOrNull {
                                        digitsOnlyP(it.number).endsWith(
                                            digits.takeLast(10)
                                        ) && digits.length >= 7
                                    }

                                    if (found != null) {
                                        beginCall(found, false)
                                        return@dial
                                    }

                                    if (!liveMode) {
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
                                        return@dial
                                    }

                                    val phone = normalizeDirectDialP(value)
                                    if (phone == null) {
                                        Toast.makeText(
                                            context,
                                            "Use the full number with country code, like +234…",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@dial
                                    }

                                    resolvingDial = true
                                    liveScope.launch {
                                        runCatching {
                                            liveRepository.resolveDialTarget(phone)
                                        }.onSuccess { target ->
                                            if (target == null) {
                                                Toast.makeText(
                                                    context,
                                                    "That number isn't on Homira.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                val name = target.displayName
                                                    .takeIf { it.isNotBlank() }
                                                    ?: target.username
                                                    ?: target.phoneE164
                                                    ?: phone

                                                beginCall(
                                                    HomiraPerson(
                                                        id = target.id,
                                                        name = name,
                                                        marker = name
                                                            .firstOrNull()
                                                            ?.uppercaseChar()
                                                            ?.toString()
                                                            ?: "H",
                                                        accent = HomiraGreen,
                                                        number = target.phoneE164
                                                            ?: phone
                                                    ),
                                                    false
                                                )
                                            }
                                        }.onFailure {
                                            Toast.makeText(
                                                context,
                                                it.message
                                                    ?: "Could not look up that number.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        resolvingDial = false
                                    }
                                }
                            )

                            MainTab.Recents -> RecentsScreen(
                                entries = appCallEntries,
                                blockedUserIds = blockedUserIds,
                                playingVoicemailId = playingVoicemailId,
                                onVoicemail = { toggleVoicemailPlayback(it) },
                                onDeleteAll = {
                                    liveScope.launch {
                                        runCatching {
                                            callHistoryStore.deleteAll()
                                        }.onSuccess {
                                            localCallHistory = emptyList()
                                        }.onFailure {
                                            Toast.makeText(
                                                context,
                                                it.message
                                                    ?: "Could not clear call history.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                onVoiceCall = { beginCall(it, false) },
                                onVideoCall = { beginCall(it, true) }
                            )

                            MainTab.Contacts -> ContactsScreen(
                                contacts = appContacts,
                                onAddContact = {
                                    overlay = OverlayScreen.AddContact
                                },
                                onVoiceCall = { beginCall(it, false) },
                                onVideoCall = { beginCall(it, true) },
                                onFavoriteChanged = { person, favorite ->
                                    if (!liveMode) {
                                        liveContacts = liveContacts.map {
                                            if (it.id == person.id) {
                                                it.copy(favorite = favorite)
                                            } else {
                                                it
                                            }
                                        }
                                    } else {
                                        liveScope.launch {
                                            runCatching {
                                                liveRepository.setContactFavorite(
                                                    person.id,
                                                    favorite
                                                )
                                            }.onSuccess {
                                                liveContacts = liveContacts.map {
                                                    if (it.id == person.id) {
                                                        it.copy(
                                                            favorite = favorite
                                                        )
                                                    } else {
                                                        it
                                                    }
                                                }
                                            }.onFailure {
                                                Toast.makeText(
                                                    context,
                                                    it.message
                                                        ?: "Could not update favorite.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                onRenameContact = { person, newName ->
                                    if (!liveMode) {
                                        liveContacts = liveContacts.map {
                                            if (it.id == person.id) {
                                                it.copy(localName = newName)
                                            } else {
                                                it
                                            }
                                        }
                                    } else {
                                        liveScope.launch {
                                            runCatching {
                                                liveRepository.setContactLocalName(
                                                    person.id,
                                                    newName
                                                )
                                            }.onSuccess {
                                                liveContacts = liveContacts.map {
                                                    if (it.id == person.id) {
                                                        it.copy(
                                                            localName = newName
                                                        )
                                                    } else {
                                                        it
                                                    }
                                                }
                                            }.onFailure {
                                                Toast.makeText(
                                                    context,
                                                    it.message
                                                        ?: "Could not rename contact.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                onDeleteContact = { person ->
                                    if (!liveMode) {
                                        liveContacts =
                                            liveContacts.filterNot {
                                                it.id == person.id
                                            }
                                    } else {
                                        liveScope.launch {
                                            runCatching {
                                                liveRepository.deleteContact(
                                                    person.id
                                                )
                                            }.onSuccess {
                                                liveContacts =
                                                    liveContacts.filterNot {
                                                        it.id == person.id
                                                    }
                                            }.onFailure {
                                                Toast.makeText(
                                                    context,
                                                    it.message
                                                        ?: "Could not delete contact.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            )

                            MainTab.Me -> MeScreen(
                                name = profileName,
                                username = profileUsername,
                                about = profileAbout,
                                email = profileEmail,
                                phone = profilePhone,
                                avatarUri = avatarUri,
                                lowDataCalls = localSettings.lowDataCalls,
                                callNotifications =
                                    localSettings.callNotifications,
                                ringtoneTitle = ringtoneTitle,
                                voicemailEnabled = voicemailEnabled,
                                voicemailGreetingMode =
                                    voicemailGreetingMode,
                                onEdit = {
                                    overlay = OverlayScreen.EditProfile
                                },
                                onLowDataChanged = { enabled ->
                                    settingsStore.setLowDataCalls(enabled)
                                    localSettings = settingsStore.load()
                                },
                                onNotificationsChanged = { enabled ->
                                    settingsStore.setCallNotifications(
                                        enabled
                                    )
                                    localSettings = settingsStore.load()

                                    if (
                                        Build.VERSION.SDK_INT >=
                                            Build.VERSION_CODES.TIRAMISU &&
                                        enabled &&
                                        !notificationPermissionGranted
                                    ) {
                                        runCatching {
                                            notificationPermissionLauncher
                                                .launch(
                                                    Manifest.permission
                                                        .POST_NOTIFICATIONS
                                                )
                                        }
                                    }
                                },
                                onRingtone = {
                                    val existingUri =
                                        localSettings.ringtoneUri
                                            ?.let(Uri::parse)
                                            ?: RingtoneManager
                                                .getDefaultUri(
                                                    RingtoneManager
                                                        .TYPE_RINGTONE
                                                )

                                    val intent = Intent(
                                        RingtoneManager
                                            .ACTION_RINGTONE_PICKER
                                    ).apply {
                                        putExtra(
                                            RingtoneManager
                                                .EXTRA_RINGTONE_TYPE,
                                            RingtoneManager.TYPE_RINGTONE
                                        )
                                        putExtra(
                                            RingtoneManager
                                                .EXTRA_RINGTONE_TITLE,
                                            "Homira ringtone"
                                        )
                                        putExtra(
                                            RingtoneManager
                                                .EXTRA_RINGTONE_SHOW_DEFAULT,
                                            true
                                        )
                                        putExtra(
                                            RingtoneManager
                                                .EXTRA_RINGTONE_SHOW_SILENT,
                                            false
                                        )
                                        putExtra(
                                            RingtoneManager
                                                .EXTRA_RINGTONE_EXISTING_URI,
                                            existingUri
                                        )
                                    }

                                    runCatching {
                                        ringtonePickerLauncher.launch(
                                            intent
                                        )
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            "Could not open the ringtone picker.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onVoicemailEnabledChanged = { enabled ->
                                    if (!liveMode) {
                                        voicemailEnabled = enabled
                                    } else {
                                        val previous = voicemailEnabled
                                        voicemailEnabled = enabled

                                        liveScope.launch {
                                            runCatching {
                                                liveRepository
                                                    .setVoicemailEnabled(
                                                        enabled
                                                    )
                                            }.onSuccess { profile ->
                                                voicemailEnabled =
                                                    profile
                                                        .voicemailEnabled
                                            }.onFailure {
                                                voicemailEnabled = previous
                                                Toast.makeText(
                                                    context,
                                                    it.message
                                                        ?: "Could not update voicemail.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                onVoicemail = {
                                    overlay = OverlayScreen.Voicemail
                                },
                                onBlockedPeople = {
                                    overlay = OverlayScreen.BlockedPeople
                                },
                                onSignOut = {
                                    liveScope.launch {
                                        runCatching {
                                            voiceEngine?.close()
                                            runCatching {
                                                HomiraPushBootstrap
                                                    .removeRegisteredToken(
                                                        context = context,
                                                        repository =
                                                            liveRepository
                                                    )
                                            }
                                            liveRepository.signOut()
                                        }.onSuccess {
                                            activePerson = null
                                            activeSession = null
                                            incomingSession = null
                                            incomingPerson = null
                                            onSignedOut()
                                        }.onFailure {
                                            Toast.makeText(
                                                context,
                                                it.message
                                                    ?: "Could not sign out.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderActions(
    title: String,
    onSettings: () -> Unit,
    onSearch: (() -> Unit)? = null,
    onBlockedPeople: (() -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = HomiraText,
            fontSize = 31.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )

        if (onSearch != null) {
            IconButton(onClick = onSearch) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = HomiraText
                )
            }
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "More",
                    tint = HomiraText
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onSettings()
                    }
                )

                if (onBlockedPeople != null) {
                    DropdownMenuItem(
                        text = { Text("Blocked people") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Block,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onBlockedPeople()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadScreen(
    contacts: List<HomiraPerson>,
    resolvingDial: Boolean,
    lastDialedNumber: String?,
    onSearchContacts: () -> Unit,
    onDial: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    var number by rememberSaveable { mutableStateOf("") }
    var clipboardNumber by remember { mutableStateOf<String?>(null) }

    fun readClipboardNumber(): String? {
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        val raw = clip.getItemAt(0)
            .coerceToText(context)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (raw.isBlank()) return null

        val compact = raw
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
        val hasLeadingPlus = compact.startsWith("+")
        val digits = compact.filter(Char::isDigit)
        if (digits.length !in 7..15) return null
        if (compact.any { !it.isDigit() && it != '+' }) return null

        return if (hasLeadingPlus) "+$digits" else digits
    }

    DisposableEffect(clipboard) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            clipboardNumber = readClipboardNumber()
        }
        clipboardNumber = readClipboardNumber()
        clipboard.addPrimaryClipChangedListener(listener)
        onDispose {
            clipboard.removePrimaryClipChangedListener(listener)
        }
    }

    val match = contacts.firstOrNull {
        val digits = digitsOnlyP(number)
        digits.length >= 7 && digitsOnlyP(it.number).endsWith(digits.takeLast(10))
    }
    val canUseCallButton =
        !resolvingDial &&
            (number.isNotBlank() || !lastDialedNumber.isNullOrBlank())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onSearchContacts) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = "Search contacts",
                    tint = HomiraText
                )
            }
        }

        Spacer(Modifier.weight(.55f))

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (number.isNotBlank()) {
                Text(
                    formatDialNumberP(number),
                    color = HomiraText,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1
                )
                Spacer(Modifier.height(7.dp))
                if (match != null) {
                    Text(
                        "${match.name} · Homira",
                        color = match.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (digitsOnlyP(number).length >= 7) {
                    Text(
                        when {
                            resolvingDial -> "Checking Homira…"
                            number.trim().startsWith("+") -> "Call with Homira"
                            else -> "Use +country code for an unsaved number"
                        },
                        color = if (resolvingDial) HomiraGreen else HomiraMuted,
                        fontSize = 13.sp
                    )
                }
            } else {
                Spacer(Modifier.height(51.dp))
                val pasteNumber = clipboardNumber
                if (pasteNumber != null) {
                    Surface(
                        modifier = Modifier.clickable {
                            number = pasteNumber
                        },
                        shape = RoundedCornerShape(24.dp),
                        color = HomiraSurfaceRaised
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 18.dp,
                                vertical = 11.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                tint = HomiraMuted,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "Paste number from clipboard",
                                color = HomiraMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        PlainDialPad(
            onDigit = {
                if (number.length < 20) number += it
            },
            onLongZero = {
                if (number.isEmpty()) number = "+"
            }
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(62.dp))
            Spacer(Modifier.width(38.dp))
            Surface(
                modifier = Modifier
                    .size(76.dp)
                    .clickable(enabled = canUseCallButton) {
                        if (number.isBlank()) {
                            number = lastDialedNumber.orEmpty()
                        } else {
                            onDial(number)
                        }
                    },
                shape = CircleShape,
                color = if (canUseCallButton) {
                    HomiraGreen
                } else {
                    HomiraSurfaceRaised
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (resolvingDial) {
                        CircularProgressIndicator(
                            color = HomiraGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(27.dp)
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Call,
                            contentDescription = if (number.isBlank()) {
                                "Recall last number"
                            } else {
                                "Call"
                            },
                            tint = if (canUseCallButton) {
                                Color.Black
                            } else {
                                HomiraMuted
                            },
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(38.dp))
            IconButton(
                onClick = {
                    if (number.isNotEmpty()) number = number.dropLast(1)
                },
                enabled = number.isNotEmpty(),
                modifier = Modifier.size(62.dp)
            ) {
                Icon(
                    Icons.Rounded.Backspace,
                    contentDescription = "Delete",
                    tint = if (number.isBlank()) {
                        Color.Transparent
                    } else {
                        HomiraText
                    },
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.weight(.18f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlainDialPad(
    onDigit: (String) -> Unit,
    onLongZero: () -> Unit
) {
    val rows = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(width = 88.dp, height = 88.dp)
                            .combinedClickable(
                                onClick = { onDigit(key.first) },
                                onLongClick = {
                                    if (key.first == "0") onLongZero()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                key.first,
                                color = HomiraText,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Normal
                            )
                            if (key.second.isNotBlank()) {
                                Text(
                                    key.second,
                                    color = HomiraMuted,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp
                                )
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
    entries: List<CallEntry>,
    blockedUserIds: Set<String>,
    playingVoicemailId: String?,
    onVoicemail: (CallEntry) -> Unit,
    onDeleteAll: () -> Unit,
    onVoiceCall: (HomiraPerson) -> Unit,
    onVideoCall: (HomiraPerson) -> Unit
) {
    var filter by rememberSaveable {
        mutableStateOf(RecentFilter.All)
    }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var hideBlocked by rememberSaveable { mutableStateOf(false) }

    val filteredByType = when (filter) {
        RecentFilter.All -> entries
        RecentFilter.Missed -> entries.filter {
            it.direction == CallDirection.Missed
        }
        RecentFilter.Rejected -> entries.filter {
            it.direction == CallDirection.Declined
        }
        RecentFilter.Outgoing -> entries.filter {
            it.direction == CallDirection.Outgoing
        }
        RecentFilter.Incoming -> entries.filter {
            it.direction == CallDirection.Incoming
        }
        RecentFilter.Voicemail -> entries.filter {
            it.voicemailSeconds != null
        }
    }

    val normalizedQuery = query.trim()
    val visibleRaw = filteredByType.filter { entry ->
        val visibleByBlocked =
            !hideBlocked || entry.person.id !in blockedUserIds
        val visibleByQuery =
            normalizedQuery.isBlank() ||
                entry.person.name.contains(
                    normalizedQuery,
                    ignoreCase = true
                ) ||
                entry.person.number.contains(normalizedQuery) ||
                directionLabel(entry.direction).contains(
                    normalizedQuery,
                    ignoreCase = true
                )

        visibleByBlocked && visibleByQuery
    }

    val filtered = buildList<CallEntry> {
        visibleRaw.forEach { entry ->
            val previous = this.lastOrNull()
            val canGroup =
                previous != null &&
                    previous.person.id == entry.person.id &&
                    previous.day == entry.day &&
                    previous.direction == entry.direction &&
                    previous.video == entry.video &&
                    previous.voicemailId == null &&
                    entry.voicemailId == null

            if (canGroup && previous != null) {
                this[this.lastIndex] = previous.copy(
                    count = previous.count + entry.count
                )
            } else {
                add(entry)
            }
        }
    }

    val days = filtered.map { it.day }.distinct()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 14.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recents",
                    color = HomiraText,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    }
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "Search calls",
                        tint = HomiraText
                    )
                }
                IconButton(onClick = { filterOpen = true }) {
                    Icon(
                        Icons.Rounded.FilterList,
                        contentDescription = "Filter calls",
                        tint = if (filter == RecentFilter.All) {
                            HomiraText
                        } else {
                            HomiraBlue
                        }
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "More",
                            tint = HomiraText
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (hideBlocked) {
                                        "Show blocked calls"
                                    } else {
                                        "Hide blocked calls"
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Block,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                hideBlocked = !hideBlocked
                                menuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete call history") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.CallEnd,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuOpen = false
                                confirmDeleteAll = true
                            }
                        )
                    }
                }
            }
        }

        if (searching) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null
                        )
                    },
                    placeholder = { Text("Search recent calls") },
                    shape = RoundedCornerShape(28.dp)
                )
            }
        }

        if (filter != RecentFilter.All) {
            item {
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = HomiraSurfaceRaised
                ) {
                    Text(
                        when (filter) {
                            RecentFilter.All -> "All calls"
                            RecentFilter.Missed -> "Missed calls"
                            RecentFilter.Rejected -> "Rejected calls"
                            RecentFilter.Outgoing -> "Outgoing calls"
                            RecentFilter.Incoming -> "Incoming calls"
                            RecentFilter.Voicemail -> "Direct voicemail"
                        },
                        color = HomiraBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(
                            horizontal = 13.dp,
                            vertical = 8.dp
                        )
                    )
                }
            }
        }

        if (filtered.isNotEmpty()) {
            item {
                Text(
                    "Swipe right for voice · left for video",
                    color = HomiraMuted.copy(alpha = .72f),
                    fontSize = 11.sp
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = HomiraSurface
                    )
                ) {
                    Text(
                        if (
                            query.isNotBlank() ||
                            filter != RecentFilter.All ||
                            hideBlocked
                        ) {
                            "No calls match this view."
                        } else {
                            "No recent calls yet."
                        },
                        color = HomiraMuted,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        days.forEach { day ->
            val dayEntries = filtered.filter { it.day == day }
            item(key = "day-$day") {
                Column {
                    Text(
                        day,
                        color = HomiraMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(
                            start = 4.dp,
                            bottom = 7.dp
                        )
                    )
                    Card(
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = HomiraSurface
                        )
                    ) {
                        Column {
                            dayEntries.forEachIndexed { index, entry ->
                                SwipeCallRow(
                                    person = entry.person,
                                    onVoice = {
                                        onVoiceCall(entry.person)
                                    },
                                    onVideo = {
                                        onVideoCall(entry.person)
                                    }
                                ) {
                                    RecentEntryRow(
                                        entry = entry,
                                        voicemailPlaying =
                                            playingVoicemailId ==
                                                entry.voicemailPlaybackKey(),
                                        onVoicemail = {
                                            onVoicemail(entry)
                                        },
                                        onCall = {
                                            onVoiceCall(entry.person)
                                        }
                                    )
                                }

                                if (index != dayEntries.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(
                                            start = 66.dp,
                                            end = 18.dp
                                        ),
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

    if (filterOpen) {
        AlertDialog(
            onDismissRequest = { filterOpen = false },
            title = {
                Text(
                    "Filter calls",
                    color = HomiraText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        RecentFilter.All to "All calls",
                        RecentFilter.Missed to "Missed calls",
                        RecentFilter.Rejected to "Rejected calls",
                        RecentFilter.Outgoing to "Outgoing calls",
                        RecentFilter.Incoming to "Incoming calls",
                        RecentFilter.Voicemail to "Direct voicemail"
                    ).forEach { (option, label) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    filter = option
                                    filterOpen = false
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = if (filter == option) {
                                HomiraSurfaceRaised
                            } else {
                                Color.Transparent
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 14.dp,
                                    vertical = 12.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(20.dp),
                                    shape = CircleShape,
                                    color = if (filter == option) {
                                        HomiraBlue
                                    } else {
                                        HomiraLine
                                    }
                                ) {
                                    if (filter == option) {
                                        Box(
                                            contentAlignment =
                                                Alignment.Center
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(8.dp),
                                                shape = CircleShape,
                                                color = HomiraBackground
                                            ) {}
                                        }
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    label,
                                    color = HomiraText,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { filterOpen = false }) {
                    Text("Done", color = HomiraGreen)
                }
            },
            containerColor = HomiraSurface
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = {
                Text(
                    "Delete call history?",
                    color = HomiraText
                )
            },
            text = {
                Text(
                    "This clears Homira's local recent-call history on this device.",
                    color = HomiraMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteAll = false
                        onDeleteAll()
                    }
                ) {
                    Text("Delete", color = HomiraDanger)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmDeleteAll = false
                    }
                ) {
                    Text("Cancel", color = HomiraMuted)
                }
            },
            containerColor = HomiraSurface
        )
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
    onAddContact: () -> Unit,
    onVoiceCall: (HomiraPerson) -> Unit,
    onVideoCall: (HomiraPerson) -> Unit,
    onFavoriteChanged: (HomiraPerson, Boolean) -> Unit,
    onRenameContact: (HomiraPerson, String) -> Unit,
    onDeleteContact: (HomiraPerson) -> Unit
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var expandedPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var infoPerson by remember { mutableStateOf<HomiraPerson?>(null) }
    var deletePerson by remember { mutableStateOf<HomiraPerson?>(null) }
    var qrPerson by remember { mutableStateOf<HomiraPerson?>(null) }
    var contactPhotoPerson by remember { mutableStateOf<HomiraPerson?>(null) }
    var editPerson by remember { mutableStateOf<HomiraPerson?>(null) }
    var editedContactName by rememberSaveable { mutableStateOf("") }

    val filtered = contacts.filter {
        it.name.contains(query, ignoreCase = true) ||
            it.number.contains(query)
    }
    val initials = filtered
        .mapNotNull { it.name.firstOrNull()?.uppercaseChar() }
        .distinct()
        .sorted()
    val favorites = contacts.filter { it.favorite }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 14.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Contacts",
                    color = HomiraText,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onAddContact) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Add contact",
                        tint = HomiraText
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    expandedPersonId = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null
                    )
                },
                placeholder = { Text("Search contacts") },
                shape = RoundedCornerShape(28.dp)
            )
        }

        if (query.isBlank() && favorites.isNotEmpty()) {
            item {
                Text(
                    "Favorites",
                    color = HomiraMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(9.dp))
                Row(
                    modifier = Modifier.horizontalScroll(
                        rememberScrollState()
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    favorites.forEach { person ->
                        FavoriteContact(
                            person = person,
                            onCall = { onVoiceCall(person) }
                        )
                    }
                }
            }
        }

        if (contacts.isEmpty() && query.isBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = HomiraSurface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.PersonAdd,
                            contentDescription = null,
                            tint = HomiraMuted
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No contacts yet",
                            color = HomiraText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
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

        if (contacts.isNotEmpty()) {
            item {
                Text(
                    "Swipe right for voice · left for video",
                    color = HomiraMuted.copy(alpha = .72f),
                    fontSize = 11.sp
                )
            }
        }

        initials.forEach { initial ->
            val section = filtered.filter {
                it.name.firstOrNull()?.uppercaseChar() == initial
            }

            item(key = "contact-$initial") {
                Column {
                    Text(
                        initial.toString(),
                        color = HomiraMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(
                            start = 4.dp,
                            bottom = 7.dp
                        )
                    )
                    Card(
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = HomiraSurface
                        )
                    ) {
                        Column {
                            section.forEachIndexed { index, person ->
                                SwipeCallRow(
                                    person = person,
                                    onVoice = {
                                        onVoiceCall(person)
                                        expandedPersonId = null
                                    },
                                    onVideo = {
                                        onVideoCall(person)
                                        expandedPersonId = null
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.background(
                                            HomiraSurface
                                        )
                                    ) {
                                        ContactRow(
                                            person = person,
                                            expanded =
                                                expandedPersonId == person.id,
                                            onClick = {
                                                expandedPersonId =
                                                    if (
                                                        expandedPersonId ==
                                                        person.id
                                                    ) {
                                                        null
                                                    } else {
                                                        person.id
                                                    }
                                            }
                                        )

                                        AnimatedVisibility(
                                            expandedPersonId == person.id
                                        ) {
                                            ContactActionStripP(
                                                person = person,
                                                onVoice = {
                                                    expandedPersonId = null
                                                    onVoiceCall(person)
                                                },
                                                onInfo = {
                                                    infoPerson = person
                                                },
                                                onVideo = {
                                                    expandedPersonId = null
                                                    onVideoCall(person)
                                                }
                                            )
                                        }
                                    }
                                }

                                if (index != section.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(
                                            start = 68.dp,
                                            end = 16.dp
                                        ),
                                        color = HomiraLine.copy(alpha = .65f)
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

    infoPerson?.let { person ->
        AlertDialog(
            onDismissRequest = { infoPerson = null },
            title = {
                Text(
                    person.name,
                    color = HomiraText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.then(
                            if (person.avatarUri != null) {
                                Modifier.clickable {
                                    contactPhotoPerson = person
                                }
                            } else {
                                Modifier
                            }
                        )
                    ) {
                        PersonAvatarP(person, 82)
                    }
                    if (person.avatarUri != null) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Tap photo to view",
                            color = HomiraMuted.copy(alpha = .72f),
                            fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        person.number.ifBlank { "Homira contact" },
                        color = HomiraMuted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ContactActionButtonP(
                            icon = Icons.Rounded.Favorite,
                            label = if (person.favorite) {
                                "Unfavorite"
                            } else {
                                "Favorite"
                            },
                            accent = if (person.favorite) {
                                HomiraPink
                            } else {
                                HomiraMuted
                            }
                        ) {
                            infoPerson = null
                            onFavoriteChanged(
                                person,
                                !person.favorite
                            )
                        }

                        ContactActionButtonP(
                            icon = Icons.Rounded.Call,
                            label = "Call",
                            accent = HomiraGreen
                        ) {
                            infoPerson = null
                            onVoiceCall(person)
                        }

                        ContactActionButtonP(
                            icon = Icons.Rounded.Videocam,
                            label = "Video",
                            accent = HomiraBlue
                        ) {
                            infoPerson = null
                            onVideoCall(person)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    TextButton(
                        onClick = {
                            val shareText = buildString {
                                append(person.name)
                                if (person.number.isNotBlank()) {
                                    append("\n")
                                    append(person.number)
                                }
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    shareText
                                )
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(
                                        intent,
                                        "Share contact"
                                    )
                                )
                            }
                        }
                    ) {
                        Text("Share contact", color = HomiraBlue)
                    }

                    TextButton(
                        onClick = {
                            infoPerson = null
                            editedContactName = person.name
                            editPerson = person
                        }
                    ) {
                        Text("Edit name", color = HomiraText)
                    }

                    TextButton(
                        onClick = {
                            infoPerson = null
                            qrPerson = person
                        }
                    ) {
                        Text("Show QR code", color = HomiraGreen)
                    }

                    TextButton(
                        onClick = {
                            infoPerson = null
                            deletePerson = person
                        }
                    ) {
                        Text("Delete contact", color = HomiraDanger)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { infoPerson = null }) {
                    Text("Done", color = HomiraGreen)
                }
            },
            containerColor = HomiraSurface
        )
    }

    contactPhotoPerson?.let { person ->
        val photo = rememberBitmapP(person.avatarUri)
        AlertDialog(
            onDismissRequest = {
                contactPhotoPerson = null
            },
            text = {
                if (photo != null) {
                    Image(
                        bitmap = photo,
                        contentDescription =
                            "${person.name} profile photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        contactPhotoPerson = null
                    }
                ) {
                    Text("Close", color = HomiraGreen)
                }
            },
            containerColor = HomiraBackground
        )
    }

    editPerson?.let { person ->
        AlertDialog(
            onDismissRequest = { editPerson = null },
            title = {
                Text(
                    "Edit contact",
                    color = HomiraText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = editedContactName,
                    onValueChange = {
                        editedContactName = it.take(60)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Name") },
                    shape = RoundedCornerShape(18.dp)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editedContactName.trim().isNotBlank(),
                    onClick = {
                        val newName = editedContactName.trim()
                        editPerson = null
                        onRenameContact(person, newName)
                    }
                ) {
                    Text("Save", color = HomiraGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { editPerson = null }) {
                    Text("Cancel", color = HomiraMuted)
                }
            },
            containerColor = HomiraSurface
        )
    }

    qrPerson?.let { person ->
        HomiraQrDialogP(
            title = person.name,
            subtitle = person.number.ifBlank {
                "Homira contact"
            },
            payload = homiraContactQrPayloadP(
                person.name,
                person.number,
                null
            ),
            onDismiss = { qrPerson = null }
        )
    }

    deletePerson?.let { person ->
        AlertDialog(
            onDismissRequest = { deletePerson = null },
            title = {
                Text(
                    "Delete ${person.name}?",
                    color = HomiraText
                )
            },
            text = {
                Text(
                    "They'll be removed from your Homira contacts. Your call history won't be deleted.",
                    color = HomiraMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletePerson = null
                        expandedPersonId = null
                        onDeleteContact(person)
                    }
                ) {
                    Text("Delete", color = HomiraDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletePerson = null }) {
                    Text("Cancel", color = HomiraMuted)
                }
            },
            containerColor = HomiraSurface
        )
    }
}

@Composable
private fun ContactActionStripP(
    person: HomiraPerson,
    onVoice: () -> Unit,
    onInfo: () -> Unit,
    onVideo: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 74.dp,
                end = 18.dp,
                bottom = 12.dp
            ),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactActionButtonP(
            icon = Icons.Rounded.Call,
            label = "Voice",
            accent = HomiraGreen,
            onClick = onVoice
        )
        ContactActionButtonP(
            icon = Icons.Rounded.Info,
            label = "Info",
            accent = HomiraMuted,
            onClick = onInfo
        )
        ContactActionButtonP(
            icon = Icons.Rounded.Videocam,
            label = "Video",
            accent = HomiraBlue,
            onClick = onVideo
        )
    }
}

@Composable
private fun ContactActionButtonP(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .size(44.dp)
                .clickable(onClick = onClick),
            shape = CircleShape,
            color = accent.copy(alpha = .13f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            color = HomiraMuted,
            fontSize = 10.sp
        )
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
private fun ContactRow(
    person: HomiraPerson,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HomiraSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonAvatarP(person, 46)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                person.name,
                color = HomiraText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            if (person.number.isNotBlank()) {
                Text(
                    person.number,
                    color = HomiraMuted,
                    fontSize = 12.sp
                )
            }
        }
        Icon(
            if (expanded) {
                Icons.Rounded.KeyboardArrowDown
            } else {
                Icons.Rounded.MoreVert
            },
            contentDescription = if (expanded) {
                "Hide contact actions"
            } else {
                "Show contact actions"
            },
            tint = HomiraMuted,
            modifier = Modifier.size(20.dp)
        )
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
    phone: String,
    avatarUri: String?,
    lowDataCalls: Boolean,
    callNotifications: Boolean,
    ringtoneTitle: String,
    voicemailEnabled: Boolean,
    voicemailGreetingMode: String,
    onEdit: () -> Unit,
    onLowDataChanged: (Boolean) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    onRingtone: () -> Unit,
    onVoicemailEnabledChanged: (Boolean) -> Unit,
    onVoicemail: () -> Unit,
    onBlockedPeople: () -> Unit,
    onSignOut: () -> Unit
) {
    val avatarBitmap = rememberBitmapP(avatarUri)
    var avatarOpen by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var ownQrOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 14.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Me",
                color = HomiraText,
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HomiraSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (avatarBitmap != null) {
                        Image(
                            avatarBitmap,
                            contentDescription = "Profile photo",
                            modifier = Modifier
                                .size(112.dp)
                                .clip(CircleShape)
                                .clickable {
                                    avatarOpen = true
                                },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .size(112.dp)
                                .clickable(onClick = onEdit),
                            shape = CircleShape,
                            color = HomiraGreen.copy(alpha = .13f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    name.take(1).uppercase(),
                                    color = HomiraGreen,
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        name,
                        color = HomiraText,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (username.isNotBlank()) {
                        Text(
                            "@$username",
                            color = HomiraMuted,
                            fontSize = 13.sp
                        )
                    }
                    if (about.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            about,
                            color = HomiraText.copy(alpha = .82f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onEdit,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HomiraText,
                            contentColor = HomiraBackground
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "Edit profile",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        item {
            SectionTitleP("Profile")
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HomiraSurface
                )
            ) {
                Column {
                    MeRowP(
                        Icons.Rounded.Edit,
                        "Edit profile",
                        "Name, about and profile photo",
                        onEdit
                    )
                    MeRowP(
                        Icons.Rounded.Phone,
                        "Phone number",
                        phone.ifBlank { "Not set" }
                    )
                    MeRowP(
                        Icons.Rounded.Person,
                        "Username",
                        if (username.isBlank()) {
                            "Not set"
                        } else {
                            "@$username"
                        }
                    )
                    MeRowP(
                        Icons.Rounded.Email,
                        "Email",
                        email.ifBlank { "Not set" }
                    )
                    MeRowP(
                        Icons.Rounded.Info,
                        "My QR code",
                        "Share your Homira contact"
                    ) {
                        ownQrOpen = true
                    }
                }
            }
        }

        item {
            SectionTitleP("Calls")
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HomiraSurface
                )
            ) {
                Column {
                    MeToggleRowP(
                        Icons.Rounded.DataSaverOn,
                        "Use less data for calls",
                        if (lowDataCalls) {
                            "Lower video bitrate while prioritizing voice"
                        } else {
                            "Adaptive quality"
                        },
                        lowDataCalls,
                        onLowDataChanged
                    )
                    MeToggleRowP(
                        Icons.Rounded.Voicemail,
                        "Voicemail",
                        if (voicemailEnabled) {
                            "Callers can leave a voice message"
                        } else {
                            "Voicemail is off"
                        },
                        voicemailEnabled,
                        onVoicemailEnabledChanged
                    )
                    if (voicemailEnabled) {
                        MeRowP(
                            Icons.Rounded.Mic,
                            "Voicemail greeting",
                            if (voicemailGreetingMode == "voice") {
                                "Custom voice greeting"
                            } else {
                                "Default Homira greeting"
                            },
                            onVoicemail
                        )
                    }
                }
            }
        }

        item {
            SectionTitleP("Privacy")
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HomiraSurface
                )
            ) {
                Column {
                    MeRowP(
                        Icons.Rounded.Block,
                        "Blocked people",
                        "Manage who can call you",
                        onBlockedPeople
                    )
                }
            }
        }

        item {
            SectionTitleP("Notifications")
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HomiraSurface
                )
            ) {
                Column {
                    MeToggleRowP(
                        Icons.Rounded.Notifications,
                        "Call notifications",
                        "Incoming and missed calls",
                        callNotifications,
                        onNotificationsChanged
                    )
                    MeRowP(
                        Icons.Rounded.VolumeUp,
                        "Ringtone",
                        ringtoneTitle,
                        onRingtone
                    )
                }
            }
        }

        item {
            SectionTitleP("Account")
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HomiraSurface
                )
            ) {
                MeRowP(
                    Icons.Rounded.Security,
                    "Sign out",
                    "Sign out of Homira on this device"
                ) {
                    confirmSignOut = true
                }
            }
        }

        item {
            SectionTitleP("About")
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HomiraSurface
                )
            ) {
                MeRowP(
                    Icons.Rounded.Info,
                    "About Homira",
                    "Private voice and video calling"
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    if (avatarOpen && avatarBitmap != null) {
        AlertDialog(
            onDismissRequest = { avatarOpen = false },
            text = {
                Image(
                    avatarBitmap,
                    contentDescription = "Profile photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Fit
                )
            },
            confirmButton = {
                TextButton(onClick = { avatarOpen = false }) {
                    Text("Close", color = HomiraGreen)
                }
            },
            containerColor = HomiraBackground
        )
    }

    if (ownQrOpen) {
        HomiraQrDialogP(
            title = name,
            subtitle = buildString {
                if (username.isNotBlank()) append("@$username")
                if (phone.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(phone)
                }
            }.ifBlank { "Homira profile" },
            payload = homiraContactQrPayloadP(
                name,
                phone,
                username
            ),
            onDismiss = { ownQrOpen = false }
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = {
                Text(
                    "Sign out?",
                    color = HomiraText
                )
            },
            text = {
                Text(
                    "Your local call history stays on this device for this Homira account.",
                    color = HomiraMuted
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        onSignOut()
                    }
                ) {
                    Text("Sign out", color = HomiraDanger)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                    }
                ) {
                    Text("Cancel", color = HomiraMuted)
                }
            },
            containerColor = HomiraSurface
        )
    }
}

@Composable
private fun MeRowP(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = 16.dp,
                vertical = 13.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = HomiraMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = HomiraText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                color = HomiraMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun MeToggleRowP(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = 11.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = HomiraMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = HomiraText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                color = HomiraMuted,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked
        )
    }
}

@Composable
private fun EditProfileScreen(
    name: String,
    username: String,
    phone: String,
    about: String,
    email: String,
    avatarUri: String?,
    callCardUri: String?,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: (
        String,
        String,
        String,
        String,
        String,
        String?,
        String?
    ) -> Unit
) {
    var editedName by rememberSaveable { mutableStateOf(name) }
    var editedAbout by rememberSaveable { mutableStateOf(about) }
    var editedAvatar by rememberSaveable {
        mutableStateOf(avatarUri)
    }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) editedAvatar = uri.toString()
    }

    val avatarBitmap = rememberBitmapP(editedAvatar)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(HomiraBackground)
            .safeDrawingPadding(),
        contentPadding = PaddingValues(
            horizontal = 20.dp,
            vertical = 14.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = HomiraText
                    )
                }
                Text(
                    "Edit profile",
                    color = HomiraText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    enabled = !saving,
                    onClick = {
                        onSave(
                            editedName,
                            username,
                            phone,
                            editedAbout,
                            email,
                            editedAvatar,
                            callCardUri
                        )
                    }
                ) {
                    Text(
                        if (saving) "Saving…" else "Save",
                        color = if (saving) {
                            HomiraMuted
                        } else {
                            HomiraGreen
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HomiraSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .clickable {
                                avatarPicker.launch("image/*")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarBitmap != null) {
                            Image(
                                avatarBitmap,
                                contentDescription = "Profile photo",
                                modifier = Modifier
                                    .size(112.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(112.dp),
                                shape = CircleShape,
                                color = HomiraGreen.copy(alpha = .13f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.AddAPhoto,
                                        contentDescription = null,
                                        tint = HomiraGreen,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Tap photo to change it",
                        color = HomiraMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            ProfileFieldP(
                "Name",
                editedName
            ) {
                editedName = it
            }
        }

        item {
            ProfileFieldP(
                "About",
                editedAbout
            ) {
                editedAbout = it
            }
        }

        item {
            SectionTitleP("Account identity")
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HomiraSurface
                )
            ) {
                Column {
                    MeRowP(
                        Icons.Rounded.Person,
                        "Username",
                        if (username.isBlank()) {
                            "Not set"
                        } else {
                            "@$username"
                        }
                    )
                    MeRowP(
                        Icons.Rounded.Phone,
                        "Phone number",
                        phone.ifBlank { "Not set" }
                    )
                    MeRowP(
                        Icons.Rounded.Email,
                        "Email",
                        email.ifBlank { "Not set" }
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedPeopleScreen(
    contacts: List<HomiraPerson>,
    blockedUserIds: Set<String>,
    onBack: () -> Unit,
    onBlockChanged: (HomiraPerson, Boolean) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visibleContacts = remember(contacts, query, blockedUserIds) {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) {
            contacts.sortedWith(
                compareByDescending<HomiraPerson> { it.id in blockedUserIds }
                    .thenBy { it.name.lowercase() }
            )
        } else {
            contacts.filter {
                it.name.lowercase().contains(normalized) ||
                    it.number.contains(normalized)
            }.sortedBy { it.name.lowercase() }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(HomiraBackground)
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = HomiraText
                    )
                }
                Column {
                    Text(
                        "Blocked people",
                        color = HomiraText,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Blocked accounts can't start calls to you",
                        color = HomiraMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search contacts") },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = HomiraMuted
                    )
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        if (visibleContacts.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = HomiraSurface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.Block,
                            contentDescription = null,
                            tint = HomiraMuted,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (contacts.isEmpty()) {
                                "No Homira contacts yet"
                            } else {
                                "No contacts match your search"
                            },
                            color = HomiraMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(
                items = visibleContacts,
                key = { "block-${it.id}" }
            ) { person ->
                val blocked = person.id in blockedUserIds
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = HomiraSurface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PersonAvatarP(person, 46)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                person.name,
                                color = HomiraText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (person.number.isNotBlank()) {
                                Text(
                                    person.number,
                                    color = HomiraMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                onBlockChanged(person, !blocked)
                            }
                        ) {
                            Text(
                                if (blocked) "Unblock" else "Block",
                                color = if (blocked) HomiraGreen else HomiraDanger,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoicemailSettingsScreen(
    repository: HomiraLiveRepository,
    liveMode: Boolean,
    initialMode: String,
    initialPath: String?,
    onGreetingChanged: (String, String?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember(context) { HomiraAudioRecorder(context) }
    val player = remember { HomiraAudioPlayer() }

    var customGreeting by rememberSaveable(initialMode) {
        mutableStateOf(initialMode == "voice")
    }
    var greetingPath by rememberSaveable(initialPath) {
        mutableStateOf(initialPath)
    }
    var recording by rememberSaveable { mutableStateOf(false) }
    var recordingSeconds by rememberSaveable { mutableIntStateOf(0) }
    var previewPlaying by rememberSaveable { mutableStateOf(false) }
    var savedGreetingSeconds by rememberSaveable { mutableIntStateOf(0) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pendingRecordStart by remember { mutableStateOf(false) }
    var microphoneGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val startRecording: () -> Unit = {
        player.stop()
        previewPlaying = false

        val file = File(
            context.cacheDir,
            "homira-greeting-${System.currentTimeMillis()}.m4a"
        )
        runCatching {
            recorder.start(file)
        }.onSuccess {
            recordingFile = file
            recordingSeconds = 0
            recording = true
        }.onFailure {
            file.delete()
            Toast.makeText(
                context,
                it.message ?: "Could not start recording.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        microphoneGranted = granted
        if (!granted) {
            pendingRecordStart = false
            Toast.makeText(
                context,
                "Microphone permission is required to record your greeting.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(microphoneGranted, pendingRecordStart) {
        if (microphoneGranted && pendingRecordStart) {
            pendingRecordStart = false
            startRecording()
        }
    }

    val stopAndSave: () -> Unit = {
        val file = recordingFile
        val durationSeconds = recordingSeconds.coerceAtLeast(1)
        val stopped = recorder.stop()
        recording = false

        if (!stopped || file == null || !file.exists() || file.length() == 0L) {
            file?.delete()
            recordingFile = null
            Toast.makeText(
                context,
                "That recording was too short. Try again.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            previewFile?.takeIf { it != file }?.delete()
            previewFile = file
            savedGreetingSeconds = durationSeconds
            customGreeting = true

            if (liveMode) {
                saving = true
                scope.launch {
                    runCatching {
                        repository.saveVoicemailGreeting(file)
                    }.onSuccess { profile ->
                        greetingPath = profile.voicemailGreetingPath
                        customGreeting = profile.voicemailGreetingMode == "voice"
                        onGreetingChanged(
                            profile.voicemailGreetingMode,
                            profile.voicemailGreetingPath
                        )
                    }.onFailure {
                        Toast.makeText(
                            context,
                            it.message ?: "Could not save your greeting.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    saving = false
                }
            } else {
                greetingPath = file.absolutePath
                onGreetingChanged("voice", greetingPath)
            }
        }
    }

    LaunchedEffect(recording) {
        if (recording) {
            while (recording && recordingSeconds < 30) {
                delay(1_000)
                if (recording) recordingSeconds++
            }
            if (recording && recordingSeconds >= 30) {
                stopAndSave()
            }
        }
    }

    fun playGreeting() {
        if (previewPlaying) {
            player.stop()
            previewPlaying = false
            return
        }

        previewFile?.takeIf { it.exists() }?.let { file ->
            previewPlaying = true
            player.play(file) { previewPlaying = false }
            return
        }

        val path = greetingPath ?: return
        if (!liveMode) return

        scope.launch {
            runCatching {
                val bytes = repository.downloadVoicemailAudio(path)
                File.createTempFile("homira-greeting-preview-", ".m4a", context.cacheDir).apply {
                    writeBytes(bytes)
                }
            }.onSuccess { file ->
                previewFile?.delete()
                previewFile = file
                previewPlaying = true
                player.play(file) { previewPlaying = false }
            }.onFailure {
                Toast.makeText(
                    context,
                    it.message ?: "Could not load your greeting.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun useDefaultGreeting() {
        player.stop()
        previewPlaying = false

        if (!liveMode) {
            customGreeting = false
            greetingPath = null
            onGreetingChanged("default", null)
            return
        }

        saving = true
        scope.launch {
            runCatching {
                repository.useDefaultVoicemailGreeting()
            }.onSuccess { profile ->
                customGreeting = false
                greetingPath = null
                previewFile?.delete()
                previewFile = null
                onGreetingChanged(
                    profile.voicemailGreetingMode,
                    profile.voicemailGreetingPath
                )
            }.onFailure {
                Toast.makeText(
                    context,
                    it.message ?: "Could not switch to the default greeting.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            saving = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.cancel()
            player.stop()
            recordingFile?.delete()
            previewFile?.takeIf { it != recordingFile }?.delete()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(HomiraBackground)
            .safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        if (recording) stopAndSave()
                        onBack()
                    }
                ) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = HomiraText
                    )
                }
                Column {
                    Text(
                        "Voicemail",
                        color = HomiraText,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "What callers hear when you don't answer",
                        color = HomiraMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = HomiraSurface)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(46.dp),
                            shape = CircleShape,
                            color = HomiraGreen.copy(alpha = .12f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Mic,
                                    contentDescription = null,
                                    tint = HomiraGreen
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Your greeting",
                                color = HomiraText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (customGreeting) {
                                    if (greetingPath != null || previewFile != null) {
                                        "Custom voice greeting"
                                    } else {
                                        "Record your voice"
                                    }
                                } else {
                                    "Default Homira greeting"
                                },
                                color = HomiraMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip("My voice", customGreeting) {
                            customGreeting = true
                        }
                        FilterChip("Default", !customGreeting) {
                            if (!saving && !recording) useDefaultGreeting()
                        }
                    }

                    if (customGreeting) {
                        Spacer(Modifier.height(18.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = HomiraSurfaceRaised
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val canPreview =
                                    previewFile?.exists() == true || greetingPath != null
                                IconButton(
                                    enabled = canPreview && !saving && !recording,
                                    onClick = { playGreeting() }
                                ) {
                                    Icon(
                                        if (previewPlaying) {
                                            Icons.Rounded.Pause
                                        } else {
                                            Icons.Rounded.PlayArrow
                                        },
                                        contentDescription = "Preview greeting",
                                        tint = if (canPreview) HomiraGreen else HomiraMuted
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (canPreview) "Current greeting" else "No custom greeting yet",
                                        color = HomiraText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        when {
                                            saving -> "Saving…"
                                            savedGreetingSeconds > 0 ->
                                                "0:${savedGreetingSeconds.toString().padStart(2, '0')}"
                                            canPreview -> "Saved securely"
                                            else -> "Record up to 30 seconds"
                                        },
                                        color = HomiraMuted,
                                        fontSize = 12.sp
                                    )
                                }
                                if (canPreview) {
                                    Text(
                                        "My voice",
                                        color = HomiraGreen,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (saving) return@Button

                                if (recording) {
                                    stopAndSave()
                                } else if (microphoneGranted) {
                                    startRecording()
                                } else {
                                    pendingRecordStart = true
                                    microphoneLauncher.launch(
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                }
                            },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (recording) {
                                    HomiraDanger
                                } else {
                                    HomiraText
                                },
                                contentColor = if (recording) {
                                    Color.White
                                } else {
                                    HomiraBackground
                                }
                            )
                        ) {
                            Icon(
                                if (recording) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    saving -> "Saving greeting…"
                                    recording ->
                                        "Stop recording · 0:${recordingSeconds.toString().padStart(2, '0')}"
                                    else -> "Record a new greeting"
                                }
                            )
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
            InfoRowP(
                Icons.Rounded.PhoneMissed,
                "No answer",
                "Offer voicemail after the ring timeout"
            )
            InfoRowP(
                Icons.Rounded.PhoneInTalk,
                "Busy",
                "Offer voicemail when you're already on a call"
            )
            InfoRowP(
                Icons.Rounded.Voicemail,
                "Message length",
                "Up to 2 minutes"
            )
        }
    }
}

@Composable
private fun LeaveVoicemailScreen(
    offer: VoicemailOffer,
    repository: HomiraLiveRepository,
    onCallAgain: (VoicemailOffer) -> Unit,
    onDismiss: () -> Unit,
    onSent: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember(context) { HomiraAudioRecorder(context) }
    val player = remember { HomiraAudioPlayer() }

    var greetingFile by remember { mutableStateOf<File?>(null) }
    var greetingLoading by remember { mutableStateOf(offer.greetingPath != null) }
    var greetingPlaying by remember { mutableStateOf(false) }
    var greetingFinished by remember { mutableStateOf(offer.greetingPath == null) }

    var recording by remember { mutableStateOf(false) }
    var recordingSeconds by rememberSaveable { mutableIntStateOf(0) }
    var messageFile by remember { mutableStateOf<File?>(null) }
    var reviewPlaying by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var pendingRecordStart by remember { mutableStateOf(false) }

    var microphoneGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val startRecording: () -> Unit = {
        player.stop()
        greetingPlaying = false
        reviewPlaying = false

        val file = File(
            context.cacheDir,
            "homira-message-${System.currentTimeMillis()}.m4a"
        )

        runCatching {
            recorder.start(file)
        }.onSuccess {
            messageFile?.takeIf { it != file }?.delete()
            messageFile = file
            recordingSeconds = 0
            recording = true
        }.onFailure {
            file.delete()
            Toast.makeText(
                context,
                it.message ?: "Could not start recording.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        microphoneGranted = granted
        if (!granted) {
            pendingRecordStart = false
            Toast.makeText(
                context,
                "Microphone permission is required to leave voicemail.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(microphoneGranted, pendingRecordStart) {
        if (microphoneGranted && pendingRecordStart) {
            pendingRecordStart = false
            startRecording()
        }
    }

    val stopRecording: () -> Unit = {
        val file = messageFile
        val stopped = recorder.stop()
        recording = false

        if (!stopped || file == null || !file.exists() || file.length() == 0L) {
            file?.delete()
            messageFile = null
            recordingSeconds = 0
            Toast.makeText(
                context,
                "That message was too short. Try again.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(recording) {
        if (recording) {
            while (recording && recordingSeconds < 120) {
                delay(1_000)
                if (recording) recordingSeconds++
            }
            if (recording && recordingSeconds >= 120) {
                stopRecording()
            }
        }
    }

    LaunchedEffect(offer.greetingPath) {
        val path = offer.greetingPath
        if (path == null) {
            greetingLoading = false
            greetingFinished = true
            return@LaunchedEffect
        }

        runCatching {
            val bytes = repository.downloadVoicemailAudio(path)
            File.createTempFile(
                "homira-caller-greeting-",
                ".m4a",
                context.cacheDir
            ).apply {
                writeBytes(bytes)
            }
        }.onSuccess { file ->
            greetingFile = file
            greetingLoading = false
            greetingPlaying = true
            player.play(file) {
                greetingPlaying = false
                greetingFinished = true
            }
        }.onFailure {
            greetingLoading = false
            greetingFinished = true
        }
    }

    fun skipGreeting() {
        player.stop()
        greetingPlaying = false
        greetingFinished = true
    }

    fun playReview() {
        val file = messageFile ?: return

        if (reviewPlaying) {
            player.stop()
            reviewPlaying = false
        } else {
            player.stop()
            reviewPlaying = true
            player.play(file) {
                reviewPlaying = false
            }
        }
    }

    fun discardAndRecordAgain() {
        player.stop()
        reviewPlaying = false
        messageFile?.delete()
        messageFile = null
        recordingSeconds = 0

        if (microphoneGranted) {
            startRecording()
        } else {
            pendingRecordStart = true
            microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.cancel()
            player.stop()
            greetingFile?.delete()
            messageFile?.delete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomiraBackground)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    enabled = !sending,
                    onClick = onDismiss
                ) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = HomiraText
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.weight(.35f))
            PersonAvatarP(offer.person, 132)
            Spacer(Modifier.height(18.dp))
            Text(
                "No answer",
                color = HomiraText,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Leave a voice message for ${offer.person.name}",
                color = HomiraMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            when {
                greetingLoading -> {
                    Text(
                        "Loading ${offer.person.name}'s greeting…",
                        color = HomiraMuted,
                        fontSize = 13.sp
                    )
                }

                greetingPlaying -> {
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = HomiraSurfaceRaised
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 14.dp,
                                vertical = 9.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = HomiraGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "Playing greeting…",
                                color = HomiraText,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { skipGreeting() }) {
                        Text("Skip greeting", color = HomiraMuted)
                    }
                }

                !greetingFinished -> Unit

                recording -> {
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = HomiraDanger.copy(alpha = .14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 16.dp,
                                vertical = 10.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = HomiraDanger,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "Recording · ${recordingSeconds / 60}:${(recordingSeconds % 60).toString().padStart(2, '0')}",
                                color = HomiraText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { stopRecording() },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HomiraDanger,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Stop recording")
                    }
                }

                messageFile != null -> {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = HomiraSurface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                enabled = !sending,
                                onClick = { playReview() }
                            ) {
                                Icon(
                                    if (reviewPlaying) {
                                        Icons.Rounded.Pause
                                    } else {
                                        Icons.Rounded.PlayArrow
                                    },
                                    contentDescription = "Preview voicemail",
                                    tint = HomiraGreen
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Your voicemail",
                                    color = HomiraText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${recordingSeconds / 60}:${(recordingSeconds % 60).toString().padStart(2, '0')}",
                                    color = HomiraMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Button(
                        enabled = !sending,
                        onClick = {
                            val file = messageFile ?: return@Button
                            sending = true
                            player.stop()
                            reviewPlaying = false

                            scope.launch {
                                runCatching {
                                    repository.uploadVoicemail(
                                        recipientId = offer.person.id,
                                        callSessionId = offer.callSessionId,
                                        audioFile = file,
                                        durationMs = recordingSeconds
                                            .coerceIn(1, 120) * 1000
                                    )
                                }.onSuccess {
                                    Toast.makeText(
                                        context,
                                        "Voicemail sent",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onSent()
                                }.onFailure {
                                    Toast.makeText(
                                        context,
                                        it.message ?: "Could not send voicemail.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                sending = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HomiraGreen,
                            contentColor = HomiraBackground
                        )
                    ) {
                        Text(
                            if (sending) "Sending…" else "Send voicemail",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(7.dp))
                    TextButton(
                        enabled = !sending,
                        onClick = { discardAndRecordAgain() }
                    ) {
                        Text("Record again", color = HomiraMuted)
                    }
                }

                else -> {
                    Text(
                        if (offer.greetingPath == null) {
                            "They didn't answer. Record your message after the tone."
                        } else {
                            "Greeting finished. You can record your message now."
                        },
                        color = HomiraMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (microphoneGranted) {
                                startRecording()
                            } else {
                                pendingRecordStart = true
                                microphoneLauncher.launch(
                                    Manifest.permission.RECORD_AUDIO
                                )
                            }
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HomiraText,
                            contentColor = HomiraBackground
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Record voice message")
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (!recording && messageFile == null && !sending) {
                TextButton(onClick = { onCallAgain(offer) }) {
                    Icon(
                        Icons.Rounded.Call,
                        contentDescription = null,
                        tint = HomiraGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Call again", color = HomiraGreen)
                }
                TextButton(onClick = onDismiss) {
                    Text("Not now", color = HomiraMuted)
                }
            }

            Spacer(Modifier.height(24.dp))
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp),
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
    var callInfoOpen by remember { mutableStateOf(false) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    val inPictureInPicture =
        HomiraCallUiState.pictureInPictureActive
    var localFeedPrimary by rememberSaveable {
        mutableStateOf(false)
    }
    var selfViewScale by rememberSaveable {
        mutableFloatStateOf(1f)
    }
    var selfViewOffsetX by rememberSaveable {
        mutableFloatStateOf(0f)
    }
    var selfViewOffsetY by rememberSaveable {
        mutableFloatStateOf(0f)
    }

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

    LaunchedEffect(inPictureInPicture) {
        if (inPictureInPicture) {
            controlsVisible = false
            menuOpen = false
            callInfoOpen = false
        }
    }

    LaunchedEffect(
        localVideo,
        remoteVideoEnabled,
        remoteScreenSharing
    ) {
        if (
            !localVideo ||
            !remoteVideoEnabled ||
            remoteScreenSharing
        ) {
            localFeedPrimary = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(if (video) Color.Black else HomiraBackground)
            .clickable {
                if (video && !inPictureInPicture) {
                    controlsVisible = true
                }
            }
    ) {
        if (video) {
            val density = LocalDensity.current
            val availableWidthPx = with(density) {
                maxWidth.toPx()
            }
            val availableHeightPx = with(density) {
                maxHeight.toPx()
            }

            val canShowLocal =
                localVideo &&
                    localVideoTrack != null &&
                    eglContext != null
            val canShowRemote =
                remoteVideoEnabled &&
                    remoteVideoTrack != null &&
                    eglContext != null
            val canSwapFeeds =
                canShowLocal &&
                    canShowRemote &&
                    !remoteScreenSharing
            val localIsMain =
                localFeedPrimary && canSwapFeeds

            when {
                localIsMain -> {
                    WebRtcVideoSurface(
                        track = requireNotNull(localVideoTrack),
                        eglContext = requireNotNull(eglContext),
                        mirror = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                canShowRemote -> {
                    WebRtcVideoSurface(
                        track = requireNotNull(remoteVideoTrack),
                        eglContext = requireNotNull(eglContext),
                        mirror = false,
                        fit = remoteScreenSharing,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (liveState == null) {
                            Text(
                                "Camera preview",
                                color = HomiraMuted,
                                fontSize = 14.sp
                            )
                        } else {
                            Column(
                                horizontalAlignment =
                                    Alignment.CenterHorizontally
                            ) {
                                PersonAvatarP(person, 128)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Waiting for video…",
                                    color = Color.White.copy(
                                        alpha = .72f
                                    ),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            val tileTrack = when {
                localIsMain && canShowRemote ->
                    remoteVideoTrack
                canShowLocal ->
                    localVideoTrack
                else ->
                    null
            }
            val tileMirror = !localIsMain
            val baseTileWidthDp =
                if (inPictureInPicture) 54f else 108f
            val baseTileHeightDp =
                if (inPictureInPicture) 78f else 156f
            val effectiveScale =
                if (inPictureInPicture) 1f else selfViewScale
            val tileWidth =
                (baseTileWidthDp * effectiveScale).dp
            val tileHeight =
                (baseTileHeightDp * effectiveScale).dp
            val baseWidthPx = with(density) {
                baseTileWidthDp.dp.toPx()
            }
            val baseHeightPx = with(density) {
                baseTileHeightDp.dp.toPx()
            }
            val sidePaddingPx = with(density) {
                32.dp.toPx()
            }
            val bottomReservedPx = with(density) {
                180.dp.toPx()
            }

            if (
                tileTrack != null &&
                eglContext != null
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(
                            top = 12.dp,
                            end = 16.dp
                        )
                        .offset {
                            IntOffset(
                                if (inPictureInPicture) {
                                    0
                                } else {
                                    selfViewOffsetX.roundToInt()
                                },
                                if (inPictureInPicture) {
                                    0
                                } else {
                                    selfViewOffsetY.roundToInt()
                                }
                            )
                        }
                        .width(tileWidth)
                        .height(tileHeight)
                        .clickable(
                            enabled =
                                canSwapFeeds &&
                                    !inPictureInPicture
                        ) {
                            localFeedPrimary =
                                !localFeedPrimary
                        }
                        .pointerInput(
                            canSwapFeeds,
                            availableWidthPx,
                            availableHeightPx
                        ) {
                            awaitEachGesture {
                                awaitFirstDown(
                                    requireUnconsumed = false
                                )

                                var gestureActive = true
                                while (gestureActive) {
                                    val event = awaitPointerEvent()
                                    val zoom =
                                        event.calculateZoom()
                                    val pan =
                                        event.calculatePan()

                                    val nextScale =
                                        (
                                            selfViewScale *
                                                zoom
                                        ).coerceIn(
                                            .65f,
                                            1.25f
                                        )
                                    selfViewScale = nextScale

                                    val currentWidthPx =
                                        baseWidthPx *
                                            nextScale
                                    val currentHeightPx =
                                        baseHeightPx *
                                            nextScale
                                    val maxHorizontalTravel =
                                        (
                                            availableWidthPx -
                                                currentWidthPx -
                                                sidePaddingPx
                                        ).coerceAtLeast(0f)
                                    val maxVerticalTravel =
                                        (
                                            availableHeightPx -
                                                currentHeightPx -
                                                bottomReservedPx
                                        ).coerceAtLeast(0f)

                                    selfViewOffsetX =
                                        (
                                            selfViewOffsetX +
                                                pan.x
                                        ).coerceIn(
                                            -maxHorizontalTravel,
                                            0f
                                        )
                                    selfViewOffsetY =
                                        (
                                            selfViewOffsetY +
                                                pan.y
                                        ).coerceIn(
                                            0f,
                                            maxVerticalTravel
                                        )

                                    if (
                                        zoom != 1f ||
                                        pan.x != 0f ||
                                        pan.y != 0f
                                    ) {
                                        event.changes.forEach {
                                            change ->
                                            if (
                                                change
                                                    .positionChanged()
                                            ) {
                                                change.consume()
                                            }
                                        }
                                    }

                                    gestureActive =
                                        event.changes.any {
                                            it.pressed
                                        }
                                }

                                // WhatsApp-style elastic upper bound:
                                // the tile may stretch while touched,
                                // then returns to its normal maximum.
                                if (selfViewScale > 1f) {
                                    selfViewScale = 1f
                                }
                            }
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = HomiraSurfaceRaised,
                    shadowElevation = 8.dp
                ) {
                    WebRtcVideoSurface(
                        track = tileTrack,
                        eglContext = eglContext,
                        mirror = tileMirror,
                        overlay = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                !inPictureInPicture &&
                    (!video || controlsVisible)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMinimize) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Minimize",
                            tint = HomiraText
                        )
                    }
                    Spacer(Modifier.weight(1f))
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

            AnimatedVisibility(
                muted && !inPictureInPicture
            ) {
                Surface(shape = RoundedCornerShape(99.dp), color = HomiraDanger.copy(alpha = .16f)) {
                    Row(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.MicOff, contentDescription = null, tint = HomiraDanger, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("You're muted", color = HomiraText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            AnimatedVisibility(
                remoteMuted && !inPictureInPicture
            ) {
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

            AnimatedVisibility(
                screenSharing && !inPictureInPicture
            ) {
                Text(
                    "Sharing your screen",
                    color = HomiraGreen,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            AnimatedVisibility(
                remoteScreenSharing && !inPictureInPicture
            ) {
                Text(
                    "${person.name} is sharing their screen",
                    color = Color.White.copy(alpha = .82f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            AnimatedVisibility(
                !inPictureInPicture &&
                    (!video || controlsVisible)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(20.dp))

                    if (!video) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceEvenly
                        ) {
                            CallControlP(
                                Icons.Rounded.VolumeUp,
                                "Speaker",
                                speaker
                            ) {
                                speaker = !speaker
                                onSpeakerChanged(speaker)
                            }
                            CallControlP(
                                Icons.Rounded.Videocam,
                                "Video",
                                localVideo
                            ) {
                                onVideoChanged(!localVideo)
                                controlsVisible = true
                            }
                            CallControlP(
                                Icons.Rounded.MicOff,
                                "Mute",
                                muted
                            ) {
                                muted = !muted
                                onMuteChanged(muted)
                            }
                        }

                        Spacer(Modifier.height(22.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceEvenly
                        ) {
                            Box {
                                CallControlP(
                                    Icons.Rounded.MoreVert,
                                    "More",
                                    false
                                ) {
                                    menuOpen = true
                                }
                                CallOptionsMenuP(
                                    expanded = menuOpen,
                                    screenSharing = screenSharing,
                                    localVideo = localVideo,
                                    onDismiss = {
                                        menuOpen = false
                                    },
                                    onShare = {
                                        onScreenShareChanged(
                                            !screenSharing
                                        )
                                        menuOpen = false
                                    },
                                    onSwitchCamera = {
                                        onSwitchCamera()
                                        menuOpen = false
                                    },
                                    onCallInfo = {
                                        callInfoOpen = true
                                        menuOpen = false
                                    }
                                )
                            }

                            CallControlP(
                                Icons.Rounded.ScreenShare,
                                "Share",
                                screenSharing
                            ) {
                                onScreenShareChanged(!screenSharing)
                            }

                            EndCallControlP(onEnd)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Top
                        ) {
                            Box {
                                CallControlP(
                                    Icons.Rounded.MoreVert,
                                    "More",
                                    false
                                ) {
                                    menuOpen = true
                                    controlsVisible = true
                                }
                                CallOptionsMenuP(
                                    expanded = menuOpen,
                                    screenSharing = screenSharing,
                                    localVideo = localVideo,
                                    onDismiss = {
                                        menuOpen = false
                                    },
                                    onShare = {
                                        onScreenShareChanged(
                                            !screenSharing
                                        )
                                        menuOpen = false
                                    },
                                    onSwitchCamera = {
                                        onSwitchCamera()
                                        menuOpen = false
                                    },
                                    onCallInfo = {
                                        callInfoOpen = true
                                        menuOpen = false
                                    }
                                )
                            }

                            CallControlP(
                                Icons.Rounded.Videocam,
                                "Video",
                                localVideo
                            ) {
                                onVideoChanged(!localVideo)
                                controlsVisible = true
                            }

                            CallControlP(
                                Icons.Rounded.VolumeUp,
                                "Speaker",
                                speaker
                            ) {
                                speaker = !speaker
                                onSpeakerChanged(speaker)
                                controlsVisible = true
                            }

                            CallControlP(
                                Icons.Rounded.MicOff,
                                "Mute",
                                muted
                            ) {
                                muted = !muted
                                onMuteChanged(muted)
                                controlsVisible = true
                            }

                            EndCallControlP(onEnd)
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        if (callInfoOpen) {
            AlertDialog(
                onDismissRequest = { callInfoOpen = false },
                title = { Text("Call info", color = HomiraText) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            person.name,
                            color = HomiraText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (startsWithVideo || videoEnabled || remoteVideoEnabled) {
                                "Video call"
                            } else {
                                "Voice call"
                            },
                            color = HomiraMuted,
                            fontSize = 13.sp
                        )
                        Text(
                            "Status: $statusText",
                            color = HomiraMuted,
                            fontSize = 13.sp
                        )
                        if (screenSharing) {
                            Text(
                                "You are sharing your screen.",
                                color = HomiraGreen,
                                fontSize = 13.sp
                            )
                        } else if (remoteScreenSharing) {
                            Text(
                                "${person.name} is sharing their screen.",
                                color = HomiraMuted,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            "Audio and video use encrypted WebRTC media paths. Homira's backend handles call setup and signaling, not the call media itself.",
                            color = HomiraMuted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { callInfoOpen = false }) {
                        Text("Done", color = HomiraGreen)
                    }
                },
                containerColor = HomiraSurface
            )
        }
    }
}

@Composable
private fun WebRtcVideoSurface(
    track: VideoTrack,
    eglContext: EglBase.Context,
    mirror: Boolean,
    overlay: Boolean = false,
    fit: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember(context, eglContext, mirror, overlay, fit) {
        SurfaceViewRenderer(context).apply {
            init(eglContext, null)
            setMirror(mirror)
            setScalingType(
                if (fit) {
                    RendererCommon.ScalingType.SCALE_ASPECT_FIT
                } else {
                    RendererCommon.ScalingType.SCALE_ASPECT_FILL
                }
            )
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
    val avatarBitmap = rememberBitmapP(person.avatarUri)

    Surface(
        modifier = Modifier.size(size.dp),
        shape = CircleShape,
        color = person.accent.copy(alpha = .13f)
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap,
                contentDescription = "${person.name} profile photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    person.marker,
                    color = person.accent,
                    fontSize = (size * .38f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
private fun CallOptionsMenuP(
    expanded: Boolean,
    screenSharing: Boolean,
    localVideo: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSwitchCamera: () -> Unit,
    onCallInfo: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    if (screenSharing) {
                        "Stop sharing screen"
                    } else {
                        "Share screen"
                    }
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Rounded.ScreenShare,
                    contentDescription = null
                )
            },
            onClick = onShare
        )

        if (localVideo) {
            DropdownMenuItem(
                text = { Text("Switch camera") },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.CameraAlt,
                        contentDescription = null
                    )
                },
                onClick = onSwitchCamera
            )
        }

        DropdownMenuItem(
            text = { Text("Call info") },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null
                )
            },
            onClick = onCallInfo
        )
    }
}

@Composable
private fun EndCallControlP(
    onEnd: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .size(56.dp)
                .clickable(onClick = onEnd),
            shape = CircleShape,
            color = HomiraDanger
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.CallEnd,
                    contentDescription = "End call",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            "End",
            color = HomiraMuted,
            fontSize = 11.sp
        )
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
private fun HomiraQrDialogP(
    title: String,
    subtitle: String,
    payload: String,
    onDismiss: () -> Unit
) {
    val qr = remember(payload) {
        runCatching {
            val size = 720
            val matrix = MultiFormatWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                size,
                size
            )
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    pixels[(y * size) + x] =
                        if (matrix[x, y]) {
                            android.graphics.Color.BLACK
                        } else {
                            android.graphics.Color.WHITE
                        }
                }
            }

            Bitmap.createBitmap(
                size,
                size,
                Bitmap.Config.ARGB_8888
            ).apply {
                setPixels(
                    pixels,
                    0,
                    size,
                    0,
                    0,
                    size,
                    size
                )
            }.asImageBitmap()
        }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                color = HomiraText,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White
                ) {
                    if (qr != null) {
                        Image(
                            bitmap = qr,
                            contentDescription = "$title QR code",
                            modifier = Modifier
                                .size(260.dp)
                                .padding(14.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    subtitle,
                    color = HomiraMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Scan to save or share this Homira contact.",
                    color = HomiraMuted.copy(alpha = .75f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = HomiraGreen)
            }
        },
        containerColor = HomiraSurface
    )
}

private fun homiraContactQrPayloadP(
    name: String,
    phone: String,
    username: String?
): String = buildString {
    append("BEGIN:VCARD\n")
    append("VERSION:3.0\n")
    append("FN:")
    append(name.replace("\n", " "))
    append("\n")
    if (phone.isNotBlank()) {
        append("TEL:")
        append(phone)
        append("\n")
    }
    if (!username.isNullOrBlank()) {
        append("NOTE:Homira @")
        append(username)
        append("\n")
    } else {
        append("NOTE:Homira contact\n")
    }
    append("END:VCARD")
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
    CallDirection.Cancelled -> Icons.Rounded.CallEnd
    CallDirection.Failed -> Icons.Rounded.PhoneMissed
}

private fun directionColor(direction: CallDirection): Color = when (direction) {
    CallDirection.Missed, CallDirection.Declined, CallDirection.Failed -> HomiraDanger
    CallDirection.Cancelled -> HomiraMuted
    else -> HomiraBlue
}

private fun directionLabel(direction: CallDirection): String = when (direction) {
    CallDirection.Incoming -> "Incoming"
    CallDirection.Outgoing -> "Outgoing"
    CallDirection.Missed -> "Missed"
    CallDirection.Declined -> "Declined"
    CallDirection.Cancelled -> "Cancelled"
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
