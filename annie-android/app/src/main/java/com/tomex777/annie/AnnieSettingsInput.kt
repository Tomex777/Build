package com.tomex777.annie

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Annie's single-line form input. Shared by Host AI and Script Studio ENV so
 * settings never fall back to unstyled platform/Material text boxes.
 * Keep labels visible above the field even while populated.
 */
@Composable
internal fun AnnieSettingsInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    numeric: Boolean = false,
    tag: String? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color(0xFFB7CAE0), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color(0xFFEEF5FF), fontSize = 15.sp),
            cursorBrush = SolidColor(Color(0xFF54D6AE)),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    secret -> KeyboardType.Password
                    numeric -> KeyboardType.Decimal
                    else -> KeyboardType.Text
                },
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clip(shape).background(Color(0xFF10243A))
                .border(1.dp, Color(0xFF294562), shape)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics { contentDescription = label }
                .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        )
    }
}
