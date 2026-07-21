package com.chronicle.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chronicle.app.presentation.addentry.AddEntryScreen
import com.chronicle.app.presentation.auth.AuthScreen
import com.chronicle.app.presentation.detail.EntryDetailScreen
import com.chronicle.app.presentation.home.HomeScreen
import com.chronicle.app.presentation.home.HomeViewModel
import com.chronicle.app.ui.theme.ChronicleTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChronicleTheme {
                val navController = rememberNavController()
                val start = if (FirebaseAuth.getInstance().currentUser != null) "home" else "auth"

                NavHost(navController = navController, startDestination = start) {

                    // --- Auth Screen ---
                    composable("auth") {
                        AuthScreen(onAuthSuccess = {
                            navController.navigate("home") { popUpTo("auth") { inclusive = true } }
                        })
                    }

                    // --- Home Screen (Fixed line 51) ---
                    composable("home") {
                        val viewModel: HomeViewModel = hiltViewModel()
                        HomeScreen(
                            onAddEntry = { navController.navigate("add_entry") },
                            onLogout = {
                                FirebaseAuth.getInstance().signOut()
                                navController.navigate("auth") { popUpTo(0) }
                            },
                            // FIX: Pass the clicked entry's ID to the detail screen
                            onEntryClick = { entry ->
                                navController.navigate("detail/${entry.id}")
                            },
                            viewModel = viewModel
                        )
                    }

                    // --- Add Entry Screen ---
                    composable("add_entry") {
                        AddEntryScreen(onBack = { navController.popBackStack() })
                    }

                    // --- Detail Screen (With Argument Parsing) ---
                    composable(
                        route = "detail/{entryId}",
                        arguments = listOf(navArgument("entryId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val entryId = backStackEntry.arguments?.getString("entryId")
                        val viewModel: HomeViewModel = hiltViewModel()
                        // Find the entry from the list using the ID
                        val entryFlow = viewModel.getEntryById(entryId ?: "")
                        val entry by entryFlow.collectAsState(initial = null)

                        entry?.let { selectedEntry ->
                            EntryDetailScreen(
                                entry = selectedEntry,
                                onBack = { navController.popBackStack() },
                                onDelete = {
                                    viewModel.deleteEntry(selectedEntry)
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}