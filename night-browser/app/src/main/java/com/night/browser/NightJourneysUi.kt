package com.night.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URI

private val NodeBackground = Color(0xFF131822)
private val JourneyCard = Color(0xFF1B202B)
private val JourneyAccent = Color(0xFFBEB6FF)
private val JourneyWhite = Color(0xFFEBEDF5)
private val JourneyGray = Color(0xFF9CA3B4)

/** True local navigation tree: selecting any node starts a branch from THAT specific visit. */
@Composable
internal fun NightJourneysPanel(
    browser: BrowserController,
    onNew: () -> Unit,
    onOpen: (Journey, JourneyNode) -> Unit,
    onSelect: (Journey) -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color(0xFF0C1017))) {
        Row(
            Modifier.fillMaxWidth().padding(start = 21.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Journeys", color = JourneyWhite, fontSize = 27.sp,
                    fontWeight = FontWeight.SemiBold)
                Text("${browser.store.journeys.size} collections", color = JourneyGray,
                    fontSize = 12.sp)
            }
            FilledIconButton(
                onClick = onNew,
                modifier = Modifier.size(45.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFF29293C),
                    contentColor = JourneyAccent
                )
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "New Journey")
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 15.dp, end = 15.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(browser.store.journeys, key = { it.id }) { journey ->
                var expanded by remember(journey.id) { mutableStateOf(true) }
                val nodes = journey.nodes
                Surface(
                    color = JourneyCard,
                    shape = RoundedCornerShape(19.dp),
                    border = BorderStroke(1.dp, Color(0xFF34384A))
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { expanded = !expanded }
                                .padding(start = 16.dp, top = 16.dp, bottom = 14.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(42.dp)
                                    .background(Color(0xFF353049), RoundedCornerShape(13.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.AccountTree, null, tint = JourneyAccent,
                                    modifier = Modifier.size(23.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(journey.name, color = JourneyWhite,
                                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(3.dp))
                                Text("${nodes.size} visited pages",
                                    color = JourneyGray, fontSize = 12.sp)
                            }
                            Icon(
                                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                tint = JourneyGray
                            )
                        }
                        if (expanded) {
                            HorizontalDivider(color = Color(0xFF303543))
                            if (nodes.isEmpty()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Start browsing in this Journey",
                                        color = JourneyGray, fontSize = 13.sp,
                                        modifier = Modifier.weight(1f))
                                    TextButton(onClick = { onSelect(journey) }) {
                                        Text("Start", color = JourneyAccent)
                                    }
                                }
                            } else {
                                val lookup = nodes.associateBy { it.id }
                                val visible = nodes.takeLast(30)
                                visible.forEach { node ->
                                    var depth = 0
                                    var parent = node.parent
                                    val visited = mutableSetOf<String>()
                                    while (parent != null && depth < 4 && visited.add(parent)) {
                                        depth++
                                        parent = lookup[parent]?.parent
                                    }
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clickable { onOpen(journey, node) }
                                            .padding(start = (14 + depth * 12).dp,
                                                end = 10.dp, top = 9.dp, bottom = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(26.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (depth == 0) {
                                                Box(Modifier.size(8.dp)
                                                    .background(JourneyAccent, CircleShape))
                                            } else {
                                                Text("↳", color = JourneyAccent, fontSize = 21.sp)
                                            }
                                        }
                                        Spacer(Modifier.width(7.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(node.title.ifBlank {
                                                runCatching {
                                                    URI(node.url).host.orEmpty()
                                                }.getOrDefault(node.url)
                                            }, color = JourneyWhite, fontSize = 13.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Spacer(Modifier.height(3.dp))
                                            Text(node.url.removePrefix("https://").removePrefix("www."),
                                                color = JourneyGray, fontSize = 11.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Icon(Icons.Rounded.CallSplit, contentDescription = "Branch here",
                                            tint = JourneyAccent, modifier = Modifier.size(18.dp))
                                    }
                                }
                                if (nodes.size > visible.size) {
                                    Text(
                                        "Showing ${visible.size} most recent visits",
                                        color = JourneyGray,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(start = 17.dp, bottom = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
