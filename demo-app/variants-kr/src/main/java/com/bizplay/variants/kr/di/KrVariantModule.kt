package com.bizplay.variants.kr.di

import com.bizplay.core.scope.VariantKey
import com.bizplay.core.variant.VariantCapabilities
import com.bizplay.variants.kr.KrCapabilities
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

/**
 * Public Hilt surface of :variants-kr.
 *
 * Per framework convention, each variant module contributes its policies into a
 * multibindings map keyed by [VariantKey]. The :app module's `VariantResolverModule`
 * is the **single point of dispatch** that selects the active variant's entries.
 * No other code branches on variant id.
 *
 * To keep the demo small we bind at SingletonComponent. In a fuller wiring these
 * would live in LoggedInComponent so they're dropped together with the session.
 */
@Module
@InstallIn(SingletonComponent::class)
object KrVariantModule {

    @Provides
    @IntoMap
    @VariantKey("kr")
    fun provideCapabilities(): VariantCapabilities = KrCapabilities()
}
