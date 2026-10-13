package com.tomex777.annie

import android.app.Service
import android.content.Intent
import android.os.*
import android.os.Process
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.util.concurrent.Executors

/** Computation-only interpreters live outside the UI process; no host objects cross IPC. */
internal class GuestRuntimeService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            val reply = message.replyTo ?: return
            if (message.what == 0) {
                reply.send(Message.obtain(null, 0).apply { arg1 = Process.myPid() })
                return
            }
            val request = Bundle(message.data)
            executor.execute {
                val response = Bundle()
                try {
                    val source = request.getString("source").orEmpty()
                    val filename = request.getString("path").orEmpty()
                    val function = request.getString("function").orEmpty()
                    val input = request.getString("input").orEmpty()
                    require(source.length <= 256_000 && input.length <= 64_000)
                    val output = when (request.getString("language")) {
                        "lua" -> LuaGuest.run(source, filename, function, input)
                        "python" -> {
                            if (!Python.isStarted()) Python.start(AndroidPlatform(this@GuestRuntimeService))
                            Python.getInstance().getModule("annie_guest").callAttr("run", source, filename, function, input).toString()
                        }
                        else -> error("Unsupported interpreter")
                    }
                    response.putString("output", output)
                } catch (failure: Throwable) {
                    response.putString("error", (failure.message ?: "Interpreter failed").take(1000))
                }
                runCatching { reply.send(Message.obtain(null, 1).apply { data = response }) }
            }
        }
    })
    override fun onBind(intent: Intent): IBinder = messenger.binder
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy(); Process.killProcess(Process.myPid()) }
}
