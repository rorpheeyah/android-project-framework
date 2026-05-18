package com.compass.core.scope

import dagger.MapKey

/**
 * Map key for variant-specific multibindings. The runtime resolver in
 * `:app/di/VariantResolverModule.kt` picks the active variant's entry from
 * the map at injection time — the *single* point of dispatch by variant id
 * in the entire codebase.
 */
@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class VariantKey(val value: String)
