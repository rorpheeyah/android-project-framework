package com.compass.data.di

import com.compass.core.repository.AuthRepository
import com.compass.data.api.FintechAuthApi
import com.compass.data.repo.FintechAuthRepo
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

/**
 * `:data` exposes exactly one public surface: this Hilt module. Repository
 * classes are `internal`; the rest of the app sees only the `:core` interfaces
 * they implement.
 *
 * Installed in SingletonComponent (not LoggedInComponent) for the demo to keep
 * the wiring legible. In production these would move to LoggedInComponent so
 * the repo instance dies with the session — see docs/10-boot-phases.md.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataBindingsModule {

    @Binds
    abstract fun authRepository(impl: FintechAuthRepo): AuthRepository
}

@Module
@InstallIn(SingletonComponent::class)
internal object DataApiModule {

    @Provides
    fun fintechAuthApi(retrofit: Retrofit): FintechAuthApi =
        retrofit.create(FintechAuthApi::class.java)
}
