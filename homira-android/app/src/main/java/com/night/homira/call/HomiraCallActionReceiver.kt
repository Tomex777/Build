package com.night.homira.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
            action != HomiraIncomingCallNotifier.ACTION_HANG_UP
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

                if (repository.isSignedIn()) {
                    val session = repository.loadCallSessionById(callId)
                    val localUserId = repository.currentUserId()

                    if (session != null && localUserId != null) {
                        val targetState = when (action) {
                            HomiraIncomingCallNotifier.ACTION_DECLINE ->
                                if (
                                    session.state == "ringing" ||
                                    session.state == "connecting"
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

                            else -> null
                        }

                        if (targetState != null) {
                            runCatching {
                                repository.setCallState(
                                    callId,
                                    targetState
                                )
                            }
                        }
                    }
                }

                HomiraIncomingCallNotifier(appContext).cancel(callId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
