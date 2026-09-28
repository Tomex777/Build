package app.nami.android

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import app.nami.source.NamiSourceErrorKind
import app.nami.source.NamiSourceException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class SourceFailurePresentationTest {
    @Test
    fun unavailableStreamHasAUsefulUserMessage() {
        val message = sourceFailureMessage(
            NamiSourceException(
                kind = NamiSourceErrorKind.STREAM_UNAVAILABLE,
                message = "Internal extractor detail",
            ),
        )

        assertEquals("This episode has no playable stream right now.", message)
        assertFalse(message.contains("extractor"))
    }

    @Test
    fun timeoutDoesNotExposeRawProviderMessage() {
        val message = sourceFailureMessage(
            SocketTimeoutException("GET https://provider.example/private-path timed out"),
        )

        assertEquals("Source timed out. Try again.", message)
        assertFalse(message.contains("provider.example"))
    }

    @Test
    fun coroutineTimeoutUsesTheSameSanitizedMessage() = runBlocking {
        val failure = runCatching {
            withTimeout(10) { delay(1_000) }
        }.exceptionOrNull()

        assertTrue(failure is TimeoutCancellationException)
        assertEquals("Source timed out. Try again.", sourceFailureMessage(requireNotNull(failure)))
    }

    @Test
    fun browserChallengeGetsActionableMessage() {
        val message = sourceFailureMessage(
            IOException("Cloudflare challenge token rejected by extractor implementation"),
        )

        assertEquals("This source needs browser verification.", message)
        assertFalse(message.contains("extractor"))
    }

    @Test
    fun connectivityFailureGetsStableMessage() {
        val message = sourceFailureMessage(
            UnknownHostException("Unable to resolve host private-provider.invalid"),
        )

        assertEquals(
            "Could not reach this source. Check your connection and try again.",
            message,
        )
        assertFalse(message.contains("private-provider"))
    }

    @Test
    fun parserFailureUsesCallSiteFallbackInsteadOfRawInternals() {
        val raw = "CSS selector #episode-list > iframe was missing"
        val message = sourceFailureMessage(
            IllegalStateException(raw),
            fallback = "Could not load this anime. Try again.",
        )

        assertEquals("Could not load this anime. Try again.", message)
        assertFalse(message.contains("selector", ignoreCase = true))
    }

    @Test
    fun genericIoFailureDoesNotLeakUrlOrHttpImplementationText() {
        val message = sourceFailureMessage(
            IOException("HTTP 503 from https://cdn.example/secret?token=abc"),
        )

        assertEquals("Network error. Check your connection and try again.", message)
        assertFalse(message.contains("token"))
    }
}
