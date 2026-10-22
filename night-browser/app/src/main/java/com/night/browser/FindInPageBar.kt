package com.night.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.mozilla.geckoview.GeckoSession

/**
 * Real Gecko find-in-page, not a synthetic DOM injection or a keyword suggestion.
 * Each search is scoped to the live active tab and held only in Compose memory.
 */
@Composable
internal fun FindInPageBar(tab: BrowserTab, onClose: () -> Unit) {
    val finder = tab.session.finder
    var query by remember(tab.id) { mutableStateOf("") }
    var current by remember(tab.id) { mutableIntStateOf(0) }
    var total by remember(tab.id) { mutableIntStateOf(-1) }
    var failed by remember(tab.id) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    DisposableEffect(finder) {
        finder.setDisplayFlags(GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL)
        onDispose { finder.clear() }
    }

    fun findNext(backwards: Boolean) {
        if (query.isBlank()) return
        finder.find(null, if (backwards) GeckoSession.FINDER_FIND_BACKWARDS
            else GeckoSession.FINDER_FIND_FORWARD).accept({ result ->
            if (result.searchString == query) {
                current = result.current
                total = result.total
                failed = false
            }
        }, { _ -> failed = true })
    }

    LaunchedEffect(query, tab.id) {
        if (query.isBlank()) {
            finder.clear()
            current = 0
            total = -1
            failed = false
        } else {
            val searched = query
            current = 0
            total = -1
            failed = false
            delay(160)
            finder.find(searched, GeckoSession.FINDER_FIND_FORWARD).accept({ result ->
                if (query == searched) {
                    current = result.current
                    total = result.total
                    failed = false
                }
            }, { _ ->
                if (query == searched) failed = true
            })
        }
    }

    Row(
        Modifier.fillMaxWidth()
            .background(Color(0xFF171D27), RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .padding(start = 12.dp, end = 3.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BasicTextField(
            value = query,
            onValueChange = { query = it.take(180) },
            modifier = Modifier.weight(1f).height(44.dp),
            singleLine = true,
            textStyle = TextStyle(color = Color(0xFFF3F5F9), fontSize = 15.sp),
            cursorBrush = SolidColor(Color(0xFFB9BEFF)),
            keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                findNext(false)
                keyboard?.hide()
            }),
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isBlank()) Text("Find on this page", color = Color(0xFFA6AFBD),
                        fontSize = 14.sp)
                    inner()
                }
            }
        )
        Text(
            text = when {
                query.isBlank() -> ""
                failed -> "Unavailable"
                total == 0 -> "0 matches"
                total < 0 -> "…"
                else -> "$current/$total"
            },
            color = Color(0xFFA6AFBD),
            fontSize = 12.sp
        )
        IconButton(onClick = { findNext(true) }, modifier = Modifier.size(42.dp),
            enabled = query.isNotBlank()) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Previous match", tint = Color(0xFFF3F5F9))
        }
        IconButton(onClick = { findNext(false) }, modifier = Modifier.size(42.dp),
            enabled = query.isNotBlank()) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Next match", tint = Color(0xFFF3F5F9))
        }
        IconButton(onClick = {
            finder.clear()
            keyboard?.hide()
            focus.clearFocus()
            onClose()
        }, modifier = Modifier.size(42.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Close find in page",
                tint = Color(0xFFF3F5F9))
        }
    }
}
