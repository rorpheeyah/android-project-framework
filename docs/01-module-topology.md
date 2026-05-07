# 01 · Module Topology

> The dependency DAG is the most important diagram in this project. If you remember nothing else, remember this graph.

---

## 1. The Dependency Graph

```
                    ┌──────────────┐
                    │   :aos-core  │   (Git submodule · infrastructure)
                    └──────┬───────┘
                           │
         ┌─────────────────┼──────────────────┐
         │                 │                  │
         ▼                 ▼                  ▼
   ┌──────────┐      ┌──────────┐      ┌────────────────┐
   │  :core   │      │ :features│◄─────┤ :features-     │
   │          │      │          │      │   chatbot      │
   └────┬─────┘      └────┬─────┘      └────────────────┘
        ▲                 │                    │
        │                 ▼                    │
        │          ┌──────────────┐            │
        └──────────┤ :tenants:kh  │            │
        │          │ :tenants:vn  │            │
        │          │ :tenants:ppc │            │
        │          └──────┬───────┘            │
        │                 │                    │
        │                 ▼                    ▼
        │          ┌────────────────────────────┐
        └──────────┤            :app            │
                   │  (Orchestrator · DI glue)  │
                   └────────────────────────────┘
```

### Reading the graph

- An arrow from `A → B` means *A depends on B* (A imports types from B).
- `:app` sits at the **bottom** because it depends on everything; only it can wire concrete tenant implementations to UI.
- `:core` sits at the **top of the product layer** because both `:features` and `:tenants:*` depend on it for shared contracts.
- `:aos-core` sits **above everything else** because infrastructure underlies all product code.

---

## 2. Module Roles

| Module | Type | Dependencies | Depended on by |
|---|---|---|---|
| `:aos-core` | Submodule (Android library) | — | `:core`, `:features`, `:features-chatbot`, `:tenants:*`, `:app` |
| `:core` | Local (Android library) | `:aos-core` | `:features`, `:features-chatbot`, `:tenants:*`, `:app` |
| `:features` | Local (Android library) | `:core`, `:aos-core` | `:app` |
| `:features-chatbot` | Local (Android library) | `:core`, `:aos-core` | `:app` |
| `:tenants:tenants-kh` | Local (Android library) | `:core`, `:aos-core` | `:app` |
| `:tenants:tenants-vn` | Local (Android library) | `:core`, `:aos-core` | `:app` |
| `:tenants:tenants-ppcbank` | Local (Android library) | `:core`, `:aos-core` | `:app` |
| `:app` | Application | All of the above | — |

---

## 3. Forbidden Imports (Compile-time Enforced)

These are the **hard rules**. Any violation must fail the build, not a code review.

| ❌ Forbidden | Why |
|---|---|
| `:features` → `:tenants:*` | Breaks Logic-Blind contract. `:features` would need recompile per tenant change. |
| `:tenants:[A]` → `:tenants:[B]` | Tenants must be hermetic. Cross-tenant imports are how regressions cascade. |
| `:core` → `:features` | Reverses the contract direction. `:core` is the upstream contract, never a consumer. |
| `:core` → `:tenants:*` | Same as above. Contract layer can't depend on implementations. |
| `:aos-core` → anything in this project | The submodule must remain project-agnostic. It's reused outside Nexus. |
| `:features` → `:features-chatbot` (or vice versa) | Both are sibling UI modules; cross-references would re-monolithize them. |

> **Enforcement plan:** in addition to the Gradle dependency declarations themselves, lint rules can pin forbidden module imports as errors. Add to CI a Gradle task that fails if any module's `build.gradle.kts` declares a forbidden dependency.

---

## 4. Why This Shape?

### 4.1 Why two cores?

`:aos-core` is reused across non-banking projects (e.g., a future insurance app) and so it must be **product-agnostic**. `:core` is project-specific (it knows about `Money`, `UserSession`, `TransferRepository`). Conflating them couples infrastructure releases to product cycles.

### 4.2 Why `:features` as a hybrid-monolith?

Per-feature modules sound clean but explode Gradle's per-module configuration cost. With ~30 banking flows, a strict modular approach pays a fixed Gradle tax 30 times. Package boundaries inside one module give the **organization benefits** without the **build cost**. Detail: [12 — Build Performance](12-build-performance.md).

### 4.3 Why `:features-chatbot` is exempt

Chatbot SDKs typically pull in tens of MB of dependencies (NLP models, transitive native libs). Co-locating that with the main UI engine penalizes every incremental build of every other feature. Isolating heavy-SDK features keeps the hot path fast.

### 4.4 Why tenants are sibling modules under `:tenants/`

- **Visibility** — a single directory listing answers "what tenants do we support?"
- **Symmetry** — every tenant has the same module shape (api/, repo/, di/), enforced by convention
- **Onboarding** — adding a tenant is a strictly additive operation: new directory, new `include()` line, new DI binding

---

## 5. Internal Package Convention

Every module follows a parallel internal layout:

| Layer | `:features` | `:tenants:tenants-kh` |
|---|---|---|
| **Public surface** | `com.nexus.features.transfer.TransferScreen` | `com.nexus.tenants.kh.di.KhBindings` (only DI module exposed) |
| **ViewModels / coordinators** | `com.nexus.features.transfer.TransferViewModel` | `com.nexus.tenants.kh.repo.BakongTransferRepo` |
| **Internal contracts** | `internal` Kotlin visibility | `internal` Kotlin visibility |
| **API / data sources** | n/a (no networking) | `com.nexus.tenants.kh.api.CambodiaApi` |

Mark every class that isn't intentionally public as `internal`. The only types crossing module boundaries are: Composables that `:app` navigates to, and DI modules.

---

## 6. Build Graph Properties

A correct topology should yield these properties on `./gradlew :app:assembleDebug`:

| Property | Target |
|---|---|
| `:aos-core` recompile triggers | only when `aos-core/` source changes |
| `:core` recompile triggers | when `:core` or `:aos-core` change |
| `:features` recompile triggers | when `:features`, `:core`, or `:aos-core` change — **never** when a tenant changes |
| `:tenants:tenants-kh` recompile triggers | when `:tenants:tenants-kh`, `:core`, or `:aos-core` change — **never** when other tenants change |
| `:app` recompile triggers | any module changes (this is correct — `:app` is the assembler) |

If a tenant change forces `:features` to recompile, the topology has been violated. Audit dependencies.

---

## 7. Cross-references

- Module specs: [02](02-aos-core.md) · [03](03-core.md) · [04](04-features.md) · [05](05-tenants.md) · [06](06-app-orchestrator.md)
- DI mechanics: [08 — Runtime Tenant Switching](08-runtime-tenant-switching.md)
- Build perf consequences: [12 — Build Performance](12-build-performance.md)
