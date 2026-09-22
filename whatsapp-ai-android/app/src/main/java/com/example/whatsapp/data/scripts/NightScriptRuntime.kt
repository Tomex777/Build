package com.example.whatsapp.data.scripts

import android.content.Context
import android.graphics.BitmapFactory
import com.example.whatsapp.data.browser.NightBrowserSpec
import com.example.whatsapp.data.browser.NightBrowserSpecCodec
import com.example.whatsapp.data.night.NightAgentToolExecutor
import com.example.whatsapp.data.night.NightMessageEntity
import com.example.whatsapp.data.night.NightRepository
import com.example.whatsapp.data.night.NightToolInvocation
import com.example.whatsapp.extensions.tools.NightExtensionToolRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.ContextAction
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

data class NightScriptCommandExecution(
    val command: String,
    val found: Boolean,
    val success: Boolean,
    val response: String? = null,
)

data class NightScriptCommandSummary(
    val name: String,
    val aliases: List<String>,
    val description: String,
    val script: String,
)

class NightScriptRuntime private constructor(
    context: Context,
) {
    private val app = context.applicationContext
    private val workspace = NightScriptWorkspace.get(app)
    private val repository = NightRepository.get(app)
    private val agentTools = NightAgentToolExecutor.get(app)
    private val factory = NightSandboxContextFactory()
    private val mutex = Mutex()
    private val storageLock = Any()

    suspend fun executeSlashCommand(
        chatId: String,
        rawText: String,
        invokingMessageId: String? = null,
    ): NightScriptCommandExecution? {
        val parsed = parseSlashCommand(rawText) ?: return null

        return withContext(Dispatchers.Default) {
            mutex.withLock {
                val discovery = discoverCommands()

                if (parsed.name == "commands") {
                    val names = discovery.commands
                        .map { "/" + it.name }
                        .distinct()
                        .sorted()

                    val response =
                        if (names.isEmpty()) {
                            "No Night script commands are registered yet. Create one in Library → Scripts & projects."
                        } else {
                            "Night script commands: " + names.joinToString(", ")
                        }

                    return@withLock NightScriptCommandExecution(
                        command = parsed.name,
                        found = true,
                        success = true,
                        response = response,
                    )
                }

                val target = discovery.byToken[parsed.name]
                    ?: return@withLock NightScriptCommandExecution(
                        command = parsed.name,
                        found = false,
                        success = false,
                        response = buildString {
                            append("Unknown Night command /")
                            append(parsed.name)
                            append(".")
                            if (discovery.commands.isNotEmpty()) {
                                append(" Try /commands.")
                            }
                            if (discovery.failures.isNotEmpty()) {
                                append(" ")
                                append(discovery.failures.size)
                                append(" script")
                                if (discovery.failures.size != 1) append("s")
                                append(" could not be loaded.")
                            }
                        },
                    )

                executeRegistered(
                    definition = target,
                    parsed = parsed,
                    chatId = chatId,
                    invokingMessageId = invokingMessageId,
                )
            }
        }
    }

    suspend fun commandSummaries(): List<NightScriptCommandSummary> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                discoverCommands().commands
                    .distinctBy { it.name }
                    .sortedBy { it.name }
            }
        }

    private fun discoverCommands(): Discovery {
        val commands = mutableListOf<NightScriptCommandSummary>()
        val byToken = linkedMapOf<String, NightScriptCommandSummary>()
        val failures = mutableListOf<String>()

        workspace.listScripts().forEach { file ->
            if (file.sizeBytes > MAX_SCRIPT_BYTES) {
                failures += file.relativePath + ": script is too large to execute."
                return@forEach
            }

            val source = workspace.readFile(file)
            val definitions = mutableListOf<Registration>()

            runCatching {
                factory.runWithDeadline(SCRIPT_LOAD_TIMEOUT_MS) { cx ->
                    val scope = cx.initSafeStandardObjects()
                    val session = Session(
                        file = file,
                        scope = scope,
                        chatId = "",
                        invokingMessageId = null,
                        executing = false,
                        onRegister = { definitions += it },
                    )
                    installNightApi(cx, scope, session)
                    cx.evaluateString(
                        scope,
                        source,
                        "night://scripts/" + file.relativePath,
                        1,
                        null,
                    )
                }
            }.onFailure { error ->
                failures += file.relativePath + ": " + conciseError(error)
                return@forEach
            }

            definitions.forEach { definition ->
                val summary = NightScriptCommandSummary(
                    name = definition.name,
                    aliases = definition.aliases,
                    description = definition.description,
                    script = file.relativePath,
                )
                commands += summary
                (listOf(definition.name) + definition.aliases).forEach { token ->
                    byToken.putIfAbsent(token, summary)
                }
            }
        }

        return Discovery(
            commands = commands,
            byToken = byToken,
            failures = failures,
        )
    }

    private fun executeRegistered(
        definition: NightScriptCommandSummary,
        parsed: ParsedCommand,
        chatId: String,
        invokingMessageId: String?,
    ): NightScriptCommandExecution {
        val file = workspace.listScripts()
            .firstOrNull { it.relativePath == definition.script }
            ?: return NightScriptCommandExecution(
                command = parsed.name,
                found = true,
                success = false,
                response = "That Night script was moved or deleted. Try the command again.",
            )

        val source = workspace.readFile(file)

        return runCatching {
            factory.runWithDeadline(SCRIPT_EXECUTION_TIMEOUT_MS) { cx ->
                val scope = cx.initSafeStandardObjects()
                val registrations = mutableListOf<Registration>()
                val session = Session(
                    file = file,
                    scope = scope,
                    chatId = chatId,
                    invokingMessageId = invokingMessageId,
                    executing = false,
                    onRegister = { registrations += it },
                )
                installNightApi(cx, scope, session)
                cx.evaluateString(
                    scope,
                    source,
                    "night://scripts/" + file.relativePath,
                    1,
                    null,
                )

                val registration = registrations.firstOrNull {
                    parsed.name == it.name || parsed.name in it.aliases
                } ?: error(
                    "The command registration changed while it was being executed."
                )

                val argv = parsed.arguments
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }

                val commandContext = cx.newObject(scope)
                ScriptableObject.putProperty(
                    commandContext,
                    "command",
                    registration.name,
                )
                ScriptableObject.putProperty(
                    commandContext,
                    "args",
                    parsed.arguments,
                )
                ScriptableObject.putProperty(
                    commandContext,
                    "argv",
                    cx.newArray(scope, argv.toTypedArray()),
                )
                ScriptableObject.putProperty(
                    commandContext,
                    "text",
                    parsed.original,
                )

                session.executing = true
                val result = registration.handler.call(
                    cx,
                    scope,
                    scope,
                    arrayOf(commandContext),
                )
                session.executing = false

                if (
                    result != null &&
                    !Undefined.isUndefined(result) &&
                    result !is Scriptable
                ) {
                    val resultText = RhinoContext.toString(result).trim()
                    if (resultText.isNotBlank()) {
                        runBlocking {
                            repository.appendText(
                                chatId = chatId,
                                role = "assistant",
                                text = resultText.take(MAX_MESSAGE_CHARS),
                                replyToMessageId = invokingMessageId,
                            )
                        }
                    }
                }

                NightScriptCommandExecution(
                    command = parsed.name,
                    found = true,
                    success = true,
                )
            }
        }.getOrElse { error ->
            NightScriptCommandExecution(
                command = parsed.name,
                found = true,
                success = false,
                response = "Night script /" + parsed.name + " failed: " + conciseError(error),
            )
        }
    }

    private fun installNightApi(
        cx: RhinoContext,
        scope: ScriptableObject,
        session: Session,
    ) {
        val night = cx.newObject(scope)

        put(
            night,
            "command",
            function { _, _, _, args ->
                val registration = parseRegistration(args, session.file)
                session.onRegister(registration)
                Undefined.instance
            },
        )

        put(
            night,
            "send",
            function { currentCx, _, _, args ->
                requireExecuting(session)
                val text = stringArg(args, 0, "text").take(MAX_MESSAGE_CHARS)
                val message = runBlocking {
                    repository.appendText(
                        chatId = session.chatId,
                        role = "assistant",
                        text = text,
                    )
                }
                jsonToJs(
                    currentCx,
                    scope,
                    JSONObject()
                        .put("ok", true)
                        .put("message_id", message.id),
                )
            },
        )

        put(
            night,
            "reply",
            function { currentCx, _, _, args ->
                requireExecuting(session)
                val text = stringArg(args, 0, "text").take(MAX_MESSAGE_CHARS)
                val message = runBlocking {
                    repository.appendText(
                        chatId = session.chatId,
                        role = "assistant",
                        text = text,
                        replyToMessageId = session.invokingMessageId,
                    )
                }
                jsonToJs(
                    currentCx,
                    scope,
                    JSONObject()
                        .put("ok", true)
                        .put("message_id", message.id),
                )
            },
        )

        put(
            night,
            "tool",
            function { currentCx, _, _, args ->
                requireExecuting(session)
                val name = stringArg(args, 0, "tool name")
                val arguments = objectArg(args.getOrNull(1))
                runTool(currentCx, scope, session, name, arguments)
            },
        )

        put(
            night,
            "fetch",
            function { currentCx, _, _, args ->
                requireExecuting(session)
                val url = stringArg(args, 0, "url")
                val options = objectArg(args.getOrNull(1))
                val maxChars = options.optInt("maxChars", 16_000)
                    .coerceIn(1_000, 50_000)
                runTool(
                    currentCx,
                    scope,
                    session,
                    "fetch_web_page",
                    JSONObject()
                        .put("url", url)
                        .put("max_chars", maxChars),
                )
            },
        )

        val storage = cx.newObject(scope)
        put(
            storage,
            "get",
            function { currentCx, _, _, args ->
                requireExecuting(session)
                val key = storageKey(args, 0)
                val fallback = args.getOrNull(1)
                val stored = readStorage(session.file).opt(key)
                if (stored == null || stored == JSONObject.NULL) {
                    fallback ?: Undefined.instance
                } else {
                    jsonToJs(currentCx, scope, stored)
                }
            },
        )
        put(
            storage,
            "set",
            function { currentCx, _, _, args ->
                requireExecuting(session)
                val key = storageKey(args, 0)
                val value = jsToJson(args.getOrNull(1), 0)
                val stored = writeStorage(session.file, key, value)
                jsonToJs(currentCx, scope, stored)
            },
        )
        put(night, "storage", storage)

        val library = cx.newObject(scope)
        put(
            library,
            "list",
            function { currentCx, _, _, args ->
                requireExecuting(session)
                val raw = args.getOrNull(0)
                val request =
                    when (raw) {
                        null, is Undefined -> JSONObject()
                        is CharSequence ->
                            JSONObject().put(
                                "query",
                                raw.toString().take(160),
                            )
                        else -> objectArg(raw)
                    }
                runTool(currentCx, scope, session, "list_library", request)
            },
        )
        put(
            library,
            "read",
            function { currentCx, _, _, args ->
                requireExecuting(session)
                val raw = args.getOrNull(0)
                val request =
                    if (raw is CharSequence) {
                        JSONObject().put("id", raw.toString())
                    } else {
                        objectArg(raw)
                    }
                runTool(currentCx, scope, session, "read_library_file", request)
            },
        )
        put(
            library,
            "save",
            function { currentCx, _, _, args ->
                requireExecuting(session)
                val name = stringArg(args, 0, "name")
                val text = stringArg(args, 1, "text")
                val format = optionalString(args.getOrNull(2))
                    ?.lowercase()
                    ?.takeIf { it == "text" || it == "markdown" }
                    ?: "markdown"
                runTool(
                    currentCx,
                    scope,
                    session,
                    "save_library_text",
                    JSONObject()
                        .put("name", name)
                        .put("text", text.take(MAX_LIBRARY_TEXT_CHARS))
                        .put("format", format),
                )
            },
        )
        put(night, "library", library)

        val extension = cx.newObject(scope)
        put(
            extension,
            "call",
            function { currentCx, _, _, args ->
                requireExecuting(session)
                val extensionId = stringArg(args, 0, "extension id")
                    .trim()
                    .lowercase()
                val toolName = stringArg(args, 1, "tool name").trim()
                val qualified =
                    NightExtensionToolRegistry.qualifiedNameFor(
                        extensionId = extensionId,
                        toolName = toolName,
                    ) ?: error(
                        "No enabled Night extension tool " +
                            extensionId +
                            "." +
                            toolName +
                            " is registered."
                    )
                runTool(
                    currentCx,
                    scope,
                    session,
                    qualified,
                    objectArg(args.getOrNull(2)),
                )
            },
        )
        put(night, "extension", extension)

        val ui = cx.newObject(scope)
        put(
            ui,
            "options",
            function { currentCx, _, _, args ->
                requireExecuting(session)
                val title = stringArg(args, 0, "title")
                val options = jsToJson(args.getOrNull(1), 0) as? JSONArray
                    ?: error("options must be an array.")
                val multiple =
                    args.getOrNull(2)?.let(RhinoContext::toBoolean) ?: false
                runTool(
                    currentCx,
                    scope,
                    session,
                    "create_options",
                    JSONObject()
                        .put("title", title)
                        .put("options", options)
                        .put("multiple", multiple),
                )
            },
        )
        put(
            ui,
            "buttons",
            function { currentCx, _, _, args ->
                requireExecuting(session)
                val title = stringArg(args, 0, "title").take(120)
                val body = optionalString(args.getOrNull(1)).orEmpty().take(500)
                val source = jsToJson(args.getOrNull(2), 0) as? JSONArray
                    ?: error("actions must be an array.")
                val actions = JSONArray()
                val commands = JSONObject()

                for (index in 0 until minOf(source.length(), 6)) {
                    val raw = source.optJSONObject(index) ?: continue
                    val label = raw.optString("label").trim().take(48)
                    var command = raw.optString("command").trim().take(500)
                    if (label.isBlank() || command.isBlank()) continue
                    if (!command.startsWith("/")) command = "/" + command
                    val actionId = "script_command_" + index
                    actions.put(
                        JSONObject()
                            .put("id", actionId)
                            .put("label", label)
                    )
                    commands.put(actionId, command)
                }
                require(actions.length() > 0) {
                    "At least one button with label and command is required."
                }

                val message = runBlocking {
                    val entity = NightMessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = session.chatId,
                        role = "assistant",
                        type = "buttons",
                        text = title,
                        createdAt = System.currentTimeMillis(),
                        payloadJson = JSONObject()
                            .put("title", title)
                            .put("body", body)
                            .put("actions", actions)
                            .put("scriptCommands", commands)
                            .toString(),
                    )
                    repository.appendMessage(entity)
                    entity
                }
                jsonToJs(
                    currentCx,
                    scope,
                    JSONObject()
                        .put("ok", true)
                        .put("message_id", message.id),
                )
            },
        )
        put(
            ui,
            "browser",
            function { currentCx, _, _, args ->
                requireExecuting(session)
                val url = stringArg(args, 0, "url")
                val title = optionalString(args.getOrNull(1))
                    ?.take(120)
                    ?.ifBlank { null }
                    ?: "Browser"
                val spec = NightBrowserSpec.general(
                    initialUrl = url,
                    sessionId = "script." + UUID.randomUUID(),
                ).copy(title = title).sanitized()

                val message = runBlocking {
                    val entity = NightMessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = session.chatId,
                        role = "assistant",
                        type = "browser",
                        text = title,
                        createdAt = System.currentTimeMillis(),
                        payloadJson = JSONObject()
                            .put("browser", NightBrowserSpecCodec.encode(spec))
                            .put("sourceLabel", "Night Script")
                            .toString(),
                    )
                    repository.appendMessage(entity)
                    entity
                }
                jsonToJs(
                    currentCx,
                    scope,
                    JSONObject()
                        .put("ok", true)
                        .put("message_id", message.id),
                )
            },
        )
        put(
            ui,
            "media",
            function { currentCx, _, _, args ->
                requireExecuting(session)
                val libraryId = stringArg(args, 0, "library id")
                val caption = optionalString(args.getOrNull(1)).orEmpty()
                    .take(MAX_MESSAGE_CHARS)
                val item = runBlocking {
                    repository.getLibraryItem(libraryId)
                } ?: error("Night Library item was not found.")

                val mime = item.mimeType.lowercase()
                val type =
                    when {
                        mime.startsWith("image/") -> "image"
                        mime.startsWith("video/") -> "video"
                        mime.startsWith("audio/") -> "audio"
                        else -> "file"
                    }

                val payload = JSONObject()
                    .put("localPath", item.localPath)
                    .put("mimeType", item.mimeType)
                    .put("sizeBytes", item.sizeBytes)

                if (type == "image") {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(item.localPath, options)
                    val aspect =
                        if (options.outHeight > 0) {
                            options.outWidth.toFloat() / options.outHeight.toFloat()
                        } else {
                            1f
                        }
                    payload.put("aspectRatio", aspect)
                }

                if (caption.isNotBlank()) {
                    payload.put("caption", caption)
                }

                val message = runBlocking {
                    val entity = NightMessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = session.chatId,
                        role = "assistant",
                        type = type,
                        text =
                            if (type == "file" || type == "audio") {
                                item.name
                            } else {
                                caption
                            },
                        createdAt = System.currentTimeMillis(),
                        libraryFileId = item.id,
                        payloadJson = payload.toString(),
                    )
                    repository.appendMessage(entity)
                    entity
                }

                jsonToJs(
                    currentCx,
                    scope,
                    JSONObject()
                        .put("ok", true)
                        .put("message_id", message.id)
                        .put("library_id", item.id),
                )
            },
        )
        put(night, "ui", ui)

        ScriptableObject.putProperty(scope, "night", night)
    }

    private fun runTool(
        cx: RhinoContext,
        scope: ScriptableObject,
        session: Session,
        name: String,
        arguments: JSONObject,
    ): Any? {
        val raw = runBlocking {
            agentTools.execute(
                chatId = session.chatId,
                invocation = NightToolInvocation(
                    id = "script_" + UUID.randomUUID(),
                    name = name,
                    argumentsJson = arguments.toString(),
                ),
            )
        }
        val result = runCatching { JSONObject(raw) }
            .getOrElse {
                JSONObject()
                    .put("ok", true)
                    .put("result", raw)
            }

        if (!result.optBoolean("ok", true)) {
            error(
                result.optString("error")
                    .takeIf { it.isNotBlank() }
                    ?: "Night tool failed."
            )
        }

        return jsonToJs(cx, scope, result)
    }

    private fun parseRegistration(
        args: Array<out Any?>,
        file: NightWorkspaceFile,
    ): Registration {
        val first = args.getOrNull(0)

        val name: String
        val aliases: List<String>
        val description: String
        val handler: Function

        if (first is Scriptable && first !is Function) {
            name = propertyString(first, "name")
            aliases = propertyStringList(first, "aliases")
            description = propertyString(first, "description").take(240)
            handler = ScriptableObject.getProperty(first, "run") as? Function
                ?: error("night.command requires a run function.")
        } else {
            name = optionalString(first).orEmpty()
            aliases = emptyList()
            description = ""
            handler = args.getOrNull(1) as? Function
                ?: error("night.command(name, handler) requires a function.")
        }

        val normalizedName = normalizeCommandName(name)
        val normalizedAliases = aliases
            .map(::normalizeCommandName)
            .filter { it != normalizedName }
            .distinct()
            .take(12)

        return Registration(
            name = normalizedName,
            aliases = normalizedAliases,
            description = description,
            handler = handler,
            script = file.relativePath,
        )
    }

    private fun normalizeCommandName(raw: String): String {
        val normalized = raw.trim()
            .removePrefix("/")
            .lowercase()
        require(COMMAND_NAME.matches(normalized)) {
            "Command names must use 1-48 lowercase letters, numbers, _ or -."
        }
        return normalized
    }

    private fun parseSlashCommand(raw: String): ParsedCommand? {
        val trimmed = raw.trimStart()
        if (!trimmed.startsWith("/") || trimmed.length < 2) return null

        val body = trimmed.removePrefix("/")
        val tokenEnd = body.indexOfFirst { it.isWhitespace() }
        val rawName =
            if (tokenEnd < 0) body else body.substring(0, tokenEnd)
        val name = rawName.trim().lowercase()
        val arguments =
            if (tokenEnd < 0) "" else body.substring(tokenEnd).trim()

        return ParsedCommand(
            name = name.take(48).ifBlank { "unknown" },
            arguments = arguments,
            original = raw,
        )
    }

    private fun function(
        handler: (
            RhinoContext,
            Scriptable,
            Scriptable,
            Array<out Any?>,
        ) -> Any?,
    ): NightJsFunction =
        NightJsFunction { cx, scope, thisObj, args ->
            handler(cx, scope, thisObj, args) ?: Undefined.instance
        }

    private fun put(
        target: Scriptable,
        name: String,
        value: Any,
    ) {
        ScriptableObject.putProperty(target, name, value)
    }

    private fun requireExecuting(session: Session) {
        check(session.executing) {
            "Night APIs that perform actions can only run inside a command handler."
        }
    }

    private fun stringArg(
        args: Array<out Any?>,
        index: Int,
        name: String,
    ): String =
        optionalString(args.getOrNull(index))
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: error(name + " is required.")

    private fun optionalString(value: Any?): String? =
        when {
            value == null || Undefined.isUndefined(value) -> null
            else -> RhinoContext.toString(value)
        }

    private fun storageKey(
        args: Array<out Any?>,
        index: Int,
    ): String {
        val key = stringArg(args, index, "storage key")
        require(STORAGE_KEY.matches(key)) {
            "Storage keys may contain letters, numbers, ., _, : and -."
        }
        return key
    }

    private fun propertyString(
        source: Scriptable,
        name: String,
    ): String {
        val value = ScriptableObject.getProperty(source, name)
        return optionalString(
            value.takeUnless { it === Scriptable.NOT_FOUND }
        ).orEmpty().trim()
    }

    private fun propertyStringList(
        source: Scriptable,
        name: String,
    ): List<String> {
        val value = ScriptableObject.getProperty(source, name)
        if (value !is NativeArray) return emptyList()

        return buildList {
            for (index in 0 until minOf(value.length.toInt(), 32)) {
                optionalString(value.get(index, value))
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }
    }

    private fun objectArg(value: Any?): JSONObject =
        when (val converted = jsToJson(value, 0)) {
            is JSONObject -> converted
            JSONObject.NULL -> JSONObject()
            else -> error("Expected an object.")
        }

    private fun jsToJson(
        value: Any?,
        depth: Int,
    ): Any {
        require(depth <= MAX_JSON_DEPTH) {
            "Script values are nested too deeply."
        }

        return when {
            value == null || Undefined.isUndefined(value) -> JSONObject.NULL
            value is String || value is Boolean -> value
            value is Number -> {
                val number = value.toDouble()
                if (number.isFinite()) value else JSONObject.NULL
            }
            value is NativeArray -> {
                val array = JSONArray()
                val length = minOf(value.length.toInt(), MAX_JSON_ARRAY)
                for (index in 0 until length) {
                    array.put(jsToJson(value.get(index, value), depth + 1))
                }
                array
            }
            value is Scriptable -> {
                val json = JSONObject()
                value.ids.take(MAX_JSON_PROPERTIES).forEach { id ->
                    val key = id.toString().take(120)
                    val property =
                        when (id) {
                            is Number -> value.get(id.toInt(), value)
                            else -> value.get(id.toString(), value)
                        }
                    if (property !== Scriptable.NOT_FOUND) {
                        json.put(key, jsToJson(property, depth + 1))
                    }
                }
                json
            }
            else -> RhinoContext.toString(value).take(MAX_MESSAGE_CHARS)
        }
    }

    private fun jsonToJs(
        cx: RhinoContext,
        scope: ScriptableObject,
        value: Any?,
        depth: Int = 0,
    ): Any? {
        if (depth > MAX_JSON_DEPTH) return Undefined.instance

        return when (value) {
            null, JSONObject.NULL -> null
            is String, is Boolean, is Number -> value
            is JSONObject -> {
                val target = cx.newObject(scope)
                val keys = value.keys()
                var count = 0
                while (keys.hasNext() && count < MAX_JSON_PROPERTIES) {
                    val key = keys.next()
                    ScriptableObject.putProperty(
                        target,
                        key,
                        jsonToJs(cx, scope, value.opt(key), depth + 1),
                    )
                    count++
                }
                target
            }
            is JSONArray -> {
                val length = minOf(value.length(), MAX_JSON_ARRAY)
                val elements = Array<Any?>(length) { index ->
                    jsonToJs(cx, scope, value.opt(index), depth + 1)
                }
                cx.newArray(scope, elements)
            }
            else -> value.toString()
        }
    }

    private fun readStorage(file: NightWorkspaceFile): JSONObject =
        synchronized(storageLock) {
            val target = storageFile(file)
            if (!target.isFile) return@synchronized JSONObject()
            runCatching {
                JSONObject(target.readText(Charsets.UTF_8))
            }.getOrElse { JSONObject() }
        }

    private fun writeStorage(
        file: NightWorkspaceFile,
        key: String,
        value: Any,
    ): JSONObject =
        synchronized(storageLock) {
            val target = storageFile(file)
            val current =
                if (target.isFile) {
                    runCatching {
                        JSONObject(target.readText(Charsets.UTF_8))
                    }.getOrElse { JSONObject() }
                } else {
                    JSONObject()
                }

            require(current.length() < MAX_STORAGE_KEYS || current.has(key)) {
                "This script reached its storage key limit."
            }

            current.put(key, value)
            val encoded = current.toString()
            require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_STORAGE_BYTES) {
                "This script reached its storage limit."
            }
            target.parentFile?.mkdirs()
            target.writeText(encoded, Charsets.UTF_8)

            JSONObject()
                .put("ok", true)
                .put("key", key)
        }

    private fun storageFile(file: NightWorkspaceFile): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(file.relativePath.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
        return File(
            workspace.scriptDataDirectory(),
            digest + ".json",
        )
    }

    private fun conciseError(error: Throwable): String {
        if (error is NightScriptTimeoutError) {
            return "execution timed out."
        }
        if (error is RhinoException) {
            return error.message
                ?.lineSequence()
                ?.firstOrNull()
                ?.take(240)
                ?: "JavaScript error."
        }
        return error.message
            ?.lineSequence()
            ?.firstOrNull()
            ?.take(240)
            ?: "Script error."
    }

    private data class ParsedCommand(
        val name: String,
        val arguments: String,
        val original: String,
    )

    private data class Registration(
        val name: String,
        val aliases: List<String>,
        val description: String,
        val handler: Function,
        val script: String,
    )

    private data class Session(
        val file: NightWorkspaceFile,
        val scope: ScriptableObject,
        val chatId: String,
        val invokingMessageId: String?,
        var executing: Boolean,
        val onRegister: (Registration) -> Unit,
    )

    private data class Discovery(
        val commands: List<NightScriptCommandSummary>,
        val byToken: Map<String, NightScriptCommandSummary>,
        val failures: List<String>,
    )

    companion object {
        private val COMMAND_NAME =
            Regex("[a-z0-9][a-z0-9_-]{0,47}")
        private val STORAGE_KEY =
            Regex("[A-Za-z0-9._:-]{1,96}")

        private const val MAX_SCRIPT_BYTES = 256L * 1024L
        private const val MAX_MESSAGE_CHARS = 16_000
        private const val MAX_LIBRARY_TEXT_CHARS = 100_000
        private const val MAX_JSON_DEPTH = 12
        private const val MAX_JSON_ARRAY = 256
        private const val MAX_JSON_PROPERTIES = 128
        private const val MAX_STORAGE_KEYS = 128
        private const val MAX_STORAGE_BYTES = 256 * 1024
        private const val SCRIPT_LOAD_TIMEOUT_MS = 1_500L
        private const val SCRIPT_EXECUTION_TIMEOUT_MS = 4_000L

        @Volatile private var instance: NightScriptRuntime? = null

        fun get(context: Context): NightScriptRuntime =
            instance ?: synchronized(this) {
                instance ?: NightScriptRuntime(
                    context.applicationContext
                ).also { instance = it }
            }
    }
}

private class NightScriptTimeoutError :
    Error("Night script execution timed out.")

internal class NightSandboxContextFactory : ContextFactory() {
    private val deadlineNanos = ThreadLocal<Long>()

    override fun makeContext(): RhinoContext =
        super.makeContext().apply {
            setInterpretedMode(true)
            languageVersion = RhinoContext.VERSION_ES6
            instructionObserverThreshold = 10_000
            maximumInterpreterStackDepth = 256
            setClassShutter(
                ClassShutter { false }
            )
        }

    override fun observeInstructionCount(
        cx: RhinoContext,
        instructionCount: Int,
    ) {
        val deadline = deadlineNanos.get() ?: return
        if (System.nanoTime() >= deadline) {
            throw NightScriptTimeoutError()
        }
    }

    fun <T> runWithDeadline(
        timeoutMs: Long,
        block: (RhinoContext) -> T,
    ): T {
        deadlineNanos.set(
            System.nanoTime() + timeoutMs * 1_000_000L
        )
        return try {
            call(
                ContextAction<T> { cx ->
                    block(cx)
                }
            )
        } finally {
            deadlineNanos.remove()
        }
    }
}
