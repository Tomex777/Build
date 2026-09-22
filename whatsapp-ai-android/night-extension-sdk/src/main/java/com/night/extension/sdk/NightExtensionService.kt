package com.night.extension.sdk

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.json.JSONObject

abstract class NightExtensionService : Service() {
    private val executor: ExecutorService =
        Executors.newCachedThreadPool()

    private val incoming =
        Messenger(
            Handler(Looper.getMainLooper()) { message ->
                when (message.what) {
                    NightExtensionProtocol.MSG_DESCRIBE,
                    NightExtensionProtocol.MSG_EXECUTE_TOOL,
                    NightExtensionProtocol.MSG_EXECUTE_ACTION -> {
                        handle(message)
                        true
                    }
                    else -> false
                }
            }
        )

    final override fun onBind(intent: Intent?): IBinder? {
        if (
            intent?.action != null &&
            intent.action != NightExtensionProtocol.ACTION_EXTENSION_SERVICE
        ) {
            return null
        }
        return incoming.binder
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    protected abstract fun descriptor(): JSONObject

    protected open fun executeTool(
        request: NightToolRequest,
    ): JSONObject =
        error("Unknown Night extension tool: ${request.toolName}")

    protected open fun executeAction(
        request: NightActionRequest,
    ): JSONObject =
        error("Unknown Night extension action: ${request.actionId}")

    private fun handle(message: Message) {
        val replyTo = message.replyTo ?: return
        val data = message.data ?: Bundle.EMPTY
        val requestId =
            data.getString(NightExtensionProtocol.KEY_REQUEST_ID).orEmpty()

        executor.execute {
            runCatching {
                when (message.what) {
                    NightExtensionProtocol.MSG_DESCRIBE ->
                        descriptor()

                    NightExtensionProtocol.MSG_EXECUTE_TOOL ->
                        executeTool(
                            NightToolRequest(
                                extensionId =
                                    data.getString(
                                        NightExtensionProtocol.KEY_EXTENSION_ID
                                    ).orEmpty(),
                                chatId =
                                    data.getString(
                                        NightExtensionProtocol.KEY_CHAT_ID
                                    ).orEmpty(),
                                toolName =
                                    data.getString(
                                        NightExtensionProtocol.KEY_TOOL_NAME
                                    ).orEmpty(),
                                argumentsJson =
                                    data.getString(
                                        NightExtensionProtocol.KEY_ARGUMENTS_JSON
                                    ).orEmpty(),
                            )
                        )

                    NightExtensionProtocol.MSG_EXECUTE_ACTION ->
                        executeAction(
                            NightActionRequest(
                                extensionId =
                                    data.getString(
                                        NightExtensionProtocol.KEY_EXTENSION_ID
                                    ).orEmpty(),
                                chatId =
                                    data.getString(
                                        NightExtensionProtocol.KEY_CHAT_ID
                                    ).orEmpty(),
                                messageId =
                                    data.getString(
                                        NightExtensionProtocol.KEY_MESSAGE_ID
                                    ).orEmpty(),
                                messageType =
                                    data.getString(
                                        NightExtensionProtocol.KEY_MESSAGE_TYPE
                                    ).orEmpty(),
                                actionId =
                                    data.getString(
                                        NightExtensionProtocol.KEY_ACTION_ID
                                    ).orEmpty(),
                                payloadJson =
                                    data.getString(
                                        NightExtensionProtocol.KEY_PAYLOAD_JSON
                                    ).orEmpty(),
                            )
                        )

                    else -> JSONObject()
                }
            }.onSuccess { result ->
                sendReply(
                    replyTo = replyTo,
                    requestId = requestId,
                    ok = true,
                    resultJson = result.toString(),
                    error = null,
                )
            }.onFailure { error ->
                sendReply(
                    replyTo = replyTo,
                    requestId = requestId,
                    ok = false,
                    resultJson = "{}",
                    error =
                        error.message
                            ?.take(800)
                            ?.takeIf { it.isNotBlank() }
                            ?: "Night extension request failed.",
                )
            }
        }
    }

    private fun sendReply(
        replyTo: Messenger,
        requestId: String,
        ok: Boolean,
        resultJson: String,
        error: String?,
    ) {
        val reply =
            Message.obtain(
                null,
                NightExtensionProtocol.MSG_RESULT,
            ).apply {
                data =
                    Bundle().apply {
                        putString(
                            NightExtensionProtocol.KEY_REQUEST_ID,
                            requestId,
                        )
                        putBoolean(
                            NightExtensionProtocol.KEY_OK,
                            ok,
                        )
                        putString(
                            NightExtensionProtocol.KEY_RESULT_JSON,
                            resultJson,
                        )
                        error?.let {
                            putString(
                                NightExtensionProtocol.KEY_ERROR,
                                it,
                            )
                        }
                    }
            }

        runCatching {
            replyTo.send(reply)
        }
    }
}
