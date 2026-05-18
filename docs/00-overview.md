# 00 · Architecture Overview

> Read this first. Everything else is implementation detail of the ideas here.

---

## 1. The Problem We're Solving

A single Android binary must service:

- **Multiple corporate customers** (POSCO, Lotte, NIA, Shinsegae, ITCen, WIPS, HANA, IBS, SPC, …) — selected at login from the user's company memberships, not at install
- **Multiple regions / regulators** (Korea, Cambodia, Vietnam) — driving currency, language defaults, tax rules, and partner-rail integrations
- **A few well-defined frontend variations** per corporate customer (formats, employee-ID regex, field visibility, approval-line shape, footer text, expense-evidence flavours)
- **Per-environment URL discovery** that must update without an app release
- **Multiple institution memberships per logged-in user** (one user, several `USE_INTT_ID` values — e.g. a consultant who can submit expenses for POSCO ICT *and* Lotte from the same login)
- **A unified server-side IPPP API** that already routes per user and per company code — the Android side does not need to know which customer organization a user belongs to in order to talk to the backend

The hostile failure mode is **Conditional Logic Sprawl**. In the current Bizplay codebase this is visible as a swarm of `DetailConfig.isXxx()` predicates — `isNIA()`, `isPOSCO_ICT()`, `isWIPS()`, `isShinsegae()`, `isLotte()`, `isITCen()`, `isHANA()`, `isIBS()`, `isSPC()`, `isChilsungBeverage()` — scattered across ViewModels, Activities, Fragments, and adapters. A single PR adjusting one customer's behaviour touches every flow the predicate appears in; regression risk per release scales with `O(customers × features)`. There are roughly **124 call sites** of `DetailConfig.isXxx()` in the existing code.

The framework eliminates this by making per-customer and per-region differences **polymorphic** through `:core` interfaces, and by making both the variant binding and the tenant binding a **one-time event at login**.

---

## 2. The Solution at a Glance

### 2.1 Two-Layer Foundation

Two stable layers, never one.

- **`:aos-core`** — *infrastructure that doesn't know it's an expense app*. Networking, encryption (SQLCipher, Secucen EdgeCrypto, Android Keystore), secure input (TransKey), malware detection (mVaccine), biometric, storage, logging, the `BizWebView` primitive. Versioned independently as a Git submodule. Reusable across non-expense projects.
- **`:core`** — *contracts that don't know which corporate customer*. Repository interfaces, domain models, `RuntimeConfig`, `Session`, `DepartmentAccount`, `VariantContext`, `TenantContext`, `TenantFlags`, `TenantParams`. Owns the shape of every interaction without prescribing the implementation.

### 2.2 Interfaces in `:core`, Implementations Split by Concern

The UI calls **interfaces**, never **implementations**. The implementation side has two homes:

- **`:data`** — implements `:core` repository interfaces. Owns the `Ippp*Api` Retrofit family (auth, receipt, approval, card, expense, OCR, notice) and the `Ippp*Repo` classes that call it. **Same code for every variant and every tenant** — the server demuxes per user + per company code.
- **`:variants-{id}`** — implements `:core` policy interfaces (region/regulator rules: currency formatting, tax thresholds, capability flags, business calendar, receipt rendering). **One module per region** — small, isolated, holds only what differs across regions. Customer-organization differences inside a region live under `:variants-{id}/tenants/{tenant-id}/` and are mostly captured as `TenantFlags` / `TenantParams` on `TenantContext` — see [19 — Tenants and Variants](19-tenants-and-variants.md).

```
:features (UI) ─→ :core (interfaces) ←─ :data (one Ippp*Api family + Ippp*Repo)
                                    ←─ :variants-kr (KrFeeCalculator, KrwAmountFormatter, …)
                                    ←─ :variants-kh (KhComplianceThresholds, …)
                                    ←─ :variants-vn (VndAmountFormatter, …)
                                    ←─ :variants-kr/tenants/nia/ (NIA structural impls if any)
                                    ←─ :variants-kr/tenants/shinsegae/ (Shinsegae structural impls)
```

`:features` is **Logic-Blind** — it cannot reach into `:data` or `:variants-*`. A compile-time guarantee, enforced by the build graph.

### 2.3 Selection-Once at Login

When the user logs in, the server returns the variant ID *and* the tenant ID. `:app` builds a `LoggedInComponent` with that variant's policy bindings *and* that tenant's profile — once. The component lives until logout. No runtime swap, no purge sequence.

To change variant or tenant, the user logs out. The component is dropped, all `@LoggedInScoped` state becomes unreachable, and the next login builds a fresh component. (Switching between *institutions the user already belongs to* — the `USE_INTT_ID` axis — is a third, lighter mechanism: a `Session.activeAccountId` flip with no DI rebuild. See [12](12-departments-and-session.md).)

The boot sequence:

```
[boot]   hardcoded MgGate URL → MgGate → RuntimeConfig (urls, maintenance, forceUpdate, webRoutes)
[gate]   if maintenance.down or version < min → MaintenanceGate, hard stop
[login]  user authenticates (optionally picks institution) → server returns variantId + tenantId + tenantFlags + tenantParams + accounts[]
[bind]   build LoggedInComponent with :variants-{variantId} policies + tenant profile
[main]   navigate into feature graph; the Session is now active
[logout] tear down LoggedInComponent → return to login
```

→ Detail: [10 — Boot Phases](10-boot-phases.md)

---

## 3. The Modules

| Module | Layer | One-line purpose |
|---|---|---|
| `:aos-core` | Infrastructure | Project-agnostic libs: network, security (SQLCipher / TransKey / mVaccine / Secucen), storage, logging, `BizWebView` |
| `:core` | Domain & Contract | Repository interfaces, domain models, `RuntimeConfig`, `Session`, `VariantContext`, `TenantContext`, MVI base |
| `:data` | Data | `Ippp*Api` (Retrofit family) + repository implementations of `:core` interfaces |
| `:features` | UI Engine | Logic-Blind Compose screens organized by package |
| `:features-scanner` | Isolated UI | Heavy-SDK feature kept off the main UI engine for build perf (io.card + camera + OCR + sasapi scraping) |
| `:features-hipass` | Variant-locked UI | Korea-only highway-toll capture with its own API + DTOs + screens |
| `:variants-{id}` | Variant Silo | Variant-specific policies + DI bindings, plus a `tenants/{id}/` subtree for corporate-customer profiles (one module per region) |
| `:app` | Orchestrator | Application class, `BootCoordinator`, navigation host |

Detailed in [01 — Module Topology](01-module-topology.md).

---

## 4. The Four Architectural Promises

### 4.1 Boot-Time Discovery, Login-Time Selection
The only network configuration baked into the binary is the MgGate URL per build environment. Everything else — IPPP API URLs (today: `Conf.IPPP_SITE_URL`), approval-form URL (today: `Constant.MG.C_APPROVAL_URL`), member URL, logo URL, partner URLs, maintenance state, version floor — comes from MgGate at cold start. The variant *and* the tenant come from the auth response. A backend URL change doesn't ship a new APK; a new tenant doesn't reach `:features` source code. Detail: [11 — MG and Runtime Config](11-mg-and-runtime-config.md).

### 4.2 Polymorphic Scalability
There is no `if (variantId == X)` and no `if (tenant.id == "nia")` anywhere in the codebase. Polymorphism does the dispatch. Where regions differ, `:features` calls a `:core` policy interface (e.g., `expenseAmountPolicy.validate(amount)`); the active variant module supplies the implementation. Where customers inside a region differ, the UI reads `tenant.flags.hidesEmployeeId` (a named field) or — for layout-level differences — calls a structural `TenantPolicy` interface bound by `@TenantKey` multibindings. Where everything is the same — including the API surface — `:data` provides one impl for everyone. Detail: [07 — `:variants-*`](07-variants.md) · [19 — Tenants and Variants](19-tenants-and-variants.md).

### 4.3 Decentralized Data/Domain
Business logic lives where it belongs:

- **Region-specific rules** → `:variants-{id}` (fee calculation, validation thresholds, tax/VAT rules, formatting, capability flags, receipt rendering, business calendar)
- **Customer-org differences** → `TenantFlags` / `TenantParams` on `TenantContext`, or per-tenant structural policy impls under `:variants-{region}/tenants/{tenant-id}/`
- **Shared API/repo logic** → `:data` (DTOs, mapping, `Ippp*Api` family — variant- and tenant-agnostic)
- **UI-specific logic** → `:features` packages (input formatting, navigation state, transient flows)
- **Account-scope state** → `Session` in `:core` + `LoggedInComponent` in `:app`
- **Never** → `:core` or `:aos-core`

This keeps the contract layer clean and the infrastructure layer stable.

### 4.4 Linear Build Performance
Adding a new feature folder (e.g. a new expense category, a new approval shortcut) must not increase build time more than the cost of compiling that folder's source. The Hybrid-Monolith design — one `:features` module with package-based feature boundaries — avoids the per-module Gradle overhead that plagues finely-modularized codebases. Detail: [14 — Build Performance](14-build-performance.md).

---

## 5. Notable Choices

| Choice | Why |
|---|---|
| One `Ippp*Api` family instead of one Retrofit interface per region | The IPPP backend demuxes by `USE_INTT_ID` + `COMPANY_CD`; the Android side has no business knowing which customer or region a user belongs to. Eliminates duplicated API code. |
| Variant modules contain *only* region policies + DI (plus per-tenant profiles) | Without per-variant APIs, repositories, or DTOs, variant modules become small (~10–15 files for the region-level shape; per-tenant profiles are 1–3 files each). Onboarding a new region or a new customer org is correspondingly cheap. |
| Login-time variant + tenant selection (no in-session swap) | The user-visible operation for "switch tenant" is logout-then-login. The implementation is one Hilt component drop. No purge sequence, no stale-cache risk. (Switching between *institution memberships the user already has* is a separate, lighter axis — `Session.activeAccountId` flip.) |
| MgGate returns URLs + maintenance/force-update + webRoutes only | Keeps boot fast and the contract narrow. Feature flags use Firebase Remote Config; per-tenant flag *values* come on the login response, not from MgGate. |
| Departments are accounts under one login | `Session.accounts` (multiple `DepartmentAccount`s, each tied to a `USE_INTT_ID`) + `activeAccountId` (a `StateFlow`) + an OkHttp interceptor stamping the active account ID + company code on requests. No nested DI graph. |
| Single APK | No build flavors per region or per customer. Server returns the variant and tenant; one binary handles all of them. |
| `BizWebView` is a `:aos-core` primitive, not a feature | Approval UI, terms screens, partner mall pages, KakaoPay link flows, BizDoc — all today implemented as WebViews — share one hardened primitive with one JS bridge contract. Replaces today's ad-hoc `BizWebview` + `BrowserBridge` usage. |

---

## 6. What This Document Doesn't Cover

This is the executive summary. For specifics, follow the doc index in the [README](../README.md). In particular:

- **What does the boot sequence look like in code?** → [10](10-boot-phases.md)
- **What does MgGate return, and how does the gate block bad releases?** → [11](11-mg-and-runtime-config.md)
- **How does the user switch between institutions inside a session (today's `SelectUserInttIdActivity`)?** → [12](12-departments-and-session.md)
- **How do I add a new variant (region)?** → [13](13-onboarding-a-variant.md)
- **How do I onboard a new corporate customer (tenant)?** → [19](19-tenants-and-variants.md)
- **Why can't I just split `:features` into one module per flow?** → [14](14-build-performance.md)
