package com.tomex777.annie

import android.content.Context
import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject

internal fun isExecutableScript(path: String): Boolean =
    path.substringAfterLast('.', "").lowercase() in setOf("js", "ts", "py", "lua") && !path.endsWith(".d.ts", true)

/** Compile in a separate runtime with no Annie, network or Android bridge exposed. */
internal object AnnieTypeScript {
    suspend fun compile(context: Context, sources: Map<String, String>): Map<String, String> {
        val typed = sources.filterKeys { it.endsWith(".ts", true) && !it.endsWith(".d.ts", true) }
        if (typed.isEmpty()) return sources.filterKeys(::isExecutableScript)
        val compiler = QuickJs.create(Dispatchers.IO)
        try {
            compiler.memoryLimit = 128L * 1024L * 1024L
            compiler.evaluationTimeoutMillis = 20_000L
            val source = context.assets.open("typescript/typescript.js").bufferedReader().use { it.readText() }
            compiler.evaluate<Unit>("$source\nvoid 0;", filename = "typescript-5.9.3.js")
            val bridge = context.assets.open("typescript/transpile.js").bufferedReader().use { it.readText() }
            compiler.evaluate<Unit>(bridge, filename = "annie-transpile.js")
            val output = sources.filterKeys(::isExecutableScript).toMutableMap()
            for ((path, text) in typed) {
                require(text.length <= 512_000) { "TypeScript source is too large: $path" }
                val json = compiler.evaluate<String>(
                    "JSON.stringify(annieTranspile(${JSONObject.quote(text)}, ${JSONObject.quote(path)}))",
                    filename = "compile-$path",
                )
                val result = JSONObject(json)
                require(!result.has("error")) { result.optString("error") }
                output[path] = result.getString("code")
            }
            return output
        } finally {
            compiler.close()
        }
    }
}
