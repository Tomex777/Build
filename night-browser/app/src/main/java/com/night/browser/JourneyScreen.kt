package com.night.browser

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val journeyInk = Color(0xFF0C1017)
private val journeyCard = Color(0xFF171D27)
private val journeyBorder = Color(0xFF2B3441)
private val journeyText = Color(0xFFF3F5F9)
private val journeySubtle = Color(0xFF9EAABC)
private val journeyAccent = Color(0xFFB9BEFF)

private data class EditTarget(
    val journeyId: String, val nodeId: String?, val original: String
)
private data class DeleteTarget(
    val journeyId: String, val nodeId: String?, val title: String,
    val singleVisit: Boolean = false
)

/** Native, branching Journey view. No generated history, AI or network calls. */
@Composable
fun JourneyScreen(
    store: JourneyStore,
    onNew: () -> Unit,
    onResume: (Journey, JourneyNode?) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val expanded = remember { mutableStateListOf<String>() }
    // Branch collapse is scoped per Journey; searching temporarily reveals
    // matching descendants without erasing the user's expansion choices.
    val collapsedBranches = remember { mutableStateMapOf<String, Set<String>>() }
    LaunchedEffect(store.journeys.map { it.id }) {
        val validIds = store.journeys.mapTo(HashSet()) { it.id }
        expanded.retainAll(validIds)
        collapsedBranches.keys.retainAll(validIds)
        if (expanded.isEmpty()) store.journeys.firstOrNull()?.let { expanded.add(it.id) }
    }
    var edit by remember { mutableStateOf<EditTarget?>(null) }
    var delete by remember { mutableStateOf<DeleteTarget?>(null) }
    var merge by remember { mutableStateOf<Journey?>(null) }
    val context = LocalContext.current
    var exportPayload by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = exportPayload
        if (uri != null && payload != null) {
            runCatching {
                checkNotNull(context.contentResolver.openOutputStream(uri)) {
                    "Unable to open the destination file"
                }.bufferedWriter().use { it.write(payload) }
            }
        }
        exportPayload = null
    }

    Column(Modifier.fillMaxSize().background(journeyInk)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, top = 19.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Journeys", fontSize = 27.sp, fontWeight = FontWeight.SemiBold,
                color = journeyText, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                store.changeJourneyRecording(!store.journeyRecordingEnabled)
            }) {
                Text(
                    if (store.journeyRecordingEnabled) "Pause" else "Resume",
                    color = if (store.journeyRecordingEnabled) journeySubtle else journeyAccent
                )
            }
            IconButton(onClick = onNew) {
                Icon(Icons.Rounded.Add, contentDescription = "Create Journey", tint = journeyText)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .background(journeyCard, RoundedCornerShape(14.dp))
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = journeySubtle, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                cursorBrush = SolidColor(journeyAccent),
                textStyle = TextStyle(color = journeyText, fontSize = 14.sp),
                decorationBox = { inner ->
                    if (query.isBlank()) Text("Search pages and branches", color = journeySubtle,
                        fontSize = 14.sp)
                    inner()
                },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false)
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Close, "Clear search", tint = journeySubtle,
                        modifier = Modifier.size(17.dp))
                }
            }
        }
        if (!store.journeyRecordingEnabled) {
            Text(
                "New visits are not being added to Journeys.",
                color = journeySubtle,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 19.dp, vertical = 11.dp)
            )
        } else {
            Spacer(Modifier.height(10.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            store.journeys.forEach { journey ->
                val collapsed = collapsedBranches[journey.id].orEmpty()
                val matched = JourneyGraph.rows(journey, query, collapsed)
                val show = query.isBlank() || matched.isNotEmpty() ||
                    journey.name.contains(query, ignoreCase = true)
                if (!show) return@forEach
                val isExpanded = journey.id in expanded || query.isNotBlank()
                item(key = "journey-${journey.id}") {
                    Surface(
                        color = journeyCard, shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, journeyBorder)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (journey.id in expanded) expanded.remove(journey.id)
                                else expanded.add(journey.id)
                            }.padding(start = 12.dp, top = 9.dp, bottom = 9.dp, end = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.AccountTree, null, tint = journeyAccent,
                                modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(journey.name, color = journeyText, fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Text("${journey.nodes.size} pages",
                                    color = journeySubtle, fontSize = 12.sp)
                            }
                            Icon(if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = journeySubtle)
                            var actions by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { actions = true }) {
                                    Icon(Icons.Rounded.MoreVert, "Actions for ${journey.name}", tint = journeySubtle)
                                }
                                DropdownMenu(
                                    expanded = actions, onDismissRequest = { actions = false },
                                    containerColor = journeyCard
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Continue") },
                                        onClick = {
                                            actions = false
                                            onResume(journey, journey.nodes.lastOrNull())
                                        }, leadingIcon = { Icon(Icons.Rounded.ArrowOutward, null) })
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            actions = false
                                            edit = EditTarget(journey.id, null, journey.name)
                                        }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                                    if (store.journeys.size > 1) {
                                        DropdownMenuItem(
                                            text = { Text("Merge into…") },
                                            onClick = { actions = false; merge = journey },
                                            leadingIcon = { Icon(Icons.Rounded.AccountTree, null) })
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Export JSON") },
                                        onClick = {
                                            actions = false
                                            store.exportJourneyJson(journey.id)?.let { payload ->
                                                exportPayload = payload
                                                val filename = journey.name.lowercase()
                                                    .replace(Regex("[^a-z0-9-]+"), "-")
                                                    .trim('-').take(40).ifBlank { "journey" }
                                                exportLauncher.launch("NIGHT-$filename.json")
                                            }
                                        }, leadingIcon = { Icon(Icons.Rounded.IosShare, null) })
                                    DropdownMenuItem(
                                        text = { Text("Delete Journey") },
                                        onClick = {
                                            actions = false
                                            delete = DeleteTarget(journey.id, null, journey.name)
                                        }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) })
                                }
                            }
                        }
                    }
                }
                if (isExpanded) {
                    if (journey.nodes.isEmpty()) {
                        item(key = "empty-${journey.id}") {
                            Text("Open a page in this Journey to create its first branch.",
                                color = journeySubtle, fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    .clickable { onResume(journey, null) })
                        }
                    } else {
                        val visible = if (query.isBlank() ||
                            journey.name.contains(query, ignoreCase = true)
                        ) JourneyGraph.rows(journey, collapsedIds = if (query.isBlank()) collapsed else emptySet()) else matched
                        items(visible, key = { "node-${it.node.id}" }) { row ->
                            JourneyNodeRow(
                                row = row,
                                isCollapsed = row.node.id in collapsed && query.isBlank(),
                                onToggleBranch = {
                                    val previous = collapsedBranches[journey.id].orEmpty()
                                    collapsedBranches[journey.id] =
                                        if (row.node.id in previous) previous - row.node.id
                                        else previous + row.node.id
                                },
                                onClick = { onResume(journey, row.node) },
                                onRename = {
                                    edit = EditTarget(journey.id, row.node.id,
                                        row.node.title.ifBlank { row.node.url })
                                },
                                onDelete = { singleVisit ->
                                    delete = DeleteTarget(journey.id, row.node.id,
                                        row.node.title.ifBlank { row.node.url }, singleVisit)
                                }
                            )
                        }
                    }
                }
                item(key = "spacer-${journey.id}") { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    edit?.let { target ->
        var title by remember(target) { mutableStateOf(target.original) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (target.nodeId == null) "Rename Journey" else "Rename page") },
            text = {
                Box(Modifier.fillMaxWidth().background(journeyInk, RoundedCornerShape(12.dp))
                    .padding(12.dp)) {
                    BasicTextField(
                        value = title, onValueChange = { title = it.take(180) },
                        singleLine = true, textStyle = TextStyle(color = journeyText, fontSize = 16.sp),
                        cursorBrush = SolidColor(journeyAccent)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = {
                        if (target.nodeId == null) store.renameJourney(target.journeyId, title)
                        else store.renameNode(target.journeyId, target.nodeId, title)
                        edit = null
                    }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } },
            containerColor = journeyCard
        )
    }
    delete?.let { target ->
        AlertDialog(
            onDismissRequest = { delete = null },
            title = {
                Text(when {
                    target.nodeId == null -> "Delete Journey?"
                    target.singleVisit -> "Delete visit?"
                    else -> "Delete branch?"
                })
            },
            text = {
                Text(when {
                    target.nodeId == null ->
                        "Remove ${target.title} and all its saved visit nodes? This cannot be undone."
                    target.singleVisit ->
                        "Remove only this visit? Descendants stay in the Journey and are linked to its parent."
                    else -> "Remove this page and all branches below it? This cannot be undone."
                })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (target.nodeId == null) store.deleteJourney(target.journeyId)
                    else if (target.singleVisit) store.deleteVisit(target.journeyId, target.nodeId)
                    else store.deleteSubtree(target.journeyId, target.nodeId)
                    delete = null
                }) { Text("Delete", color = Color(0xFFFFA7A7)) }
            },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } },
            containerColor = journeyCard
        )
    }
    merge?.let { source ->
        AlertDialog(
            onDismissRequest = { merge = null },
            title = { Text("Merge ${source.name} into…") },
            text = {
                Column {
                    Text("Recent pages are kept up to the 600-node Journey limit.",
                        fontSize = 13.sp, color = journeySubtle)
                    Spacer(Modifier.height(12.dp))
                    store.journeys.filterNot { it.id == source.id }.forEach { destination ->
                        TextButton(
                            onClick = {
                                store.mergeJourneys(source.id, destination.id)
                                if (destination.id !in expanded) expanded.add(destination.id)
                                merge = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(destination.name, maxLines = 2,
                            overflow = TextOverflow.Ellipsis) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { merge = null }) { Text("Cancel") } },
            containerColor = journeyCard
        )
    }
}

@Composable
private fun JourneyNodeRow(
    row: JourneyRow,
    isCollapsed: Boolean,
    onToggleBranch: () -> Unit,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: (Boolean) -> Unit
) {
    var menu by remember(row.node.id) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .background(journeyCard, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 5.dp, bottom = 5.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shownDepth = row.depth.coerceAtMost(6)
        Canvas(Modifier.width((26 + shownDepth * 15).dp).height(44.dp)) {
            val x0 = 9.dp.toPx()
            val step = 15.dp.toPx()
            val middle = size.height * 0.5f
            val width = 1.4.dp.toPx()
            val stem = journeySubtle.copy(alpha = 0.47f)
            val continuation = row.ancestorContinues.takeLast((shownDepth - 1).coerceAtLeast(0))
            continuation.forEachIndexed { level, continues ->
                if (continues) {
                    val x = x0 + level * step
                    drawLine(stem, Offset(x, 0f), Offset(x, size.height), width)
                }
            }
            val nodeX = x0 + shownDepth * step
            if (shownDepth > 0) {
                val parentX = nodeX - step
                drawLine(stem, Offset(parentX, 0f), Offset(parentX, middle), width)
                if (row.hasNextSibling) {
                    drawLine(stem, Offset(parentX, middle), Offset(parentX, size.height), width)
                }
                drawLine(stem, Offset(parentX, middle), Offset(nodeX, middle),
                    width, cap = StrokeCap.Round)
            }
            drawCircle(
                color = if (row.matchesSearch) journeyAccent else journeySubtle,
                radius = if (shownDepth == 0) 4.dp.toPx() else 3.dp.toPx(),
                center = Offset(nodeX, middle)
            )
        }
        Spacer(Modifier.width(3.dp))
        Column(Modifier.weight(1f)) {
            Text(row.node.title.ifBlank { row.node.url },
                color = journeyText, fontSize = 14.sp,
                fontWeight = if (row.matchesSearch) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.node.url, color = journeySubtle, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (row.hasChildren) {
            IconButton(onClick = onToggleBranch, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (isCollapsed) Icons.Rounded.ChevronRight else Icons.Rounded.ExpandMore,
                    contentDescription = if (isCollapsed) "Expand branch" else "Collapse branch",
                    tint = journeySubtle,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.MoreVert, "Actions for ${row.node.title.ifBlank { row.node.url }}", tint = journeySubtle,
                    modifier = Modifier.size(19.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false },
                containerColor = journeyCard) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menu = false; onRename() },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                DropdownMenuItem(
                    text = { Text("Delete visit only") },
                    onClick = { menu = false; onDelete(true) },
                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) })
                DropdownMenuItem(
                    text = { Text("Delete entire branch") },
                    onClick = { menu = false; onDelete(false) },
                    leadingIcon = { Icon(Icons.Rounded.AccountTree, null) })
            }
        }
    }
}
