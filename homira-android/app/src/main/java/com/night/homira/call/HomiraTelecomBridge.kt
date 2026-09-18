package com.night.homira.call

import android.content.Context
import android.net.Uri
import android.telecom.DisconnectCause
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

sealed interface HomiraTelecomPlatformEvent {
    data class AnswerRequested(val requestedCallType: Int) :
        HomiraTelecomPlatformEvent

    data class DisconnectRequested(val cause: DisconnectCause) :
        HomiraTelecomPlatformEvent

    data object ActivateRequested : HomiraTelecomPlatformEvent
    data object InactivateRequested : HomiraTelecomPlatformEvent
}

private sealed interface HomiraTelecomAction {
    data class Answer(val video: Boolean) : HomiraTelecomAction
    data object SetActive : HomiraTelecomAction
    data class Disconnect(val cause: Int) : HomiraTelecomAction
}

class HomiraTelecomBridge(
    context: Context
) {
    private val callsManager = CallsManager(context.applicationContext).apply {
        registerAppWithTelecom(
            CallsManager.CAPABILITY_BASELINE or
                CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING
        )
    }

    private val _platformEvents =
        MutableSharedFlow<HomiraTelecomPlatformEvent>(
            extraBufferCapacity = 8
        )
    val platformEvents: Flow<HomiraTelecomPlatformEvent> =
        _platformEvents.asSharedFlow()

    @Volatile
    private var actions: Channel<HomiraTelecomAction>? = null

    suspend fun registerCall(
        callId: String,
        peerName: String,
        peerAddress: String,
        incoming: Boolean,
        video: Boolean
    ) {
        check(actions == null) {
            "Homira currently supports one Telecom call at a time"
        }

        val callActions = Channel<HomiraTelecomAction>(Channel.BUFFERED)
        actions = callActions

        val attributes = CallAttributesCompat(
            displayName = peerName,
            address = Uri.parse(
                "homira:${Uri.encode(peerAddress.ifBlank { callId })}"
            ),
            isLogExcluded = true,
            direction = if (incoming) {
                CallAttributesCompat.DIRECTION_INCOMING
            } else {
                CallAttributesCompat.DIRECTION_OUTGOING
            },
            callType = if (video) {
                CallAttributesCompat.CALL_TYPE_VIDEO_CALL
            } else {
                CallAttributesCompat.CALL_TYPE_AUDIO_CALL
            },
            callCapabilities = 0
        )

        try {
            callsManager.addCall(
                attributes,
                onAnswerCall = { requestedType ->
                    _platformEvents.emit(
                        HomiraTelecomPlatformEvent.AnswerRequested(
                            requestedType
                        )
                    )
                },
                onSetCallDisconnected = { cause ->
                    _platformEvents.emit(
                        HomiraTelecomPlatformEvent.DisconnectRequested(cause)
                    )
                },
                onSetCallActive = {
                    _platformEvents.emit(
                        HomiraTelecomPlatformEvent.ActivateRequested
                    )
                },
                onSetCallInactive = {
                    _platformEvents.emit(
                        HomiraTelecomPlatformEvent.InactivateRequested
                    )
                }
            ) {
                launch {
                    processActions(callActions.consumeAsFlow())
                }
            }
        } finally {
            actions = null
            callActions.close()
        }
    }

    suspend fun answer(video: Boolean): Boolean =
        send(HomiraTelecomAction.Answer(video))

    suspend fun markActive(): Boolean =
        send(HomiraTelecomAction.SetActive)

    suspend fun disconnect(
        cause: Int = DisconnectCause.LOCAL
    ): Boolean = send(HomiraTelecomAction.Disconnect(cause))

    private suspend fun send(action: HomiraTelecomAction): Boolean {
        val channel = actions ?: return false
        channel.send(action)
        return true
    }

    private suspend fun androidx.core.telecom.CallControlScope.processActions(
        actionFlow: Flow<HomiraTelecomAction>
    ) {
        actionFlow.collect { action ->
            when (action) {
                is HomiraTelecomAction.Answer -> {
                    answer(
                        if (action.video) {
                            CallAttributesCompat.CALL_TYPE_VIDEO_CALL
                        } else {
                            CallAttributesCompat.CALL_TYPE_AUDIO_CALL
                        }
                    )
                }

                HomiraTelecomAction.SetActive -> {
                    setActive()
                }

                is HomiraTelecomAction.Disconnect -> {
                    disconnect(DisconnectCause(action.cause))
                }
            }
        }
    }
}
