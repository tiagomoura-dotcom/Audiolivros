package com.tiagomoura.audiolivros

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tiagomoura.audiolivros.core.theme.AudiolivrosTheme
import com.tiagomoura.audiolivros.core.theme.Fundo
import com.tiagomoura.audiolivros.library.LibraryScreen
import com.tiagomoura.audiolivros.player.PlayerScreen
import com.tiagomoura.audiolivros.player.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint

private const val ROUTE_LIBRARY = "biblioteca"
private const val ROUTE_PLAYER = "player/{${PlayerViewModel.ARG_BOOK_ID}}"

private fun playerRoute(bookId: String) = "player/$bookId"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Fundo.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Fundo.toArgb()),
        )
        super.onCreate(savedInstanceState)
        setContent {
            AudiolivrosTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = ROUTE_LIBRARY) {
                    composable(ROUTE_LIBRARY) {
                        LibraryScreen(onOpenBook = { navController.navigate(playerRoute(it)) { launchSingleTop = true } })
                    }
                    composable(
                        route = ROUTE_PLAYER,
                        arguments = listOf(navArgument(PlayerViewModel.ARG_BOOK_ID) { type = NavType.StringType }),
                    ) {
                        PlayerScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
