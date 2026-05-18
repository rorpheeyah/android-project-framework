package com.bizplay.demo.di

import com.bizplay.aoscore.security.NoopSecurityProvider
import com.bizplay.aoscore.security.SecureKeypad
import com.bizplay.aoscore.security.SecurityProvider
import com.bizplay.aoscore.security.StubSecureKeypad
import com.bizplay.core.boot.BootCoordinator
import com.bizplay.core.session.SessionHolder
import com.bizplay.demo.boot.BootCoordinatorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the `:core` gateway interfaces to their `:app` implementations.
 * Auth/repo bindings live in `:data/DataModule`; variant policy bindings live
 * in `:variants-{id}/<Variant>Module`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {

    @Binds
    @Singleton
    abstract fun bootCoordinator(impl: BootCoordinatorImpl): BootCoordinator

    @Binds
    @Singleton
    abstract fun sessionHolder(impl: LoggedInSessionHolder): SessionHolder

    @Binds
    @Singleton
    abstract fun securityProvider(impl: NoopSecurityProvider): SecurityProvider

    @Binds
    @Singleton
    abstract fun secureKeypad(impl: StubSecureKeypad): SecureKeypad
}
