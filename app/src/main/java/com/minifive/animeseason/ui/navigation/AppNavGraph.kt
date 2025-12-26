package com.minifive.animeseason.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.minifive.animeseason.ui.screens.DatabaseScreen
import com.minifive.animeseason.ui.screens.DetailScreen
import com.minifive.animeseason.ui.screens.FavoritesScreen
import com.minifive.animeseason.ui.screens.SeasonScreen
import com.minifive.animeseason.ui.screens.SettingsScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    startDestination: String = AppRoute.Season.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(AppRoute.Season.route) {
            SeasonScreen(
                onAnimeClick = { animeId ->
                    navController.navigate(AppRoute.Detail.createRoute(animeId))
                }
            )
        }

        composable(AppRoute.Database.route) {
            DatabaseScreen(
                onAnimeClick = { animeId ->
                    navController.navigate(AppRoute.Detail.createRoute(animeId))
                }
            )
        }

        composable(AppRoute.Favorites.route) {
            FavoritesScreen(
                onAnimeClick = { animeId ->
                    navController.navigate(AppRoute.Detail.createRoute(animeId))
                },
                snackbarHostState = snackbarHostState
            )
        }

        composable(AppRoute.Settings.route) {
            SettingsScreen()
        }

        composable(
            route = AppRoute.Detail.route,
            arguments = listOf(
                navArgument(AppRoute.Detail.ARG_ANIME_ID) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val animeId = backStackEntry.arguments?.getString(AppRoute.Detail.ARG_ANIME_ID).orEmpty()
            DetailScreen(
                animeId = animeId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
