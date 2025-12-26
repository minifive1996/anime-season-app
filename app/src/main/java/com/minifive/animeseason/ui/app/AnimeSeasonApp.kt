package com.minifive.animeseason.ui.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.minifive.animeseason.ui.theme.YourTheme

@Composable
fun AnimeSeasonApp() {
    YourTheme {
        val navController = rememberNavController()
        AppScaffold(navController = navController)
    }
}
