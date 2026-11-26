package com.example.sonet.ui

import android.app.Activity
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Colors for content drawn directly on the wallpaper, picked for legibility. */
@Immutable
data class WallpaperContrast(
    val darkText: Boolean,
    val text: Color,
    val secondaryText: Color,
    val scrim: Color,
    val textShadow: Shadow?,
)

private fun contrastFor(darkText: Boolean) = if (darkText) {
    WallpaperContrast(
        darkText = true,
        text = Color(0xFF111111),
        secondaryText = Color(0xB3111111),
        scrim = Color.White.copy(alpha = 0.35f),
        textShadow = null,
    )
} else {
    WallpaperContrast(
        darkText = false,
        text = Color.White,
        secondaryText = Color(0xCCFFFFFF),
        scrim = Color.Black.copy(alpha = 0.35f),
        textShadow = Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 8f),
    )
}

/**
 * Follows the system wallpaper's brightness hint (like the stock launcher does), so text on
 * the home screen stays readable whatever the wallpaper is. Also keeps the status and
 * navigation bar icons in sync.
 */
@Composable
fun rememberWallpaperContrast(): WallpaperContrast {
    val context = LocalContext.current
    val wallpaperManager = remember { WallpaperManager.getInstance(context) }
    var darkText by remember {
        mutableStateOf(wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM).supportsDarkText())
    }

    DisposableEffect(wallpaperManager) {
        val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) darkText = colors.supportsDarkText()
        }
        wallpaperManager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { wallpaperManager.removeOnColorsChangedListener(listener) }
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkText
            isAppearanceLightNavigationBars = darkText
        }
    }

    return remember(darkText) { contrastFor(darkText) }
}

private fun WallpaperColors?.supportsDarkText(): Boolean =
    this != null && colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
