package com.example.whatsapp.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.extensions.runtime.NightIntegrationKind
import com.example.whatsapp.extensions.runtime.NightIntegrationSummary

/**
 * Shared management row for every Night integration.
 *
 * Native Night extensions and MCP connections intentionally look and behave like
 * one ecosystem here. [NightIntegrationSummary.kind] is the small marker that
 * tells the user (and the runtime adapter) which backend owns the integration.
 */
@Composable
internal fun NightIntegrationRow(
    integration: NightIntegrationSummary,
    secondaryText: String,
    statusText: String,
    onToggle: () -> Unit,
    toggleEnabled: Boolean = true,
    statusAccent: Boolean = integration.enabled && integration.error.isNullOrBlank(),
    trailingActions: @Composable RowScope.() -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val text = Color(0xFFE7EAEC)
    val muted = Color(0xFF9CA5A9)
    val accent = Color(0xFF21C063)
    val errorColor = Color(0xFFFF8A92)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = integration.displayName,
                        color = if (integration.enabled) text else muted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = when (integration.kind) {
                            NightIntegrationKind.EXTENSION -> "EXT"
                            NightIntegrationKind.MCP -> "MCP"
                        },
                        color = muted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(
                                color = Color(0xFF20272A),
                                shape = RoundedCornerShape(5.dp),
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }

                Text(
                    text = secondaryText,
                    color = muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }

            trailingActions()
        }

        Text(
            text = statusText,
            color = if (statusAccent) accent else muted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )

        integration.error
            ?.takeIf { it.isNotBlank() }
            ?.let { message ->
                Text(
                    text = message,
                    color = errorColor,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onToggle,
                enabled = toggleEnabled,
            ) {
                Text(
                    text = if (integration.enabled) "Disable" else "Enable",
                    color = if (toggleEnabled) accent else muted.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                )
            }
        }

        footer()
    }
}
