package com.compass.features.auth.login

import com.compass.core.policy.SupportContacts
import com.compass.core.policy.VariantCapabilities

/**
 * Pre-login the LoggedInComponent doesn't exist yet, so the variant-specific
 * policies aren't available the usual way. The orchestrator (`:app`) exposes
 * a "default variant" set of bindings keyed off the build's primary variant
 * so the login screen can still show the right support hotline and decide
 * whether to render the institution code field.
 *
 * Bundled into one small container so the ViewModel takes a single dep.
 */
data class PreLoginPolicies(
    val capabilities: VariantCapabilities,
    val supportContacts: SupportContacts,
)
