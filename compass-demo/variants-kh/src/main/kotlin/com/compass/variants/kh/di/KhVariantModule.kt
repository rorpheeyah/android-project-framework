package com.compass.variants.kh.di

import com.compass.core.policy.AmountFormatter
import com.compass.core.policy.SupportContacts
import com.compass.core.policy.VariantCapabilities
import com.compass.core.scope.VariantKey
import com.compass.variants.kh.capability.KhCapabilities
import com.compass.variants.kh.format.KhrAmountFormatter
import com.compass.variants.kh.support.KhSupportContacts
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

/**
 * KH variant's bindings. Every policy is added to a `Map<String, Policy>` via
 * @VariantKey("kh"); the active variant is picked by VariantResolverModule in `:app`.
 *
 * `:variants-kh` contains only policies + DI. No UI, no networking, no DTOs.
 *
 * Installed in SingletonComponent for demo simplicity — production would install
 * in LoggedInComponent so policies die on logout. See docs/10-boot-phases.md.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class KhVariantModule {

    @Binds @IntoMap @VariantKey("kh")
    abstract fun capabilities(impl: KhCapabilities): VariantCapabilities

    @Binds @IntoMap @VariantKey("kh")
    abstract fun amountFormatter(impl: KhrAmountFormatter): AmountFormatter

    @Binds @IntoMap @VariantKey("kh")
    abstract fun supportContacts(impl: KhSupportContacts): SupportContacts
}
