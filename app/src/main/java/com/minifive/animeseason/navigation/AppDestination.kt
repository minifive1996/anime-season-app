package com.minifive.animeseason.navigation

import androidx.annotation.DrawableRes
import com.minifive.animeseason.R

sealed class AppDestination(
    val route: String,
    val label: String,
    @DrawableRes val iconRes: Int
) {
    data object Season : AppDestination("season", "本季", R.drawable.ic_nav_season)
    data object Database : AppDestination("database", "資料庫", R.drawable.ic_nav_database)
    data object Favorites : AppDestination("favorites", "收藏", R.drawable.ic_nav_favorites)
    data object Settings : AppDestination("settings", "設定", R.drawable.ic_nav_settings)
}

val bottomNavDestinations = listOf(
    AppDestination.Season,
    AppDestination.Database,
    AppDestination.Favorites,
    AppDestination.Settings
)
