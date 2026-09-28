package app.nami.android

import android.util.Log
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.TimeoutCancellationException

internal fun sourceFailureMessage(
    failure: Throwable,
    fallback: String = "Source unavailable. Try again.",
): String {
    val causes = generateSequence(failure as Throwable?) { current ->
        current?.cause?.takeUnless { it === current }
    }.take(8).filterNotNull().toList()
    val diagnosticText = causes
        .mapNotNull { it.message }
        .joinToString(" ")
        .lowercase()

    return when {
        causes.any { it is TimeoutCancellationException || it is SocketTimeoutException } ||
            "timed out" in diagnosticText ||
            "timeout" in diagnosticText -> "Source timed out. Try again."

        "cloudflare" in diagnosticText ||
            "captcha" in diagnosticText ||
            "challenge" in diagnosticText -> "This source needs browser verification."

        causes.any { it is UnknownHostException || it is ConnectException } ||
            "unable to resolve host" in diagnosticText -> {
            "Could not reach this source. Check your connection and try again."
        }

        causes.any { it is IOException } ->
            "Network error. Check your connection and try again."

        causes.any { it is SecurityException } ->
            "This source could not access a required resource."

        else -> fallback
    }
}

internal fun logSourceFailure(stage: String, failure: Throwable) {
    // Keep extension/parser diagnostics available to developers without exposing them in the UI.
    runCatching {
        Log.w("NamiSource", stage + " failed", failure)
    }
}
