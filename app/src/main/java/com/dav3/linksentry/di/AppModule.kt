package com.dav3.linksentry.di

import com.dav3.linksentry.domain.system.AndroidLinkActions
import com.dav3.linksentry.domain.system.BrowserRoleChecker
import com.dav3.linksentry.domain.system.DefaultBrowserRoleChecker
import com.dav3.linksentry.domain.system.DefaultHandlerResolver
import com.dav3.linksentry.domain.system.HandlerResolver
import com.dav3.linksentry.domain.system.LinkActions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindHandlerResolver(impl: DefaultHandlerResolver): HandlerResolver

    @Binds
    @Singleton
    abstract fun bindLinkActions(impl: AndroidLinkActions): LinkActions

    @Binds
    @Singleton
    abstract fun bindBrowserRoleChecker(impl: DefaultBrowserRoleChecker): BrowserRoleChecker
}
