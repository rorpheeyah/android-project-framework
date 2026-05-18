# 06 · `:features` — UI Engine (Hybrid-Monolith)

> **Type:** Local Android library
> **Role:** Logic-Blind Compose UI organized by package
> **Constraint:** May import `:core`, `:design-system`, and `:aos-core`. May **never** import `:data` or `:variants-*`.

---

## 1. Purpose

`:features` is the **UI engine** — the screens, ViewModels, navigation, and feature-flow orchestration that the user actually sees. It is deliberately **Logic-Blind**: every business decision (how a reimbursable amount is calculated, which validation rule applies, what API is called, which fields are visible for the current tenant) is delegated to a `:core` interface that some other module implements.

This module is a **single Gradle module** with feature boundaries enforced by **packages**, not by sub-modules. We call this shape the **Hybrid-Monolith**.

The visual primitives (`BizTheme`, `BizButton`, `BizTextField`, `BizWebView`, …) live in `:design-system`, not here. `:features` consumes them.

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

> **When to break out into a separate module:** when a feature pulls in **heavy unique dependencies** that other features should not pay for, OR when a feature is variant-locked with its own API + DTOs + screens. The scanner (io.card + camera + OCR + scraping) is the canonical example for the first; `:features-hipass` (Korea-only highway-toll capture) is the canonical example for the second. See [07 — `:variants-*` § "When the Variant Has Unique Features"](07-variants.md).

---

## 3. Package Layout

```
:features/
└── src/main/kotlin/com/bizplay/features/
    ├── boot/                  # Cold-start: MgGate fetch, MaintenanceGate, ForceUpdateGate
    │   ├── BootScreen.kt
    │   ├── BootViewModel.kt
    │   ├── BootContract.kt
    │   ├── MaintenanceGate.kt
    │   └── ForceUpdateGate.kt
    │
    ├── auth/                  # Login, OTP, institution picker (today's SelectUserInttIdActivity)
    │   ├── login/
    │   │   ├── LoginScreen.kt
    │   │   ├── LoginViewModel.kt
    │   │   └── LoginContract.kt
    │   ├── otp/
    │   ├── institutionpicker/
    │   │   ├── InstitutionPickerScreen.kt
    │   │   ├── InstitutionPickerViewModel.kt
    │   │   └── InstitutionPickerContract.kt
    │   └── AuthNavigator.kt
    │
    ├── receipt/               # Receipt list, detail, edit, create (camera entry lives in :features-scanner)
    │   ├── list/
    │   │   ├── ReceiptListScreen.kt
    │   │   ├── ReceiptListViewModel.kt
    │   │   └── ReceiptListContract.kt
    │   ├── detail/
    │   ├── edit/
    │   ├── create/
    │   ├── transport/         # taxi / transport entry
    │   ├── biztrip/           # business-trip bundle (and BizDoc variant)
    │   ├── gasoline/          # fuel + route capture
    │   └── ReceiptNavigator.kt
    │
    ├── expense/               # Expense report bundling, category picker
    │   ├── report/
    │   ├── category/
    │   └── ExpenseNavigator.kt
    │
    ├── approval/              # Approval inbox, approve/reject, line setup
    │   ├── inbox/
    │   │   ├── ApprovalInboxScreen.kt
    │   │   ├── ApprovalInboxViewModel.kt
    │   │   └── ApprovalInboxContract.kt
    │   ├── action/
    │   ├── line/
    │   └── ApprovalNavigator.kt
    │
    ├── card/                  # Card register, list, statement (card scanner lives in :features-scanner)
    │   ├── register/
    │   ├── list/
    │   ├── statement/
    │   └── CardNavigator.kt
    │
    ├── notice/                # Announcements, contact, help
    │   ├── list/
    │   ├── detail/
    │   └── NoticeNavigator.kt
    │
    └── account/               # Active-institution switcher (today's USE_INTT_ID flip), profile, language
        ├── switcher/
        │   ├── InstitutionSwitcherSheet.kt
        │   ├── InstitutionSwitcherViewModel.kt
        │   └── InstitutionSwitcherContract.kt
        ├── profile/
        ├── language/
        └── AccountNavigator.kt
```

Note the absence of a `common/` package. Theme and component primitives live in `:design-system`, depended on directly. Anything truly cross-cutting at the *feature* level (a multi-flow state holder, for example) can stay in a feature-local helper or be promoted to `:core/model/` if it carries domain meaning.

> **What's not in `:features`:** the OCR / camera / card-scan flows live in `:features-scanner` (heavy SDK weight). The Hi-Pass capture flow lives in `:features-hipass` (Korea-only API surface). The chatbot / KakaoPay-link flows, if implemented, would live in their own sibling modules. See [07 — `:variants-*` § 9](07-variants.md).

### Package-level conventions

- **One `*Contract.kt` per screen** — defines the screen's `UiState`, `UiEvent`, and `UiEffect` sealed types. Keeps MVI types co-located with the screen they belong to.
- **`internal` Kotlin visibility** for everything except: the Composable that `:app` navigates to, and the navigator entry function.
- **No nested `data` packages** — feature packages should hold UI + ViewModel only. Anything resembling a data source belongs in `:data` or `:core`.

---

## 4. Logic-Blind Constraint

A `:features` ViewModel **must not know** which variant or tenant is active.

| Allowed in `:features` | Not allowed in `:features` |
|---|---|
| `class ReceiptDetailViewModel @Inject constructor(repo: ReceiptRepository, amountPolicy: ExpenseAmountPolicy, capabilities: VariantCapabilities, tenant: TenantContext)` | `class ReceiptDetailViewModel @Inject constructor(repo: IpppReceiptRepo)` |
| `repo.detail(id)` | `if (variantId == VariantId.KR) kakaoPayFlow() else localFlow()` |
| Reading `VariantContext.displayName` to render a label | Reading `VariantContext.id` to dispatch logic |
| Reading `tenant.flags.hidesEmployeeId` to gate a field | Reading `tenant.id == "nia"` to gate a field |
| Reading `capabilities.supportsKakaoPayLink()` and gating UI on the boolean | Reading `variantId` and gating UI on the string |
| Reading `tenant.params.employeeIdRegex` and constructing a validator | Hardcoding the regex per tenant |

Two rules:

1. **Types from `:data` and `:variants-*` cannot appear in `:features` source code.** The build graph forbids it.
2. **The variant ID and tenant ID must not appear in conditional branches.** `VariantContext` may be read for **display** (currency code, region name, market label, logo); `TenantContext` may be read for its **flag/param fields** — never for **dispatch**. To gate a feature, use `VariantCapabilities` (a `:core` interface) or a named `TenantFlags` field — the variant module / tenant profile sets the boolean, the UI reads it.

> **Compare with today:** the existing Bizplay code has `if (DetailConfig.isNIA()) hideEmployeeId(); else if (DetailConfig.isWIPS()) clearEmployeeNumber(); …` patterns in `ReceiptDetailActivity` and several adapters. In the framework, the same screens become `setState { copy(showEmployeeId = !tenant.flags.hidesEmployeeId, clearEmployeeNumberOnApproval = tenant.flags.clearsEmployeeNumberOnApproval) }` — *one read*, no branching, in `init { … }` of the VM.

---

## 5. Navigation

Navigation lives in two layers:

| Layer | Owner | Purpose |
|---|---|---|
| **Top-level graph** | `:app` (`AppNavigation.kt`) | Boot → Auth → Main scaffold; conditionally includes variant-locked feature graphs (`:features-hipass` when `capabilities.supportsHipassTracking()` is true) |
| **Feature subgraphs** | `:features` (`AuthNavigator.kt`, `ReceiptNavigator.kt`, `ApprovalNavigator.kt`, …) | Internal routing within a feature |

Each feature exposes a `NavGraphBuilder.<feature>NavGraph(navController, …)` extension function, called from `:app`. Feature graphs reference each other only by route strings (no cross-package imports of Composables).

---

## 6. Visual Foundation: `:design-system`

`:features` does **not** define its own theme or components. Every Compose primitive comes from `:design-system`:

```kotlin
// In a :features screen
import com.bizplay.design.theme.BizTheme
import com.bizplay.design.components.button.BizButton
import com.bizplay.design.components.input.BizTextField
import com.bizplay.design.components.input.BizPasswordField

@Composable
internal fun LoginContent(state: LoginState, onEvent: (LoginEvent) -> Unit) {
    BizTextField(
        value = state.username,
        onValueChange = { onEvent(LoginEvent.UsernameChanged(it)) },
        label = "Employee ID",
    )
    BizPasswordField(                          // routes through TransKey via :aos-core/security/
        value = state.password,
        onValueChange = { onEvent(LoginEvent.PasswordChanged(it)) },
    )
    BizButton(
        onClick = { onEvent(LoginEvent.SubmitClicked) },
        isLoading = state.isSubmitting,
    ) { Text("Sign in") }
}
```

`:app` wraps the entire `NavHost` in `BizTheme { … }`, so every screen renders inside the design system without each having to opt in.

> **Per-tenant branding override** (POSCO red, Lotte navy, …) is a future-roadmap concern. Branding is intentionally **not** part of MgGate's `RuntimeConfig` — see [11](11-mg-and-runtime-config.md). Until then, all variants and tenants share the design system.

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
| `IpppReceiptRepo` | `:data` |
| `interface ReceiptRepository` | `:core` |
| `OkHttpClient` setup | `:aos-core` |
| `BizButton` and other UI primitives | `:design-system` |
| Region-specific fee / tax tables | `:variants-{id}` |
| Tenant-specific layouts (Shinsegae's distinct approval line) | `:variants-{region}/tenants/{id}/` |
| `if (variantId == "kr") { … }` | nowhere — use a policy or capability interface |
| `if (DetailConfig.isNIA()) { … }` | nowhere — read `tenant.flags.*` instead |
| WebView screens (terms, approval, mall, KakaoPay link) | `:features-{name}` sibling modules — see [18](18-webview-integration.md) |
| Camera / OCR / card-scan UI | `:features-scanner` |
| Hi-Pass capture | `:features-hipass` |

---

## 9. Cross-references

- The contracts `:features` consumes: [03 — `:core`](03-core.md)
- The visual primitives `:features` uses: [04 — `:design-system`](04-design-system.md)
- Where repository implementations come from: [05 — `:data`](05-data.md)
- Where variant policies come from: [07 — `:variants-*`](07-variants.md)
- Where tenant flags / params come from: [19 — Tenants and Variants](19-tenants-and-variants.md)
- MVI conventions every ViewModel follows: [09 — MVI Pattern](09-mvi-pattern.md)
- Why Hybrid-Monolith specifically: [14 — Build Performance](14-build-performance.md)
