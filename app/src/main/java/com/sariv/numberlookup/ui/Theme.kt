// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 RiversLee9312 and contributors

package com.sariv.numberlookup.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF386941), onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0BC), onPrimaryContainer = Color(0xFF002109),
    secondary = Color(0xFF526350), secondaryContainer = Color(0xFFD5E8D0),
    onSecondaryContainer = Color(0xFF101F10), tertiary = Color(0xFF38656A),
    surface = Color(0xFFF7FAF2), onSurface = Color(0xFF181D17),
    surfaceContainerLow = Color(0xFFF0F4EB), surfaceContainer = Color(0xFFEAEEE5),
    surfaceContainerHigh = Color(0xFFE4E8DF), surfaceContainerHighest = Color(0xFFDEE3DA),
    onSurfaceVariant = Color(0xFF424940), outline = Color(0xFF72796F),
    outlineVariant = Color(0xFFC2C9BD),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ED5A2), onPrimary = Color(0xFF003912),
    primaryContainer = Color(0xFF20512B), onPrimaryContainer = Color(0xFFB9F0BC),
    secondary = Color(0xFFB9CCB4), secondaryContainer = Color(0xFF3B4B39),
    onSecondaryContainer = Color(0xFFD5E8D0), tertiary = Color(0xFFA0CFD4),
    surface = Color(0xFF10150F), onSurface = Color(0xFFE0E4DB),
    surfaceContainerLow = Color(0xFF181D17), surfaceContainer = Color(0xFF1C211B),
    surfaceContainerHigh = Color(0xFF272C25), surfaceContainerHighest = Color(0xFF32372F),
    onSurfaceVariant = Color(0xFFC2C9BD), outline = Color(0xFF8C9388),
    outlineVariant = Color(0xFF424940),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NumberLookupTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialExpressiveTheme(colorScheme = colors, content = content)
}
