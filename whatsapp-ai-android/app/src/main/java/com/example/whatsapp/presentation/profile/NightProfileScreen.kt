package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NightProfileScreen(
    displayName: String,
    onBack: () -> Unit,
    onSaveName: (String) -> Unit,
) {
    var name by remember(displayName) { mutableStateOf(displayName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F11))
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFFE7EAEC))
            }
            Text("Profile", color = Color(0xFFE7EAEC), fontSize = 22.sp)
        }

        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "This is the name Night uses for you.",
                color = Color(0xFF9CA5A9),
                fontSize = 13.sp,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedTextColor = Color(0xFFE7EAEC),
                    unfocusedTextColor = Color(0xFFE7EAEC),
                    cursorColor = Color(0xFF21C063),
                ),
            )

            Button(
                onClick = { onSaveName(name.trim()) },
                enabled = name.trim().isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF21C063),
                    contentColor = Color(0xFF07110B),
                ),
            ) {
                Text("Save")
            }
        }
    }
}
