# 03 · `:core` — Domain & Contract Layer

> **Type:** Local Android library
> **Role:** The **blueprint**. Defines how `:features`, `:data`, and `:variants-*` communicate.
> **Stability:** Medium — changes propagate widely; treat as a public API.

---

## 1. Purpose

`:core` is the **contract layer**. It owns:

- **Repository interfaces** — what `:data` implements
- **Policy interfaces** — what `:variants-*` implement
- **Domain models** — the shared vocabulary (`Money`, `UserSession`, `AccountSummary`)
- **`VariantContext`** — the immutable variant snapshot resolved at login
- **`RuntimeConfig`** — the typed container of values returned by MG at boot
- **`Session`** and **`DepartmentAccount`** — multi-account session state
- **MVI base contracts** — sealed-interface anchors that every feature ViewModel reuses
- **`@LoggedInScoped`** — the Hilt scope annotation used by both `:data` and `:variants-*`

`:core` is the bridge that lets all four sibling modules evolve independently. **All of them depend on `:core`; none depend on each other.**

`:core` contains **no implementation logic**. No Retrofit, no Hilt providers (only the scope annotation), no Compose, no Android dependencies beyond ViewModel base classes.

---

## 2. What Goes In `:core`

### 2.1 `variant/`

| Type | Kind | Responsibility |
|---|---|---|
| `VariantContext` | data class | Snapshot describing the active variant: `id`, `displayName`, `marketCode`, `defaultCurrency`. Resolved once at login; immutable for the session. |
| `VariantId` | value class | Type-safe wrapper around the variant identifier string. |

> The variant cannot change inside a session — `VariantContext` is a constructor-time value passed into `LoggedInComponent`. There is no `currentVariant: StateFlow<…>` and no provider interface that could re-emit.

### 2.2 `runtime/`

| Type | Kind | Responsibility |
|---|---|---|
| `RuntimeConfig` | data class | What MG returned at boot: `urls`, `maintenance`, `forceUpdate`. Immutable for the process lifetime. |
| `ApiUrls` | data class | Named base URLs: `main`, `auxiliary`, etc. Consumed by `BaseUrlProvider` in `:aos-core`. |
| `MaintenanceState` | sealed class | `Up` or `Down(message, eta)` |
| `ForceUpdate` | data class | `minimumVersionCode`, `storeUrl` |

### 2.3 `session/`

| Type | Kind | Responsibility |
|---|---|---|
| `Session` | class | Holds `userSession: UserSession`, `accounts: List<DepartmentAccount>`, `activeAccountId: StateFlow<AccountId>`. Lives inside `LoggedInComponent`. |
| `DepartmentAccount` | data class | One of the accounts a logged-in user has access to. Carries `id`, `displayName`, `accountType`, `currency`. |
| `AccountId` | value class | Type-safe account identifier. |

→ Detail: [12 — Departments and Session](12-departments-and-session.md)

### 2.4 `repository/`

Pure interfaces. No Android imports, no implementation logic. Implemented by `:data`.

```kotlin
interface TransferRepository {
    suspend fun resolveBeneficiary(qrPayload: String): Result<Beneficiary>
    suspend fun submit(intent: TransferIntent): Result<TransferReceipt>
    fun feeQuote(amount: Money): Flow<FeeQuote>
}

interface AuthRepository {
    suspend fun login(credential: LoginCredential): Result<LoginResponse>  // → variantId + accounts
    suspend fun requestOtp(phone: String): Result<OtpHandle>
    suspend fun verifyOtp(handle: OtpHandle, code: String): Result<UserSession>
    suspend fun logout(): Result<Unit>
}

interface AccountRepository {
    fun balances(accountId: AccountId): Flow<List<AccountBalance>>
    suspend fun history(accountId: AccountId, page: Int): Result<TransactionPage>
}
```

These are **the contract** between the UI and the unified backend. `:features` only ever sees these interfaces. `:data` provides one impl of each, and that impl serves every variant — the server demuxes.

### 2.5 `policy/`

Pluggable per-variant rules. Implemented by `:variants-*`. The variant-specific surface that `:features` consumes:

```kotlin
interface TransferAmountPolicy {
    fun validate(amount: Money): ValidationResult
    val dailyLimit: Money
}

interface FeeCalculator {
    fun quote(amount: Money, channel: TransferChannel): FeeQuote
}

interface AmountFormatter {
    fun format(amount: Money): String
}

interface VariantCapabilities {
    fun supportsKhqrScan(): Boolean
    fun supportsCardlessAtm(): Boolean
    fun supportsBilingualReceipt(): Boolean
    // …
}
```

`:features` injects these by interface; the active variant module provides the impl. To gate UI on a capability, the ViewModel reads `capabilities.supportsKhqrScan()` and stores it in `UiState` — never on `variantId`.

### 2.6 `model/`

Domain types that cross the contract boundary. Every type here is **immutable**, **serializable**, and **variant-agnostic**.

| Model | Purpose |
|---|---|
| `Money` | Amount + currency. Avoids float arithmetic; uses `BigDecimal` internally. |
| `Currency` | Enum or sealed class: `KHR`, `VND`, `USD`, … |
| `UserSession` | `userId`, `tokens`, `expiresAt`, `variantId` |
| `LoginResponse` | `userSession`, `accounts: List<DepartmentAccount>`, `variantId` |
| `Beneficiary` | Resolved transfer target (account number, bank code, holder name) |
| `TransferIntent` | What the user is trying to do (source, dest, amount, narrative) |
| `TransferReceipt` | What happened (id, status, fee, timestamp) |
| `AccountBalance` | Account ID, currency, available/ledger amounts |

### 2.7 `mvi/`

Base contracts the entire `:features` module conforms to. See [09 — MVI Pattern](09-mvi-pattern.md) for usage.

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

### 2.8 `scope/`

The `@LoggedInScoped` annotation lives here so that both `:data` and `:variants-*` can apply it without importing `:app`:

```kotlin
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class LoggedInScoped
```

The Hilt component definition (`LoggedInComponent`) lives in `:app` — see [10 — Boot Phases](10-boot-phases.md).

---

## 3. What Does NOT Go In `:core`

| ❌ Doesn't belong | ✅ Goes in |
|---|---|
| `interface FintechApi` (Retrofit) | `:data` |
| `class FintechTransferRepo` (impl) | `:data` |
| DTOs / Moshi adapters | `:data` |
| `class KhTransferAmountPolicy` (impl) | `:variants-kh` |
| Compose `LoginScreen` | `:features` |
| `LoginViewModel` | `:features` |
| `OkHttpClient` configuration | `:aos-core` |
| `if (variantId == "kh")` logic | nowhere |
| MG endpoint URL | `:app` (build-time per environment) |

> **Smell test:** if a class in `:core` knows the name of a specific variant, it is misplaced. Move it.

---

## 4. Stability Discipline

`:core` is the **most-imported product module**. A change here forces recompile of `:data`, `:features`, AND every `:variants-*` module. Treat changes accordingly:

- **Adding** a method to a repository or policy interface → low-risk, but requires updating impls (`:data` for repos, every variant for policies).
- **Removing** a method → review every impl first; coordinate with affected teams.
- **Renaming** a domain model field → expensive; prefer additive deprecation.

Every PR that touches `:core` must list which downstream modules need follow-up changes.

---

## 5. Public Surface

```
com.compass.core.variant        ← VariantContext, VariantId
com.compass.core.runtime        ← RuntimeConfig, ApiUrls, MaintenanceState, ForceUpdate
com.compass.core.session        ← Session, DepartmentAccount, AccountId
com.compass.core.repository     ← *Repository interfaces
com.compass.core.policy         ← *Policy interfaces, VariantCapabilities, AmountFormatter
com.compass.core.model          ← Money, UserSession, LoginResponse, Beneficiary, …
com.compass.core.mvi            ← UiState, UiEvent, UiEffect, MviViewModel
com.compass.core.scope          ← @LoggedInScoped
```

There are no `internal` types in `:core` worth mentioning — almost everything is part of the public surface, by design. That's what makes it a contract layer.

---

## 6. Cross-references

- How `:features` consumes these contracts: [06 — `:features`](06-features.md)
- How `:data` implements the repository contracts: [05 — `:data`](05-data.md)
- How `:variants-*` implements the policy contracts: [07 — `:variants-*`](07-variants.md)
- The `LoggedInComponent` that scopes most of these instances: [10 — Boot Phases](10-boot-phases.md)
- MVI base usage details: [09 — MVI Pattern](09-mvi-pattern.md)
