package com.night.homira

import android.content.Intent
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.night.homira.call.HomiraIncomingCallNotifier
import com.night.homira.call.HomiraCallUiState
import com.night.homira.ui.HomiraAuthGate
import com.night.homira.ui.HomiraTheme

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val crashReport = remember {
                mutableStateOf(HomiraCrashReporter.peek(context))
            }

            val report = crashReport.value
            if (report == null) {
                HomiraAuthGate(
                    requestedCallId = requestedCallId.value,
                    requestedAnswerCall = requestedAnswerCall.value
                )
            } else {
                HomiraTheme {
                    AlertDialog(
                    onDismissRequest = {
                        HomiraCrashReporter.clear(context)
                        crashReport.value = null
                    },
                    title = { Text("Previous Homira crash detected") },
                    text = {
                        Text(
                            "Homira saved the technical crash details from the previous run. " +
                                "Copy them so we can identify any device-only crash exactly."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(
                                    ClipboardManager::class.java
                                )
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Homira crash report", report)
                                )
                                Toast.makeText(
                                    context,
                                    "Crash report copied",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Text("Copy details")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                HomiraCrashReporter.clear(context)
                                crashReport.value = null
                            }
                        ) {
                            Text("Dismiss")
                        }
                    }
                    )
                }
            }
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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            HomiraCallUiState.videoCallActive &&
            !isInPictureInPictureMode
        ) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(9, 16))
                .build()

            runCatching {
                enterPictureInPictureMode(params)
            }
        }
    }
}
