package com.minifive.animeseason.ui.navigation

sealed class AppRoute(val route: String) {
    data object Season : AppRoute("season")
    data object Database : AppRoute("database")
    data object Favorites : AppRoute("favorites")
    data object Settings : AppRoute("settings")

    data object Detail : AppRoute("detail/{animeId}") {
        const val ARG_ANIME_ID = "animeId"
        fun createRoute(animeId: String): String = "detail/$animeId"
    }
}
