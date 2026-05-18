package com.bizplay.data.di

import com.bizplay.core.repository.AuthRepository
import com.bizplay.data.repo.IpppAuthRepo
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The single public surface of :data. Everything else in this module is `internal`.
 *
 * AuthRepository is bound at the SingletonComponent level because login runs before
 * LoggedInComponent exists. Post-login repositories (Receipt, Approval, Card, …)
 * would be bound to a LoggedInComponent in a fuller demo.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    @Singleton
    abstract fun authRepository(impl: IpppAuthRepo): AuthRepository
}
