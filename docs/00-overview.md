# 00 · Architecture Overview

> Read this first. Everything else is implementation detail of the ideas here.

---

## 1. The Problem We're Solving

A single Android binary must service:

- **Multiple companies/regions** (PPCBank, partner co-issuers, market deployments) — selected at login, not at install
- **A few well-defined frontend variations** (formats, validation thresholds, fee rules, capability flags)
- **Per-environment URL discovery** that must update without an app release
- **Multiple accounts per logged-in user** (Personal, Corporate, Joint within one login)
- **A unified server-side API** that already routes per user — the Android side does not need to know which bank a user belongs to in order to talk to the backend

The hostile failure mode is **Conditional Logic Sprawl** — variant-specific behavior expressed as `if (variantId == "kh") … else if (variantId == "vn") …` chains scattered across ViewModels, repositories, and validators. A single PR touches every flow; regression risk per release scales with `O(variants × features)`.

The framework eliminates this by making variant differences **polymorphic** through `:core` interfaces, and by making the variant binding a **one-time event at login**.

---

## 2. The Solution at a Glance

### 2.1 Two-Layer Foundation

Two stable layers, never one.

- **`:aos-core`** — *infrastructure that doesn't know it's a bank*. Networking, encryption, biometric, storage, logging. Versioned independently as a Git submodule. Reusable across non-banking projects.
- **`:core`** — *contracts that don't know which company*. Repository interfaces, domain models, `RuntimeConfig`, `Session`, `DepartmentAccount`. Owns the shape of every interaction without prescribing the implementation.

### 2.2 Interfaces in `:core`, Implementations Split by Concern

The UI calls **interfaces**, never **implementations**. The implementation side has two homes:

- **`:data`** — implements `:core` repository interfaces. Owns the single `FintechApi` Retrofit interface and the `*Repo` classes that call it. **Same code for every variant** — the server demuxes per user.
- **`:variants-{id}`** — implements `:core` policy interfaces (validation, fee rules, formatting, capability flags). **One module per region/company** — small, isolated, holds only what differs.

```
:features (UI) ─→ :core (interfaces) ←─ :data (one FintechApi + *Repo)
                                    ←─ :variants-kh (KhTransferAmountPolicy, …)
                                    ←─ :variants-vn (VnFeeCalculator, …)
```

`:features` is **Logic-Blind** — it cannot reach into `:data` or `:variants-*`. A compile-time guarantee, enforced by the build graph.

### 2.3 Selection-Once at Login

When the user logs in, the server returns the variant ID. `:app` builds a `LoggedInComponent` with that variant's policy bindings — once. The component lives until logout. No runtime swap, no purge sequence.

To change variants, the user logs out. The component is dropped, all `@LoggedInScoped` state becomes unreachable, and the next login builds a fresh component.

The boot sequence:

```
[boot]   hardcoded MG URL → MG → RuntimeConfig (urls, maintenance, forceUpdate)
[gate]   if maintenance.down or version < min → MaintenanceGate, hard stop
[login]  user authenticates → server returns variantId + accounts[]
[bind]   build LoggedInComponent with :variants-{variantId} policies
[main]   navigate into feature graph; the Session is now active
[logout] tear down LoggedInComponent → return to login
```

→ Detail: [10 — Boot Phases](10-boot-phases.md)

---

## 3. The Modules

| Module | Layer | One-line purpose |
|---|---|---|
| `:aos-core` | Infrastructure | Project-agnostic libs: network, security, storage, logging |
| `:core` | Domain & Contract | Repository interfaces, domain models, `RuntimeConfig`, `Session`, MVI base |
| `:data` | Data | `FintechApi` (Retrofit) + repository implementations of `:core` interfaces |
| `:features` | UI Engine | Logic-Blind Compose screens organized by package |
| `:features-chatbot` | Isolated UI | Heavy-SDK feature kept off the main UI engine for build perf |
| `:variants-{id}` | Variant Silo | Variant-specific policies + DI bindings (one module per region/company) |
| `:app` | Orchestrator | Application class, `BootCoordinator`, navigation host |

Detailed in [01 — Module Topology](01-module-topology.md).

---

## 4. The Four Architectural Promises

### 4.1 Boot-Time Discovery, Login-Time Selection
The only network configuration baked into the binary is the MG URL per build environment. Everything else — main API URLs, maintenance state, version floor — comes from MG at cold start. The variant comes from the auth response. A backend URL change doesn't ship a new APK; a new variant doesn't reach `:features` source code. Detail: [11 — MG and Runtime Config](11-mg-and-runtime-config.md).

### 4.2 Polymorphic Scalability
There is no `if (variantId == X)` anywhere in the codebase. Polymorphism does the dispatch. Where variants differ, `:features` calls a `:core` policy interface (e.g., `transferAmountPolicy.validate(amount)`); the active variant module supplies the implementation. Where variants are the same — including the API surface — `:data` provides one impl for everyone. Detail: [07 — `:variants-*`](07-variants.md).

### 4.3 Decentralized Data/Domain
Business logic lives where it belongs:

- **Variant-specific rules** → `:variants-{id}` (fee calculation, validation thresholds, regulatory checks, formatting, capability flags)
- **Shared API/repo logic** → `:data` (DTOs, mapping, single Retrofit surface)
- **UI-specific logic** → `:features` packages (input formatting, navigation state, transient flows)
- **Account-scope state** → `Session` in `:core` + `LoggedInComponent` in `:app`
- **Never** → `:core` or `:aos-core`

This keeps the contract layer clean and the infrastructure layer stable.

### 4.4 Linear Build Performance
Adding a new feature folder must not increase build time more than the cost of compiling that folder's source. The Hybrid-Monolith design — one `:features` module with package-based feature boundaries — avoids the per-module Gradle overhead that plagues finely-modularized codebases. Detail: [14 — Build Performance](14-build-performance.md).

---

## 5. Notable Choices

| Choice | Why |
|---|---|
| One `FintechApi` instead of one Retrofit interface per variant | Server demuxes by user identity; the Android side has no business knowing which bank a user belongs to. Eliminates duplicated API code. |
| Variant modules contain *only* policies + DI | Without per-variant APIs, repositories, or DTOs, variant modules become small (~5–10 files). Onboarding a new variant is correspondingly cheap. |
| Login-time variant selection (no in-session swap) | The user-visible operation for "switch variant" is logout-then-login. The implementation is one Hilt component drop. No purge sequence, no stale-cache risk. |
| MG returns URLs + maintenance/force-update only | Keeps boot fast and the contract narrow. Feature flags use Firebase Remote Config; branding is local. |
| Departments are accounts under one login | `Session.accounts` + `activeAccountId` (a `StateFlow`) + an OkHttp interceptor stamping the active account ID on requests. No nested DI graph. |
| Single APK | No build flavors per company. Server returns the variant ID; one binary handles all of them. |

---

## 6. What This Document Doesn't Cover

This is the executive summary. For specifics, follow the doc index in the [README](../README.md). In particular:

- **What does the boot sequence look like in code?** → [10](10-boot-phases.md)
- **What does MG return, and how does the gate block bad releases?** → [11](11-mg-and-runtime-config.md)
- **How does the user switch between accounts inside a session?** → [12](12-departments-and-session.md)
- **How do I add a new variant?** → [13](13-onboarding-a-variant.md)
- **Why can't I just split `:features` into one module per flow?** → [14](14-build-performance.md)
