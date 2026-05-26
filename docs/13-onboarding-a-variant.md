# 13 · Onboarding a Tenant (or a New Region)

> **Promise:** Adding a new tenant requires **zero changes to `:features`, `:data`, or sibling tenants**.
> **Scope for a new tenant in an existing region:** one new concrete-tenant module + one `settings.gradle.kts` line + one `TenantCatalogue` entry.
> **Scope for a new region:** one new region-base module + one `default` tenant + one or more concrete tenants + one `settings.gradle.kts` line per module + catalogue entries.
> **Worked examples below:** adding **`:tenants:cambodia:partner-a`** (new tenant in existing region) and **`:tenants:vietnam:*`** (entirely new region).

This is the proof that the framework's architecture survives contact with growth. With the unified server-side API, tenant modules are tiny — a few policy classes plus a Hilt binding module. If a step in this checklist starts requiring `:features` or `:data` edits, the framework has regressed — open an architecture issue.

> **Historical note:** earlier iterations of this doc documented onboarding a *variant* (region as a DI axis). The variant axis has been collapsed into the tenant axis; region is now a Gradle module hierarchy. See [19 § 12](19-tenants-and-variants.md) for the rationale.

---

## 1. Pre-Flight

Before writing code, gather:

| Artifact | Source | Why |
|---|---|---|
| Tenant-specific business rules | Compliance / product | Drives the `*Policy` implementations |
| Server-side tenant ID assignment | Backend team | Auth response must return this composite ID (e.g., `"cambodia:partner-a"`) for users in this tenant |
| Capability list (which features are on/off) | Product | Drives `TenantCapabilities` flags |
| Display metadata | Brand team | Display name, region code, default currency for `TenantContext` |
| (New region only) regulator scope | Compliance | Drives the region-base policies |

If any of these is unavailable, do not begin onboarding. Stub data leaks into production.

> **Note:** the server-side fintech API handles all backend routing, so no per-tenant Retrofit interface, no per-tenant DTOs, no per-tenant repository — none of that lives on the Android side. The tenant module is *just* policies + DI.

### 1.1 No production fallback; scaffold by copying

There is **no production fallback to `default`**. If the auth response does not include a recognized `tenantId`, the app must reject the login — silent fallback to the region's `default` tenant would risk routing the user through the wrong policies. Fail loud. The `:tenants:{region}:default` module exists for tests and as the no-overrides baseline target; not for production users.

For **scaffolding** a new concrete tenant, copy an existing peer in the same region:

```bash
cp -R tenants/cambodia/nh tenants/cambodia/partner-a
# In tenants/cambodia/partner-a/, rename NhKh* → PartnerAKh*, swap rule values, regex patterns, flags.
# Then update settings.gradle.kts and the TenantCatalogue entry per Steps 2 / 5.
```

For **scaffolding a new region**, copy an existing region wholesale:

```bash
cp -R tenants/cambodia tenants/vietnam
# Rename Kh* → Vn*, swap currency/calendar/regulator rules in :tenants:vietnam:base/.
# Then rename and adjust the default + concrete tenant subfolders.
```

The "boilerplate" is the **structural template** — the package layout (`policy/`, `format/`, `flags/`, `capability/`, `di/`) and the Hilt module shape. The *content* (limits, fees, regex patterns, flags, currency symbol, support contacts) is what differs.

---

## 2. The Onboarding Checklist — New Concrete Tenant

This is the most common case: an existing region (e.g., Cambodia) gains a new partner organization.

### Step 1 — Create the module

```bash
mkdir -p tenants/cambodia/partner-a/src/main/kotlin/com/<org>/tenants/cambodia/partnera/{policy,flags,capability,di}
```

Create `tenants/cambodia/partner-a/build.gradle.kts` mirroring an existing tenant. Required dependencies:

- `:core` (contracts)
- `:tenants:cambodia:base` (Gradle dependency on the region base — **mandatory**; Hilt resolution depends on it)
- `:aos-sdk` (helpers, if needed)
- Hilt + KSP

> **Forbidden** in tenant build files: dependencies on `:features`, `:features-chatbot`, `:data`, `:design-system`, sibling concrete tenants (in the same or another region), or other region bases. CI must reject these.

### Step 2 — Register in `settings.gradle.kts`

Add **one line**:

```kotlin
include(":tenants:cambodia:partner-a")
```

This is the only edit to the root build configuration.

### Step 3 — Write the `TenantProfile` factory

```kotlin
// :tenants:cambodia:partner-a/flags/PartnerAKhTenantProfile.kt
internal object PartnerAKhTenantProfile {
    fun fromLoginResponse(loginResponse: LoginResponse): TenantContext = TenantContext(
        id = TenantId("cambodia:partner-a"),
        displayName = "Partner A Cambodia",
        regionCode = "kh",
        defaultCurrency = Currency.KHR,
        flags = loginResponse.tenantFlags,
        params = loginResponse.tenantParams,
    )
}
```

### Step 4 — Implement any tenant-specific policies

Most policies will be reused from `:tenants:cambodia:base`. Only define what differs for Partner A:

```kotlin
// :tenants:cambodia:partner-a/policy/PartnerAStaffIdValidator.kt
internal class PartnerAStaffIdValidator : StaffIdValidator {
    private val partnerARegex = Regex("""^PA\d{7}$""")
    override fun validate(id: String) =
        if (partnerARegex.matches(id)) ValidationResult.Valid
        else ValidationResult.Invalid("Must be Partner A staff ID (PA + 7 digits)")
}
```

### Step 5 — Wire the Hilt module (concrete-rebinds-everything pattern)

```kotlin
// :tenants:cambodia:partner-a/di/PartnerAKhTenantModule.kt
@Module
@InstallIn(LoggedInComponent::class)
abstract class PartnerAKhTenantModule {
    // Reuse region-base classes where the regional baseline applies
    @Binds @IntoMap @TenantKey("cambodia:partner-a") @LoggedInScoped
    abstract fun loanEligibility(impl: KhDefaultLoanEligibilityPolicy): LoanEligibilityPolicy

    @Binds @IntoMap @TenantKey("cambodia:partner-a") @LoggedInScoped
    abstract fun otpDelivery(impl: KhOtpDeliveryPolicy): OtpDeliveryPolicy

    @Binds @IntoMap @TenantKey("cambodia:partner-a") @LoggedInScoped
    abstract fun amountFormatter(impl: KhrAmountFormatter): AmountFormatter

    @Binds @IntoMap @TenantKey("cambodia:partner-a") @LoggedInScoped
    abstract fun businessCalendar(impl: KhBusinessCalendar): BusinessCalendar

    // Provide tenant-specific overrides
    @Binds @IntoMap @TenantKey("cambodia:partner-a") @LoggedInScoped
    abstract fun staffIdValidator(impl: PartnerAStaffIdValidator): StaffIdValidator

    @Binds @IntoMap @TenantKey("cambodia:partner-a") @LoggedInScoped
    abstract fun supportContacts(impl: PartnerAKhSupportContacts): SupportContacts

    // … one @IntoMap binding per :core policy interface
}
```

`@InstallIn(LoggedInComponent::class)` plus `@IntoMap @TenantKey("cambodia:partner-a")` is what makes Hilt aggregate this tenant's bindings into the `Map<String, T>` for each policy interface. **No edit to `:app` is required** — Hilt finds the module automatically; the resolver in `:app/di/TenantResolverModule.kt` looks up `tenant.id.value` in the map and finds the new tenant's entries.

> **Important:** *every* tenant's bindings compile into the same `LoggedInComponent::class`. At runtime, only the entries matching the logged-in user's `tenantId` are exercised; the others sit inert. This keeps `:features` Logic-Blind and onboarding additive — the cost is a slightly larger APK.

### Step 6 — Register in the tenant catalogue

```kotlin
// :app/src/main/kotlin/com/<org>/app/tenant/TenantCatalogue.kt
object TenantCatalogue {
    val profiles: Map<TenantId, (LoginResponse) -> TenantContext> = mapOf(
        TenantId("cambodia:default")    to ::khDefaultProfile,
        TenantId("cambodia:nh")         to NhKhTenantProfile::fromLoginResponse,
        TenantId("cambodia:partner-a")  to PartnerAKhTenantProfile::fromLoginResponse,   // ← added
        // …
    )
}
```

This is the **one place in `:app`** that must learn about the new tenant. The catalogue feeds:

- `TenantContextResolver`, which produces the right `TenantContext` after login by `tenantId`.
- The debug overlay's tenant indicator.

### Step 7 — Coordinate server-side

- **Auth response:** the auth backend must return `tenantId = "cambodia:partner-a"` for users belonging to the new tenant, along with the appropriate `tenantFlags` and `tenantParams`.

### Step 8 — Add tests

Each new tenant module ships with:

- **Unit tests** for any tenant-specific `*Policy` (golden cases)
- **Unit tests** for `TenantProfile.fromLoginResponse` (assert the flag/param mapping is correct)
- **Integration test** in `:app` running with `tenantId = "cambodia:partner-a"` injected (one happy-path flow that exercises a tenant-specific binding plus the shared `:data` repos)

---

## 3. The Onboarding Checklist — New Region

When the first user of a new country/regulator lands, you create the region first, then at least one tenant inside it.

### Step 1 — Create the region-base module

```bash
mkdir -p tenants/vietnam/base/src/main/kotlin/com/<org>/tenants/vietnam/base/{policy,format,capability}
```

Define the regulator-wide policies (SBV compliance limits, VND formatting, VN business calendar, OTP-via-SMS or whatever the regulator mandates).

```kotlin
// :tenants:vietnam:base/policy/VnDefaultLoanEligibilityPolicy.kt
internal class VnDefaultLoanEligibilityPolicy : LoanEligibilityPolicy {
    override val minAgeYears = 18
    override val maxLoanToIncomeRatio = BigDecimal("0.40")    // SBV-mandated, lower than KH
    override fun validate(applicant: LoanApplicant): ValidationResult = /* … */
}

// :tenants:vietnam:base/format/VndAmountFormatter.kt
internal class VndAmountFormatter : AmountFormatter {
    override fun format(amount: Money): String = "%,.0f₫".format(amount.value)
}
```

**Region-base modules never declare `@TenantKey` bindings directly.** They provide reusable implementation *classes* that concrete-tenant modules in this region bind via `@TenantKey("vietnam:<tenantSlug>")`.

### Step 2 — Create the region's `:default` tenant

```bash
mkdir -p tenants/vietnam/default/src/main/kotlin/com/<org>/tenants/vietnam/default/di
```

```kotlin
// :tenants:vietnam:default/di/VnDefaultTenantModule.kt
@Module
@InstallIn(LoggedInComponent::class)
abstract class VnDefaultTenantModule {
    @Binds @IntoMap @TenantKey("vietnam:default") @LoggedInScoped
    abstract fun loanEligibility(impl: VnDefaultLoanEligibilityPolicy): LoanEligibilityPolicy

    @Binds @IntoMap @TenantKey("vietnam:default") @LoggedInScoped
    abstract fun amountFormatter(impl: VndAmountFormatter): AmountFormatter

    // … one @IntoMap binding per :core policy interface
}
```

This `default` tenant exists for tests and as the no-overrides baseline target. It must never resolve in production.

### Step 3 — Create at least one concrete tenant

Follow §2 above, with `:tenants:vietnam:{tenantSlug}` instead of `:tenants:cambodia:{tenantSlug}`.

### Step 4 — Register all new modules in `settings.gradle.kts`

```kotlin
include(":tenants:vietnam:base")
include(":tenants:vietnam:default")
include(":tenants:vietnam:nh")            // example concrete tenant
```

### Step 5 — Register all concrete tenants in `TenantCatalogue`

```kotlin
TenantId("vietnam:default") to ::vnDefaultProfile,
TenantId("vietnam:nh")      to NhVnTenantProfile::fromLoginResponse,
```

### Step 6 — Coordinate server-side

The auth backend must return `tenantId = "vietnam:nh"` (or another `vietnam:*` value) for users in this region.

### Step 7 — Tests

- Unit tests on every region-base policy.
- Unit tests on the `default` tenant's `TenantProfile`.
- Integration test in `:app` with `tenantId = "vietnam:nh"` injected.

---

## 4. What You DO NOT Touch

The whole point. Verify after onboarding:

| Module | Diff line count expected |
|---|---|
| `:aos-sdk` | 0 |
| `:core` | 0 (the contracts are already sufficient) |
| `:data` | 0 (the API is unified server-side) |
| `:features` | **0** |
| `:features-chatbot` | 0 |
| Sibling concrete tenants (same or other region) | 0 |
| Other region bases | 0 |
| `:app` | ~3 lines: `TenantCatalogue` entry only |
| `settings.gradle.kts` | 1 line per new module |

If a PR onboarding a tenant has more than that, **review it** — something is leaking into the wrong layer.

---

## 5. Onboarding Verification Checklist

Run before merging:

```
[ ] :tenants:cambodia:partner-a compiles standalone
    (./gradlew :tenants:cambodia:partner-a:assembleDebug)
[ ] :tenants:cambodia:partner-a declares Gradle dependency on :tenants:cambodia:base
    (verify build.gradle.kts)
[ ] :features compiles without :tenants:cambodia:partner-a as a dependency
[ ] :data compiles without :tenants:cambodia:partner-a as a dependency
[ ] No reference to "cambodia:partner-a" exists as a string literal in :features or :data source sets
    (no if/when on tenant.id outside :app/di/TenantResolverModule.kt)
[ ] Logging in with a "cambodia:partner-a" user resolves PartnerAStaffIdValidator as the active StaffIdValidator
    (verifiable via instrumentation test that injects tenantId and asserts the bound class)
[ ] Logging out and logging in as a "cambodia:nh" user resolves NhKhStaffIdValidator instead
    (no stale Partner A state, no stale tokens)
[ ] Policy unit tests cover happy-path + at least one boundary case per method
[ ] Capability flags match the product spec
[ ] :app size delta (release APK) reasonable (typically <100 KB for a new tenant — tenants are small)
```

---

## 6. When Something Doesn't Fit

Some scenarios require thought:

### "The new tenant has a feature no other tenant has."

Three options, ordered by scope:

1. **It's a UI difference only** (a button, a label, a field) → use a `TenantCapabilities` flag in `:core`. Add a method, implement it in every tenant (others return `false`), and gate the UI in `:features` on the flag. No new module.

2. **It's a new flow built on existing APIs** (Compose + ViewModels, no new endpoints) → add a new package to `:features`, gate its visibility on the new capability flag. Stays in `:features`; no new module.

3. **It's a unique feature with its own API endpoints, DTOs, and screens** → create a dedicated `:features-{feature-name}` module (mirroring `:features-chatbot`). The module owns the tenant-unique Retrofit interface, DTOs, repository, and Composables. `:app` conditionally includes its nav graph based on the capability flag. See [07 — `:tenants:*` § 9](07-variants.md).

In all three options, the tenant module (`:tenants:{region}:{tenantSlug}`) stays policies + DI only — it does not absorb feature code.

### "The new tenant uses a totally different validation rule."

That's exactly what `*Policy` interfaces are for. Implement the policy with the new rule; no other module changes.

### "The new tenant uses a different backend protocol."

This should not happen — the unified fintech API is the architectural assumption. If it does, escalate to backend before adding any tenant-only API code; that's a layering violation that defeats the design.

### "The new tenant is staging-only."

Same module shape. Server-side gating ensures production users never receive the new tenant's `tenantId`. The Android side stays oblivious — it can't know, and shouldn't.

### "I need to share a policy across regions."

That's the signal to **promote to `:core`**. Region bases must not depend on each other; a shared `:tenants:base` super-module is forbidden. If `:tenants:cambodia:base` and `:tenants:vietnam:base` both need the same policy class verbatim, move the class to `:core` and have both region bases depend on it.

---

## 7. Cross-references

- The boot mechanism that activates the tenant: [10 — Boot Phases](10-boot-phases.md)
- The contract interfaces being implemented: [03 — `:core`](03-core.md)
- The shared data layer that every tenant uses: [05 — `:data`](05-data.md)
- The `Session` and account model: [12 — Departments and Session](12-departments-and-session.md)
- The tenant behavioral model: [19 — Tenants and Regions](19-tenants-and-variants.md)
- The module structure for tenants and region bases: [07 — `:tenants:*`](07-variants.md)
- Build performance implications of new modules: [14 — Build Performance](14-build-performance.md)
