# 03 · `:core` — Domain & Contract Layer

> **Type:** Local Android library
> **Role:** The **blueprint**. Defines how `:features`, `:data`, and `:variants-*` communicate.
> **Stability:** Medium — changes propagate widely; treat as a public API.

---

## 1. Purpose

`:core` is the **contract layer**. It owns:

- **Repository interfaces** — what `:data` implements
- **Policy interfaces** — what `:variants-*` implement (per region) and what per-tenant structural impls plug into (per corporate customer)
- **Domain models** — the shared vocabulary (`Money`, `UserSession`, `Receipt`, `ApprovalRequest`, `DepartmentAccount`)
- **`VariantContext`** — the immutable variant (region) snapshot resolved at login
- **`TenantContext`** — the immutable tenant (corporate customer) snapshot resolved at login, carrying `TenantFlags` and `TenantParams`
- **`RuntimeConfig`** — the typed container of values returned by MgGate at boot
- **`Session`** and **`DepartmentAccount`** — multi-institution session state (matches today's `USE_INTT_ID` axis)
- **MVI base contracts** — sealed-interface anchors that every feature ViewModel reuses
- **`@LoggedInScoped`**, **`@VariantKey`**, **`@TenantKey`** — the Hilt scope / map-key annotations used by `:data` and `:variants-*`

`:core` is the bridge that lets all four sibling modules evolve independently. **All of them depend on `:core`; none depend on each other.**

`:core` contains **no implementation logic**. No Retrofit, no Hilt providers (only the scope / key annotations), no Compose, no Android dependencies beyond ViewModel base classes.

---

## 2. What Goes In `:core`

### 2.1 `variant/`

| Type | Kind | Responsibility |
|---|---|---|
| `VariantContext` | data class | Snapshot describing the active variant (region/regulator): `id`, `displayName`, `marketCode`, `defaultCurrency`, `defaultLocale`. Resolved once at login; immutable for the session. |
| `VariantId` | value class | Type-safe wrapper around the variant identifier string (`"kr"`, `"kh"`, `"vn"`). |
| `TenantContext` | data class | Snapshot describing the active tenant (corporate customer inside a region): `id`, `displayName`, `flags: TenantFlags`, `params: TenantParams`. Resolved once at login from `LoginResponse`; immutable for the session. |
| `TenantId` | value class | Type-safe wrapper around the tenant identifier string (`"posco_ict"`, `"nia"`, `"lotte"`, `"shinsegae"`, `"default"`, …). |
| `TenantFlags` | data class | Named boolean fields the server sets per tenant (`hidesEmployeeId`, `clearsEmployeeNumberOnApproval`, `requiresIdNumberCapture`, …). Client owns the schema; server owns the values. Replaces today's `DetailConfig.isXxx()` chains. |
| `TenantParams` | data class | Named typed fields the server sets per tenant (`employeeIdRegex`, `approvalLineMaxDepth`, `receiptFooterText`, `supportPhoneOverride`, …). Same client-schema / server-values contract as `TenantFlags`. |

> Neither variant nor tenant can change inside a session — both are constructor-time values passed into `LoggedInComponent`. There is no `currentVariant: StateFlow<…>` / `currentTenant: StateFlow<…>` and no provider interface that could re-emit. See [19 — Tenants and Variants](19-tenants-and-variants.md) for the full distinction between the two axes.

### 2.2 `runtime/`

| Type | Kind | Responsibility |
|---|---|---|
| `RuntimeConfig` | data class | What MgGate returned at boot: `urls`, `webRoutes`, `maintenance`, `forceUpdate`. Immutable for the process lifetime. |
| `ApiUrls` | data class | Named base URLs: `main` (today's `Conf.IPPP_SITE_URL`), `auxiliary` (KakaoPay link callbacks, etc.). Consumed by `BaseUrlProvider` in `:aos-core`. |
| `MaintenanceState` | sealed class | `Up` or `Down(message, eta)` |
| `ForceUpdate` | data class | `minimumVersionCode`, `storeUrl` |

### 2.3 `session/`

| Type | Kind | Responsibility |
|---|---|---|
| `Session` | class | Holds `userSession: UserSession`, `variantContext: VariantContext`, `tenantContext: TenantContext`, `accounts: List<DepartmentAccount>`, `activeAccountId: StateFlow<AccountId>`. Lives inside `LoggedInComponent`. |
| `DepartmentAccount` | data class | One of the institutions / company memberships a logged-in user has access to (maps to a `USE_INTT_ID` in today's Bizplay code). Carries `id`, `displayName` (e.g. "POSCO ICT", "Lotte E&C"), `companyCode` (today's `COMPANY_CD`), `divisionCode` (today's `DVSN_CD`), `divisionName` (today's `DVSN_NM`). |
| `AccountId` | value class | Type-safe account identifier (wraps the `USE_INTT_ID` string). |

→ Detail: [12 — Departments and Session](12-departments-and-session.md)

### 2.4 `repository/`

Pure interfaces. No Android imports, no implementation logic. Implemented by `:data`.

```kotlin
interface ReceiptRepository {
    suspend fun list(filter: ReceiptFilter, page: Int): Result<ReceiptPage>
    suspend fun detail(id: ReceiptId): Result<Receipt>
    suspend fun create(draft: ReceiptDraft): Result<Receipt>
    suspend fun update(id: ReceiptId, edits: ReceiptEdits): Result<Receipt>
    suspend fun delete(id: ReceiptId): Result<Unit>
    fun observe(id: ReceiptId): Flow<Receipt>
}

interface AuthRepository {
    suspend fun login(credential: LoginCredential): Result<LoginResponse>  // → variantId + tenantId + tenantFlags + tenantParams + accounts
    suspend fun requestOtp(phone: String): Result<OtpHandle>
    suspend fun verifyOtp(handle: OtpHandle, code: String): Result<UserSession>
    suspend fun logout(): Result<Unit>
}

interface ApprovalRepository {
    suspend fun inbox(filter: ApprovalFilter, page: Int): Result<ApprovalPage>
    suspend fun detail(id: ApprovalId): Result<ApprovalRequest>
    suspend fun approve(id: ApprovalId, comment: String?): Result<Unit>
    suspend fun reject(id: ApprovalId, reason: String): Result<Unit>
    suspend fun routeOptions(draftId: ReceiptId): Result<List<ApprovalLineTemplate>>
}

interface CardRepository {
    suspend fun register(card: CardRegistration): Result<Card>
    suspend fun cards(): Result<List<Card>>
    suspend fun statement(cardId: CardId, period: StatementPeriod): Result<CardStatement>
}
```

These are **the contract** between the UI and the unified IPPP backend. `:features` only ever sees these interfaces. `:data` provides one impl of each, and that impl serves every variant and tenant — the server demuxes by `USE_INTT_ID` + `COMPANY_CD`.

### 2.5 `policy/`

Pluggable per-variant rules. Implemented by `:variants-*`. The variant-specific surface that `:features` consumes:

```kotlin
interface ExpenseAmountPolicy {
    fun validate(amount: Money): ValidationResult
    val dailyLimit: Money
    val perReceiptLimit: Money
}

interface FeeCalculator {
    fun reimbursableAmount(amount: Money, category: ExpenseCategory): Money
}

interface AmountFormatter {
    fun format(amount: Money): String
}

interface VariantCapabilities {
    fun supportsKakaoPayLink(): Boolean
    fun supportsHipassTracking(): Boolean
    fun supportsMyDataIntegration(): Boolean
    fun supportsBilingualReceipt(): Boolean
    fun supportsOcrTicketScan(): Boolean
    // …
}

interface ReceiptRenderer {
    fun render(receipt: Receipt, primaryLanguage: String): RenderedReceipt
}

interface ApprovalLineRenderer {
    fun render(line: ApprovalLine, tenant: TenantContext): RenderedApprovalLine
}
```

`:features` injects these by interface; the active variant module provides the impl. To gate UI on a capability, the ViewModel reads `capabilities.supportsKakaoPayLink()` and stores it in `UiState` — never on `variantId`.

> **Per-tenant structural policies** (e.g. Shinsegae's distinct approval-line layout) are also `:core` interfaces, implemented inside `:variants-{region}/tenants/{tenant-id}/` and dispatched via `@TenantKey` multibindings. See [19 — Tenants and Variants § 6](19-tenants-and-variants.md).

### 2.6 `model/`

Domain types that cross the contract boundary. Every type here is **immutable**, **serializable**, and **variant-agnostic**.

| Model | Purpose |
|---|---|
| `Money` | Amount + currency. Avoids float arithmetic; uses `BigDecimal` internally. |
| `Currency` | Enum or sealed class: `KRW`, `KHR`, `VND`, `USD`, … |
| `UserSession` | `userId`, `tokens`, `expiresAt`, `variantId`, `tenantId` |
| `LoginResponse` | `userSession`, `variantId`, `tenantId`, `tenantFlags`, `tenantParams`, `accounts: List<DepartmentAccount>` |
| `Receipt` | Final state of a submitted receipt (id, payment channel, amount, category, employee ID, attached photos, status) |
| `ReceiptDraft` | What the user is composing before submission (no id, may have local-only fields) |
| `ApprovalRequest` | An approval task in the inbox (id, requester, amount, category, current step, approvers) |
| `ApprovalLine` | The sequence of approvers configured for a draft |
| `Card` | A registered personal or corporate card (id, last4, issuer, scheme) |
| `CardStatement` | Period statement (lines, totals, period range) |
| `ExpenseCategory` | Meal, transport, accommodation, fuel, toll, entertainment, training, supplies, … |

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

The `@LoggedInScoped` annotation, plus the map-key annotations for variant and tenant multibindings:

```kotlin
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class LoggedInScoped

@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class VariantKey(val value: String)

@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class TenantKey(val value: String)
```

These live in `:core` so every variant module, every tenant subfolder, and `:app` can use them without cross-module imports. The Hilt component definition (`LoggedInComponent`) lives in `:app` — see [10 — Boot Phases](10-boot-phases.md).

---

## 3. What Does NOT Go In `:core`

| ❌ Doesn't belong | ✅ Goes in |
|---|---|
| `interface IpppReceiptApi` (Retrofit) | `:data` |
| `class IpppReceiptRepo` (impl) | `:data` |
| DTOs / Moshi adapters | `:data` |
| `class KrExpenseAmountPolicy` (impl) | `:variants-kr` |
| `class ShinsegaeApprovalLineRenderer` (impl) | `:variants-kr/tenants/shinsegae/` |
| Compose `ReceiptListScreen` | `:features` |
| `ReceiptListViewModel` | `:features` |
| `OkHttpClient` configuration | `:aos-core` |
| `if (variantId == "kr")` logic | nowhere |
| `if (tenant.id == "nia")` logic | nowhere |
| MgGate endpoint URL | `:app` (build-time per environment) |

> **Smell test:** if a class in `:core` knows the name of a specific variant or tenant, it is misplaced. Move it.

---

## 4. Stability Discipline

`:core` is the **most-imported product module**. A change here forces recompile of `:data`, `:features`, AND every `:variants-*` module. Treat changes accordingly:

- **Adding** a method to a repository or policy interface → low-risk, but requires updating impls (`:data` for repos, every variant for policies).
- **Removing** a method → review every impl first; coordinate with affected teams.
- **Renaming** a domain model field → expensive; prefer additive deprecation.
- **Adding a `TenantFlags` field** → every existing tenant profile and the server contract must add a default value (typically `false` or the prior implicit behaviour).

Every PR that touches `:core` must list which downstream modules need follow-up changes.

---

## 5. Public Surface

```
com.bizplay.core.variant        ← VariantContext, VariantId,
                                  TenantContext, TenantId, TenantFlags, TenantParams
com.bizplay.core.runtime        ← RuntimeConfig, ApiUrls, MaintenanceState, ForceUpdate,
                                  StoreReviewMode, WebRouteKey
com.bizplay.core.session        ← Session, DepartmentAccount, AccountId
com.bizplay.core.repository     ← *Repository interfaces
com.bizplay.core.policy         ← *Policy interfaces, VariantCapabilities, AmountFormatter,
                                  ReceiptRenderer, ApprovalLineRenderer
com.bizplay.core.model          ← Money, UserSession, LoginResponse, Receipt, ReceiptDraft,
                                  ApprovalRequest, ApprovalLine, Card, CardStatement, ExpenseCategory, …
com.bizplay.core.mvi            ← UiState, UiEvent, UiEffect, MviViewModel
com.bizplay.core.scope          ← @LoggedInScoped, @VariantKey, @TenantKey
```

There are no `internal` types in `:core` worth mentioning — almost everything is part of the public surface, by design. That's what makes it a contract layer.

---

## 6. Cross-references

- How `:features` consumes these contracts: [06 — `:features`](06-features.md)
- How `:data` implements the repository contracts: [05 — `:data`](05-data.md)
- How `:variants-*` implements the policy contracts: [07 — `:variants-*`](07-variants.md)
- The tenant axis (`TenantContext` / `TenantFlags` / `TenantParams` and when to introduce a structural `TenantPolicy`): [19 — Tenants and Variants](19-tenants-and-variants.md)
- The `LoggedInComponent` that scopes most of these instances: [10 — Boot Phases](10-boot-phases.md)
- MVI base usage details: [09 — MVI Pattern](09-mvi-pattern.md)
