# The Compass Framework

> **Codename:** Compass · **Domain:** Multi-region fintech (Android) · **Status:** Architecture spec (design phase)

A multi-region Android banking architecture where the variant (region/company) is selected once at login, the configuration is fetched once from MG at boot, and the front-end stays Logic-Blind across all variants — without ever writing `if (variantId == "BankA")`.

The codebase services many regions and companies from a single binary, with the unified server-side fintech API handling backend routing per user. The Android side never knows which bank a user belongs to in order to talk to the backend.

---

## Mission Statement

| | |
|---|---|
| **Problem** | One Android app must serve N companies across M regions, each with unique business rules and regulatory constraints — without `if`/`when` chains, code duplication, or cross-contamination of logic. |
| **Solution** | Separate **infrastructure** (`:aos-core`), **contracts** (`:core`), **UI primitives** (`:design-system`), **shared data** (`:data`), **UI** (`:features`), and **variant policies** (`:variants-*`) into a strict dependency DAG. Resolve URLs from MG at boot. Bind variant policies once at login. Tear it down on logout — no runtime swap. |
| **Non-negotiables** | Zero conditional sprawl · Linear build performance · Variant policy isolation · Boot-time URL discovery · Single APK |

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
| [02 — `:aos-core`](docs/02-aos-core.md) | Submodule | Infrastructure |
| [03 — `:core`](docs/03-core.md) | Local | Domain & Contracts |
| [04 — `:design-system`](docs/04-design-system.md) | Local | UI primitives (theme + components) |
| [05 — `:data`](docs/05-data.md) | Local | Data layer (FintechApi + repos) |
| [06 — `:features`](docs/06-features.md) | Local | UI Engine (Hybrid-Monolith) |
| [07 — `:variants-*`](docs/07-variants.md) | Local | Variant Silos (policies + DI) |
| [08 — `:app`](docs/08-app-orchestrator.md) | Local | Orchestrator / Boot |

### Strategic Mechanisms
| Doc | Topic |
|---|---|
| [09 — MVI Pattern](docs/09-mvi-pattern.md) | UiState · UiEvent · UiEffect conventions |
| [10 — Boot Phases](docs/10-boot-phases.md) | MG → gate → login → SessionGraph build → logout |
| [11 — MG and Runtime Config](docs/11-mg-and-runtime-config.md) | Service-discovery endpoint, maintenance/force-update gating |
| [12 — Departments and Session](docs/12-departments-and-session.md) | Multi-account session, account switcher |

### Deliverables
| Doc | Topic |
|---|---|
| [13 — Onboarding a Variant](docs/13-onboarding-a-variant.md) | Step-by-step: adding `variants-my` with zero `:features` / `:data` changes |
| [14 — Build Performance](docs/14-build-performance.md) | Linear build strategy, why Hybrid-Monolith |
| [15 — Tech Stack](docs/15-tech-stack.md) | Quick reference card |
| [16 — Glossary](docs/16-glossary.md) | Logic-Blind, Hybrid-Monolith, Variant Silo, MG, RuntimeConfig, etc. |
| [17 — Project Structure](docs/17-project-structure.md) | Single-page tree of every module's directory layout |

---

## At a Glance

```
root/
├── aos-core/        (Submodule)   Infrastructure: Network · Security · Storage · Logging
├── core/            (Local)       Domain: Repository/Policy interfaces · Models · RuntimeConfig · Session · MVI base
├── design-system/   (Local)       Theme tokens · CompassButton/TextField/… · Compose modifiers
├── data/            (Local)       FintechApi (Retrofit) + *Repo implementations
│
├── features/        (UI Engine)   Logic-Blind Compose screens & ViewModels
│   ├── boot/                      Package: Cold-start, MaintenanceGate
│   ├── auth/                      Package: Login · Registration · OTP
│   ├── transfer/                  Package: P2P · QR · Review
│   └── account/                   Package: Balances · History · Account switcher
│
├── features-chatbot/              Isolated module (heavy SDKs)
├── features-{variant-feature}/    Variant-locked features (e.g. features-bakong-disputes)
│
├── variants-kh/                   Cambodia: KhTransferAmountPolicy + KhFeeCalculator + KhCapabilities + DI
├── variants-vn/                   Vietnam: policies + DI
├── variants-ppcbank/              PPCBank legacy: policies + DI
│
└── app/             (Orchestrator) Application · BootCoordinator · LoggedInComponent
```

For the full directory layout of each module, see [17 — Project Structure](docs/17-project-structure.md).

---

## Reading Order

If you're new to the framework, read in this order:

1. **[Overview](docs/00-overview.md)** — understand the problem before the solution.
2. **[Module Topology](docs/01-module-topology.md)** — see the dependency DAG.
3. **[Boot Phases](docs/10-boot-phases.md)** — the highest-leverage mechanism.
4. **[MG and Runtime Config](docs/11-mg-and-runtime-config.md)** — what the binary contains and what it doesn't.
5. **[Onboarding a Variant](docs/13-onboarding-a-variant.md)** — the system's promise, made operational.

Everything else is reference.
