# 12 · Build Performance

> **Strategic Requirement #4:** Linear Build Performance.
> Adding a new feature folder must not create a build bottleneck.

---

## 1. The Goal

A build should pay cost proportional to **what changed**, not to **how big the project is**. Concretely:

| Action | Acceptable cost |
|---|---|
| Edit one Composable in `:features/transfer/input/` | Recompile that file + dependents only |
| Add one new feature folder under `:features/` | Compile the new files; no Gradle reconfiguration cost |
| Add one new tenant module | Compile the new module; **`:features` must not recompile** |
| Bump `:aos-core` (network change) | Recompile `:core`, `:features`, `:tenants:*`, `:app` (this is unavoidable; mitigated by submodule discipline) |

The hostile failure mode is **"clean build to debug a unrelated issue"** — if devs feel the need to do this regularly, build perf has degraded.

---

## 2. Why Hybrid-Monolith for `:features`

The textbook microservices-style answer to "many features" is **one Gradle module per feature**. This pattern looks clean but has a fixed-overhead trap:

| Per-Feature Modules | Hybrid-Monolith |
|---|---|
| Each feature's `build.gradle.kts` re-evaluated per configure phase | One config phase covers all features |
| Each feature triggers KSP / kapt round-trip independently | One KSP/kapt invocation amortized |
| Each feature pays the Hilt aggregator cost | Hilt aggregator runs once for `:features` |
| Cross-feature refactor → edit N build files | Cross-feature refactor → move files, edit zero build files |
| Feature dependency expression: `implementation(project(":feature-x"))` × N | Feature dependency expression: package import |

For ~30 banking flows, "Hybrid-Monolith" wins by a measurable amount on every dev's laptop:

- Configuration phase: ~3-5x faster
- Incremental compile of an unrelated feature: ~2x faster
- IDE indexing and find-usages: noticeably faster (fewer module boundaries to cross)

### When to break out into a separate module

Heuristic: **does this feature pull in unique heavy dependencies?**

- ✅ Chatbot (NLP models, voice SDK) → `:features-chatbot`
- ✅ Live KYC (face SDK, native vision) → would justify `:features-kyc-livecheck`
- ✅ Card 3DS (large issuer SDK, JS bundles) → would justify `:features-3ds`
- ❌ A new "Bill Pay" flow (just Compose + ViewModels) → stays in `:features`
- ❌ A new "Statement Download" flow (just Compose + ViewModels) → stays in `:features`

The threshold is roughly: *"does adding this feature to `:features` slow incremental builds for everyone working on unrelated features?"* If yes, isolate it.

---

## 3. Why `:tenants:*` are Real Modules

Tenant modules **must** be separate Gradle modules — not packages — for two reasons that have nothing to do with build perf:

1. **Dependency isolation:** the build graph itself is what enforces "tenant A cannot import tenant B". This is impossible at the package level.
2. **Independent test runs:** `./gradlew :tenants:tenants-kh:test` runs only KH tests. Critical when tenant teams own different release cadences.

The build perf cost (per-tenant Gradle config overhead × N tenants) is **acceptable** because:

- Tenant count grows much more slowly than feature count (years vs. weeks).
- Each tenant module is small (just repos + DTOs + one DI module).
- Tenants build in parallel.

---

## 4. Gradle-level Optimizations

### 4.1 Configuration cache (mandatory)

```properties
# gradle.properties
org.gradle.configuration-cache=true
```

The configuration cache transforms `./gradlew :app:assembleDebug` from "re-run all build script logic each time" to "load a serialized graph". Empirically, ~5-10x faster on warm runs for a project this size.

### 4.2 Parallel project builds (mandatory)

```properties
org.gradle.parallel=true
```

Tenant modules and isolated feature modules build in parallel — leverage available cores.

### 4.3 Build cache (mandatory)

```properties
org.gradle.caching=true
```

KSP outputs and resource processing are cacheable; on a clean machine, build cache turns "rebuild everything" into "download mostly-prebuilt outputs".

### 4.4 Non-transitive R class

```properties
android.nonTransitiveRClass=true
```

Makes resource references explicit per module. Reduces the surface area that needs to be reprocessed when one module changes resources.

### 4.5 KSP over kapt

Use **KSP** (Kotlin Symbol Processing), not kapt, for Hilt and any other annotation processors. KSP is roughly 2x faster on cold builds and significantly faster on incremental builds. Hilt has supported KSP for current versions of the framework.

---

## 5. Compose-Specific Discipline

Compose has its own compile cost characteristics:

### 5.1 Strong skipping mode (Compose Compiler 1.5.4+)

Reduces the cases in which a `@Composable` is recomposed unnecessarily — also reduces compile-time generation of skip helpers.

### 5.2 Stability annotations

Marking domain models in `:core` as `@Immutable` (where appropriate) lets the Compose compiler skip recomposition when an instance is structurally equal. Indirectly speeds dev because logcat / layout inspector are cleaner.

### 5.3 Avoid huge Composables

If a Composable spans more than ~200 lines, it's both harder to recompose efficiently and slower to incrementally compile (every edit re-runs Compose's transforms over the whole function). Break into sub-composables.

---

## 6. The "Adding a new feature folder" Test

Reviewing a PR that adds a new feature folder under `:features/`. The build system should react with:

| Module | Recompile triggered? |
|---|---|
| `:aos-core` | No |
| `:core` | No |
| `:features` | Yes — but **only the new files** + anything that imports them |
| `:features-chatbot` | No |
| `:tenants:*` | No |
| `:app` | Yes — `:app` always recompiles when its dependency `:features` does |

Use `./gradlew :app:assembleDebug --info` to verify. If unrelated `:features` files are recompiling, suspect:

- A change to a `common/` symbol that the new feature imports (verify intent)
- A change to a `:core` interface (verify intent)
- Otherwise: investigate; this is a leak.

---

## 7. The "Adding a new tenant module" Test

Reviewing a PR that adds `:tenants:tenants-my`. The build system should react with:

| Module | Recompile triggered? |
|---|---|
| `:aos-core` | No |
| `:core` | No |
| `:features` | **No** — this is the architectural promise |
| `:features-chatbot` | No |
| Other `:tenants:*` | No |
| `:app` | Yes (new dependency, new catalogue entry) |
| `:tenants:tenants-my` | Yes (it's new) |

If `:features` recompiles after a tenant-only change, the topology has been violated. Inspect `:features/build.gradle.kts` for an accidental `implementation(project(":tenants:tenants-my"))`.

---

## 8. Long-Term Build Health

Discipline that pays compounding returns:

| Practice | Benefit |
|---|---|
| Profile builds quarterly with `--scan` | Catch regressions before they're normalized |
| Keep `:features` package count visible in dashboards | See concentration trends; decide when to split |
| Track APK size per tenant in CI | Detect dependency bloat per tenant |
| Set a CI budget for `:app:assembleDebug` time | Page when exceeded; force investigation |

---

## 9. Cross-references

- The Hybrid-Monolith design rationale: [04 — `:features`](04-features.md)
- Tenant module shape and dependency rules: [05 — `:tenants:*`](05-tenants.md)
- The dependency DAG: [01 — Module Topology](01-module-topology.md)
