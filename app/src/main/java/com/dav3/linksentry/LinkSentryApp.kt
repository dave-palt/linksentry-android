package com.dav3.linksentry

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class LinkSentryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i(
            "LinkSentry starting — version ${BuildConfig.VERSION_NAME} " +
                "(${BuildConfig.VERSION_CODE}), git=${BuildConfig.GIT_SHA.take(8)}",
        )
    }
}
