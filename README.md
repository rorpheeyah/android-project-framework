# The Nexus Framework

> **Codename:** Nexus · **Domain:** Multi-tenant fintech (Android) · **Role:** Lead Android Architect & Systems Engineer

A high-stakes, multi-tenant banking architecture engineered to eliminate **Conditional Logic Sprawl** through a **dual-core system**, **interface-driven development**, and **runtime dependency injection**.

The codebase services many tenants — different banks, different markets, different rails (KHQR, Napas, PPCBank legacy) — from a single binary, without ever writing `if (tenant == "BankA")`.

---

## Mission Statement

| | |
|---|---|
| **Problem** | One Android app must serve N banks across M markets, each with unique APIs, validation rules, and regulatory constraints — without `if`/`when` chains, code duplication, or cross-contamination of logic. |
| **Solution** | Separate **infrastructure** (`:aos-core`), **contracts** (`:core`), **UI** (`:features`), and **logic** (`:tenants:*`) into a strict dependency DAG. Wire the right tenant implementation at runtime via Hilt. Swap the entire DI graph without restarting the app. |
| **Non-negotiables** | Zero conditional sprawl · Linear build performance · Tenant logic isolation · Hot DI swap with memory purge |

---

## Documentation Index

### Foundations
| Doc | Purpose |
|---|---|
| [00 — Overview](docs/00-overview.md) | Vision, the Conditional Logic Sprawl problem, dual-core principle |
| [01 — Module Topology](docs/01-module-topology.md) | All modules, dependency rules, forbidden imports |

### Per-Module Specs
| Doc | Module | Layer |
|---|---|---|
| [02 — `:aos-core`](docs/02-aos-core.md) | Submodule | Infrastructure |
| [03 — `:core`](docs/03-core.md) | Local | Domain & Contracts |
| [04 — `:features`](docs/04-features.md) | Local | UI Engine (Hybrid-Monolith) |
| [05 — `:tenants:*`](docs/05-tenants.md) | Local | Logic Silos |
| [06 — `:app`](docs/06-app-orchestrator.md) | Local | Orchestrator / DI Glue |

### Strategic Mechanisms
| Doc | Topic |
|---|---|
| [07 — MVI Pattern](docs/07-mvi-pattern.md) | UiState · UiEvent · UiEffect conventions |
| [08 — Runtime Tenant Switching](docs/08-runtime-tenant-switching.md) | DI swap + memory purge + session clear |
| [09 — Environment Configuration](docs/09-environment-configuration.md) | Debug server picker + EnvironmentInterceptor |

### Deliverables
| Doc | Topic |
|---|---|
| [10 — Contract→Implementation Walkthrough](docs/10-contract-implementation-example.md) | ViewModel ↔ `:core` contract ↔ `:tenants:kh` impl |
| [11 — Onboarding a New Tenant](docs/11-onboarding-new-tenant.md) | Step-by-step: adding `tenants-my` with zero `:features` changes |
| [12 — Build Performance](docs/12-build-performance.md) | Linear build strategy, why Hybrid-Monolith |
| [13 — Tech Stack](docs/13-tech-stack.md) | Quick reference card |
| [14 — Glossary](docs/14-glossary.md) | Logic-Blind, Hybrid-Monolith, Logic Silo, TenantContext, etc. |

---

## At a Glance

```
root/
├── aos-core/        (Submodule)   Infrastructure: Network · Security · Storage · Logging
├── core/            (Local)       Domain: Repository Interfaces · Models · TenantContext · MVI base
│
├── features/        (UI Engine)   Logic-Blind Compose screens & ViewModels
│   ├── auth/                      Package: Login · Registration · OTP
│   ├── transfer/                  Package: P2P · QR · Review
│   ├── splash/                    Package: Cold-start gate
│   └── common/                    Package: Theme · Design system
│
├── features-chatbot/              Isolated module (heavy SDKs)
│
├── tenants/         (Logic Silos)
│   ├── tenants-kh/                Cambodia · Bakong · KHQR
│   ├── tenants-vn/                Vietnam · Napas
│   └── tenants-ppcbank/           PPCBank legacy override
│
└── app/             (Orchestrator) Application · DI registry · Runtime tenant switcher
```

---

## Reading Order

If you're new to the framework, read in this order:

1. **[Overview](docs/00-overview.md)** — understand the problem before the solution.
2. **[Module Topology](docs/01-module-topology.md)** — see the dependency DAG.
3. **[Contract Walkthrough](docs/10-contract-implementation-example.md)** — concrete example of how a request flows through the layers.
4. **[Runtime Switching](docs/08-runtime-tenant-switching.md)** — the highest-leverage mechanism.
5. **[Onboarding a New Tenant](docs/11-onboarding-new-tenant.md)** — the system's promise, made operational.

Everything else is reference.
