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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dav3.linksentry.domain.system.BrowserRoleChecker
import com.dav3.linksentry.ui.history.HistoryScreen
import com.dav3.linksentry.ui.inspect.InspectScreen
import com.dav3.linksentry.ui.inspect.NewUrlScreen
import com.dav3.linksentry.ui.settings.SettingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

object Dest {
    const val INSPECT = "inspect"
    const val NEW_URL = "inspect/new"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

@HiltViewModel
class RoleViewModel @Inject constructor(
    private val roleChecker: BrowserRoleChecker,
) : ViewModel() {
    private val _isDefault = MutableStateFlow(roleChecker.isDefaultBrowser())
    val isDefault: StateFlow<Boolean> = _isDefault.asStateFlow()

    /** Re-query the browser role; called when the app regains focus. */
    fun refresh() {
        _isDefault.value = roleChecker.isDefaultBrowser()
    }
}

@Composable
fun LinkSentryNavHost(
    navController: NavHostController = rememberNavController(),
    initialUrl: String? = null,
    onUrlInspected: () -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Handler-picker layout (list vs grid) comes from app settings.
    val settingsVm: SettingsNavViewModel = hiltViewModel()
    val appSettings by settingsVm.settings.collectAsState()

    // Pending URL to inspect: consumed by InspectScreen exactly once.
    // `initialUrl` can change at any time (warm start via onNewIntent while
    // the NavHost is already composed) — follow it instead of capturing it.
    var pendingUrl by remember { mutableStateOf(initialUrl) }
    LaunchedEffect(initialUrl) {
        if (initialUrl != null) {
            pendingUrl = initialUrl
            navController.navigate(Dest.INSPECT) { launchSingleTop = true }
        }
    }
    val roleVm: RoleViewModel = hiltViewModel()

    Scaffold(
        bottomBar = {
            if (currentRoute != Dest.NEW_URL) {
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
                    onInspectNew = { navController.navigate(Dest.NEW_URL) },
                    handlerLayout = appSettings.handlerLayout,
                )
            }
            composable(Dest.NEW_URL) {
                NewUrlScreen(
                    onSubmitted = { navController.popBackStack() },
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
                val isDefault by roleVm.isDefault.collectAsState()
                // Refresh when the user returns from system default-browser
                // settings — the composition itself won't re-run on resume.
                androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                    roleVm.refresh()
                    onPauseOrDispose { }
                }
                SettingsScreen(isDefaultBrowser = isDefault)
            }
        }
    }
}

@HiltViewModel
class SettingsNavViewModel @Inject constructor(
    settingsRepository: com.dav3.linksentry.domain.repository.SettingsRepository,
) : ViewModel() {
    val settings = settingsRepository.settings
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), com.dav3.linksentry.domain.model.AppSettings())
}
