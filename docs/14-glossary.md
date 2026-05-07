# 14 · Glossary

> Terms specific to the Nexus Framework. Use these consistently in code, comments, commits, and conversation.

---

### `:aos-core`

The infrastructure submodule. Project-agnostic plumbing (network, security, storage, logging) reusable across non-banking projects. Does not know it's a bank.

→ [02 — `:aos-core`](02-aos-core.md)

---

### `:core`

The contract layer. Houses repository interfaces, domain models, MVI base contracts, and the `TenantContext`. Imported by both `:features` and `:tenants:*`; depends on neither. The "blueprint" both sides build to.

→ [03 — `:core`](03-core.md)

---

### Conditional Logic Sprawl

The anti-pattern Nexus exists to prevent: tenant-specific behavior expressed as `if (tenantId == X) … else if (tenantId == Y)` chains scattered across the codebase. Replaced by polymorphic dispatch through `:core` interfaces.

→ [00 — Overview](00-overview.md)

---

### Decentralized Data/Domain

Architectural principle: business logic lives in `:tenants:*` (for tenant differentiation) or `:features` packages (for shared UI logic). Never in `:core` or `:aos-core`. Keeps the contract and infrastructure layers clean.

---

### Dual-Core System

The two-layer foundation: `:aos-core` (infrastructure, project-agnostic) + `:core` (domain, project-specific). The split prevents infrastructure releases from being coupled to product cycles, and prevents product domain bloat from polluting infrastructure.

---

### `EnvironmentInterceptor`

The `:aos-core` OkHttp interceptor that rewrites request URLs at call time using the `BaseUrlProvider`. Allows the active environment to change without rebuilding the OkHttp client.

→ [09 — Environment Configuration](09-environment-configuration.md)

---

### `:features`

The **Hybrid-Monolith** UI engine. One Gradle module containing all standard banking flows organized by package: `auth/`, `transfer/`, `account/`, `splash/`, `common/`, etc. Logic-Blind by construction.

→ [04 — `:features`](04-features.md)

---

### `:features-chatbot`

An **isolated feature module**, sibling to `:features`, kept separate because chatbot SDKs pull in heavy unique dependencies that should not penalize incremental builds of unrelated features. The prototype for "isolate when SDK weight justifies it".

---

### Hybrid-Monolith

The shape of `:features`: a single Gradle module with internal feature packages. Combines the **organizational benefits** of feature modules with the **build performance** of a single module. Discipline (lint + code review) replaces the build system as the boundary enforcer at the package level.

→ [12 — Build Performance](12-build-performance.md)

---

### Linear Build Performance

Strategic Requirement #4. Adding a new feature folder must increase build cost only proportional to the new code's compile cost — not impose a fixed overhead per folder. Drives the choice of Hybrid-Monolith for `:features`.

---

### Logic-Blind

The constraint that defines `:features`. UI code may consume `:core` interfaces but **must not import any `:tenants:*` module**. A `:features` ViewModel cannot, by construction, know which tenant is active. Enforced by the build graph.

→ [04 — `:features`](04-features.md)

---

### Logic Silo

A `:tenants:[id]` module. Houses everything specific to one tenant: APIs, repository implementations, DTOs, policies, and the tenant's Hilt module. Strictly isolated from sibling silos — `:tenants:tenants-kh` cannot import `:tenants:tenants-vn`.

→ [05 — `:tenants:*`](05-tenants.md)

---

### MVI

Model-View-Intent. The architecture pattern used by every screen in `:features` and `:features-chatbot`. State is immutable, exhausted by `UiState`. User actions are `UiEvent`. One-shot side effects (navigation, toasts) are `UiEffect`. Strict unidirectional data flow.

→ [07 — MVI Pattern](07-mvi-pattern.md)

---

### Polymorphic Scalability

Strategic Requirement #2. `:features` calls a `:core` interface; `:app` provides the implementation matching the active tenant via Hilt. There is no `if (tenant == X)` anywhere in the codebase — polymorphism is the dispatch.

---

### `TenantBindingModule`

The `:app/di/` Hilt module that orchestrates which tenant bindings are live. In practice, Hilt's `@InstallIn(TenantComponent::class)` annotations on each tenant's own DI module make the registration automatic — `TenantBindingModule` exists primarily for orchestration concerns (component lifecycle).

---

### `TenantComponent`

The custom Hilt component scoped to the active tenant. Created by `TenantComponentManager.recreate(...)` and destroyed when the tenant switches. All `@TenantScoped` instances belong to exactly one component.

→ [08 — Runtime Tenant Switching](08-runtime-tenant-switching.md)

---

### `TenantContext`

Snapshot type in `:core` describing the active tenant: ID, display name, market code, default currency, base URL key. Read by `:features` for **display purposes only** (e.g., currency formatting), never for conditional logic.

---

### `TenantEntryPointAccessor`

The lookup type in `:app` that exposes the live `TenantComponent`'s bindings to ViewModels. Resolved at call time so that hot-swapping the tenant is visible to existing ViewModel instances.

---

### `TenantProvider`

Interface in `:core` exposing `currentTenant: StateFlow<TenantContext>` and `switchTo(...)`. The single source of truth for "who am I a bank for, right now?" Implemented by `DefaultTenantProvider` in `:app`.

---

### `@TenantScoped`

The custom Hilt scope annotation. Instances annotated `@TenantScoped` live as long as the current `TenantComponent` and are destroyed (eligible for GC) when the component is rebuilt.

---

### `TenantSwitcher`

The `:app` singleton that orchestrates the tenant switch sequence: lock → cancel → purge → destroy → rebuild → commit → unlock. The only place where switch mechanics should be modified.

→ [08 — Runtime Tenant Switching](08-runtime-tenant-switching.md)

---

### Zero-Trust Runtime Switching

Strategic Requirement #1. Treat every tenant switch as a security event: purge the current session, clear sensitive memory and caches, and re-inject the DI graph from a clean slate before serving any tenant-B request. No app restart.

→ [08 — Runtime Tenant Switching](08-runtime-tenant-switching.md)

---

### Cross-references

- For a top-level summary: [00 — Overview](00-overview.md)
- For the architectural shape that ties these terms together: [01 — Module Topology](01-module-topology.md)
