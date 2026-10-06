package com.tomex777.annie

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import java.util.UUID

/** Public package-scoped download API over Annie's existing native transfer service. */
internal object AnnieDownloadsApi {
    private const val RATE_PREFS = "annie_download_api_rate_v1"
    private const val RATE_WINDOW_MS = 60_000L
    private const val RATE_LIMIT = 12
    private const val MAX_CONCURRENT = 3

    fun start(
        context: Context,
        project: ScriptProject,
        chatId: String,
        request: JSONObject,
    ): JSONObject {
        require(project.hasPackageManifest) {
            "Only imported packages can use downloads APIs"
        }

        val url = request.optString("url").trim()
        requireHttpUrl(url)

        val browserSessionId = request.optString("browserSession").trim().takeIf(String::isNotBlank)
        if (browserSessionId != null) {
            val session = AnnieBrowserSessionStore.get(context, browserSessionId)
                ?: throw AnnieError(
                    AnnieErrorCode.NOT_FOUND,
                    "Unknown Annie browser session: $browserSessionId",
                    "downloads.start",
                )
            require(AnnieBrowserSessionStore.allows(session, url)) {
                throw AnnieError(
                    AnnieErrorCode.HOST_NOT_ALLOWED,
                    "Download URL is outside this browser session's allowed sites",
                    "downloads.start",
                )
            }
        }

        checkRate(context, project.id)
        val active = DownloadStore.read(context).count {
            it.ownerScriptId == project.id && it.state in setOf(
                DownloadState.QUEUED,
                DownloadState.DOWNLOADING,
                DownloadState.WAITING_FOR_CONNECTION,
            )
        }
        if (active >= MAX_CONCURRENT) {
            throw AnnieError(
                AnnieErrorCode.RESOURCE_LIMIT,
                "This package already has $MAX_CONCURRENT active downloads",
                "downloads.start",
            )
        }

        val title = request.optString("title").trim().takeIf(String::isNotBlank)
            ?: DownloadFileMetadata.filename(null, null, url, null)

        val headers = request.optJSONObject("headers") ?: JSONObject()
        val completion = request.optJSONObject("completionAction")
        val completionAction = completion?.optString("action")?.trim()
            ?.takeIf { !it.isNullOrBlank() }

        if (completionAction != null) {
            require(completionAction.length <= 128) { "Completion action is too long" }
        }

        val payload = completion?.opt("payload")?.let { value ->
            when (value) {
                JSONObject.NULL -> "{}"
                is JSONObject, is JSONArray -> value.toString()
                else -> JSONObject().put("value", value).toString()
            }
        } ?: "{}"
        require(payload.length <= 32_000) { "Completion action payload is too large" }

        val id = "script-download-" + UUID.randomUUID().toString().replace("-", "")
        val item = DownloadItem(
            id = id,
            canonicalTitleId = "file:$id",
            sourceId = project.manifest.packageId,
            sourceName = project.manifest.displayName,
            kind = DownloadMediaKind.FILE,
            title = title,
            unitTitle = title,
            state = DownloadState.QUEUED,
            sourceUrl = url,
            headersJson = headers.toString(),
            browserSessionId = browserSessionId,
            ownerScriptId = project.id,
            completionActionJson = completionAction?.let {
                JSONObject()
                    .put("action", it)
                    .put("payload", JSONTokener(payload).nextValue())
                    .toString()
            } ?: "",
            completionChatId = chatId.takeIf(String::isNotBlank),
        )

        DownloadTransferService.enqueue(context, item)
        return JSONObject().put("id", id)
    }

    fun status(context: Context, projectId: String, id: String): JSONObject {
        val item = owned(context, projectId, id)
            ?: throw AnnieError(
                AnnieErrorCode.NOT_FOUND,
                "Download not found: $id",
                "downloads.status",
            )
        return JSONObject()
            .put("id", item.id)
            .put("state", item.state.name.lowercase())
            .put("progress", item.progress.toDouble())
            .put("bytes", item.bytesDone)
            .put("total", if (item.bytesTotal > 0L) item.bytesTotal else JSONObject.NULL)
            .put("error", item.failureReason.takeIf(String::isNotBlank) ?: JSONObject.NULL)
    }

    fun list(context: Context, projectId: String): JSONArray = JSONArray().apply {
        DownloadStore.read(context)
            .filter { it.ownerScriptId == projectId }
            .forEach { put(status(context, projectId, it.id)) }
    }

    fun cancel(context: Context, projectId: String, id: String) =
        mutateOwned(context, projectId, id) { DownloadTransferService.remove(context, it) }

    fun pause(context: Context, projectId: String, id: String) =
        mutateOwned(context, projectId, id) { DownloadTransferService.pause(context, it) }

    fun resume(context: Context, projectId: String, id: String) =
        mutateOwned(context, projectId, id) { DownloadTransferService.resume(context, it) }

    private fun owned(context: Context, projectId: String, id: String): DownloadItem? =
        DownloadStore.find(context, id)?.takeIf { it.ownerScriptId == projectId }

    private fun mutateOwned(
        context: Context,
        projectId: String,
        id: String,
        action: (DownloadItem) -> Unit,
    ) {
        val item = owned(context, projectId, id)
            ?: throw AnnieError(
                AnnieErrorCode.NOT_FOUND,
                "Download not found: $id",
                "downloads.control",
            )
        action(item)
    }

    private fun checkRate(context: Context, projectId: String) {
        val prefs = context.getSharedPreferences(RATE_PREFS, Context.MODE_PRIVATE)
        val key = projectId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val raw = prefs.getString(key, "[]") ?: "[]"
        val now = System.currentTimeMillis()
        val recent = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optLong(index).takeIf { it > now - RATE_WINDOW_MS }?.let(::add)
                }
            }
        }.getOrDefault(emptyList())

        if (recent.size >= RATE_LIMIT) {
            val retryAfter = (recent.minOrNull()!! + RATE_WINDOW_MS - now).coerceAtLeast(1L)
            throw AnnieError(
                AnnieErrorCode.RATE_LIMITED,
                "Download start rate limit exceeded",
                "downloads.start",
                retryable = true,
                retryAfterMs = retryAfter,
            )
        }

        prefs.edit().putString(key, JSONArray(recent + now).toString()).apply()
    }

    private fun requireHttpUrl(raw: String) {
        val uri = runCatching { URI(raw) }.getOrNull()
        require(
            uri != null &&
                (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) &&
                !uri.host.isNullOrBlank() &&
                uri.rawUserInfo.isNullOrBlank()
        ) {
            throw AnnieError(
                AnnieErrorCode.INVALID_ARGUMENT,
                "Only safe HTTP and HTTPS URLs are allowed",
                "downloads.start",
            )
        }
    }
}

internal class PackageDownloadOperationProvider(
    private val context: Context,
) : OperationProvider {
    override val id: String = "downloads"
    override val version: String = "1"

    override val operations = listOf(
        op(
            "start",
            capabilities = setOf(DOWNLOADS_CAPABILITY, NETWORK_ACCESS_CAPABILITY),
            permissions = listOf(DOWNLOADS_START_PERMISSION, NETWORK_ACCESS_PERMISSION),
            input = schema(
                "url" to OperationProperty("string", required = true, maxLength = 8192),
                "title" to OperationProperty("string", maxLength = 240),
                "browserSession" to OperationProperty("string", maxLength = 128),
                "headers" to OperationProperty("object"),
                "completionAction" to OperationProperty("object"),
            ),
        ),
        op(
            "status",
            permissions = listOf(DOWNLOADS_CONTROL_PERMISSION),
            input = schema("id" to OperationProperty("string", required = true, maxLength = 128)),
        ),
        op("list", permissions = listOf(DOWNLOADS_CONTROL_PERMISSION), input = schema()),
        op(
            "cancel",
            permissions = listOf(DOWNLOADS_CONTROL_PERMISSION),
            input = schema("id" to OperationProperty("string", required = true, maxLength = 128)),
        ),
        op(
            "pause",
            permissions = listOf(DOWNLOADS_CONTROL_PERMISSION),
            input = schema("id" to OperationProperty("string", required = true, maxLength = 128)),
        ),
        op(
            "resume",
            permissions = listOf(DOWNLOADS_CONTROL_PERMISSION),
            input = schema("id" to OperationProperty("string", required = true, maxLength = 128)),
        ),
    )

    override suspend fun invoke(
        operation: OperationDefinition,
        invocation: OperationInvocation,
        input: JSONObject,
    ): JSONObject {
        val projectId = invocation.projectId ?: throw AnnieError(
            AnnieErrorCode.NOT_A_PACKAGE,
            "Package operation has no project identity",
            operation.id,
        )

        return when (operation.id) {
            "downloads.start" -> {
                val headers = input.optJSONObject("headers")
                if (headers != null) {
                    val keys = headers.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        require(key.isNotBlank() && key.length <= 128) { "Download header names must be non-empty and <= 128 characters" }
                        val value = headers.opt(key)
                        require(value is String && value.length <= 4096) { "Download header '$key' must be a string <= 4096 characters" }
                    }
                }
                val completion = input.optJSONObject("completionAction")
                if (completion != null) {
                    val actionName = completion.optString("action").trim()
                    require(actionName.matches(Regex("[A-Za-z][A-Za-z0-9_.:-]{0,127}"))) {
                        "Completion action must be a valid package action name"
                    }
                }
                val project = ScriptFiles(context).listProjects().firstOrNull { it.id == projectId }
                    ?: throw AnnieError(
                        AnnieErrorCode.NOT_FOUND,
                        "Package project could not be loaded",
                        operation.id,
                    )
                AnnieDownloadsApi.start(context, project, invocation.chatId.orEmpty(), input)
            }
            "downloads.status" -> AnnieDownloadsApi.status(
                context, projectId, input.optString("id").trim(),
            )
            "downloads.list" -> JSONObject().put(
                "items", AnnieDownloadsApi.list(context, projectId),
            )
            "downloads.cancel" -> {
                AnnieDownloadsApi.cancel(context, projectId, input.optString("id").trim())
                JSONObject().put("ok", true)
            }
            "downloads.pause" -> {
                AnnieDownloadsApi.pause(context, projectId, input.optString("id").trim())
                JSONObject().put("ok", true)
            }
            "downloads.resume" -> {
                AnnieDownloadsApi.resume(context, projectId, input.optString("id").trim())
                JSONObject().put("ok", true)
            }
            else -> throw AnnieError(
                AnnieErrorCode.UNSUPPORTED,
                "Operation is not available: ${operation.id}".replace("$", "${'$'}"),
                operation.id,
            )
        }
    }

    private fun op(
        name: String,
        capabilities: Set<String> = setOf(DOWNLOADS_CAPABILITY),
        permissions: List<String>,
        input: OperationInputSchema,
    ) = OperationDefinition(
        id = "downloads.$name",
        namespace = "downloads",
        name = name,
        capability = capabilities.first(),
        capabilities = capabilities,
        permissions = permissions,
        provider = id,
        since = 1,
        input = input,
        errors = setOf(
            AnnieErrorCode.NOT_A_PACKAGE,
            AnnieErrorCode.NOT_DECLARED,
            AnnieErrorCode.NOT_GRANTED,
            AnnieErrorCode.INVALID_ARGUMENT,
            AnnieErrorCode.RESOURCE_LIMIT,
            AnnieErrorCode.HOST_NOT_ALLOWED,
            AnnieErrorCode.RATE_LIMITED,
            AnnieErrorCode.NOT_FOUND,
            AnnieErrorCode.TIMEOUT,
            AnnieErrorCode.NETWORK_ERROR,
            AnnieErrorCode.INTERNAL,
        ),
    )

    private fun schema(vararg properties: Pair<String, OperationProperty>) =
        OperationInputSchema(linkedMapOf(*properties), additionalProperties = false)
}
internal object DownloadCompletionDispatcher {
    private const val WORK_PREFIX = "annie-download-completion:"
    const val KEY_DOWNLOAD_ID = "download_id"

    fun enqueue(context: Context, item: DownloadItem) {
        if (
            item.completionActionJson.isBlank() ||
            item.ownerScriptId.isNullOrBlank() ||
            item.completionChatId.isNullOrBlank()
        ) return

        val request = OneTimeWorkRequestBuilder<DownloadCompletionWorker>()
            .setInputData(workDataOf(KEY_DOWNLOAD_ID to item.id))
            .addTag(WORK_PREFIX + item.id)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WORK_PREFIX + item.id,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}

internal class DownloadCompletionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(DownloadCompletionDispatcher.KEY_DOWNLOAD_ID).orEmpty()
        if (id.isBlank()) return@withContext Result.failure()

        val item = DownloadStore.find(applicationContext, id)
            ?: return@withContext Result.success()
        if (item.state != DownloadState.COMPLETE || item.completionActionJson.isBlank()) {
            return@withContext Result.success()
        }

        val scriptId = item.ownerScriptId ?: return@withContext Result.success()
        val chatId = item.completionChatId ?: return@withContext Result.success()
        val completion = runCatching {
            JSONObject(item.completionActionJson)
        }.getOrElse {
            return@withContext Result.failure()
        }
        val action = completion.optString("action").trim()
        if (action.isBlank()) return@withContext Result.success()

        val payloadValue = completion.opt("payload")
        val payload = when (payloadValue) {
            null, JSONObject.NULL -> "{}"
            is JSONObject, is JSONArray -> payloadValue.toString()
            else -> JSONObject().put("value", payloadValue).toString()
        }

        val workspace = ScriptWorkspace(applicationContext)
        try {
            workspace.reload()
            val dispatch = workspace.executeAction(
                scriptId = scriptId,
                actionId = action,
                payloadJson = payload,
                chatId = chatId,
                messageId = System.nanoTime(),
            )
            if (dispatch != null) {
                ChatHistoryStore.appendScriptResult(
                    context = applicationContext,
                    chatId = chatId,
                    resultJson = dispatch.resultJson,
                    scriptId = dispatch.scriptId,
                    channel = "download:$id",
                )
                DownloadStore.update(
                    applicationContext,
                    item.copy(completionActionJson = "", completionChatId = null),
                )
            }
            Result.success()
        } catch (_: Throwable) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            workspace.close()
        }
    }
}
