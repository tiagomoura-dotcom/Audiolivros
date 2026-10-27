package com.tiagomoura.audiolivros

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tiagomoura.audiolivros.chapters.ChapterEditorScreen
import com.tiagomoura.audiolivros.chapters.ChapterEditorViewModel
import com.tiagomoura.audiolivros.chapters.SilenceScreen
import com.tiagomoura.audiolivros.chapters.SilenceViewModel
import com.tiagomoura.audiolivros.core.theme.AudiolivrosTheme
import com.tiagomoura.audiolivros.core.theme.Fundo
import com.tiagomoura.audiolivros.library.LibraryScreen
import com.tiagomoura.audiolivros.player.PlayerScreen
import com.tiagomoura.audiolivros.player.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint

private const val ROUTE_LIBRARY = "biblioteca"
private const val ROUTE_PLAYER = "player/{${PlayerViewModel.ARG_BOOK_ID}}"

private const val ROUTE_CHAPTERS =
    "capitulos/{${ChapterEditorViewModel.ARG_BOOK_ID}}?${ChapterEditorViewModel.ARG_REANALYZE}={${ChapterEditorViewModel.ARG_REANALYZE}}"

private const val ROUTE_SILENCE = "pausas/{${SilenceViewModel.ARG_BOOK_ID}}"

private fun playerRoute(bookId: String) = "player/$bookId"

private fun silenceRoute(bookId: String) = "pausas/$bookId"

private fun chaptersRoute(bookId: String, reanalyze: Boolean) =
    "capitulos/$bookId?${ChapterEditorViewModel.ARG_REANALYZE}=$reanalyze"

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
                    ) { entry ->
                        PlayerScreen(
                            onBack = { navController.popBackStack() },
                            onEditChapters = { reanalyze -> navController.navigate(chaptersRoute(bookIdOf(entry), reanalyze)) },
                        )
                    }
                    composable(
                        route = ROUTE_CHAPTERS,
                        arguments = listOf(
                            navArgument(ChapterEditorViewModel.ARG_BOOK_ID) { type = NavType.StringType },
                            navArgument(ChapterEditorViewModel.ARG_REANALYZE) {
                                type = NavType.BoolType
                                defaultValue = false
                            },
                        ),
                    ) {
                        ChapterEditorScreen(
                            onBack = { navController.popBackStack() },
                            onSilenceAnalysis = { bookId -> navController.navigate(silenceRoute(bookId)) },
                        )
                    }
                    composable(
                        route = ROUTE_SILENCE,
                        arguments = listOf(navArgument(SilenceViewModel.ARG_BOOK_ID) { type = NavType.StringType }),
                    ) {
                        SilenceScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

private fun bookIdOf(entry: NavBackStackEntry): String =
    checkNotNull(entry.arguments?.getString(PlayerViewModel.ARG_BOOK_ID))
