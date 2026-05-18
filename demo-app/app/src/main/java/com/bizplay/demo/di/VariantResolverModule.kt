package com.bizplay.demo.di

import com.bizplay.core.scope.LoggedInScoped
import com.bizplay.core.variant.VariantCapabilities
import com.bizplay.core.variant.VariantContext
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn

/**
 * The **single point of dispatch** for variant-specific policies.
 *
 * Each `:variants-{id}` module contributes its policies into a multibindings map
 * keyed by [com.bizplay.core.scope.VariantKey]. This resolver picks the active
 * entry using [VariantContext.id]; no other code in the codebase is allowed to
 * read `variant.id` for dispatch.
 *
 * Installed in [LoggedInComponent] so the active variant is fixed for the
 * lifetime of the session.
 */
@Module
@InstallIn(LoggedInComponent::class)
object VariantResolverModule {

    @Provides
    @LoggedInScoped
    fun activeCapabilities(
        ctx: VariantContext,
        registry: Map<String, @JvmSuppressWildcards VariantCapabilities>,
    ): VariantCapabilities = registry[ctx.id.value]
        ?: error("No VariantCapabilities bound for variantId=${ctx.id.value}. " +
            "Check that :variants-${ctx.id.value} is on the :app classpath.")
}
