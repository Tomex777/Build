package eu.kanade.tachiyomi.util

import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Compatibility shim adapted from Aniyomi v0.18.2.1 source-api
 * (eu/kanade/tachiyomi/util/JsoupExtensions.kt), Apache License 2.0.
 * See apps/nami/licenses/ANIYOMI-APACHE-2.0.txt and THIRD_PARTY_NOTICES.md.
 */
fun Element.selectText(css: String, defaultValue: String? = null): String? =
    select(css).first()?.text() ?: defaultValue

fun Element.selectInt(css: String, defaultValue: Int = 0): Int =
    select(css).first()?.text()?.toInt() ?: defaultValue

fun Element.attrOrText(css: String): String =
    if (css != "text") attr(css) else text()

/**
 * Returns a Jsoup document for this response.
 * @param html the response body, if it has already been consumed.
 */
fun Response.asJsoup(html: String? = null): Document =
    Jsoup.parse(html ?: body.string(), request.url.toString())
