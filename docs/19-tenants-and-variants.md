# 19 · Tenants and Regions

> **Why this doc exists:** Real-world multi-customer fintech needs to flex along two pressures — different regulators/regions, and different customer organizations inside those regions. The framework handles this with **a single DI axis (tenant) plus a Gradle module hierarchy (region)**. One axis to dispatch on; the region grouping is structural. This doc gives each shape a name, a place to live, and a discipline for telling them apart.

> **Historical note:** earlier iterations of this framework documented two parallel DI axes (variant + tenant). That model has been collapsed. Region is no longer a runtime DI axis — it's expressed as Gradle module dependency. See §11 for the rationale.

---

## 1. One axis, with a regional hierarchy

```
                                  region (Gradle hierarchy, NOT a DI axis)
                                  ──────────────────────────────────────
                                  cambodia              korea
                                  :tenants:cambodia:    :tenants:korea:
   tenant (the only DI axis)      ──────────────────    ──────────────────
   ─────────────────────────
   base (shared region policy)         ●                     ●
   default                             ●                     ●
   nh                                  ●                     ●
   posco-ict                                                 ●
   itcen                                                     ●
   lotte                                                     ●
   nia                                                       ●
   shinsegae                                                 ●
   wips                                                      ●
```

| Shape | Compass term | What differs | Lifetime | Bound where |
|---|---|---|---|---|
| **Region** | (Gradle hierarchy) | Currency, rails, compliance thresholds, holiday calendar, OTP channel mandated by regulator, regional KYC requirements | Per login; doesn't change without logout | `:tenants:{region}:base` Gradle module — concrete tenants depend on it |
| **Tenant** | **Tenant** (the only DI axis) | Field visibility, label strings, employee-ID format, approval-line shape, footer text, per-org capability toggles. Plus regional baseline overrides. | Per login; doesn't change without logout | `TenantContext` carried in `Session`; concrete impls inside `:tenants:{region}:{tenantId}/` |
| **Account inside a tenant** | `DepartmentAccount` | Active account ID stamped on requests | Switchable any time inside one session | `Session.activeAccountId: StateFlow` (see [12](12-departments-and-session.md)) |

**Two nested concepts: Tenant ⊃ Account.** Region appears in the diagram but is NOT a runtime DI dimension — it's how tenants are organized on disk and how shared policies are reused.

---

## 2. When a difference is region-baseline vs tenant-override

| Question | If "yes" → it goes in **`:tenants:{region}:base`** | If "yes" → it goes in **`:tenants:{region}:{tenantId}/`** |
|---|---|---|
| Different currency / settlement rail? | ✓ | |
| Different regulator (NBC vs SBV vs FSS)? | ✓ | |
| Different KYC body / compliance limits? | ✓ | |
| Different OTP channel mandated by law? | ✓ | |
| Different regional holiday calendar? | ✓ | |
| Different fee tiering? | usually | rarely |
| Different label text / footer / disclosure language? | possibly | usually |
| Hides or shows a field on a screen? | rarely | usually |
| Different approval-line shape? | rarely | usually |
| Different employee-ID format / regex? | rarely | usually |
| Different per-org capability toggle? | | ✓ |

**Rule of thumb:** if a regulator change rolls out across every tenant in the region uniformly, the difference belongs in the **region base**. If a single tenant org needs the change while the rest of the region doesn't, it belongs in the **concrete tenant**.

---

## 3. Module Placement

```
:tenants/
└── korea/
    ├── base/                                             ← region baseline (Gradle module)
    │   └── src/main/kotlin/com/<org>/tenants/korea/base/
    │       ├── policy/
    │       │   ├── KrDefaultFeeCalculator.kt             (regulator-wide KR rules)
    │       │   ├── KrwAmountFormatter.kt
    │       │   ├── KrComplianceThresholds.kt
    │       │   ├── KrOtpDeliveryPolicy.kt
    │       │   └── KrBusinessCalendar.kt
    │       └── capability/
    │           └── KrBaseCapabilities.kt                 (KR-baseline flags concrete tenants can inherit)
    ├── default/                                          ← empty/sentinel tenant
    │   └── di/
    │       └── KrDefaultTenantModule.kt                  (@TenantKey("korea:default") bindings, all reusing base)
    ├── nh/                                               ← concrete tenant
    │   ├── policy/
    │   │   └── NhKhKycRequirementPolicy.kt               (overrides KR baseline)
    │   ├── flags/
    │   │   └── NhTenantProfile.kt                        (TenantContext factory: flags + params)
    │   └── di/
    │       └── NhTenantModule.kt                         (@TenantKey("korea:nh") bindings)
    └── shinsegae/                                        ← concrete tenant with structural escalation
        ├── policy/
        │   └── ShinsegaeApprovalLineRenderer.kt          (structural — different layout from KR baseline)
        ├── flags/
        │   └── ShinsegaeTenantProfile.kt
        └── di/
            └── ShinsegaeTenantModule.kt                  (@TenantKey("korea:shinsegae") bindings)
```

**Each concrete tenant module declares Gradle dependency on its region base.** Hilt sees the union of bindings; concrete-tenant bindings win on conflict via standard `@Binds` precedence within the per-tenant map slot.

**A tenant exists inside one region.** A customer who operates across two regions has two tenant entries — `:tenants:cambodia:nh` and `:tenants:korea:nh` are independent modules with independent profiles, separate Hilt keys, and (usually) different policy impls.

---

## 4. `TenantContext` in `:core`

`TenantContext` is an immutable snapshot resolved at login.

```kotlin
// :core/tenant/TenantContext.kt
data class TenantContext(
    val id: TenantId,
    val displayName: String,
    val regionCode: String,          // "kh", "kr", … — informational only, NOT used for DI dispatch
    val defaultCurrency: Currency,
    val flags: TenantFlags,
    val params: TenantParams,
)

// :core/tenant/TenantFlags.kt — explicit, named booleans (NOT a Map<String, Boolean>)
data class TenantFlags(
    val hidesEmployeeId: Boolean = false,
    val clearsEmployeeNumberOnApproval: Boolean = false,
    val requiresIdNumberCapture: Boolean = false,
    val showsBilingualReceipt: Boolean = false,
    val allowsPasswordResetInApp: Boolean = false,
    // …grows as new tenants reveal new dimensions; each addition is a :core PR
)

// :core/tenant/TenantParams.kt — explicit, named, typed
data class TenantParams(
    val employeeIdRegex: String? = null,
    val approvalLineMaxDepth: Int = 5,
    val receiptFooterText: String? = null,
    val supportPhoneOverride: String? = null,
    // …
)

// :core/tenant/TenantId.kt
@JvmInline value class TenantId(val value: String)   // composite: "<region>:<tenantSlug>", e.g. "korea:shinsegae"
```

**`TenantId.value` is a composite of `<region>:<tenantSlug>`.** The composite makes the same tenant slug unambiguous across regions (e.g., `"cambodia:nh"` vs `"korea:nh"`). `regionCode` is a separate field on `TenantContext` for display and informational use only — **never branch on `regionCode`**, same rule as never branch on `tenant.id`.

**Why named fields, not a `Map<String, Any>`?**

| Map<String, Any> | Named fields |
|---|---|
| Compile-time invisible — what tenants set what? | Grep finds every consumer |
| Typos compile and crash at runtime | Typos fail to compile |
| Schema evolves silently | Schema changes are reviewable PRs to `:core` |
| Loose contract with the server | Tight contract with the server |

A `Map`-style backdoor is exactly how `DetailConfig.isXxx()` antipatterns return. The framework forbids it.

---

## 5. Default: parameterized policies (most cases)

Most tenant variability flows through `TenantContext` consumed by **existing** policies bound at the region-base layer. No new policy interface; no new tenant-specific impl.

```kotlin
// :features/receipt/detail/ReceiptDetailViewModel.kt
class ReceiptDetailViewModel @Inject constructor(
    private val tenant:    TenantContext,
    private val visibility: ReceiptVisibilityPolicy,  // bound at region-base level
) : MviViewModel<…>() {

    private val showEmployeeId      = !tenant.flags.hidesEmployeeId
    private val employeeIdValidator = tenant.params.employeeIdRegex?.toRegex()

    init { /* state.value = ReceiptDetailState(showEmployeeId, …) */ }
}
```

The ViewModel reads **fields**, never `tenant.id` or `tenant.regionCode`. Same Logic-Blind rule that applied to variants applies to tenants.

| Was (antipattern) | Now (Compass) |
|---|---|
| `if (DetailConfig.isNIA()) hideEmployeeId()` (in 12 places) | `if (tenant.flags.hidesEmployeeId) …` (or just bind the flag to `UiState`) |
| `if (DetailConfig.isWIPS()) ID_NUMBER = ""` (in adapter) | Read `tenant.flags.clearsEmployeeNumberOnApproval` in the VM, pass the cleared model to the adapter |
| `if (isPOSCO_ICT()) showPoscoReceipt()` | Promote `BZP_TRIP` evidence type to a `ReceiptEvidenceClassifier` policy if it's structural; otherwise `tenant.flags.usesTripExpenseFlow` |

A new tenant onboarded with parametric differences adds **one file**: a `TenantProfile` factory that returns its `TenantContext`. No `:features` change, no `:core` change.

---

## 6. Escalation: structural `TenantPolicy` (when params aren't enough)

Sometimes a tenant's behavior is structurally different — Shinsegae has a fundamentally different approval-line shape, not just different fields. That's the escalation point: a new policy interface in `:core/policy/`, with per-tenant impls inside the tenant module.

```kotlin
// :core/policy/ApprovalLineRenderer.kt
interface ApprovalLineRenderer {
    fun render(line: ApprovalLine, tenant: TenantContext): RenderedApprovalLine
}

// :tenants:korea:base/policy/KrDefaultApprovalLineRenderer.kt
internal class KrDefaultApprovalLineRenderer : ApprovalLineRenderer { /* standard layout */ }

// :tenants:korea:shinsegae/policy/ShinsegaeApprovalLineRenderer.kt
internal class ShinsegaeApprovalLineRenderer : ApprovalLineRenderer { /* Shinsegae layout */ }
```

### Decision table — flag, param, or structural?

| Shape of the per-tenant difference | Goes in… |
|---|---|
| "Hide / show field X" | `TenantFlags.hidesX: Boolean` |
| "Allow values matching regex R" | `TenantParams.xRegex: String` |
| "Display text Y here" | `TenantParams.yText: String` |
| "Render this thing entirely differently" | New `:core/policy/` interface + tenant impls |
| "Different network endpoint" | Region-base policy (regulator concern), not concrete-tenant |

If you're tempted to add a flag like `usesShinsegaeApprovalLine: Boolean`, that's the smell that escalation is correct — the consumer would branch on the flag and produce different UIs, which is structural. Add the interface instead.

---

## 7. Server contract: login returns the tenant

```json
POST /v1/auth/login (response)
{
  "userSession":  { … },
  "tenantId":     "korea:shinsegae",
  "regionCode":   "kr",
  "defaultCurrency": "KRW",
  "tenantFlags":  {
    "hidesEmployeeId":                false,
    "clearsEmployeeNumberOnApproval": false,
    "requiresIdNumberCapture":        false,
    "showsBilingualReceipt":          false,
    "allowsPasswordResetInApp":       true
  },
  "tenantParams": {
    "employeeIdRegex":     "^[A-Z]\\d{6}$",
    "approvalLineMaxDepth": 7,
    "receiptFooterText":   null
  },
  "accounts":  [ … ]
}
```

`AuthRepository.login` returns a `LoginResponse` carrying `tenantId`. `BootCoordinator` builds `LoggedInComponent` with the resolved `TenantContext`. If the server returns a `tenantId` with no matching Hilt binding, **boot fails fast with a clear error** — no silent fallback to a default tenant in production. The `:tenants:{region}:default` module exists for tests and as the no-overrides baseline; production users must always have a real tenant.

```kotlin
// :core/model/LoginResponse.kt
data class LoginResponse(
    val userSession:     UserSession,
    val tenantId:        TenantId,
    val regionCode:      String,
    val defaultCurrency: Currency,
    val tenantFlags:     TenantFlags,
    val tenantParams:    TenantParams,
    val accounts:        List<DepartmentAccount>,
)
```

The server is the source of truth for `flags` and `params`. The framework does **not** ship a hardcoded `flags` table per tenant inside the client — that's the same antipattern as hardcoded URLs.

> **Why server-sourced flags?** The same reason MG sources URLs at runtime: changing a flag mid-quarter shouldn't require an APK release. The client only needs the *schema* (typed fields in `TenantFlags`); the *values* are server-supplied.

---

## 8. DI dispatch: one map, one resolver, one key

Tenant impls bind via Hilt multibindings keyed by `@TenantKey`. The composite tenant id (`<region>:<tenantSlug>`) is the map key.

```kotlin
// :core/scope/TenantKey.kt
@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class TenantKey(val value: String)
```

### Region-base module (provides shared regional policies, keyed by every tenant in the region)

The recommended v1 pattern is **concrete-rebinds-everything**: the region base provides reusable *implementation classes*; each concrete tenant module declares the Hilt `@TenantKey` bindings for its own tenant slug, reusing the base implementation classes where the regional baseline applies and supplying overrides where they don't.

```kotlin
// :tenants:korea:base/policy/KrDefaultOtpDeliveryPolicy.kt  ← class only, no Hilt binding here
internal class KrDefaultOtpDeliveryPolicy : OtpDeliveryPolicy {
    override val preferredChannel = OtpChannel.Sms
    override val codeLength = 6
    override val expirySeconds = 300
}

// :tenants:korea:nh/di/NhTenantModule.kt  ← concrete tenant declares all its bindings
@Module
@InstallIn(LoggedInComponent::class)
abstract class NhTenantModule {
    @Binds @IntoMap @TenantKey("korea:nh") @LoggedInScoped
    abstract fun otpDelivery(impl: KrDefaultOtpDeliveryPolicy): OtpDeliveryPolicy   // reuses base class

    @Binds @IntoMap @TenantKey("korea:nh") @LoggedInScoped
    abstract fun approvalLine(impl: KrDefaultApprovalLineRenderer): ApprovalLineRenderer

    @Binds @IntoMap @TenantKey("korea:nh") @LoggedInScoped
    abstract fun amountFormatter(impl: KrwAmountFormatter): AmountFormatter
    // … one binding per :core policy interface
}

// :tenants:korea:shinsegae/di/ShinsegaeTenantModule.kt  ← concrete tenant with override
@Module
@InstallIn(LoggedInComponent::class)
abstract class ShinsegaeTenantModule {
    @Binds @IntoMap @TenantKey("korea:shinsegae") @LoggedInScoped
    abstract fun otpDelivery(impl: KrDefaultOtpDeliveryPolicy): OtpDeliveryPolicy   // reuses base

    @Binds @IntoMap @TenantKey("korea:shinsegae") @LoggedInScoped
    abstract fun approvalLine(impl: ShinsegaeApprovalLineRenderer): ApprovalLineRenderer  // overrides

    @Binds @IntoMap @TenantKey("korea:shinsegae") @LoggedInScoped
    abstract fun amountFormatter(impl: KrwAmountFormatter): AmountFormatter
    // … one binding per :core policy interface
}
```

**Trade-off:** the concrete-rebinds-everything pattern means each tenant module declares the full set of `@TenantKey` bindings — a bit more boilerplate than chain-walking, but no custom resolver and no surprises at runtime. Recommended for v1; revisit if tenant count grows past ~10.

### The resolver in `:app`

`:app` provides one resolver per `:core` policy interface — each picks the active impl from the multibindings map by `TenantContext.id.value`:

```kotlin
// :app/di/TenantResolverModule.kt
@Module
@InstallIn(LoggedInComponent::class)
object TenantResolverModule {

    @Provides @LoggedInScoped
    fun otpDeliveryPolicy(
        tenant: TenantContext,
        all: Map<String, @JvmSuppressWildcards OtpDeliveryPolicy>,
    ): OtpDeliveryPolicy = checkNotNull(all[tenant.id.value]) {
        "No OtpDeliveryPolicy registered for tenant ${tenant.id}"
    }

    @Provides @LoggedInScoped
    fun approvalLineRenderer(
        tenant: TenantContext,
        all: Map<String, @JvmSuppressWildcards ApprovalLineRenderer>,
    ): ApprovalLineRenderer = checkNotNull(all[tenant.id.value]) {
        "No ApprovalLineRenderer registered for tenant ${tenant.id}"
    }

    // … one provider per :core policy interface (mechanical; ~10 entries)
}
```

The map lookup is the **single point of dispatch** in the codebase — no `when (tenant.id)` branching anywhere else.

For non-structural (flag/param) differences, **no Hilt entry is needed** — the `TenantContext` itself is injected into the VM directly.

---

## 9. Worked example: BizPlay's 11 customers under `:tenants:korea:*`

Mapping the BizPlay antipattern (`DetailConfig.isXxx()`) to Compass's tenant model:

| BizPlay predicate | Compass shape | Where it lives |
|---|---|---|
| `isPOSCO_ICT()` → use BZP_TRIP receipt type | `flags.usesTripExpenseFlow = true` | `:tenants:korea:posco-ict/flags/PoscoIctTenantProfile.kt` |
| `isNIA()` → hide employee ID, mask ID number | `flags.hidesEmployeeId = true`, `flags.requiresIdNumberCapture = true` | `:tenants:korea:nia/flags/NiaTenantProfile.kt` |
| `isWIPS()` → clear `ID_NUMBER` on approval | `flags.clearsEmployeeNumberOnApproval = true` | `:tenants:korea:wips/flags/WipsTenantProfile.kt` |
| `isShinsegae()` → different approval line shape | Structural — `ShinsegaeApprovalLineRenderer` | `:tenants:korea:shinsegae/policy/` |
| `isLotte()` → photo list / receipt detail tweaks | `flags.usesLotteReceiptStyle = true` (or a structural renderer) | `:tenants:korea:lotte/` |
| `isITCen()` → minor UI tweaks | `flags.*` as needed | `:tenants:korea:itcen/` |
| `isChilsungBeverage()` → BSNN-NO-based detection | (Same as Lotte; child of Lotte's flags) | folded into `lotte/` |
| `isHANA()` → `allowsPasswordResetInApp = true` | `flags.allowsPasswordResetInApp = true` | `:tenants:korea:hana/` |
| `isIBS()` | One small flag | `:tenants:korea:ibs/` |
| `isSPC()` | Capability flag | `:tenants:korea:spc/` |
| `isPOSTGRES()` | Test-env naming; **not a tenant** — it's a buildType | (Drop entirely; use `BuildConfig.MG_URL` per buildType — see [11 § 3](11-mg-and-runtime-config.md)) |

**124 call sites of `DetailConfig.isXxx()` collapse to:**

- ~5 named fields in `TenantFlags`
- ~3 named fields in `TenantParams`
- 1 structural policy (`ApprovalLineRenderer`) with 2 impls (default + Shinsegae) bound at the concrete-tenant layer
- 10 `TenantProfile` factories (one per tenant, each ~10–30 lines)

Zero `if (tenant.id == "…")` branches anywhere in `:features`.

---

## 10. Onboarding cost

| Action | New **region** (e.g., add Vietnam) | New **tenant in existing region** (e.g., add `lotte` to Korea) |
|---|---|---|
| New region-base module (`:tenants:{region}:base/`) | ✓ | ✗ |
| New region `default` tenant module | ✓ | ✗ |
| New concrete tenant module | ✓ (the first one) | ✓ |
| Region-baseline policies (currency, regulator rules, calendar) | ✓ (full set in base) | reuse from base |
| `TenantProfile` factory | ✓ (default + concrete) | ✓ (~10–30 lines) |
| Hilt `@TenantKey` bindings | ✓ (full policy set per concrete tenant) | ✓ (full policy set, reusing base classes) |
| `settings.gradle.kts` change | ✓ `include(":tenants:vietnam:base")`, `:default`, plus concrete tenants | ✓ `include(":tenants:korea:lotte")` |
| `TenantCatalogue` entry | ✓ (one per concrete tenant in the new region) | ✓ (one line: `TenantId("korea:lotte") to ::lotteProfile`) |
| `:features` change | ✗ | ✗ |
| `:data` change | ✗ | ✗ |
| `:core` change | only if adding a new policy interface | only if adding a new flag, param, or `TenantPolicy` |

A concrete tenant is **a single PR**: the module folder, the Hilt module, the `TenantProfile`, the catalogue entry. See [13 — Onboarding a Tenant](13-onboarding-a-variant.md).

---

## 11. What is NOT a tenant

| ❌ Don't model as a tenant | ✅ Goes in |
|---|---|
| A different environment (prod vs staging) | `BuildConfig.MG_URL` per buildType (see [11 § 3](11-mg-and-runtime-config.md)) |
| A user's individual settings (notification prefs) | User-scoped prefs (`PreferenceDelegator`-equivalent) on `Session` |
| A user's role inside an org (admin vs member) | Permission set on `UserSession` |
| A subaccount inside an org | `DepartmentAccount` — see [12](12-departments-and-session.md) |
| A Play Store reviewer flag | `RuntimeConfig.storeReviewMode` — see [11 § 6.5](11-mg-and-runtime-config.md) |
| A non-released feature being A/B tested | Feature-flag system (Firebase Remote Config); not MG, not tenant |
| "What region does this user live in" | `TenantContext.regionCode` — informational only, never branch on it |

If a difference is **temporary** (rollout, kill switch, experiment), it is **not a tenant** — tenants are stable organizational identities. The other mechanisms exist for the temporary cases.

---

## 12. Why was the variant axis collapsed?

Earlier iterations of this framework documented two parallel DI axes — `VariantContext` (region) and `TenantContext` (org-in-region). The two-axis model was load-bearing when consuming apps were expected to host multiple regions in a single APK. In practice:

- The framework's consuming apps are per-product, with one region per app in the realistic case.
- Where multi-tenant was useful, the same tenant-org was almost never identical across regions — splitting them as `(region, tenant)` pairs created composite keys (`cambodia:nh`, `korea:nh`) that were structurally separate from each other anyway.
- The two-axis model required two resolvers, two map keys, two `Catalogue` types, two onboarding flows, and two sets of forbidden-import rules.
- The compile-time-enforced regional grouping (which tenants share KR policies) is just as reliable when expressed as a Gradle module dependency: if a concrete tenant module forgets to declare its `:tenants:{region}:base` dependency, Hilt fails to resolve the regional policies at boot. Fail-fast at boot, not at runtime.

The collapsed single-axis model preserves every load-bearing property of the original (no `if (id == ...)` branching, single point of dispatch, additive onboarding, the `default` tenant per region) and removes ~30% of the architectural surface.

---

## 13. Hard invariants

These extend the invariants in CLAUDE.md.

1. **`TenantContext` is immutable for the session.** Tenant change requires logout.
2. **`:features` reads tenant fields, never `tenant.id` or `tenant.regionCode`.** Branching on either is the antipattern this whole model is designed to eliminate. Use flags, params, or structural `TenantPolicy`.
3. **Tenant flags are server-sourced.** The client owns the *schema*; the server owns the *values*. No hardcoded per-tenant flag tables in the client.
4. **Tenants live inside region directories.** A multi-region customer has separate tenant entries per region (`:tenants:cambodia:nh` and `:tenants:korea:nh` are independent modules).
5. **Every region has a `:tenants:{region}:default` module.** Single-tenant regions still ship a `default` tenant. This keeps the `TenantContext` field on `Session` non-nullable in tests and makes "the no-overrides baseline" a real testable target.
6. **`TenantFlags` and `TenantParams` use named, typed fields.** No `Map<String, Any>` backdoors.
7. **Concrete tenant modules declare Gradle dependency on their region base.** Hilt resolution of regional policies depends on the base being on the classpath.
8. **`TenantId.value` is a composite `<region>:<tenantSlug>`.** Same slug under different regions are independent tenants with separate bindings.
9. **Environments are buildTypes, not tenants.** "POSTGRES", "staging", "prod" are not tenants.

---

## 14. Cross-references

- The module structure for tenants and region bases: [07 — Tenants and Region Bases](07-variants.md)
- The `:core` contracts layer where `TenantContext` / `TenantFlags` / `TenantParams` live: [03 — `:core`](03-core.md)
- The login boot phase that resolves the tenant: [10 — Boot Phases](10-boot-phases.md)
- The `LoggedInComponent` that holds `TenantContext`: [10 — Boot Phases](10-boot-phases.md)
- The `Session.activeAccountId` (the account axis): [12 — Departments and Session](12-departments-and-session.md)
- Onboarding a tenant or region: [13 — Onboarding a Tenant](13-onboarding-a-variant.md)
- The store-review-mode mechanism (NOT a tenant): [11 — MG and Runtime Config § 6.5](11-mg-and-runtime-config.md)
