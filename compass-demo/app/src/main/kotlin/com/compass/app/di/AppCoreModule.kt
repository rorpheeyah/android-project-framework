package com.compass.app.di

import android.content.Context
import com.aos.core.storage.EncryptedPrefs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppCoreModule {

    @Provides @Singleton
    fun encryptedPrefs(@ApplicationContext context: Context): EncryptedPrefs =
        EncryptedPrefs(context)
}
