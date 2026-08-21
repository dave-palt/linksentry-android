package com.dav3.linksentry.domain.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.dav3.linksentry.domain.model.HandlerApp
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Side effects the Inspect UI can trigger: open in a chosen app, share,
 * copy, jump to system settings. Interface so ViewModels stay testable.
 */
interface LinkActions {
    /** Launch the chosen app for the URL via an EXPLICIT intent. */
    fun openWith(app: HandlerApp, url: String): Boolean

    fun share(url: String)

    fun copy(text: String)

    fun openDefaultAppsSettings()
}

@Singleton
class AndroidLinkActions @Inject constructor(
    @ApplicationContext private val context: Context,
) : LinkActions {

    override fun openWith(app: HandlerApp, url: String): Boolean {
        val intent = buildOpenIntent(app, url)
        return try {
            context.startActivity(intent)
            true
        } catch (e: android.content.ActivityNotFoundException) {
            Timber.w(e, "Handler %s no longer available", app.packageName)
            false
        }
    }

    override fun share(url: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun copy(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("LinkSentry", text))
    }

    override fun openDefaultAppsSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        /**
         * Explicit component launch — the ONLY way LinkSentry dispatches URLs.
         * Never re-fire the implicit VIEW intent: as the default browser it
         * would resolve straight back to LinkSentry (infinite loop).
         */
        fun buildOpenIntent(app: HandlerApp, url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setClassName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
