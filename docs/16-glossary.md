# 16 · Glossary

> Terms specific to this framework. Use these consistently in code, comments, commits, and conversation.

---

### `:aos-core`

The infrastructure submodule. Project-agnostic plumbing (network, security, storage, logging, the WebView primitive) reusable across non-expense-management projects. Wraps the existing security stack (SQLCipher, TransKey, mVaccine, Secucen EdgeCrypto, RSLicense). Does not know it's an expense app.

→ [02 — `:aos-core`](02-aos-core.md)

---

### `:core`

The contract layer. Houses repository interfaces, policy interfaces, domain models, MVI base contracts, `RuntimeConfig`, `Session`, `DepartmentAccount`, `VariantContext`, `TenantContext`, `TenantFlags`, `TenantParams`. Imported by `:data`, `:features`, and `:variants-*`; depends on none of them.

→ [03 — `:core`](03-core.md)

---

### `:data`

The data layer. Owns the `Ippp*Api` Retrofit family (split by feature area: auth, receipt, approval, card, expense, OCR, notice) and the `Ippp*Repo` classes that implement `:core` repository interfaces. Variant- and tenant-agnostic — same code runs for every logged-in user; the server demuxes by `USE_INTT_ID` + `COMPANY_CD`.

→ [05 — `:data`](05-data.md)

---

### `:design-system`

The shared visual library: theme tokens (`BizTheme`, `BizColors`, `BizTypography`), primitive Composables (`BizButton`, `BizTextField`, `BizPasswordField`, `BizWebView`, `BizReceiptHeader`, `BizReceiptRow`, …), and Compose-side helpers. Variant-agnostic and domain-agnostic — no expense / receipt types, no `:core` dependency. Depended on by every UI module (`:features`, `:features-scanner`, `:features-{variant-feature}`).

→ [04 — `:design-system`](04-design-system.md)

---

### `:features`

The **Hybrid-Monolith** UI engine. One Gradle module containing all standard expense-management flows organized by package (boot, auth, receipt, expense, approval, card, notice, account). Logic-Blind by construction.

→ [06 — `:features`](06-features.md)

---

### `:features-scanner`

An **isolated feature module**, sibling to `:features`. Kept separate because the camera + OCR + payment-card scan + scraping stack (io.card, cameraviewplus, sasapi, OCR partner SDKs) pulls in heavy unique dependencies that would penalize incremental builds of unrelated features.

---

### `:features-hipass`

The canonical **variant-locked feature module**. Korea-only highway-toll capture with its own IPPP endpoints + DTOs + screens. Gated by `VariantCapabilities.supportsHipassTracking()`. Compiles into the binary for all variants; only registered into the nav graph when the active variant returns `true`.

---

### `:variants-{id}`

The **variant module**. One per region/regulator: `:variants-kr`, `:variants-kh`, `:variants-vn`. Houses region-specific policies (validation, fees, formatting, business calendar, receipt rendering) and capability flags. Per-corporate-customer differences inside a region are modeled as [Tenant]s living under `:variants-{region}/tenants/`. **Does not** own UI, Retrofit, DTOs, or repositories.

→ [07 — `:variants-*`](07-variants.md)

---

### `:app`

The orchestrator. Application class, `BootCoordinator`, navigation host, `LoggedInComponent` definition, `VariantCatalogue`, `TenantCatalogue`, debug overlays. The only module that depends on every other module.

→ [08 — `:app`](08-app-orchestrator.md)

---

### `AccountIdInterceptor`

OkHttp interceptor in `:app/session/` that reads `Session.activeAccount()` at call time and stamps the institution headers (`X-Use-Intt-Id`, `X-Company-Cd`, `X-Dvsn-Cd`) on every authenticated request. Today's `USE_INTT_ID` and `COMPANY_CD` request fields are produced here.

→ [12 — Departments and Session](12-departments-and-session.md)

---

### `BizWebView`

The hardened WebView Composable in `:aos-core/webview/`. Replaces today's `BizWebview` Java class. Co-located with `WebActionBridge` (the JS bridge primitive) and `CookieSync` (the OkHttp ↔ `CookieManager` bridge). Used by sibling webview feature modules (terms, approval UI, mall, KakaoPay link, …).

→ [18 — WebView Integration](18-webview-integration.md)

---

### `BootCoordinator`

The `:app` singleton that runs the boot sequence: MgGate fetch → MaintenanceGate → Login → build SessionGraph (with variant + tenant) → navigate to main.

→ [10 — Boot Phases](10-boot-phases.md)

---

### Conditional Logic Sprawl

The anti-pattern this framework exists to prevent: variant- or tenant-specific behavior expressed as `if (variantId == X)` chains or `if (DetailConfig.isXxx())` predicates scattered across the codebase. The existing Bizplay code has ~124 call sites of `DetailConfig.isXxx()` — the framework replaces those with polymorphic dispatch through `:core` interfaces (variant policies) and named `TenantFlags` / `TenantParams` fields (tenant differences).

→ [00 — Overview](00-overview.md), [19 — Tenants and Variants § 5](19-tenants-and-variants.md)

---

### Decentralized Data/Domain

Architectural principle: business logic lives in `:variants-{id}` (for variant differentiation) or in `TenantFlags` / per-tenant structural policies (for customer-org differentiation) or `:features` packages (for shared UI logic). Shared API/repo logic lives in `:data`. Never in `:core` or `:aos-core`.

---

### `DepartmentAccount`

A `:core` value type representing one of the multiple institution memberships a logged-in user has access to (e.g., POSCO ICT — Seoul HQ, Lotte E&C — Procurement). Carries `id` (today's `USE_INTT_ID`), `companyCode` (today's `COMPANY_CD`), `divisionCode` / `divisionName` (today's `DVSN_CD` / `DVSN_NM`). The active membership is held in `Session.activeAccountId` (a `StateFlow<AccountId>`); `AccountIdInterceptor` stamps it on every authenticated request.

→ [12 — Departments and Session](12-departments-and-session.md)

---

### `Ippp*Api`

The family of Retrofit interfaces in `:data` that talk to the team's unified IPPP backend, split by feature area for ergonomics: `IpppAuthApi`, `IpppReceiptApi`, `IpppApprovalApi`, `IpppCardApi`, `IpppExpenseApi`, `IpppOcrApi`, `IpppNoticeApi`. **One backend, one set of DTOs, no per-variant or per-tenant duplication** — the server reads the auth token + the `USE_INTT_ID` / `COMPANY_CD` headers and dispatches to the right corporate / rail itself.

→ [05 — `:data`](05-data.md)

---

### Hybrid-Monolith

The shape of `:features`: a single Gradle module with internal feature packages. Combines the **organizational benefits** of feature modules with the **build performance** of a single module. Discipline (lint + code review) replaces the build system as the boundary enforcer at the package level.

→ [14 — Build Performance](14-build-performance.md)

---

### Isolated Feature Module

A `:features-{feature-name}` Gradle module sibling to `:features` that holds a self-contained feature with either unique heavy dependencies (e.g. `:features-scanner` for io.card + camera + OCR + scraping) or a variant-locked scope with its own API + DTOs + screens (e.g. `:features-hipass` for Korea-only highway-toll capture). Conditionally wired into the top-level nav graph by `:app`, gated on a `VariantCapabilities` flag for variant-locked cases.

→ [07 — `:variants-*` § "When the Variant Has Unique Features"](07-variants.md)

---

### Linear Build Performance

Architectural promise: adding a new feature folder must increase build cost only proportional to the new code's compile cost — not impose a fixed overhead per folder. Drives the choice of Hybrid-Monolith for `:features`.

---

### Logic-Blind

The constraint that defines `:features`. UI code may consume `:core` interfaces but **must not import `:data` or any `:variants-*` module**. A `:features` ViewModel cannot, by construction, know which variant or tenant is active. Enforced by the build graph. (Reading `tenant.flags.hidesEmployeeId` is allowed — that's a flag read, not dispatch.)

→ [06 — `:features`](06-features.md)

---

### `LoggedInComponent`

The custom Hilt component built **once at login** with the bindings of the user's variant + the user's tenant (and the shared `:data` repos), and torn down at logout. All `@LoggedInScoped` instances belong to one component. There is no in-session swap — variant or tenant change requires logout-then-login. Institution switching (active `USE_INTT_ID`) is a separate, lighter axis.

→ [10 — Boot Phases](10-boot-phases.md)

---

### `@LoggedInScoped`

The custom Hilt scope annotation defined in `:core/scope/`. Instances are alive for one logged-in session: created when `LoggedInComponent` is built, eligible for GC when the component is dropped on logout.

---

### `MaintenanceGate`

Compose screen shown after MgGate fetch and before the login screen if `RuntimeConfig.maintenance.isDown` is true or if the running app version is below `RuntimeConfig.forceUpdate.minimumVersionCode`. Hard-blocks until the user resolves it.

→ [11 — MG and Runtime Config](11-mg-and-runtime-config.md)

---

### MG (Mobile Gateway / `MgGate`)

The startup service-discovery and ops-gating endpoint. Hardcoded URL per build/environment is the only network configuration the binary contains (today: `Conf.SITE_MG_URL + "/MgGate"`). MG returns `{ urls, webRoutes, maintenance, forceUpdate, storeReviewMode }`. Called once at cold start before any feature can run.

→ [11 — MG and Runtime Config](11-mg-and-runtime-config.md)

---

### MVI

Model-View-Intent. The architecture pattern used by every screen in `:features`, `:features-scanner`, and sibling feature modules. State is immutable, exhausted by `UiState`. User actions are `UiEvent`. One-shot side effects (navigation, toasts) are `UiEffect`. Strict unidirectional data flow.

→ [09 — MVI Pattern](09-mvi-pattern.md)

---

### Polymorphic Scalability

Architectural principle: `:features` calls a `:core` interface; `:data` provides one impl for repositories (uniform across variants), and the active `:variants-{id}` provides impls for region policies (variant-specific). Tenant differences are surfaced as named `TenantFlags` / `TenantParams` fields, or — for structural differences — per-tenant impls of `:core` policy interfaces. There is no `if (variantId == X)` and no `if (tenant.id == X)` anywhere in the codebase — polymorphism (or named field reads) is the dispatch.

---

### `RuntimeConfig`

Immutable container in `:core` holding the values returned by MgGate: API base URLs, web-route allowlist, maintenance state, force-update info, store-review mode. Resolved once during boot, lives for the process lifetime.

→ [11 — MG and Runtime Config](11-mg-and-runtime-config.md)

---

### `Session`

The logged-in user's session state, held inside `LoggedInComponent`. Owns the `UserSession` (tokens, userId), `variantContext: VariantContext`, `tenantContext: TenantContext`, `accounts: List<DepartmentAccount>`, and `activeAccountId: StateFlow<AccountId>`. Destroyed on logout.

→ [12 — Departments and Session](12-departments-and-session.md)

---

### Tenant

A corporate-customer identity that lives **inside** a [Variant]. Captures per-organisation differences that do not change the regulator or rails — typically field visibility (employee-ID, ID-number capture), label text, employee-ID format / regex, approval-line shape, receipt-style preference, password-reset-allowed. Captured at login as a `TenantContext`; immutable for the session. Every variant ships a `default` tenant. In Bizplay's existing reality, the Korean tenants are POSCO, Lotte, NIA, Shinsegae, ITCen, WIPS, HANA, IBS, SPC, and Chilsung Beverage (folded under Lotte's flags).

→ [19 — Tenants and Variants](19-tenants-and-variants.md)

---

### `TenantContext`

Snapshot type in `:core/variant/` describing the active tenant: `id`, `displayName`, plus `flags: TenantFlags` and `params: TenantParams`. Resolved once at login from `LoginResponse`. Read by `:features` for **display and parametric gating only** — never `tenant.id` for dispatch.

---

### `TenantFlags`

Data class in `:core/variant/` carrying named boolean fields the server sets per tenant (`hidesEmployeeId`, `clearsEmployeeNumberOnApproval`, `requiresIdNumberCapture`, `usesTripExpenseFlow`, `allowsPasswordResetInApp`, …). The client owns the field schema; the server owns the values. New flag = `:core` PR.

---

### `@TenantKey`

Hilt multibinding key annotation in `:core/scope/` analogous to `@VariantKey`. Used by structural `TenantPolicy` impls (e.g. `ShinsegaeApprovalLineRenderer`) to bind into a `Map<String, T>` resolved by `TenantContext.id` at injection time. Variants ship a `default` entry as fallback.

---

### `TenantParams`

Data class in `:core/variant/` carrying named typed fields (`employeeIdRegex`, `approvalLineMaxDepth`, `receiptFooterText`, `supportPhoneOverride`, …). Same client-schema / server-values contract as `TenantFlags`.

---

### `TenantPolicy`

Conceptual term for a `:core/policy/` interface implemented per tenant when parametric `TenantFlags` / `TenantParams` aren't enough — e.g. `ApprovalLineRenderer` with `DefaultApprovalLineRenderer` + `ShinsegaeApprovalLineRenderer` impls. Lives in `:variants-{region}/tenants/{id}/`. The escalation; not the default.

---

### Two-Layer Foundation

The split between `:aos-core` (project-agnostic infrastructure) and `:core` (project-specific contracts). The split exists because infrastructure stability and product-domain stability evolve at different rates.

---

### Variant

A region/regulator-specific implementation of `:core` policy interfaces, packaged as a single Gradle module (`:variants-{id}`). Differs by currency, settlement rails, KYC body, compliance limits, tax/VAT rules, holiday calendar. Variants are isolated from each other; cross-variant imports are forbidden by the build graph. Per-corporate-customer differences inside a region are modeled as [Tenant]s, not as separate variants.

→ [19 — Tenants and Variants](19-tenants-and-variants.md)

→ [07 — `:variants-*`](07-variants.md)

---

### `VariantCapabilities`

A `:core` interface that each variant implements with boolean flags (`supportsKakaoPayLink()`, `supportsHipassTracking()`, `supportsMyDataIntegration()`, `supportsOcrTicketScan()`, etc.). The way `:features` gates UI on variant-specific feature presence — never by branching on `variantId`.

---

### `VariantContext`

Snapshot type in `:core` describing the active variant: ID, display name, market code, default currency, default locale. Resolved once at login, immutable for the session. Read by `:features` for **display purposes only** (e.g., currency formatting, region name); never for conditional logic.

---

### Variant Silo

A `:variants-{id}` module viewed as an isolation boundary. The build graph enforces "variant A cannot import variant B" — a regression in one variant cannot reach another.

---

### Cross-references

- For a top-level summary: [00 — Overview](00-overview.md)
- For the architectural shape that ties these terms together: [01 — Module Topology](01-module-topology.md)
