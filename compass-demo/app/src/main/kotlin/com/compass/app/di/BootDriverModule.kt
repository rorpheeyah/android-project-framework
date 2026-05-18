package com.compass.app.di

import com.compass.app.boot.BootCoordinator
import com.compass.features.boot.BootDriver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds `:features`'s [BootDriver] interface to `:app`'s [BootCoordinator]
 * implementation. Keeps `:features` from importing `:app`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BootDriverModule {

    @Binds
    abstract fun bootDriver(impl: BootCoordinator): BootDriver
}
