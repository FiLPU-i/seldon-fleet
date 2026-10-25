package br.edu.uea.seldonfleet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val StatusOkColor = Color(0xFF2E7D32)
val StatusOkBg = Color(0xFFE8F5E9)

val StatusWarningColor = Color(0xFFEF6C00)
val StatusWarningBg = Color(0xFFFFF3E0)

val StatusCriticalColor = Color(0xFFC62828)
val StatusCriticalBg = Color(0xFFFFEBEE)

val StatusUnknownColor = Color(0xFF455A64)
val StatusUnknownBg = Color(0xFFECEFF1)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0D47A1),
    secondary = Color(0xFF1565C0),
    background = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF)
)

@Composable
fun SeldonFleetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
