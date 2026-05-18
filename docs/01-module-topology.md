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
    │:data │  │:vrnt│  │ :features│  │:features-scanner │  │:features-{n}│
    └───┬──┘  └──┬──┘  └─────┬────┘  └─────────┬────────┘  └──────┬──────┘
        │        │           │                 │                  │
        └────────┴───────────┴─────────────────┴──────────────────┘
                                  ▲
┌──────────────────────────────────────────────────────────────────────┐
│                                :app                                  │   orchestrator
└──────────────────────────────────────────────────────────────────────┘
```

Where:
- `:vrnt` is shorthand for `:variants-{id}` (one module per region: `:variants-kr`, `:variants-kh`, `:variants-vn`). Per-tenant profiles live *inside* each variant module at `:variants-{region}/tenants/{tenant-id}/` — not as sibling modules.
- `:features-{n}` is shorthand for `:features-{feature-name}` (e.g. `:features-hipass` for Korea-only highway-toll capture).

### Reading the graph

- An arrow `↑` from a lower row to a higher row means the lower module **depends on** the higher one.
- `:app` sits at the **bottom** because it depends on everything; only it can wire concrete implementations into the runtime.
- `:core` and `:design-system` sit at the **top of the product layer**, side by side: `:core` owns domain contracts, `:design-system` owns UI primitives. Neither depends on the other.
- `:aos-core` sits **above everything else** because infrastructure underlies all product code.
- The five siblings in the middle row (`:data`, `:variants-{id}`, `:features`, `:features-scanner`, `:features-{feature}`) **never depend on each other**.

---

## 2. Module Roles

| Module | Type | Dependencies | Depended on by |
|---|---|---|---|
| `:aos-core` | Submodule (Android library) | — | every other module |
| `:core` | Local (Android library) | `:aos-core` | `:data`, `:features`, `:features-scanner`, `:features-{n}`, `:variants-*`, `:app` |
| `:design-system` | Local (Android library) | `:aos-core` | `:features`, `:features-scanner`, `:features-{n}`, `:app` |
| `:data` | Local (Android library) | `:core`, `:aos-core` | `:app` |
| `:features` | Local (Android library) | `:core`, `:design-system`, `:aos-core` | `:app` |
| `:features-scanner` | Local (Android library) | `:core`, `:design-system`, `:aos-core` | `:app` |
| `:features-{feature-name}` | Local (Android library) | `:core`, `:design-system`, `:aos-core` | `:app` |
| `:variants-kr` | Local (Android library) | `:core`, `:aos-core` | `:app` |
| `:variants-kh` | Local (Android library) | `:core`, `:aos-core` | `:app` |
| `:variants-vn` | Local (Android library) | `:core`, `:aos-core` | `:app` |
| `:app` | Application | All of the above | — |

---

## 3. Forbidden Imports (Compile-time Enforced)

These are the **hard rules**. Any violation must fail the build, not a code review.

| ❌ Forbidden | Why |
|---|---|
| `:features` → `:variants-*` | Breaks Logic-Blind contract. `:features` must compile without knowing which variants exist. |
| `:features` → `:data` | UI must depend only on `:core` interfaces; impls are wired at runtime by `:app`. |
| `:features` ↔ `:features-scanner` ↔ `:features-{n}` | Sibling UI modules — no cross-references. Shared UI primitives go in `:design-system`. |
| `:variants-{A}` → `:variants-{B}` | Variants must be hermetic. Cross-variant imports are how regressions cascade (KR's quirks leaking into KH). |
| `:variants-*` → `:data` | Variants contribute policies, not data plumbing. |
| `:variants-*` → `:design-system` | Variants have no UI; they don't need design primitives. |
| `:data` → `:variants-*` | The data layer is variant-agnostic; the server demuxes per `USE_INTT_ID` + `COMPANY_CD`. |
| `:data` → `:design-system` | The data layer has no UI. |
| `:design-system` → `:core`, `:data`, `:features`, `:variants-*` | The design system is variant-agnostic and domain-agnostic. |
| `:core` → `:design-system`, `:data`, `:features`, `:variants-*` | Reverses the contract direction. `:core` is the upstream contract, never a consumer. |
| `:aos-core` → anything in this project | The submodule must remain project-agnostic. |

> **Enforcement plan:** in addition to the Gradle dependency declarations themselves, lint rules can pin forbidden module imports as errors. CI must fail any module's `build.gradle.kts` that declares a forbidden dependency.

---

## 4. Why This Shape?

### 4.1 Why two foundation layers (`:aos-core` + `:core`)?

`:aos-core` is reused across non-expense projects (e.g., a future health-tracking or HR-tools app) and so it must be **product-agnostic**. `:core` is project-specific (it knows about `Money`, `UserSession`, `DepartmentAccount`, `ReceiptRepository`, `TenantFlags`). Conflating them couples infrastructure releases to product cycles.

In practical Bizplay terms: the existing `SMART_LIB_STUDIO_CAMBODIA/smart_lib` submodule is roughly the right scope for `:aos-core` — it already provides `RetrofitService`, `PreferenceDelegator`, `BizLocationManager`, FlexibleToolBar, etc. The framework formalises and hardens that boundary (adds typed `BaseUrlInterceptor`, `EncryptedDatabase` wrapper over SQLCipher, `SecureKeypad` wrapper over TransKey, the `BizWebView` Composable). `:core` is everything domain-specific that today lives mixed into `:app` (`USE_INTT_ID` handling, the `Constant.MG` keys, the `ComTran` listener shape).

### 4.2 Why `:design-system` separately from `:core`?

`:core` is the **domain** layer (expense types and contracts). `:design-system` is the **UI primitive** layer (theme tokens, components — `BizButton`, `BizTheme`, `BizWebView`). They live in different conceptual layers and have different stability profiles. More practically: if `:design-system` were inside `:core`, every theme tweak would force a recompile of `:data`, `:variants-*`, and every product module. Keeping them separate confines blast radius and lets non-UI modules (`:data`, `:variants-*`) skip Compose dependencies entirely.

### 4.3 Why `:data` separately from `:core`?

`:core` is the contract layer — interfaces and immutable models. `:data` is the implementation: Retrofit + DTOs + mapping + repository classes. Putting impls in `:core` would make every API tweak recompile every consumer of `:core` (which is every product module). Keeping `:data` separate confines the recompile blast radius for what will be the most-edited surface in the project — the IPPP backend evolves frequently, and the `*_REQ` / `*_RES` shape changes weekly today.

### 4.4 Why `:features` as a hybrid-monolith?

Per-feature Gradle modules sound clean but explode Gradle's per-module configuration cost. With ~30 expense flows (receipt list, receipt detail, OCR scan, gallery pick, business-trip wizard, gasoline-route entry, taxi entry, Hi-Pass list, card register, card management, approval inbox, approval line setup, notice list, more menu, profile, login, OTP, company picker, language picker, KakaoPay link, online mall …), a strict modular approach pays a fixed Gradle tax 30 times. Package boundaries inside one module give the **organization benefits** without the **build cost**. Detail: [14 — Build Performance](14-build-performance.md).

### 4.5 Why `:features-scanner` and `:features-{feature}` are exempt

Two triggers for breaking out a feature module:

1. **Heavy unique dependencies** — io.card (payment-card OCR) plus `cameraviewplus` plus the sasapi scraping SDK plus the OCR partner integrations pull in tens of MB of native and Java code. Co-locating that with the main UI engine would penalise every incremental build of every other feature. `:features-scanner` is the canonical example — it owns the camera entry points, the card-scan flow, the receipt-OCR flow, the ticket-OCR flow, and the scraping-driven enrichment paths.
2. **Variant-locked features** — A feature with its own API + DTOs + screens that only one variant uses (e.g., a Korea-only government-mandated flow such as Hi-Pass toll capture, or `:features-mydata` for Korea's open-banking integration) goes in `:features-{feature-name}`, gated by a `VariantCapabilities` flag. Detail: [07 — `:variants-*` § "When the Variant Has Unique Features"](07-variants.md).

Both share the same dependency shape — `:core` + `:design-system` + `:aos-core`, no sibling cross-edges.

### 4.6 Why variants are flat sibling modules

- **Visibility** — a single root listing answers "what regions do we support?"
- **Symmetry** — every variant has the same internal layout (`policy/`, `format/`, `capability/`, `support/`, `tenants/`, `di/`), enforced by convention.
- **Onboarding** — adding a region is strictly additive: new directory, new `include()` line, new catalogue entry.
- **Isolation** — Gradle module dependencies enforce that variant A's code cannot reach variant B's. A package boundary inside one module would not (Kotlin's `internal` is per-module).

The naming convention is `variants-<id>` (kebab-case, no nested colon path). Mirrors `features-scanner` exactly.

> **Tenants do not get sibling modules.** Per-corporate-customer differences (POSCO / Lotte / NIA / Shinsegae / …) live *inside* the relevant variant module under `tenants/{tenant-id}/`. A multi-region customer has separate tenant entries per variant. See [19 — Tenants and Variants](19-tenants-and-variants.md).

---

## 5. Internal Package Convention

Every module follows a parallel internal layout:

| Layer | `:features` | `:variants-kr` | `:data` | `:design-system` |
|---|---|---|---|---|
| **Public surface** | `com.bizplay.features.receipt.ReceiptListScreen` | `com.bizplay.variants.kr.di.KrVariantModule` | `com.bizplay.data.di.DataModule` | All of `com.bizplay.design.*` (component primitives are intentionally public) |
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
| `:variants-kr` recompile triggers | when `:variants-kr`, `:core`, or `:aos-core` change — **never** when `:variants-kh` or `:variants-vn` changes |
| `:app` recompile triggers | any module changes (this is correct — `:app` is the assembler) |

If a variant change forces `:features` to recompile, the topology has been violated. If a `:data` change forces `:features` or `:variants-*` to recompile, the topology has been violated. If a `:core` change forces `:design-system` to recompile, the topology has been violated.

---

## 7. Cross-references

- Module specs: [02](02-aos-core.md) · [03](03-core.md) · [04](04-design-system.md) · [05](05-data.md) · [06](06-features.md) · [07](07-variants.md) · [08](08-app-orchestrator.md)
- Boot mechanics: [10 — Boot Phases](10-boot-phases.md)
- Build perf consequences: [14 — Build Performance](14-build-performance.md)
- Full project tree on one page: [17 — Project Structure](17-project-structure.md)
- The tenant axis that lives inside variants: [19 — Tenants and Variants](19-tenants-and-variants.md)
