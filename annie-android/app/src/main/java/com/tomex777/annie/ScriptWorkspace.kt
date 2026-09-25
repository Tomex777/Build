package com.tomex777.annie

import android.content.Context
import com.dokar.quickjs.ModuleContent
import com.dokar.quickjs.ModuleLoader
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

internal data class ScriptProject(
    val id: String,
    val name: String,
    val entryPath: String,
    val files: Map<String, String>,
    val enabled: Boolean = true,
)

internal data class ScriptCommand(
    val scriptId: String,
    val name: String,
    val aliases: List<String>,
    val description: String,
    val usage: String,
)

internal data class ScriptLog(
    val atMillis: Long,
    val scriptId: String,
    val level: String,
    val message: String,
)

internal data class ScriptDispatch(
    val scriptId: String,
    val channel: String,
    val resultJson: String,
)

private data class ActiveScriptSession(
    val scriptId: String,
    val sessionName: String,
)

/** Private, app-local JavaScript project files. No script path can escape this workspace. */
internal class ScriptFiles(context: Context) {
    private val appContext = context.applicationContext
    private val enabledPrefs = appContext.getSharedPreferences("annie_script_enabled", Context.MODE_PRIVATE)
    val root: File = File(appContext.filesDir, "annie-scripts").apply { mkdirs() }

    fun ensureStarterScript() {
        val echo = File(root, "echo.js")
        if (!echo.exists()) {
            echo.writeText(
                """annie.commands.register({
                    |  name: "echo",
                    |  description: "Repeat a message",
                    |  usage: "/echo <text>",
                    |  async execute(ctx) {
                    |    return { type: "text", text: ctx.text || "Hello from Annie JavaScript 😭" };
                    |  }
                    |});
                """.trimMargin()
            )
        }
        val chess = File(root, "chess.js")
        if (!chess.exists()) chess.writeText(StarterScripts.chess)
    }

    fun listProjects(): List<ScriptProject> = root.listFiles().orEmpty()
        .filter { it.isDirectory || it.isFile && it.extension.equals("js", ignoreCase = true) }
        .mapNotNull(::readProject)
        .sortedBy { it.name.lowercase() }

    fun readProject(file: File): ScriptProject? = runCatching {
        if (!file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) return null
        val relative = file.relativeTo(root).invariantSeparatorsPath
        val entry = if (file.isDirectory) File(file, "main.js") else file
        if (!entry.isFile || !entry.extension.equals("js", ignoreCase = true)) return null
        val sourceFiles = linkedMapOf<String, String>()
        if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile && it.extension.equals("js", ignoreCase = true) }
                .forEach { child -> sourceFiles[child.relativeTo(file).invariantSeparatorsPath] = child.readText() }
        } else {
            sourceFiles[entry.name] = entry.readText()
        }
        val entryRelative = if (file.isDirectory) "main.js" else file.name
        val id = relative.removeSuffix(".js")
        ScriptProject(
            id = id,
            name = file.nameWithoutExtension,
            entryPath = entryRelative,
            files = sourceFiles,
            enabled = enabledPrefs.getBoolean(id, true),
        )
    }.getOrNull()

    fun resolveRelativePath(baseModule: String, requested: String, projectId: String): String? {
        if (!requested.startsWith("./") && !requested.startsWith("../")) return null
        val prefix = "$projectId/"
        if (!baseModule.startsWith(prefix)) return null
        val parent = baseModule.removePrefix(prefix).substringBeforeLast('/', "")
        val stack = mutableListOf<String>()
        if (parent.isNotEmpty()) stack += parent.split('/').filter(String::isNotEmpty)
        for (part in requested.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isEmpty()) return null else stack.removeAt(stack.lastIndex)
                else -> {
                    if (part.contains('\\') || part == ":") return null
                    stack += part
                }
            }
        }
        if (stack.isEmpty() || stack.any { it.startsWith('.') && it == ".." }) return null
        val resolved = stack.joinToString("/")
        return if (resolved.endsWith(".js", ignoreCase = true)) "$prefix$resolved" else "$prefix$resolved.js"
    }

    fun createScript(name: String): File {
        val safe = validateName(name, ".js")
        return File(root, safe).apply {
            require(!exists()) { "A script with this name already exists" }
            writeText("""annie.commands.register({
                |  name: "${safe.removeSuffix(".js")}",
                |  description: "My command",
                |  async execute(ctx) {
                |    return { type: "text", text: "Hello from JavaScript" };
                |  }
                |});
            """.trimMargin())
        }
    }

    fun createFolder(name: String): File {
        val safe = validateName(name, "")
        return File(root, safe).apply {
            require(mkdir()) { "A project with this name already exists" }
            File(this, "main.js").writeText("""annie.commands.register({
                |  name: "$safe",
                |  description: "My command",
                |  async execute(ctx) {
                |    return { type: "text", text: "Hello from JavaScript" };
                |  }
                |});
            """.trimMargin())
        }
    }

    fun readFile(projectId: String, relativePath: String): String {
        val target = resolveProjectFile(projectId, relativePath)
        require(target.isFile) { "Script file does not exist" }
        return target.readText()
    }

    fun setEnabled(projectId: String, enabled: Boolean) {
        resolveProjectContainer(projectId)
        enabledPrefs.edit().putBoolean(projectId, enabled).apply()
    }

    fun isEnabled(projectId: String): Boolean =
        enabledPrefs.getBoolean(projectId, true)

    fun writeFile(projectId: String, relativePath: String, source: String) {
        require(source.length <= MAX_SOURCE_CHARS) { "Script file is too large" }
        val target = resolveProjectFile(projectId, relativePath, allowMissing = true)
        require(target.extension.equals("js", ignoreCase = true)) { "Only JavaScript files are supported" }
        target.parentFile?.mkdirs()
        target.writeText(source)
    }

    fun createFile(projectId: String, relativePath: String): File {
        val project = resolveProjectContainer(projectId)
        require(project.isDirectory) { "Standalone scripts cannot contain helper files" }
        val safeRelative = validateRelativeJsPath(relativePath)
        val target = resolveProjectFile(projectId, safeRelative, allowMissing = true)
        require(!target.exists()) { "A script file with this name already exists" }
        target.parentFile?.mkdirs()
        target.writeText("// Annie JavaScript module\n")
        return target
    }

    fun renameProject(projectId: String, newName: String): String {
        val source = resolveProjectContainer(projectId)
        val normalized = validateName(newName, "")
        val destination = if (source.isDirectory) File(root, normalized) else File(root, "$normalized.js")
        require(!destination.exists()) { "A script project with this name already exists" }
        val wasEnabled = isEnabled(projectId)
        require(source.renameTo(destination)) { "Could not rename script project" }
        enabledPrefs.edit().remove(projectId).putBoolean(normalized, wasEnabled).apply()
        return normalized
    }

    fun deleteProject(projectId: String) {
        val source = resolveProjectContainer(projectId)
        if (source.isDirectory) {
            require(source.deleteRecursively()) { "Could not delete script project" }
        } else {
            require(source.delete()) { "Could not delete script file" }
        }
        enabledPrefs.edit().remove(projectId).apply()
    }

    fun renameFile(projectId: String, relativePath: String, newRelativePath: String): String {
        val project = resolveProjectContainer(projectId)
        require(project.isDirectory) { "Rename the standalone project instead" }
        val source = resolveProjectFile(projectId, relativePath)
        require(source.isFile) { "Script file does not exist" }
        val safeTarget = validateRelativeJsPath(newRelativePath)
        val destination = resolveProjectFile(projectId, safeTarget, allowMissing = true)
        require(!destination.exists()) { "A script file with this name already exists" }
        destination.parentFile?.mkdirs()
        require(source.renameTo(destination)) { "Could not rename script file" }
        return safeTarget
    }

    fun deleteFile(projectId: String, relativePath: String) {
        val project = resolveProjectContainer(projectId)
        require(project.isDirectory) { "Delete the standalone project instead" }
        require(relativePath != "main.js") { "main.js is the folder entry point and cannot be deleted" }
        val target = resolveProjectFile(projectId, relativePath)
        require(target.isFile && target.delete()) { "Could not delete script file" }
    }

    private fun resolveProjectContainer(projectId: String): File {
        require(projectId.matches(Regex("[A-Za-z0-9_-]{1,48}"))) { "Invalid project id" }
        val directory = File(root, projectId).canonicalFile
        if (directory.isDirectory) return directory
        val standalone = File(root, "$projectId.js").canonicalFile
        if (standalone.isFile) return standalone
        throw IllegalArgumentException("Script project does not exist")
    }

    private fun resolveProjectFile(projectId: String, relativePath: String, allowMissing: Boolean = false): File {
        val project = resolveProjectContainer(projectId)
        if (project.isFile) {
            require(relativePath == project.name) { "Standalone scripts have a single file" }
            return project
        }
        val safeRelative = validateRelativeJsPath(relativePath)
        val target = File(project, safeRelative).canonicalFile
        require(target.toPath().startsWith(project.canonicalFile.toPath())) { "Invalid script path" }
        if (!allowMissing) require(target.exists()) { "Script file does not exist" }
        return target
    }

    private fun validateRelativeJsPath(path: String): String {
        val normalized = path.trim().replace('\\', '/').removePrefix("/")
        require(normalized.matches(Regex("(?:[A-Za-z0-9_-]{1,48}/)*[A-Za-z0-9_-]{1,48}\\.js"))) {
            "Use JavaScript paths such as helper.js or lib/parser.js"
        }
        return normalized
    }

    private fun validateName(name: String, suffix: String): String {
        val normalized = name.trim().removeSuffix(suffix)
        require(normalized.matches(Regex("[A-Za-z0-9_-]{1,48}"))) { "Use letters, numbers, _ or - for names" }
        return "$normalized$suffix"
    }

    companion object { const val MAX_SOURCE_CHARS = 512_000 }
}

/** One isolated QuickJS runtime per enabled project; all work is serialized off the main thread. */
internal class ScriptRuntime(
    private val context: Context,
    private val project: ScriptProject,
    private val files: ScriptFiles,
    private val onLog: (ScriptLog) -> Unit,
) : AutoCloseable {
    private val lock = Mutex()
    private val registered = linkedMapOf<String, ScriptCommand>()
    private val runtime: QuickJs

    init {
        val modulePrefix = "${project.id}/"
        val loader = ModuleLoader { normalizedName ->
            if (!normalizedName.startsWith(modulePrefix)) return@ModuleLoader null
            val relative = normalizedName.removePrefix(modulePrefix)
            project.files[relative]?.let(ModuleContent::Source)
        }
        runtime = QuickJs.create(Dispatchers.IO, loader)
        runtime.memoryLimit = MAX_RUNTIME_BYTES
        runtime.evaluationTimeoutMillis = EVALUATION_TIMEOUT_MS
        runtime.function("annieRegisterCommand") { args ->
            val metadata = JSONObject(args.firstOrNull() as? String ?: error("Missing command definition"))
            val name = metadata.optString("name").lowercase()
            require(name.matches(Regex("[a-z][a-z0-9_-]{0,31}"))) { "Command names must start with a letter" }
            val aliases = metadata.optJSONArray("aliases").toStringList()
            val command = ScriptCommand(
                scriptId = project.id,
                name = name,
                aliases = aliases.map(String::lowercase),
                description = metadata.optString("description"),
                usage = metadata.optString("usage", "/$name"),
            )
            registered[name] = command
            aliases.forEach { registered[it.lowercase()] = command }
            null
        }
        runtime.function("annieStoreGet") { args ->
            val key = args.firstOrNull()?.toString().orEmpty()
            context.getSharedPreferences(storageName(project.id), Context.MODE_PRIVATE).getString(key, null)
        }
        runtime.function("annieStoreSet") { args ->
            val key = args.getOrNull(0)?.toString().orEmpty()
            val value = args.getOrNull(1)?.toString().orEmpty()
            context.getSharedPreferences(storageName(project.id), Context.MODE_PRIVATE).edit().putString(key, value).apply()
            null
        }
        runtime.function("annieSessionSet") { args ->
            val chatId = args.getOrNull(0)?.toString().orEmpty()
            val sessionName = args.getOrNull(1)?.toString().orEmpty()
            require(chatId.isNotBlank()) { "Session requires a chat id" }
            require(sessionName.matches(Regex("[A-Za-z][A-Za-z0-9_.:-]{0,63}"))) { "Invalid session name" }
            writeActiveSession(context, chatId, ActiveScriptSession(project.id, sessionName))
            null
        }
        runtime.function("annieSessionClear") { args ->
            val chatId = args.getOrNull(0)?.toString().orEmpty()
            if (chatId.isNotBlank()) clearActiveSession(context, chatId)
            null
        }
        runtime.function("annieRenderChess") { args ->
            val fen = args.firstOrNull()?.toString().orEmpty()
            ChessBoardRenderer.render(context, fen)
        }
        runtime.asyncFunction("annieHttpRequest") { args ->
            val request = JSONObject(args.firstOrNull() as? String ?: "{}")
            requestHttp(request)
        }
        runtime.function("annieLog") { args ->
            val level = args.getOrNull(0)?.toString()?.uppercase()?.take(8) ?: "INFO"
            val message = redact(args.drop(1).joinToString(" ")).take(MAX_LOG_CHARS)
            onLog(ScriptLog(System.currentTimeMillis(), project.id, level, message))
            null
        }
    }

    suspend fun load(): List<ScriptCommand> = lock.withLock {
        registered.clear()
        runtime.evaluate<Unit>(BOOTSTRAP, filename = "annie-runtime.js")
        val entryModule = "${project.id}/${project.entryPath}"
        val entry = project.files[project.entryPath] ?: error("Missing script entry: ${project.entryPath}")
        runtime.evaluate<Unit>(entry, filename = entryModule, asModule = true)
        registered.values.distinctBy { it.name }
    }

    suspend fun execute(commandName: String, commandText: String, chatId: String, messageId: Long): String = lock.withLock {
        val commandArgs = commandText.trim().split(Regex("\\s+")).filter(String::isNotBlank).drop(1)
        val invocation = JSONObject()
            .put("text", commandArgs.joinToString(" "))
            .put("args", JSONArray(commandArgs))
            .put("command", commandName)
            .put("chatId", chatId)
            .put("messageId", messageId)
            .put("replyTo", JSONObject.NULL)
        val source = "await globalThis.__annieRun(${JSONObject.quote(commandName)}, ${JSONObject.quote(invocation.toString())})"
        runtime.evaluate<String>(source, filename = "annie-invocation.js", asModule = true)
    }

    suspend fun executeSession(sessionName: String, text: String, chatId: String, messageId: Long): String = lock.withLock {
        val invocation = JSONObject()
            .put("text", text)
            .put("args", JSONArray())
            .put("command", "")
            .put("chatId", chatId)
            .put("messageId", messageId)
            .put("replyTo", JSONObject.NULL)
        val source = "await globalThis.__annieSession(${JSONObject.quote(sessionName)}, ${JSONObject.quote(invocation.toString())})"
        runtime.evaluate<String>(source, filename = "annie-session.js", asModule = true)
    }

    suspend fun executeAction(actionId: String, payloadJson: String, chatId: String, messageId: Long): String = lock.withLock {
        val invocation = JSONObject()
            .put("text", "")
            .put("args", JSONArray())
            .put("command", "")
            .put("chatId", chatId)
            .put("messageId", messageId)
            .put("replyTo", JSONObject.NULL)
        val source = "await globalThis.__annieAction(${JSONObject.quote(actionId)}, ${JSONObject.quote(payloadJson)}, ${JSONObject.quote(invocation.toString())})"
        runtime.evaluate<String>(source, filename = "annie-action.js", asModule = true)
    }

    override fun close() { runtime.close() }

    private suspend fun requestHttp(request: JSONObject): Map<String, Any?> = withContext(Dispatchers.IO) {
        val address = request.optString("url")
        val url = URL(address)
        require(url.protocol == "https" || url.protocol == "http") { "Only HTTP and HTTPS URLs are allowed" }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = request.optString("method", "GET").uppercase().takeIf { it in ALLOWED_METHODS } ?: "GET"
            connectTimeout = request.optInt("timeoutMs", DEFAULT_HTTP_TIMEOUT_MS).coerceIn(1_000, MAX_HTTP_TIMEOUT_MS)
            readTimeout = connectTimeout
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Annie/1.0")
            request.optJSONObject("headers")?.let { headers ->
                val iterator = headers.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    if (key.equals("host", true) || key.equals("content-length", true) || key.equals("connection", true)) continue
                    setRequestProperty(key.take(128), headers.optString(key).take(4096))
                }
            }
        }
        try {
            val body = request.optString("body").takeIf { request.has("body") && !request.isNull("body") }
            if (body != null && connection.requestMethod in BODY_METHODS) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", request.optString("contentType", "application/json"))
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val responseBody = stream?.bufferedReader()?.use { it.readText().take(MAX_HTTP_RESPONSE_CHARS) }.orEmpty()
            val responseJson = runCatching { JSONObject(responseBody).toMap() }.getOrNull()
            mapOf(
                "status" to status,
                "ok" to (status in 200..299),
                "url" to (connection.url?.toString() ?: address),
                "body" to responseBody,
                "json" to responseJson,
            )
        } finally { connection.disconnect() }
    }

    private fun redact(value: String): String = value.replace(
        Regex("(?i)(authorization|api[_-]?key|token|password)(\\s*[=:]\\s*)[^,\\s]+"), "$1$2[redacted]"
    )

    companion object {
        private const val MAX_RUNTIME_BYTES = 32L * 1024L * 1024L
        private const val EVALUATION_TIMEOUT_MS = 8_000L
        private const val DEFAULT_HTTP_TIMEOUT_MS = 20_000
        private const val MAX_HTTP_TIMEOUT_MS = 120_000
        private const val MAX_HTTP_RESPONSE_CHARS = 2_000_000
        private const val MAX_LOG_CHARS = 2_000
        private val ALLOWED_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE")
        private val BODY_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
        private fun storageName(id: String) = "annie_script_storage_${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}"

        private val BOOTSTRAP = """
            |globalThis.__annieCommandHandlers = Object.create(null);
            |globalThis.__annieActionHandlers = Object.create(null);
            |globalThis.__annieSessionHandlers = Object.create(null);
            |const __annieContext = rawContext => {
            |  const ctx = JSON.parse(rawContext);
            |  ctx.session = {
            |    start: name => annieSessionSet(String(ctx.chatId), String(name)),
            |    end: () => annieSessionClear(String(ctx.chatId))
            |  };
            |  return ctx;
            |};
            |globalThis.annie = {
            |  commands: { register(definition) {
            |    if (!definition || typeof definition.execute !== "function") throw new TypeError("Command requires execute(ctx)");
            |    if (!definition.name) throw new TypeError("Command requires a name");
            |    const name = String(definition.name).toLowerCase();
            |    const aliases = Array.isArray(definition.aliases) ? definition.aliases.map(String) : [];
            |    annieRegisterCommand(JSON.stringify({name, aliases, description: definition.description || "", usage: definition.usage || "/" + name}));
            |    globalThis.__annieCommandHandlers[name] = definition.execute;
            |    for (const alias of aliases) globalThis.__annieCommandHandlers[alias.toLowerCase()] = definition.execute;
            |  }},
            |  actions: { register(name, handler) {
            |    if (typeof handler !== "function") throw new TypeError("Action requires a handler");
            |    globalThis.__annieActionHandlers[String(name)] = handler;
            |  }},
            |  sessions: { register(definition) {
            |    if (!definition || !definition.name || typeof definition.onMessage !== "function") throw new TypeError("Session requires name and onMessage(ctx)");
            |    globalThis.__annieSessionHandlers[String(definition.name)] = definition.onMessage;
            |  }},
            |  http: { request: request => annieHttpRequest(JSON.stringify(request)) },
            |  image: { chess: fen => annieRenderChess(String(fen)) },
            |  storage: {
            |    get: key => { const raw = annieStoreGet(String(key)); return raw == null ? null : JSON.parse(raw); },
            |    set: (key, value) => annieStoreSet(String(key), JSON.stringify(value))
            |  },
            |  log: { info: (...args) => annieLog("INFO", ...args), warn: (...args) => annieLog("WARN", ...args), error: (...args) => annieLog("ERROR", ...args) }
            |};
            |globalThis.console = annie.log;
            |globalThis.__annieRun = async (name, rawContext) => {
            |  const execute = globalThis.__annieCommandHandlers[String(name).toLowerCase()];
            |  if (!execute) throw new Error("Command not registered: " + name);
            |  const result = await execute(__annieContext(rawContext));
            |  return JSON.stringify(result === undefined ? null : result);
            |};
            |globalThis.__annieSession = async (name, rawContext) => {
            |  const handler = globalThis.__annieSessionHandlers[String(name)];
            |  if (!handler) throw new Error("Session not registered: " + name);
            |  const result = await handler(__annieContext(rawContext));
            |  return JSON.stringify(result === undefined ? null : result);
            |};
            |globalThis.__annieAction = async (name, rawPayload, rawContext) => {
            |  const handler = globalThis.__annieActionHandlers[String(name)];
            |  if (!handler) throw new Error("Action not registered: " + name);
            |  const payload = rawPayload ? JSON.parse(rawPayload) : null;
            |  const result = await handler(payload, __annieContext(rawContext));
            |  return JSON.stringify(result === undefined ? null : result);
            |};
        """.trimMargin()
    }
}

/** Loads enabled scripts, refreshes slash metadata, and routes a command to its owning runtime. */
internal class ScriptWorkspace(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    val files = ScriptFiles(appContext)
    private val runtimes = ConcurrentHashMap<String, ScriptRuntime>()
    private val logs = mutableListOf<ScriptLog>()
    private val logLock = Any()
    @Volatile private var commands: List<ScriptCommand> = emptyList()

    init { files.ensureStarterScript() }

    fun commands(): List<ScriptCommand> = commands
    fun logs(): List<ScriptLog> = synchronized(logLock) { logs.toList() }

    suspend fun reload(): List<ScriptCommand> = withContext(Dispatchers.IO) {
        val projects = files.listProjects()
        val enabledProjects = projects.filter { it.enabled }
        val old = runtimes.values.toList()
        runtimes.clear()
        old.forEach(ScriptRuntime::close)
        val nextCommands = mutableListOf<ScriptCommand>()
        for (project in enabledProjects) {
            runCatching {
                val engine = ScriptRuntime(appContext, project, files, ::appendLog)
                val loadedCommands = engine.load()
                runtimes[project.id] = engine
                nextCommands += loadedCommands
                appendLog(ScriptLog(System.currentTimeMillis(), project.id, "INFO", "Loaded ${project.entryPath}"))
            }.onFailure { error ->
                appendLog(ScriptLog(System.currentTimeMillis(), project.id, "ERROR", error.message ?: "Script failed to load"))
            }
        }
        commands = nextCommands.distinctBy { it.name }
        commands
    }

    suspend fun execute(commandName: String, commandText: String, chatId: String, messageId: Long): String? {
        val command = commands.firstOrNull { it.name == commandName.lowercase() || commandName.lowercase() in it.aliases }
            ?: return null
        val runtime = runtimes[command.scriptId] ?: return null
        return runCatching {
            appendLog(ScriptLog(System.currentTimeMillis(), command.scriptId, "INFO", "Command /${command.name}"))
            runtime.execute(command.name, commandText, chatId, messageId)
        }.onFailure { error ->
            appendLog(ScriptLog(System.currentTimeMillis(), command.scriptId, "ERROR", error.message ?: "Script execution failed"))
        }.getOrElse { JSONObject().put("type", "error").put("text", it.message ?: "Script failed").toString() }
    }

    suspend fun executeSession(text: String, chatId: String, messageId: Long): ScriptDispatch? {
        val active = readActiveSession(appContext, chatId) ?: return null
        val runtime = runtimes[active.scriptId]
        if (runtime == null) {
            clearActiveSession(appContext, chatId)
            return null
        }
        val json = runCatching {
            appendLog(ScriptLog(System.currentTimeMillis(), active.scriptId, "INFO", "Session ${active.sessionName}"))
            runtime.executeSession(active.sessionName, text, chatId, messageId)
        }.onFailure { error ->
            appendLog(ScriptLog(System.currentTimeMillis(), active.scriptId, "ERROR", error.message ?: "Session execution failed"))
        }.getOrElse { JSONObject().put("type", "error").put("text", it.message ?: "Session failed").toString() }
        return ScriptDispatch(active.scriptId, active.sessionName, json)
    }

    suspend fun executeAction(
        scriptId: String,
        actionId: String,
        payloadJson: String,
        chatId: String,
        messageId: Long,
    ): ScriptDispatch? {
        val runtime = runtimes[scriptId] ?: return null
        val json = runCatching {
            appendLog(ScriptLog(System.currentTimeMillis(), scriptId, "INFO", "Action $actionId"))
            runtime.executeAction(actionId, payloadJson, chatId, messageId)
        }.onFailure { error ->
            appendLog(ScriptLog(System.currentTimeMillis(), scriptId, "ERROR", error.message ?: "Action execution failed"))
        }.getOrElse { JSONObject().put("type", "error").put("text", it.message ?: "Action failed").toString() }
        return ScriptDispatch(scriptId, actionId, json)
    }

    private fun appendLog(row: ScriptLog) = synchronized(logLock) {
        logs += row
        while (logs.size > 1_000) logs.removeAt(0)
    }

    override fun close() { runtimes.values.forEach(ScriptRuntime::close); runtimes.clear() }
}

private fun JSONArray?.toStringList(): List<String> = this?.let { array ->
    buildList { for (index in 0 until array.length()) array.optString(index).trim().takeIf(String::isNotBlank)?.let(::add) }
}.orEmpty()

private const val SESSION_PREFS = "annie_script_sessions"

private fun writeActiveSession(context: Context, chatId: String, session: ActiveScriptSession) {
    val raw = JSONObject().put("scriptId", session.scriptId).put("sessionName", session.sessionName).toString()
    context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).edit().putString(chatId, raw).apply()
}

private fun readActiveSession(context: Context, chatId: String): ActiveScriptSession? {
    val raw = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).getString(chatId, null) ?: return null
    return runCatching {
        val json = JSONObject(raw)
        ActiveScriptSession(json.getString("scriptId"), json.getString("sessionName"))
    }.getOrNull()
}

private fun clearActiveSession(context: Context, chatId: String) {
    context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).edit().remove(chatId).apply()
}

private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key -> opt(key).takeUnless { it == JSONObject.NULL } }
