package com.night.homira

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.night.homira.call.HomiraIncomingCallNotifier
import com.night.homira.ui.HomiraAuthGate

class MainActivity : ComponentActivity() {
    private val requestedCallId = mutableStateOf<String?>(null)
    private val requestedAnswerCall = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedCallId.value = intent?.getStringExtra(
            HomiraIncomingCallNotifier.EXTRA_CALL_ID
        )
        requestedAnswerCall.value = intent?.getBooleanExtra(
            HomiraIncomingCallNotifier.EXTRA_ANSWER_CALL,
            false
        ) ?: false

        enableEdgeToEdge()
        setContent {
            HomiraAuthGate(
                requestedCallId = requestedCallId.value,
                requestedAnswerCall = requestedAnswerCall.value
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedCallId.value = intent.getStringExtra(
            HomiraIncomingCallNotifier.EXTRA_CALL_ID
        )
        requestedAnswerCall.value = intent.getBooleanExtra(
            HomiraIncomingCallNotifier.EXTRA_ANSWER_CALL,
            false
        )
    }
}
