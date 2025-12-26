package com.minifive.animeseason.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.minifive.animeseason.ui.navigation.AppNavGraph

@Composable
fun AppNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    AppNavGraph(
        navController = navController,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}
