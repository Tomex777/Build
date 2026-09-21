package com.example.whatsapp.data.night

import android.content.Context
import android.graphics.BitmapFactory
import com.example.whatsapp.extensions.tools.NightExtensionToolRegistry
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class NightToolInvocation(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

class NightAgentToolExecutor private constructor(
    private val context: Context,
    private val repository: NightRepository,
    private val files: NightFileContextService,
    private val libraryStore: NightLibraryStore,
    private val web: NightWebToolService,
    private val scheduler: NightScheduleManager,
    private val appearance: NightAppearanceController,
    private val images: NightImageGenerationService,
) {
    suspend fun execute(
        chatId: String,
        invocation: NightToolInvocation,
    ): String = runCatching {
        val rawArguments = invocation.argumentsJson.ifBlank { "{}" }
        val args = runCatching { JSONObject(rawArguments) }
            .getOrElse {
                error("The AI produced invalid JSON arguments for " + invocation.name + ".")
            }

        when (invocation.name) {
            "get_current_time" -> currentTime()
            "list_library" -> listLibrary(args)
            "read_library_file" -> readLibraryFile(args)
            "save_library_text" -> saveLibraryText(chatId, args)
            "web_search" -> searchWeb(args)
            "fetch_web_page" -> fetchWebPage(args)
            "schedule_task" -> scheduleTask(chatId, args)
            "list_scheduled_tasks" -> listScheduledTasks(chatId)
            "cancel_scheduled_task" -> cancelScheduledTask(chatId, args)
            "set_appearance" -> setAppearance(args)
            "create_options" -> createOptions(chatId, args)
            "generate_image" -> generateImage(chatId, args)
            else -> {
                val ownerExtensionId =
                    NightExtensionToolRegistry.extensionIdFor(invocation.name)
                        ?: error("Unknown Night tool: " + invocation.name)
                val extensionResult = NightExtensionToolRegistry.execute(
                    qualifiedName = invocation.name,
                    chatId = chatId,
                    arguments = args,
                ) ?: error("Unknown Night tool: " + invocation.name)

                NightExtensionMessageEmitter.persistFromToolResult(
                    repository = repository,
                    chatId = chatId,
                    ownerExtensionId = ownerExtensionId,
                    result = extensionResult,
                ).toString()
            }
        }
    }.getOrElse { error ->
        JSONObject()
            .put("ok", false)
            .put("error", error.message ?: "Tool execution failed.")
            .toString()
    }

    private fun currentTime(): String {
        val now = ZonedDateTime.now()
        return JSONObject()
            .put("ok", true)
            .put("iso", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .put("epoch_ms", System.currentTimeMillis())
            .put("zone", now.zone.id)
            .toString()
    }

    private suspend fun listLibrary(args: JSONObject): String {
        val items = files.listLibrary(
            query = args.optString("query").takeIf { it.isNotBlank() },
            limit = args.optInt("limit", 20),
        )
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("mime_type", item.mimeType)
                    .put("size_bytes", item.sizeBytes)
                    .put("created_at", item.createdAt)
            )
        }
        return JSONObject()
            .put("ok", true)
            .put("files", array)
            .toString()
    }

    private suspend fun readLibraryFile(args: JSONObject): String {
        val result = files.read(
            id = args.optString("id").takeIf { it.isNotBlank() },
            name = args.optString("name").takeIf { it.isNotBlank() },
            query = args.optString("query").takeIf { it.isNotBlank() },
            maxChars = args.optInt("max_chars", 18_000),
        ).getOrThrow()

        return JSONObject()
            .put("ok", true)
            .put("id", result.item.id)
            .put("name", result.item.name)
            .put("mime_type", result.item.mimeType)
            .put("truncated", result.truncated)
            .put("text", result.text)
            .toString()
    }

    private suspend fun saveLibraryText(
        chatId: String,
        args: JSONObject,
    ): String {
        val name = args.optString("name").trim()
        val text = args.optString("text")
        val format = args.optString("format", "markdown").trim().lowercase()
        require(format == "markdown" || format == "text") {
            "format must be markdown or text."
        }

        val messageId = UUID.randomUUID().toString()
        val item = libraryStore.saveText(
            name = name,
            text = text,
            markdown = format == "markdown",
            sourceChatId = chatId,
            sourceMessageId = messageId,
        ).getOrThrow()

        val message = NightMessageEntity(
            id = messageId,
            chatId = chatId,
            role = "assistant",
            type = "file",
            text = item.name,
            createdAt = System.currentTimeMillis(),
            libraryFileId = item.id,
            payloadJson = JSONObject()
                .put("localPath", item.localPath)
                .put("mimeType", item.mimeType)
                .put("sizeBytes", item.sizeBytes)
                .put("displayName", item.name)
                .toString(),
        )
        repository.appendMessage(message)

        return JSONObject()
            .put("ok", true)
            .put("message_id", message.id)
            .put("library_id", item.id)
            .put("name", item.name)
            .put("mime_type", item.mimeType)
            .put("size_bytes", item.sizeBytes)
            .toString()
    }

    private suspend fun searchWeb(args: JSONObject): String {
        val query = args.optString("query").trim()
        require(query.isNotBlank()) { "query is required." }
        val results = web.search(
            query = query,
            maxResults = args.optInt("max_results", 6),
        ).getOrThrow()

        val array = JSONArray()
        results.forEach {
            array.put(
                JSONObject()
                    .put("title", it.title)
                    .put("url", it.url)
                    .put("snippet", it.snippet)
            )
        }
        return JSONObject()
            .put("ok", true)
            .put("query", query)
            .put("results", array)
            .toString()
    }

    private suspend fun fetchWebPage(args: JSONObject): String {
        val url = args.optString("url").trim()
        require(url.isNotBlank()) { "url is required." }
        val text = web.fetchPage(
            url = url,
            maxChars = args.optInt("max_chars", 16_000),
        ).getOrThrow()

        return JSONObject()
            .put("ok", true)
            .put("url", url)
            .put("text", text)
            .toString()
    }

    private suspend fun scheduleTask(
        chatId: String,
        args: JSONObject,
    ): String {
        val prompt = args.optString("prompt").trim()
        require(prompt.isNotBlank()) { "prompt is required." }

        val now = System.currentTimeMillis()
        val runAt = when {
            args.has("run_at_epoch_ms") && !args.isNull("run_at_epoch_ms") ->
                args.optLong("run_at_epoch_ms", 0L)
            args.has("delay_minutes") ->
                now + args.optLong("delay_minutes", 0L).coerceAtLeast(0L) * 60_000L
            else -> error("Provide run_at_epoch_ms or delay_minutes.")
        }
        require(runAt >= now - 5_000L) { "Scheduled time is already in the past." }

        val repeat = args.optLong("repeat_minutes", 0L)
            .takeIf { it > 0L }

        val task = scheduler.create(
            chatId = chatId,
            prompt = prompt,
            runAt = runAt,
            repeatMinutes = repeat,
        )

        return JSONObject()
            .put("ok", true)
            .put("task_id", task.id)
            .put("run_at_epoch_ms", task.runAt)
            .put("repeat_minutes", task.repeatMinutes)
            .put("prompt", task.prompt)
            .toString()
    }

    private suspend fun listScheduledTasks(chatId: String): String {
        val array = JSONArray()
        repository.getScheduledTasks()
            .filter { it.chatId == chatId && it.state == "scheduled" }
            .forEach { task ->
                array.put(
                    JSONObject()
                        .put("id", task.id)
                        .put("prompt", task.prompt)
                        .put("run_at_epoch_ms", task.runAt)
                        .put("repeat_minutes", task.repeatMinutes)
                        .put("state", task.state)
                )
            }

        return JSONObject()
            .put("ok", true)
            .put("tasks", array)
            .toString()
    }

    private suspend fun cancelScheduledTask(
        chatId: String,
        args: JSONObject,
    ): String {
        val taskId = args.optString("task_id").trim()
        require(taskId.isNotBlank()) { "task_id is required." }
        val task = repository.getScheduledTask(taskId)
            ?: error("Scheduled task was not found.")
        require(task.chatId == chatId) {
            "This scheduled task belongs to a different chat."
        }
        scheduler.cancel(task)
        return JSONObject()
            .put("ok", true)
            .put("task_id", taskId)
            .put("cancelled", true)
            .toString()
    }

    private suspend fun setAppearance(args: JSONObject): String {
        val instruction = args.optString("instruction").trim()
        require(instruction.isNotBlank()) { "instruction is required." }
        val result = appearance.handleNaturalRequest(instruction)
            ?: error("That appearance change is not supported yet.")

        return JSONObject()
            .put("ok", true)
            .put("result", result)
            .toString()
    }

    private suspend fun createOptions(
        chatId: String,
        args: JSONObject,
    ): String {
        val title = args.optString("title").trim()
        require(title.isNotBlank()) { "title is required." }
        val source = args.optJSONArray("options") ?: error("options are required.")
        val values = buildList {
            for (index in 0 until source.length()) {
                val value = source.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }.distinct().take(6)
        require(values.size >= 2) { "At least two options are required." }

        val payload = JSONObject().put("options", JSONArray(values))
        val message = NightMessageEntity(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            role = "assistant",
            type = "choice",
            text = title,
            createdAt = System.currentTimeMillis(),
            payloadJson = payload.toString(),
        )
        repository.appendMessage(message)

        return JSONObject()
            .put("ok", true)
            .put("message_id", message.id)
            .put("title", title)
            .put("options", JSONArray(values))
            .toString()
    }

    private suspend fun generateImage(
        chatId: String,
        args: JSONObject,
    ): String {
        val prompt = args.optString("prompt").trim()
        require(prompt.isNotBlank()) { "prompt is required." }
        val generated = images.generate(
            chatId = chatId,
            prompt = prompt,
            size = args.optString("size", "1024x1024"),
        ).getOrThrow()

        val file = File(generated.localPath)
        val id = UUID.randomUUID().toString()
        val messageId = UUID.randomUUID().toString()
        val library = NightLibraryItemEntity(
            id = id,
            name = file.name,
            mimeType = generated.mimeType,
            sizeBytes = file.length(),
            localPath = generated.localPath,
            createdAt = System.currentTimeMillis(),
            sourceChatId = chatId,
            sourceMessageId = messageId,
        )
        repository.addLibraryItem(library)

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(generated.localPath, options)
        val aspect = if (options.outHeight > 0) {
            options.outWidth.toFloat() / options.outHeight.toFloat()
        } else {
            1f
        }

        val message = NightMessageEntity(
            id = messageId,
            chatId = chatId,
            role = "assistant",
            type = "image",
            text = prompt,
            createdAt = System.currentTimeMillis(),
            libraryFileId = id,
            payloadJson = JSONObject()
                .put("localPath", generated.localPath)
                .put("mimeType", generated.mimeType)
                .put("sizeBytes", file.length())
                .put("aspectRatio", aspect)
                .toString(),
        )
        repository.appendMessage(message)

        return JSONObject()
            .put("ok", true)
            .put("message_id", message.id)
            .put("library_id", id)
            .put("local_path", generated.localPath)
            .put("mime_type", generated.mimeType)
            .put("prompt", prompt)
            .toString()
    }

    companion object {
        @Volatile private var instance: NightAgentToolExecutor? = null

        fun get(context: Context): NightAgentToolExecutor =
            instance ?: synchronized(this) {
                val app = context.applicationContext
                val repository = NightRepository.get(app)
                instance ?: NightAgentToolExecutor(
                    context = app,
                    repository = repository,
                    files = NightFileContextService.get(app),
                    libraryStore = NightLibraryStore.get(app),
                    web = NightWebToolService.get(),
                    scheduler = NightScheduleManager.get(app),
                    appearance = NightAppearanceController(repository),
                    images = NightImageGenerationService.get(app),
                ).also { instance = it }
            }
    }
}

object NightAgentToolSchemas {
    fun isSideEffect(name: String): Boolean =
        name in setOf(
            "save_library_text",
            "schedule_task",
            "cancel_scheduled_task",
            "set_appearance",
            "create_options",
            "generate_image",
        ) || name.startsWith("ext__")

    fun all(): JSONArray = JSONArray()
        .put(function(
            name = "get_current_time",
            description = "Get the device's current local date, time, timezone and epoch milliseconds.",
            properties = JSONObject(),
            required = emptyList(),
        ))
        .put(function(
            name = "list_library",
            description = "List files saved in Night Library. Use this to discover a file id before reading it.",
            properties = JSONObject()
                .put("query", string("Optional filename or MIME filter."))
                .put("limit", integer("Maximum number of files, 1 to 100.")),
            required = emptyList(),
        ))
        .put(function(
            name = "read_library_file",
            description = "Extract readable local text from a PDF, DOCX or text-like file in Night Library. Prefer id when known. query focuses extraction on relevant passages.",
            properties = JSONObject()
                .put("id", string("Night Library file id."))
                .put("name", string("Filename when id is not known."))
                .put("query", string("What information to focus on inside the document."))
                .put("max_chars", integer("Maximum extracted text characters.")),
            required = emptyList(),
        ))
        .put(function(
            name = "save_library_text",
            description = "Save plain text or Markdown as a real file in Night Library so it remains available to the user and future chats.",
            properties = JSONObject()
                .put("name", string("Filename or short title. Night adds .md or .txt when needed."))
                .put("text", string("Complete text to save."))
                .put("format", string("markdown or text. Defaults to markdown.")),
            required = listOf("name", "text"),
        ))
        .put(function(
            name = "web_search",
            description = "Search the public web for current information. Follow promising results with fetch_web_page before making detailed factual claims.",
            properties = JSONObject()
                .put("query", string("Search query."))
                .put("max_results", integer("Maximum results, 1 to 10.")),
            required = listOf("query"),
        ))
        .put(function(
            name = "fetch_web_page",
            description = "Fetch and extract readable text from a public HTTP or HTTPS webpage.",
            properties = JSONObject()
                .put("url", string("Absolute HTTP or HTTPS URL."))
                .put("max_chars", integer("Maximum extracted characters.")),
            required = listOf("url"),
        ))
        .put(function(
            name = "schedule_task",
            description = "Schedule Night to run an AI prompt later. Use get_current_time first for absolute dates. Provide either run_at_epoch_ms or delay_minutes.",
            properties = JSONObject()
                .put("prompt", string("Prompt Night should execute when the task runs."))
                .put("run_at_epoch_ms", integer("Absolute device time in Unix epoch milliseconds."))
                .put("delay_minutes", integer("Alternative relative delay in minutes."))
                .put("repeat_minutes", integer("Optional recurrence interval in minutes.")),
            required = listOf("prompt"),
        ))
        .put(function(
            name = "list_scheduled_tasks",
            description = "List active scheduled Night tasks for the current chat.",
            properties = JSONObject(),
            required = emptyList(),
        ))
        .put(function(
            name = "cancel_scheduled_task",
            description = "Cancel one active scheduled Night task. Use list_scheduled_tasks first when the task id is not known.",
            properties = JSONObject()
                .put("task_id", string("Scheduled task id to cancel.")),
            required = listOf("task_id"),
        ))
        .put(function(
            name = "set_appearance",
            description = "Change Night chat appearance: user/AI bubble color, wallpaper color, font family or font size.",
            properties = JSONObject()
                .put("instruction", string("Natural-language appearance change, e.g. 'make my bubbles purple'.")),
            required = listOf("instruction"),
        ))
        .put(function(
            name = "create_options",
            description = "Create Night's interactive single-user Options card when the user needs to choose from a compact set.",
            properties = JSONObject()
                .put("title", string("Question or choice title."))
                .put("options", arrayOfStrings("Two to six concise options.")),
            required = listOf("title", "options"),
        ))
        .put(function(
            name = "generate_image",
            description = "Generate an image and insert it into the current Night chat using the configured image-generation route.",
            properties = JSONObject()
                .put("prompt", string("Detailed image-generation prompt."))
                .put("size", string("1024x1024, 1024x1536, or 1536x1024.")),
            required = listOf("prompt"),
        ))
        .also { schemas ->
            val extensionSchemas = NightExtensionToolRegistry.schemas()
            for (index in 0 until extensionSchemas.length()) {
                schemas.put(extensionSchemas.getJSONObject(index))
            }
        }

    private fun function(
        name: String,
        description: String,
        properties: JSONObject,
        required: List<String>,
    ): JSONObject =
        JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put(
                        "parameters",
                        JSONObject()
                            .put("type", "object")
                            .put("properties", properties)
                            .put("required", JSONArray(required))
                            .put("additionalProperties", false)
                    )
            )

    private fun string(description: String): JSONObject =
        JSONObject()
            .put("type", "string")
            .put("description", description)

    private fun integer(description: String): JSONObject =
        JSONObject()
            .put("type", "integer")
            .put("description", description)

    private fun arrayOfStrings(description: String): JSONObject =
        JSONObject()
            .put("type", "array")
            .put("description", description)
            .put("items", JSONObject().put("type", "string"))
}
