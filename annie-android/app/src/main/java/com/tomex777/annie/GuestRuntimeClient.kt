package com.tomex777.annie

import android.content.*
import android.os.*
import android.os.Process
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal object GuestRuntimeClient {
    private val lock = Mutex()

    suspend fun run(context: Context, language: String, path: String, source: String, function: String, input: String): String =
        lock.withLock { withContext(Dispatchers.Main) {
            require(source.length <= 256_000 && input.length <= 64_000) { "Interpreter input exceeds its limit" }
            val ready = CompletableDeferred<Messenger>()
            val result = CompletableDeferred<String>()
            var workerPid = 0
            val receiver = Messenger(object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(message: Message) {
                    if (message.what == 0) workerPid = message.arg1
                    else {
                        val error = message.data.getString("error")
                        if (error != null) result.completeExceptionally(IllegalStateException(error))
                        else result.complete(message.data.getString("output") ?: "null")
                    }
                }
            })
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) { ready.complete(Messenger(service)) }
                override fun onServiceDisconnected(name: ComponentName) {
                    result.completeExceptionally(IllegalStateException("Interpreter worker stopped"))
                }
                override fun onNullBinding(name: ComponentName) { ready.completeExceptionally(IllegalStateException("Interpreter unavailable")) }
            }
            val intent = Intent(context, GuestRuntimeService::class.java)
            val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            check(bound) { "Interpreter unavailable" }
            try {
                withTimeout(20_000L) {
                    val worker = ready.await()
                    worker.send(Message.obtain(null, 0).apply { replyTo = receiver })
                    worker.send(Message.obtain(null, 1).apply {
                        replyTo = receiver
                        data = Bundle().apply {
                            putString("language", language); putString("path", path); putString("source", source)
                            putString("function", function); putString("input", input)
                        }
                    })
                    result.await()
                }
            } finally {
                // Destroy all guest state after success, failure, cancellation or timeout.
                context.unbindService(connection)
                if (workerPid > 0 && workerPid != Process.myPid()) {
                    Process.killProcess(workerPid)
                    withContext(NonCancellable) {
                        repeat(20) {
                            if (!java.io.File("/proc/$workerPid").exists()) return@withContext
                            delay(25L)
                        }
                    }
                }
            }
        } }
}
