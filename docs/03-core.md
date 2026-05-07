# 03 · `:core` — Domain & Contract Layer

> **Type:** Local Android library
> **Role:** The **blueprint**. Defines how `:features` and `:tenants:*` communicate.
> **Stability:** Medium — changes propagate widely; treat as a public API.

---

## 1. Purpose

`:core` is the **contract layer**. It owns:

- **Repository interfaces** — what tenants must implement
- **Domain models** — the shared vocabulary (Money, UserSession, AccountSummary)
- **Tenant context** — the `TenantProvider` and `TenantContext` types
- **MVI base contracts** — sealed-interface anchors that every feature ViewModel reuses

`:core` is the bridge that lets `:features` and `:tenants:*` evolve independently. **Both depend on `:core`; neither depends on the other.**

---

## 2. What Goes In `:core`

### 2.1 `tenant/`

| Type | Kind | Responsibility |
|---|---|---|
| `TenantContext` | data class | Snapshot describing the active tenant: `id`, `displayName`, `marketCode`, `defaultCurrency`, `baseUrlKey` |
| `TenantProvider` | interface | Source of truth for the active tenant. Exposes `currentTenant: StateFlow<TenantContext>` and `switchTo(id)`. |
| `TenantId` | value class | Type-safe wrapper around the tenant identifier string. |

### 2.2 `repository/`

Pure interfaces. No Android imports, no implementation logic.

```kotlin
interface TransferRepository {
    suspend fun resolveBeneficiary(qrPayload: String): Result<Beneficiary>
    suspend fun submit(intent: TransferIntent): Result<TransferReceipt>
    fun feeQuote(amount: Money): Flow<FeeQuote>
}

interface AuthRepository {
    suspend fun login(credential: LoginCredential): Result<UserSession>
    suspend fun requestOtp(phone: String): Result<OtpHandle>
    suspend fun verifyOtp(handle: OtpHandle, code: String): Result<UserSession>
}

interface AccountRepository {
    fun balances(): Flow<List<AccountBalance>>
    suspend fun history(accountId: AccountId, page: Int): Result<TransactionPage>
}
```

These interfaces are **the contract**. Each tenant module provides an implementation; `:features` only ever sees the interface.

### 2.3 `model/`

Domain types that cross the contract boundary. Every type here is **immutable**, **serializable**, and **tenant-agnostic**.

| Model | Purpose |
|---|---|
| `Money` | Amount + currency. Avoids float arithmetic; uses `BigDecimal` internally. |
| `Currency` | Enum or sealed class: `KHR`, `VND`, `USD`, … |
| `UserSession` | `userId`, `tokens`, `expiresAt`, `tenantId` |
| `Beneficiary` | Resolved transfer target (account number, bank code, holder name) |
| `TransferIntent` | What the user is trying to do (source, dest, amount, narrative) |
| `TransferReceipt` | What happened (id, status, fee, timestamp) |
| `AccountBalance` | Account ID, currency, available/ledger amounts |

### 2.4 `mvi/`

Base contracts the entire `:features` module conforms to. See [07 — MVI Pattern](07-mvi-pattern.md) for usage.

```kotlin
interface UiState
interface UiEvent
interface UiEffect

abstract class MviViewModel<S : UiState, E : UiEvent, F : UiEffect> : ViewModel() {
    abstract val initialState: S
    val state: StateFlow<S>
    val effects: SharedFlow<F>
    abstract fun onEvent(event: E)
    protected fun setState(reducer: S.() -> S)
    protected suspend fun emitEffect(effect: F)
}
```

### 2.5 `tenant/policy/` (optional, encouraged)

Pluggable per-tenant policies that aren't full repositories — too small to deserve their own interface family but too tenant-specific to live in `:features`:

```kotlin
interface TransferAmountPolicy {
    fun validate(amount: Money): ValidationResult
    val dailyLimit: Money
}

interface FeeCalculator {
    fun quote(amount: Money, channel: TransferChannel): FeeQuote
}
```

`:features` injects these by interface; `:tenants:*` provides the impl.

---

## 3. What Does NOT Go In `:core`

| ❌ Doesn't belong | ✅ Goes in |
|---|---|
| Retrofit `interface CambodiaApi` | `:tenants:tenants-kh` |
| `BakongTransferRepo` | `:tenants:tenants-kh` |
| Compose `LoginScreen` | `:features` |
| `LoginViewModel` | `:features` |
| `OkHttpClient` configuration | `:aos-core` |
| `if (tenant == "kh")` logic | nowhere |

> **Smell test:** if a class in `:core` knows the name of a specific tenant, it is misplaced. Move it.

---

## 4. Stability Discipline

`:core` is the **most-imported product module**. A change here forces recompile of `:features` AND every `:tenants:*` module. Treat changes accordingly:

- **Adding** a method to a repository interface → low-risk, but requires updating every tenant impl.
- **Removing** a method → review every tenant impl first; coordinate with the affected tenant teams.
- **Renaming** a domain model field → expensive; prefer additive deprecation.

Every PR that touches `:core` must list which tenants need follow-up changes.

---

## 5. Public Surface

```
com.nexus.core.tenant       ← TenantProvider, TenantContext, TenantId
com.nexus.core.repository   ← *Repository interfaces
com.nexus.core.model        ← Money, UserSession, Beneficiary, …
com.nexus.core.mvi          ← UiState, UiEvent, UiEffect, MviViewModel
com.nexus.core.tenant.policy ← TransferAmountPolicy, FeeCalculator, …
```

There are no `internal` types in `:core` worth mentioning — almost everything is part of the public surface, by design. That's what makes it a contract layer.

---

## 6. Cross-references

- How `:features` consumes these contracts: [04 — `:features`](04-features.md)
- How `:tenants:*` implements these contracts: [05 — `:tenants:*`](05-tenants.md)
- End-to-end concrete example: [10 — Contract Walkthrough](10-contract-implementation-example.md)
- MVI base usage details: [07 — MVI Pattern](07-mvi-pattern.md)
