# 16 · Glossary

> Terms specific to this framework. Use these consistently in code, comments, commits, and conversation.

> **Migration note:** earlier glossary entries for `Variant`, `VariantContext`, `VariantCapabilities`, `Variant Silo`, and `:variants-{id}` have been retired in the variant-collapse refactor. The single DI axis is now `Tenant`, with region as a Gradle module hierarchy. See [19 § 12](19-tenants-and-variants.md) for the rationale.

---

### `:aos-sdk`

The infrastructure submodule (formerly `:aos-core`). Project-agnostic plumbing (network, security, storage, logging, camera, ML, imaging, PDF, push channels, deep links, locale, WebView) reusable across non-banking Android products. Git-tag-released for multi-product consumption. Does not know it's a bank.

→ [02 — `:aos-sdk`](02-aos-core.md)

---

### `:core`

The contract layer. Houses repository interfaces, policy interfaces, domain models, MVI base contracts, `RuntimeConfig`, `Session`, `DepartmentAccount`, `TenantContext`. Imported by `:data`, `:features`, and `:tenants:*:*`; depends on none of them.

→ [03 — `:core`](03-core.md)

---

### `:data`

The data layer. Owns the unified `Fintech*Api` family (Retrofit interfaces split by feature area) and the `*Repo` classes that implement `:core` repository interfaces, plus `:data/external/` for third-party clients (CBC, MWL agency, bank-statement analyzer, Sendbird chat impl). Tenant-agnostic — same code runs for every logged-in user; the server demuxes.

→ [05 — `:data`](05-data.md)

---

### `:design-system`

The shared visual library: theme tokens (`CompassTheme`, `CompassColors`, `CompassTypography`), primitive Composables (`CompassButton`, `CompassTextField`, `CompassBottomSheet`, `CompassPinInput`, `LocaleSelector`, …), and Compose-side helpers (including `Modifier.secureScreen()` for FLAG_SECURE). Tenant-agnostic and domain-agnostic — no banking types, no `:core` dependency. Depended on by every UI module (`:features`, `:features-chatbot`, `:features-{name}`).

→ [04 — `:design-system`](04-design-system.md)

---

### `:features`

The **Hybrid-Monolith** UI engine. One Gradle module containing all standard banking flows organized by package. Logic-Blind by construction.

→ [06 — `:features`](06-features.md)

---

### `:features-chatbot`

An **isolated feature module**, sibling to `:features`. Kept separate because chatbot SDKs pull in heavy unique dependencies that would penalize incremental builds of unrelated features. Other heavy-SDK siblings follow the same pattern: `:features-kyc` (CameraX + ML Kit), `:features-support-chat` (Sendbird), `:features-branch-locator` (Google Maps Compose).

---

### `:tenants:{region}:base`

A **region-baseline module**. One per regulator/region (`:tenants:cambodia:base`, `:tenants:korea:base`). Houses shared regulator-wide policy classes (currency formatter, compliance thresholds, OTP delivery, business calendar, KYC requirements). Provides classes; does not declare Hilt bindings. Concrete tenants in the region depend on this module via Gradle.

→ [07 — `:tenants:*`](07-variants.md)

---

### `:tenants:{region}:{tenantSlug}`

A **concrete-tenant module**. One per customer organization within a region (`:tenants:cambodia:nh`, `:tenants:cambodia:partner-a`, …). Houses tenant-specific policies and a Hilt module that declares `@TenantKey("<region>:<tenantSlug>")` bindings — typically rebinding region-base classes plus any tenant-specific overrides. **Does not** own UI, Retrofit, DTOs, or repositories. Depends on its own region base via Gradle.

→ [07 — `:tenants:*`](07-variants.md)

Every region also ships a `:tenants:{region}:default` sentinel tenant — used in tests and as the no-overrides baseline. Never resolves in production.

---

### `:app`

The orchestrator. Application class, `BootCoordinator`, navigation host, `LoggedInComponent` definition, `TenantCatalogue`, `TenantResolverModule`, debug overlays. The only module that depends on every other module.

→ [08 — `:app`](08-app-orchestrator.md)

---

### `AccountIdInterceptor`

OkHttp interceptor in `:app/session/` that reads `Session.activeAccountId.value` at call time and stamps it as the `X-Account-Id` header on every authenticated request. Active even in single-account consuming apps (where it stamps the same value every time) — kept for outsource-project multi-account readiness.

→ [12 — Departments and Session](12-departments-and-session.md)

---

### `BootCoordinator`

The `:app` singleton that runs the boot sequence: MG fetch (with 24h stale-config fallback) → MaintenanceGate → Login → build LoggedInComponent → navigate to main.

→ [10 — Boot Phases](10-boot-phases.md)

---

### Conditional Logic Sprawl

The anti-pattern this framework exists to prevent: tenant-specific behavior expressed as `if (tenant.id == X) … else if (tenant.id == Y) …` chains scattered across the codebase. Replaced by polymorphic dispatch through `:core` interfaces, with one single point of dispatch in `:app/di/TenantResolverModule.kt`.

→ [00 — Overview](00-overview.md)

---

### Decentralized Data/Domain

Architectural principle: business logic lives in `:tenants:{region}:{tenantSlug}` and `:tenants:{region}:base` (for tenant/regional differentiation) or `:features` packages (for shared UI logic). Shared API/repo logic lives in `:data`. Never in `:core` or `:aos-sdk`.

---

### `DepartmentAccount`

A `:core` value type representing one of the multiple accounts a logged-in user has access to (e.g., Personal, Corporate, Joint). The active account is held in `Session.activeAccountId` (a `StateFlow<AccountId>`); `AccountIdInterceptor` stamps it on every authenticated request. Single-account consuming apps ship with `accounts.size == 1` and hide the switcher UI declaratively.

→ [12 — Departments and Session](12-departments-and-session.md)

---

### `Fintech*Api`

The family of Retrofit interfaces in `:data` that talk to the team's unified backend, split by feature area for ergonomics: `FintechAuthApi`, `FintechLoanApi`, `FintechRepaymentApi`, `FintechGuarantorApi`, `FintechKycApi`, etc. **One backend, one set of DTOs, no per-tenant duplication** — the server reads the auth token + active account ID and dispatches to the right downstream system itself.

→ [05 — `:data`](05-data.md)

---

### Hybrid-Monolith

The shape of `:features`: a single Gradle module with internal feature packages. Combines the **organizational benefits** of feature modules with the **build performance** of a single module. Discipline (lint + code review) replaces the build system as the boundary enforcer at the package level. Heavy-SDK features are the documented exception (`:features-chatbot`, `:features-kyc`, `:features-support-chat`, `:features-branch-locator`).

→ [14 — Build Performance](14-build-performance.md)

---

### Isolated Feature Module

A `:features-{feature-name}` Gradle module sibling to `:features` that holds a self-contained feature with either unique heavy dependencies (e.g. `:features-chatbot`, `:features-kyc`, `:features-support-chat`, `:features-branch-locator`) or a tenant-locked scope with its own API + DTOs + screens (e.g. `:features-bakong-disputes`). Conditionally wired into the top-level nav graph by `:app`, gated on a `TenantCapabilities` flag for tenant-locked cases.

→ [07 — `:tenants:*` § "When the Tenant Has Unique Features"](07-variants.md)

---

### Linear Build Performance

Architectural promise: adding a new feature folder must increase build cost only proportional to the new code's compile cost — not impose a fixed overhead per folder. Drives the choice of Hybrid-Monolith for `:features`.

---

### Logic-Blind

The constraint that defines `:features`. UI code may consume `:core` interfaces but **must not import `:data` or any `:tenants:*:*` module**. A `:features` ViewModel cannot, by construction, know which tenant is active. Enforced by the build graph.

→ [06 — `:features`](06-features.md)

---

### `LoggedInComponent`

The custom Hilt component built **once at login** with the bindings of the user's tenant (and the shared `:data` repos), and torn down at logout. All `@LoggedInScoped` instances belong to one component. There is no in-session swap — tenant change requires logout-then-login.

→ [10 — Boot Phases](10-boot-phases.md)

---

### `@LoggedInScoped`

The custom Hilt scope annotation defined in `:core/scope/`. Instances are alive for one logged-in session: created when `LoggedInComponent` is built, eligible for GC when the component is dropped on logout.

---

### `MaintenanceGate`

Compose screen shown after MG fetch and before the login screen if `RuntimeConfig.maintenance.isDown` is true or if the running app version is below `RuntimeConfig.forceUpdate.minimumVersionCode`. Hard-blocks until the user resolves it.

→ [11 — MG and Runtime Config](11-mg-and-runtime-config.md)

---

### MG (Mobile Gateway)

The startup service-discovery and ops-gating endpoint. Hardcoded URL per build/environment is the only network configuration the binary contains (plus a single `staleConfigTtl` constant for the offline fallback window). MG returns `{ urls, maintenance, forceUpdate, thirdPartyAppIds }`. Called once at cold start before any feature can run; falls back to last-known-good cache for up to 24h on transient failure.

→ [11 — MG and Runtime Config](11-mg-and-runtime-config.md)

---

### MVI

Model-View-Intent. The architecture pattern used by every screen in `:features` and every sibling `:features-{name}` module. State is immutable, exhausted by `UiState`. User actions are `UiEvent`. One-shot side effects (navigation, toasts) are `UiEffect`. Strict unidirectional data flow.

→ [09 — MVI Pattern](09-mvi-pattern.md)

---

### Polymorphic Scalability

Architectural principle: `:features` calls a `:core` interface; `:data` provides one impl for repositories (uniform across tenants), and the active `:tenants:{region}:{tenantSlug}` provides impls for policies (tenant-specific). There is no `if (tenant.id == X)` anywhere in the codebase outside `:app/di/TenantResolverModule.kt` — polymorphism is the dispatch.

---

### Region

A regulator/country boundary, expressed in this framework as a **Gradle module hierarchy** rather than a runtime DI axis. Region-wide policies live in `:tenants:{region}:base`; concrete tenants in the region depend on the base via Gradle. Region is **not** an axis you dispatch on at runtime — the tenant is the only runtime axis.

→ [07 — `:tenants:*`](07-variants.md), [19 — Tenants and Regions](19-tenants-and-variants.md)

---

### `RuntimeConfig`

Immutable container in `:core` holding the values returned by MG: API base URLs, maintenance state, force-update info, third-party SDK app-ids (Sendbird, Google Maps). Resolved once during boot, lives for the process lifetime. A last-known-good copy is cached locally for the 24h stale-config fallback.

→ [11 — MG and Runtime Config](11-mg-and-runtime-config.md)

---

### `Session`

The logged-in user's session state, held inside `LoggedInComponent`. Owns the `UserSession` (tokens, userId), `accounts: List<DepartmentAccount>`, and `activeAccountId: StateFlow<AccountId>`. Destroyed on logout.

→ [12 — Departments and Session](12-departments-and-session.md)

---

### Stale-Config Fallback

The MG offline-resilience mechanism. After every successful MG fetch, `BootCoordinator` persists the typed `RuntimeConfig` to `EncryptedPrefs` with a timestamp. On cold start, if MG is unreachable within 3–5s and a cached `RuntimeConfig` exists with `now - cachedAt < staleConfigTtl` (default 24h), boot proceeds with the cached config and a non-blocking banner. A non-fatal Crashlytics event is emitted on fallback activation. If no cached config is fresh enough, boot hard-fails.

→ [11 — MG and Runtime Config § 7](11-mg-and-runtime-config.md)

---

### Tenant

The framework's single runtime DI axis. A customer-organization identity selected at login, packaged as a `:tenants:{region}:{tenantSlug}` Gradle module. `TenantId.value` is a composite `<region>:<tenantSlug>` (e.g. `"cambodia:nh"`). Captures per-org differences: field visibility, label text, employee-ID format, approval-line shape, plus optional structural overrides. Captured at login as a `TenantContext`; immutable for the session. Every region ships a `default` tenant (for tests).

→ [19 — Tenants and Regions](19-tenants-and-variants.md)

---

### `TenantCapabilities`

A `:core` interface that each tenant implements with boolean flags (`supportsBakongDisputes()`, `supportsCardlessAtm()`, etc.). The way `:features` gates UI on tenant-specific feature presence — never by branching on `tenant.id`.

---

### `TenantContext`

Snapshot type in `:core/tenant/` describing the active tenant: `id`, `displayName`, `regionCode`, `defaultCurrency`, `flags: TenantFlags`, `params: TenantParams`. Resolved once at login from `LoginResponse`. Read by `:features` for **display and parametric gating only** — never `tenant.id` or `tenant.regionCode` for dispatch.

---

### `TenantFlags`

Data class in `:core/tenant/` carrying named boolean fields the server sets per tenant (`hidesEmployeeId`, `clearsEmployeeNumberOnApproval`, …). The client owns the field schema; the server owns the values. New flag = `:core` PR.

---

### `@TenantKey`

Hilt multibinding key annotation in `:core/scope/`. Every concrete tenant module's policy binding uses `@Binds @IntoMap @TenantKey("<region>:<tenantSlug>") @LoggedInScoped`. The runtime resolver lives in `:app/di/TenantResolverModule.kt` and is the **single point of dispatch** — no other code branches on `tenant.id`.

---

### `TenantParams`

Data class in `:core/tenant/` carrying named typed fields (`employeeIdRegex`, `approvalLineMaxDepth`, …). Same client-schema / server-values contract as `TenantFlags`.

---

### `TenantPolicy`

Conceptual term for a `:core/policy/` interface implemented per tenant when parametric `TenantFlags` / `TenantParams` aren't enough — e.g. `ApprovalLineRenderer` with a region-base default impl plus a `ShinsegaeApprovalLineRenderer` override. Lives in `:tenants:{region}:base/policy/` (default) or `:tenants:{region}:{tenantSlug}/policy/` (override). The escalation; not the default.

---

### Two-Layer Foundation

The split between `:aos-sdk` (project-agnostic infrastructure) and `:core` (project-specific contracts). The split exists because infrastructure stability and product-domain stability evolve at different rates. `:aos-sdk` is git-tag-released for multi-product consumption; `:core` is per-project.

---

### Cross-references

- For a top-level summary: [00 — Overview](00-overview.md)
- For the architectural shape that ties these terms together: [01 — Module Topology](01-module-topology.md)
- For the tenant behavioral model: [19 — Tenants and Regions](19-tenants-and-variants.md)
