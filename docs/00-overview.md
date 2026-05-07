# 00 · Architecture Overview

> Read this first. Everything else is implementation detail of the ideas here.

---

## 1. The Problem We're Solving

A single Android binary must service:

- **Multiple banks** (PPCBank, partner co-issuers, white-label deployments)
- **Multiple markets** (Cambodia, Vietnam, Malaysia, …)
- **Multiple rails** (KHQR / Bakong, Napas / VietQR, legacy PPC APIs)
- **Multiple environments** per build (Production, Staging, UAT, Sandbox)

Naïve approaches degrade into one of three failure modes:

| Anti-pattern | Symptom | Why it fails |
|---|---|---|
| **Conditional Logic Sprawl** | `if (tenant == "kh") … else if (tenant == "vn") …` scattered across ViewModels, repositories, validators | A single PR touches every flow. Regression risk per release scales with `O(tenants × features)`. |
| **Fork-per-Tenant** | One Git branch (or one app variant) per bank | Bug fixes must be cherry-picked N times. Shared components drift. CI cost scales linearly. |
| **God-Repository** | One mega-class with private per-tenant methods | Same conditional sprawl, just hidden. Test surface becomes unreviewable. |

**Nexus rejects all three.** A new tenant must cost **one new module** and **one new DI binding** — nothing else.

---

## 2. The Solution at a Glance

Three engineering principles — applied in lockstep — eliminate conditional sprawl:

### 2.1 Dual-Core System

Two stable layers, never one.

- **`:aos-core`** — *infrastructure that doesn't know it's a bank*. Networking, encryption, biometric, storage, logging. Versioned independently as a Git submodule. Reusable across non-banking projects.
- **`:core`** — *contracts that don't know which bank*. Repository **interfaces**, domain models, the `TenantContext`. Owns the shape of every tenant interaction without prescribing the implementation.

The split exists because infrastructure stability and product-domain stability evolve at different rates. Conflating them is what causes `:core` modules to bloat over time.

### 2.2 Interface-Driven Development

The UI calls **interfaces**, never **implementations**.

```
:features (UI) ─→ :core (interfaces) ←─ :tenants:kh (implementation)
                                    ←─ :tenants:vn (implementation)
                                    ←─ :tenants:ppcbank (implementation)
```

`:features` is **Logic-Blind** — it cannot reach into `:tenants:*`. A compile-time guarantee, enforced by the module dependency graph.

### 2.3 Runtime Dependency Injection

A `@TenantScoped` Hilt component replaces its bindings when the user (or the system) switches tenants. The current session, sensitive memory, and any cached state are **purged before re-injection**.

No app restart. No leaked state. No cross-tenant contamination.

---

## 3. The Five Modules

| Module | Layer | One-line purpose |
|---|---|---|
| `:aos-core` | Infrastructure | Project-agnostic libs: network, security, storage, logging |
| `:core` | Domain & Contract | Repository interfaces, domain models, `TenantContext` |
| `:features` | UI Engine | Logic-Blind Compose screens organized by package |
| `:features-chatbot` | Isolated UI | Heavy-SDK feature kept off the main UI engine for build perf |
| `:tenants:[id]` | Logic Silo | Concrete implementations + tenant-specific business rules |
| `:app` | Orchestrator | Application class, DI registry, runtime tenant switcher |

Detailed in [01 — Module Topology](01-module-topology.md).

---

## 4. The Four Strategic Requirements

These are the non-negotiables. Every design choice in the framework traces back to one of these.

### 4.1 Zero-Trust Runtime Switching
Switching tenants is treated as a **security event**, not a configuration change. Sequence: lock UI → cancel in-flight work → clear `StateFlow`s → wipe caches → revoke session → re-inject DI graph → unlock. No restart. Detail: [08 — Runtime Switching](08-runtime-tenant-switching.md).

### 4.2 Polymorphic Scalability
There is no `if (tenant == X)` anywhere in the codebase. Polymorphism does the dispatch. `:features` calls `validator.isValid(amount)`; `:app` provides `BankAValidator` (from `:tenants:bank-a`) when `BankA` is the active tenant. Detail: [10 — Contract Walkthrough](10-contract-implementation-example.md).

### 4.3 Decentralized Data/Domain
Business logic lives where it belongs:

- **Tenant-specific rules** → `:tenants:[id]` (fee calculation, validation thresholds, regulatory checks)
- **UI-specific logic** → `:features` packages (input formatting, navigation state, transient flows)
- **Never** → `:core` or `:aos-core`

This keeps the contract layer clean and the infrastructure layer stable.

### 4.4 Linear Build Performance
Adding a new feature folder must not increase build time more than the cost of compiling that folder's source. The Hybrid-Monolith design — one `:features` module with package-based feature boundaries — avoids the per-module Gradle overhead that plagues finely-modularized codebases. Detail: [12 — Build Performance](12-build-performance.md).

---

## 5. What Makes This Different

| Common pattern | Nexus equivalent | Why |
|---|---|---|
| One feature module per screen flow | Packages inside one `:features` module | Eliminates Gradle configuration overhead at scale |
| `BuildConfig.FLAVOR` switching | DI-driven tenant selection | Build-time flavors lock the binary to one tenant; Nexus selects at runtime |
| Repository implementations next to interfaces | Interfaces in `:core`, impls in `:tenants:*` | Forces clean separation; tenants can't leak into the contract layer |
| Single Application class with conditional init | Orchestrator delegates to active tenant's bindings | Onboarding is additive (new module + new binding), never modifying existing code |

---

## 6. What This Document Doesn't Cover

This is the executive summary. For specifics, follow the doc index in the [README](../README.md). In particular:

- **How exactly does the DI graph swap?** → [08](08-runtime-tenant-switching.md)
- **What does a request actually look like end-to-end?** → [10](10-contract-implementation-example.md)
- **How do I add a new bank?** → [11](11-onboarding-new-tenant.md)
- **Why can't I just split `:features` into one module per flow?** → [12](12-build-performance.md)
