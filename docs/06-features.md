# 06 · `:features` — UI Engine (Hybrid-Monolith)

> **Type:** Local Android library
> **Role:** Logic-Blind Compose UI organized by package
> **Constraint:** May import `:core`, `:design-system`, and `:aos-sdk`. May **never** import `:data` or `:tenants:*:*` (any tenant or region-base module).

---

## 1. Purpose

`:features` is the **UI engine** — the screens, ViewModels, navigation, and feature-flow orchestration that the user actually sees. It is deliberately **Logic-Blind**: every banking decision (how a fee is calculated, which validation rule applies, what API is called) is delegated to a `:core` interface that some other module implements.

This module is a **single Gradle module** with feature boundaries enforced by **packages**, not by sub-modules. We call this shape the **Hybrid-Monolith**.

The visual primitives (`CompassTheme`, `CompassButton`, `CompassTextField`, …) live in `:design-system`, not here. `:features` consumes them.

---

## 2. Why Hybrid-Monolith?

The textbook answer to "many features" is "one Gradle module per feature". For this project, that's the wrong tradeoff:

| Strict per-feature modules | Hybrid-Monolith (chosen) |
|---|---|
| Build graph: dozens of feature modules | One feature module, dozens of packages |
| Per-module Gradle config cost: high (~hundreds of ms each) | Paid once |
| Cross-feature refactor: edit N `build.gradle.kts` | Edit nothing — packages move freely |
| Build perf scales with: O(features × Gradle overhead) | O(features) — linear in source size |

The win is **build performance** + **refactoring fluidity**. The cost — and we accept it — is that **package-level discipline depends on convention** rather than the build system. Lint rules and code review enforce it.

> **When to break out into a separate module:** when a feature pulls in **heavy unique dependencies** that other features should not pay for, OR when a feature is tenant-locked with its own API + DTOs + screens. The chatbot, KYC, support-chat, and branch-locator modules are canonical examples for the first; `:features-bakong-disputes` is the canonical example for the second. See [07 — `:tenants:*` § "When the Tenant Has Unique Features"](07-variants.md).

---

## 3. Package Layout

```
:features/
└── src/main/kotlin/com/<org>/features/
    ├── boot/                  # Cold-start: MG fetch, MaintenanceGate, ForceUpdateGate
    │   ├── BootScreen.kt
    │   ├── BootViewModel.kt
    │   ├── BootContract.kt
    │   ├── MaintenanceGate.kt
    │   └── ForceUpdateGate.kt
    │
    ├── auth/                  # Login, registration, OTP, PIN, biometric prompt
    │   ├── login/
    │   │   ├── LoginScreen.kt
    │   │   ├── LoginViewModel.kt
    │   │   └── LoginContract.kt
    │   ├── pin/
    │   ├── otp/
    │   ├── biometric/
    │   └── AuthNavigator.kt
    │
    ├── dashboard/             # Multi-currency dashboard, loan summary, quick actions
    │   ├── DashboardScreen.kt
    │   ├── DashboardViewModel.kt
    │   └── DashboardContract.kt
    │
    ├── loan/                  # Product list/detail, apply (MWL & NON-MWL), my loan, repayment, calculator
    │   ├── product-list/
    │   ├── product-detail/
    │   ├── apply/
    │   │   ├── LoanApplyScreen.kt
    │   │   ├── LoanApplyViewModel.kt
    │   │   └── LoanApplyContract.kt
    │   ├── my-loan/
    │   ├── repayment/
    │   ├── payoff/
    │   ├── calculator/
    │   ├── LoanFlowState.kt
    │   └── LoanNavigator.kt
    │
    └── account/               # Account switcher (hidden when accounts.size == 1)
        ├── switcher/
        │   ├── AccountSwitcherSheet.kt
        │   ├── AccountSwitcherViewModel.kt
        │   └── AccountSwitcherContract.kt
        └── AccountNavigator.kt
```

Heavy-SDK flows live in sibling modules, not in `:features` — see [01 — Module Topology § 4.5](01-module-topology.md). Concretely: `:features-kyc` (CameraX + ML Kit), `:features-support-chat` (Sendbird), `:features-branch-locator` (Google Maps Compose), `:features-chatbot` (NLP/LLM).

Note the absence of a `common/` package. Theme and component primitives live in `:design-system`, depended on directly. Anything truly cross-cutting at the *feature* level (a multi-flow state holder, for example) can stay in a feature-local helper or be promoted to `:core/model/` if it carries domain meaning.

### Package-level conventions

- **One `*Contract.kt` per screen** — defines the screen's `UiState`, `UiEvent`, and `UiEffect` sealed types. Keeps MVI types co-located with the screen they belong to.
- **`internal` Kotlin visibility** for everything except: the Composable that `:app` navigates to, and the navigator entry function.
- **No nested `data` packages** — feature packages should hold UI + ViewModel only. Anything resembling a data source belongs in `:data` or `:core`.

---

## 4. Logic-Blind Constraint

A `:features` ViewModel **must not know** which tenant is active.

| Allowed in `:features` | Not allowed in `:features` |
|---|---|
| `class LoanApplyViewModel @Inject constructor(repo: LoanApplicationRepository, policy: LoanEligibilityPolicy)` | `class LoanApplyViewModel @Inject constructor(repo: LoanApplicationRepo)` |
| `repo.submit(application)` | `if (tenant.id == TenantId("cambodia:nh")) flowA() else flowB()` |
| Reading `TenantContext.displayName` to render a label | Reading `TenantContext.id` to dispatch logic |
| Reading `capabilities.supportsBakongDisputes()` and gating UI on the boolean | Reading `tenant.id` and gating UI on the string |

Two rules:

1. **Types from `:data` and `:tenants:*:*` cannot appear in `:features` source code.** The build graph forbids it.
2. **The tenant ID must not appear in conditional branches.** `TenantContext` may be read for **display** (currency code, organization name, market label, logo); never for **dispatch**. To gate a feature, use `TenantCapabilities` (a `:core` interface) — the tenant module sets the boolean, the UI reads it.

---

## 5. Navigation

Navigation lives in two layers:

| Layer | Owner | Purpose |
|---|---|---|
| **Top-level graph** | `:app` (`AppNavigation.kt`) | Boot → Auth → Main scaffold; conditionally includes tenant-locked feature graphs |
| **Feature subgraphs** | `:features` (`AuthNavigator.kt`, `LoanNavigator.kt`, `DashboardNavigator.kt`, …) | Internal routing within a feature |

Each feature exposes a `NavGraphBuilder.<feature>NavGraph(navController, …)` extension function, called from `:app`. Feature graphs reference each other only by route strings (no cross-package imports of Composables).

---

## 6. Visual Foundation: `:design-system`

`:features` does **not** define its own theme or components. Every Compose primitive comes from `:design-system`:

```kotlin
// In a :features screen
import com.<org>.design.theme.CompassTheme
import com.<org>.design.components.button.CompassButton
import com.<org>.design.components.input.CompassTextField

@Composable
internal fun LoginContent(state: LoginState, onEvent: (LoginEvent) -> Unit) {
    CompassTextField(
        value = state.username,
        onValueChange = { onEvent(LoginEvent.UsernameChanged(it)) },
    )
    CompassButton(
        onClick = { onEvent(LoginEvent.SubmitClicked) },
        isLoading = state.isSubmitting,
    ) { Text("Sign in") }
}
```

`:app` wraps the entire `NavHost` in `CompassTheme { … }`, so every screen renders inside the design system without each having to opt in.

> **Tenant branding override** is a future-roadmap concern. Branding is intentionally **not** part of MG's `RuntimeConfig` — see [11](11-mg-and-runtime-config.md). Until then, all tenants share the design system.

Detail: [04 — `:design-system`](04-design-system.md).

---

## 7. Public Surface

`:features` exposes only:

- The `NavGraphBuilder.*NavGraph(...)` extension functions for each feature
- The `Route` constants needed by `:app` for top-level deeplinks
- Nothing else — all ViewModels, screens, contracts, and helpers are `internal`

---

## 8. What Does NOT Go In `:features`

| ❌ Doesn't belong | ✅ Goes in |
|---|---|
| `LoanApplicationRepo` | `:data` |
| `interface LoanApplicationRepository` | `:core` |
| `OkHttpClient` setup | `:aos-sdk` |
| `CompassButton` and other UI primitives | `:design-system` |
| Tenant-specific fee tables | `:tenants:{region}:{tenantSlug}` (or `:tenants:{region}:base` if regulator-wide) |
| `if (tenant.id == TenantId("cambodia:nh")) { … }` | nowhere — use a policy or capability interface |

---

## 9. Cross-references

- The contracts `:features` consumes: [03 — `:core`](03-core.md)
- The visual primitives `:features` uses: [04 — `:design-system`](04-design-system.md)
- Where repository implementations come from: [05 — `:data`](05-data.md)
- Where tenant policies come from: [07 — `:tenants:*`](07-variants.md)
- MVI conventions every ViewModel follows: [09 — MVI Pattern](09-mvi-pattern.md)
- Why Hybrid-Monolith specifically: [14 — Build Performance](14-build-performance.md)
