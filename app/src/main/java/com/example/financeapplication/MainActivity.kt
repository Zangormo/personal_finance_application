package com.example.financeapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.financeapplication.datastores.UserPreferencesDataStore
import com.example.financeapplication.screens.*
import com.example.financeapplication.ui.theme.FinanceApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            FinanceApplicationTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var startDestination by remember { mutableStateOf<String?>(null) }

                if (startDestination != null) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = startDestination!!,
                            modifier = Modifier.padding(innerPadding),
                            enterTransition = {
                                slideInHorizontally(tween(300)) { it / 5 } + fadeIn(tween(300))
                            },
                            exitTransition = {
                                slideOutHorizontally(tween(300)) { -it / 10 } + fadeOut(tween(200))
                            },
                            popEnterTransition = {
                                slideInHorizontally(tween(300)) { -it / 10 } + fadeIn(tween(300))
                            },
                            popExitTransition = {
                                slideOutHorizontally(tween(300)) { it / 5 } + fadeOut(tween(200))
                            }
                        ) {
                            val back: () -> Unit = { navController.popBackStack() }

                            composable("welcome_screen") {
                                WelcomeScreen(onContinueClicked = { balance ->
                                    scope.launch {
                                        UserPreferencesDataStore.setOverallBalance(context, balance)
                                        UserPreferencesDataStore.setFirstRunCompleted(context)
                                        navController.navigate("main_screen") {
                                            popUpTo("welcome_screen") { inclusive = true }
                                        }
                                    }
                                })
                            }
                            composable("main_screen") {
                                HomeScreen(onNavigate = { dest ->
                                    navController.navigate(
                                        when (dest) {
                                            HomeDestination.ADD_SPENDING -> "transaction/spending"
                                            HomeDestination.ADD_INCOME -> "transaction/income"
                                            HomeDestination.HISTORY -> "history"
                                            HomeDestination.PLANNED -> "planned"
                                            HomeDestination.SAVINGS -> "savings"
                                            HomeDestination.OVERVIEW -> "overview"
                                            HomeDestination.SUBSCRIPTIONS -> "subscriptions"
                                            HomeDestination.DEBTS -> "debts"
                                            HomeDestination.TODO -> "todo"
                                            HomeDestination.NOTES -> "notes"
                                        }
                                    )
                                })
                            }
                            composable("transaction/spending") { TransactionScreen(initialIsIncome = false, onBackPress = back) }
                            composable("transaction/income") { TransactionScreen(initialIsIncome = true, onBackPress = back) }
                            composable("history") { HistoryScreen(onBackPress = back) }
                            composable("planned") { PlannedScreen(onBackPress = back) }
                            composable("savings") { SavingsScreen(onBackPress = back) }
                            composable("overview") { OverviewScreen(onBackPress = back) }
                            composable("subscriptions") { SubscriptionsScreen(onBackPress = back) }
                            composable("debts") { DebtsScreen(onBackPress = back) }
                            composable("todo") {
                                TodoCategoriesScreen(
                                    onBackPress = back,
                                    onOpenCategory = { id -> navController.navigate("todo/$id") }
                                )
                            }
                            composable("todo/{category}") { entry ->
                                TodoListScreen(
                                    categoryRoute = entry.arguments?.getString("category") ?: UNCATEGORIZED_ROUTE,
                                    onBackPress = back
                                )
                            }
                            composable("notes") {
                                NotesScreen(
                                    onBackPress = back,
                                    onOpenNote = { id -> navController.navigate("note/$id") }
                                )
                            }
                            composable("note/{id}") { entry ->
                                NoteScreen(
                                    noteId = entry.arguments?.getString("id") ?: NEW_NOTE,
                                    onBackPress = back
                                )
                            }
                        }
                    }
                } else {
                    SplashScreen(onScreenDetermined = { destination ->
                        startDestination = destination
                    })
                }
            }
        }
    }
}
