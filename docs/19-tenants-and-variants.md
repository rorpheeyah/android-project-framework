# 19 · Tenants and Variants

> **Why this doc exists:** Real-world Bizplay has two axes of "differs per customer", not one. The existing codebase collapses them into a single sprawl of `DetailConfig.isXxx()` predicates. The framework gives each axis a name, a place to live, and a discipline for telling them apart.

---

## 1. Two axes, not one

```
                              region / regulator boundary
                              ───────────────────────────
                              KR (Korea)       KH (Cambodia)   VN (Vietnam)
                              :variants-kr     :variants-kh    :variants-vn
   customer-org boundary       ─────────────  ─────────────   ─────────────
   ───────────────────────────
   default                        ●                ●               ●
   posco_ict                      ●
   itcen                          ●
   lotte                          ●
   nia                            ●
   shinsegae                      ●
   wips                           ●
   hana                           ●
   ibs                            ●
   spc                            ●
```

| Axis | Bizplay framework term | What differs | Lifetime | Bound where |
|---|---|---|---|---|
| **Region / regulator** | **Variant** | Currency, tax rules, fee/reimbursement caps, business calendar, OTP channel, capability flags (KakaoPay, Hi-Pass, MyData), receipt format | Per login; doesn't change without logout | `:variants-{region}/` module |
| **Customer org inside a region** | **Tenant** | Field visibility (employee ID, ID number), label strings, employee-ID format / regex, approval-line shape, footer text, receipt-style preference | Per login; doesn't change without logout | `TenantContext` carried in `Session`; defaults inside `:variants-{region}/tenants/` |
| **Institution membership inside an org** | `DepartmentAccount` | Active `USE_INTT_ID` + `COMPANY_CD` stamped on requests | Switchable any time inside one session | `Session.activeAccountId: StateFlow` (see [12](12-departments-and-session.md)) |

Three nested concepts. **Variant ⊃ Tenant ⊃ Account.** Each has a different change frequency and a different binding mechanism.

> **The existing Bizplay code conflates axes 1 and 2.** `DetailConfig.isNIA()`, `isPOSCO_ICT()`, etc. are *tenant* predicates today, but they appear in places that should be variant-only (currency formatting, business calendar) and vice versa. The framework forces a clean separation.

---

## 2. When something is a variant vs a tenant

| Question | If "yes" → it's a **variant** | If "yes" → it's a **tenant** |
|---|---|---|
| Different currency / settlement rail? | ✓ | |
| Different regulator (NTS / FSS in Korea vs NBC in Cambodia)? | ✓ | |
| Different tax/VAT rules? | ✓ | |
| Different OTP channel mandated by law? | ✓ | |
| Different fee tiering or per-category reimbursement cap? | usually | rarely |
| Different label text / footer / disclosure language? | possibly | usually |
| Hides or shows a field on a screen? | rarely | usually |
| Different approval-line shape (number of steps, ordering)? | rarely | usually |
| Different employee-ID format / regex? | rarely | usually |
| Different per-org capability toggle? | | ✓ |

**Rule of thumb:** If a Korean regulatory change rolls out across every customer in Korea uniformly, the difference belongs in the **variant**. If a single customer org (POSCO ICT, NIA, Shinsegae) needs the change while the rest of Korea doesn't, it belongs in the **tenant**.

---

## 3. Module Placement

```
:variants-kr/                                          ← regulator/region boundary
├── policy/                                             (variant-level: KR tax rules, KRW format,
│   ├── KrFeeCalculator.kt                                  Korean OTP rules, business calendar, …)
│   ├── KrwAmountFormatter.kt
│   └── KrApprovalThresholds.kt
├── capability/
│   └── KrCapabilities.kt
├── tenants/                                           ← tenant directory
│   ├── default/
│   │   ├── DefaultKrTenantProfile.kt                  (TenantContext factory: flags+params)
│   │   └── DefaultApprovalLineRenderer.kt             (structural impl when params aren't enough)
│   ├── nia/
│   │   └── NiaTenantProfile.kt
│   ├── posco_ict/
│   │   └── PoscoIctTenantProfile.kt
│   ├── shinsegae/
│   │   ├── ShinsegaeTenantProfile.kt
│   │   └── ShinsegaeApprovalLineRenderer.kt           (structural — different layout)
│   ├── lotte/
│   │   └── LotteTenantProfile.kt
│   ├── itcen/
│   │   └── ItcenTenantProfile.kt
│   ├── wips/
│   │   └── WipsTenantProfile.kt
│   ├── hana/
│   │   └── HanaTenantProfile.kt
│   ├── ibs/
│   │   └── IbsTenantProfile.kt
│   └── spc/
│       └── SpcTenantProfile.kt
└── di/
    ├── KrVariantModule.kt                             (variant-level Hilt bindings)
    └── KrTenantModule.kt                              (tenant @IntoMap dispatch within :variants-kr)
```

A tenant is **a directory inside its parent variant**, not a sibling module. Reason: a tenant only makes sense in the regulatory context of its variant. A customer who operates across two markets (rare but possible — say POSCO with subsidiaries in both Korea and Vietnam) has two tenant entries (one per variant), with separate IDs and independent profiles.

---

## 4. `TenantContext` in `:core`

`TenantContext` lives alongside `VariantContext` in `:core/variant/`. Both are immutable snapshots resolved at login.

```kotlin
// :core/variant/TenantContext.kt
data class TenantContext(
    val id: TenantId,
    val displayName: String,
    val flags: TenantFlags,
    val params: TenantParams,
)

// :core/variant/TenantFlags.kt — explicit, named booleans (NOT a Map<String, Boolean>)
data class TenantFlags(
    val hidesEmployeeId: Boolean = false,                  // NIA today
    val clearsEmployeeNumberOnApproval: Boolean = false,   // WIPS today
    val requiresIdNumberCapture: Boolean = false,          // NIA today
    val usesTripExpenseFlow: Boolean = false,              // POSCO ICT today (BZP_TRIP receipt type)
    val usesLotteReceiptStyle: Boolean = false,            // Lotte / Chilsung today
    val allowsPasswordResetInApp: Boolean = false,         // HANA today
    val showsBilingualReceipt: Boolean = false,
    // …grows as new tenants reveal new dimensions; each addition is a :core PR
)

// :core/variant/TenantParams.kt — explicit, named, typed
data class TenantParams(
    val employeeIdRegex: String? = null,
    val approvalLineMaxDepth: Int = 5,
    val receiptFooterText: String? = null,
    val supportPhoneOverride: String? = null,
    // …
)

// :core/variant/TenantId.kt
@JvmInline value class TenantId(val value: String)
```

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

Most tenant variability flows through `TenantContext` consumed by **existing** variant-level policies. No new policy interface; no new variant impl.

```kotlin
// :features/receipt/detail/ReceiptDetailViewModel.kt
class ReceiptDetailViewModel @Inject constructor(
    private val tenant:    TenantContext,
    private val visibility: ReceiptVisibilityPolicy,  // variant-level
) : MviViewModel<…>() {

    private val showEmployeeId      = !tenant.flags.hidesEmployeeId
    private val employeeIdValidator = tenant.params.employeeIdRegex?.toRegex()
    private val requiresIdCapture   = tenant.flags.requiresIdNumberCapture

    init { /* state.value = ReceiptDetailState(showEmployeeId, requiresIdCapture, …) */ }
}
```

The ViewModel reads **fields**, never `tenant.id`. Same Logic-Blind rule as variants.

| Was (today's Bizplay antipattern) | Now (framework) |
|---|---|
| `if (DetailConfig.isNIA()) hideEmployeeId()` (in 12 places) | `if (tenant.flags.hidesEmployeeId) …` (or just bind the flag to `UiState`) |
| `if (DetailConfig.isWIPS()) ID_NUMBER = ""` (in adapter) | Read `tenant.flags.clearsEmployeeNumberOnApproval` in the VM, pass the cleared model to the adapter |
| `if (isPOSCO_ICT()) showPoscoReceipt()` | Promote `BZP_TRIP` evidence type to a `ReceiptEvidenceClassifier` policy if it's structural; otherwise `tenant.flags.usesTripExpenseFlow` |
| `if (isHANA()) showPasswordResetButton()` | `tenant.flags.allowsPasswordResetInApp` |
| `if (isChilsungBeverage()) lotteReceiptStyle()` | `tenant.flags.usesLotteReceiptStyle` (Chilsung is a Lotte child — shares Lotte's flag) |

A new tenant onboarded with parametric differences adds **one file**: a `TenantProfile` factory that returns its `TenantContext`. No `:features` change, no `:core` change.

---

## 6. Escalation: structural `TenantPolicy` (when params aren't enough)

Sometimes a tenant's behavior is structurally different — Shinsegae has a fundamentally different approval-line shape, not just different fields. That's the escalation point: a new policy interface in `:core/policy/`, with per-tenant impls inside the variant module.

```kotlin
// :core/policy/ApprovalLineRenderer.kt
interface ApprovalLineRenderer {
    fun render(line: ApprovalLine, tenant: TenantContext): RenderedApprovalLine
}

// :variants-kr/tenants/default/DefaultApprovalLineRenderer.kt
internal class DefaultApprovalLineRenderer : ApprovalLineRenderer { /* standard 3-step layout */ }

// :variants-kr/tenants/shinsegae/ShinsegaeApprovalLineRenderer.kt
internal class ShinsegaeApprovalLineRenderer : ApprovalLineRenderer { /* Shinsegae's parallel-route layout */ }
```

This is exactly the variant escalation pattern — same shape, different scope.

### Decision table — flag, param, or structural?

| Shape of the per-tenant difference | Goes in… |
|---|---|
| "Hide / show field X" | `TenantFlags.hidesX: Boolean` |
| "Allow values matching regex R" | `TenantParams.xRegex: String` |
| "Display text Y here" | `TenantParams.yText: String` |
| "Render the approval line / receipt entirely differently" | New `:core/policy/` interface + tenant impls |
| "Different network endpoint" | (Usually) variant, not tenant — escalate to variant |
| "Different per-category expense cap" | Usually variant (e.g. KR caps); if it's truly per-customer, `TenantParams.<category>CapKrwOverride: Money?` |

If you're tempted to add a flag like `usesShinsegaeApprovalLine: Boolean`, that's the smell that escalation is correct — the consumer would branch on the flag and produce different UIs, which is structural. Add the interface instead.

---

## 7. Server contract: login returns the tenant

```json
POST /v1/auth/login (response)
{
  "userSession":  { … },
  "variantId":    "kr",
  "tenantId":     "shinsegae",
  "tenantFlags":  {
    "hidesEmployeeId":                false,
    "clearsEmployeeNumberOnApproval": false,
    "requiresIdNumberCapture":        false,
    "usesTripExpenseFlow":            false,
    "usesLotteReceiptStyle":          false,
    "allowsPasswordResetInApp":       false,
    "showsBilingualReceipt":          false
  },
  "tenantParams": {
    "employeeIdRegex":     "^[A-Z]\\d{6}$",
    "approvalLineMaxDepth": 7,
    "receiptFooterText":   null
  },
  "accounts":  [ … ]
}
```

`AuthRepository.login` returns a `LoginResponse` carrying both `variantId` and `tenantId`. `BootCoordinator` builds `LoggedInComponent` with both contexts. The defaults defined in `:variants-{region}/tenants/default/` are used if the server returns an unknown tenant — but **only as a hard-failover with a logged error**, not as a silent fallback.

```kotlin
// :core/model/LoginResponse.kt
data class LoginResponse(
    val userSession: UserSession,
    val variantId:   VariantId,
    val tenantId:    TenantId,
    val tenantFlags: TenantFlags,
    val tenantParams: TenantParams,
    val accounts:    List<DepartmentAccount>,
)
```

The server is the source of truth for both `flags` and `params`. The framework does **not** ship a hardcoded `flags` table per tenant inside the client — that's the same antipattern as hardcoded URLs.

> **Why server-sourced flags?** The same reason MgGate sources URLs at runtime: changing a flag mid-quarter shouldn't require an APK release. The client only needs the *schema* (typed fields in `TenantFlags`); the *values* are server-supplied. The existing Bizplay codebase has the *opposite* shape — `DetailConfig.isNIA()` returns a hardcoded boolean tied to compile-time strings — and the cost of that is the inability to onboard a new tenant without an APK release.

---

## 8. DI dispatch for structural tenant policies

Structural `TenantPolicy` impls bind via Hilt multibindings keyed by `@TenantKey` — same pattern as `@VariantKey`, one level deeper.

```kotlin
// :core/scope/TenantKey.kt
@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class TenantKey(val value: String)
```

Per-tenant module entries:

```kotlin
// :variants-kr/di/KrTenantModule.kt
@Module
@InstallIn(LoggedInComponent::class)
abstract class KrTenantModule {

    @Binds @IntoMap @TenantKey("default")  @LoggedInScoped
    abstract fun defaultApproval(impl: DefaultApprovalLineRenderer): ApprovalLineRenderer

    @Binds @IntoMap @TenantKey("shinsegae") @LoggedInScoped
    abstract fun shinsegaeApproval(impl: ShinsegaeApprovalLineRenderer): ApprovalLineRenderer

    // …
}
```

Resolver in `:app` (mirrors `VariantResolverModule`):

```kotlin
// :app/di/TenantResolverModule.kt
@Module
@InstallIn(LoggedInComponent::class)
object TenantResolverModule {

    @Provides @LoggedInScoped
    fun approvalLineRenderer(
        tenant: TenantContext,
        all: Map<String, @JvmSuppressWildcards ApprovalLineRenderer>,
    ): ApprovalLineRenderer = all[tenant.id.value]
        ?: all["default"]
        ?: error("No ApprovalLineRenderer registered, not even 'default'")
}
```

Note the **`default` fallback** — every variant ships a `default` tenant impl. New tenants with no special structural needs simply get the default impl by not appearing in the map.

For non-structural (flag/param) differences, **no Hilt entry is needed** — the `TenantContext` itself is injected into the VM directly.

---

## 9. Worked example: Bizplay's Korean tenants under `:variants-kr`

Mapping the Bizplay antipattern (`DetailConfig.isXxx()`) to the framework's tenant model:

| Existing Bizplay predicate | Framework shape | Where it lives |
|---|---|---|
| `isPOSCO_ICT()` → use BZP_TRIP receipt type | `flags.usesTripExpenseFlow = true` | `posco_ict/PoscoIctTenantProfile.kt` |
| `isNIA()` → hide employee ID, mask ID number | `flags.hidesEmployeeId = true`, `flags.requiresIdNumberCapture = true` | `nia/NiaTenantProfile.kt` |
| `isWIPS()` → clear `ID_NUMBER` on approval | `flags.clearsEmployeeNumberOnApproval = true` | `wips/WipsTenantProfile.kt` |
| `isShinsegae()` → different approval line shape | Structural — `ShinsegaeApprovalLineRenderer` | `shinsegae/` |
| `isLotte()` → photo list / receipt detail tweaks | `flags.usesLotteReceiptStyle = true` (or a structural renderer) | `lotte/LotteTenantProfile.kt` |
| `isITCen()` → minor UI tweaks | `flags.*` as needed | `itcen/ItcenTenantProfile.kt` |
| `isChilsungBeverage()` → BSNN-NO-based detection | (Same as Lotte; child of Lotte's flags) | folded into `lotte/` |
| `isHANA()` → `allowsPasswordResetInApp = true` | `flags.allowsPasswordResetInApp = true` | `hana/HanaTenantProfile.kt` |
| `isIBS()` | One small flag | `ibs/IbsTenantProfile.kt` |
| `isSPC()` | Capability flag | `spc/SpcTenantProfile.kt` |
| `isPOSTGRES()` | Test-env naming; **not a tenant** — it's an environment variant | (Drop entirely; use `BuildConfig.MG_URL` per buildType — see [11 § 3](11-mg-and-runtime-config.md)) |
| `isADD()` / `isADDDEV()` | Same as POSTGRES — environment names | (Drop entirely) |

**124 call sites of `DetailConfig.isXxx()` collapse to:**

- ~6 named fields in `TenantFlags`
- ~3 named fields in `TenantParams`
- 1 structural policy (`ApprovalLineRenderer`) with 2 impls (default + Shinsegae)
- 9 `TenantProfile` factories (one per real tenant, each ~10–30 lines)

Zero `if (tenant.id == "…")` branches anywhere in `:features`.

---

## 10. Onboarding a tenant — vs onboarding a variant

| Action | Onboarding a **variant** | Onboarding a **tenant** |
|---|---|---|
| New module | ✓ `:variants-{region}` | ✗ |
| New `tenants/` subfolder | (n/a) | ✓ `:variants-{region}/tenants/{id}/` |
| `TenantProfile` factory | (n/a) | ✓ (~10–30 lines) |
| Hilt `@VariantKey` bindings | ✓ (full policy set) | ✗ unless structural |
| Hilt `@TenantKey` bindings | (n/a) | ✓ only if a `TenantPolicy` interface is involved |
| `settings.gradle.kts` change | ✓ `include(":variants-{region}")` | ✗ |
| `VariantCatalogue` entry | ✓ | ✗ |
| `TenantCatalogue` entry | (n/a) | ✓ — one line: `TenantId("nia") to ::niaProfile` |
| `:features` change | ✗ | ✗ |
| `:data` change | ✗ | ✗ |
| `:core` change | only if adding a new variant-level policy interface | only if adding a new flag, param, or `TenantPolicy` |

A tenant is strictly more lightweight than a variant. The framework should make tenant onboarding **a single PR per tenant** — usually three files: the `TenantProfile`, the catalogue entry, and a possibly-empty `:variants-{region}/tenants/{id}/` subfolder. That's the win over today's `DetailConfig.isXxx()` shape — where adding one new customer means touching every screen that branches on customer identity.

---

## 11. What is NOT a tenant

| ❌ Don't model as a tenant | ✅ Goes in |
|---|---|
| A different environment (prod vs staging vs UAT vs sandbox) | `BuildConfig.MG_URL` per buildType (see [11 § 3](11-mg-and-runtime-config.md)) |
| A different regulator / region | New `:variants-{region}` module |
| A different currency / rail | New `:variants-{region}` module |
| A user's individual settings (notification prefs) | User-scoped prefs (`EncryptedPrefs`) on `Session` |
| A user's role inside an org (admin vs approver vs submitter) | Permission set on `UserSession` |
| A subaccount inside an org (which `USE_INTT_ID` is active) | `DepartmentAccount` — see [12](12-departments-and-session.md) |
| A Play Store reviewer flag | `RuntimeConfig.storeReviewMode` — see [11 § 6.5](11-mg-and-runtime-config.md) |
| A non-released feature being A/B tested | Feature-flag system (Firebase Remote Config); not MgGate, not tenant |
| A test/sandbox tenant inside the existing `DetailConfig` chain (`isPOSTGRES`, `isADD`, `isADDDEV`) | Not a tenant — those are environments. Use buildTypes. |

If a difference is **temporary** (rollout, kill switch, experiment), it is **not a tenant** — tenants are stable organizational identities. The other mechanisms exist for the temporary cases.

---

## 12. Hard invariants

These extend the invariants in CLAUDE.md.

1. **`TenantContext` is immutable for the session.** Tenant change requires logout. Same rule as `VariantContext`.
2. **`:features` reads tenant fields, never `tenant.id`.** Branching on `tenant.id` is the same antipattern as branching on `variantId`. Use flags, params, or structural `TenantPolicy`.
3. **Tenant flags are server-sourced.** The client owns the *schema*; the server owns the *values*. No hardcoded per-tenant flag tables in the client.
4. **Tenants live inside variants.** No `:tenants-{id}` sibling modules. A multi-region customer has separate tenant entries per variant.
5. **Every variant has a `default` tenant.** Single-tenant variants (`:variants-kh`, `:variants-vn` today) still ship a `default` profile. This keeps the `TenantContext` field on `Session` non-nullable.
6. **`TenantFlags` and `TenantParams` use named, typed fields.** No `Map<String, Any>` backdoors.
7. **Environments are buildTypes, not tenants.** `isPOSTGRES`, `isADD`, `isADDDEV` from the existing `DetailConfig` are dropped — replaced by `BuildConfig.MG_URL` per buildType.

---

## 13. Cross-references

- The variant module shape this nests inside: [07 — `:variants-*`](07-variants.md)
- The `:core` contracts layer where `TenantContext` / `TenantFlags` / `TenantParams` live: [03 — `:core`](03-core.md)
- The login boot phase that resolves the tenant: [10 — Boot Phases](10-boot-phases.md)
- The `LoggedInComponent` that holds both `VariantContext` and `TenantContext`: [10 — Boot Phases](10-boot-phases.md)
- The `Session.activeAccountId` (third axis, see §1): [12 — Departments and Session](12-departments-and-session.md)
- Onboarding a brand-new region: [13 — Onboarding a Variant](13-onboarding-a-variant.md)
- The store-review-mode mechanism (NOT a tenant): [11 — MG and Runtime Config § 6.5](11-mg-and-runtime-config.md)
