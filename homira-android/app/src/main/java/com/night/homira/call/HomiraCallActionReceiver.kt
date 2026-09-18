package com.night.homira.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.night.homira.data.HomiraCallHistoryStore
import com.night.homira.data.HomiraLiveRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HomiraCallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (
            action != HomiraIncomingCallNotifier.ACTION_DECLINE &&
            action != HomiraIncomingCallNotifier.ACTION_HANG_UP &&
            action != HomiraIncomingCallNotifier.ACTION_RING_TIMEOUT
        ) {
            return
        }

        val callId = intent.getStringExtra(
            HomiraIncomingCallNotifier.EXTRA_CALL_ID
        ) ?: return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = HomiraLiveRepository()
                repository.initialize()

                var keepOngoingNotification = false

                if (repository.isSignedIn()) {
                    val session = repository.loadCallSessionById(callId)
                    val localUserId = repository.currentUserId()

                    if (session != null && localUserId != null) {
                        if (
                            action == HomiraIncomingCallNotifier.ACTION_RING_TIMEOUT &&
                            session.state == "active"
                        ) {
                            keepOngoingNotification = true
                        } else {
                            val incoming = session.calleeId == localUserId
                            val targetState = when (action) {
                                HomiraIncomingCallNotifier.ACTION_DECLINE ->
                                    if (
                                        incoming &&
                                        (
                                            session.state == "ringing" ||
                                            session.state == "connecting"
                                        )
                                    ) {
                                        "declined"
                                    } else {
                                        null
                                    }

                                HomiraIncomingCallNotifier.ACTION_HANG_UP ->
                                    when (session.state) {
                                        "ringing", "connecting" ->
                                            if (session.callerId == localUserId) {
                                                "cancelled"
                                            } else {
                                                "declined"
                                            }

                                        "active" -> "ended"
                                        else -> null
                                    }

                                HomiraIncomingCallNotifier.ACTION_RING_TIMEOUT ->
                                    if (
                                        incoming &&
                                        (
                                            session.state == "ringing" ||
                                            session.state == "connecting"
                                        )
                                    ) {
                                        "missed"
                                    } else {
                                        null
                                    }

                                else -> null
                            }

                            val finalSession = if (targetState != null) {
                                runCatching {
                                    repository.setCallState(
                                        callId,
                                        targetState
                                    )
                                }.getOrNull() ?: session
                            } else {
                                session
                            }

                            val peerUserId = if (incoming) {
                                session.callerId
                            } else {
                                session.calleeId
                            }
                            val peerProfile = runCatching {
                                repository.loadProfileById(peerUserId)
                            }.getOrNull()
                            val pushedCallerId = intent.getStringExtra(
                                HomiraIncomingCallNotifier.EXTRA_CALLER_ID
                            )
                            val pushedCallerName = intent.getStringExtra(
                                HomiraIncomingCallNotifier.EXTRA_CALLER_NAME
                            )
                            val peerName = if (
                                incoming &&
                                pushedCallerId == peerUserId &&
                                !pushedCallerName.isNullOrBlank()
                            ) {
                                pushedCallerName
                            } else {
                                peerProfile?.displayName?.takeIf { it.isNotBlank() }
                                    ?: peerProfile?.username?.takeIf { it.isNotBlank() }
                                    ?: peerProfile?.phoneE164
                                    ?: "Homira caller"
                            }
                            val mediaType = intent.getStringExtra(
                                HomiraIncomingCallNotifier.EXTRA_MEDIA_TYPE
                            )?.takeIf { it == "audio" || it == "video" }
                                ?: session.mediaType

                            val history = HomiraCallHistoryStore(
                                context = appContext,
                                ownerKey = localUserId
                            )
                            history.recordRinging(
                                id = callId,
                                peerUserId = peerUserId,
                                peerName = peerName,
                                peerNumber = peerProfile?.phoneE164.orEmpty(),
                                direction = if (incoming) {
                                    HomiraCallHistoryStore.DIRECTION_INCOMING
                                } else {
                                    HomiraCallHistoryStore.DIRECTION_OUTGOING
                                },
                                mediaType = mediaType
                            )

                            val historyOutcome = when (finalSession.state) {
                                "missed" ->
                                    HomiraCallHistoryStore.OUTCOME_MISSED

                                "declined" ->
                                    HomiraCallHistoryStore.OUTCOME_DECLINED

                                "cancelled" ->
                                    if (incoming) {
                                        HomiraCallHistoryStore.OUTCOME_MISSED
                                    } else {
                                        HomiraCallHistoryStore.OUTCOME_CANCELLED
                                    }

                                "failed" ->
                                    HomiraCallHistoryStore.OUTCOME_FAILED

                                "ended" ->
                                    HomiraCallHistoryStore.OUTCOME_ANSWERED

                                else -> null
                            }

                            if (historyOutcome != null) {
                                history.markTerminal(
                                    id = callId,
                                    outcome = historyOutcome
                                )
                            }
                        }
                    }
                }

                if (!keepOngoingNotification) {
                    HomiraIncomingCallNotifier(appContext).cancel(callId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
