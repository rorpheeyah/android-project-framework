# The Compass Framework

> **Codename:** Compass · **Domain:** Multi-tenant fintech (Android) · **Status:** Architecture spec (design phase)

A multi-tenant Android banking architecture where the tenant (customer organization) is selected once at login, the configuration is fetched once from MG at boot, and the front-end stays Logic-Blind across all tenants — without ever writing `if (tenant.id == "BankA")`.

The codebase services many tenants from a single binary, with the unified server-side fintech API handling backend routing per user. The Android side never knows which organization a user belongs to in order to talk to the backend. Region grouping (Cambodia, Korea, …) is expressed as a Gradle module hierarchy; the only runtime DI axis is the tenant.

> **Historical note:** earlier iterations of this framework used `:aos-core` (renamed to `:aos-sdk` for multi-product consumption) and a two-axis DI dispatch (`VariantContext` + `TenantContext`). The variant axis has been collapsed into the tenant axis; region is now a Gradle module hierarchy. See `docs/19 § 12` for the rationale.

---

## Mission Statement

| | |
|---|---|
| **Problem** | One Android app must serve N organizations across one or more regulators, each with unique business rules and regulatory constraints — without `if`/`when` chains, code duplication, or cross-contamination of logic. |
| **Solution** | Separate **infrastructure** (`:aos-sdk`), **contracts** (`:core`), **UI primitives** (`:design-system`), **shared data** (`:data`), **UI** (`:features` + sibling `:features-{name}` modules), and **tenant policies** (`:tenants:{region}:{tenantSlug}`) into a strict dependency DAG. Resolve URLs from MG at boot (with stale-config fallback). Bind tenant policies once at login. Tear it down on logout — no runtime swap. |
| **Non-negotiables** | Zero conditional sprawl · Linear build performance · Tenant policy isolation · Boot-time URL discovery · Single APK |

---

## Documentation Index

### Foundations
| Doc | Purpose |
|---|---|
| [00 — Overview](docs/00-overview.md) | Vision, the Conditional Logic Sprawl problem, the solution shape |
| [01 — Module Topology](docs/01-module-topology.md) | All modules, dependency rules, forbidden imports |

### Per-Module Specs
| Doc | Module | Layer |
|---|---|---|
| [02 — `:aos-sdk`](docs/02-aos-core.md) | Submodule | Infrastructure (git-tag-released for multi-product consumption) |
| [03 — `:core`](docs/03-core.md) | Local | Domain & Contracts |
| [04 — `:design-system`](docs/04-design-system.md) | Local | UI primitives (theme + components) |
| [05 — `:data`](docs/05-data.md) | Local | Data layer (Fintech*Api + repos + third-party clients) |
| [06 — `:features`](docs/06-features.md) | Local | UI Engine (Hybrid-Monolith) |
| [07 — `:tenants:*`](docs/07-variants.md) | Local | Tenant silos and region bases (policies + DI) |
| [08 — `:app`](docs/08-app-orchestrator.md) | Local | Orchestrator / Boot |

### Strategic Mechanisms
| Doc | Topic |
|---|---|
| [09 — MVI Pattern](docs/09-mvi-pattern.md) | UiState · UiEvent · UiEffect conventions |
| [10 — Boot Phases](docs/10-boot-phases.md) | MG → gate → login → SessionGraph build → logout |
| [11 — MG and Runtime Config](docs/11-mg-and-runtime-config.md) | Service-discovery endpoint, maintenance/force-update gating, stale-config fallback |
| [12 — Departments and Session](docs/12-departments-and-session.md) | Multi-account session, account switcher (optional per consuming app) |

### Deliverables
| Doc | Topic |
|---|---|
| [13 — Onboarding a Tenant](docs/13-onboarding-a-variant.md) | Step-by-step: adding a new tenant (or a new region) with zero `:features` / `:data` changes |
| [14 — Build Performance](docs/14-build-performance.md) | Linear build strategy, why Hybrid-Monolith |
| [15 — Tech Stack](docs/15-tech-stack.md) | Quick reference card |
| [16 — Glossary](docs/16-glossary.md) | Logic-Blind, Hybrid-Monolith, Tenant, Region, MG, RuntimeConfig, etc. |
| [17 — Project Structure](docs/17-project-structure.md) | Single-page tree of every module's directory layout |
| [18 — WebView Integration](docs/18-webview-integration.md) | WebView primitives, JS bridge contract, URL allowlist, cookie/session sync |
| [19 — Tenants and Regions](docs/19-tenants-and-variants.md) | The single tenant axis; `TenantContext` with flags/params; structural escalation; region as Gradle module hierarchy |

### Capability Specs (PRD-Driven Additions)
| Doc | Topic |
|---|---|
| [20 — Chat](docs/20-chat.md) | Sendbird customer ↔ officer chat; provider-agnostic `ChatRepository`; `:features-support-chat` sibling module |
| [21 — Push Channels](docs/21-push-channels.md) | FCM with three channels: reminder · transaction · announcement |
| [22 — Deeplinks](docs/22-deeplinks.md) | App Links + Compose Nav DSL; guarantor SMS link; payment deeplink |
| [23 — KYC Capture](docs/23-kyc-capture.md) | In-house CameraX + ML Kit; SDK primitives + `:features-kyc` flow split |
| [24 — PDF](docs/24-pdf.md) | Loan-contract download + preview; OkHttp + WorkManager + app-private storage |
| [25 — Locale](docs/25-locale.md) | KR/EN/KH runtime switching; bundled Noto fonts; strings.xml is the only correct home for UI text |
| [26 — PIN and Session](docs/26-pin-and-session.md) | 4-digit PIN UX, lockout, biometric tier; inactivity-driven session timeout |
| [27 — Maps and Location](docs/27-maps-and-location.md) | Google Maps Compose branch locator; offline-cacheable; point-of-use permissions |
| [28 — Background Work](docs/28-background-work.md) | WorkManager + Hilt; tag-by-userId for logout safety |
| [29 — Local Database](docs/29-local-database.md) | Room + SQLCipher; per-feature DBs; what gets cached vs what doesn't |
| [30 — Form Wizard](docs/30-form-wizard.md) | Multi-step apply-loan (9 NON-MWL, 18 MWL); NavGraph-scoped wizard with draft persistence |

---

## At a Glance

```
root/
├── aos-sdk/         (Submodule, git-tagged)   Infrastructure: Network · Security · Storage · Logging · Camera · ML · Imaging · PDF · Push · Deeplink · Locale · WebView
├── core/            (Local)                   Domain: Repository/Policy interfaces · Models · RuntimeConfig · Session · TenantContext · MVI base
├── design-system/   (Local)                   Theme tokens · CompassButton/TextField/PinInput/… · Compose modifiers · LocaleSelector
├── data/            (Local)                   Fintech*Api (Retrofit) + *Repo implementations + :data/external/ for third-party
│
├── features/        (UI Engine)               Logic-Blind Compose screens & ViewModels
│   ├── boot/                                  Package: Cold-start, MaintenanceGate
│   ├── auth/                                  Package: Login · Registration · OTP · PIN · Biometric
│   ├── dashboard/                             Package: Dashboard, multi-currency display
│   ├── loan/                                  Package: Product list/detail · Apply (NON-MWL, MWL) · My Loan · Repayment · Calculator
│   └── …
│
├── features-chatbot/                          Isolated UI sibling (NLP/LLM chatbot)
├── features-kyc/                              Isolated UI sibling (CameraX + ML Kit)
├── features-support-chat/                     Isolated UI sibling (Sendbird)
├── features-branch-locator/                   Isolated UI sibling (Google Maps)
├── features-{tenant-feature}/                 Tenant-locked features (e.g. features-bakong-disputes)
│
├── tenants/
│   ├── cambodia/
│   │   ├── base/                              KH regulator baseline (KHR/USD format, NBC compliance, KH calendar, OTP-SMS)
│   │   ├── default/                           Sentinel tenant for tests
│   │   └── nh/                                NH-KH (this PRD's tenant) — depends on cambodia/base
│   └── korea/                                 (illustrative: if/when KR ships)
│       ├── base/
│       ├── default/
│       └── nh/
│
└── app/             (Orchestrator)            Application · BootCoordinator (incl. stale-config fallback) · LoggedInComponent · TenantResolverModule
```

For the full directory layout of each module, see [17 — Project Structure](docs/17-project-structure.md).

---

## Reading Order

If you're new to the framework, read in this order:

1. **[Overview](docs/00-overview.md)** — understand the problem before the solution.
2. **[Module Topology](docs/01-module-topology.md)** — see the dependency DAG.
3. **[Boot Phases](docs/10-boot-phases.md)** — the highest-leverage mechanism.
4. **[MG and Runtime Config](docs/11-mg-and-runtime-config.md)** — what the binary contains and what it doesn't.
5. **[Tenants and Regions](docs/19-tenants-and-variants.md)** — the single-axis model and regional Gradle hierarchy.
6. **[Onboarding a Tenant](docs/13-onboarding-a-variant.md)** — the system's promise, made operational.

Everything else is reference.
