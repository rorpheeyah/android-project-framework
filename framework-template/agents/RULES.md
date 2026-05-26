# Compass Framework — Agent Rules

> **Version:** 1.0 (aligned with framework spec at `docs/` as of 2026-05)
> **Audience:** any AI coding agent (Claude Code, Cursor, Copilot, Cline, Aider, etc.) working in a repository that consumes this framework.
> **Source of truth:** [`docs/`](../../docs/) — this file is a distillation, not a replacement.
>
> **How to read this:** the rules below are normative. Where prose narrative is needed, follow the cross-reference to `docs/`. If a rule and a doc disagree, the doc wins; flag the divergence to the human.

---

## 0. First-Contact Checklist for Any Agent

Before writing or changing any code in a Compass project, an agent **must**:

1. Read `docs/00-overview.md` and `docs/01-module-topology.md`. These two files alone establish 80% of the architecture's load-bearing rules.
2. Read this `RULES.md` end-to-end.
3. Identify the module the change is being made in (the Gradle path: `:aos-sdk`, `:core`, `:design-system`, `:data`, `:features`, `:features-{name}`, `:tenants:{region}:base`, `:tenants:{region}:{tenantSlug}`, or `:app`).
4. Cross-check the proposed change against §3 (forbidden imports) and §4 (per-module rules) **before** writing the diff.

If the requested change cannot be made without violating a rule, say so and propose an alternative. Do not silently bend a rule.

---

## 1. The Architecture in One Diagram

```
infrastructure   :aos-sdk          (submodule · banking-agnostic · git-tag-released · reusable across products)
                       ↑
contracts /     :core              :design-system
foundations    (interfaces,        (theme + components,
                models, MVI)        Compose primitives)
                       ↑                ↑
implementations :data ·                 :features ·
& UI            :tenants:{region}:base· :features-chatbot ·
                :tenants:{region}:{tn}· :features-{feature-name}
                       ↑
orchestrator     :app              (depends on everything; runs the boot sequence)
```

Arrow `↑` reads as "depends on". `:core` and `:design-system` are sibling foundations. Implementation/UI siblings never depend on each other, **except** that a concrete tenant module depends on its own region base.

---

## 2. Hard Invariants (Twelve Rules That Must Not Be Broken)

Each is a compile-time or convention-time promise. Violating any is a regression — push back even if asked.

1. **No conditional logic on tenant ID anywhere.** `if (tenant.id == "...")` or `when (tenant.id) { ... }` is forbidden everywhere except `:app/di/TenantResolverModule.kt`. Same for `tenant.regionCode`. Use polymorphism via `:core` interfaces with `@TenantKey` multibinding instead. Reading `tenant.flags.*`, `tenant.params.*`, `tenant.displayName`, `tenant.defaultCurrency` is fine — that's data, not dispatch.
2. **`:features` is Logic-Blind.** ViewModels depend on `:core` interfaces only. They never name a concrete `:data` repo or a `:tenants:*:*` policy class. Onboarding a new tenant must require **zero** edits to `:features`, `:features-chatbot`, or `:features-{name}`.
3. **`:aos-sdk` is banking-agnostic.** Any term like `Account`, `Money`, `Transfer`, `Loan`, `Tenant`, or a tenant id appearing inside `:aos-sdk` is misplaced. The SDK is consumed by multiple Android products via git tags; banking terminology there would force every consumer to be a bank.
4. **One primary backend, no per-tenant duplication.** One Retrofit interface family (`Fintech*Api`) split per feature area. One set of DTOs. One repository implementation per `:core` interface. Tenant routing is the server's job (per-user). Third-party integrations get typed clients in `:data/external/` but never branch by tenant.
5. **Tenant modules contain only policies + DI.** No UI, no networking, no DTOs, no repositories. Region-base modules have the same constraint. Tenant-unique features (with their own API or screens) go in `:features-{name}` sibling modules gated by `TenantCapabilities`.
6. **`:design-system` is tenant-agnostic and domain-agnostic.** No banking types, no `:core` dependency. Theme tokens + primitive Composables only. User-facing strings live in `strings.xml`, not in tenant policy classes.
7. **The tenant binds once at login and stays.** `LoggedInComponent` is built once with the user's `TenantContext` and dropped on logout. No runtime swap, no purge sequence. Tenant change in production = logout-then-login.
8. **Only one URL is hardcoded: MG.** The Mobile Gateway URL per build environment is the *only* network config baked into the binary. Main API URLs, third-party SDK app-ids, maintenance state, and version floors all come from MG's `RuntimeConfig`. The one narrow exception is `staleConfigTtl` — a single BuildConfig constant (default 24h) for the MG-down fallback window.
9. **Departments are accounts, not sub-tenants.** Multiple `DepartmentAccount`s under one user share the same `LoggedInComponent`. The active account is `Session.activeAccountId` (a `StateFlow`), stamped on requests by `AccountIdInterceptor`. Don't model them as nested DI graphs.
10. **`:features` is a Hybrid-Monolith on purpose.** Feature boundaries are **packages**, not Gradle modules. New flow = new package under `:features/`. Exceptions: heavy-SDK features (chatbot, KYC, support chat, branch locator) and tenant-locked features become sibling `:features-{name}` modules.
11. **Onboarding a new tenant is strictly additive.** New `:tenants:{region}:{tenantSlug}` module + one `settings.gradle.kts` include + one `TenantCatalogue` entry. If a tenant-onboarding diff touches `:aos-sdk`, `:core`, `:data`, `:design-system`, `:features`, sibling tenants, or other region bases — the architecture has been violated.
12. **Region is a Gradle module hierarchy, not a DI axis.** Per-region differences live in `:tenants:{region}:base`. Per-organization differences live in `:tenants:{region}:{tenantSlug}` via `TenantContext`. There are **no** `VariantContext` / `VariantId` / `VariantKey` / `:variants-*` types — those were removed in the variant collapse. Reintroducing them is forbidden.

---

## 3. Forbidden Imports (Compile-Time Rules)

If you find yourself about to add any of these dependencies, stop. The framework's whole point is to make these illegal.

| From | To | Why forbidden |
|---|---|---|
| `:features` / `:features-chatbot` / `:features-{n}` | `:data` | Breaks Logic-Blind. ViewModels use `:core` interfaces only. |
| `:features` / `:features-chatbot` / `:features-{n}` | `:tenants:*:*` | Breaks Logic-Blind. UI must not know which tenant it is. |
| `:features` ↔ `:features-chatbot` ↔ `:features-{n}` | each other | Sibling UI modules — no cross-references. Shared primitives go in `:design-system`. |
| `:tenants:{regionA}:{tenantX}` | `:tenants:{regionA}:{tenantY}` | No cross-tenant coupling. |
| `:tenants:{regionA}:*` | `:tenants:{regionB}:*` | No cross-region coupling at any level. Promote to `:core` if truly shared. |
| `:tenants:{region}:base` | its own concrete tenants | Would create a cycle. |
| `:tenants:*:*` | `:data` | Tenant modules are pure policy + DI; no networking. |
| `:tenants:*:*` | `:design-system` | Tenant modules contain no UI. |
| `:data` | `:tenants:*:*` | Data layer is tenant-agnostic. |
| `:data` | `:design-system` | Data layer is headless. |
| `:design-system` | `:core` | UI primitives are domain-agnostic. |
| `:design-system` | `:data` / `:features` / `:tenants:*:*` | UI primitives are tenant- and impl-agnostic. |
| `:core` | `:data` / `:design-system` / `:features` / `:tenants:*:*` | Contract layer is upstream, never a consumer. |
| `:aos-sdk` | anything else in the project | SDK must remain product-agnostic; reusable across multiple Android products via git tag. |

**Mandatory edge** (the opposite of forbidden — must exist):
- Every `:tenants:{region}:{tenantSlug}` **must declare** Gradle dependency on `:tenants:{region}:base`. Forgetting this means Hilt resolution of regional baseline policies fails at boot.

---

## 4. Per-Module Rules

### 4.1 `:aos-sdk` — infrastructure

**Belongs:** network primitives, security, encrypted storage, camera, ML, imaging, push, deeplinks, WebView, PDF, locale, background work, encrypted DB, permissions, logging. All banking-agnostic.

**Does NOT belong:** any banking term (`Account`, `Money`, `Loan`, `Transfer`), any tenant id, any business policy, any UI built on `:design-system`, any DTO that references a domain type.

**Naming:** types use `Aos*` prefix (e.g., `AosWebView`, `AosCameraView`). Package root is `com.<your-org>.aos.sdk.*`.

**Public surface:** intentionally narrow. Kotlin `internal` is the default; only the named primitives are public.

**Distribution:** consumed via git submodule pinned to a tag, or via Maven artifact. Never edited from a consuming project's PR.

### 4.2 `:core` — contracts & foundations

**Belongs:** `TenantContext` / `TenantId` / `TenantFlags` / `TenantParams` / `TenantCapabilities`, `RuntimeConfig` / `ApiUrls` / `MaintenanceState` / `ForceUpdate`, `Session` / `DepartmentAccount` / `AccountId`, `UiState` / `UiEvent` / `UiEffect` / `MviViewModel` base, `@LoggedInScoped` / `@TenantKey`, domain interfaces (`*Repository`), domain policies (`*Policy`), domain models (`Money`, `Currency`, plus product-specific value objects).

**Does NOT belong:** implementations, Retrofit interfaces, DTOs, Compose code, Hilt `@Module`s that bind impls, anything that imports Android frameworks beyond minimal coroutines/Flow.

**Naming:** interfaces named for the contract (e.g., `AuthRepository`, `OtpDeliveryPolicy`), not for any implementor.

**Visibility:** all types are public — `:core` is a contract layer for downstream consumers.

### 4.3 `:design-system` — theme & primitives

**Belongs:** `<YourBrand>Theme`, color tokens, typography, spacing, motion specs, primitive Composables (`<YourBrand>Button`, `<YourBrand>TextField`, `<YourBrand>Dialog`, …), bundled font assets, Compose extension helpers.

**Does NOT belong:** screen-level Composables, ViewModels, any reference to `:core` types, any tenant-specific styling, any banking term.

**Naming:** components use the consuming project's `<YourBrand>*` prefix (framework docs use `Compass*` as a placeholder). Package root is `com.<your-org>.design.*`.

**Visibility:** intentionally public — these are the building blocks for `:features`.

### 4.4 `:data` — Retrofit + repositories

**Belongs:** `Fintech*Api` family (split per feature area: `FintechAuthApi`, `Fintech<Area>Api`), per-area DTO packages, repository implementations (one per `:core` interface) named `Fintech<Area>Repo` or `<Area>Repo`, mapper functions DTO ↔ domain model, `:data/external/` for third-party clients (credit bureau, KYC vendors, statement analyzers), one `DataModule` Hilt module binding repos.

**Does NOT belong:** UI, tenant-specific branching, per-tenant repo classes, per-tenant DTOs, hardcoded URLs (base URLs come from `RuntimeConfig`, stamped by interceptors).

**Naming:** `Fintech*Api` for Retrofit interfaces, `Fintech<Area>Repo` / `<Area>Repo` for implementations. Internal Kotlin visibility default; only the `DataModule` is public.

### 4.5 `:features` — Logic-Blind UI engine

**Belongs:** screen-level Composables, ViewModels, navigation graphs, per-feature packages (`auth/`, `dashboard/`, `transfer/`, …). MVI suffix convention: `*Screen.kt`, `*ViewModel.kt`, `*Contract.kt`.

**Does NOT belong:** anything in §3 forbidden-imports table. Specifically: no `:data` import, no `:tenants:*:*` import, no concrete repo or policy class references, no `if (tenant.id == ...)`.

**Reading `TenantContext`:** allowed — `tenant.flags.*` for feature gating, `tenant.params.*` for typed values, `tenant.displayName` / `tenant.defaultCurrency` for display. Never `tenant.id` or `tenant.regionCode` for dispatch.

### 4.6 `:features-{name}` — heavy-SDK or tenant-locked feature modules

**Belongs:** features that justify their own Gradle module — typically because they pull a heavy SDK (chatbot vendor, KYC vendor, mapping) or have a tenant-unique API surface (own DTOs, own screens, gated by `TenantCapabilities`).

**Rules:** same Logic-Blind rules as `:features`. Plus: no cross-references between sibling `:features-{name}` modules; shared primitives go in `:design-system`.

**When to create a new `:features-{name}` module instead of a new package in `:features`:**
- The feature pulls a 10MB+ SDK.
- The feature has its own server API (own DTOs, own Retrofit interface).
- The feature is gated by a `TenantCapability` and should not link into the binary when no shipping tenant has it.

### 4.7 `:tenants:{region}:base` — regional baseline

**Belongs:** regulator/region-wide policy classes (default `OtpDeliveryPolicy`, `ComplianceThresholds`, `BusinessCalendar`, `AmountFormatter` for the regional currency), region-level capability defaults.

**Does NOT belong:** Hilt `@Module` that binds anything. Region-base modules **provide classes only**; concrete tenants bind them.

**Why:** Hilt cannot have multiple modules binding the same `@TenantKey` at the same level. The concrete-tenant rebind-everything pattern keeps the dispatch graph unambiguous.

### 4.8 `:tenants:{region}:{tenantSlug}` — concrete tenant

**Belongs:** `TenantContext` factory (the `TenantFlags` + `TenantParams` + `displayName` + `regionCode` for this tenant), per-tenant policy overrides where the region baseline doesn't apply, support-contact data, `TenantCapabilities` declaration, one Hilt `@Module` that binds the full policy set under `@TenantKey("<region>:<tenantSlug>")`.

**Does NOT belong:** UI, networking, DTOs, repositories, hardcoded business strings (those go in `strings.xml`), any reference to a sibling tenant.

**The concrete-rebinds-everything pattern:** this module declares the full `@TenantKey @Binds` set, reusing region-base classes where regional baseline applies, overriding where it doesn't. Do not try to "inherit only what's different" — Hilt won't resolve a partial map.

**Mandatory:** Gradle dependency on `:tenants:{region}:base`.

### 4.9 `:app` — orchestrator

**Belongs:** `<YourBrand>Application` (`@HiltAndroidApp`), `MainActivity`, `AppNavigation`, `BootCoordinator` + boot pipeline, `LoggedInComponent` + `LoggedInComponentManager` + `LoggedInEntryPoint`, `TenantResolverModule` (**the single point of tenant-id dispatch**), `TenantCatalogue`, `SessionFactory`, `AccountIdInterceptor`, `LogoutHandler`.

**Special privilege:** `:app/di/TenantResolverModule.kt` is the **only** place `when (tenant.id) { ... }` is allowed. It maps a `TenantId` to the right `@TenantKey`-tagged provider. No other code branches on tenant id.

**The orchestrator owns the boot sequence:** MG fetch → maintenance/force-update gate → login → `SessionFactory.build(loginResponse) → LoggedInComponent` → navigation to dashboard. See `docs/10-boot-phases.md`.

---

## 5. Tenant Dispatch — How To Do It Right

The single most common violation in any multi-tenant codebase is the "just one conditional" creep: `if (tenant.id == "x") doX() else doY()`. The framework's architecture is designed to make this both **unnecessary** and **impossible-to-merge**.

### 5.1 The three legal mechanisms

| Mechanism | When to use | Where it lives |
|---|---|---|
| **`TenantFlags`** (named booleans) | A feature toggle. UI should show or hide something; a flow should skip a step. | `tenant.flags.hidesEmployeeId` is read directly in `:features` ViewModels and Composables. |
| **`TenantParams`** (named typed fields) | A value that differs per tenant: a maximum amount, a contact phone, a logo URL. | `tenant.params.maxTransferAmount` is read directly in `:features`. |
| **`TenantPolicy` interface** (structural escalation) | The *behavior* differs, not just a value: a different OTP channel, a different compliance check, a different currency format. | A `:core/policy/` interface with per-tenant `@TenantKey @Binds` implementations in each `:tenants:{region}:{tenantSlug}/policy/`. |

**Decision rule:** start with a flag or param. Escalate to a policy interface only when the difference is behavior, not a value. Most differences are values.

### 5.2 The multibinding contract

For a structural policy (a `:core` interface), the binding pattern is **always**:

```kotlin
// In :tenants:{region}:{tenantSlug}/di/<Tenant><Region>TenantModule.kt

@Module
@InstallIn(LoggedInComponent::class)
internal interface <Tenant><Region>TenantModule {

    @Binds
    @IntoMap
    @TenantKey("<region>:<tenantSlug>")
    @LoggedInScoped
    fun bindOtpDelivery(impl: <Region>OtpDeliveryPolicy): OtpDeliveryPolicy

    // ... one @Binds line per :core policy interface, every tenant declares the full set
}
```

The runtime resolver in `:app/di/TenantResolverModule.kt` looks up the active `TenantId` in the `Map<TenantId, OtpDeliveryPolicy>` and provides the matching impl into `LoggedInComponent`.

### 5.3 Forbidden patterns

```kotlin
// ❌ Never — dispatch on tenant.id outside TenantResolverModule
if (tenant.id == TenantId("cambodia:nh")) {
    fancyNhFlow()
} else {
    defaultFlow()
}

// ❌ Never — string-typed config registry that bypasses Hilt
val tenantConfig: Map<String, Any> = mapOf("nh" to ..., "partner-a" to ...)

// ❌ Never — region branching
if (tenant.regionCode == "cambodia") { ... }

// ❌ Never — per-tenant Retrofit interface or per-tenant DTO
interface NhBankAuthApi : FintechAuthApi { ... }
```

```kotlin
// ✅ Always — read flag/param values
if (tenant.flags.hidesEmployeeId) hideEmployeeIdField()
val limit = tenant.params.maxTransferAmount

// ✅ Always — structural via injected policy
class TransferViewModel @Inject constructor(
    private val otpDelivery: OtpDeliveryPolicy,    // bound per-tenant by Hilt
) : MviViewModel<...>()
```

---

## 6. MVI Conventions

Every screen has exactly three sibling Kotlin files:

```
<feature-package>/<screen>/
├── <Screen>Screen.kt        # @Composable, stateless w.r.t. domain — collects state, emits events
├── <Screen>ViewModel.kt     # extends MviViewModel<UiState, UiEvent, UiEffect>
└── <Screen>Contract.kt      # contains the sealed UiState / UiEvent / UiEffect for this screen
```

### 6.1 The contract types

```kotlin
// <Screen>Contract.kt
data class <Screen>UiState(...)   // immutable; everything the screen renders comes from here

sealed interface <Screen>UiEvent {      // user-driven inputs
    data object Submit : <Screen>UiEvent
    data class FieldChanged(val text: String) : <Screen>UiEvent
}

sealed interface <Screen>UiEffect {     // one-shot side effects (navigation, toast, system action)
    data object NavigateNext : <Screen>UiEffect
    data class ShowError(val message: String) : <Screen>UiEffect
}
```

### 6.2 The ViewModel template

```kotlin
@HiltViewModel
class <Screen>ViewModel @Inject constructor(
    private val someRepository: SomeRepository,       // :core interface, never a concrete impl
    private val somePolicy: SomePolicy,               // :core policy, bound per-tenant elsewhere
) : MviViewModel<<Screen>UiState, <Screen>UiEvent, <Screen>UiEffect>(
    initialState = <Screen>UiState(...)
) {
    override fun handleEvent(event: <Screen>UiEvent) {
        when (event) {
            is <Screen>UiEvent.Submit -> submit()
            ...
        }
    }
}
```

### 6.3 Hard MVI rules

- The ViewModel **never** holds Composable state. `UiState` is the single source of truth.
- The ViewModel **never** imports an Android UI type (`Context`, `Activity`, `View`, `Compose*`). It depends on `:core` only.
- One-shot actions (navigation, toast) are `UiEffect`s, not state mutations.
- The Composable reads state via `viewModel.state.collectAsStateWithLifecycle()` and dispatches events via `viewModel.onEvent(...)`.
- `UiEffect`s are consumed in a `LaunchedEffect(Unit) { viewModel.effects.collect { ... } }` block.

See `docs/09-mvi-pattern.md` for the full pattern.

---

## 7. Naming Rules

### 7.1 Keep verbatim across every project

Module names: `:aos-sdk`, `:core`, `:design-system`, `:data`, `:features`, `:features-*`, `:tenants:{region}:{tenantSlug}`, `:app`.

Framework type names: `TenantContext`, `TenantId`, `TenantFlags`, `TenantParams`, `TenantCapabilities`, `TenantKey`, `TenantCatalogue`, `TenantContextResolver`, `TenantResolverModule`, `Session`, `UserSession`, `DepartmentAccount`, `AccountId`, `AccountIdInterceptor`, `MgClient`, `RuntimeConfig`, `MaintenanceGate`, `ForceUpdateGate`, `BootCoordinator`, `BootResult`, `StaleConfigFallback`, `LoggedInComponent`, `LoggedInComponentManager`, `LoggedInEntryPoint`, `LoggedInBindingsModule`, `@LoggedInScoped`.

Framework concept names: `Aos*` for primitives inside `:aos-sdk` (`AosWebView`, `AosCameraView`, etc.).

### 7.2 Parameterize per project

Use the placeholders consistently:

| Placeholder | What you substitute | Example |
|---|---|---|
| `<your-project>` | git repo name | `acme-customer-app` |
| `<your-org-domain>` | reverse-DNS package root | `com.acme` |
| `<your-org-prefix>` | 2–4 letter brand prefix | `Acme` |
| `<region>` | regulator/country slug in `:tenants:{region}` | `cambodia`, `vietnam`, `korea` |
| `<tenant-slug>` | organization slug | `nh`, `partner-a` |
| `<your-domain>` | the business capability | `loan`, `policy`, `payment` |

These appear in `:app/<YourOrgPrefix>Application` and `:design-system/<YourOrgPrefix>Theme` + components.

### 7.3 Banned terms — do not reintroduce

The variant axis was collapsed into the tenant axis. These names are gone and must not come back:

- `variant`, `Variant*`, `:variants-*`, `@VariantKey`, `VariantContext`, `VariantId`, `VariantCapabilities`, `VariantFlags`, `VariantParams`.

If a doc edit, prompt, or PR description uses any of them, flag it and translate to `tenant` / `Tenant*` / `:tenants:{region}:{tenantSlug}` / `@TenantKey` / `TenantContext` / `TenantId` / `TenantCapabilities` / `TenantFlags` / `TenantParams`.

### 7.4 Composite tenant id

`TenantId.value` is a composite `<region>:<tenantSlug>` string (e.g., `"cambodia:nh"`). Never split, never compose by hand. `@TenantKey("<region>:<tenantSlug>")` uses the same composite. No bare slug, no `<region>/<tenantSlug>`, no `<region>_<tenantSlug>`.

---

## 8. Configuration & URL Rules

1. **The only hardcoded URL is `BuildConfig.MG_URL` per build type** (production, staging, dev). Add it via `buildConfigField` in `:app/build.gradle.kts`.
2. **`BuildConfig.STALE_CONFIG_TTL_MS`** is the one exception — the duration of the MG-down fallback window. Default 24 hours.
3. **Everything else** comes from MG's `RuntimeConfig`: primary API base URL, third-party SDK app ids (Sendbird, Google Maps, etc.), maintenance state, minimum supported version, `webRoutes` allowlist for WebView, feature flags that are not tenant-specific.
4. **Do not** create a `production.json` of base URLs, an `api-config.json`, an `app-config.xml`, or anything similar that bakes URLs into the binary.
5. **Tenant-specific values** (per-tenant flags, per-tenant params, per-tenant policies) do not come from MG. They come from `TenantContext` resolved from the login response. MG is environment config; `TenantContext` is per-user identity.

---

## 9. Boot Pipeline Rules

```
App start
  → MgClient.fetchConfig()
      ↳ success → RuntimeConfig in memory + cache
      ↳ failure within staleConfigTtl → use cached RuntimeConfig + show stale-config banner
      ↳ failure outside staleConfigTtl → maintenance/service-unavailable screen
  → MaintenanceGate.check(runtimeConfig)
      ↳ down → maintenance screen
  → ForceUpdateGate.check(runtimeConfig, BuildConfig.VERSION_CODE)
      ↳ below floor → force-update screen
  → AuthRepository.currentSession() (from SecureStorage)
      ↳ null → Login screen
      ↳ valid → BootCoordinator.onSessionRestored(...)
  → SessionFactory.build(loginResponse) → TenantContext + Session + DepartmentAccounts
  → LoggedInComponentManager.activate(...) → LoggedInComponent constructed
  → Navigate to dashboard
```

Rules:
- `LoggedInComponent` is built **exactly once per login** with that user's `TenantContext`.
- On logout: `LoggedInComponentManager.deactivate()` drops the component. Anything `@LoggedInScoped` is GC'd. Caches/DBs scoped to the user are wiped.
- A tenant change in production = logout + login. No live tenant swap.
- See `docs/10-boot-phases.md` for the full pipeline + `docs/11-mg-and-runtime-config.md` for MG.

---

## 10. Common Red Flags (Push Back On These)

When a user prompt suggests any of these, the agent should push back, not comply:

| Red flag prompt | What it implies | Correct response |
|---|---|---|
| *"Just add a quick if-statement for tenant X"* | Tenant-id dispatch outside `TenantResolverModule`. | Propose `TenantFlags` / `TenantParams` if it's a value, or a `:core` policy interface if it's behavior. |
| *"Create a new Retrofit interface for tenant Y's API"* | Per-tenant API surface. | One server demuxes; one `Fintech*Api`. If tenant has a truly unique 3rd-party API, that goes in `:features-{name}` with its own `api/` package. |
| *"Hardcode the production URL in BuildConfig"* | Bypassing MG. | MG provides URLs at runtime. Only `MG_URL` and `STALE_CONFIG_TTL_MS` are hardcoded. |
| *"Add an `if (Locale.getDefault() == ...)` for translation"* | Strings in code. | Translated strings live in `strings.xml`. Use `stringResource(R.string.…)`. |
| *"Put the chat SDK calls in `:features`"* | Heavy SDK in the monolith. | Heavy-SDK features get their own `:features-{name}` module. |
| *"Add a banking type (`Money`, `Account`) to `:aos-sdk`"* | Domain leak into the SDK. | Banking types live in `:core` (contracts) or `:data` (DTOs). SDK stays banking-agnostic. |
| *"Inherit `:tenants:{region}:partnerA` from `:tenants:{region}:partnerB`"* | Cross-tenant coupling. | Shared regional behavior goes in `:tenants:{region}:base`. Tenant siblings never depend on each other. |
| *"Put the user's auth token in `EncryptedDatabase`"* | Token in the wrong store. | Auth tokens live in `EncryptedPrefs`. Room+SQLCipher is for chat history, drafts, branch cache, notification inbox — not auth state. |
| *"Add `VariantContext` for the new region"* | Reintroducing the collapsed axis. | Use the region-base hierarchy: `:tenants:{newRegion}:base` + `:tenants:{newRegion}:default` + at least one concrete tenant. |
| *"Just put this string in the tenant policy class"* | Display string in code. | Strings go in `strings.xml`. Tenant policy classes carry behavior or typed values, not user-facing copy. |

---

## 11. When To Ask vs. Proceed

This framework intentionally has sharp edges. Some decisions are reversible (file naming, internal helper extraction); some are not (module shape, `:aos-sdk` API surface, `TenantContext` schema).

| Situation | Action |
|---|---|
| User asks for a new feature inside `:features` | Proceed; add a package, write MVI files. |
| User asks for a new feature that needs its own server API or DTOs | **Ask** whether to add it to `:features` (package) or `:features-{name}` (sibling module). Default: package unless heavy-SDK or tenant-locked. |
| User asks for a new tenant onboarding | Follow `docs/13-onboarding-a-variant.md` (or its post-rename version). New module + include + catalogue entry only. |
| User asks for a new region | Follow the same checklist plus create the region-base + region-default. |
| User asks for a change to `:aos-sdk` | **Ask before editing.** The SDK is git-tag-released and consumed by multiple products. Confirm the change is product-agnostic. |
| User asks for a change to `:core` types | **Ask before editing** if it changes the public shape of a `TenantContext`-family type or a `*Repository` interface. Other `:core` additions are routine. |
| User asks for a change that would break a forbidden-import rule | **Refuse** and propose an alternative. Don't bend the rule. |
| User asks for a change that would reintroduce variant terminology | **Refuse** and translate to tenant terminology. |

---

## 12. Verifying Your Own Work

Before declaring a change done, an agent should:

1. **Re-read the diff** and check it against §3 (forbidden imports) — every new `import` line.
2. **Search for `tenant.id ==` and `when (tenant.id)`** in the changed files — these are only allowed in `:app/di/TenantResolverModule.kt`.
3. **Search for `BuildConfig.` URL references** — only `MG_URL` and `STALE_CONFIG_TTL_MS` are valid hardcoded values.
4. **Search for banking terms in `:aos-sdk` changes** — `Account`, `Money`, `Loan`, `Transfer`, `Tenant` are red flags inside the SDK.
5. **Search for variant terminology** — `Variant`, `variants`, `VariantContext`, etc. — anywhere in changed text or code.
6. **Confirm a new `:tenants:{region}:{tenantSlug}`** declares Gradle dependency on `:tenants:{region}:base`.
7. **Confirm a new `*ViewModel` in `:features`** imports only from `:core` (and `:design-system` for UI primitives, in the `*Screen.kt`).
8. **Confirm `strings.xml`** holds any new user-facing copy, not Kotlin string literals.

If any check fails, fix before reporting done.

---

## 13. Quick Cross-Reference

| Looking for | Doc |
|---|---|
| Why this architecture exists | `docs/00-overview.md` |
| The dependency DAG (this file's §1, §3) | `docs/01-module-topology.md` |
| `:aos-sdk` capability list | `docs/02-aos-core.md` (filename pending rename to `02-aos-sdk.md`) |
| `:core` types catalog | `docs/03-core.md` |
| Design-system rules | `docs/04-design-system.md` |
| `:data` Retrofit + repo pattern | `docs/05-data.md` |
| Hybrid-Monolith justification | `docs/06-features.md`, `docs/14-build-performance.md` |
| Tenant model deep dive | `docs/19-tenants-and-variants.md` (filename pending rename) |
| Onboarding a new tenant | `docs/13-onboarding-a-variant.md` (filename pending rename) |
| MVI conventions | `docs/09-mvi-pattern.md` |
| Boot pipeline | `docs/10-boot-phases.md` |
| MG + `RuntimeConfig` | `docs/11-mg-and-runtime-config.md` |
| Glossary | `docs/16-glossary.md` |

---

## Appendix A — One-Paragraph TL;DR

A Compass project has a strict dependency DAG with three foundation tiers (`:aos-sdk` infrastructure, `:core`+`:design-system` contracts, then implementation/UI siblings) and an orchestrator (`:app`). The framework's signature is **single-axis tenant dispatch** via Hilt `@TenantKey` multibinding, with `:features` kept **Logic-Blind** so onboarding a new tenant is strictly additive (a new module + a settings include + a catalogue entry, nothing else). The Mobile Gateway (MG) is the only URL hardcoded into the binary; everything else comes from `RuntimeConfig`. Banking terms never appear in `:aos-sdk`. Variant terminology was removed and must not return.
