package com.tomex777.annie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriptScheduleTest {
    @Test fun scriptCanCreateListDisableEnableAndCancelSchedule() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "scheduleproof" + System.nanoTime().toString().takeLast(8)
        val files = ScriptFiles(context)
        val script = files.createScript(name)
        files.writeFile(
            name,
            script.name,
            """
                |annie.actions.register("scheduled-check", async payload => {
                |  return annie.messages.text("scheduled " + payload.value);
                |});
                |annie.commands.register({
                |  name: "$name",
                |  async execute() {
                |    const created = await annie.schedule.create({
                |      id: "daily-check",
                |      every: "day",
                |      at: "19:00",
                |      action: "scheduled-check",
                |      payload: { value: 7 }
                |    });
                |    const first = await annie.schedule.list();
                |    const disabled = await annie.schedule.disable("daily-check");
                |    const afterDisable = await annie.schedule.list();
                |    const enabled = await annie.schedule.enable("daily-check");
                |    const afterEnable = await annie.schedule.list();
                |    const cancelled = await annie.schedule.cancel("daily-check");
                |    const afterCancel = await annie.schedule.list();
                |    return annie.messages.text(JSON.stringify({
                |      approximate: created.approximate,
                |      firstCount: first.length,
                |      disabled,
                |      disabledState: afterDisable[0].enabled,
                |      enabled,
                |      enabledState: afterEnable[0].enabled,
                |      cancelled,
                |      finalCount: afterCancel.length
                |    }));
                |  }
                |});
            """.trimMargin(),
        )

        val workspace = ScriptWorkspace(context)
        try {
            workspace.reload()
            val result = JSONObject(workspace.execute(name, "/$name", "schedule-test-chat", 1L))
            val payload = JSONObject(result.getString("text"))
            assertTrue(payload.getBoolean("approximate"))
            assertEquals(1, payload.getInt("firstCount"))
            assertTrue(payload.getBoolean("disabled"))
            assertFalse(payload.getBoolean("disabledState"))
            assertTrue(payload.getBoolean("enabled"))
            assertTrue(payload.getBoolean("enabledState"))
            assertTrue(payload.getBoolean("cancelled"))
            assertEquals(0, payload.getInt("finalCount"))
        } finally {
            workspace.close()
            ScriptScheduler.cancel(context, name, "daily-check")
            runCatching { files.deleteProject(name) }
        }
    }

    @Test fun recurringScheduleRejectsSubFifteenMinuteCadence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val error = runCatching {
            ScriptScheduler.create(
                context = context,
                scriptId = "validation",
                chatId = "chat",
                spec = JSONObject()
                    .put("id", "too-fast")
                    .put("every", "5m")
                    .put("action", "check"),
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("at least 15 minutes"))
        ScriptScheduler.cancel(context, "validation", "too-fast")
    }
}
