package com.night.homira.call

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.night.homira.data.HomiraLiveRepository
import com.night.homira.data.HomiraSettingsStore
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HomiraFirebaseMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId: String) {
        persistMessagingTarget(installationId)
    }

    private fun persistMessagingTarget(target: String) {
        HomiraPushBootstrap.storeTarget(this, target)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val repository = HomiraLiveRepository()
            runCatching {
                repository.initialize()
                if (repository.isSignedIn()) {
                    repository.registerPushToken(
                        deviceId = HomiraPushBootstrap.deviceId(
                            this@HomiraFirebaseMessagingService
                        ),
                        token = target,
                        platform = "android"
                    )
                    Log.i(
                        "HomiraPush",
                        "Push target registered for signed-in user"
                    )
                } else {
                    Log.i(
                        "HomiraPush",
                        "Push target cached until sign-in completes"
                    )
                }
            }.onFailure { error ->
                Log.e(
                    "HomiraPush",
                    "Could not register push target",
                    error
                )
            }
        }
    }

    private fun acknowledgeIncomingPush(
        callId: String,
        notificationShown: Boolean
    ) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val repository = HomiraLiveRepository()
            runCatching {
                repository.initialize()
                if (repository.isSignedIn()) {
                    repository.acknowledgeIncomingCallPush(
                        callId = callId,
                        deviceId = HomiraPushBootstrap.deviceId(
                            this@HomiraFirebaseMessagingService
                        ),
                        notificationShown = notificationShown
                    )
                    Log.i(
                        "HomiraPush",
                        "Push receipt acknowledged for $callId; " +
                            "notificationShown=$notificationShown"
                    )
                }
            }.onFailure { error ->
                Log.e(
                    "HomiraPush",
                    "Could not acknowledge push receipt for $callId",
                    error
                )
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: return

        if (
            type != TYPE_INCOMING_CALL &&
            type != TYPE_MISSED_CALL
        ) {
            return
        }

        val callId = data["call_id"]?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return

        Log.i(
            "HomiraPush",
            "Received $type push for $callId"
        )

        val callerName = data["caller_name"]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Homira caller"

        val mediaType = data["media_type"]
            ?.takeIf {
                it == "audio" || it == "video"
            }
            ?: "audio"

        if (type == TYPE_MISSED_CALL) {
            runCatching {
                HomiraIncomingCallNotifier(this).showMissed(
                    callId = callId,
                    mediaType = mediaType,
                    callerName = callerName
                )
            }
            return
        }

        val expiresAt = data["expires_at"]
            ?.let { value ->
                runCatching {
                    OffsetDateTime.parse(value).toInstant()
                }.getOrNull()
            }

        if (
            expiresAt != null &&
            !expiresAt.isAfter(Instant.now())
        ) {
            acknowledgeIncomingPush(
                callId = callId,
                notificationShown = false
            )
            return
        }

        val callerId = data["caller_id"]
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val remainingMs = expiresAt?.let { expiry ->
            Duration.between(
                Instant.now(),
                expiry
            )
                .toMillis()
                .coerceAtLeast(1_000L)
        }

        val timeoutMs = minOf(
            HomiraIncomingCallNotifier.DEFAULT_RING_TIMEOUT_MS,
            remainingMs
                ?: HomiraIncomingCallNotifier
                    .DEFAULT_RING_TIMEOUT_MS
        )

        val notificationShown = runCatching {
            val settings =
                HomiraSettingsStore(this).load()

            HomiraIncomingCallNotifier(this).show(
                callId = callId,
                mediaType = mediaType,
                callerName = callerName,
                callerId = callerId,
                notificationsEnabled =
                    settings.callNotifications,
                ringtoneUri = settings.ringtoneUri,
                timeoutMs = timeoutMs
            )
        }.getOrDefault(false)

        Log.i(
            "HomiraPush",
            "Incoming call $callId notificationShown=$notificationShown"
        )

        acknowledgeIncomingPush(
            callId = callId,
            notificationShown = notificationShown
        )
    }

    companion object {
        private const val TYPE_INCOMING_CALL = "incoming_call"
        private const val TYPE_MISSED_CALL = "missed_call"
    }
}
