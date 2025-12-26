package com.minifive.animeseason.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.minifive.animeseason.core.prefs.UserPrefsRepository
import com.minifive.animeseason.ui.theme.AniInfoTheme

@Composable
fun AnimeSeasonApp() {
    val context = LocalContext.current
    val prefs = remember { UserPrefsRepository(context.applicationContext) }

    val themeColorArgb by prefs.themeColorArgbFlow.collectAsState(initial = null)

    // 放在 Theme 外面，避免主題狀態變動時 NavController 有機會被重建
    val navController = rememberNavController()

    AniInfoTheme(
        dynamicColor = true,
        themeColorArgb = themeColorArgb
    ) {
        AppScaffold(navController = navController)
    }
}
