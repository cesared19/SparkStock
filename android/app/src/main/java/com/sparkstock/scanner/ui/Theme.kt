package com.sparkstock.scanner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// SparkyTC's crimson, like SparkStock's website
val Brand = Color(0xFFC01C37)
val Good = Color(0xFF15803D)
val Warning = Color(0xFFB45309)
val Bad = Color(0xFFB91C1C)

private val Light = lightColorScheme(primary = Brand, onPrimary = Color.White, secondary = Color(0xFF0F766E))
private val Dark = darkColorScheme(
    primary = Color(0xFFF06E87), onPrimary = Color(0xFF3D0712), secondary = Color(0xFF5EEAD4),
    background = Color(0xFF0B1220), surface = Color(0xFF111622),
)

@Composable
fun SparkStockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
