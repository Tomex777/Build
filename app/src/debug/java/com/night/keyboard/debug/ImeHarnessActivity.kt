package com.night.keyboard.debug

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.night.keyboard.ui.theme.KeyboardTheme
import kotlinx.coroutines.delay

class ImeHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
        )
        val mode = intent.getStringExtra("mode") ?: "normal"
        setContent {
            KeyboardTheme {
                ImeHarnessScreen(mode)
            }
        }
    }
}

private data class HarnessMode(
    val initialText: String,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val imeAction: ImeAction = ImeAction.Default,
    val singleLine: Boolean = true,
    val password: Boolean = false,
    val initialSelection: TextRange = TextRange.Zero,
)

private fun modeConfig(mode: String): HarnessMode = when (mode) {
    "email" -> HarnessMode(
        initialText = "person@example.com",
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
    )
    "url" -> HarnessMode(
        initialText = "https://example.com",
        keyboardType = KeyboardType.Uri,
        imeAction = ImeAction.Go,
    )
    "number" -> HarnessMode(
        initialText = "1234567890",
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Done,
    )
    "password" -> HarnessMode(
        initialText = "private-secret",
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
        password = true,
    )
    "multiline" -> HarnessMode(
        initialText = "First line\nSecond line",
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Default,
        singleLine = false,
    )
    "search" -> HarnessMode(
        initialText = "keyboard search",
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Search,
    )
    "rtl" -> HarnessMode(
        initialText = "مرحبا بالعالم",
        keyboardType = KeyboardType.Text,
    )
    "selected" -> HarnessMode(
        initialText = "select this text",
        initialSelection = TextRange(0, 6),
    )
    else -> HarnessMode(
        initialText = "Cursor test: move the caret through this sentence",
    )
}

@Composable
private fun ImeHarnessScreen(mode: String) {
    val config = remember(mode) { modeConfig(mode) }
    var value by remember(mode) {
        mutableStateOf(
            TextFieldValue(
                text = config.initialText,
                selection = config.initialSelection,
            ),
        )
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(mode) {
        delay(700)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Surface(color = Color(0xFFF7F7F8), modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text(
                "Keyboard IME QA",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF111317),
            )
            Text(
                "Mode: $mode",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF626A73),
                modifier = Modifier.padding(top = 5.dp),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (config.singleLine) 76.dp else 132.dp)
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(
                    keyboardType = config.keyboardType,
                    imeAction = config.imeAction,
                ),
                singleLine = config.singleLine,
                visualTransformation = if (config.password) {
                    PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                label = { Text(mode.replaceFirstChar(Char::uppercase)) },
            )
            Text(
                "Text length: ${value.text.length}",
                color = Color(0xFF626A73),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Selection: ${value.selection.start}-${value.selection.end}",
                color = Color(0xFF626A73),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
