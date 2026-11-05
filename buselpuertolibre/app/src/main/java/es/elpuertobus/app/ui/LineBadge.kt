package es.elpuertobus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.elpuertobus.app.ui.theme.TransitColors

@Composable
fun LineBadge(
    line: Int,
    colour: String,
    modifier: Modifier = Modifier,
) {
    val fill = runCatching { Color(android.graphics.Color.parseColor(colour)) }.getOrDefault(Color(0xFF2563EB))
    val ink = if (fill.luminance() > 0.62f) TransitColors.SlateInkOnLightBadge else TransitColors.WhiteInk
    Box(
        modifier =
            modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = line.toString(),
            color = ink,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
