# 01 · Module Topology

> The dependency DAG is the most important diagram in this project. If you remember nothing else, remember this graph.

---

## 1. The Layered Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                            :aos-sdk                                  │   infrastructure
└──────────────────────────────────────────────────────────────────────┘
                                  ▲
        ┌──────────────────────── ┴ ──────────────────────────┐
        │                                                     │
┌──────────────────┐                              ┌──────────────────────┐
│      :core       │   contracts                  │    :design-system    │   UI primitives
└──────────────────┘                              └──────────────────────┘
        ▲      ▲      ▲      ▲                         ▲      ▲      ▲
        │      │      │      │                         │      │      │
    ┌───┴──┐  ┌┴─────┐ ┌┴─────────────┐ ┌──────────┐ ┌──────────────────┐  ┌──────────────┐
    │:data │  │:tn:b │ │:tn:b/{tenant}│ │ :features│ │:features-chatbot │  │:features-{n}│
    └───┬──┘  └──┬───┘ └──────┬───────┘ └─────┬────┘ └─────────┬────────┘  └──────┬──────┘
        │        │            │               │                │                  │
        │        │   (Gradle dep ↑)           │                │                  │
        │        └────────────┘               │                │                  │
        └────────────────────────────┴───────┴────────────────┴──────────────────┘
                                              ▲
┌──────────────────────────────────────────────────────────────────────┐
│                                :app                                  │   orchestrator
└──────────────────────────────────────────────────────────────────────┘
```

Where:
- `:aos-sdk` (formerly `:aos-core`) — infrastructure submodule, banking-agnostic, git-tag-released. Consumed by multiple Android products.
- `:tn:b` is shorthand for `:tenants:{region}:base` (one per region, e.g., `:tenants:cambodia:base`, `:tenants:korea:base`).
- `:tn:b/{tenant}` is shorthand for `:tenants:{region}:{tenantSlug}` (e.g., `:tenants:cambodia:nh`, `:tenants:korea:shinsegae`). Each concrete tenant module **depends on its region base** via Gradle.
- `:features-{n}` is shorthand for `:features-{feature-name}` (e.g., `:features-kyc`, `:features-support-chat`, `:features-branch-locator`, `:features-bakong-disputes`).

### Reading the graph

- An arrow `↑` from a lower row to a higher row means the lower module **depends on** the higher one.
- `:app` sits at the **bottom** because it depends on everything; only it can wire concrete implementations into the runtime.
- `:core` and `:design-system` sit at the **top of the product layer**, side by side: `:core` owns domain contracts, `:design-system` owns UI primitives. Neither depends on the other.
- `:aos-sdk` sits **above everything else** because infrastructure underlies all product code.
- Concrete tenant modules (`:tenants:{region}:{tenantSlug}`) have **one Gradle dependency on their region base** (`:tenants:{region}:base`). This is the structural mechanism that replaces what was previously a separate variant DI axis. Hilt resolution of regional policies requires the base to be on the classpath.
- The horizontal siblings on the middle layer (`:data`, every `:tenants:*:*`, `:features`, `:features-chatbot`, every `:features-{feature}`) **never depend on each other**, except the region-base ← concrete-tenant dependency edge.

---

## 2. Module Roles

| Module | Type | Dependencies | Depended on by |
|---|---|---|---|
| `:aos-sdk` | Submodule (Android library) | — | every other module |
| `:core` | Local (Android library) | `:aos-sdk` | `:data`, `:features`, `:features-chatbot`, `:features-{n}`, `:tenants:*:*`, `:app` |
| `:design-system` | Local (Android library) | `:aos-sdk` | `:features`, `:features-chatbot`, `:features-{n}`, `:app` |
| `:data` | Local (Android library) | `:core`, `:aos-sdk` | `:app` |
| `:features` | Local (Android library) | `:core`, `:design-system`, `:aos-sdk` | `:app` |
| `:features-chatbot` | Local (Android library) | `:core`, `:design-system`, `:aos-sdk` | `:app` |
| `:features-{feature-name}` | Local (Android library) | `:core`, `:design-system`, `:aos-sdk` | `:app` |
| `:tenants:{region}:base` | Local (Android library) | `:core`, `:aos-sdk` | `:tenants:{region}:{tenantSlug}` (sibling concrete tenants), `:app` |
| `:tenants:{region}:{tenantSlug}` | Local (Android library) | `:core`, `:aos-sdk`, **`:tenants:{region}:base`** | `:app` |
| `:app` | Application | All of the above | — |

The `:tenants:{region}:base` ← `:tenants:{region}:{tenantSlug}` Gradle edge is the **only** intra-tier dependency in the graph. Every other middle-layer sibling pair is forbidden.

---

## 3. Forbidden Imports (Compile-time Enforced)

These are the **hard rules**. Any violation must fail the build, not a code review.

| ❌ Forbidden | Why |
|---|---|
| `:features` → `:tenants:*:*` | Breaks Logic-Blind contract. `:features` must compile without knowing which tenants exist. |
| `:features` → `:data` | UI must depend only on `:core` interfaces; impls are wired at runtime by `:app`. |
| `:features` ↔ `:features-chatbot` ↔ `:features-{n}` | Sibling UI modules — no cross-references. Shared UI primitives go in `:design-system`. |
| `:tenants:{regionA}:{tenantX}` → `:tenants:{regionA}:{tenantY}` | Concrete tenants must be hermetic within their region. Cross-tenant imports are how regressions cascade. |
| `:tenants:{regionA}:*` → `:tenants:{regionB}:*` | No cross-region coupling at any level (base or concrete). If shared policy is genuinely cross-regional, promote to `:core`. |
| `:tenants:{regionA}:base` → `:tenants:{regionA}:{anyTenant}` | Region base must not depend on its own concrete tenants (would create a cycle). |
| `:tenants:*:*` → `:data` | Tenant modules contribute policies, not data plumbing. |
| `:tenants:*:*` → `:design-system` | Tenant modules have no UI; they don't need design primitives. |
| `:tenants:*:*` → `:features`, `:features-chatbot`, `:features-{n}` | Tenant modules are upstream of UI modules. |
| `:data` → `:tenants:*:*` | The data layer is tenant-agnostic; the server demuxes per user. |
| `:data` → `:design-system` | The data layer has no UI. |
| `:design-system` → `:core`, `:data`, `:features`, `:tenants:*:*` | The design system is tenant-agnostic and domain-agnostic. |
| `:core` → `:design-system`, `:data`, `:features`, `:tenants:*:*` | Reverses the contract direction. `:core` is the upstream contract, never a consumer. |
| `:aos-sdk` → anything in this project | The SDK must remain product-agnostic. Reusable across multiple Android products via git tag. |

**Mandatory edge:** concrete tenant module **must declare** Gradle dependency on `:tenants:{region}:base` (its own region base only). Forgetting this dependency means Hilt resolution of regional baseline policies will fail at boot — fail-fast at app start, not at runtime.

> **Enforcement plan:** in addition to the Gradle dependency declarations themselves, lint rules can pin forbidden module imports as errors. CI must fail any module's `build.gradle.kts` that declares a forbidden dependency. Additionally, a static-analysis rule must fail any `if (tenant.id == ...)` or `when (tenant.id) { ... }` outside `:tenants:*:*` and `:app/di/TenantResolverModule.kt`.

---

## 4. Why This Shape?

### 4.1 Why two foundation layers (`:aos-sdk` + `:core`)?

`:aos-sdk` is reused across non-banking Android products (e.g., a future insurance app, a merchant app, an officer dashboard) and is **product-agnostic**. It ships as a git-tagged versioned artifact. `:core` is project-specific (it knows about `Money`, `UserSession`, `DepartmentAccount`, lending repository interfaces). Conflating them couples infrastructure releases to product cycles.

### 4.2 Why `:design-system` separately from `:core`?

`:core` is the **domain** layer (banking types and contracts). `:design-system` is the **UI primitive** layer (theme tokens, components). They live in different conceptual layers and have different stability profiles. More practically: if `:design-system` were inside `:core`, every theme tweak would force a recompile of `:data`, every `:tenants:*:*`, and every product module. Keeping them separate confines blast radius and lets non-UI modules (`:data`, tenant modules) skip Compose dependencies entirely.

### 4.3 Why `:data` separately from `:core`?

`:core` is the contract layer — interfaces and immutable models. `:data` is the implementation: Retrofit + DTOs + mapping + repository classes. Putting impls in `:core` would make every API tweak recompile every consumer of `:core` (which is every product module). Keeping `:data` separate confines the recompile blast radius for what will be the most-edited surface in the project.

### 4.4 Why `:features` as a hybrid-monolith?

Per-feature Gradle modules sound clean but explode Gradle's per-module configuration cost. With dozens of flows, a strict modular approach pays a fixed Gradle tax dozens of times. Package boundaries inside one module give the **organization benefits** without the **build cost**. Detail: [14 — Build Performance](14-build-performance.md).

### 4.5 Why `:features-chatbot` and `:features-{feature}` are exempt

Two triggers for breaking out a feature module:

1. **Heavy unique dependencies** — Chatbot SDKs typically pull in tens of MB (NLP models, transitive native libs). Co-locating that with the main UI engine penalizes every incremental build of every other feature. `:features-chatbot` is the canonical example; `:features-kyc` (CameraX + ML Kit), `:features-support-chat` (Sendbird SDK), and `:features-branch-locator` (Google Maps) follow the same rationale.
2. **Tenant-locked features** — A feature with its own API + DTOs + screens that only one tenant uses (e.g., a regulator-mandated flow specific to that market) goes in `:features-{feature-name}`, gated by a `TenantCapabilities` flag. Detail: [07 — `:tenants:*` § "When the Tenant Has Unique Features"](07-variants.md).

Both share the same dependency shape — `:core` + `:design-system` + `:aos-sdk`, no sibling cross-edges.

### 4.6 Why tenants are organized as `:tenants:{region}:{tenantSlug}` hierarchy

- **Visibility** — a single root listing under `:tenants/` answers "what regions and tenants do we support?"
- **Symmetry** — every concrete tenant has the same internal layout (`policy/`, `format/`, `flags/`, `capability/`, `di/`), enforced by convention.
- **Onboarding** — adding a tenant is strictly additive: new directory, new `include()` line, new catalogue entry. New regions follow the same shape with one extra step (the region base).
- **Isolation** — Gradle module dependencies enforce that tenant A's code cannot reach tenant B's. A package boundary inside one module would not (Kotlin's `internal` is per-module).
- **Regional sharing without a DI axis** — the region-base ← concrete-tenant Gradle dependency lets concrete tenants reuse regional policy classes (KHR formatter, KH compliance thresholds, etc.) without a separate "variant" DI dimension. One axis (tenant), one resolver, one map.

The naming convention is `tenants/{region}/{tenantSlug}` (filesystem path) and `:tenants:{region}:{tenantSlug}` (Gradle path).

---

## 5. Internal Package Convention

Every module follows a parallel internal layout:

| Layer | `:features` | `:tenants:cambodia:nh` | `:data` | `:design-system` |
|---|---|---|---|---|
| **Public surface** | `com.<org>.features.loan.apply.LoanApplyScreen` | `com.<org>.tenants.cambodia.nh.di.NhKhTenantModule` | `com.<org>.data.di.DataModule` | All of `com.<org>.design.*` (component primitives are intentionally public) |
| **Internal contracts** | `internal` Kotlin visibility | `internal` Kotlin visibility | `internal` Kotlin visibility | n/a — module is intentionally public-API |

Mark every class that isn't intentionally public as `internal`. The only types crossing module boundaries are: Composables that `:app` navigates to, Hilt modules, and the entire `:design-system` surface.

---

## 6. Build Graph Properties

A correct topology should yield these properties on `./gradlew :app:assembleDebug`:

| Property | Target |
|---|---|
| `:aos-sdk` recompile triggers | only when `aos-sdk/` source changes |
| `:core` recompile triggers | when `:core` or `:aos-sdk` change |
| `:design-system` recompile triggers | when `:design-system` or `:aos-sdk` change — **never** when `:core` changes |
| `:data` recompile triggers | when `:data`, `:core`, or `:aos-sdk` change |
| `:features` recompile triggers | when `:features`, `:core`, `:design-system`, or `:aos-sdk` change — **never** when `:data` or a tenant changes |
| `:tenants:cambodia:base` recompile triggers | when `:tenants:cambodia:base`, `:core`, or `:aos-sdk` change — **never** when other regions or concrete tenants change |
| `:tenants:cambodia:nh` recompile triggers | when itself, its region base, `:core`, or `:aos-sdk` change — **never** when sibling concrete tenants in the same or other regions change |
| `:app` recompile triggers | any module changes (this is correct — `:app` is the assembler) |

If a tenant change forces `:features` to recompile, the topology has been violated. If a `:data` change forces `:features` or `:tenants:*:*` to recompile, the topology has been violated. If a `:core` change forces `:design-system` to recompile, the topology has been violated. If a change in one region forces another region to recompile, the topology has been violated.

---

## 7. Cross-references

- Module specs: [02](02-aos-core.md) · [03](03-core.md) · [04](04-design-system.md) · [05](05-data.md) · [06](06-features.md) · [07](07-variants.md) · [08](08-app-orchestrator.md)
- Boot mechanics: [10 — Boot Phases](10-boot-phases.md)
- Build perf consequences: [14 — Build Performance](14-build-performance.md)
- Full project tree on one page: [17 — Project Structure](17-project-structure.md)
- Tenant behavioral model: [19 — Tenants and Regions](19-tenants-and-variants.md)
