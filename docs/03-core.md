# 03 · `:core` — Domain & Contract Layer

> **Type:** Local Android library
> **Role:** The **blueprint**. Defines how `:features`, `:data`, and `:tenants:*:*` communicate.
> **Stability:** Medium — changes propagate widely; treat as a public API.

---

## 1. Purpose

`:core` is the **contract layer**. It owns:

- **Repository interfaces** — what `:data` implements
- **Policy interfaces** — what `:tenants:*:*` implement
- **Domain models** — the shared vocabulary (`Money`, `UserSession`, `AccountSummary`)
- **`TenantContext`** — the immutable tenant snapshot resolved at login
- **`RuntimeConfig`** — the typed container of values returned by MG at boot
- **`Session`** and **`DepartmentAccount`** — multi-account session state
- **MVI base contracts** — sealed-interface anchors that every feature ViewModel reuses
- **`@LoggedInScoped` + `@TenantKey`** — Hilt scope annotation and the multibinding map key used by both `:data` and `:tenants:*:*`

`:core` is the bridge that lets all sibling modules evolve independently. **All of them depend on `:core`; none depend on each other**, except for the one allowed edge: concrete tenant modules depend on their own region base.

`:core` contains **no implementation logic**. No Retrofit, no Hilt providers (only the scope annotation and map key), no Compose, no Android dependencies beyond ViewModel base classes.

---

## 2. What Goes In `:core`

### 2.1 `tenant/`

| Type | Kind | Responsibility |
|---|---|---|
| `TenantContext` | data class | Snapshot describing the active tenant: `id`, `displayName`, `regionCode`, `defaultCurrency`, `flags: TenantFlags`, `params: TenantParams`. Resolved once at login from `LoginResponse`; immutable for the session. |
| `TenantId` | value class | Type-safe wrapper around the composite tenant identifier string (`<region>:<tenantSlug>`, e.g. `"cambodia:nh"`). |
| `TenantFlags` | data class | Named boolean fields the server sets per tenant (`hidesEmployeeId`, `clearsEmployeeNumberOnApproval`, …). Client owns the schema; server owns the values. |
| `TenantParams` | data class | Named typed fields the server sets per tenant (`employeeIdRegex`, `approvalLineMaxDepth`, …). Same client-schema / server-values contract as `TenantFlags`. |
| `TenantCapabilities` | interface | Per-tenant boolean capability flags. Implemented by concrete tenant modules. |

> `TenantContext` cannot change inside a session — it is a constructor-time value passed into `LoggedInComponent`. There is no `currentTenant: StateFlow<…>` and no provider interface that could re-emit. See [19 — Tenants and Regions](19-tenants-and-variants.md) for the full behavioral model.

### 2.2 `runtime/`

| Type | Kind | Responsibility |
|---|---|---|
| `RuntimeConfig` | data class | What MG returned at boot: `urls`, `maintenance`, `forceUpdate`, plus third-party app-ids (Sendbird, Google Maps). Immutable for the process lifetime. |
| `ApiUrls` | data class | Named base URLs: `main`, `auxiliary`, etc. Consumed by `BaseUrlProvider` in `:aos-sdk`. |
| `MaintenanceState` | sealed class | `Up` or `Down(message, eta)` |
| `ForceUpdate` | data class | `minimumVersionCode`, `storeUrl` |

### 2.3 `session/`

| Type | Kind | Responsibility |
|---|---|---|
| `Session` | class | Holds `userSession: UserSession`, `accounts: List<DepartmentAccount>`, `activeAccountId: StateFlow<AccountId>`. Lives inside `LoggedInComponent`. Single-account consuming apps ship with `accounts.size == 1`. |
| `DepartmentAccount` | data class | One of the accounts a logged-in user has access to. Carries `id`, `displayName`, `accountType`, `currency`. |
| `AccountId` | value class | Type-safe account identifier. |

→ Detail: [12 — Departments and Session](12-departments-and-session.md)

### 2.4 `repository/`

Pure interfaces. No Android imports, no implementation logic. Implemented by `:data`.

```kotlin
interface LoanRepository {
    suspend fun listProducts(): Result<List<LoanProduct>>
    suspend fun productDetail(id: LoanProductId): Result<LoanProduct>
    fun myLoans(): Flow<List<Loan>>
}

interface LoanApplicationRepository {
    suspend fun submit(application: LoanApplication): Result<LoanApplicationId>
    fun applicationStatus(id: LoanApplicationId): Flow<LoanApplicationStatus>
}

interface AuthRepository {
    suspend fun login(credential: LoginCredential): Result<LoginResponse>  // → tenantId + accounts
    suspend fun requestOtp(phone: String): Result<OtpHandle>
    suspend fun verifyOtp(handle: OtpHandle, code: String): Result<UserSession>
    suspend fun logout(): Result<Unit>
}

interface RepaymentRepository {
    fun schedule(loanId: LoanId): Flow<RepaymentSchedule>
    suspend fun pay(installmentId: InstallmentId): Result<PaymentReceipt>
}

interface ChatRepository {                                  // provider-agnostic
    fun threads(): Flow<List<ChatThread>>
    fun messages(threadId: ChatThreadId): Flow<List<ChatMessage>>
    suspend fun send(threadId: ChatThreadId, content: ChatMessageContent): Result<Unit>
}
```

These are **the contract** between the UI and the unified backend. `:features` only ever sees these interfaces. `:data` provides one impl of each, and that impl serves every tenant — the server demuxes.

> Domain examples above use the loan/lending vocabulary per the current PRD. The historical examples used transfer/payments terminology; both shapes work — what's load-bearing is "interfaces here, impls in `:data`, no per-tenant Retrofit."

### 2.5 `policy/`

Pluggable per-tenant rules. Implemented by `:tenants:*:*` (typically by region-base modules for regulator-wide rules, with concrete-tenant overrides where needed). The surface that `:features` consumes:

```kotlin
interface LoanEligibilityPolicy {
    val minAgeYears: Int
    val maxLoanToIncomeRatio: BigDecimal
    fun validate(applicant: LoanApplicant): ValidationResult
}

interface EmiCalculator {
    fun compute(principal: Money, termMonths: Int, annualRate: BigDecimal): Installment
}

interface AmountFormatter {
    fun format(amount: Money): String
}

interface OtpDeliveryPolicy {
    val preferredChannel: OtpChannel
    val codeLength: Int
    val expirySeconds: Int
}

interface TenantCapabilities {
    fun supportsBakongDisputes(): Boolean
    fun supportsCardlessAtm(): Boolean
    fun supportsBilingualReceipt(): Boolean
    // …
}
```

`:features` injects these by interface; the active tenant module provides the impl (directly or via reuse of region-base classes). To gate UI on a capability, the ViewModel reads `capabilities.supportsBakongDisputes()` and stores it in `UiState` — never on `tenant.id`.

### 2.6 `model/`

Domain types that cross the contract boundary. Every type here is **immutable**, **serializable**, and **tenant-agnostic**.

| Model | Purpose |
|---|---|
| `Money` | Amount + currency. Avoids float arithmetic; uses `BigDecimal` internally. |
| `Currency` | Enum or sealed class: `KHR`, `USD`, `KRW`, `VND`, … |
| `UserSession` | `userId`, `tokens`, `expiresAt`, `tenantId` |
| `LoginResponse` | `userSession`, `accounts: List<DepartmentAccount>`, `tenantId`, `regionCode`, `defaultCurrency`, `tenantFlags`, `tenantParams` |
| `LoanProduct` | Product metadata (id, name, term, rate range, eligibility flags) |
| `LoanApplication` | What the user is applying for (product, amount, term, purpose, employment, guarantor) |
| `RepaymentSchedule` | Schedule of `Installment`s with due dates and amounts |
| `Guarantor` | Linked guarantor: name, phone, NID, verification status |

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

The `@LoggedInScoped` annotation and the `@TenantKey` map key live here so that both `:data` and `:tenants:*:*` can use them without importing `:app`:

```kotlin
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class LoggedInScoped

@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class TenantKey(val value: String)   // composite "<region>:<tenantSlug>"
```

The Hilt component definition (`LoggedInComponent`) lives in `:app` — see [10 — Boot Phases](10-boot-phases.md).

---

## 3. What Does NOT Go In `:core`

| ❌ Doesn't belong | ✅ Goes in |
|---|---|
| `interface FintechLoanApi` (Retrofit) | `:data` |
| `class LoanRepo` (impl) | `:data` |
| DTOs / Moshi adapters | `:data` |
| `class NhKhStaffIdValidator` (impl) | `:tenants:cambodia:nh` |
| `class KhDefaultLoanEligibilityPolicy` (impl) | `:tenants:cambodia:base` |
| Compose `LoginScreen` | `:features` |
| `LoginViewModel` | `:features` |
| `OkHttpClient` configuration | `:aos-sdk` |
| `if (tenant.id == "cambodia:nh")` logic | nowhere |
| MG endpoint URL | `:app` (build-time per environment) |

> **Smell test:** if a class in `:core` knows the name of a specific tenant or region, it is misplaced. Move it.

---

## 4. Stability Discipline

`:core` is the **most-imported product module**. A change here forces recompile of `:data`, `:features`, AND every `:tenants:*:*` module. Treat changes accordingly:

- **Adding** a method to a repository or policy interface → low-risk, but requires updating impls (`:data` for repos, every concrete tenant for policies — region-base modules typically supply the impl that concrete tenants rebind via `@TenantKey`).
- **Removing** a method → review every impl first; coordinate with affected teams.
- **Renaming** a domain model field → expensive; prefer additive deprecation.

Every PR that touches `:core` must list which downstream modules need follow-up changes.

---

## 5. Public Surface

```
com.compass.core.tenant         ← TenantContext, TenantId, TenantFlags, TenantParams,
                                  TenantCapabilities
com.compass.core.runtime        ← RuntimeConfig, ApiUrls, MaintenanceState, ForceUpdate,
                                  StoreReviewMode
com.compass.core.session        ← Session, DepartmentAccount, AccountId
com.compass.core.repository     ← *Repository interfaces
com.compass.core.policy         ← *Policy interfaces, AmountFormatter
com.compass.core.kyc            ← KycCaptureRequest, KycCaptureResult
com.compass.core.wizard         ← WizardState, WizardEvent, WizardEffect
com.compass.core.deeplink       ← DeepLinkRoute
com.compass.core.model          ← Money, UserSession, LoginResponse, Loan, …
com.compass.core.mvi            ← UiState, UiEvent, UiEffect, MviViewModel
com.compass.core.scope          ← @LoggedInScoped, @TenantKey
```

There are no `internal` types in `:core` worth mentioning — almost everything is part of the public surface, by design. That's what makes it a contract layer.

---

## 6. Cross-references

- How `:features` consumes these contracts: [06 — `:features`](06-features.md)
- How `:data` implements the repository contracts: [05 — `:data`](05-data.md)
- How `:tenants:*:*` implements the policy contracts: [07 — `:tenants:*`](07-variants.md)
- The tenant behavioral model (`TenantContext` / `TenantFlags` / `TenantParams` and when to introduce a structural `TenantPolicy`): [19 — Tenants and Regions](19-tenants-and-variants.md)
- The `LoggedInComponent` that scopes most of these instances: [10 — Boot Phases](10-boot-phases.md)
- MVI base usage details: [09 — MVI Pattern](09-mvi-pattern.md)
