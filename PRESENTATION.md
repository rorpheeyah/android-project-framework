# Bizplay IPPP — Architecture Brief

> 10-min read · audience: eng lead + team · authoritative spec in `docs/`

---

## The problem

```
   if (DetailConfig.isNIA())             { … }       ← scattered across every flow
   else if (DetailConfig.isPOSCO_ICT())  { … }       ← scattered across every flow
   else if (DetailConfig.isLotte())      { … }       ← scattered across every flow
   else if (DetailConfig.isWIPS())       { … }       ← scattered across every flow
   else if (DetailConfig.isShinsegae())  { … }
   else if (DetailConfig.isITCen())      { … }
   else if (DetailConfig.isHANA())       { … }
   else { /* IBS, SPC, Chilsung, … */ }
```

~124 call sites in the existing `Bizplay4.0_IPPP` codebase.
Regression risk = **O(tenants × features)**.

Three approaches we rejected:

| Approach | Cost |
|---|---|
| `if (DetailConfig.isXxx())` chains (today's shape) | One PR touches every flow |
| One APK per corporate customer | Cherry-picks across N branches; CI/store-listing matrix |
| Runtime DI hot-swap | Engineering effort on a flow users never take |

---

## The solution: a layered DAG

```
                   :aos-core                                 ← infrastructure
                        ↑                                      (HTTP, security:
                        │                                       SQLCipher / TransKey /
                        │                                       mVaccine / Secucen,
                        │                                       BizWebView, storage, logs)
        ┌───────────────┴───────────────┐
      :core                       :design-system            ← contracts +
        ↑   ↑   ↑                       ↑   ↑                 UI primitives (BizTheme,
        │   │   │                       │   │                  BizButton, BizWebView, …)
      :data │  :variants-{id}      :features  :features-scanner :features-hipass
            │       ↑                   ↑           ↑                 ↑
            └───────┴───────────┬───────┴───────────┴─────────────────┘
                                ↓
                              :app                          ← orchestrator
```

8 module shapes. Depend upward, never sideways. Forbidden imports enforced at compile time.

---

## Four promises

| Promise | Mechanism |
|---|---|
| **Server-side demux** | One IPPP backend handles per-user, per-`USE_INTT_ID`, per-`COMPANY_CD` routing. No `KrKakaoPayApi` on the client. |
| **Login-once binding** | `LoggedInComponent` built at login with variant + tenant, dropped at logout. No runtime swap. |
| **MgGate bootstrap** | Only the MgGate URL is hardcoded (today's `Conf.SITE_MG_URL + "/MgGate"`). Real backend URLs fetched from MgGate at cold start. |
| **Additive variant + tenant onboarding** | New variant = new module + 2 lines. New tenant = 1–3 files inside its variant. Zero change to `:features` / `:data`. |

---

## Two axes, three layers

```
Variant ⊃ Tenant ⊃ DepartmentAccount

Variant       (region/regulator)   :variants-kr / :variants-kh / :variants-vn
              ↓ login-time bind, no runtime swap

Tenant        (corporate customer) POSCO ICT, Lotte, NIA, Shinsegae, ITCen,
                                   WIPS, HANA, IBS, SPC, default
              ↓ login-time bind, no runtime swap

Account       (institution)        USE_INTT_ID / COMPANY_CD (today's
                                   SelectUserInttIdActivity flow)
              ↓ session-level flip, just a header swap
```

→ Detail in [`docs/19-tenants-and-variants.md`](docs/19-tenants-and-variants.md).

---

## Variant snapshot: same interface, different content

```
                       KR (Korea)                 │  KH (Cambodia)
─────────────────────────────────────────────────────────────────────────────
ExpenseAmountPolicy:
  perReceiptLimit      1M KRW   (≈ $750)          │  4M KHR   (≈ $1,000)
  dailyLimit           5M KRW                     │  20M KHR

FeeCalculator (per-category reimbursement caps):
                       Meal 50k, Fuel 200k,       │  Flat full reimbursement
                       Entertainment 100k         │

VariantCapabilities:
  KakaoPayLink         ✓                          │  ✗
  HipassTracking       ✓                          │  ✗
  MyDataIntegration    ✓                          │  ✗
  BilingualReceipt     ✗ (Korean only)            │  ✓ (English + Khmer)

AmountFormatter:
                       "₩1,234,567"               │  "1,234,567៛"

SupportContacts:
                       +82 2 1588 9999            │  +855 23 999 999
                       Mon–Fri 9a–6p KST          │  Mon–Fri 8a–8p ICT
```

~10 small files per variant. Same `:core` interfaces. Different content.

→ Full code in [`docs/07-variants.md` § 5.9](docs/07-variants.md).

---

## Tenant snapshot: same variant, different content

Inside `:variants-kr`:

```
                  POSCO ICT          NIA              Shinsegae          default
─────────────────────────────────────────────────────────────────────────────────
hidesEmployeeId     false             true             false              false
clearsEmployeeId    false             false            false              false
requiresIdNumber    false             true             false              false
usesTripFlow        true              false            false              false
allowsPwdReset      false             false            false              false

ApprovalLineRenderer:
                    default           default          structural         default
                                                       (own layout)
```

Tenant = ~3 files inside the parent variant. Switching tenants is logout-login (same as variant).
Switching `USE_INTT_ID` inside a tenant = `Session.activeAccountId` flip, instantaneous.

→ Full mapping in [`docs/19-tenants-and-variants.md` § 9](docs/19-tenants-and-variants.md).

---

## Onboarding cost

### Adding `:variants-vn` (a new region)

| Touch | Lines |
|---|---|
| New `:variants-vn` module | 150–250 |
| `settings.gradle.kts` | +1 |
| `:app/variant/VariantCatalogue.kt` | +1 |
| **`:features`** | **0** |
| **`:data`** | **0** |
| **`:design-system`** | **0** |
| **Other variants** | **0** |
| **`:core`** | **0** (unless adding a new policy interface) |

### Adding a new tenant inside `:variants-kr` (e.g. a new Korean corporate customer)

| Touch | Lines |
|---|---|
| New `tenants/{id}/TenantProfile.kt` | 10–30 |
| `:app/variant/TenantCatalogue.kt` | +1 |
| **`:features`** | **0** |
| **`:data`** | **0** |
| **`:core`** | **0** (unless adding a new `TenantFlags` field) |

Compile-time enforced.

---

## Worked flow: a KR / POSCO_ICT user submits a receipt

```
  Submit tap
     ↓
  ReceiptDetailViewModel                       ← no use case in between
     amountPolicy.validate(amount)             ← KrExpenseAmountPolicy   from :variants-kr
     feeCalculator.reimbursableAmount(...)     ← KrFeeCalculator         from :variants-kr
     // tenant.flags.usesTripExpenseFlow already read at init → state.showTripFields
     repo.create(draft)
     ↓
  ReceiptRepository                            ← :core interface
     ↓ Hilt resolves to active impl
  IpppReceiptRepo                              ← :data
     POST /v1/receipt
         Authorization: Bearer …
         X-Use-Intt-Id: INTT-12345    ← POSCO ICT — Seoul HQ membership
         X-Company-Cd:  POSCO-ICT
     ↓
  [IPPP backend → server-side per-company-code routing]
```

UI didn't know KR. UI didn't know POSCO. Repo didn't know either. The server figured it out from auth + the institution headers.

---

## No `:domain` module · no use cases

```
   Common "Clean Architecture"            Bizplay framework
   ───────────────────────────            ─────────────────────────────
        :app                                  :app
         ↑                                     ↑
    :presentation     UI + VM             :features         UI + VM
         ↑                                     ↑
      :domain       use cases             :core          contracts + models
         ↑          + interfaces            ↑     ↑
       :data       repo impls          :data    :variants-*
                                       (impls)  (policies — variability)
```

`:core` already plays the "domain contracts" role. The variability that use cases would carry lives in `:variants-*/policy/`. Tenant variability lives in `TenantFlags` / `TenantParams`. The orchestration that use cases would perform happens in the ViewModel — typically 5–15 lines.

### Side-by-side: same flow, two designs

```kotlin
// ❌ With a use case layer
class SubmitReceiptUseCase @Inject constructor(
    private val policy: ExpenseAmountPolicy,
    private val repo:   ReceiptRepository,
) {
    suspend operator fun invoke(draft: ReceiptDraft): Result<Receipt> =
        when (val v = policy.validate(draft.amount)) {
            is ValidationResult.Invalid -> Result.failure(ValidationException(v.reason))
            ValidationResult.Valid      -> repo.create(draft)
        }
}

class ReceiptDetailViewModel @Inject constructor(
    private val submit: SubmitReceiptUseCase,            // hides policy + repo behind a wrapper
) : MviViewModel<…>() { … }
```

```kotlin
// ✅ Bizplay framework — VM composes policy + repo directly
class ReceiptDetailViewModel @Inject constructor(
    private val policy: ExpenseAmountPolicy,
    private val repo:   ReceiptRepository,
) : MviViewModel<…>() {
    private fun onSubmit() = viewModelScope.launch {
        when (val v = policy.validate(state.value.amount)) {
            is ValidationResult.Invalid -> setState { copy(error = v.reason) }
            ValidationResult.Valid      -> repo.create(state.value.draft).fold(…)
        }
    }
}
```

### What a use case layer would buy us — and where we already get it

| Promised benefit | Bizplay framework already provides it via |
|---|---|
| Reusable business rules across screens | **Policies** — injectable wherever needed |
| Variant-specific behavior | **`:variants-{id}`** — one policy impl set per variant |
| Tenant-specific behavior | **`TenantFlags` / `TenantParams`** on `TenantContext` |
| Pure JVM-testable logic, no fixtures | **Policies** — no Android, no Hilt |
| ViewModel kept thin | Rules live in policies; VM only orchestrates |
| UI insulated from impls | **`:core` interfaces** — `:features` never names a `:data` or `:variants-*` class |
| I/O decoupled from rules | **Repository** (I/O) vs **Policy** (rules) — already the split |

### Rule of thumb — where new behavior lands

| Variability lives in… | Goes in… |
|---|---|
| Region-level rule (limit, fee, format, regex) | **`:core/policy/`** interface → variant impl |
| Region-level capability toggle | **`VariantCapabilities`** → variant returns bool |
| Per-corporate-customer field visibility / format | **`TenantFlags` / `TenantParams`** → `TenantProfile` sets the values |
| Per-corporate-customer structural difference | **`:core/policy/`** interface → tenant impl in `:variants-{region}/tenants/{id}/` |
| I/O | **`:core/repository/`** interface → `:data` impl |
| Multi-step orchestration (validate → submit → emit) | **ViewModel** |

A `SubmitReceiptUseCase` would add a class to maintain, multiply by `N actions × M variants × K tenants` for anything axis-touching, and yield **zero new flexibility** (the variant + tenant strategy already substitutes behavior at login via Hilt multibindings — see [`docs/07-variants.md` § 6](docs/07-variants.md) and [`docs/19-tenants-and-variants.md` § 8](docs/19-tenants-and-variants.md)).

---

## Tradeoffs we accept

| Cost | Why we're OK with it |
|---|---|
| Slightly larger APK | Variants are ~10 files each, tenants ~3 each; multi-axis compile-in cost negligible |
| Convention-based feature boundaries inside `:features` | Lint enforces; build-perf wins justify |
| Variant or tenant change = logout-login | Runtime swap is costly machinery for a non-real flow; institution switch (the *real* flow) is a cheap header swap |
| Java → Kotlin migration over time | Existing Bizplay code stays compilable; new code is Kotlin + Compose + Hilt + MVI |

---

## FAQ

| Question | Answer |
|---|---|
| Can a user log in to different corporate accounts on the same device? | **Yes.** Logout drops the session graph (component, prefs, SQLCipher session scope, cache) and pops navigation to root. The next login rebuilds with whatever `variantId` + `tenantId` the server returns. No special code path. |
| What about per-tenant branding (POSCO red vs Lotte navy vs NIA orange)? | Future-roadmap. Branding is intentionally **not** in `RuntimeConfig`; per-tenant theming would be a separate `TenantBrandPolicy` mechanism that `:app` weaves into `BizTheme` composition locals. |
| What if a customer wants their own backend? | Doesn't happen — IPPP is one backend with server-side demux per `USE_INTT_ID` + `COMPANY_CD`. If a customer genuinely needs a *different* protocol, they're a new variant, not a new tenant. |
| What if a variant grows to need its own UI/API/screens? | Goes in a sibling module: `:features-{feature-name}` (e.g. `:features-hipass` for Korea-only highway tolls), gated by a `VariantCapabilities` flag. The variant module stays pure. |
| Where do use cases / interactors live? | **There are none.** ViewModel composes a `:core` policy + a `:core` repository directly. See "No `:domain` module · no use cases" above. |
| Why no separate `:domain` module? | `:core` already owns contracts + models. Variability lives in `:variants-*/policy/` and `TenantFlags`. Splitting it across two modules buys no new isolation. |
| What happens to the existing `DetailConfig.isXxx()` chain? | Deleted. Each predicate maps to a named `TenantFlags` field (e.g. `isNIA()` → `tenant.flags.hidesEmployeeId + requiresIdNumberCapture`), or — for structural differences like Shinsegae's approval line — a per-tenant impl of a `:core/policy/` interface. See [`docs/19-tenants-and-variants.md` § 9](docs/19-tenants-and-variants.md). |
| What happens to `BizWebview` and `BrowserBridge`? | The Java `BizWebview` becomes the Compose `BizWebView` in `:aos-core/webview/`. The `BrowserBridge` family with its `iWebAction` / `iWebActionBA` / `iwebaction` near-duplicates collapses to a single-method `WebActionBridge` with a versioned payload. URLs come from `RuntimeConfig.webRoutes`, not hardcoded loadUrl strings. See [`docs/18-webview-integration.md`](docs/18-webview-integration.md). |

---

## Status & sequence

Architecture spec: **complete** (20 docs in `docs/`). Implementation: **not started** in the new shape; the existing `Bizplay4.0_IPPP` project is the starting source material for the per-feature ports.

```
1. :aos-core          (formalise from today's SMART_LIB_STUDIO_CAMBODIA submodule;
                       add typed wrappers for SQLCipher, TransKey, mVaccine, Secucen,
                       and the BizWebView Composable)
2. :core              (interfaces, models, RuntimeConfig, Session,
                       VariantContext, TenantContext, TenantFlags, TenantParams)
3. :design-system     (BizTheme + first components: BizButton, BizTextField,
                       BizPasswordField wrapping TransKey, BizWebViewFrame)
4. :data              (Ippp*Api family + first repo: auth)
5. :variants-kr       (region policies + at least one tenant profile: posco_ict)
6. :app               (BootCoordinator + LoggedInComponent + VariantResolverModule
                       + TenantResolverModule)
7. End-to-end flow    (login → institution picker → receipt list → first receipt create)
8. Add more tenants   (NIA, Lotte, Shinsegae, …) — strictly additive, ~3 files each
9. :variants-kh       (canonical demo that the architecture's promise holds across
                       regions, not just inside Korea)
```

---

## Reference (deeper docs)

| Question | Doc |
|---|---|
| Why this shape? | [`docs/00-overview.md`](docs/00-overview.md) |
| Dependency rules? | [`docs/01-module-topology.md`](docs/01-module-topology.md) |
| Boot mechanism? | [`docs/10-boot-phases.md`](docs/10-boot-phases.md) |
| Onboarding a region? | [`docs/13-onboarding-a-variant.md`](docs/13-onboarding-a-variant.md) |
| Onboarding a tenant? | [`docs/19-tenants-and-variants.md`](docs/19-tenants-and-variants.md) |
| WebView discipline? | [`docs/18-webview-integration.md`](docs/18-webview-integration.md) |
| Full project tree? | [`docs/17-project-structure.md`](docs/17-project-structure.md) |
