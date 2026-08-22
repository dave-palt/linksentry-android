package com.dav3.linksentry.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dav3.linksentry.domain.system.BrowserRoleChecker
import com.dav3.linksentry.ui.history.HistoryScreen
import com.dav3.linksentry.ui.inspect.InspectScreen
import com.dav3.linksentry.ui.settings.SettingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

object Dest {
    const val INSPECT = "inspect"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

@HiltViewModel
class RoleViewModel @Inject constructor(
    private val roleChecker: BrowserRoleChecker,
) : ViewModel() {
    fun isDefaultBrowser(): Boolean = roleChecker.isDefaultBrowser()
}

@Composable
fun LinkSentryNavHost(
    navController: NavHostController = rememberNavController(),
    initialUrl: String? = null,
    onUrlInspected: () -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Pending URL to inspect: consumed by InspectScreen exactly once.
    var pendingUrl by remember { mutableStateOf(initialUrl) }
    val roleVm: RoleViewModel = hiltViewModel()

    LaunchedEffect(Unit) {
        if (initialUrl != null) {
            navController.navigate(Dest.INSPECT) { launchSingleTop = true }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text("Inspect") },
                    selected = currentRoute == Dest.INSPECT,
                    onClick = {
                        navController.navigate(Dest.INSPECT) {
                            popUpTo(Dest.INSPECT) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    label = { Text("History") },
                    selected = currentRoute == Dest.HISTORY,
                    onClick = {
                        navController.navigate(Dest.HISTORY) {
                            popUpTo(Dest.INSPECT) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = currentRoute == Dest.SETTINGS,
                    onClick = {
                        navController.navigate(Dest.SETTINGS) {
                            popUpTo(Dest.INSPECT) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.INSPECT,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.INSPECT) {
                InspectScreen(
                    initialUrl = pendingUrl,
                    onUrlInspected = {
                        pendingUrl = null
                        onUrlInspected()
                    },
                )
            }
            composable(Dest.HISTORY) {
                HistoryScreen(
                    onReinspect = { url ->
                        pendingUrl = url
                        navController.navigate(Dest.INSPECT) {
                            popUpTo(Dest.INSPECT) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Dest.SETTINGS) {
                SettingsScreen(isDefaultBrowser = roleVm.isDefaultBrowser())
            }
        }
    }
}
