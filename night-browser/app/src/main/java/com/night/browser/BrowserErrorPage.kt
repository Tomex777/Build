package com.night.browser

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Local, offline Gecko error document. The untrusted failed URI is NEVER
 * interpolated into HTML, avoiding script/markup injection and referrer leaks.
 * Retry uses the browser's Reload action, which retains the original URL.
 */
internal object BrowserErrorPage {
    enum class Kind { CONNECTION, SECURITY, BLOCKED, UNKNOWN }

    fun dataUri(kind: Kind): String {
        val (heading, detail) = when (kind) {
            Kind.CONNECTION -> "Couldn't connect" to
                "Check your internet connection or the website address."
            Kind.SECURITY -> "Connection isn't secure" to
                "NIGHT Browser couldn't verify this website's security."
            Kind.BLOCKED -> "Website blocked" to
                "This page was blocked to protect your device."
            Kind.UNKNOWN -> "Page unavailable" to
                "This page couldn't load. Try again or use another website."
        }
        val html = """
            <!doctype html><html lang="en"><head>
            <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta http-equiv="Content-Security-Policy"
              content="default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'">
            <title>NIGHT Browser · $heading</title>
            <style>
              *{box-sizing:border-box}html,body{min-height:100%;margin:0}
              body{background:#0c1017;color:#f3f5f9;font:16px/1.55 system-ui,Arial,sans-serif}
              main{max-width:480px;margin:0 auto;padding:clamp(80px,20vh,180px) 28px 36px}
              svg{display:block;margin-bottom:28px;color:#b9beff}
              h1{font-size:25px;line-height:1.24;letter-spacing:-.5px;margin:0 0 13px;font-weight:650}
              p{color:#9da8ba;margin:0 0 25px;max-width:350px}
              small{display:block;color:#788495}
            </style></head><body><main role="main">
            <svg width="47" height="47" viewBox="0 0 48 48" fill="none"
                 aria-hidden="true"><rect x="4" y="4" width="40" height="40"
                 rx="11" stroke="currentColor" stroke-width="2"/>
                 <path d="M14 24h20M24 14v20" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round"/></svg>
            <h1>$heading</h1><p>$detail</p>
            <small>Use Reload to try again.</small></main></body></html>
        """.trimIndent()
        return "data:text/html;charset=utf-8;base64," +
            Base64.getEncoder().encodeToString(html.toByteArray(StandardCharsets.UTF_8))
    }
}
