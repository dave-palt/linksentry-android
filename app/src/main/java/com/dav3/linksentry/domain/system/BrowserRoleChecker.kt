package com.dav3.linksentry.domain.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Whether LinkSentry currently holds the system browser role. */
interface BrowserRoleChecker {
    fun isDefaultBrowser(): Boolean
}

@Singleton
class DefaultBrowserRoleChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) : BrowserRoleChecker {

    override fun isDefaultBrowser(): Boolean {
        // Robolectric shadows RoleManager poorly; the legacy resolve path is
        // deterministic everywhere, so prefer it when it answers.
        val resolved = legacyResolve()
        if (resolved != null) return resolved == context.packageName

        return if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java) ?: return false
            rm.isRoleHeld(RoleManager.ROLE_BROWSER)
        } else {
            false
        }
    }

    /**
     * Resolves a generic https intent without MATCH_ALL — the system returns
     * the user's chosen default handler, or null if a chooser would show.
     * Package-visibility caveat: on API 30+ our <queries> block makes browsers
     * and deep-link handlers visible, so a third-party default still resolves.
     */
    private fun legacyResolve(): String? {
        val probe = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com/"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            pm.resolveActivity(probe, 0)?.activityInfo?.packageName
        } catch (_: Exception) {
            null
        }
    }
}
