# 07 · `:tenants:*` — Tenants and Region Bases

> **Type:** Local Android library modules, organized as a two-level hierarchy:
> - **Region base** — `:tenants:{region}:base` (one per region, e.g., `:tenants:cambodia:base`, `:tenants:korea:base`)
> - **Concrete tenants** — `:tenants:{region}:{tenantSlug}` (e.g., `:tenants:korea:nh`, `:tenants:korea:shinsegae`)
>
> **Role:** Tenant-specific **policies** (validation, fees, formatting, capabilities) + DI bindings. Region bases provide shared regulator/region policy classes; concrete tenants declare Hilt `@TenantKey` bindings.
>
> **Isolation guarantee:** No concrete tenant module may depend on another concrete tenant. No region base may depend on another region base. Concrete tenants depend on their own region base only.

> **Historical note:** earlier iterations used `:variants-{id}` flat sibling modules paired with a separate tenant axis nested inside. That two-axis model has been collapsed — see [19 § 12](19-tenants-and-variants.md).

---

## 1. Purpose

A tenant module is a **silo** that holds *only what differs* between organizations (or, for region bases, between regulator domains). With a unified server-side API, the data layer is shared across tenants — so each tenant module is reduced to:

1. **Policy implementations** — `:core` policy interfaces (fee calc, amount validation, formatting, business rules)
2. **Capability flags** — implementations of `TenantCapabilities`
3. **`TenantProfile` factory** — produces the `TenantContext` (flags + params) from `LoginResponse`
4. **DI bindings** — one Hilt module exposing those impls to `LoggedInComponent` via `@IntoMap @TenantKey("<region>:<tenantSlug>")`

A tenant module does **not** own:

- UI (lives in `:features`)
- Retrofit interfaces or DTOs (the server demuxes; the unified `Fintech*Api` lives in `:data`)
- Repository implementations (also in `:data`)

A typical concrete tenant module is 5–15 small Kotlin files. A region-base module is similarly small — usually 8–15 policy classes that the regulator/region mandates.

---

## 2. Module Hierarchy

```
:tenants/
├── cambodia/
│   ├── base/                                           ← region baseline
│   │   └── src/main/kotlin/com/<org>/tenants/cambodia/base/
│   │       ├── policy/
│   │       │   ├── KhDefaultLoanEligibilityPolicy.kt
│   │       │   ├── KhDefaultRepaymentPenaltyCalculator.kt
│   │       │   ├── KhComplianceThresholds.kt
│   │       │   ├── KhOtpDeliveryPolicy.kt
│   │       │   └── KhBusinessCalendar.kt
│   │       ├── format/
│   │       │   ├── KhrAmountFormatter.kt
│   │       │   └── UsdAmountFormatter.kt              (dual-currency for KH)
│   │       └── capability/
│   │           └── KhBaseCapabilities.kt
│   ├── default/                                        ← sentinel tenant (used in tests + no-override baseline)
│   │   └── di/
│   │       └── KhDefaultTenantModule.kt                (@TenantKey("cambodia:default"))
│   └── nh/                                             ← concrete tenant (this PRD's product)
│       ├── flags/
│       │   └── NhKhTenantProfile.kt                    (TenantContext factory: flags + params)
│       ├── policy/
│       │   └── NhKhStaffIdValidator.kt                 (tenant-specific override)
│       └── di/
│           └── NhKhTenantModule.kt                     (@TenantKey("cambodia:nh"))
└── korea/                                              (similar shape if KR ships)
    ├── base/
    ├── default/
    └── nh/
```

| Layer | Purpose | Visibility |
|---|---|---|
| `policy/` (in base or tenant) | Business rules implementing `:core` policy interfaces | `internal` |
| `format/` | Locale/currency formatting helpers | `internal` |
| `capability/` | Boolean flags gating UI features | `internal` |
| `flags/` (in concrete tenant) | `TenantProfile` factory producing `TenantContext` | `internal` |
| `di/` | The single public surface — Hilt module exposed to `:app` | `public` |

**Only the DI module should be visible outside the tenant module.** Everything else is `internal`. This prevents `:app` from accidentally referencing a concrete policy class — it should only ever reference the `:core` interface.

---

## 3. The Region Catalogue

| Region module | Regulator | Distinctive baseline responsibilities |
|---|---|---|
| `:tenants:cambodia:base` | NBC | KHR/USD formatting, NBC compliance limits, KH business calendar |
| `:tenants:korea:base` | FSS (if KR ships) | KRW formatting, FSS compliance limits, KR business calendar |

Each region typically lists 1–N concrete tenants alongside the `base` + `default` siblings. See [19 § 1](19-tenants-and-variants.md) for the concrete-tenant matrix and [13 — Onboarding a Tenant](13-onboarding-a-variant.md).

---

## 4. Policy Implementation Pattern

Pure Kotlin classes implementing `:core` policy interfaces. Stateless where possible.

```kotlin
// Region-base example — regulator-wide rule, shared by every tenant in Cambodia
internal class KhDefaultLoanEligibilityPolicy : LoanEligibilityPolicy {
    override val minAgeYears: Int = 18
    override val maxLoanToIncomeRatio: BigDecimal = BigDecimal("0.45")
    override fun validate(applicant: LoanApplicant): ValidationResult = when {
        applicant.age < minAgeYears                       -> ValidationResult.Invalid("Must be 18+")
        applicant.loanToIncomeRatio > maxLoanToIncomeRatio -> ValidationResult.Invalid("Above LTI cap")
        else                                              -> ValidationResult.Valid
    }
}

// Region-base example — shared formatting
internal class KhrAmountFormatter : AmountFormatter {
    override fun format(amount: Money): String = "%,.0f៛".format(amount.value)
}

// Concrete-tenant example — tenant-specific override
internal class NhKhStaffIdValidator : StaffIdValidator {
    private val nhStaffRegex = Regex("""^NH\d{9}$""")  // NH staff IDs only
    override fun validate(id: String) =
        if (nhStaffRegex.matches(id)) ValidationResult.Valid
        else ValidationResult.Invalid("Must be NH staff ID (NH + 9 digits)")
}
```

No networking, no Android dependencies, JVM-unit-testable.

---

## 5. Common Tenant/Region Surfaces

The following `:core` policy interfaces are typical inhabitants of region bases and concrete tenants. The placement (base vs concrete) depends on whether the rule is regulator-wide or org-specific.

### 5.1 `LoanEligibilityPolicy` *(region base)*

Age, income, bureau-score thresholds. Regulator-wide.

### 5.2 `EmiCalculator` *(region base)*

Rounding rules, day-count convention. Regulator-wide.

### 5.3 `RepaymentPenaltyCalculator` *(region base)*

Overdue penalty rules per regulator.

### 5.4 `OtpDeliveryPolicy` *(region base)*

Preferred OTP channel and timing, regulator-mandated.

```kotlin
// :core/policy/OtpDeliveryPolicy.kt
enum class OtpChannel { Sms, Voice, InApp }

interface OtpDeliveryPolicy {
    val preferredChannel: OtpChannel
    val codeLength: Int
    val expirySeconds: Int
}

// :tenants:cambodia:base/policy/KhOtpDeliveryPolicy.kt
internal class KhOtpDeliveryPolicy : OtpDeliveryPolicy {
    override val preferredChannel: OtpChannel = OtpChannel.Sms
    override val codeLength: Int = 6
    override val expirySeconds: Int = 300
}
```

### 5.5 `SupportContacts` *(concrete tenant)*

Customer-care contact info, surfaced in the help screen. Differs per tenant org.

```kotlin
// :tenants:cambodia:nh/support/NhKhSupportContacts.kt
internal class NhKhSupportContacts : SupportContacts {
    override val customerCarePhone   = "+855 23 999 999"
    override val customerCareEmail   = "support@nhfinance.kh"
    override val customerCareHours   = "Mon–Fri, 8 AM – 8 PM (ICT)"
}
```

### 5.6 `ComplianceThresholds` *(region base)*

Per-regulator transaction-scrutiny thresholds.

### 5.7 `BusinessCalendar` *(region base)*

Bank holidays and settlement cutoffs.

### 5.8 `KycRequirementPolicy` *(region base, possibly overridden by concrete tenant)*

Which documents are required for KYC. Regulator sets the baseline; some tenant orgs may require additional docs.

```kotlin
// :core/policy/KycRequirementPolicy.kt
interface KycRequirementPolicy {
    val requiredDocuments: List<KycDocumentType>
    val requiresLivenessSelfie: Boolean
}

// :tenants:cambodia:base/policy/KhDefaultKycRequirementPolicy.kt — NBC baseline
internal class KhDefaultKycRequirementPolicy : KycRequirementPolicy {
    override val requiredDocuments = listOf(KycDocumentType.IdCardFront, KycDocumentType.IdCardBack)
    override val requiresLivenessSelfie = true
}
```

### 5.9 `StaffIdPolicy` *(concrete tenant)*

Referral-code validation. Differs per org.

### 5.10 Adding a new policy surface

When `:core/policy/` gains a new interface, every concrete tenant must bind it. The build fails until each tenant explicitly answers what its behavior is. Avoid `default` fallbacks on policy interfaces; force the per-tenant decision.

### 5.11 Worked test example

Because policies are pure Kotlin classes with no Android dependencies, unit tests run on the JVM with no fixtures.

```kotlin
// :tenants:cambodia:base/src/test/kotlin/.../KhDefaultLoanEligibilityPolicyTest.kt
class KhDefaultLoanEligibilityPolicyTest {

    private val policy = KhDefaultLoanEligibilityPolicy()

    @Test fun `underage applicant is invalid`() {
        val applicant = LoanApplicant(age = 17, /* … */)
        val result = policy.validate(applicant)
        assertEquals(ValidationResult.Invalid("Must be 18+"), result)
    }

    @Test fun `over-LTI applicant is invalid`() {
        val applicant = LoanApplicant(age = 30, loanToIncomeRatio = BigDecimal("0.50"), /* … */)
        val result = policy.validate(applicant)
        assertEquals(ValidationResult.Invalid("Above LTI cap"), result)
    }

    @Test fun `eligible applicant is valid`() {
        val applicant = LoanApplicant(age = 30, loanToIncomeRatio = BigDecimal("0.40"), /* … */)
        assertEquals(ValidationResult.Valid, policy.validate(applicant))
    }
}
```

Run with `./gradlew :tenants:cambodia:base:test`. No Hilt, no Android runtime, no test fixtures.

---

## 6. The Hilt Binding Module

Each **concrete tenant** exposes **exactly one** Hilt module that declares `@IntoMap @TenantKey("<region>:<tenantSlug>")` bindings for every `:core` policy interface its tenant needs.

### 6.1 The map key (defined in `:core`)

```kotlin
// :core/scope/TenantKey.kt
@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class TenantKey(val value: String)
```

Lives in `:core` so every tenant module and `:app` can use it without cross-module imports.

### 6.2 Per-tenant Hilt module (concrete-rebinds-everything pattern)

Each concrete tenant declares the full set of bindings — reusing region-base implementation classes where the regional baseline applies, supplying overrides where it doesn't.

```kotlin
// :tenants:cambodia:nh/di/NhKhTenantModule.kt
@Module
@InstallIn(LoggedInComponent::class)
abstract class NhKhTenantModule {
    @Binds @IntoMap @TenantKey("cambodia:nh") @LoggedInScoped
    abstract fun loanEligibility(impl: KhDefaultLoanEligibilityPolicy): LoanEligibilityPolicy   // reuses base

    @Binds @IntoMap @TenantKey("cambodia:nh") @LoggedInScoped
    abstract fun otpDelivery(impl: KhOtpDeliveryPolicy): OtpDeliveryPolicy                       // reuses base

    @Binds @IntoMap @TenantKey("cambodia:nh") @LoggedInScoped
    abstract fun amountFormatter(impl: KhrAmountFormatter): AmountFormatter                     // reuses base

    @Binds @IntoMap @TenantKey("cambodia:nh") @LoggedInScoped
    abstract fun businessCalendar(impl: KhBusinessCalendar): BusinessCalendar                   // reuses base

    @Binds @IntoMap @TenantKey("cambodia:nh") @LoggedInScoped
    abstract fun staffIdValidator(impl: NhKhStaffIdValidator): StaffIdValidator                 // overrides

    @Binds @IntoMap @TenantKey("cambodia:nh") @LoggedInScoped
    abstract fun supportContacts(impl: NhKhSupportContacts): SupportContacts                    // overrides

    // … one @IntoMap binding per :core policy interface
}
```

**Trade-off:** the concrete-rebinds-everything pattern means each tenant module declares the full set of `@TenantKey` bindings. Boilerplate cost; the gain is no custom chain-walking resolver and no surprises at runtime. Recommended for v1; revisit if tenant count grows past ~10.

`@InstallIn(LoggedInComponent::class)` plus `@IntoMap @TenantKey("cambodia:nh")` is what makes Hilt aggregate this tenant's bindings into the `Map<String, T>` for each policy interface. **No edit to `:app` is required to register the module** — Hilt's annotation processor finds it automatically.

### 6.3 The resolver in `:app`

`:app` provides one resolver per `:core` policy interface — each picks the active impl from the multibindings map by `TenantContext.id.value`:

```kotlin
// :app/di/TenantResolverModule.kt
@Module
@InstallIn(LoggedInComponent::class)
object TenantResolverModule {

    @Provides @LoggedInScoped
    fun loanEligibilityPolicy(
        tenant: TenantContext,
        all: Map<String, @JvmSuppressWildcards LoanEligibilityPolicy>,
    ): LoanEligibilityPolicy = checkNotNull(all[tenant.id.value]) {
        "No LoanEligibilityPolicy registered for tenant ${tenant.id}"
    }

    @Provides @LoggedInScoped
    fun otpDeliveryPolicy(
        tenant: TenantContext,
        all: Map<String, @JvmSuppressWildcards OtpDeliveryPolicy>,
    ): OtpDeliveryPolicy = checkNotNull(all[tenant.id.value]) {
        "No OtpDeliveryPolicy registered for tenant ${tenant.id}"
    }

    // … one provider per :core policy interface (mechanical; ~10 entries)
}
```

The map lookup is the **single point of dispatch** in the codebase — no `when (tenant.id)` branching in `:features`, `:data`, or any tenant module. Adding a new tenant doesn't change `TenantResolverModule`; Hilt automatically adds the new tenant's `@IntoMap` entries to each map.

### 6.4 What this looks like at compile time

```
NhKhTenantModule:         @IntoMap @TenantKey("cambodia:nh")     ──┐
KhDefaultTenantModule:    @IntoMap @TenantKey("cambodia:default") ──┤  Map<String, LoanEligibilityPolicy>
NhKrTenantModule:         @IntoMap @TenantKey("korea:nh")          ──┤   { "cambodia:nh"      → KhDefaultLoanEligibilityPolicy,
ShinsegaeTenantModule:    @IntoMap @TenantKey("korea:shinsegae")   ──┤     "cambodia:default" → KhDefaultLoanEligibilityPolicy,
                                                                       "korea:nh"          → KrDefaultLoanEligibilityPolicy,
                                                                       "korea:shinsegae"   → KrDefaultLoanEligibilityPolicy }
                                                                          │
                                                                          ↓
                                                            TenantResolverModule (in :app)
                                                            picks one by TenantContext.id.value
                                                                          ↓
                                          :features ViewModels see ONE LoanEligibilityPolicy
                                          (the active tenant's), via Hilt — Logic-Blind.
```

There is no compile error from "multiple bindings for `LoanEligibilityPolicy`" because each binding goes into the map under a unique key. Dagger generates the maps; the resolver picks; the consumer never knows.

> **Note on multiple tenants in one APK:** every tenant's Hilt module compiles into the binary. Only the entries matching the logged-in user's `tenantId` are *exercised*; the others sit inert in the maps. This keeps `:features` Logic-Blind and onboarding additive. The cost is a slightly larger APK; the benefit is no per-tenant `:app` change. See [13 — Onboarding](13-onboarding-a-variant.md).

---

## 7. Isolation Guarantee

The build graph enforces:

- ❌ `:tenants:{regionA}:{tenantX}` cannot import `:tenants:{regionA}:{tenantY}` (no cross-tenant coupling within a region)
- ❌ `:tenants:{regionA}:*` cannot import `:tenants:{regionB}:*` (no cross-region coupling at any level)
- ❌ Region-base modules cannot import each other (`:tenants:cambodia:base` ↛ `:tenants:korea:base`). If shared policy is genuinely cross-regional, promote to `:core`.
- ❌ `:tenants:*:*` cannot import `:features`, `:data`, `:design-system`
- ✅ Concrete tenant modules **must** declare Gradle dependency on their own region base (`:tenants:cambodia:nh` → `:tenants:cambodia:base`)
- ✅ `:tenants:*:*` may import `:core` (contracts) and `:aos-sdk` (infrastructure helpers)

A bug in a Cambodia tenant cannot, by construction, reach a Korea tenant. CI verifies this by failing any PR whose tenant module declares a forbidden dependency.

---

## 8. End-to-end Example: A Loan Application Submit

The path from user tap to network call shows where `:tenants:*` plugs in:

```
[User taps Submit]
        │
        ▼
:features (UI)        LoanApplyScreen → LoanApplyViewModel
                          │
                          │  eligibilityPolicy.validate(applicant)    ── from :tenants:{active}
                          │  emiCalculator.compute(amount, term, rate) ── from :tenants:{active}
                          │  if (validation is Valid) →
                          │  loanRepo.submit(application)
                          ▼
:core (interfaces)    LoanEligibilityPolicy, EmiCalculator, LoanRepository
                          │
                          │  (resolved by Hilt to active impls)
                          ▼
:tenants:cambodia:nh   (depends on :tenants:cambodia:base)
                       KhDefaultLoanEligibilityPolicy, NhKhStaffIdValidator
:data                  LoanRepo : LoanRepository
                          │  api.submitLoanApplication(req).toDomain()
                          ▼
:aos-sdk (infra)      RetrofitFactory + BaseUrlInterceptor + AccountIdInterceptor
                          │  HTTP POST {RuntimeConfig.urls.main}/v1/loans/apply
                          │       Authorization: Bearer ...
                          │       X-Account-Id: <session.activeAccountId>
                          ▼
                      [Unified fintech backend; demuxes per user]
```

The ViewModel only knows interfaces. Hilt resolves:
- `KhDefaultLoanEligibilityPolicy` because the user's `tenantId == "cambodia:nh"` was bound into `LoggedInComponent` at login.
- `LoanRepo` because `:data` is the only repo provider — same for every tenant.

→ For the boot mechanics: [10 — Boot Phases](10-boot-phases.md).

---

## 9. When the Tenant Has Unique Features

A tenant module holds *only* policies + DI. If a tenant introduces a feature that no other tenant has — its own API endpoints, its own DTOs, its own screens — that feature does **not** go in `:tenants:{region}:{tenantId}`. It gets its own dedicated module, mirroring how `:features-chatbot` is structured.

### Why not put the unique feature inside `:tenants:*`?

Four reasons:

1. **The tenant module is logic-only.** It depends on `:core` and `:aos-sdk` only. Adding UI would force a `:design-system` dependency; adding API code would require Retrofit + Moshi setup; adding a Compose screen would require Compose dependencies. The tenant module's small, pure shape is what makes it predictable.
2. **Tenant modules are symmetric.** Every concrete tenant has the same internal structure (`policy/`, `format/`, `flags/`, `capability/`, `di/`). Unique features are **not** symmetric — only some tenants have them. Mixing feature code into a tenant module would make tenants stop looking alike.
3. **Capability gating is cleaner with separate modules.** `:app` reads `capabilities.supportsBakongDisputes()` and conditionally includes the `:features-bakong-disputes` nav graph. The flag and the feature module are decoupled. Embedding the feature in the tenant module weakens the gate (the code is loaded either way) and conflates "what tenant am I" with "what features does this user see."
4. **Features migrate; tenants don't.** If a feature is later supported by another tenant, the feature module just gains a new capability-flag setter from another tenant. No code moves between modules.

### Module shape

```
:features-bakong-disputes/                    (or :features-{tenant}-bakong-disputes if locked to one tenant)
└── src/main/kotlin/com/<org>/features/bakongdisputes/
    ├── api/
    │   ├── BakongDisputeApi.kt              # Retrofit, tenant-unique endpoints
    │   └── dto/
    │       ├── DisputeRequest.kt
    │       └── DisputeResponse.kt
    ├── repo/
    │   └── BakongDisputeRepo.kt
    ├── screen/
    │   ├── DisputeListScreen.kt
    │   ├── DisputeDetailScreen.kt
    │   └── DisputeContract.kt
    └── di/
        └── BakongDisputesModule.kt          # @InstallIn(LoggedInComponent::class)
```

Dependencies: `:core`, `:aos-sdk`, `:design-system`. Does **not** depend on `:features`.

### Capability gating

`:app`'s navigation conditionally includes the feature's nav graph based on a `TenantCapabilities` flag:

```kotlin
@Composable
fun AppNavigation(navController: NavHostController, capabilities: TenantCapabilities) {
    NavHost(navController, startDestination = Route.Boot) {
        bootNavGraph(navController)
        authNavGraph(navController)
        mainScaffoldNavGraph(navController)
        chatbotNavGraph(navController)
        if (capabilities.supportsBakongDisputes()) {
            bakongDisputesNavGraph(navController)
        }
    }
}
```

Each tenant's `TenantCapabilities` impl returns `true` for the features it supports and `false` for the rest. The feature's Hilt bindings still install (they're inert when unreachable), but no UI is wired in.

### Naming

| Choice | Use when |
|---|---|
| `:features-{feature-name}` | Default. Mirrors `:features-chatbot`. The feature *could* one day be supported by another tenant. |
| `:features-{tenant}-{feature-name}` | The feature is structurally locked to one tenant. |

### When to extract a feature module vs. keep in `:features`

| Trigger | Action |
|---|---|
| Feature has unique heavy SDK dependencies | Extract (same rationale as `:features-chatbot`) |
| Feature has unique Retrofit endpoints + DTOs that other tenants don't share | Extract |
| Feature differs only in policy/format/visibility | Keep in `:features`, gate via `TenantCapabilities` |
| Feature differs only in business rule (limit, fee) | Keep in `:features`, supply the rule via a `:core` policy interface |

The threshold mirrors the build-perf heuristic from [14](14-build-performance.md): if a tenant-unique feature would slow incremental builds for everyone else, isolate it.

### Tenants stay isolated from these feature modules

`:tenants:cambodia:nh` does **not** depend on `:features-bakong-disputes`. The connection is one-way:

- `:tenants:cambodia:nh` provides `NhKhCapabilities.supportsBakongDisputes() = true` via its `TenantCapabilities` impl.
- `:features-bakong-disputes` installs its own Hilt bindings into `LoggedInComponent`.
- `:app` reads the capability flag and conditionally wires the nav graph.

No cross-edge between `:tenants:cambodia:nh` and `:features-bakong-disputes`. They communicate through `:core` and `:app`.

---

## 10. What Does NOT Go In `:tenants:*`

| ❌ Doesn't belong | ✅ Goes in |
|---|---|
| Compose UI | `:features` or a `:features-{name}` sibling module |
| Retrofit interfaces or DTOs | `:data` |
| Repository implementations | `:data` |
| Domain models shared across tenants | `:core` |
| `OkHttpClient` configuration | `:aos-sdk` |
| References to other tenant modules (within or across regions) | nowhere |
| References to other region-base modules | nowhere — promote shared policy to `:core` if truly needed |
| MG endpoint URL | `:app` (build-time) |
| User-facing display strings | `strings.xml` resources in `:design-system` or the consuming `:features` module |

If two tenants need the same logic, **either promote it to the region base** (if it's regulator-wide) **or promote it to `:core`** (if it's a contract every region defines independently). Premature consolidation across tenants is how isolation erodes.

---

## 11. The `:features-chatbot` Sibling

`:features-chatbot` is **not a tenant** — it's an isolated UI feature, sibling to `:features`. It exists because heavy SDKs (chat NLP, voice) penalize incremental builds of unrelated features. Its dependency rules:

- Depends on: `:core`, `:aos-sdk`, `:design-system`
- Does **not** depend on: `:features` (no cross-imports)
- Depended on by: `:app` (via navigation entry point)

Treat `:features-chatbot` as the prototype for **"isolate when SDK weight justifies it"**. Concrete new sibling modules follow the same pattern: `:features-kyc` (CameraX + ML Kit weight), `:features-support-chat` (Sendbird SDK weight), `:features-branch-locator` (Google Maps weight).

---

## 12. Cross-references

- The tenant behavioral model (`TenantContext`, flags, params, escalation): [19 — Tenants and Regions](19-tenants-and-variants.md)
- The interfaces tenants and region bases implement: [03 — `:core`](03-core.md)
- The repos that handle the API side (tenant-agnostic): [05 — `:data`](05-data.md)
- The shared design system that tenant-unique features consume: [04 — `:design-system`](04-design-system.md)
- How `:app` wires tenants at boot: [08 — `:app`](08-app-orchestrator.md)
- The `LoggedInComponent` mechanism: [10 — Boot Phases](10-boot-phases.md)
- Onboarding a tenant or region: [13 — Onboarding a Tenant](13-onboarding-a-variant.md)
