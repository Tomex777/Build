package com.night.homira.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.night.homira.MainActivity
import com.night.homira.data.LiveCallSession

class HomiraIncomingCallNotifier(
    context: Context
) {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(NotificationManager::class.java)

    init {
        ensureChannel()
    }

    fun show(
        session: LiveCallSession,
        callerName: String,
        notificationsEnabled: Boolean,
        ringtoneUri: String? = null
    ): Boolean {
        if (!notificationsEnabled) return false

        if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val requestBase = notificationId(session.id)

        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CALL_ID, session.id)
        }
        val openPendingIntent = PendingIntent.getActivity(
            appContext,
            requestBase,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(
            appContext,
            HomiraCallActionReceiver::class.java
        ).apply {
            action = ACTION_DECLINE
            putExtra(EXTRA_CALL_ID, session.id)
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

        val notification = Notification.Builder(appContext, CHANNEL_INCOMING_CALLS)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(callerName)
            .setContentText(
                if (session.mediaType == "video") {
                    "Incoming video call"
                } else {
                    "Incoming voice call"
                }
            )
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPendingIntent)
            .setStyle(
                Notification.CallStyle.forIncomingCall(
                    caller,
                    declinePendingIntent,
                    openPendingIntent
                )
            )
            .apply {
                if (notificationManager.canUseFullScreenIntent()) {
                    setFullScreenIntent(openPendingIntent, true)
                }
            }
            .build()

        notificationManager.notify(
            NOTIFICATION_TAG,
            notificationId(session.id),
            notification
        )
        HomiraRingtonePlayback.play(
            context = appContext,
            uriString = ringtoneUri
        )
        return true
    }

    fun cancel(callId: String) {
        notificationManager.cancel(
            NOTIFICATION_TAG,
            notificationId(callId)
        )
        HomiraRingtonePlayback.stop()
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
        const val EXTRA_CALL_ID = "homira_call_id"

        private const val CHANNEL_INCOMING_CALLS = "homira_incoming_calls_v2"
        private const val NOTIFICATION_TAG = "homira_call"
    }
}


private object HomiraRingtonePlayback {
    private var active: Ringtone? = null

    @Synchronized
    fun play(
        context: Context,
        uriString: String?
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
        ringtone.isLooping = true
        active = ringtone

        runCatching {
            ringtone.play()
        }.onFailure {
            active = null
            runCatching { ringtone.stop() }
        }
    }

    @Synchronized
    fun stop() {
        val ringtone = active ?: return
        active = null
        runCatching { ringtone.stop() }
    }
}
