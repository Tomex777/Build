package com.tomex.aether

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchSheet(
    currentQuery: String,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember(currentQuery) { mutableStateOf(currentQuery) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AetherSurface,
        contentColor = AetherText,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
            Text("Search this feed", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Search stays inside the subreddits in the selected category.",
                color = AetherMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("meme, character, topic…") },
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (currentQuery.isNotBlank()) {
                    OutlinedButton(onClick = { onClear(); onDismiss() }) { Text("Clear") }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSearch(query); onDismiss() },
                    enabled = query.trim().isNotBlank(),
                ) { Text("Search") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VibeSheet(
    loading: Boolean,
    result: VibeResult?,
    onMatch: (String) -> Unit,
    onApply: (VibeResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var mood by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AetherSurface,
        contentColor = AetherText,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 30.dp)) {
            Text("Vibe Match", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Describe what you feel like seeing. Aether picks from your existing category pills.",
                color = AetherMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = mood,
                onValueChange = { mood = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("tired and need something stupid…") },
                minLines = 2,
                maxLines = 4,
            )
            Button(
                onClick = { onMatch(mood) },
                enabled = mood.trim().isNotBlank() && !loading,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Match my vibe")
            }

            result?.let {
                HorizontalDivider(Modifier.padding(vertical = 18.dp), color = Color(0xFF292929))
                Text(it.category, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (it.reason.isNotBlank()) {
                    Text(it.reason, color = AetherMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                }
                Button(
                    onClick = { onApply(it); onDismiss() },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                ) { Text("Go to ${it.category}") }
            }
        }
    }
}


@Composable
internal fun RedditSetupState(onSetup: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Text("Connect Reddit", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Aether needs the Client ID from your Reddit installed app to load feeds and comments.",
                color = AetherMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 7.dp, bottom = 14.dp),
            )
            Button(onClick = onSetup) { Text("Set up Reddit") }
        }
    }
}
