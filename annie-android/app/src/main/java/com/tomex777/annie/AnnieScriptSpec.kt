package com.tomex777.annie

internal object AnnieScriptSpec {
    const val FILE_NAME = "ANNIE_SCRIPTING_SPEC.md"

    fun build(
        project: ScriptProject? = null,
        selectedPath: String? = null,
        selectedSource: String? = null,
    ): String = buildString {
        appendLine("# Annie Scripting Specification")
        appendLine()
        appendLine("Runtime: Annie local JavaScript / QuickJS")
        appendLine("Rendering rule: JavaScript returns structured data; Kotlin/Compose owns all native UI.")
        appendLine("Scripts cannot provide arbitrary Compose code or WebView HTML as a message renderer.")
        appendLine()

        appendLine("## Commands, actions, and sessions")
        appendLine("~~~js")
        appendLine("annie.commands.register({ name: \"hello\", async execute(ctx) { return annie.messages.text(\"Hi\"); } });")
        appendLine("annie.actions.register(\"retry\", async (payload, ctx) => annie.messages.text(\"Retrying\"));")
        appendLine("annie.sessions.register({ name: \"wizard\", async onMessage(ctx) { return annie.messages.text(ctx.text); } });")
        appendLine("ctx.session.start(\"wizard\");")
        appendLine("ctx.session.end();")
        appendLine("~~~")
        appendLine()

        appendLine("## Native services")
        appendLine("- annie.http.request(request) — native HTTP/HTTPS request with status, headers/body and optional browser session.")
        appendLine("- annie.browser.open(spec) — native inline browser message backed by Annie's browser session.")
        appendLine("- annie.browser.session(id) / annie.browser.clear(id) — inspect or clear a browser session.")
        appendLine("- annie.storage.get/set(key, value) — arbitrary persistent runtime data scoped to the script.")
        appendLine("- annie.files.readText/writeText/delete/list(path) — private script data files.")
        appendLine("- annie.log.info/warn/error(...) — Script Studio Output logging.")
        appendLine()

        appendLine("## Message API")
        appendLine("Registered native message types: ${MessageTypeRegistry.supportedWireNames().joinToString(", ")}.")
        appendLine("~~~js")
        appendLine("return annie.messages.text(\"Hello\");")
        appendLine("return annie.messages.image({ uri, caption: \"Result\" });")
        appendLine("return annie.messages.music({ title, artist, artwork, streamUrl, lyrics });")
        appendLine("return annie.messages.video({ title, uri, thumbnail, width: 1920, height: 1080 });")
        appendLine("return annie.messages.options({ title: \"Choose\", options: [{ id: \"a\", label: \"A\", action: \"pick\" }] });")
        appendLine("return annie.messages.progress({ text: \"Downloading…\", progress: 0.64, state: \"running\" });")
        appendLine("return annie.messages.browser({ url: \"https://example.com\", sessionId: \"source.main\" });")
        appendLine("~~~")
        appendLine("Progress states understood by the native renderer include running/indeterminate, queued, paused, success/completed, failed/error, and cancelled.")
        appendLine("Video cards are compact chat previews; tapping opens Annie's standalone player. Music remains an in-chat player.")
        appendLine()

        appendLine("## ENV — persistent user configuration")
        appendLine("ENV is declarative configuration and is separate from annie.storage.")
        appendLine("Supported field types: ${ScriptEnvFieldType.entries.joinToString(", ") { it.wireName }}.")
        appendLine("~~~js")
        appendLine("annie.env.define({")
        appendLine("  title: \"My Source\",")
        appendLine("  fields: [")
        appendLine("    { key: \"enabled\", type: \"switch\", label: \"Enabled\", default: true },")
        appendLine("    { key: \"baseUrl\", type: \"text\", label: \"Base URL\" },")
        appendLine("    { key: \"token\", type: \"secret\", label: \"Token\" },")
        appendLine("    { key: \"quality\", type: \"select\", label: \"Preferred quality\", options: [\"720p\", \"1080p\"], default: \"1080p\", scriptWritable: true }")
        appendLine("  ]")
        appendLine("});")
        appendLine("const enabled = await annie.env.get(\"enabled\");")
        appendLine("const token = await annie.env.secret(\"token\");")
        appendLine("await annie.env.set(\"quality\", \"720p\"); // only when scriptWritable is true")
        appendLine("const publicValues = annie.env.values(); // secrets are excluded")
        appendLine("~~~")
        appendLine("Secret ENV values are script-scoped, masked in UI, stored with Android Keystore-backed encryption, excluded from values(), and available only through annie.env.secret(key).")
        appendLine()

        appendLine("## Browser security")
        appendLine("Browser messages use safe HTTP/HTTPS URLs. Restricted sessions only navigate allowed hosts/subdomains. Browser-session HTTP requests reuse the session's cookies and User-Agent.")
        appendLine()

        appendLine("## Runtime constraints")
        appendLine("- Script execution is bounded; do not build permanently running loops.")
        appendLine("- Keep normal AI/script calls short and structured.")
        appendLine("- Large/long work should be modeled as resumable native tasks when that API is available.")
        appendLine("- Secrets must never be printed to logs or returned in normal exports.")
        appendLine()

        appendLine("## Complete command example")
        appendLine("~~~js")
        appendLine("annie.commands.register({")
        appendLine("  name: \"source\",")
        appendLine("  description: \"Fetch a configured source\",")
        appendLine("  async execute(ctx) {")
        appendLine("    if (!(await annie.env.get(\"enabled\"))) return annie.messages.text(\"Source is disabled\");")
        appendLine("    const baseUrl = await annie.env.get(\"baseUrl\");")
        appendLine("    const response = await annie.http.request({ url: baseUrl + \"/search?q=\" + encodeURIComponent(ctx.text) });")
        appendLine("    return annie.messages.text(response.ok ? response.body : \"Request failed: \" + response.status);")
        appendLine("  }")
        appendLine("});")
        appendLine("~~~")

        if (project != null) {
            appendLine()
            appendLine("## Current project context")
            appendLine("Project: ${project.name}")
            appendLine("Entry: ${project.entryPath}")
            project.files.toSortedMap().forEach { (path, source) ->
                appendLine()
                appendLine("### $path")
                appendLine("~~~js")
                appendLine(if (path == selectedPath && selectedSource != null) selectedSource else source)
                appendLine("~~~")
            }
        }
    }
}
