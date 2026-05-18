package com.bizplay.core.scope

import dagger.MapKey
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.BINARY)
annotation class LoggedInScoped

@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class VariantKey(val value: String)

@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class TenantKey(val value: String)
