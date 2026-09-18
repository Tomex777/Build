package com.night.homira.call

import android.content.Context
import android.net.Uri
import android.telecom.DisconnectCause
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface HomiraTelecomPlatformEvent {
    data class AnswerRequested(val requestedCallType: Int) :
        HomiraTelecomPlatformEvent

    data class DisconnectRequested(val cause: DisconnectCause) :
        HomiraTelecomPlatformEvent

    data object ActivateRequested : HomiraTelecomPlatformEvent
    data object InactivateRequested : HomiraTelecomPlatformEvent
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

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    @Volatile
    private var controlScope: CallControlScope? = null

    suspend fun registerCall(
        callId: String,
        peerName: String,
        peerAddress: String,
        incoming: Boolean,
        video: Boolean
    ) {
        check(controlScope == null) {
            "Homira currently supports one Telecom call at a time"
        }

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

        _ready.value = false

        try {
            callsManager.addCall(
                callAttributes = attributes,
                onAnswer = { requestedType ->
                    _platformEvents.emit(
                        HomiraTelecomPlatformEvent.AnswerRequested(
                            requestedType
                        )
                    )
                },
                onDisconnect = { cause ->
                    _platformEvents.emit(
                        HomiraTelecomPlatformEvent.DisconnectRequested(cause)
                    )
                },
                onSetActive = {
                    _platformEvents.emit(
                        HomiraTelecomPlatformEvent.ActivateRequested
                    )
                },
                onSetInactive = {
                    _platformEvents.emit(
                        HomiraTelecomPlatformEvent.InactivateRequested
                    )
                }
            ) {
                controlScope = this
                _ready.value = true
            }
        } finally {
            controlScope = null
            _ready.value = false
        }
    }

    suspend fun answer(video: Boolean): Boolean {
        val scope = controlScope ?: return false
        return scope.answer(
            if (video) {
                CallAttributesCompat.CALL_TYPE_VIDEO_CALL
            } else {
                CallAttributesCompat.CALL_TYPE_AUDIO_CALL
            }
        ) is CallControlResult.Success
    }

    suspend fun markActive(): Boolean {
        val scope = controlScope ?: return false
        return scope.setActive() is CallControlResult.Success
    }

    suspend fun disconnect(
        cause: Int = DisconnectCause.LOCAL
    ): Boolean {
        val scope = controlScope ?: return false
        return scope.disconnect(
            DisconnectCause(cause)
        ) is CallControlResult.Success
    }
}
