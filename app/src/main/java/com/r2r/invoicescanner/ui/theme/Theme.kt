package com.r2r.invoicescanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.r2r.invoicescanner.model.ConfidenceBand

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5CAD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF52606F),
    secondaryContainer = Color(0xFFE3E9F2),
    onSecondaryContainer = Color(0xFF0F1D2A),
    tertiary = Color(0xFFB85C00),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00468A),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondaryContainer = Color(0xFF2B3742),
    onSecondaryContainer = Color(0xFFDCE3EC),
)

@Composable
fun InvoiceScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

val PositiveGreen = Color(0xFF2E7D32)
val WarningAmber = Color(0xFFE65100)
val NegativeRed = Color(0xFFC62828)

fun bandColor(band: ConfidenceBand): Color = when (band) {
    ConfidenceBand.HIGH -> PositiveGreen
    ConfidenceBand.MEDIUM -> WarningAmber
    ConfidenceBand.LOW -> NegativeRed
}
