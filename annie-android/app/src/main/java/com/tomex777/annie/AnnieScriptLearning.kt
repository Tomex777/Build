package com.tomex777.annie

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LearnText = Color(0xFFEEF5FF)
private val LearnMuted = Color(0xFF9CB2CC)
private val LearnBlue = Color(0xFF42B9F5)
private val LearnCard = Color(0xFF10243A)

@Composable
internal fun AnnieScriptLearningContent(
    onCreateScript: () -> Unit,
    onExtensions: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 720.dp).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)
            .testTag("script_learning"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Learn scripting", color = LearnText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Start with one small command. Annie runs scripts in a controlled JavaScript environment and renders their results as native chat messages.",
            color = LearnMuted, fontSize = 13.sp)
        Surface(color = LearnCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF294562))) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your first command", color = LearnText, fontWeight = FontWeight.SemiBold)
                Text("Register a command named /hello, then return a chat message when it runs.", color = LearnMuted, fontSize = 12.sp)
                Text(
                    "annie.commands.register({\n  name: \"hello\",\n  async execute(ctx) {\n    return annie.messages.text(\"Hello\");\n  }\n});",
                    color = Color(0xFFD2E8FF), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF081522), RoundedCornerShape(9.dp)).padding(12.dp)
                        .testTag("script_learning_first_command"),
                )
                LearnAction("Create a script", onCreateScript, "script_learning_create")
            }
        }
        LearnSection("Return Annie cards", "Use the message helpers to return text, media cards, options and other native message types. Annie renders and handles those cards.")
        LearnSection("Store data and use the network", "Use package storage for saved state. Network access requires the package to declare its network capability and permission, and for you to grant that permission.")
        LearnSection("Use Android features safely", "The allowlisted bridge includes device info, text to speech, speech recognition, OCR, document picking, media inspection and package-owned notifications. Each feature requires its declared permission; scripts do not get unrestricted Android objects or filesystem access.")
        LearnSection("Packages and extensions", "A package can add slash commands, media sources, services, actions, assets and capabilities. Imported packages start disabled, and you can review permissions before enabling them.")
        LearnSection("Scheduling and tasks", "Packages can declare background work for host-managed scheduling. Annie owns the worker lifecycle and applies the package's declared capabilities.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LearnAction("Browse extensions", onExtensions, "script_learning_extensions")
            LearnAction("Create script", onCreateScript, "script_learning_create_footer")
        }
        Text("The API tab remains the detailed reference. Use AI spec in Script Studio to export Annie's current scripting specification for an AI coding tool.",
            color = LearnMuted, fontSize = 11.sp)
    }
}

@Composable
private fun LearnSection(title: String, body: String) {
    Surface(color = LearnCard, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFF213B56))) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = LearnText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = LearnMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LearnAction(label: String, onClick: () -> Unit, tag: String) {
    Surface(
        color = Color(0xFF16446A), shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, LearnBlue.copy(alpha = .45f)),
        modifier = Modifier.clickable(onClick = onClick).testTag(tag),
    ) {
        Text(label, color = LearnText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}
