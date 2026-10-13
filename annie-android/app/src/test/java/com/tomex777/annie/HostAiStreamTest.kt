package com.tomex777.annie

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HostAiStreamTest {
    @Test fun realSseFramesEmitTextIncrementallyAndIgnorePingAndThinking() = runBlocking {
        val frames = listOf(
            "data: {\"type\":\"ping\"}", "",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}", "",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"private\"}}", "",
            "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\" world 🌍\"}}", "",
            "data: {\"type\":\"message_stop\"}", "", "",
        ).joinToString("\n")
        val chunks = mutableListOf<String>()
        assertEquals("Hello world 🌍", consumeAnthropicStream(frames.reader().buffered()) { chunks += it })
        assertEquals(listOf("Hello", " world 🌍"), chunks)
    }

    @Test fun truncatedStreamIsAnError() = runBlocking {
        try { consumeAnthropicStream("data: {\"type\":\"ping\"}\n\n".reader().buffered()) {}; fail("Truncated stream accepted") }
        catch (failure: AnnieError) { assertEquals(AnnieErrorCode.NETWORK_ERROR, failure.code) }
    }

    @Test fun providerErrorIsAnErrorWithoutDisclosingItsRawPayload() = runBlocking {
        try { consumeAnthropicStream("data: {\"type\":\"error\",\"error\":{\"message\":\"private provider detail\"}}\n\n".reader().buffered()) {}; fail("Provider error accepted") }
        catch (failure: AnnieError) {
            assertEquals(AnnieErrorCode.NETWORK_ERROR, failure.code)
            assertFalse(failure.message.contains("private provider detail"))
        }
    }
}
