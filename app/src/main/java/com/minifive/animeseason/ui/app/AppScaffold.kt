package com.minifive.animeseason.ui.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.minifive.animeseason.navigation.AppNavHost

@Composable
fun AppScaffold(
    navController: NavHostController
) {
    Scaffold(
        bottomBar = { AppBottomBar(navController) }
    ) { innerPadding: PaddingValues ->
        AppNavHost(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
