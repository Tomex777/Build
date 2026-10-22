package com.tomex777.annie

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AnnieAboutContent(onOpenSource: (String) -> Unit) {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    var showLicense by remember { mutableStateOf(false) }
    var showBrowserLicense by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)
        .testTag("annie_about"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Annie", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(version, fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFF9CB2CC))
        Text("Open-source software", fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 12.dp))
        Text("libVLC 3.7.4 · VideoLAN", fontSize = 13.sp)
        TextButton(onClick = { showLicense = true }) { Text("License") }
        TextButton(onClick = { onOpenSource("https://code.videolan.org/videolan/libvlcjni") }) { Text("libVLC source") }
        Text("Based on Omni Browser by RebelRoot (github.com/REBEL-ROOT/omni-browser)",
            fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        TextButton(onClick = { onOpenSource("https://github.com/REBEL-ROOT/omni-browser") }) {
            Text("Original browser source")
        }
        TextButton(onClick = { showBrowserLicense = true }) { Text("Browser license (GPLv3)") }
    }
    if (showLicense) {
        val license = remember { context.assets.open("licenses/libvlc-LGPL-2.1.txt").bufferedReader().use { it.readText() } }
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text("GNU LGPL 2.1") },
            text = { Text(license, fontSize = 12.sp, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text("Close") } },
        )
    }
    if (showBrowserLicense) {
        val browserLicense = remember {
            context.assets.open("licenses/omni-GPL-3.0.txt").bufferedReader().use { it.readText() }
        }
        AlertDialog(
            onDismissRequest = { showBrowserLicense = false },
            title = { Text("Omni Browser source license · GPLv3") },
            text = {
                Text(browserLicense, fontSize = 12.sp,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
            },
            confirmButton = { TextButton(onClick = { showBrowserLicense = false }) { Text("Close") } },
        )
    }
}
