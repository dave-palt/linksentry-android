package com.dav3.linksentry.domain.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.dav3.linksentry.domain.model.HandlerApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Enumerates installed apps that can handle a URL. */
interface HandlerResolver {
    suspend fun resolve(uri: Uri): List<HandlerApp>

    /**
     * Every launchable app on the device (launcher-style MAIN/LAUNCHER
     * query) — the "search all apps" fallback for URLs no declared
     * handler matches. Requires the launcher `<queries>` intent.
     */
    suspend fun allLaunchableApps(): List<HandlerApp>

    /**
     * Live view of the all-apps list behind [allLaunchableApps]; empty
     * until first loaded (see [warmAllAppsCache]).
     */
    val allAppsCache: StateFlow<List<HandlerApp>>

    /**
     * (Re)load the all-apps list into the cache when empty. Used by
     * [allLaunchableApps] and the process-start preload; the resume path
     * calls [refreshAllAppsCache] to revalidate.
     */
    suspend fun warmAllAppsCache()

    /**
     * Force a re-query; re-emits only when the list actually changed.
     * The resume path uses this: newly installed/removed apps pop into
     * the picker without a full reload.
     */
    suspend fun refreshAllAppsCache()
}

/**
 * PackageManager-backed implementation.
 *
 * Platform notes:
 * - `MATCH_ALL` bypasses default-handler filtering so the full handler set is
 *   always returned (otherwise browsers can be omitted from query results).
 * - Manifest `<queries>` declarations (http + https VIEW/BROWSABLE) grant
 *   package visibility on API 30+.
 * - Results pointing at our own package are always dropped — re-dispatching an
 *   implicit VIEW intent would loop straight back into LinkSentry (we may be
 *   the default browser). Launches go through explicit intents only.
 */
@Singleton
class DefaultHandlerResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : HandlerResolver {

    /**
     * Stale-while-revalidate cache of every launchable app. Empty until
     * first warmed (process start / first use); refreshes update it only
     * when the device state actually changed so collectors don't churn.
     * Memory-only on purpose: no new persistence or permissions, and the
     * start-up preload covers process rebirth.
     */
    private val _allAppsCache = MutableStateFlow<List<HandlerApp>>(emptyList())
    override val allAppsCache: StateFlow<List<HandlerApp>> = _allAppsCache.asStateFlow()

    /** Serializes cache refreshes (resume + preload can race). */
    private val refreshMutex = Mutex()

    override suspend fun warmAllAppsCache() {
        // Return the cached list instantly; refresh only when empty —
        // resume-driven revalidation calls [refreshAllAppsCache] instead.
        if (_allAppsCache.value.isEmpty()) {
            refreshAllAppsCache()
        }
    }

    /** Force a re-query; re-emits only when the list actually changed. */
    override suspend fun refreshAllAppsCache() {
        refreshMutex.withLock {
            val fresh = queryAllLaunchableApps()
            if (fresh != _allAppsCache.value) {
                _allAppsCache.value = fresh
            }
        }
    }

    override suspend fun allLaunchableApps(): List<HandlerApp> {
        warmAllAppsCache()
        return _allAppsCache.value
    }

    override suspend fun resolve(uri: Uri): List<HandlerApp> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val viewIntent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        val raw = query(pm, viewIntent)

        // A wildcard-host probe: only true browsers (filters without a host
        // restriction) match a host like this. Host-specific deep-link apps
        // (e.g. YouTube) will not.
        val browserProbe = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://$PROBE_HOST/"),
        ).addCategory(Intent.CATEGORY_BROWSABLE)
        val browserPkgs = query(pm, browserProbe)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()

        raw.asSequence()
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != context.packageName } // never offer ourselves
            .distinctBy { it.packageName } // one row per app
            .map {
                HandlerApp(
                    packageName = it.packageName,
                    activityName = it.name,
                    label = it.loadLabel(pm).toString(),
                    isBrowser = it.packageName in browserPkgs,
                    icon = runCatching { it.loadIcon(pm) }.getOrNull(),
                )
            }
            .sortedWith(
                compareByDescending<HandlerApp> { it.isBrowser }
                    .thenBy { it.label.lowercase() },
            )
            .toList()
    }

    private fun query(pm: PackageManager, intent: Intent): List<android.content.pm.ResolveInfo> = if (Build.VERSION.SDK_INT >= 33) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    }

    private suspend fun queryAllLaunchableApps(): List<HandlerApp> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        query(pm, launcherIntent)
            .asSequence()
            .mapNotNull { it.activityInfo }
            // MATCH_ALL also surfaces DISABLED components (e.g. alternate
            // launcher-icon aliases) — offering those would yield dead rows.
            .filter { it.enabled }
            .filter { it.packageName != context.packageName } // never offer ourselves
            .distinctBy { it.packageName } // one row per app
            .map {
                HandlerApp(
                    packageName = it.packageName,
                    activityName = it.name,
                    label = it.loadLabel(pm).toString(),
                    isBrowser = false,
                    icon = runCatching { it.loadIcon(pm) }.getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private companion object {
        const val PROBE_HOST = "linksentry-probe.invalid"
    }
}
