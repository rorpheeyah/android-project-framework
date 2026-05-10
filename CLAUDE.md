# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Type

This repository contains **architecture documentation only** — there is no source code, build system, tests, or runnable artifacts. It is a specification of the **Compass Framework**: a multi-region Android banking architecture intended to be implemented in another repository (or repositories) that consume `:aos-core` as a Git submodule. The design is still in progress; doc edits are the primary form of work here.

Consequences for Claude:

- There are no `build`, `lint`, or `test` commands to run. Do not invent Gradle invocations.
- Tasks in this repo are almost always **documentation edits** (`README.md` or `docs/*.md`).
- When asked about implementation specifics ("how is X coded"), answer from the docs and explicitly note that the code does not exist here — do not fabricate file paths under `app/`, `features/`, etc.

## Documentation Layout

Single source of truth is `docs/`, indexed by `README.md`. Reading order matters:

| Doc | Purpose |
|---|---|
| `docs/00-overview.md` | The problem (Conditional Logic Sprawl) and the solution shape. Read first. |
| `docs/01-module-topology.md` | The dependency DAG and **forbidden imports table** — the most load-bearing diagram. |
| `docs/02-aos-core.md` | `:aos-core` — infrastructure (network, security, storage, logging). |
| `docs/03-core.md` | `:core` — interfaces, domain models, `RuntimeConfig`, `Session`, MVI base. |
| `docs/04-design-system.md` | `:design-system` — theme tokens, primitive Composables, Compose helpers. |
| `docs/05-data.md` | `:data` — `Fintech*Api` family (split per feature area) + repository implementations (one per `:core` interface). |
| `docs/06-features.md` | `:features` — Hybrid-Monolith UI engine. |
| `docs/07-variants.md` | `:variants-*` — variant silos (policies + DI), plus the variant-unique feature module pattern. |
| `docs/08-app-orchestrator.md` | `:app` — orchestrator, `BootCoordinator`, `LoggedInComponent`, navigation. |
| `docs/09-mvi-pattern.md` | UiState / UiEvent / UiEffect conventions used by every screen. |
| `docs/10-boot-phases.md` | MG → gate → login → SessionGraph build → logout. The highest-leverage mechanism. |
| `docs/11-mg-and-runtime-config.md` | MG endpoint contract, `RuntimeConfig`, `MaintenanceGate`, force-update gating. |
| `docs/12-departments-and-session.md` | Multi-account session model; `Session.activeAccountId`; `AccountIdInterceptor`. |
| `docs/13-onboarding-a-variant.md` | Step-by-step checklist for adding a new variant module. |
| `docs/14-build-performance.md` | Why Hybrid-Monolith for `:features`, recompile-trigger expectations. |
| `docs/15-tech-stack.md` | One-page tech reference (Compose, Hilt, Retrofit, etc.). |
| `docs/16-glossary.md` | Definitions of framework-specific terms. |
| `docs/17-project-structure.md` | Single-page tree of every module's directory layout. |

## The Architecture in One Page

The framework defines eight module shapes with a strict dependency DAG. **Editing the docs requires preserving these invariants** — if a doc edit implies a new dependency edge or a new module shape, flag it explicitly rather than slipping it in.

```
infrastructure   :aos-core         (submodule · banking-agnostic · no banking terms)
                       ↑
contracts /     :core              :design-system
foundations    (interfaces,        (theme + components,
                models, MVI)        Compose primitives)
                       ↑                ↑
implementations :data ·             :features ·
& UI            :variants-{id}      :features-chatbot ·
                                    :features-{feature-name}
                       ↑
orchestrator     :app              (depends on everything; runs the boot sequence)
```

Arrow `↑` reads as "depends on". `:core` and `:design-system` are sibling foundations; neither depends on the other. The implementation/UI siblings never depend on each other.

Five implementation siblings depend on `:core` (and some on `:design-system`) but never on each other:
- `:data` provides the `Fintech*Api` family (split per feature area: `FintechAuthApi`, `FintechTransferApi`, `FintechAccountApi`, …) + repository implementations of `:core` interfaces (one repo per interface). **Variant-agnostic** — the server demuxes per user.
- `:features` provides the Logic-Blind UI engine.
- `:features-chatbot` is a sibling UI module isolated for heavy SDK weight.
- `:features-{feature-name}` are sibling UI modules for variant-locked features (with their own API/DTOs/screens), gated by `VariantCapabilities`.
- `:variants-{id}` provides variant-specific policies (validation, fees, formatting, capability flags, support contacts, business calendars, …) + a Hilt module.

**Forbidden imports** (compile-time rules; doc text must never describe code that violates them):

- `:features`, `:features-chatbot`, `:features-{n}` → `:data` or `:variants-*` (breaks Logic-Blind)
- `:features` ↔ `:features-chatbot` ↔ `:features-{n}` (sibling UI modules, no cross-references — shared primitives go in `:design-system`)
- `:variants-{A}` → `:variants-{B}` (no cross-variant coupling)
- `:variants-*` → `:data`, `:design-system` (variants are pure policy + DI, no networking, no UI)
- `:data` → `:variants-*`, `:design-system` (data layer is variant-agnostic and headless)
- `:design-system` → `:core`, `:data`, `:features`, `:variants-*` (UI primitives are variant-agnostic and domain-agnostic)
- `:core` → any of `:data`, `:design-system`, `:features`, `:variants-*` (contract layer is upstream, never a consumer)
- `:aos-core` → anything else in the project (must remain product-agnostic)

## Hard Invariants to Preserve When Editing Docs

These are the framework's promises. Any doc change that contradicts one is a regression — call it out instead of silently writing it.

1. **No conditional logic on variant ID anywhere.** `if (variantId == "kh")` is never the answer. Polymorphism via `:core` interfaces is. `VariantContext` may be read for **display** (currency, name, logo) but never for **dispatch**. UI feature gating uses `VariantCapabilities` flags.
2. **`:features` is Logic-Blind.** ViewModels depend on `:core` interfaces only. They never name a concrete `:data` repo or `:variants-*` policy class. Onboarding a new variant requires **zero** `:features` edits.
3. **`:aos-core` is banking-agnostic.** Any term like `Account`, `Money`, `Transfer`, `Variant`, or a variant ID appearing in `:aos-core` is misplaced — it belongs in `:core` (if it's a contract), `:data` (if it's an impl), or `:variants-*` (if it's variant-specific).
4. **One fintech backend, no per-variant duplication.** The unified server-side fintech API handles backend routing per user. Inside `:data`, the Retrofit surface is split by feature area (`FintechAuthApi`, `FintechTransferApi`, `FintechAccountApi`, …) for ergonomics — but there is one set of DTOs and one repository implementation per `:core` interface, regardless of variant. Doc edits proposing per-variant Retrofit interfaces (e.g. `KhBakongApi`, `VnNapasApi`), per-variant DTOs, or per-variant repo classes are regressions.
5. **Variant modules contain only policies + DI.** No UI, no networking, no DTOs, no repositories. Variant-unique features (with their own API, DTOs, or screens) go in dedicated `:features-{name}` modules sibling to `:features` (mirroring `:features-chatbot`), gated by `VariantCapabilities` for navigation. Variant-unique work never lives in `:variants-{id}` (which stays policies + DI), `:data`, or `:core`. If a variant module's structure proposed in a doc edit includes `api/`, `repo/`, or Compose files, flag it.
6. **`:design-system` is variant-agnostic and domain-agnostic.** No banking types, no `:core` dependency. Theme tokens and primitive Composables only. Doc edits proposing variant-specific styling inside `:design-system` are regressions; per-variant theming is a future-roadmap concern that would be supplied through a separate mechanism.
7. **The variant binds once at login and stays.** `LoggedInComponent` is built once with the user's variant context (and `:data`'s repo bindings + the active `:variants-{id}`'s policy bindings) and dropped on logout. There is no runtime swap, no purge sequence. Variant change in production means logout-then-login.
8. **Only one URL is hardcoded: MG.** The MG URL per build environment is the *only* network configuration baked into the binary. Main API URLs, maintenance state, and version floors all come from MG's `RuntimeConfig`. Doc text that proposes hardcoding a `production.json` of base URLs is a regression.
9. **Departments are accounts, not sub-variants.** Multiple `DepartmentAccount`s under one logged-in user share the same `LoggedInComponent`. The active account is `Session.activeAccountId` (a `StateFlow`), stamped on requests by `AccountIdInterceptor`. Don't model them as nested DI graphs.
10. **`:features` is a Hybrid-Monolith on purpose.** Feature boundaries are **packages**, not Gradle modules. Adding a new flow means a new package under `:features/`. The exception is heavy-SDK features (chatbot is the reference example) and variant-locked features with their own API/DTOs/screens (e.g. `:features-bakong-disputes`) — those become sibling modules.
11. **Onboarding a new variant is strictly additive.** New module + one `include(":variants-X")` line + `VariantCatalogue` entry. If a variant onboarding doc/PR touches `:features`, `:data`, `:design-system`, or sibling variants, the architecture has been violated.

## Naming and Style Conventions Used Across Docs

Be consistent with these; they're load-bearing for grep:

- Module references use the Gradle path with leading colon: `:aos-core`, `:core`, `:design-system`, `:data`, `:features`, `:features-chatbot`, `:variants-kh`, `:app`.
- Variant module directory names are flat siblings, kebab-case: `variants-<id>` (so `variants-kh/`, `variants-vn/`, `variants-ppcbank/`). Mirrors `features-chatbot`.
- Variant-locked feature modules follow the same flat sibling pattern: `:features-{feature-name}` (default) or `:features-{variant}-{feature-name}` if structurally locked.
- Custom Hilt scope is `@LoggedInScoped` (defined in `:core/scope/`); the component is `LoggedInComponent`; the orchestrator is `BootCoordinator`; the lifecycle owner is `LoggedInComponentManager`.
- The MG client is `MgClient`; the typed payload is `RuntimeConfig`; the gate Composables are `MaintenanceGate` and `ForceUpdateGate`.
- The session type is `Session` (in `:core`); accounts are `DepartmentAccount`; the OkHttp interceptor that stamps the active account ID is `AccountIdInterceptor`.
- The variant types in `:core` are `VariantContext`, `VariantId`, `VariantCapabilities` (no other names).
- The variant binding map key is `@VariantKey("<id>")` (defined in `:core/scope/`). Every `:variants-{id}` policy binding uses `@Binds @IntoMap @VariantKey("<id>") @LoggedInScoped`. The runtime resolver lives in `:app/di/VariantResolverModule.kt` and is the **single point of dispatch** for variant-specific policies — no other code branches on `variant.id`.
- The unified API surface is the `Fintech*Api` family (in `:data`), split per feature area: `FintechAuthApi`, `FintechTransferApi`, `FintechAccountApi`, etc. There is no `KhBakong*Api`, `VnNapas*Api`, etc. — those are server-side concerns.
- Repository implementations are named `Fintech<Area>Repo` (e.g. `FintechTransferRepo`, `FintechAuthRepo`) — one per `:core` repository interface, never per-variant.
- Design-system primitives are `Compass<Component>` (e.g. `CompassButton`, `CompassTheme`); they live in `com.<org>.design.*`.
- MVI suffix convention per screen: `*Screen.kt`, `*ViewModel.kt`, `*Contract.kt` (where the contract file holds the screen's `UiState`, `UiEvent`, and `UiEffect` sealed types).
- Internal Kotlin visibility (`internal`) is the default for `:data` and `:variants-*` classes; the **only** intentionally public surface is each module's Hilt `@Module`. `:design-system` is the exception: its components are intentionally public.
- Cross-references between docs use relative links (e.g., `[10 — Boot Phases](10-boot-phases.md)`). Preserve this style when adding new cross-references.

## Glossary Pointer

When introducing a framework-specific term in any doc, first check `docs/16-glossary.md`. Reuse the existing term verbatim; if a new term is genuinely needed, add a glossary entry in the same PR.
