package com.minifive.animeseason.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.minifive.animeseason.ui.screens.DatabaseScreen
import com.minifive.animeseason.ui.screens.FavoritesScreen
import com.minifive.animeseason.ui.screens.SeasonScreen
import com.minifive.animeseason.ui.screens.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.Season.route,
        modifier = modifier
    ) {
        composable(AppDestination.Season.route) { SeasonScreen() }
        composable(AppDestination.Database.route) { DatabaseScreen() }
        composable(AppDestination.Favorites.route) { FavoritesScreen() }
        composable(AppDestination.Settings.route) { SettingsScreen() }
    }
}
