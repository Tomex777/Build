package com.tomex777.annie

import androidx.compose.runtime.mutableStateListOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiTwoRuntimeTest {
    @Test fun strictWrappersPreserveTypesAndStreamOnePersistentMessage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val name = "apitwo" + System.nanoTime().toString().takeLast(8)
        val chat = "$name-chat"
        val previous = ChatHistoryStore.read(context)
        try {
            val folder = workspace.files.createFolder(name)
            File(folder, "manifest.json").writeText("""
                {"packageId":"com.example.$name","displayName":"API2 proof","version":"1.0.0","apiVersion":"2","entryPoint":"main.js",
                 "capabilities":["android.tts","messages"],"permissions":["android.tts.speak","messages.post"]}
            """.trimIndent())
            workspace.files.writeFile(name, "main.js", """
                annie.commands.register({name:'$name', async execute() {
                    const failures=[];
                    try { await annie.android.tts.speak(42); } catch(error) { failures.push(error.code); }
                    try { await annie.android.tts.speak('hello',{unknown:true}); } catch(error) { failures.push(error.code); }
                    try { await annie.messages.send({type:'text',text:'bad'},'ignored'); } catch(error) { failures.push(error.code); }
                    const stream=await annie.messages.stream('Hello');
                    await stream.append(' world'); await stream.finish('!');
                    return annie.messages.text(failures.join(',')+'|'+typeof annie.messages.canvas);
                }});
            """.trimIndent())
            workspace.files.setEnabled(name, true)
            workspace.files.setGrantedPermissions(name, setOf("android.tts.speak", "messages.post"))
            ChatHistoryStore.write(context, previous + ChatSession(chat, mutableStateListOf(ChatEntry(1L, true, "hi"))))
            assertTrue("API2 package did not load: ${workspace.logs()}", workspace.reload().any { it.name == name })
            val response = JSONObject(workspace.execute(name, "/$name", chat, 2L))
            assertEquals("INVALID_ARGUMENT,INVALID_ARGUMENT,INVALID_ARGUMENT|function", response.getString("text"))
            val stored = ChatHistoryStore.read(context).single { it.id == chat }.messages
            assertEquals(2, stored.size)
            assertEquals("Hello world!", stored.last().text)
            assertFalse(JSONObject(stored.last().scriptMessageJson).getBoolean("streaming"))
        } finally {
            runCatching { workspace.files.deleteProject(name) }
            workspace.close()
            ChatHistoryStore.write(context, previous)
        }
    }
}
