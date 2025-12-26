package com.minifive.animeseason.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.minifive.animeseason.R

data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    @field:StringRes val labelRes: Int, // ✅ 消掉「annotation use-site」警告
)

val bottomNavItems = listOf(
    BottomNavItem(
        route = AppRoute.Season.route,
        icon = Icons.AutoMirrored.Filled.List, // ✅ 消掉 Icons.Filled.List deprecate
        labelRes = R.string.nav_season
    ),
    BottomNavItem(
        route = AppRoute.Database.route,
        icon = Icons.Filled.Search,
        labelRes = R.string.nav_database
    ),
    BottomNavItem(
        route = AppRoute.Favorites.route,
        icon = Icons.Filled.Favorite,
        labelRes = R.string.nav_favorites
    ),
    BottomNavItem(
        route = AppRoute.Settings.route,
        icon = Icons.Filled.Settings,
        labelRes = R.string.nav_settings
    ),
)
