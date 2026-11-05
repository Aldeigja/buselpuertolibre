package es.elpuertobus.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SheetSurface = Color(0xFFF8FAFC)

private val BusColorScheme =
    lightColorScheme(
        primary = TransitColors.Slate900,
        onPrimary = Color.White,
        primaryContainer = TransitColors.Slate100,
        onPrimaryContainer = TransitColors.Slate900,
        secondaryContainer = TransitColors.Slate100,
        onSecondaryContainer = TransitColors.Slate900,
        surface = SheetSurface,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = SheetSurface,
        surfaceContainer = SheetSurface,
        surfaceContainerHigh = TransitColors.Slate100,
        surfaceContainerHighest = TransitColors.Slate100,
        surfaceTint = Color.Transparent,
        onSurface = TransitColors.Slate900,
        onSurfaceVariant = TransitColors.Slate600,
        outline = TransitColors.Slate200,
        outlineVariant = TransitColors.Slate100,
    )

@Composable
fun BusElPuertoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BusColorScheme,
        content = content,
    )
}
