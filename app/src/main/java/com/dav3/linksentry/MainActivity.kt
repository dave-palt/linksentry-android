package com.dav3.linksentry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.dav3.linksentry.data.local.SettingsRepositoryImpl
import com.dav3.linksentry.domain.model.ThemeMode
import com.dav3.linksentry.ui.nav.LinkSentryNavHost
import com.dav3.linksentry.ui.theme.LinkSentryTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/**
 * Single-activity app. Receives ACTION_VIEW http/https intents (as the
 * default browser) and shows the Inspect screen; launcher entry opens the
 * manual-inspect tab.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** URL handed in by a VIEW intent; consumed once by the NavHost. */
    private val pendingUrl = MutableStateFlow<String?>(null)

    @Inject
    lateinit var settingsRepository: SettingsRepositoryImpl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingUrl.value = intentUrl(intent)

        setContent {
            val themeMode by settingsRepository.settings
                .collectAsState(initial = com.dav3.linksentry.domain.model.AppSettings())
                .let { androidx.compose.runtime.derivedStateOf { it.value.theme } }

            LinkSentryTheme(
                darkTheme = when (themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                },
            ) {
                val url by pendingUrl.collectAsState()
                LinkSentryNavHost(
                    initialUrl = url,
                    onUrlInspected = { pendingUrl.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTop: a second link tap while we're open arrives here.
        pendingUrl.value = intentUrl(intent)
    }

    private fun intentUrl(intent: Intent?): String? = intent?.takeIf { it.action == android.content.Intent.ACTION_VIEW }?.dataString
}
