package studio.artistscene.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import studio.artistscene.core.TransformAxis

/**
 * The numeric field is display-only until explicitly tapped. This prevents a tall
 * Inspector sheet from jumping to the bottom or raising the IME as it opens.
 * Keep this UI independent of the scene model so it can be integrated safely.
 */
internal object AxisNumericDraft {
    private val draftPattern = Regex("""^-?\d*(?:[.,]\d*)?$""")

    fun accepts(text: String): Boolean = text.length <= 16 && draftPattern.matches(text)

    fun parse(text: String): Float? =
        text.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }

    fun display(value: Float): String {
        if (!value.isFinite()) return "—"
        val decimals = if (abs(value) > 0f && abs(value) < .1f) 3 else 2
        return String.format(Locale.US, "%.${decimals}f", value)
    }
}

private val InspectorText = Color(0xFFF3FAFA)
private val InspectorMuted = Color(0xFFA7BBC0)
private val InspectorValueSurface = Color(0xFF121D24)
private val InspectorBorder = Color(0xFF41565F)

@Composable
internal fun StudioAxisField(
    inputKey: String,
    axis: TransformAxis,
    value: Float,
    step: Float,
    enabled: Boolean,
    onDelta: (Float) -> Unit,
    onSet: (Float) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val requestFocus = remember(inputKey, axis) { FocusRequester() }
    var editing by remember(inputKey, axis) { mutableStateOf(false) }
    var draft by remember(inputKey, axis) { mutableStateOf(AxisNumericDraft.display(value)) }
    var wasFocused by remember(inputKey, axis) { mutableStateOf(false) }
    val canEdit = enabled && value.isFinite()
    val canStep = canEdit && step.isFinite() && step > 0f

    val tint = when (axis) {
        TransformAxis.X -> Color(0xFFFC8F8A)
        TransformAxis.Y -> Color(0xFF85CFA9)
        TransformAxis.Z -> Color(0xFF8BABFF)
    }

    LaunchedEffect(value, inputKey, axis) {
        if (!editing) draft = AxisNumericDraft.display(value)
    }
    LaunchedEffect(editing) {
        if (editing) {
            withFrameNanos { }
            requestFocus.requestFocus()
            keyboard?.show()
        }
    }

    fun resetEditing(clearFocus: Boolean = true) {
        editing = false
        draft = AxisNumericDraft.display(value)
        if (clearFocus) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    fun commit(clearFocus: Boolean = true) {
        if (editing) {
            AxisNumericDraft.parse(draft)?.takeIf { canEdit }?.let(onSet)
        }
        resetEditing(clearFocus)
    }

    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFF202C33), RoundedCornerShape(13.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp)
                .background(tint.copy(alpha = .16f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(axis.name, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(
            onClick = {
                resetEditing()
                onDelta(-step)
            },
            enabled = canStep,
            modifier = Modifier.size(42.dp),
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease ${axis.name}",
                tint = if (canStep) InspectorText else InspectorMuted.copy(alpha = .5f))
        }

        val valueModifier = Modifier.weight(1f).height(44.dp)
        if (!editing) {
            Box(
                modifier = valueModifier
                    .background(InspectorValueSurface, RoundedCornerShape(10.dp))
                    .border(1.dp, InspectorBorder, RoundedCornerShape(10.dp))
                    .clickable(enabled = canEdit, onClickLabel = "Edit ${axis.name} value") {
                        draft = AxisNumericDraft.display(value)
                        editing = true
                    }
                    .semantics(mergeDescendants = true) {
                        testTag = "numeric-${axis.name.lowercase()}"
                        contentDescription = "${axis.name} value"
                        text = AnnotatedString(AxisNumericDraft.display(value))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    AxisNumericDraft.display(value),
                    color = if (canEdit) InspectorText else InspectorMuted,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            BasicTextField(
                value = draft,
                onValueChange = { typed ->
                    if (AxisNumericDraft.accepts(typed)) {
                        draft = typed.replace(',', '.')
                    }
                },
                singleLine = true,
                enabled = canEdit,
                textStyle = TextStyle(
                    color = InspectorText,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp,
                ),
                cursorBrush = SolidColor(tint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = valueModifier
                    .testTag("numeric-${axis.name.lowercase()}")
                    .focusRequester(requestFocus)
                    .onFocusChanged { state ->
                        if (wasFocused && !state.isFocused && editing) {
                            // Focus is already elsewhere; don't change focus from its callback.
                            commit(clearFocus = false)
                        }
                        wasFocused = state.isFocused
                    },
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(InspectorValueSurface, RoundedCornerShape(10.dp))
                            .border(1.dp, tint, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) { inner() }
                },
            )
        }
        IconButton(
            onClick = {
                resetEditing()
                onDelta(step)
            },
            enabled = canStep,
            modifier = Modifier.size(42.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase ${axis.name}",
                tint = if (canStep) InspectorText else InspectorMuted.copy(alpha = .5f))
        }
        Text(
            if (step == 15f) "15°" else AxisNumericDraft.display(step),
            color = InspectorMuted,
            fontSize = 10.sp,
            modifier = Modifier.width(34.dp).alpha(if (canStep) 1f else .5f),
            textAlign = TextAlign.Center,
        )
    }
}
