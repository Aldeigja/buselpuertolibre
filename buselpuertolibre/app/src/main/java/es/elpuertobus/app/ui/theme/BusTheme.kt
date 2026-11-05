package es.elpuertobus.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BusColorScheme =
    lightColorScheme(
        primary = Color(0xFF2563EB),
        onPrimary = Color.White,
        surface = Color(0xFFFAFAF9),
        onSurface = TransitColors.Slate900,
        onSurfaceVariant = TransitColors.Slate600,
        outline = TransitColors.Slate200,
        outlineVariant = TransitColors.Slate100,
        secondaryContainer = TransitColors.Slate100,
        onSecondaryContainer = TransitColors.Slate600,
    )

@Composable
fun BusElPuertoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BusColorScheme,
        content = content,
    )
}
