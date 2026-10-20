package com.night.browser.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.night.browser.Bookmark
import com.night.browser.Journey
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Composable integration point for the browser shell (Agent 1/2 owns MainActivity).
 *
 * NO provider call until a user selects individual records and confirms EACH request.
 * The tab's real privacy state MUST be passed; do not default privateTab to false.
 * Both the key and selected records are transient Compose state (never persisted).
 */
@Composable
fun UniversalSearchPanel(
    bookmarks: List<Bookmark>,
    journeys: List<Journey>,
    privateTab: Boolean,
    onBack: () -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val provider = remember { UniversalSearchProvider() }
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var temporaryKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("gpt-4.1-mini") }
    var localFilter by remember { mutableStateOf("") }
    var confirmRequest by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<AiSearchOutcome?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    val localIndex = remember(bookmarks, journeys) {
        SearchSourceCatalog.index(bookmarks, journeys)
    }
    val approved = remember(localIndex, localFilter) {
        SearchSourceCatalog.match(localIndex, localFilter)
    }
    val selected = remember { mutableStateListOf<String>() }

    // A record's title/domain could change after a tick; require selection
    // again when the actual catalog changes, rather than consent by stale ID.
    LaunchedEffect(localIndex) {
        // Abort an in-flight answer if the approved records changed underneath
        // the user; a later result must never masquerade as the current catalog.
        if (selected.isNotEmpty() || busy) {
            generation++
            job?.cancel()
            job = null
            busy = false
            outcome = null
            showResult = false
        }
        selected.clear()
        confirmRequest = false
    }

    fun invalidateDisplayedAnswer() {
        outcome = null
        showResult = false
    }

    fun revoke() {
        generation++
        job?.cancel()
        job = null
        temporaryKey = ""
        selected.clear()
        confirmRequest = false
        outcome = null
        showResult = false
        busy = false
    }

    DisposableEffect(Unit) {
        onDispose {
            // A running provider request cannot retract an already transmitted prompt.
            job?.cancel()
        }
    }

    LaunchedEffect(privateTab) {
        if (privateTab) revoke()
    }

    Column(modifier.fillMaxSize().background(colors.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(
                "Universal Search",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            TextButton(onClick = { revoke() }) { Text("Clear") }
        }
        HorizontalDivider()

        if (privateTab) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("AI search is unavailable in private tabs. Nothing has been sent.")
            }
            return@Column
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text(
                "Ask about selected pages",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "Only the pages you tick below can be used. The model receives your question, selected page titles and website domains—not page contents, full URLs, cookies or all your history.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            SearchEntryField(
                label = "What are you looking for?",
                placeholder = "Ask about a saved or visited page",
                value = question,
                onValueChange = {
                    if (!busy && it.length <= 500) {
                        question = it
                        invalidateDisplayedAnswer()
                    }
                },
                enabled = !busy,
            )
            Spacer(Modifier.height(8.dp))
            SearchEntryField(
                label = "Temporary OpenAI API key",
                placeholder = "Paste key for this session",
                value = temporaryKey,
                onValueChange = {
                    if (!busy) {
                        temporaryKey = it.replace("\n", "")
                        invalidateDisplayedAnswer()
                    }
                },
                secret = true,
                enabled = !busy,
            )
            Spacer(Modifier.height(8.dp))
            SearchEntryField(
                label = "Model",
                placeholder = "OpenAI model ID",
                value = model,
                onValueChange = {
                    if (!busy && it.length <= 80) {
                        model = it
                        invalidateDisplayedAnswer()
                    }
                },
                enabled = !busy,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Key is never saved. OpenAI may bill your account. Nothing is sent without a separate confirmation.",
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (showResult) {
            Column(Modifier.weight(1f).padding(horizontal = 18.dp, vertical = 8.dp)) {
                TextButton(onClick = { showResult = false }) { Text("Change selected pages") }
                when (val current = outcome) {
                    is AiSearchOutcome.Answer -> {
                        Text("Based only on approved page titles and domains, not page contents.",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Text(current.text, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(14.dp))
                        Text("Cited pages", style = MaterialTheme.typography.titleSmall)
                        current.citations.forEach { source ->
                            TextButton(
                                onClick = { onOpenSource(source.url) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    source.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    is AiSearchOutcome.Unavailable ->
                        Text(current.explanation, color = colors.error)
                    null -> Unit
                }
            }
        } else {
            SearchEntryField(
                label = "Filter saved pages · on device",
                placeholder = "Search titles or websites",
                value = localFilter,
                onValueChange = { if (!busy && it.length <= 120) localFilter = it },
                enabled = !busy,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Select pages · " + selected.size + " of " + UniversalSearchPolicy.MAX_SHARED_SOURCES + " max",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )
            if (approved.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (localIndex.isEmpty())
                            "No eligible saved or visited pages. Open and bookmark a public HTTPS page first."
                        else "No pages match this local filter.",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    items(approved, key = { it.id }) { source ->
                        val isSelected = selected.contains(source.id)
                        val canAdd = isSelected || selected.size < UniversalSearchPolicy.MAX_SHARED_SOURCES
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = canAdd && !busy) {
                                    if (isSelected) selected.remove(source.id) else selected.add(source.id)
                                    invalidateDisplayedAnswer()
                                }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    if (!busy) {
                                        if (isSelected) selected.remove(source.id)
                                        else if (canAdd) selected.add(source.id)
                                        invalidateDisplayedAnswer()
                                    }
                                },
                                enabled = canAdd && !busy,
                            )
                            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    source.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    source.category + " · " + UniversalSearchPolicy.safeHost(source.url).orEmpty(),
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Asking the provider…")
                }
            } else {
                Button(
                    onClick = { confirmRequest = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = question.isNotBlank() && temporaryKey.isNotBlank() &&
                        selected.isNotEmpty(),
                ) { Text("Ask AI about selected pages") }
                if (!showResult && outcome is AiSearchOutcome.Unavailable) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        (outcome as AiSearchOutcome.Unavailable).explanation,
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (confirmRequest) {
        AlertDialog(
            onDismissRequest = { confirmRequest = false },
            title = { Text("Share selected pages?") },
            text = {
                val chosen = localIndex.filter { selected.contains(it.id) }
                    .take(UniversalSearchPolicy.MAX_SHARED_SOURCES)
                // The disclosure must show EXACTLY the context about to be shared.
                // Hiding extra sources behind "+ N more" would not be informed consent.
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Send this question and the page titles/domains below to OpenAI once? " +
                            "The full URLs, page contents, cookies and other history stay on your device. " +
                            "Once sent, the request cannot be retracted."
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Question: " + question.trim())
                    Spacer(Modifier.height(8.dp))
                    chosen.forEach { source ->
                        Text(
                            "• " + source.title.trim().take(160) +
                                " (" + UniversalSearchPolicy.safeHost(source.url).orEmpty() + ")",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRequest = false
                    val approvedRequest = UniversalSearchPolicy.prepare(
                        question = question,
                        eligible = localIndex,
                        selectedIds = selected.toSet(),
                        explicitlyConfirmed = true,
                        privateTab = privateTab,
                    )
                    when (approvedRequest) {
                        is SearchPreparation.Rejected ->
                            outcome = AiSearchOutcome.Unavailable(approvedRequest.reason)
                        is SearchPreparation.Ready -> {
                            val requestGeneration = ++generation
                            val requestKey = temporaryKey
                            val requestModel = model
                            busy = true
                            outcome = null
                            showResult = false
                            job = scope.launch {
                                val answer = provider.answer(approvedRequest, requestKey, requestModel)
                                if (requestGeneration == generation) {
                                    outcome = answer
                                    showResult = answer is AiSearchOutcome.Answer
                                    busy = false
                                }
                            }
                        }
                    }
                }) { Text("Share and ask") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRequest = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SearchEntryField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    secret: Boolean = false,
    enabled: Boolean = true,
    placeholder: String = "",
) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Box(
            Modifier.fillMaxWidth().height(48.dp)
                .background(colors.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                visualTransformation = if (secret) PasswordVisualTransformation()
                    else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text
                ),
                cursorBrush = SolidColor(colors.primary),
                decorationBox = { field ->
                    if (value.isEmpty()) {
                        Text(placeholder, color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    field()
                },
            )
        }
    }
}
