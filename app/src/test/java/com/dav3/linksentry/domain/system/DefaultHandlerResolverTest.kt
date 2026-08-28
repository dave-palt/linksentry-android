package com.dav3.linksentry.domain.system

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Cache behavior of [DefaultHandlerResolver]: the all-apps list is loaded
 * once, reused instantly (no PackageManager re-query per link), and
 * revalidated in the background — a changed device state (new install,
 * uninstall) updates the cache without re-emitting identical lists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultHandlerResolverTest {

    private lateinit var resolver: DefaultHandlerResolver

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        resolver = DefaultHandlerResolver(context)
    }

    private fun launchable(pkg: String): ResolveInfo = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            packageName = pkg
            name = "$pkg.MainActivity"
            enabled = true
            nonLocalizedLabel = pkg
        }
    }

    private fun seedLauncherApps(vararg pkgs: String) {
        val pm = ApplicationProvider.getApplicationContext<Context>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pkgs.forEach { shadowOf(pm).addResolveInfoForIntent(intent, launchable(it)) }
    }

    private suspend fun awaitCacheNonEmpty() {
        withTimeout(5_000) {
            resolver.allAppsCache.first { it.isNotEmpty() }
        }
    }

    @Test
    fun allLaunchableApps_returns_launchable_apps_sorted_by_label() = runBlocking {
        seedLauncherApps("com.zeta.app", "com.alpha.app")
        val apps = resolver.allLaunchableApps()
        assert(apps.map { it.packageName } == listOf("com.alpha.app", "com.zeta.app"))
    }

    @Test
    fun warmAllAppsCache_populates_the_cache() = runBlocking {
        seedLauncherApps("com.alpha.app")
        resolver.warmAllAppsCache()
        awaitCacheNonEmpty()
        assert(resolver.allAppsCache.value.map { it.packageName } == listOf("com.alpha.app"))
    }

    @Test
    fun warmAllAppsCache_refresh_picks_up_newly_installed_app() = runBlocking {
        seedLauncherApps("com.alpha.app")
        resolver.warmAllAppsCache()
        awaitCacheNonEmpty()
        assert(resolver.allAppsCache.value.map { it.packageName } == listOf("com.alpha.app"))

        // A new app appears on the device (install) — the resume-path
        // refresh picks it up.
        seedLauncherApps("com.alpha.app", "com.new.app")
        resolver.refreshAllAppsCache()
        withTimeout(5_000) {
            resolver.allAppsCache.first { it.map { a -> a.packageName }.contains("com.new.app") }
        }
        assert(resolver.allAppsCache.value.map { it.packageName } == listOf("com.alpha.app", "com.new.app"))
    }

    @Test
    fun warmAllAppsCache_does_not_reemit_identical_list() = runBlocking {
        seedLauncherApps("com.alpha.app", "com.beta.app")
        resolver.warmAllAppsCache()
        awaitCacheNonEmpty()

        var emissions = 0
        val observer = launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            resolver.allAppsCache.collect { emissions++ }
        }
        // Two refreshes with identical device state.
        resolver.warmAllAppsCache()
        resolver.warmAllAppsCache()
        // Give the warm jobs time to complete.
        withTimeout(5_000) {
            kotlinx.coroutines.delay(500)
        }
        observer.cancel()
        // Exactly one emission (the initial cache value) — no re-emission.
        assert(emissions == 1) { "expected 1 emission, got $emissions" }
    }
}
