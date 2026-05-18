# The Bizplay IPPP Architecture

> **Codename:** Bizplay IPPP · **Domain:** Multi-tenant corporate expense / receipt management (Android) · **Status:** Architecture spec (design phase, target for refactor of `Bizplay4.0_IPPP`)

A multi-tenant, multi-region Android corporate-expense architecture where the **variant** (region/regulator — KR / KH / VN) and the **tenant** (corporate customer — POSCO / Lotte / NIA / Shinsegae / ITCen / WIPS / HANA / IBS / SPC / default) are selected once at login, the configuration is fetched once from MgGate at boot, and the front-end stays Logic-Blind across all variants and tenants — without ever writing `if (DetailConfig.isNIA())` or `if (variantId == "kh")`.

The codebase services many corporate customers across multiple regions from a single binary, with the unified server-side IPPP API handling backend routing per user and per company code. The Android side never knows *which customer organization* a logged-in user belongs to in order to talk to the backend.

---

## Mission Statement

| | |
|---|---|
| **Problem** | One Android app must serve N corporate customers across M regions, each with unique business rules (approval line shape, employee-ID format, receipt fields, expense categories, tax/VAT rules) — without `if (DetailConfig.isXxx())` chains scattered across every flow, code duplication, or cross-contamination of logic. The current Bizplay codebase has roughly 124 call sites of `DetailConfig.isXxx()`. |
| **Solution** | Separate **infrastructure** (`:aos-core`), **contracts** (`:core`), **UI primitives** (`:design-system`), **shared data** (`:data`), **UI** (`:features`), **variant policies** (`:variants-*`), and **tenant profiles** (`:variants-{region}/tenants/{id}/`) into a strict dependency DAG. Resolve URLs from MgGate at boot. Bind variant and tenant policies once at login. Tear them down on logout — no runtime swap. |
| **Non-negotiables** | Zero conditional sprawl · Linear build performance · Variant + tenant policy isolation · Boot-time URL discovery · Single APK |

---

## Documentation Index

### Foundations
| Doc | Purpose |
|---|---|
| [00 — Overview](docs/00-overview.md) | Vision, the `DetailConfig.isXxx()` Conditional Logic Sprawl problem, the solution shape |
| [01 — Module Topology](docs/01-module-topology.md) | All modules, dependency rules, forbidden imports |

### Per-Module Specs
| Doc | Module | Layer |
|---|---|---|
| [02 — `:aos-core`](docs/02-aos-core.md) | Submodule | Infrastructure (HTTP, SQLCipher, TransKey, mVaccine, Secucen) |
| [03 — `:core`](docs/03-core.md) | Local | Domain & Contracts |
| [04 — `:design-system`](docs/04-design-system.md) | Local | UI primitives (`BizTheme`, `BizButton`, …) |
| [05 — `:data`](docs/05-data.md) | Local | Data layer (`Ippp*Api` family + repos) |
| [06 — `:features`](docs/06-features.md) | Local | UI Engine (Hybrid-Monolith) |
| [07 — `:variants-*`](docs/07-variants.md) | Local | Variant Silos (policies + DI) |
| [08 — `:app`](docs/08-app-orchestrator.md) | Local | Orchestrator / Boot |

### Strategic Mechanisms
| Doc | Topic |
|---|---|
| [09 — MVI Pattern](docs/09-mvi-pattern.md) | UiState · UiEvent · UiEffect conventions |
| [10 — Boot Phases](docs/10-boot-phases.md) | MgGate → gate → login → SessionGraph build → logout |
| [11 — MG and Runtime Config](docs/11-mg-and-runtime-config.md) | Service-discovery endpoint (today's `MgGate`), maintenance/force-update gating |
| [12 — Departments and Session](docs/12-departments-and-session.md) | Multi-institution session, account switcher (`USE_INTT_ID`) |

### Deliverables
| Doc | Topic |
|---|---|
| [13 — Onboarding a Variant](docs/13-onboarding-a-variant.md) | Step-by-step: adding `variants-vn` with zero `:features` / `:data` changes |
| [14 — Build Performance](docs/14-build-performance.md) | Linear build strategy, why Hybrid-Monolith |
| [15 — Tech Stack](docs/15-tech-stack.md) | Quick reference card |
| [16 — Glossary](docs/16-glossary.md) | Logic-Blind, Hybrid-Monolith, Variant Silo, MG, RuntimeConfig, Tenant, etc. |
| [17 — Project Structure](docs/17-project-structure.md) | Single-page tree of every module's directory layout |
| [18 — WebView Integration](docs/18-webview-integration.md) | `BizWebView` primitives, JS bridge contract (replaces `BrowserBridge`), URL allowlist, cookie/session sync |
| [19 — Tenants and Variants](docs/19-tenants-and-variants.md) | The two axes: regulator-boundary (variant: KR/KH/VN) vs corporate-customer boundary (tenant: POSCO/Lotte/NIA/…); `TenantContext`, flags/params, structural escalation |

---

## At a Glance

```
root/
├── aos-core/        (Submodule)   Infrastructure: Network · Security (SQLCipher, TransKey, mVaccine, Secucen) · Storage · Logging · BizWebView
├── core/            (Local)       Domain: Repository/Policy interfaces · Models · RuntimeConfig · Session · MVI base
├── design-system/   (Local)       Theme tokens · BizButton/BizTextField/BizWebView/… · Compose modifiers
├── data/            (Local)       Ippp*Api (Retrofit) + *Repo implementations
│
├── features/        (UI Engine)   Logic-Blind Compose screens & ViewModels
│   ├── boot/                      Package: Cold-start, MaintenanceGate
│   ├── auth/                      Package: Login · OTP · Institution picker
│   ├── receipt/                   Package: List · Detail · OCR · Camera capture entry
│   ├── expense/                   Package: Submission · Categorisation · Business-trip bundle
│   ├── approval/                  Package: Inbox · Approve / reject · Routing
│   ├── card/                      Package: Registration · Management · Statement
│   └── notice/                    Package: Announcements · Help
│
├── features-scanner/              Isolated module (heavy SDKs: io.card + cameraviewplus + OCR + sasapi)
├── features-hipass/               Variant-locked feature: Korea-only highway-toll capture (own API + DTOs + screens)
│
├── variants-kr/                   Korea: KrFeeCalculator + KrwAmountFormatter + KrApprovalThresholds + KR tenants (POSCO, Lotte, NIA, Shinsegae, ITCen, WIPS, HANA, IBS, SPC, default) + DI
├── variants-kh/                   Cambodia: KhrAmountFormatter + KH compliance + tenants + DI
├── variants-vn/                   Vietnam: VndAmountFormatter + VN compliance + tenants + DI
│
└── app/             (Orchestrator) Application · BootCoordinator · LoggedInComponent
```

For the full directory layout of each module, see [17 — Project Structure](docs/17-project-structure.md).

---

## Reading Order

If you're new to the framework, read in this order:

1. **[Overview](docs/00-overview.md)** — understand the problem (`DetailConfig` sprawl) before the solution.
2. **[Module Topology](docs/01-module-topology.md)** — see the dependency DAG.
3. **[Boot Phases](docs/10-boot-phases.md)** — the highest-leverage mechanism.
4. **[MG and Runtime Config](docs/11-mg-and-runtime-config.md)** — what the binary contains and what it doesn't.
5. **[Tenants and Variants](docs/19-tenants-and-variants.md)** — the two axes the architecture distinguishes.
6. **[Onboarding a Variant](docs/13-onboarding-a-variant.md)** — the system's promise, made operational.

Everything else is reference.
