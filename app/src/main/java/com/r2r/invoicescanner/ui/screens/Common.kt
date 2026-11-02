package com.r2r.invoicescanner.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.r2r.invoicescanner.model.bandOf
import com.r2r.invoicescanner.ui.theme.bandColor
import kotlinx.coroutines.launch

@Composable
fun ConfidenceChip(score: Int, modifier: Modifier = Modifier) {
    val color = bandColor(bandOf(score))
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f), modifier = modifier) {
        Text(
            text = "$score%",
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * Single-line text field used across the app: double-tap selects all the text (so typing
 * replaces it) and, when [clearable], a small × on the right clears it.
 */
@Composable
fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    clearable: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = LocalTextStyle.current,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    // Keep cursor / selection locally; follow the text when it is changed from outside
    // (e.g. the subtotal re-balanced after the tax was edited).
    var local by remember { mutableStateOf(TextFieldValue(value)) }
    val shown = if (local.text == value) local else TextFieldValue(value, TextRange(value.length))
    val current by rememberUpdatedState(shown)
    val scope = rememberCoroutineScope()

    val showClear = clearable && value.isNotEmpty()
    val trailing: (@Composable () -> Unit)? = if (!showClear && trailingIcon == null) null else {
        {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showClear) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear $label")
                    }
                }
                trailingIcon?.invoke()
            }
        }
    }

    OutlinedTextField(
        value = shown,
        onValueChange = { v ->
            local = v
            if (v.text != value) onValueChange(v.text)
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        isError = isError,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = textStyle,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon,
        trailingIcon = trailing,
        modifier = modifier.pointerInput(Unit) {
            // Watch taps without consuming them, so the field still focuses / places the cursor.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
                val quick = up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                if (!quick || (up.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    return@awaitEachGesture
                }
                val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                } ?: return@awaitEachGesture
                if ((second.position - up.position).getDistance() > viewConfiguration.touchSlop * 4) {
                    return@awaitEachGesture
                }
                waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
                // After the field's own double-tap handling (which selects one word), select everything.
                scope.launch {
                    val v = current
                    local = v.copy(selection = TextRange(0, v.text.length))
                }
            }
        },
    )
}
