# 04 · `:features` — UI Engine (Hybrid-Monolith)

> **Type:** Local Android library
> **Role:** Logic-Blind Compose UI organized by package, not by module
> **Constraint:** May import `:core` and `:aos-core`. May **never** import `:tenants:*`.

---

## 1. Purpose

`:features` is the **UI engine** — the screens, ViewModels, navigation, and design system that the user actually sees. It is deliberately **Logic-Blind**: every banking decision (how a fee is calculated, which validation rule applies, what API is called) is delegated to a `:core` interface that some tenant module implements.

This module is a **single Gradle module** with feature boundaries enforced by **packages**, not by sub-modules. We call this shape the **Hybrid-Monolith**.

---

## 2. Why Hybrid-Monolith?

The textbook answer to "many features" is "one Gradle module per feature". For Nexus, this is the wrong tradeoff:

| Strict per-feature modules | Hybrid-Monolith (chosen) |
|---|---|
| Build graph: dozens of feature modules | One feature module, dozens of packages |
| Per-module Gradle config cost: high (~hundreds of ms each) | Paid once |
| Cross-feature refactor: edit N `build.gradle.kts` | Edit nothing — packages move freely |
| Shared design system: needs its own module | Lives in `common/` package, no extra module |
| Build perf scales with: O(features × Gradle overhead) | O(features) — linear in source size |

The win is **build performance** + **refactoring fluidity**. The cost — and we accept it — is that **package-level discipline depends on convention** rather than the build system. Lint rules and code review enforce it.

> **When to break out into a separate module:** when a feature pulls in **heavy unique dependencies** that other features should not pay for. The chatbot is the canonical example — see `:features-chatbot`.

---

## 3. Package Layout

```
:features/
└── src/main/kotlin/com/nexus/features/
    ├── splash/                 # Cold-start gate, integrity check, routing decision
    │   ├── SplashScreen.kt
    │   ├── SplashViewModel.kt
    │   └── SplashContract.kt   # UiState/UiEvent/UiEffect
    │
    ├── auth/                   # Login, registration, OTP, biometric prompt
    │   ├── login/
    │   │   ├── LoginScreen.kt
    │   │   ├── LoginViewModel.kt
    │   │   └── LoginContract.kt
    │   ├── otp/
    │   │   └── …
    │   └── AuthNavigator.kt
    │
    ├── transfer/               # P2P, QR, beneficiary lookup, review
    │   ├── input/
    │   │   ├── TransferInputScreen.kt
    │   │   ├── TransferInputViewModel.kt
    │   │   └── TransferInputContract.kt
    │   ├── review/
    │   │   └── …
    │   ├── result/
    │   │   └── …
    │   └── TransferFlowState.kt
    │
    ├── account/                # Balances, history
    │   └── …
    │
    └── common/                 # Design system: theme, components, layouts
        ├── theme/
        │   ├── NexusTheme.kt
        │   ├── NexusColors.kt
        │   └── NexusTypography.kt
        └── components/
            ├── NexusButton.kt
            ├── NexusTextField.kt
            └── NexusBottomSheet.kt
```

### Package-level conventions

- **One `*Contract.kt` per screen** — defines the screen's `UiState`, `UiEvent`, and `UiEffect` sealed types. Keeps MVI types co-located with the screen they belong to.
- **`internal` Kotlin visibility** for everything except: the Composable that `:app` navigates to, and the navigator entry function.
- **No nested `data` packages** — feature packages should hold UI + ViewModel only. Anything resembling a data source belongs in `:tenants:*` or `:core`.

---

## 4. Logic-Blind Constraint

A `:features` ViewModel **must not know** which tenant is active.

| Allowed in `:features` | Not allowed in `:features` |
|---|---|
| `class TransferViewModel @Inject constructor(repo: TransferRepository, policy: TransferAmountPolicy)` | `class TransferViewModel @Inject constructor(repo: BakongTransferRepo)` |
| `repo.submit(intent)` | `if (tenantId == TenantId.KH) bakongFlow() else napasFlow()` |
| Reading `TenantContext.displayName` to render a label | Reading `TenantContext.id` to dispatch logic |

The first rule of Logic-Blind: **types from `:tenants:*` cannot appear in `:features` source code**. The second rule: **the tenant ID must not appear in conditional branches**. (Reading `TenantContext` for display purposes — bank logo, currency code, market name — is fine.)

---

## 5. Navigation

Navigation lives in two layers:

| Layer | Owner | Purpose |
|---|---|---|
| **Top-level graph** | `:app` (`AppNavigation.kt`) | Splash → Auth → Main scaffold |
| **Feature subgraphs** | `:features` (`AuthNavigator.kt`, `TransferNavigator.kt`, …) | Internal routing within a feature |

Each feature exposes a `NavGraphBuilder.<feature>NavGraph(navController, …)` extension function, called from `:app`. Feature graphs reference each other only by route strings (no cross-package imports of Composables).

---

## 6. Theme & Design System (`common/`)

The `common/` package owns the **design system**:

- `NexusTheme` — Material3 theme setup with the framework's "Hyper-Physical Minimalism" tokens (orange accent, neutral grays, generous spacing)
- `NexusButton`, `NexusTextField`, `NexusCard`, `NexusBottomSheet` — primitives every feature uses
- `NexusColors`, `NexusTypography`, `NexusSpacing` — token files

> **Tenant branding override** is a future-roadmap concern. The plan: per-tenant theme overrides supplied via `TenantContext.brandTokens` and consumed by a CompositionLocal. Until then, all tenants share the Nexus brand.

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
| `BakongTransferRepo` | `:tenants:tenants-kh` |
| `interface TransferRepository` | `:core` |
| `OkHttpClient` setup | `:aos-core` |
| Tenant-specific fee tables | `:tenants:[id]` |
| `if (tenant.id == TenantId.PPC) { … }` | nowhere — use a policy interface |

---

## 9. Cross-references

- The contracts `:features` consumes: [03 — `:core`](03-core.md)
- Where implementations come from: [05 — `:tenants:*`](05-tenants.md)
- MVI conventions every ViewModel follows: [07 — MVI Pattern](07-mvi-pattern.md)
- Why Hybrid-Monolith specifically: [12 — Build Performance](12-build-performance.md)
