# 01 · Module Topology

> The dependency DAG is the most important diagram in this project. If you remember nothing else, remember this graph.

---

## 1. The Layered Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                            :aos-core                                 │   infrastructure
└──────────────────────────────────────────────────────────────────────┘
                                  ▲
        ┌──────────────────────── ┴ ──────────────────────────┐
        │                                                     │
┌──────────────────┐                              ┌──────────────────────┐
│      :core       │   contracts                  │    :design-system    │   UI primitives
└──────────────────┘                              └──────────────────────┘
        ▲      ▲      ▲                                ▲      ▲      ▲
        │      │      │                                │      │      │
    ┌───┴──┐  ┌┴─────┐ ┌──────────┐ ┌──────────────────┐  ┌──────────────┐
    │:data │  │:vrnt│  │ :features│  │:features-chatbot │  │:features-{n}│
    └───┬──┘  └──┬──┘  └─────┬────┘  └─────────┬────────┘  └──────┬──────┘
        │        │           │                 │                  │
        └────────┴───────────┴─────────────────┴──────────────────┘
                                  ▲
┌──────────────────────────────────────────────────────────────────────┐
│                                :app                                  │   orchestrator
└──────────────────────────────────────────────────────────────────────┘
```

Where:
- `:vrnt` is shorthand for `:variants-{id}` (one module per region/company)
- `:features-{n}` is shorthand for `:features-{feature-name}` (e.g. `:features-bakong-disputes`)

### Reading the graph

- An arrow `↑` from a lower row to a higher row means the lower module **depends on** the higher one.
- `:app` sits at the **bottom** because it depends on everything; only it can wire concrete implementations into the runtime.
- `:core` and `:design-system` sit at the **top of the product layer**, side by side: `:core` owns domain contracts, `:design-system` owns UI primitives. Neither depends on the other.
- `:aos-core` sits **above everything else** because infrastructure underlies all product code.
- The five siblings in the middle row (`:data`, `:variants-{id}`, `:features`, `:features-chatbot`, `:features-{feature}`) **never depend on each other**.

---

## 2. Module Roles

| Module | Type | Dependencies | Depended on by |
|---|---|---|---|
| `:aos-core` | Submodule (Android library) | — | every other module |
| `:core` | Local (Android library) | `:aos-core` | `:data`, `:features`, `:features-chatbot`, `:features-{n}`, `:variants-*`, `:app` |
| `:design-system` | Local (Android library) | `:aos-core` | `:features`, `:features-chatbot`, `:features-{n}`, `:app` |
| `:data` | Local (Android library) | `:core`, `:aos-core` | `:app` |
| `:features` | Local (Android library) | `:core`, `:design-system`, `:aos-core` | `:app` |
| `:features-chatbot` | Local (Android library) | `:core`, `:design-system`, `:aos-core` | `:app` |
| `:features-{feature-name}` | Local (Android library) | `:core`, `:design-system`, `:aos-core` | `:app` |
| `:variants-kh` | Local (Android library) | `:core`, `:aos-core` | `:app` |
| `:variants-vn` | Local (Android library) | `:core`, `:aos-core` | `:app` |
| `:variants-ppcbank` | Local (Android library) | `:core`, `:aos-core` | `:app` |
| `:app` | Application | All of the above | — |

---

## 3. Forbidden Imports (Compile-time Enforced)

These are the **hard rules**. Any violation must fail the build, not a code review.

| ❌ Forbidden | Why |
|---|---|
| `:features` → `:variants-*` | Breaks Logic-Blind contract. `:features` must compile without knowing which variants exist. |
| `:features` → `:data` | UI must depend only on `:core` interfaces; impls are wired at runtime by `:app`. |
| `:features` ↔ `:features-chatbot` ↔ `:features-{n}` | Sibling UI modules — no cross-references. Shared UI primitives go in `:design-system`. |
| `:variants-{A}` → `:variants-{B}` | Variants must be hermetic. Cross-variant imports are how regressions cascade. |
| `:variants-*` → `:data` | Variants contribute policies, not data plumbing. |
| `:variants-*` → `:design-system` | Variants have no UI; they don't need design primitives. |
| `:data` → `:variants-*` | The data layer is variant-agnostic; the server demuxes per user. |
| `:data` → `:design-system` | The data layer has no UI. |
| `:design-system` → `:core`, `:data`, `:features`, `:variants-*` | The design system is variant-agnostic and domain-agnostic. |
| `:core` → `:design-system`, `:data`, `:features`, `:variants-*` | Reverses the contract direction. `:core` is the upstream contract, never a consumer. |
| `:aos-core` → anything in this project | The submodule must remain project-agnostic. |

> **Enforcement plan:** in addition to the Gradle dependency declarations themselves, lint rules can pin forbidden module imports as errors. CI must fail any module's `build.gradle.kts` that declares a forbidden dependency.

---

## 4. Why This Shape?

### 4.1 Why two foundation layers (`:aos-core` + `:core`)?

`:aos-core` is reused across non-banking projects (e.g., a future insurance app) and so it must be **product-agnostic**. `:core` is project-specific (it knows about `Money`, `UserSession`, `DepartmentAccount`, `TransferRepository`). Conflating them couples infrastructure releases to product cycles.

### 4.2 Why `:design-system` separately from `:core`?

`:core` is the **domain** layer (banking types and contracts). `:design-system` is the **UI primitive** layer (theme tokens, components). They live in different conceptual layers and have different stability profiles. More practically: if `:design-system` were inside `:core`, every theme tweak would force a recompile of `:data`, `:variants-*`, and every product module. Keeping them separate confines blast radius and lets non-UI modules (`:data`, `:variants-*`) skip Compose dependencies entirely.

### 4.3 Why `:data` separately from `:core`?

`:core` is the contract layer — interfaces and immutable models. `:data` is the implementation: Retrofit + DTOs + mapping + repository classes. Putting impls in `:core` would make every API tweak recompile every consumer of `:core` (which is every product module). Keeping `:data` separate confines the recompile blast radius for what will be the most-edited surface in the project.

### 4.4 Why `:features` as a hybrid-monolith?

Per-feature Gradle modules sound clean but explode Gradle's per-module configuration cost. With ~30 banking flows, a strict modular approach pays a fixed Gradle tax 30 times. Package boundaries inside one module give the **organization benefits** without the **build cost**. Detail: [14 — Build Performance](14-build-performance.md).

### 4.5 Why `:features-chatbot` and `:features-{feature}` are exempt

Two triggers for breaking out a feature module:

1. **Heavy unique dependencies** — Chatbot SDKs typically pull in tens of MB (NLP models, transitive native libs). Co-locating that with the main UI engine penalizes every incremental build of every other feature. `:features-chatbot` is the canonical example.
2. **Variant-locked features** — A feature with its own API + DTOs + screens that only one variant uses (e.g., a regulator-mandated flow specific to that market) goes in `:features-{feature-name}`, gated by a `VariantCapabilities` flag. Detail: [07 — `:variants-*` § "When the Variant Has Unique Features"](07-variants.md).

Both share the same dependency shape — `:core` + `:design-system` + `:aos-core`, no sibling cross-edges.

### 4.6 Why variants are flat sibling modules

- **Visibility** — a single root listing answers "what variants do we support?"
- **Symmetry** — every variant has the same internal layout (`policy/`, `format/`, `capability/`, `support/`, `di/`), enforced by convention.
- **Onboarding** — adding a variant is strictly additive: new directory, new `include()` line, new catalogue entry.
- **Isolation** — Gradle module dependencies enforce that variant A's code cannot reach variant B's. A package boundary inside one module would not (Kotlin's `internal` is per-module).

The naming convention is `variants-<id>` (kebab-case, no nested colon path). Mirrors `features-chatbot` exactly.

---

## 5. Internal Package Convention

Every module follows a parallel internal layout:

| Layer | `:features` | `:variants-kh` | `:data` | `:design-system` |
|---|---|---|---|---|
| **Public surface** | `com.<org>.features.transfer.TransferScreen` | `com.<org>.variants.kh.di.KhVariantModule` | `com.<org>.data.di.DataModule` | All of `com.<org>.design.*` (component primitives are intentionally public) |
| **Internal contracts** | `internal` Kotlin visibility | `internal` Kotlin visibility | `internal` Kotlin visibility | n/a — module is intentionally public-API |

Mark every class that isn't intentionally public as `internal`. The only types crossing module boundaries are: Composables that `:app` navigates to, Hilt modules, and the entire `:design-system` surface.

---

## 6. Build Graph Properties

A correct topology should yield these properties on `./gradlew :app:assembleDebug`:

| Property | Target |
|---|---|
| `:aos-core` recompile triggers | only when `aos-core/` source changes |
| `:core` recompile triggers | when `:core` or `:aos-core` change |
| `:design-system` recompile triggers | when `:design-system` or `:aos-core` change — **never** when `:core` changes |
| `:data` recompile triggers | when `:data`, `:core`, or `:aos-core` change |
| `:features` recompile triggers | when `:features`, `:core`, `:design-system`, or `:aos-core` change — **never** when `:data` or a variant changes |
| `:variants-kh` recompile triggers | when `:variants-kh`, `:core`, or `:aos-core` change — **never** when other variants change |
| `:app` recompile triggers | any module changes (this is correct — `:app` is the assembler) |

If a variant change forces `:features` to recompile, the topology has been violated. If a `:data` change forces `:features` or `:variants-*` to recompile, the topology has been violated. If a `:core` change forces `:design-system` to recompile, the topology has been violated.

---

## 7. Cross-references

- Module specs: [02](02-aos-core.md) · [03](03-core.md) · [04](04-design-system.md) · [05](05-data.md) · [06](06-features.md) · [07](07-variants.md) · [08](08-app-orchestrator.md)
- Boot mechanics: [10 — Boot Phases](10-boot-phases.md)
- Build perf consequences: [14 — Build Performance](14-build-performance.md)
- Full project tree on one page: [17 — Project Structure](17-project-structure.md)
