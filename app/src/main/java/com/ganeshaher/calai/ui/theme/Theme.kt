package com.ganeshaher.calai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CalendarColors = lightColorScheme(
    primary = Color(0xFF6D3EC1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF35205C),
    secondary = Color(0xFF734CA8),
    secondaryContainer = Color(0xFFEDE2F9),
    onSecondaryContainer = Color(0xFF402A57),
    background = Color(0xFFF4EDF9),
    onBackground = Color(0xFF2C203B),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF2C203B),
    surfaceVariant = Color(0xFFF3EDF8),
    onSurfaceVariant = Color(0xFF655772),
    outline = Color(0xFF887899),
    outlineVariant = Color(0xFFDCCEE7)
)

private val CalendarShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun CalAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CalendarColors,
        typography = Typography,
        shapes = CalendarShapes,
        content = content
    )
}
