package com.minifive.animeseason.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun AniInfoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 允許 dynamic，但如果使用者指定了 themeColorArgb，會優先使用指定色
    dynamicColor: Boolean = true,
    themeColorArgb: Int? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val baseScheme = when {
        dynamicColor && themeColorArgb == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val finalScheme = if (themeColorArgb != null) {
        val primary = Color(themeColorArgb)
        val primaryContainer = containerFromPrimary(primary, darkTheme)
        baseScheme.copy(
            primary = primary,
            onPrimary = onColorFor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onColorFor(primaryContainer)
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = finalScheme,
        typography = Typography,
        content = content
    )
}

private fun onColorFor(background: Color): Color {
    return if (background.luminance() > 0.5f) Color.Black else Color.White
}

private fun containerFromPrimary(primary: Color, darkTheme: Boolean): Color {
    return if (darkTheme) {
        // dark：往黑混一點，讓 container 更沉穩
        lerp(primary, Color.Black, 0.35f)
    } else {
        // light：往白混多一點，得到柔和 container
        lerp(primary, Color.White, 0.82f)
    }
}
