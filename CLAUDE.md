# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Type

This repository contains **architecture documentation only** — there is no source code, build system, tests, or runnable artifacts. It is a specification of the **Compass Framework**: a multi-tenant Android banking architecture intended to be implemented in another repository (or repositories) that consume `:aos-sdk` as a Git submodule pinned to a tagged release. The design is still in progress; doc edits are the primary form of work here.

Consequences for Claude:

- There are no `build`, `lint`, or `test` commands to run. Do not invent Gradle invocations.
- Tasks in this repo are almost always **documentation edits** (`README.md` or `docs/*.md`).
- When asked about implementation specifics ("how is X coded"), answer from the docs and explicitly note that the code does not exist here — do not fabricate file paths under `app/`, `features/`, etc.

## Historical Note (Architectural Migration In Progress)

Earlier iterations of this framework used:
- `:aos-core` as the infrastructure submodule name (now **`:aos-sdk`** — versioned, git-tagged for multi-product consumption)
- A two-axis DI dispatch: `VariantContext` (region/regulator) + `TenantContext` (org-within-region)

That two-axis model has been **collapsed into a single tenant axis** with a regional Gradle module hierarchy (`:tenants:{region}:base` ← `:tenants:{region}:{tenantSlug}`). See `docs/19 § 12` for the rationale. The `VariantContext` / `VariantId` / `VariantCapabilities` / `@VariantKey` types no longer exist; the `:variants-{id}` flat-sibling convention has been replaced with `:tenants:{region}:{tenantSlug}` hierarchy.

Some doc files still use legacy filenames (`docs/07-variants.md`, `docs/13-onboarding-a-variant.md`, `docs/19-tenants-and-variants.md`) for cross-reference stability; their content has been rewritten for the new model.

## Documentation Layout

Single source of truth is `docs/`, indexed by `README.md`. Reading order matters:

| Doc | Purpose |
|---|---|
| `docs/00-overview.md` | The problem (Conditional Logic Sprawl) and the solution shape. Read first. |
| `docs/01-module-topology.md` | The dependency DAG and **forbidden imports table** — the most load-bearing diagram. |
| `docs/02-aos-core.md` | `:aos-sdk` — infrastructure (network, security, storage, logging). (Filename pending rename.) |
| `docs/03-core.md` | `:core` — interfaces, domain models, `RuntimeConfig`, `Session`, MVI base. |
| `docs/04-design-system.md` | `:design-system` — theme tokens, primitive Composables, Compose helpers. |
| `docs/05-data.md` | `:data` — `Fintech*Api` family (split per feature area) + repository implementations (one per `:core` interface). |
| `docs/06-features.md` | `:features` — Hybrid-Monolith UI engine. |
| `docs/07-variants.md` | `:tenants:*` — tenant silos and region bases (policies + DI), plus the tenant-unique feature module pattern. (Filename pending rename.) |
| `docs/08-app-orchestrator.md` | `:app` — orchestrator, `BootCoordinator`, `LoggedInComponent`, navigation. |
| `docs/09-mvi-pattern.md` | UiState / UiEvent / UiEffect conventions used by every screen. |
| `docs/10-boot-phases.md` | MG → gate → login → SessionGraph build → logout. The highest-leverage mechanism. |
| `docs/11-mg-and-runtime-config.md` | MG endpoint contract, `RuntimeConfig`, `MaintenanceGate`, force-update gating, stale-config fallback. |
| `docs/12-departments-and-session.md` | Multi-account session model; `Session.activeAccountId`; `AccountIdInterceptor`. |
| `docs/13-onboarding-a-variant.md` | Step-by-step checklist for adding a new tenant or a new region. (Filename pending rename.) |
| `docs/14-build-performance.md` | Why Hybrid-Monolith for `:features`, recompile-trigger expectations. |
| `docs/15-tech-stack.md` | One-page tech reference (Compose, Hilt, Retrofit, etc.). |
| `docs/16-glossary.md` | Definitions of framework-specific terms. |
| `docs/17-project-structure.md` | Single-page tree of every module's directory layout. |
| `docs/18-webview-integration.md` | WebView primitives in `:aos-sdk`, JS bridge contract, URL allowlist via `RuntimeConfig.webRoutes`, cookie sync. |
| `docs/19-tenants-and-variants.md` | The tenant model: `TenantContext` carries `TenantFlags`+`TenantParams`; structural `TenantPolicy` is the escalation when params aren't enough; region is a Gradle module hierarchy, not a DI axis. (Filename pending rename.) |
| `docs/20-chat.md` | Customer ↔ loan officer chat. Sendbird buy; provider-agnostic `ChatRepository` in `:core`; `:features-support-chat` sibling UI module. |
| `docs/21-push-channels.md` | FCM with `NotificationChannel` registry: `reminder` / `transaction` / `announcement`. Per-channel importance, POST_NOTIFICATIONS runtime permission, deeplink payload routing. |
| `docs/22-deeplinks.md` | App Links (HTTPS verified) + Compose Nav DSL. Guarantor SMS link, payment deeplink, push tap-through. Signed JWT for security-sensitive flows. |
| `docs/23-kyc-capture.md` | In-house eKYC: CameraX + ML Kit primitives in `:aos-sdk/{camera,ml,imaging}/`, KYC flow in `:features-kyc`. Provider-polymorphic via `KycRepository` for future vendor swap. |
| `docs/24-pdf.md` | Loan-contract download + in-app preview. OkHttp streaming + WorkManager retry + app-private storage; `PdfRenderer` for preview; `FileProvider` for share. |
| `docs/25-locale.md` | Runtime locale switching (KR/EN/KH). `AppCompatDelegate.setApplicationLocales`, `LocaleConfig.xml`, bundled Noto fonts. **User-facing strings live in `strings.xml`, NEVER in tenant policy classes.** |
| `docs/26-pin-and-session.md` | 4-digit PIN UX (HMAC hash, brute-force lockout, biometric-bound tier, OTP recovery); inactivity-driven session timeout via `SessionTimeoutPolicy` + `InactivityDetector`. |
| `docs/27-maps-and-location.md` | Branch locator via Google Maps Compose. Offline-cacheable branch list. Location permission at point-of-use, not cold start. |
| `docs/28-background-work.md` | WorkManager with Hilt-integrated `WorkerFactory`. Tag-by-userId for logout safety. Workers: KYC upload, contract download, MG retry, FCM token refresh, draft sync. |
| `docs/29-local-database.md` | Room + SQLCipher for chat history, drafts, branch cache, notification inbox. Per-feature DBs share encryption via `:aos-sdk/storage/EncryptedDatabase`. NOT for auth tokens (those stay in `EncryptedPrefs`). |
| `docs/30-form-wizard.md` | Multi-step apply-loan flows (9-step NON-MWL, 18-step MWL). `:core/wizard/` contract, NavGraph-scoped ViewModel, draft auto-persistence to `drafts.db`. |

## The Architecture in One Page

The framework defines a strict dependency DAG. **Editing the docs requires preserving these invariants** — if a doc edit implies a new dependency edge or a new module shape, flag it explicitly rather than slipping it in.

```
infrastructure   :aos-sdk          (submodule · banking-agnostic · no banking terms · git-tag-released)
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

Arrow `↑` reads as "depends on". `:core` and `:design-system` are sibling foundations; neither depends on the other. The implementation/UI siblings never depend on each other — except the **one** allowed intra-tier edge: concrete tenant modules depend on their own region base.

Implementation siblings depend on `:core` (and some on `:design-system`) but never on each other:
- `:data` provides the `Fintech*Api` family (split per feature area: `FintechAuthApi`, `FintechLoanApi`, …) + repository implementations of `:core` interfaces (one repo per interface). **Tenant-agnostic** — the server demuxes per user.
- `:features` provides the Logic-Blind UI engine.
- `:features-chatbot` is a sibling UI module isolated for heavy SDK weight.
- `:features-{feature-name}` are sibling UI modules for tenant-locked features (with their own API/DTOs/screens), gated by `TenantCapabilities`. Examples: `:features-kyc`, `:features-support-chat`, `:features-branch-locator`.
- `:tenants:{region}:base` provides regulator/region-wide policies (currency formatting, regulator compliance thresholds, OTP delivery channel, holiday calendar, KYC requirements). One per region.
- `:tenants:{region}:{tenantSlug}` provides concrete-tenant policies (organization-specific flags, params, structural overrides) + a Hilt module that declares `@TenantKey("<region>:<tenantSlug>")` bindings. Depends on its own region base.

**Forbidden imports** (compile-time rules; doc text must never describe code that violates them):

- `:features`, `:features-chatbot`, `:features-{n}` → `:data` or `:tenants:*:*` (breaks Logic-Blind)
- `:features` ↔ `:features-chatbot` ↔ `:features-{n}` (sibling UI modules, no cross-references — shared primitives go in `:design-system`)
- `:tenants:{regionA}:{tenantX}` → `:tenants:{regionA}:{tenantY}` (no cross-tenant coupling within a region)
- `:tenants:{regionA}:*` → `:tenants:{regionB}:*` (no cross-region coupling at any level; promote to `:core` if truly shared)
- `:tenants:{region}:base` → its own concrete tenants (would create a cycle)
- `:tenants:*:*` → `:data`, `:design-system` (tenant modules are pure policy + DI, no networking, no UI)
- `:data` → `:tenants:*:*`, `:design-system` (data layer is tenant-agnostic and headless)
- `:design-system` → `:core`, `:data`, `:features`, `:tenants:*:*` (UI primitives are tenant-agnostic and domain-agnostic)
- `:core` → any of `:data`, `:design-system`, `:features`, `:tenants:*:*` (contract layer is upstream, never a consumer)
- `:aos-sdk` → anything else in the project (must remain product-agnostic; reusable across multiple Android products via git tag)

**Mandatory edge:** every `:tenants:{region}:{tenantSlug}` **must declare** Gradle dependency on `:tenants:{region}:base`. Forgetting this dependency means Hilt resolution of regional baseline policies will fail at boot.

## Hard Invariants to Preserve When Editing Docs

These are the framework's promises. Any doc change that contradicts one is a regression — call it out instead of silently writing it.

1. **No conditional logic on tenant ID anywhere.** `if (tenant.id == "cambodia:nh")` or `when (tenant.id)` outside `:app/di/TenantResolverModule.kt` is forbidden. Same applies to `tenant.regionCode` — it's informational, not for dispatch. Polymorphism via `:core` interfaces is the answer. `TenantContext` may be read for its **flag/param values** (`tenant.flags.hidesEmployeeId`) and for **display** (`tenant.displayName`, `tenant.defaultCurrency`) but never for **dispatch** on `tenant.id`. UI feature gating uses `TenantCapabilities` or `TenantFlags`.
2. **`:features` is Logic-Blind.** ViewModels depend on `:core` interfaces only. They never name a concrete `:data` repo or a `:tenants:*:*` policy class. Onboarding a new tenant requires **zero** `:features` edits.
3. **`:aos-sdk` is banking-agnostic.** Any term like `Account`, `Money`, `Transfer`, `Loan`, `Tenant`, or a tenant ID appearing in `:aos-sdk` is misplaced — it belongs in `:core` (if it's a contract), `:data` (if it's an impl), or `:tenants:*:*` (if it's tenant-specific). `:aos-sdk` is consumed by multiple Android products via git-tagged releases; banking terminology in the SDK would force every consumer to also be a bank.
4. **One primary fintech backend, no per-tenant duplication.** The unified server-side fintech API handles backend routing per user. Inside `:data`, the Retrofit surface is split by feature area (`FintechAuthApi`, `FintechLoanApi`, `FintechRepaymentApi`, …) for ergonomics — but there is one set of DTOs and one repository implementation per `:core` interface, regardless of tenant. Third-party integrations (CBC, MWL agency, bank-statement analyzer) get typed clients in `:data/external/` but **never branch by tenant**. Doc edits proposing per-tenant Retrofit interfaces, per-tenant DTOs, or per-tenant repo classes are regressions.
5. **Tenant modules contain only policies + DI.** No UI, no networking, no DTOs, no repositories. Region-base modules have the same constraint. Tenant-unique features (with their own API, DTOs, or screens) go in dedicated `:features-{name}` modules sibling to `:features` (mirroring `:features-chatbot`), gated by `TenantCapabilities` for navigation. Tenant-unique work never lives in `:tenants:*:*` (which stays policies + DI), `:data`, or `:core`. If a tenant module's structure proposed in a doc edit includes `api/`, `repo/`, or Compose files, flag it.
6. **`:design-system` is tenant-agnostic and domain-agnostic.** No banking types, no `:core` dependency. Theme tokens and primitive Composables only. User-facing display strings (KR/EN/KH) live in `strings.xml` localized resources, **not** in tenant policy classes. Doc edits proposing tenant-specific styling inside `:design-system` are regressions; per-tenant theming is a future-roadmap concern that would be supplied through a separate mechanism.
7. **The tenant binds once at login and stays.** `LoggedInComponent` is built once with the user's `TenantContext` (plus `:data`'s repo bindings and the active tenant's policy bindings) and dropped on logout. There is no runtime swap, no purge sequence. Tenant change in production means logout-then-login.
8. **Only one URL is hardcoded: MG.** The MG URL per build environment is the *only* network configuration baked into the binary. Main API URLs, third-party SDK app-ids (Sendbird, Google Maps), maintenance state, and version floors all come from MG's `RuntimeConfig`. Doc text that proposes hardcoding a `production.json` of base URLs is a regression. **One narrow exception:** `staleConfigTtl` (the duration of the MG fallback window) is a single BuildConfig constant with a sane default (24h), required to bootstrap the fallback before MG is reachable.
9. **Departments are accounts, not sub-tenants.** Multiple `DepartmentAccount`s under one logged-in user share the same `LoggedInComponent`. The active account is `Session.activeAccountId` (a `StateFlow`), stamped on requests by `AccountIdInterceptor`. Don't model them as nested DI graphs. Single-account consuming apps (e.g., the lending PRD) ship with `accounts.size == 1` and hide the switcher UI declaratively (`if (accounts.size > 1) ...`); the infrastructure remains for multi-account outsource projects.
10. **`:features` is a Hybrid-Monolith on purpose.** Feature boundaries are **packages**, not Gradle modules. Adding a new flow means a new package under `:features/`. The exceptions are heavy-SDK features (chatbot, KYC, support chat, branch locator, etc.) and tenant-locked features with their own API/DTOs/screens — those become sibling `:features-{name}` modules.
11. **Onboarding a new tenant is strictly additive.** New module under `:tenants:{region}:{tenantSlug}` + one `include(":tenants:{region}:{tenantSlug}")` line + `TenantCatalogue` entry. Onboarding a new region adds the region-base + `default` tenant + at least one concrete tenant. If a tenant onboarding doc/PR touches `:aos-sdk`, `:core`, `:data`, `:design-system`, `:features`, sibling concrete tenants (same or other region), or other region bases, the architecture has been violated.
12. **Region is a Gradle module hierarchy, not a DI axis.** Per-region differences live in `:tenants:{region}:base` (currency, regulator rules, calendar, OTP channel). Per-organization differences live in `:tenants:{region}:{tenantSlug}` via `TenantContext`. `TenantContext` carries `TenantFlags` (named booleans), `TenantParams` (named typed fields), `regionCode` (informational only), and `displayName`. UI reads `tenant.flags.*` / `tenant.params.*` — never `tenant.id` or `tenant.regionCode` for dispatch. Structural escalation is a `:core/policy/` interface with per-tenant impls inside `:tenants:{region}:{tenantSlug}/policy/`. The client owns the field schema; the server owns the values — no hardcoded per-tenant tables, no `Map<String, Any>` backdoors. Every region ships a `:tenants:{region}:default` module (for tests and the no-overrides baseline; never resolves in production). Environments are buildTypes, not tenants.

## Naming and Style Conventions Used Across Docs

Be consistent with these; they're load-bearing for grep:

- Module references use the Gradle path with leading colon: `:aos-sdk`, `:core`, `:design-system`, `:data`, `:features`, `:features-chatbot`, `:tenants:cambodia:nh`, `:app`.
- Tenant module paths follow the hierarchy: `:tenants:{region}:{tenantSlug}` (Gradle path) or `tenants/{region}/{tenantSlug}/` (filesystem). Region bases: `:tenants:{region}:base`. Every region ships a `:tenants:{region}:default`.
- Tenant-locked feature modules follow the flat sibling pattern: `:features-{feature-name}` (default) or `:features-{tenant}-{feature-name}` if structurally locked.
- Custom Hilt scope is `@LoggedInScoped` (defined in `:core/scope/`); the component is `LoggedInComponent`; the orchestrator is `BootCoordinator`; the lifecycle owner is `LoggedInComponentManager`.
- The MG client is `MgClient`; the typed payload is `RuntimeConfig`; the gate Composables are `MaintenanceGate` and `ForceUpdateGate`.
- The session type is `Session` (in `:core`); accounts are `DepartmentAccount`; the OkHttp interceptor that stamps the active account ID is `AccountIdInterceptor`.
- The tenant types in `:core` are `TenantContext`, `TenantId`, `TenantFlags`, `TenantParams`, `TenantCapabilities`. `TenantId.value` is a composite `<region>:<tenantSlug>` string. **There are no `VariantContext` / `VariantId` / `VariantCapabilities` / `VariantKey` types** — those were removed in the variant collapse.
- The tenant binding map key is `@TenantKey("<region>:<tenantSlug>")` (defined in `:core/scope/`). Every `:tenants:{region}:{tenantSlug}` policy binding uses `@Binds @IntoMap @TenantKey("<region>:<tenantSlug>") @LoggedInScoped`. The runtime resolver lives in `:app/di/TenantResolverModule.kt` and is the **single point of dispatch** for tenant-specific policies — no other code branches on `tenant.id`.
- The unified API surface is the `Fintech*Api` family (in `:data`), split per feature area: `FintechAuthApi`, `FintechLoanApi`, `FintechRepaymentApi`, etc. There is no per-tenant Retrofit interface. Third-party integrations live in `:data/external/`.
- Repository implementations are named `Fintech<Area>Repo` or `<Area>Repo` — one per `:core` repository interface, never per-tenant.
- Design-system primitives are `Compass<Component>` (e.g. `CompassButton`, `CompassTheme`); they live in `com.<org>.design.*`.
- MVI suffix convention per screen: `*Screen.kt`, `*ViewModel.kt`, `*Contract.kt` (where the contract file holds the screen's `UiState`, `UiEvent`, and `UiEffect` sealed types).
- Internal Kotlin visibility (`internal`) is the default for `:data` and `:tenants:*:*` classes; the **only** intentionally public surface is each module's Hilt `@Module`. `:design-system` is the exception: its components are intentionally public.
- Cross-references between docs use relative links (e.g., `[10 — Boot Phases](10-boot-phases.md)`). Preserve this style when adding new cross-references.

## Glossary Pointer

When introducing a framework-specific term in any doc, first check `docs/16-glossary.md`. Reuse the existing term verbatim; if a new term is genuinely needed, add a glossary entry in the same PR. **Do not introduce `variant`, `VariantContext`, `VariantId`, `VariantKey`, `:variants-*`** in any new doc text — those names were removed in the architectural collapse and reintroducing them would re-create the two-axis ambiguity. Use `tenant`, `region`, `TenantContext`, `:tenants:{region}:base`, `:tenants:{region}:{tenantSlug}` instead.
