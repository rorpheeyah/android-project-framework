package com.bizplay.aoscore.security


import javax.inject.Inject

/**
 * Wraps mVaccine (raonsecure.mvaccine.MVaccineManager in the existing Bizplay codebase)
 * plus root / tampering checks. Called once on cold start from BootCoordinator;
 * a [Threat] result aborts boot before any network I/O.
 *
 * Product-agnostic: knows nothing about receipts, expenses, or variants.
 */
interface SecurityProvider {
    suspend fun runSelfChecks(): SelfCheckResult

    sealed interface SelfCheckResult {
        data object Ok : SelfCheckResult
        data class Threat(val reason: String) : SelfCheckResult
    }
}

/**
 * Demo stub: always returns Ok. A production build wires the mVaccine,
 * AppIron, and Secucen EdgeCrypto SDKs in here.
 */
class NoopSecurityProvider @Inject constructor() : SecurityProvider {
    override suspend fun runSelfChecks(): SecurityProvider.SelfCheckResult =
        SecurityProvider.SelfCheckResult.Ok
}
