package com.compass.variants.vn.di

import com.compass.core.policy.AmountFormatter
import com.compass.core.policy.SupportContacts
import com.compass.core.policy.VariantCapabilities
import com.compass.core.scope.VariantKey
import com.compass.variants.vn.capability.VnCapabilities
import com.compass.variants.vn.format.VndAmountFormatter
import com.compass.variants.vn.support.VnSupportContacts
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
internal abstract class VnVariantModule {

    @Binds @IntoMap @VariantKey("vn")
    abstract fun capabilities(impl: VnCapabilities): VariantCapabilities

    @Binds @IntoMap @VariantKey("vn")
    abstract fun amountFormatter(impl: VndAmountFormatter): AmountFormatter

    @Binds @IntoMap @VariantKey("vn")
    abstract fun supportContacts(impl: VnSupportContacts): SupportContacts
}
