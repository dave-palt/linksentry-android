package com.dav3.linksentry

import android.app.Application
import com.dav3.linksentry.domain.system.HandlerResolver
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class LinkSentryApp : Application() {

    @Inject lateinit var handlerResolver: HandlerResolver

    /** App-lifetime scope for background work that must outlive screens. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i(
            "LinkSentry starting — version ${BuildConfig.VERSION_NAME} " +
                "(${BuildConfig.VERSION_CODE}), git=${BuildConfig.GIT_SHA.take(8)}",
        )
        // Preload the all-apps cache so the FIRST inspect already has the
        // handler/all-apps list ready (covers cold start, install, update
        // and process rebirth). Off the main thread; failure is non-fatal —
        // the next inspect falls back to loading on demand.
        appScope.launch {
            runCatching { handlerResolver.warmAllAppsCache() }
                .onFailure { Timber.w(it, "All-apps preload failed") }
        }
    }
}
