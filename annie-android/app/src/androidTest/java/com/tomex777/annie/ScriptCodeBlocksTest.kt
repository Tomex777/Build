package com.tomex777.annie

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScriptCodeBlocksTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun codeBlockRendersWithNativeMonospaceAndCopiesExactSource() {
        val value = "const n = 2;\nconsole.log(n);"
        compose.setContent {
            AnnieTheme {
                ScriptCodeBlockMessage(
                    JSONObject().put("type", "code").put("language", "javascript")
                        .put("title", "JS example").put("code", value)
                )
            }
        }
        compose.onNodeWithTag("script_code_message").assertIsDisplayed()
        compose.onNodeWithTag("script_code_content").assertIsDisplayed()
        compose.onNodeWithTag("script_code_copy").performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(value, clipboard.primaryClip?.getItemAt(0)?.text.toString())
    }

    @Test fun copyBlockRendersAndCopiesTextWithoutExecutingIt() {
        val value = "https://example.com/?a=1&b=2"
        compose.setContent {
            AnnieTheme {
                ScriptCopyBlockMessage(
                    JSONObject().put("type", "copy").put("title", "Share link").put("text", value)
                )
            }
        }
        compose.onNodeWithTag("script_copy_message").assertIsDisplayed()
        compose.onNodeWithTag("script_copy_action").performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(value, clipboard.primaryClip?.getItemAt(0)?.text.toString())
    }
}
