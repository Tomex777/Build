package com.tomex777.annie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScriptMatchesMessageTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allMultiSourceMatchesRenderAndRouteOwningAction() {
        val data = JSONObject()
            .put("type", "matches")
            .put("title", "Possible matches")
            .put(
                "items",
                JSONArray()
                    .put(JSONObject().put("id", "a").put("title", "Evening Train").put("sourceName", "Source A").put("action", "pick"))
                    .put(JSONObject().put("id", "b").put("title", "Summer Crossing").put("sourceName", "Source B").put("action", "pick"))
                    .put(JSONObject().put("id", "c").put("title", "Moon Harbor").put("sourceName", "Source C").put("action", "pick"))
            )
        var selected = ""

        compose.setContent {
            AnnieTheme {
                ScriptMatchesMessage(data, scriptId = "") { _, payload ->
                    selected = JSONObject(payload).optString("id")
                }
            }
        }

        compose.onNodeWithText("Possible matches").assertIsDisplayed()
        compose.onNodeWithText("Evening Train").assertIsDisplayed()
        compose.onNodeWithText("Summer Crossing").assertIsDisplayed()
        compose.onNodeWithText("Moon Harbor").assertIsDisplayed()
        compose.onNodeWithText("Source A").assertIsDisplayed()
        compose.onNodeWithText("Source B").assertIsDisplayed()
        compose.onNodeWithText("Source C").assertIsDisplayed()
        compose.onNodeWithTag("script_match_2").performClick()
        compose.runOnIdle { assertEquals("c", selected) }
    }
}
