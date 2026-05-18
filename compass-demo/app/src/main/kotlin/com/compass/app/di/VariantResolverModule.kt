package com.compass.app.di

import com.compass.app.session.LoggedInComponentManager
import com.compass.app.variant.VariantCatalogue
import com.compass.core.policy.AmountFormatter
import com.compass.core.policy.SupportContacts
import com.compass.core.policy.VariantCapabilities
import com.compass.features.auth.login.PreLoginPolicies
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The **single point of dispatch** by variant id in the entire codebase.
 *
 * Every variant module contributes its policies to a `Map<String, Policy>`
 * via `@VariantKey("<id>") @IntoMap`. This module reads
 * `Session.variantContext.id` and picks the active entry from the map at
 * injection time.
 *
 * Nothing else in the codebase — not features, not data — branches on the
 * variant id.
 *
 * Pre-login policies are picked from the catalogue's default variant so
 * the LoginScreen can render the right support hotline before the session
 * exists.
 */
@Module
@InstallIn(SingletonComponent::class)
object VariantResolverModule {

    @Provides
    fun amountFormatter(
        manager: LoggedInComponentManager,
        formatters: Map<String, @JvmSuppressWildcards AmountFormatter>,
        catalogue: VariantCatalogue,
    ): AmountFormatter =
        formatters.pickForActive(manager, catalogue)

    @Provides
    fun variantCapabilities(
        manager: LoggedInComponentManager,
        capabilities: Map<String, @JvmSuppressWildcards VariantCapabilities>,
        catalogue: VariantCatalogue,
    ): VariantCapabilities =
        capabilities.pickForActive(manager, catalogue)

    @Provides
    fun supportContacts(
        manager: LoggedInComponentManager,
        contacts: Map<String, @JvmSuppressWildcards SupportContacts>,
        catalogue: VariantCatalogue,
    ): SupportContacts =
        contacts.pickForActive(manager, catalogue)

    @Provides
    fun preLoginPolicies(
        capabilities: Map<String, @JvmSuppressWildcards VariantCapabilities>,
        contacts: Map<String, @JvmSuppressWildcards SupportContacts>,
        catalogue: VariantCatalogue,
    ): PreLoginPolicies {
        val defaultId = catalogue.defaultVariant().id.value
        return PreLoginPolicies(
            capabilities = capabilities.getValue(defaultId),
            supportContacts = contacts.getValue(defaultId),
        )
    }

    private fun <T> Map<String, T>.pickForActive(
        manager: LoggedInComponentManager,
        catalogue: VariantCatalogue,
    ): T {
        val activeId = manager.current.value?.variantContext?.id?.value
            ?: catalogue.defaultVariant().id.value
        return getValue(activeId)
    }
}
