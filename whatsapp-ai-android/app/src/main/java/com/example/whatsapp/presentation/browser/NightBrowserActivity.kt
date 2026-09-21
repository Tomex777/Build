package com.example.whatsapp.presentation.browser

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.data.browser.NightBrowserSpec
import com.example.whatsapp.data.browser.NightBrowserSpecCodec
import com.example.whatsapp.ui.theme.WhatsappTheme

private val BrowserBackground = ComposeColor(0xFF0B141A)
private val BrowserChrome = ComposeColor(0xFF172126)
private val BrowserPanel = ComposeColor(0xFF202C33)
private val BrowserText = ComposeColor(0xFFE9EDEF)
private val BrowserMuted = ComposeColor(0xFF8696A0)
private val BrowserAccent = ComposeColor(0xFF25D366)

class NightBrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val spec = NightBrowserSpecCodec.decode(
            intent.getStringExtra(EXTRA_SPEC).orEmpty()
        )
        if (spec == null) {
            finish()
            return
        }

        enableEdgeToEdge()
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK

        setContent {
            WhatsappTheme(darkTheme = true) {
                NightBrowserFullScreen(
                    spec = spec,
                    onClose = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_SPEC = "night_browser_spec"

        fun createIntent(
            context: Context,
            spec: NightBrowserSpec,
        ): Intent =
            Intent(context, NightBrowserActivity::class.java)
                .putExtra(
                    EXTRA_SPEC,
                    NightBrowserSpecCodec.encode(spec).toString(),
                )
    }
}

@Composable
fun NightBrowserFullScreen(
    spec: NightBrowserSpec,
    onClose: () -> Unit,
) {
    val safeSpec = remember(spec) { spec.sanitized() }
    val controller = rememberNightBrowserController()
    var addressText by remember { mutableStateOf(safeSpec.initialUrl) }

    LaunchedEffect(controller.currentUrl) {
        if (controller.currentUrl.isNotBlank()) {
            addressText = controller.currentUrl
        }
    }

    BackHandler {
        if (controller.canGoBack()) {
            controller.goBack()
        } else {
            onClose()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrowserBackground)
            .semantics {
                contentDescription = "Night full browser"
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrowserChrome)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 7.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.semantics {
                        contentDescription = "Close browser"
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = BrowserText,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                ) {
                    Text(
                        text = controller.pageTitle.ifBlank { safeSpec.title },
                        color = BrowserText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = safeSpec.normalizedAllowedHosts().joinToString(", "),
                        color = BrowserMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(
                    onClick = controller::reload,
                    modifier = Modifier.semantics {
                        contentDescription = "Reload browser"
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = BrowserText,
                    )
                }
            }

            OutlinedTextField(
                value = addressText,
                onValueChange = { addressText = it.take(4096) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        controller.loadUrl(
                            safeSpec,
                            normalizeBrowserInput(addressText),
                        )
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Browser address"
                    },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = controller::goBack,
                    enabled = controller.canGoBack(),
                    modifier = Modifier.semantics {
                        contentDescription = "Browser back"
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        tint = if (controller.canGoBack()) BrowserText else BrowserMuted,
                    )
                }

                Spacer(modifier = Modifier.size(18.dp))

                IconButton(
                    onClick = controller::goForward,
                    enabled = controller.canGoForward(),
                    modifier = Modifier.semantics {
                        contentDescription = "Browser forward"
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = if (controller.canGoForward()) BrowserText else BrowserMuted,
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
                    trackColor = BrowserPanel,
                )
            }

            controller.blockedUrl?.let {
                Text(
                    text = "Night blocked navigation outside this browser session.",
                    color = ComposeColor(0xFFFF8A92),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(ComposeColor.White),
        ) {
            NightBrowserWebView(
                spec = safeSpec,
                controller = controller,
                modifier = Modifier.fillMaxSize(),
            )

            controller.errorText?.let { error ->
                Text(
                    text = error,
                    color = BrowserText,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                        .background(
                            BrowserChrome.copy(alpha = 0.94f),
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        Spacer(
            modifier = Modifier
                .navigationBarsPadding()
                .height(1.dp),
        )
    }
}

private fun normalizeBrowserInput(value: String): String {
    val trimmed = value.trim()
    return when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        else -> "https://" + trimmed
    }
}
