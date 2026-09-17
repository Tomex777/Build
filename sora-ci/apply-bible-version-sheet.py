from pathlib import Path

path = Path("sora-overlay/app/src/main/java/com/night/sora/ui/screens/BibleScreen.kt")
text = path.read_text()

old = '''@Composable
private fun TranslationMenu(
    selected: BibleTranslation,
    translations: List<BibleTranslation>,
    onSelected: (BibleTranslation) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(selected.shortLabel, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(17.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 270.dp, max = 330.dp),
        ) {
            translations.forEach { translation ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(translation.name, fontSize = 13.sp, fontWeight = if (translation.id == selected.id) FontWeight.Bold else FontWeight.Medium)
                            Text("${translation.shortLabel} · ${translation.language}", color = SoraMuted, fontSize = 9.sp)
                        }
                    },
                    trailingIcon = { if (translation.id == selected.id) Icon(Icons.Rounded.Check, null, tint = SoraAccent) },
                    onClick = { onSelected(translation); expanded = false },
                )
            }
        }
    }
}
'''

new = '''@Composable
private fun TranslationMenu(
    selected: BibleTranslation,
    translations: List<BibleTranslation>,
    onSelected: (BibleTranslation) -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val visibleTranslations = remember(query, translations) {
        val needle = query.trim()
        if (needle.isBlank()) translations else translations.filter { translation ->
            translation.shortLabel.contains(needle, ignoreCase = true) ||
                translation.name.contains(needle, ignoreCase = true) ||
                translation.language.contains(needle, ignoreCase = true)
        }
    }

    Surface(
        color = SoraSurfaceHigh,
        contentColor = SoraText,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable {
            query = ""
            sheetOpen = true
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected.shortLabel, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
            Spacer(Modifier.width(3.dp))
            Icon(Icons.Rounded.KeyboardArrowDown, "Choose Bible version", modifier = Modifier.size(16.dp))
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            containerColor = SoraSurface,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
            ) {
                Text("Select version", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Choose the translation used across the Bible reader.",
                    color = SoraMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )

                Surface(
                    color = SoraSurfaceHigh,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = SoraMuted, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(9.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = SoraText, fontSize = 14.sp),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (query.isEmpty()) Text("Search versions", color = SoraMuted, fontSize = 14.sp)
                                inner()
                            },
                        )
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Close, "Clear search", tint = SoraMuted, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }

                Text(
                    if (query.isBlank()) "All versions" else "Results · ${visibleTranslations.size}",
                    color = SoraMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 17.dp, bottom = 6.dp),
                )

                if (visibleTranslations.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 38.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.SearchOff, null, tint = SoraMuted, modifier = Modifier.size(32.dp))
                        Text("No versions found", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 9.dp))
                        Text("Try another name or abbreviation.", color = SoraMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        items(visibleTranslations, key = { it.id }) { translation ->
                            val isSelected = translation.id == selected.id
                            Surface(
                                color = if (isSelected) SoraSurfaceHigh else Color.Transparent,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onSelected(translation)
                                    sheetOpen = false
                                },
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(38.dp).background(
                                            if (isSelected) SoraAccent.copy(alpha = .14f) else Color.White.copy(alpha = .045f),
                                            RoundedCornerShape(11.dp),
                                        ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            translation.shortLabel.take(5),
                                            color = if (isSelected) SoraAccent else SoraText,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                        Text(
                                            translation.name,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${translation.shortLabel} · ${translation.language}",
                                            color = SoraMuted,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Rounded.Check, "Selected", tint = SoraAccent, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
'''

count = text.count(old)
if count != 1:
    raise SystemExit(f"TranslationMenu marker mismatch: {count}")

path.write_text(text.replace(old, new, 1))
