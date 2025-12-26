package com.minifive.animeseason.ui.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.minifive.animeseason.navigation.bottomNavDestinations
import com.minifive.animeseason.ui.components.BannerAdSlot

@Composable
fun AppBottomBar(
    navController: NavController
) {
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = backStackEntry?.destination?.route

    Column {
        BannerAdSlot()
        NavigationBar {
            bottomNavDestinations.forEach { dest ->
                val selected = currentRoute == dest.route
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(painterResource(dest.iconRes), contentDescription = dest.label) },
                    label = { Text(dest.label) }
                )
            }
        }
    }
}
