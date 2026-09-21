package com.example.whatsapp.presentation.chatscreen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.data.browser.NightBrowserSpec
import com.example.whatsapp.presentation.browser.NightBrowserActivity
import com.example.whatsapp.presentation.browser.NightBrowserWebView
import com.example.whatsapp.presentation.browser.rememberNightBrowserController

private val BrowserBubble = Color(0xFF242625)
private val BrowserToolbar = Color(0xFF20282C)
private val BrowserText = Color(0xFFECEDEE)
private val BrowserMuted = Color(0xFF9EA7AB)
private val BrowserAccent = Color(0xFF25D366)

@Composable
fun NightBrowserMessageBubble(
    messageId: String,
    spec: NightBrowserSpec,
    time: String,
    sourceLabel: String = "",
    onAction: (messageId: String, actionId: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val safeSpec = remember(spec) { spec.sanitized() }
    val controller = rememberNightBrowserController()

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 350.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 5.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp,
                    )
                )
                .background(BrowserBubble)
                .padding(7.dp)
                .semantics {
                    contentDescription = "Browser message " + safeSpec.sessionId
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = BrowserToolbar,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = BrowserText,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(9.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = safeSpec.title,
                        color = BrowserText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = sourceLabel.ifBlank {
                            browserHost(controller.currentUrl.ifBlank { safeSpec.initialUrl })
                        },
                        color = BrowserMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (controller.isLoading) {
                    Text(
                        text = controller.progress.coerceIn(0, 100).toString() + "%",
                        color = BrowserMuted,
                        fontSize = 9.sp,
                    )
                }
            }

            if (controller.isLoading) {
                LinearProgressIndicator(
                    progress = { controller.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = BrowserAccent,
                    trackColor = BrowserToolbar,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp)
                    .padding(top = 5.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.White),
            ) {
                NightBrowserWebView(
                    spec = safeSpec,
                    controller = controller,
                    modifier = Modifier.fillMaxWidth().height(270.dp),
                )

                controller.errorText?.let { error ->
                    Text(
                        text = error,
                        color = BrowserText,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                            .background(
                                BrowserToolbar.copy(alpha = 0.94f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                    )
                }
            }

            controller.blockedUrl?.let {
                Text(
                    text = "Blocked navigation outside this session",
                    color = Color(0xFFFF8791),
                    fontSize = 9.sp,
                    modifier = Modifier.padding(start = 3.dp, top = 5.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = controller::goBack,
                        enabled = controller.canGoBack(),
                        modifier = Modifier
                            .size(40.dp)
                            .semantics {
                                contentDescription = "Inline browser back"
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                            tint = if (controller.canGoBack()) BrowserText else BrowserMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    IconButton(
                        onClick = controller::reload,
                        modifier = Modifier
                            .size(40.dp)
                            .semantics {
                                contentDescription = "Inline browser reload"
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = BrowserText,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    safeSpec.verifyActionId?.let { actionId ->
                        Surface(
                            color = BrowserToolbar,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .semantics {
                                    contentDescription = "Verify browser session"
                                },
                            onClick = {
                                onAction(messageId, actionId)
                            },
                        ) {
                            Text(
                                text = safeSpec.verifyLabel,
                                color = BrowserAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 8.dp,
                                ),
                            )
                        }

                        Spacer(modifier = Modifier.width(5.dp))
                    }

                    IconButton(
                        onClick = {
                            context.startActivity(
                                NightBrowserActivity.createIntent(
                                    context = context,
                                    spec = safeSpec,
                                )
                            )
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .semantics {
                                contentDescription = "Expand browser"
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = null,
                            tint = BrowserText,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }

            Text(
                text = time,
                color = BrowserMuted,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 1.dp, end = 2.dp),
            )
        }
    }
}

private fun browserHost(url: String): String =
    runCatching { Uri.parse(url).host.orEmpty() }
        .getOrDefault("")
        .ifBlank { "Web" }
