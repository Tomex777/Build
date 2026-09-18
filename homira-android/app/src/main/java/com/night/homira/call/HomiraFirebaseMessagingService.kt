package com.night.homira.call

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.night.homira.data.HomiraLiveRepository
import com.night.homira.data.HomiraSettingsStore
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

    @Deprecated("FCM registration tokens are being replaced by installation IDs")
    override fun onNewToken(token: String) {
        persistMessagingTarget(token)
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
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != TYPE_INCOMING_CALL) return

        val callId = data["call_id"]?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return

        val expiresAt = data["expires_at"]
            ?.let { value ->
                runCatching {
                    OffsetDateTime.parse(value).toInstant()
                }.getOrNull()
            }

        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            return
        }

        val callerName = data["caller_name"]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Homira caller"

        val mediaType = data["media_type"]
            ?.takeIf { it == "audio" || it == "video" }
            ?: "audio"

        val settings = HomiraSettingsStore(this).load()
        if (!settings.callNotifications) return

        HomiraIncomingCallNotifier(this).show(
            callId = callId,
            mediaType = mediaType,
            callerName = callerName,
            notificationsEnabled = true,
            ringtoneUri = settings.ringtoneUri
        )
    }

    companion object {
        private const val TYPE_INCOMING_CALL = "incoming_call"
    }
}
