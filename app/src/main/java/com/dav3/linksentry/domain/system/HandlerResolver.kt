package com.dav3.linksentry.domain.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.dav3.linksentry.domain.model.HandlerApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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

    override suspend fun allLaunchableApps(): List<HandlerApp> = withContext(Dispatchers.Default) {
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
