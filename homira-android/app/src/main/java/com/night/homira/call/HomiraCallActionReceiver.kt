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
        if (intent.action != HomiraIncomingCallNotifier.ACTION_DECLINE) return

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
                    runCatching {
                        repository.setCallState(callId, "declined")
                    }
                }
                HomiraIncomingCallNotifier(appContext).cancel(callId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
