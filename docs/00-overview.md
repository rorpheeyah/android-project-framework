# 00 · Architecture Overview

> Read this first. Everything else is implementation detail of the ideas here.

---

## 1. The Problem We're Solving

A single Android binary must service:

- **Multiple customer organizations (tenants)** — selected at login, not at install. The same APK can serve NH's tenants in Cambodia today and outsource-project tenants tomorrow.
- **A few well-defined frontend variations** (formats, validation thresholds, fee rules, capability flags, label strings)
- **Per-environment URL discovery** that must update without an app release
- **Multiple accounts per logged-in user** when the consuming product supports them (single-account is the degenerate case; the infra stays in place for outsource readiness)
- **A unified server-side API** that already routes per user — the Android side does not need to know which organization a user belongs to in order to talk to the backend

The hostile failure mode is **Conditional Logic Sprawl** — tenant-specific behavior expressed as `if (tenant.id == "cambodia:nh") … else if (tenant.id == "korea:shinsegae") …` chains scattered across ViewModels, repositories, and validators. A single PR touches every flow; regression risk per release scales with `O(tenants × features)`.

The framework eliminates this by making tenant differences **polymorphic** through `:core` interfaces, and by making the tenant binding a **one-time event at login**.

---

## 2. The Solution at a Glance

### 2.1 Two-Layer Foundation

Two stable layers, never one.

- **`:aos-sdk`** — *infrastructure that doesn't know it's a bank*. Networking, encryption, biometric, storage, logging, camera, ML, imaging, PDF, push channels, deep links, locale management, WebView. Versioned independently as a Git submodule pinned to a git tag. Reusable across non-banking Android products.
- **`:core`** — *contracts that don't know which organization*. Repository interfaces, domain models, `RuntimeConfig`, `Session`, `DepartmentAccount`, `TenantContext`. Owns the shape of every interaction without prescribing the implementation.

### 2.2 Interfaces in `:core`, Implementations Split by Concern

The UI calls **interfaces**, never **implementations**. The implementation side has two homes:

- **`:data`** — implements `:core` repository interfaces. Owns the unified `Fintech*Api` family (Retrofit interfaces split by feature area) and the `*Repo` classes that call them. **Same code for every tenant** — the server demuxes per user.
- **`:tenants:{region}:{tenantSlug}`** — implements `:core` policy interfaces (validation, fee rules, formatting, capability flags) at the concrete-tenant layer. Concrete tenant modules depend on a `:tenants:{region}:base` Gradle module that provides shared regional policies (currency formatter, regulator rules, compliance thresholds, etc.).

```
:features (UI) ─→ :core (interfaces) ←─ :data (Fintech*Api + *Repo)
                                    ←─ :tenants:cambodia:nh (NhKhStaffIdValidator, …)
                                    ←─ :tenants:cambodia:base (KhrAmountFormatter, …)
```

`:features` is **Logic-Blind** — it cannot reach into `:data` or `:tenants:*:*`. A compile-time guarantee, enforced by the build graph.

### 2.3 Selection-Once at Login

When the user logs in, the server returns the tenant ID (composite `<region>:<tenantSlug>`). `:app` builds a `LoggedInComponent` with that tenant's policy bindings — once. The component lives until logout. No runtime swap, no purge sequence.

To change tenants, the user logs out. The component is dropped, all `@LoggedInScoped` state becomes unreachable, and the next login builds a fresh component.

The boot sequence:

```
[boot]   hardcoded MG URL → MG → RuntimeConfig (urls, maintenance, forceUpdate)
         (falls back to stale-config cache up to 24h if MG unreachable)
[gate]   if maintenance.down or version < min → MaintenanceGate, hard stop
[login]  user authenticates → server returns tenantId + accounts[]
[bind]   build LoggedInComponent with :tenants:{region}:{tenantSlug} policies
[main]   navigate into feature graph; the Session is now active
[logout] tear down LoggedInComponent → return to login
```

→ Detail: [10 — Boot Phases](10-boot-phases.md)

---

## 3. The Modules

| Module | Layer | One-line purpose |
|---|---|---|
| `:aos-sdk` | Infrastructure | Product-agnostic libs: network, security, storage, logging, camera, ML, imaging, PDF, push, deeplink, locale, WebView |
| `:core` | Domain & Contract | Repository interfaces, domain models, `RuntimeConfig`, `Session`, `TenantContext`, MVI base |
| `:data` | Data | `Fintech*Api` (Retrofit) + repository implementations of `:core` interfaces, including third-party clients (`:data/external/`) |
| `:features` | UI Engine | Logic-Blind Compose screens organized by package |
| `:features-chatbot` | Isolated UI | Heavy-SDK feature kept off the main UI engine for build perf |
| `:features-kyc`, `:features-support-chat`, `:features-branch-locator`, … | Isolated UI siblings | Heavy-SDK features (CameraX + ML Kit, Sendbird, Google Maps) |
| `:tenants:{region}:base` | Region Baseline | Shared regulator/region policies (currency, compliance, calendar, OTP) |
| `:tenants:{region}:{tenantSlug}` | Concrete Tenant | Tenant-specific policies + Hilt module with `@TenantKey` bindings |
| `:app` | Orchestrator | Application class, `BootCoordinator`, navigation host |

Detailed in [01 — Module Topology](01-module-topology.md).

---

## 4. The Four Architectural Promises

### 4.1 Boot-Time Discovery, Login-Time Selection
The only network configuration baked into the binary is the MG URL per build environment (plus a single `staleConfigTtl` constant for the offline fallback window). Everything else — main API URLs, third-party SDK app-ids (Sendbird, Google Maps), maintenance state, version floor — comes from MG at cold start. The tenant comes from the auth response. A backend URL change doesn't ship a new APK; a new tenant doesn't reach `:features` source code. Detail: [11 — MG and Runtime Config](11-mg-and-runtime-config.md).

### 4.2 Polymorphic Scalability
There is no `if (tenant.id == X)` anywhere in the codebase outside the single dispatch point in `:app/di/TenantResolverModule.kt`. Polymorphism does the dispatch. Where tenants differ, `:features` calls a `:core` policy interface (e.g., `loanEligibilityPolicy.validate(applicant)`); the active tenant module supplies the implementation. Where tenants are the same — including the API surface — `:data` provides one impl for everyone. Region grouping is structural (Gradle module hierarchy), not a runtime axis. Detail: [07 — `:tenants:*`](07-variants.md).

### 4.3 Decentralized Data/Domain
Business logic lives where it belongs:

- **Tenant-specific rules** → `:tenants:{region}:{tenantSlug}` (organization-specific validators, staff-ID formats, label strings)
- **Region-wide rules** → `:tenants:{region}:base` (regulator-mandated thresholds, currency formatter, business calendar, OTP delivery)
- **Shared API/repo logic** → `:data` (DTOs, mapping, unified Retrofit surface)
- **UI-specific logic** → `:features` packages (input formatting, navigation state, transient flows)
- **Account-scope state** → `Session` in `:core` + `LoggedInComponent` in `:app`
- **Never** → `:core` or `:aos-sdk`

This keeps the contract layer clean and the infrastructure layer stable.

### 4.4 Linear Build Performance
Adding a new feature folder must not increase build time more than the cost of compiling that folder's source. The Hybrid-Monolith design — one `:features` module with package-based feature boundaries — avoids the per-module Gradle overhead that plagues finely-modularized codebases. Heavy-SDK features are the documented exception (`:features-chatbot`, `:features-kyc`, `:features-support-chat`, `:features-branch-locator`). Detail: [14 — Build Performance](14-build-performance.md).

---

## 5. Notable Choices

| Choice | Why |
|---|---|
| Unified `Fintech*Api` family in `:data` instead of one Retrofit interface per tenant | Server demuxes by user identity; the Android side has no business knowing which organization a user belongs to. Eliminates duplicated API code. |
| Tenant modules contain *only* policies + DI | Without per-tenant APIs, repositories, or DTOs, tenant modules become small (~5–15 files). Onboarding a new tenant is correspondingly cheap. |
| Login-time tenant selection (no in-session swap) | The user-visible operation for "switch tenant" is logout-then-login. The implementation is one Hilt component drop. No purge sequence, no stale-cache risk. |
| MG returns URLs + maintenance/force-update + 3rd-party app-ids | Keeps boot fast and the contract narrow. Feature flags use Firebase Remote Config; branding is local. Stale-config fallback (24h) for offline robustness. |
| Departments are accounts under one login | `Session.accounts` + `activeAccountId` (a `StateFlow`) + an OkHttp interceptor stamping the active account ID on requests. No nested DI graph. Single-account products hide the switcher declaratively. |
| Single APK | No build flavors per organization. Server returns the tenant ID; one binary handles all of them. |
| Region as Gradle hierarchy, not DI axis | Concrete tenants depend on their region base for shared policies. One axis, one resolver, one map — simpler than the historical two-axis (variant + tenant) model. |

---

## 6. What This Document Doesn't Cover

This is the executive summary. For specifics, follow the doc index in the [README](../README.md). In particular:

- **What does the boot sequence look like in code?** → [10](10-boot-phases.md)
- **What does MG return, and how does the gate block bad releases?** → [11](11-mg-and-runtime-config.md)
- **How does the user switch between accounts inside a session?** → [12](12-departments-and-session.md)
- **How do I add a new tenant or region?** → [13](13-onboarding-a-variant.md)
- **What is the tenant model (flags, params, structural escalation)?** → [19](19-tenants-and-variants.md)
- **Why can't I just split `:features` into one module per flow?** → [14](14-build-performance.md)
