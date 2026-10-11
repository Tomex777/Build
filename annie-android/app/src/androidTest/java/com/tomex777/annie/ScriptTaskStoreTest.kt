package com.tomex777.annie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriptTaskStoreTest {
    @Test fun taskStatePersistsAndCanBeCancelledWithoutKeepingRuntimeAlive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scriptId = "taskstore-" + System.nanoTime().toString().takeLast(8)
        val id = "job"
        val now = System.currentTimeMillis()
        val entry = ScriptTaskEntry(
            id = id,
            scriptId = scriptId,
            chatId = "chat",
            title = "Processing",
            action = "run-job",
            payloadJson = """{"value":1}""",
            state = ScriptTaskState.QUEUED,
            attempts = 0,
            lastError = null,
            createdAtMillis = now,
            updatedAtMillis = now,
        )

        try {
            ScriptTaskStore.put(context, entry)
            val restored = ScriptTaskStore.get(context, scriptId, id)
            assertNotNull(restored)
            assertEquals(ScriptTaskState.QUEUED, restored?.state)
            assertEquals("""{"value":1}""", restored?.payloadJson)

            assertTrue(ScriptTaskManager.cancel(context, scriptId, id))
            val cancelled = ScriptTaskStore.get(context, scriptId, id)
            assertEquals(ScriptTaskState.CANCELLED, cancelled?.state)
        } finally {
            ScriptTaskStore.remove(context, scriptId, id)
        }
    }
}
