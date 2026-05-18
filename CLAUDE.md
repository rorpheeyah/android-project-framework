# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Type

This repository contains **architecture documentation only** — there is no source code, build system, tests, or runnable artifacts. It is a specification of the **Bizplay IPPP target architecture**: the multi-tenant, multi-region Android corporate-expense / receipt-management framework that the existing `Bizplay4.0_IPPP` project at `/Users/rorpheeyah/AndroidStudioProjects/Bizplay4.0_IPPP/` is intended to be refactored toward. The current Bizplay codebase is a Java-heavy MVVM monolith with `DetailConfig.isXxx()` per-customer branching; this documentation describes the Kotlin + Compose + Hilt + MVI shape it should grow into. The design is still in progress; doc edits are the primary form of work here.

Consequences for Claude:

- There are no `build`, `lint`, or `test` commands to run. Do not invent Gradle invocations.
- Tasks in this repo are almost always **documentation edits** (`README.md` or `docs/*.md`).
- When asked about implementation specifics ("how is X coded"), answer from the docs and explicitly note that the code does not exist here — do not fabricate file paths under `app/`, `features/`, etc. The Bizplay4.0_IPPP project at the path above is a *reference for the current state*, not a place to point readers for the architecture this repo describes.

## Documentation Layout

Single source of truth is `docs/`, indexed by `README.md`. Reading order matters:

| Doc | Purpose |
|---|---|
| `docs/00-overview.md` | The problem (Conditional Logic Sprawl in `DetailConfig.isXxx()`) and the solution shape. Read first. |
| `docs/01-module-topology.md` | The dependency DAG and **forbidden imports table** — the most load-bearing diagram. |
| `docs/02-aos-core.md` | `:aos-core` — infrastructure (network, security, storage, logging; wraps SQLCipher, TransKey, mVaccine, Secucen EdgeCrypto). |
| `docs/03-core.md` | `:core` — interfaces, domain models, `RuntimeConfig`, `Session`, MVI base. |
| `docs/04-design-system.md` | `:design-system` — theme tokens, primitive Composables (`BizTheme`, `BizButton`, …), Compose helpers. |
| `docs/05-data.md` | `:data` — `Ippp*Api` family (split per feature area: Auth, Receipt, Approval, Card, Expense, Ocr, Notice) + repository implementations (one per `:core` interface). |
| `docs/06-features.md` | `:features` — Hybrid-Monolith UI engine. |
| `docs/07-variants.md` | `:variants-*` — variant silos (policies + DI), plus the variant-unique feature module pattern. |
| `docs/08-app-orchestrator.md` | `:app` — orchestrator, `BootCoordinator`, `LoggedInComponent`, navigation. |
| `docs/09-mvi-pattern.md` | UiState / UiEvent / UiEffect conventions used by every screen. |
| `docs/10-boot-phases.md` | MG → gate → login → SessionGraph build → logout. The highest-leverage mechanism. |
| `docs/11-mg-and-runtime-config.md` | MgGate endpoint contract (real Bizplay endpoint), `RuntimeConfig`, `MaintenanceGate`, force-update gating. |
| `docs/12-departments-and-session.md` | Multi-institution session model; `Session.activeAccountId` (maps to `USE_INTT_ID`); `AccountIdInterceptor`. |
| `docs/13-onboarding-a-variant.md` | Step-by-step checklist for adding a new region variant module. |
| `docs/14-build-performance.md` | Why Hybrid-Monolith for `:features`, recompile-trigger expectations. |
| `docs/15-tech-stack.md` | One-page tech reference (Compose, Hilt, Retrofit, SQLCipher, TransKey, mVaccine, etc.). |
| `docs/16-glossary.md` | Definitions of framework-specific terms. |
| `docs/17-project-structure.md` | Single-page tree of every module's directory layout. |
| `docs/18-webview-integration.md` | `BizWebView` primitives in `:aos-core`, JS bridge contract (replaces today's `BrowserBridge`), URL allowlist via `RuntimeConfig.webRoutes`, cookie sync. |
| `docs/19-tenants-and-variants.md` | Distinguishes **variant** (region/regulator boundary: KR / KH / VN) from **tenant** (corporate-customer boundary inside a variant: POSCO / Lotte / NIA / Shinsegae / ITCen / WIPS / HANA / IBS / SPC / default); `TenantContext` carries `TenantFlags`+`TenantParams`; structural `TenantPolicy` is the escalation when params aren't enough. |

## The Architecture in One Page

The framework defines eight module shapes with a strict dependency DAG. **Editing the docs requires preserving these invariants** — if a doc edit implies a new dependency edge or a new module shape, flag it explicitly rather than slipping it in.

```
infrastructure   :aos-core         (submodule · product-agnostic · no expense / banking terms)
                       ↑
contracts /     :core              :design-system
foundations    (interfaces,        (theme + components,
                models, MVI)        Compose primitives)
                       ↑                ↑
implementations :data ·             :features ·
& UI            :variants-{id}      :features-scanner ·
                                    :features-{feature-name}
                       ↑
orchestrator     :app              (depends on everything; runs the boot sequence)
```

Arrow `↑` reads as "depends on". `:core` and `:design-system` are sibling foundations; neither depends on the other. The implementation/UI siblings never depend on each other.

Five implementation siblings depend on `:core` (and some on `:design-system`) but never on each other:
- `:data` provides the `Ippp*Api` family (split per feature area: `IpppAuthApi`, `IpppReceiptApi`, `IpppApprovalApi`, `IpppCardApi`, `IpppExpenseApi`, `IpppOcrApi`, `IpppNoticeApi`) + repository implementations of `:core` interfaces (one repo per interface). **Variant-agnostic** — the IPPP backend demuxes per user / per company code.
- `:features` provides the Logic-Blind UI engine.
- `:features-scanner` is a sibling UI module isolated for heavy SDK weight (camera + io.card + OCR + scraping). This is the heavy-SDK escape hatch — the same pattern a chatbot module would follow if Bizplay ever adopted one.
- `:features-{feature-name}` are sibling UI modules for variant-locked features (with their own API/DTOs/screens), gated by `VariantCapabilities`. The canonical example is `:features-hipass` — Korea-only highway-toll capture, with its own backend integration that no other variant uses.
- `:variants-{id}` provides variant-specific policies (validation, fees, formatting, capability flags, support contacts, business calendars, receipt rendering, …) + a Hilt module. `:variants-kr`, `:variants-kh`, `:variants-vn` are the three planned variants.

**Forbidden imports** (compile-time rules; doc text must never describe code that violates them):

- `:features`, `:features-scanner`, `:features-{n}` → `:data` or `:variants-*` (breaks Logic-Blind)
- `:features` ↔ `:features-scanner` ↔ `:features-{n}` (sibling UI modules, no cross-references — shared primitives go in `:design-system`)
- `:variants-{A}` → `:variants-{B}` (no cross-variant coupling)
- `:variants-*` → `:data`, `:design-system` (variants are pure policy + DI, no networking, no UI)
- `:data` → `:variants-*`, `:design-system` (data layer is variant-agnostic and headless)
- `:design-system` → `:core`, `:data`, `:features`, `:variants-*` (UI primitives are variant-agnostic and domain-agnostic)
- `:core` → any of `:data`, `:design-system`, `:features`, `:variants-*` (contract layer is upstream, never a consumer)
- `:aos-core` → anything else in the project (must remain product-agnostic — no `Receipt`, `Expense`, `Approval`, `BizDoc`, or variant ID may appear there)

## Hard Invariants to Preserve When Editing Docs

These are the framework's promises. Any doc change that contradicts one is a regression — call it out instead of silently writing it.

1. **No conditional logic on variant ID or tenant ID anywhere.** `if (variantId == "kr")` and `if (tenant.id == "nia")` are equally forbidden. The existing Bizplay codebase's `DetailConfig.isNIA() / isPOSCO_ICT() / isWIPS() / isShinsegae() / …` chains are the *anti-pattern this framework eliminates*. Polymorphism via `:core` interfaces is the answer. `VariantContext` may be read for **display** (currency, name, logo) but never for **dispatch**; `TenantContext` may be read for its **flag/param values** (`tenant.flags.hidesEmployeeId`) but never for **dispatch** on `tenant.id`. UI feature gating uses `VariantCapabilities` or `TenantFlags`.
2. **`:features` is Logic-Blind.** ViewModels depend on `:core` interfaces only. They never name a concrete `:data` repo or `:variants-*` policy class. Onboarding a new variant requires **zero** `:features` edits.
3. **`:aos-core` is product-agnostic.** Any term like `Receipt`, `Expense`, `Approval`, `Card`, `BizDoc`, `Money`, `Variant`, or a variant/tenant ID appearing in `:aos-core` is misplaced — it belongs in `:core` (if it's a contract), `:data` (if it's an impl), or `:variants-*` (if it's variant-specific). `:aos-core` knows about HTTP, encryption, storage, the WebView primitive — nothing about expenses.
4. **One IPPP backend, no per-variant duplication.** The unified server-side IPPP API handles backend routing per user and per company code. Inside `:data`, the Retrofit surface is split by feature area (`IpppAuthApi`, `IpppReceiptApi`, `IpppApprovalApi`, `IpppCardApi`, …) for ergonomics — but there is one set of DTOs and one repository implementation per `:core` interface, regardless of variant. Doc edits proposing per-variant Retrofit interfaces (e.g. `KrKakaoPayApi`, `KhLocalRailApi`), per-variant DTOs, or per-variant repo classes are regressions.
5. **Variant modules contain only policies + DI.** No UI, no networking, no DTOs, no repositories. Variant-unique features (with their own API, DTOs, or screens) go in dedicated `:features-{name}` modules sibling to `:features` (mirroring `:features-scanner`), gated by `VariantCapabilities` for navigation. Variant-unique work never lives in `:variants-{id}` (which stays policies + DI), `:data`, or `:core`. If a variant module's structure proposed in a doc edit includes `api/`, `repo/`, or Compose files, flag it.
6. **`:design-system` is variant-agnostic and domain-agnostic.** No expense/receipt types, no `:core` dependency. Theme tokens and primitive Composables only (`BizTheme`, `BizButton`, `BizTextField`, `BizDialog`, `BizWebView`, …). Doc edits proposing tenant-specific or variant-specific styling inside `:design-system` are regressions; per-tenant theming (e.g., POSCO red vs Lotte navy) is a future-roadmap concern that would be supplied through a separate mechanism reading `TenantContext` at the `:app` level.
7. **The variant and tenant bind once at login and stay.** `LoggedInComponent` is built once with the user's `VariantContext` and `TenantContext` (plus `:data`'s repo bindings, the active `:variants-{id}`'s policy bindings, and any per-tenant structural policy bindings) and dropped on logout. There is no runtime swap, no purge sequence. Variant or tenant change in production means logout-then-login. The existing Bizplay multi-company picker (`SelectUserInttIdActivity`) covers a different axis — selecting which institution membership is *active* — handled by `Session.activeAccountId`, not a DI rebuild.
8. **Only one URL is hardcoded: MG.** The MG URL per build environment is the *only* network configuration baked into the binary. The existing Bizplay project already has this shape (`Conf.SITE_MG_URL + "/MgGate"`) — the framework keeps it. Main API URLs (today: `Conf.IPPP_SITE_URL`), maintenance state, and version floors all come from MG's `RuntimeConfig`. Doc text that proposes hardcoding a per-environment URL table for IPPP endpoints is a regression.
9. **Departments are accounts, not sub-variants.** A logged-in user can hold multiple `DepartmentAccount`s — these map to the existing Bizplay `USE_INTT_ID` axis (multiple institutions / companies per user, picked at login). They all share the same `LoggedInComponent`. The active account is `Session.activeAccountId` (a `StateFlow`), stamped on requests by `AccountIdInterceptor` (sets the `USE_INTT_ID` + `COMPANY_CD` request fields). Don't model them as nested DI graphs.
10. **`:features` is a Hybrid-Monolith on purpose.** Feature boundaries are **packages**, not Gradle modules. Adding a new flow (e.g. a new expense type) means a new package under `:features/`. The exception is heavy-SDK features (scanner is the reference example — io.card + camera + OCR + sasapi scraping) and variant-locked features with their own API/DTOs/screens (`:features-hipass` for Korea-only highway tolls) — those become sibling modules.
11. **Onboarding a new variant is strictly additive.** New module + one `include(":variants-X")` line + `VariantCatalogue` entry. If a variant onboarding doc/PR touches `:features`, `:data`, `:design-system`, or sibling variants, the architecture has been violated.
12. **Tenants are an axis inside variants, not separate modules.** Per-corporate-customer differences (the POSCO / Lotte / NIA / Shinsegae / ITCen / WIPS / HANA / IBS / SPC axis that the existing `DetailConfig.isXxx()` represents) live in `TenantContext` (immutable, captured at login from `LoginResponse`) carrying `TenantFlags` (named booleans) and `TenantParams` (named typed fields). UI reads `tenant.flags.*` / `tenant.params.*` — never `tenant.id` for dispatch. Structural escalation is a `:core/policy/` interface with per-tenant impls inside `:variants-{region}/tenants/{id}/`. The client owns the field schema; the server (MgGate + login response) owns the values — no hardcoded per-tenant tables, no `Map<String, Any>` backdoors. Every variant ships a `default` tenant. Onboarding a tenant is additive (one subdirectory + one `TenantCatalogue` entry); environments are buildTypes, not tenants.

## Naming and Style Conventions Used Across Docs

Be consistent with these; they're load-bearing for grep:

- Module references use the Gradle path with leading colon: `:aos-core`, `:core`, `:design-system`, `:data`, `:features`, `:features-scanner`, `:variants-kr`, `:app`.
- Variant module directory names are flat siblings, kebab-case: `variants-<id>` (so `variants-kr/`, `variants-kh/`, `variants-vn/`). Mirrors `features-scanner`.
- Variant-locked feature modules follow the same flat sibling pattern: `:features-{feature-name}` (default) or `:features-{variant}-{feature-name}` if structurally locked.
- Custom Hilt scope is `@LoggedInScoped` (defined in `:core/scope/`); the component is `LoggedInComponent`; the orchestrator is `BootCoordinator`; the lifecycle owner is `LoggedInComponentManager`.
- The MG client is `MgClient` (existing Bizplay endpoint: `MgGate`); the typed payload is `RuntimeConfig`; the gate Composables are `MaintenanceGate` and `ForceUpdateGate`.
- The session type is `Session` (in `:core`); accounts are `DepartmentAccount` (each backed by an institution / `USE_INTT_ID`); the OkHttp interceptor that stamps the active account ID is `AccountIdInterceptor`.
- The variant types in `:core` are `VariantContext`, `VariantId`, `VariantCapabilities` (no other names).
- The variant binding map key is `@VariantKey("<id>")` (defined in `:core/scope/`). Every `:variants-{id}` policy binding uses `@Binds @IntoMap @VariantKey("<id>") @LoggedInScoped`. The runtime resolver lives in `:app/di/VariantResolverModule.kt` and is the **single point of dispatch** for variant-specific policies — no other code branches on `variant.id`.
- The unified API surface is the `Ippp*Api` family (in `:data`), split per feature area: `IpppAuthApi`, `IpppReceiptApi`, `IpppApprovalApi`, `IpppCardApi`, `IpppExpenseApi`, `IpppOcrApi`, `IpppNoticeApi`. There is no `KrKakaoPay*Api`, `KhLocal*Api`, etc. — those are server-side concerns.
- Repository implementations are named `Ippp<Area>Repo` (e.g. `IpppReceiptRepo`, `IpppAuthRepo`, `IpppApprovalRepo`) — one per `:core` repository interface, never per-variant.
- Design-system primitives are `Biz<Component>` (e.g. `BizButton`, `BizTheme`, `BizWebView`, `BizDialog`); they live in `com.bizplay.design.*`. This mirrors the existing Bizplay convention (`BizWebview`, `BizLocationManager`).
- MVI suffix convention per screen: `*Screen.kt`, `*ViewModel.kt`, `*Contract.kt` (where the contract file holds the screen's `UiState`, `UiEvent`, and `UiEffect` sealed types).
- Internal Kotlin visibility (`internal`) is the default for `:data` and `:variants-*` classes; the **only** intentionally public surface is each module's Hilt `@Module`. `:design-system` is the exception: its components are intentionally public.
- Cross-references between docs use relative links (e.g., `[10 — Boot Phases](10-boot-phases.md)`). Preserve this style when adding new cross-references.

## Mapping from Current Bizplay to Target Framework

When docs reference the *current* Bizplay reality vs the *target* architecture, use this table:

| Current Bizplay 4.0 IPPP | Target framework |
|---|---|
| `app/src/main/java/com/bizcard/bizplayPPPEnt/ui/<feature>/` (Activities, Fragments, ViewModels) | Packages inside `:features/<feature>/` (Compose screens + MVI ViewModels) |
| `ComTran` networking wrapper + `mComTran.requestData(tranCode, url, params, listener)` | Retrofit `Ippp*Api` interfaces + suspend functions in `:data/api/` |
| `*_REQ` / `*_RES` / `*_REC` model classes | DTOs in `:data/api/dto/<area>/` + domain types in `:core/model/` |
| `Constant.MG.C_*_URL` keys read via `MemoryPreferenceDelegator` | `RuntimeConfig.urls.*` + `RuntimeConfig.webRoutes["…"]` |
| `IntroViewModel.requestMG()` cold-start fetch | `BootCoordinator.runBoot()` + `MgClient.fetch()` |
| `DetailConfig.isNIA() / isPOSCO_ICT() / isWIPS() / isShinsegae() / isLotte() / …` | `TenantFlags` named fields read from `TenantContext.flags` (or a structural `TenantPolicy` for layout-level differences) |
| `USE_INTT_ID` request field + `SelectUserInttIdActivity` flow | `Session.activeAccountId: StateFlow<AccountId>` + `AccountIdInterceptor` |
| `COMPANY_CD` request field | Stamped by `AccountIdInterceptor` from the active `DepartmentAccount` |
| `BizWebview` + `BrowserBridge` JS interface | `BizWebView` in `:aos-core/webview/` + `WebActionBridge` (one `@JavascriptInterface` method, versioned payload) |
| `TransKeyManager` (mtk SDK) | Wrapped behind `:aos-core/security/SecureKeypad` |
| `mVaccine` (core_b2b2c jar) | Wrapped behind `:aos-core/security/SecurityProvider` (cold-start abort on malware detection) |
| `SQLCipher` direct usage + `AsyncGenerateCipherDatabase` | Wrapped behind `:aos-core/storage/EncryptedDatabase` |
| `Secucen lib` (`libEdgeCrypto.so`) | Wrapped behind `:aos-core/security/EdgeCrypto` |
| `io.card` scanner + `cameraviewplus` + `sasapi` scraping | Live inside `:features-scanner` (heavy-SDK isolation) |
| `ui/receipt/hi_pass/` (Korea-only Hi-Pass toll) | `:features-hipass` (variant-locked feature module) |
| `LocaleHelper` + 5 string resource sets (KO/EN/VI/JA/ZH) | Still a `Locale` concern; the **regulator/rail** axis becomes `VariantId` (KR / KH / VN) separately |

This table is the rosetta stone: when answering "how do I do X today vs how should I do X in the target architecture", consult it first.

## Glossary Pointer

When introducing a framework-specific term in any doc, first check `docs/16-glossary.md`. Reuse the existing term verbatim; if a new term is genuinely needed, add a glossary entry in the same PR.
