package com.compass.app.di

import com.compass.app.session.LoggedInComponentManager
import com.compass.core.session.Session
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Bridges Session — built only after login — to the singleton component so
 * ViewModels can inject it directly.
 *
 * Calling this provider before login throws. ViewModels that touch Session
 * therefore live only on routes that aren't reachable until the
 * LoggedInComponent is built — enforced by `AppNavigation`.
 */
@Module
@InstallIn(SingletonComponent::class)
object LoggedInBindingsModule {

    @Provides
    fun session(manager: LoggedInComponentManager): Session = manager.require()
}
