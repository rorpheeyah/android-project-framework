package com.aos.core.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Banking-agnostic HTTP plumbing. Owns the OkHttp + Retrofit construction
 * and nothing about which product is being built.
 */
object HttpClient {

    fun okHttp(
        interceptors: List<Interceptor> = emptyList(),
        loggingEnabled: Boolean = false,
        timeoutSeconds: Long = 30,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        interceptors.forEach(builder::addInterceptor)
        if (loggingEnabled) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
            )
        }
        return builder.build()
    }

    fun moshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    fun retrofit(baseUrl: String, client: OkHttpClient, moshi: Moshi = moshi()): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
}
