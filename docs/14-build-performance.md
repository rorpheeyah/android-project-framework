# 14 · Build Performance

> Adding a new feature folder must not create a build bottleneck.

---

## 1. The Goal

A build should pay cost proportional to **what changed**, not to **how big the project is**. Concretely:

| Action | Acceptable cost |
|---|---|
| Edit one Composable in `:features/receipt/detail/` | Recompile that file + dependents only |
| Add one new feature folder under `:features/` | Compile the new files; no Gradle reconfiguration cost |
| Add a method to `IpppReceiptApi` in `:data` | Recompile `:data`, `:app` |
| Add a new variant module | Compile the new module; **`:features` and `:data` must not recompile** |
| Add a new tenant profile under `:variants-kr/tenants/` | Recompile `:variants-kr`, `:app`; `:features` and `:data` untouched |
| Bump `:aos-core` (network change) | Recompile `:core`, `:data`, `:features`, `:variants-*`, `:app` (unavoidable; mitigated by submodule discipline) |

The hostile failure mode is **"clean build to debug an unrelated issue"** — if devs feel the need to do this regularly, build perf has degraded.

---

## 2. Why Hybrid-Monolith for `:features`

The textbook microservices-style answer to "many features" is **one Gradle module per feature**. This pattern looks clean but has a fixed-overhead trap:

| Per-Feature Modules | Hybrid-Monolith |
|---|---|
| Each feature's `build.gradle.kts` re-evaluated per configure phase | One config phase covers all features |
| Each feature triggers KSP / kapt round-trip independently | One KSP/kapt invocation amortized |
| Each feature pays the Hilt aggregator cost | Hilt aggregator runs once for `:features` |
| Cross-feature refactor → edit N build files | Cross-feature refactor → move files, edit zero build files |

For ~30 expense-management flows (receipt list, receipt detail, OCR, gallery, business-trip wizard, gasoline-route, taxi, Hi-Pass, card register / management / statement, approval inbox / action / line setup, notice list, profile, login / OTP / company picker, language, KakaoPay link, online mall, …), "Hybrid-Monolith" wins by a measurable amount on every dev's laptop:

- Configuration phase: ~3-5x faster
- Incremental compile of an unrelated feature: ~2x faster
- IDE indexing and find-usages: noticeably faster (fewer module boundaries to cross)

### When to break out into a separate module

Heuristic: **does this feature pull in unique heavy dependencies?**

- ✅ Scanner (io.card payment-card OCR, `cameraviewplus`, OCR partner integrations, `sasapi` scraping) → `:features-scanner`
- ✅ Hi-Pass (Korea-only highway-toll backend with own DTOs + screens) → `:features-hipass`
- ✅ MyData (Korea-only open-banking integration, would pull in government KYC SDKs) → would justify `:features-mydata`
- ✅ Chatbot (NLP models, voice SDK) if/when adopted → `:features-chatbot`
- ❌ A new "Training Reimbursement" flow (just Compose + ViewModels) → stays in `:features`
- ❌ A new expense category (just Compose + ViewModels) → stays in `:features`

The threshold is roughly: *"does adding this feature to `:features` slow incremental builds for everyone working on unrelated features?"* If yes, isolate it.

---

## 3. Why `:data` Is Its Own Module

`:data` is the most-edited surface in the project — every IPPP backend tweak (new endpoint, new DTO field, response shape change, new `*_REQ` / `*_RES` shape) lands here. Putting it in `:core` would force a recompile of every product module on every API tweak. Keeping it as a sibling confines the blast radius:

| API change happens | Recompile triggers |
|---|---|
| With `:data` separate | `:data`, `:app` |
| If `:data` were merged into `:core` | `:core`, `:data` *(if it remained)*, `:features`, every `:variants-*`, `:app` |

For an expense-management app where the API changes weekly (new tenants, new approval shapes, new card schemes), this saves real developer time.

---

## 4. Why `:variants-*` Are Real Modules

Variant modules **must** be separate Gradle modules — not packages — for two reasons that have nothing to do with build perf:

1. **Dependency isolation:** the build graph itself is what enforces "variant A cannot import variant B". This is impossible at the package level; Kotlin's `internal` visibility is module-scoped, so packages inside one module can see each other.
2. **Independent test runs:** `./gradlew :variants-kr:test` runs only Korea-variant tests. Critical when teams own different release cadences.

The build perf cost (per-variant Gradle config overhead × N variants) is **acceptable** because:

- Variant count grows much more slowly than feature count (years vs. weeks).
- Each variant module is small (just policies + DI + tenant profiles).
- Variants build in parallel.

> **Tenants are NOT separate Gradle modules.** Per-corporate-customer profiles live inside their parent variant's `tenants/{id}/` subfolder. Onboarding a tenant adds files inside `:variants-kr` — it does not add a new Gradle module. This keeps tenant onboarding cheap (typically ~3 files, no `build.gradle.kts` edits) while preserving region-level isolation. See [19 — Tenants and Variants](19-tenants-and-variants.md).

---

## 5. Gradle-level Optimizations

### 5.1 Configuration cache (mandatory)

```properties
# gradle.properties
org.gradle.configuration-cache=true
```

The configuration cache transforms `./gradlew :app:assembleDebug` from "re-run all build script logic each time" to "load a serialized graph". Empirically, ~5-10x faster on warm runs for a project this size.

### 5.2 Parallel project builds (mandatory)

```properties
org.gradle.parallel=true
```

Variant modules and isolated feature modules build in parallel — leverage available cores.

### 5.3 Build cache (mandatory)

```properties
org.gradle.caching=true
```

KSP outputs and resource processing are cacheable; on a clean machine, build cache turns "rebuild everything" into "download mostly-prebuilt outputs".

### 5.4 Non-transitive R class

```properties
android.nonTransitiveRClass=true
```

Makes resource references explicit per module. Reduces the surface area that needs to be reprocessed when one module changes resources.

### 5.5 KSP over kapt

Use **KSP** (Kotlin Symbol Processing), not kapt, for Hilt and any other annotation processors. KSP is roughly 2x faster on cold builds and significantly faster on incremental builds.

---

## 6. Compose-Specific Discipline

Compose has its own compile cost characteristics:

### 6.1 Strong skipping mode (Compose Compiler 1.5.4+)

Reduces the cases in which a `@Composable` is recomposed unnecessarily — also reduces compile-time generation of skip helpers.

### 6.2 Stability annotations

Marking domain models in `:core` as `@Immutable` (where appropriate) lets the Compose compiler skip recomposition when an instance is structurally equal.

### 6.3 Avoid huge Composables

If a Composable spans more than ~200 lines, it's both harder to recompose efficiently and slower to incrementally compile (every edit re-runs Compose's transforms over the whole function). Break into sub-composables. (Today's Bizplay Activities frequently exceed 1,000 lines — the framework's Compose port should fragment those aggressively.)

---

## 7. The "Adding a new feature folder" Test

Reviewing a PR that adds a new feature folder under `:features/` (e.g. a new "Training Reimbursement" flow). The build system should react with:

| Module | Recompile triggered? |
|---|---|
| `:aos-core` | No |
| `:core` | No |
| `:data` | No |
| `:features` | Yes — but **only the new files** + anything that imports them |
| `:features-scanner` | No |
| `:features-hipass` | No |
| `:variants-*` | No |
| `:app` | Yes — `:app` always recompiles when its dependency `:features` does |

Use `./gradlew :app:assembleDebug --info` to verify. If unrelated `:features` files are recompiling, suspect:

- A change to a shared helper that the new feature imports (verify intent)
- A change to a `:core` interface (verify intent)
- Otherwise: investigate; this is a leak.

---

## 8. The "Adding a new variant module" Test

Reviewing a PR that adds `:variants-vn`. The build system should react with:

| Module | Recompile triggered? |
|---|---|
| `:aos-core` | No |
| `:core` | No |
| `:data` | No |
| `:features` | **No** — this is the architectural promise |
| `:features-scanner` | No |
| `:features-hipass` | No |
| Other `:variants-*` | No |
| `:app` | Yes (new dependency, new catalogue entry) |
| `:variants-vn` | Yes (it's new) |

If `:features` or `:data` recompiles after a variant-only change, the topology has been violated. Inspect their `build.gradle.kts` for an accidental `implementation(project(":variants-vn"))`.

---

## 9. The "Adding a new tenant profile" Test

Reviewing a PR that adds a new corporate-customer profile (e.g. a hypothetical `tenants/kepco/KepcoTenantProfile.kt` under `:variants-kr`). The build system should react with:

| Module | Recompile triggered? |
|---|---|
| `:aos-core` | No |
| `:core` | No (unless a new `TenantFlags` field or `TenantPolicy` interface was added in the same PR) |
| `:data` | No |
| `:features` | **No** |
| `:variants-kr` | Yes — but only the new profile file plus the `TenantCatalogue` and the `KrTenantModule` if structural impls were added |
| Other `:variants-*` | No |
| `:app` | Yes — `:app` recompiles because `:variants-kr` did |

Even lighter than a variant onboarding. The discipline that makes this work is: the `TenantFlags` schema is in `:core` (so adding *values* doesn't touch `:core`); the per-tenant *profile* lives inside the parent variant.

---

## 10. Long-Term Build Health

Discipline that pays compounding returns:

| Practice | Benefit |
|---|---|
| Profile builds quarterly with `--scan` | Catch regressions before they're normalized |
| Keep `:features` package count visible in dashboards | See concentration trends; decide when to split |
| Track APK size per variant in CI | Detect dependency bloat |
| Set a CI budget for `:app:assembleDebug` time | Page when exceeded; force investigation |

---

## 11. Cross-references

- The Hybrid-Monolith design rationale: [06 — `:features`](06-features.md)
- Variant module shape and dependency rules: [07 — `:variants-*`](07-variants.md)
- Tenant onboarding (lighter than variant onboarding): [19 — Tenants and Variants](19-tenants-and-variants.md)
- Why `:data` is separate from `:core`: [05 — `:data`](05-data.md)
- The dependency DAG: [01 — Module Topology](01-module-topology.md)
