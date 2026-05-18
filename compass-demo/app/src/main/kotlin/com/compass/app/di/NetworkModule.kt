package com.compass.app.di

import com.aos.core.network.AuthHeaderInterceptor
import com.aos.core.network.BaseUrlInterceptor
import com.aos.core.network.BaseUrlProvider
import com.aos.core.network.HttpClient
import com.aos.core.storage.EncryptedPrefs
import com.compass.app.demo.DemoAuthInterceptor
import com.compass.app.session.AccountIdInterceptor
import com.compass.app.BuildConfig
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TOKEN_KEY = "access_token"
    private const val PLACEHOLDER_BASE_URL = "https://placeholder.invalid/"

    @Provides @Singleton
    fun baseUrlProvider(): BaseUrlProvider = BaseUrlProvider().apply {
        set(PLACEHOLDER_BASE_URL)
    }

    @Provides @Singleton
    fun moshi(): Moshi = HttpClient.moshi()

    @Provides @Singleton
    fun okHttpClient(
        baseUrlProvider: BaseUrlProvider,
        accountIdInterceptor: AccountIdInterceptor,
        demoAuthInterceptor: DemoAuthInterceptor,
        encryptedPrefs: EncryptedPrefs,
    ): OkHttpClient = HttpClient.okHttp(
        interceptors = listOfNotNull(
            // Demo-only short-circuit; in production builds this would be omitted
            // and the request would hit the MG-discovered API URL instead.
            demoAuthInterceptor.takeIf { BuildConfig.DEBUG },
            BaseUrlInterceptor(baseUrlProvider),
            AuthHeaderInterceptor { encryptedPrefs.getString(TOKEN_KEY) },
            accountIdInterceptor,
        ),
        loggingEnabled = BuildConfig.DEBUG,
    )

    @Provides @Singleton
    fun retrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        HttpClient.retrofit(PLACEHOLDER_BASE_URL, okHttpClient, moshi)
}
