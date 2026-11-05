package com.hostytalk.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Teal = Color(0xFF0E7C86)
val Amber = Color(0xFFF2A541)

private val Light = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = Amber,
    onSecondary = Color(0xFF2B1A00),
    background = Color(0xFFF5F7F8),
    surface = Color.White,
    surfaceVariant = Color(0xFFE3F3F4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5CC3CC),
    onPrimary = Color(0xFF00363B),
    secondary = Amber,
    background = Color(0xFF101719),
    surface = Color(0xFF1A2326),
    surfaceVariant = Color(0xFF123C40),
)

@Composable
fun HostyTalkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
