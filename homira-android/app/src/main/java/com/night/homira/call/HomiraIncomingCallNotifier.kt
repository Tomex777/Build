package com.night.homira.call

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.night.homira.MainActivity
import com.night.homira.data.LiveCallSession

class HomiraIncomingCallNotifier(
    context: Context
) {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(NotificationManager::class.java)
    private val alarmManager =
        appContext.getSystemService(AlarmManager::class.java)

    init {
        runCatching { ensureChannel() }
    }

    fun show(
        session: LiveCallSession,
        callerName: String,
        notificationsEnabled: Boolean,
        ringtoneUri: String? = null
    ): Boolean = show(
        callId = session.id,
        mediaType = session.mediaType,
        callerName = callerName,
        callerId = session.callerId,
        notificationsEnabled = notificationsEnabled,
        ringtoneUri = ringtoneUri
    )

    fun show(
        callId: String,
        mediaType: String,
        callerName: String,
        callerId: String? = null,
        notificationsEnabled: Boolean,
        ringtoneUri: String? = null,
        timeoutMs: Long = DEFAULT_RING_TIMEOUT_MS
    ): Boolean {
        val safeTimeoutMs = timeoutMs.coerceIn(
            MIN_RING_TIMEOUT_MS,
            MAX_RING_TIMEOUT_MS
        )
        scheduleRingTimeout(
            callId = callId,
            callerId = callerId,
            callerName = callerName,
            mediaType = mediaType,
            timeoutMs = safeTimeoutMs
        )

        if (!notificationsEnabled) return false

        if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val requestBase = notificationId(callId)

        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CALL_ID, callId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            appContext,
            requestBase,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(appContext, MainActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_ANSWER_CALL, true)
        }
        val answerPendingIntent = PendingIntent.getActivity(
            appContext,
            requestBase + 3,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(
            appContext,
            HomiraCallActionReceiver::class.java
        ).apply {
            action = ACTION_DECLINE
            putExtra(EXTRA_CALL_ID, callId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestBase + 1,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caller = Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        val notification = NotificationCompat.Builder(
            appContext,
            CHANNEL_INCOMING_CALLS
        )
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(callerName)
            .setContentText(
                if (mediaType == "video") {
                    "Incoming video call"
                } else {
                    "Incoming voice call"
                }
            )
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(safeTimeoutMs)
            .setContentIntent(openPendingIntent)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    declinePendingIntent,
                    answerPendingIntent
                )
            )
            .apply {
                val fullScreenAllowed =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                        runCatching {
                            notificationManager.canUseFullScreenIntent()
                        }.getOrDefault(false)

                if (fullScreenAllowed) {
                    setFullScreenIntent(openPendingIntent, true)
                }
            }
            .build()

        notificationManager.notify(
            NOTIFICATION_TAG,
            notificationId(callId),
            notification
        )
        HomiraRingtonePlayback.play(
            context = appContext,
            uriString = ringtoneUri,
            callId = callId,
            timeoutMs = safeTimeoutMs
        )
        return true
    }

    fun showOngoing(
        callId: String,
        mediaType: String,
        peerName: String,
        calling: Boolean = false
    ): Boolean {
        cancelRingTimeout(callId)

        if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            HomiraRingtonePlayback.stop()
            return false
        }

        val requestBase = notificationId(callId)

        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CALL_ID, callId)
        }
        val openPendingIntent = PendingIntent.getActivity(
            appContext,
            requestBase,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangUpIntent = Intent(
            appContext,
            HomiraCallActionReceiver::class.java
        ).apply {
            action = ACTION_HANG_UP
            putExtra(EXTRA_CALL_ID, callId)
        }
        val hangUpPendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestBase + 2,
            hangUpIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val peer = Person.Builder()
            .setName(peerName)
            .setImportant(true)
            .build()

        val notification = NotificationCompat.Builder(
            appContext,
            CHANNEL_INCOMING_CALLS
        )
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(peerName)
            .setContentText(
                when {
                    calling && mediaType == "video" -> "Calling · video"
                    calling -> "Calling…"
                    mediaType == "video" -> "Video call in progress"
                    else -> "Call in progress"
                }
            )
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPendingIntent)
            .setStyle(
                NotificationCompat.CallStyle.forOngoingCall(
                    peer,
                    hangUpPendingIntent
                )
            )
            .build()

        HomiraRingtonePlayback.stop()
        notificationManager.notify(
            NOTIFICATION_TAG,
            notificationId(callId),
            notification
        )
        return true
    }

    fun cancel(callId: String) {
        cancelRingTimeout(callId)
        notificationManager.cancel(
            NOTIFICATION_TAG,
            notificationId(callId)
        )
        HomiraRingtonePlayback.stop()
    }

    private fun scheduleRingTimeout(
        callId: String,
        callerId: String?,
        callerName: String,
        mediaType: String,
        timeoutMs: Long
    ) {
        val intent = Intent(
            appContext,
            HomiraCallActionReceiver::class.java
        ).apply {
            action = ACTION_RING_TIMEOUT
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_ID, callerId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_MEDIA_TYPE, mediaType)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            notificationId("$callId:ring-timeout"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + timeoutMs,
            pendingIntent
        )
    }

    private fun cancelRingTimeout(callId: String) {
        val intent = Intent(
            appContext,
            HomiraCallActionReceiver::class.java
        ).apply {
            action = ACTION_RING_TIMEOUT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            notificationId("$callId:ring-timeout"),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun ensureChannel() {
        if (notificationManager.getNotificationChannel(CHANNEL_INCOMING_CALLS) != null) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_INCOMING_CALLS,
            "Incoming calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming Homira voice and video calls"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            setSound(null, null)
        }

        notificationManager.createNotificationChannel(channel)
    }

    private fun notificationId(callId: String): Int =
        (callId.hashCode() and 0x7fffffff).coerceAtLeast(1)

    companion object {
        const val ACTION_DECLINE = "com.night.homira.action.DECLINE_CALL"
        const val ACTION_HANG_UP = "com.night.homira.action.HANG_UP_CALL"
        const val ACTION_RING_TIMEOUT = "com.night.homira.action.RING_TIMEOUT"
        const val EXTRA_CALL_ID = "homira_call_id"
        const val EXTRA_ANSWER_CALL = "homira_answer_call"
        const val EXTRA_CALLER_ID = "homira_caller_id"
        const val EXTRA_CALLER_NAME = "homira_caller_name"
        const val EXTRA_MEDIA_TYPE = "homira_media_type"

        const val DEFAULT_RING_TIMEOUT_MS = 30_000L
        private const val MIN_RING_TIMEOUT_MS = 1_000L
        private const val MAX_RING_TIMEOUT_MS = 45_000L

        private const val CHANNEL_INCOMING_CALLS = "homira_incoming_calls_v2"
        private const val NOTIFICATION_TAG = "homira_call"
    }
}


private object HomiraRingtonePlayback {
    private val handler = Handler(Looper.getMainLooper())
    private var active: Ringtone? = null
    private var activeCallId: String? = null

    @Synchronized
    fun play(
        context: Context,
        uriString: String?,
        callId: String,
        timeoutMs: Long
    ) {
        stop()

        val uri = uriString
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(
                RingtoneManager.TYPE_RINGTONE
            )
            ?: return

        val ringtone = runCatching {
            RingtoneManager.getRingtone(context, uri)
        }.getOrNull() ?: return

        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone.isLooping = true
        }
        active = ringtone
        activeCallId = callId

        runCatching {
            ringtone.play()
            handler.postDelayed(
                {
                    stopIfCall(callId)
                },
                timeoutMs
            )
        }.onFailure {
            active = null
            activeCallId = null
            runCatching { ringtone.stop() }
        }
    }

    @Synchronized
    private fun stopIfCall(callId: String) {
        if (activeCallId != callId) return
        stop()
    }

    @Synchronized
    fun stop() {
        val ringtone = active
        active = null
        activeCallId = null
        if (ringtone != null) {
            runCatching { ringtone.stop() }
        }
    }
}
