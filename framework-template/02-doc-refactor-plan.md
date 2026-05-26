# 02 · Doc Refactor Plan

> **Purpose:** Concrete plan for splitting `docs/` into a domain-neutral framework spec and a clearly-isolated reference-app section. Derived from [`01-current-state-audit.md`](01-current-state-audit.md). Executable as a single multi-PR refactor.

---

## 1. Target Structure

```
docs/
├── framework/                                ← architectural rules + capability specs (domain-neutral)
│   ├── 00-overview.md
│   ├── 01-module-topology.md
│   ├── 02-aos-sdk.md                         ← renamed from 02-aos-core.md
│   ├── 03-core.md                            ← stripped of instance domain types; pattern only
│   ├── 04-design-system.md
│   ├── 05-data.md                            ← stripped of concrete *Api family; pattern only
│   ├── 06-features.md
│   ├── 07-tenants.md                         ← renamed from 07-variants.md
│   ├── 08-app-orchestrator.md
│   ├── 09-mvi-pattern.md
│   ├── 10-boot-phases.md
│   ├── 11-mg-and-runtime-config.md
│   ├── 12-departments-and-session.md
│   ├── 13-onboarding-a-tenant.md             ← renamed from 13-onboarding-a-variant.md
│   ├── 14-build-performance.md
│   ├── 15-tech-stack.md
│   ├── 16-glossary.md
│   ├── 17-project-structure.md               ← skeleton tree; concrete paths moved to reference-app
│   ├── 18-webview-integration.md
│   ├── 19-tenants-and-regions.md             ← renamed from 19-tenants-and-variants.md
│   ├── 20-chat.md
│   ├── 21-push-channels.md
│   ├── 22-deeplinks.md
│   ├── 23-kyc-capture.md
│   ├── 24-pdf.md
│   ├── 25-locale.md
│   ├── 26-pin-and-session.md
│   ├── 27-maps-and-location.md
│   ├── 28-background-work.md
│   ├── 29-local-database.md
│   └── 30-form-wizard.md                     ← wizard pattern only; instance step lists moved to reference-app
│
└── reference-app/                            ← one consuming product as an illustration (currently: lending)
    ├── README.md                             ← "this is one instance of the framework"
    ├── prd-summary.md                        ← short summary of the consuming project's PRD
    ├── domain-types.md                       ← concrete domain types extracted from docs/03
    ├── api-surface.md                        ← concrete *Api family extracted from docs/05
    ├── reference-tenant.md                   ← the concrete tenant module spec
    ├── instance-flows.md                     ← long multi-step flows (e.g., apply wizards)
    ├── multi-tenant-migration-example.md     ← the worked example of migrating from a `Detail*.isXxx()` antipattern
    └── reference-project-structure.md        ← the FULL module tree with concrete paths
```

---

## 2. Migration Matrix

| Source location | Destination | Action | Effort |
|---|---|---|---|
| `docs/00-overview.md` | `docs/framework/00-overview.md` | Move; replace instance-domain examples in the "Modules" table with generic placeholders; add "Reference instance example" callouts | Small |
| `docs/01-module-topology.md` | `docs/framework/01-module-topology.md` | Move as-is | Trivial |
| `docs/02-aos-core.md` | `docs/framework/02-aos-sdk.md` | Rename + move; content already SDK-generic | Trivial |
| `docs/03-core.md` | `docs/framework/03-core.md` + `docs/reference-app/domain-types.md` | Split: keep contract-layer pattern in framework; move concrete repository/policy/model types (§2.4, §2.5, §2.6) to reference-app. Leave generic placeholders + a "see your project's domain-types.md" note in framework/03. | Medium |
| `docs/04-design-system.md` | `docs/framework/04-design-system.md` | Move as-is | Trivial |
| `docs/05-data.md` | `docs/framework/05-data.md` + `docs/reference-app/api-surface.md` | Split: keep `:data` layer architecture, the Retrofit-per-feature-area pattern, `:data/external/` convention, `DataModule` shape in framework. Move concrete `Fintech*Api` family and third-party clients to reference-app/api-surface.md. Replace long code listings with generic shape ("e.g., FintechAuthApi + Fintech<YourArea>Api per area") + a "Reference instance example" link. | Medium |
| `docs/06-features.md` | `docs/framework/06-features.md` | Move; replace the directory tree with a generic example using placeholders ({feature1}, {feature2}); add a "Reference instance example" sidebar showing the actual tree | Small |
| `docs/07-variants.md` | `docs/framework/07-tenants.md` | Rename + move; policy examples tagged with instance-specific naming should be marked with "Reference instance example" sidebars or use generic placeholders (e.g., `<Region>DefaultExamplePolicy`) | Small |
| `docs/08-app-orchestrator.md` | `docs/framework/08-app-orchestrator.md` | Move as-is | Trivial |
| `docs/09-mvi-pattern.md` | `docs/framework/09-mvi-pattern.md` | Move; the instance-domain example ViewModel can stay if marked "Reference instance example" or be replaced with a generic `XScreen/XEvent/XState` shape | Small |
| `docs/10-boot-phases.md` | `docs/framework/10-boot-phases.md` | Move as-is; the tenant-switch test example uses a specific composite key string — generic enough | Trivial |
| `docs/11-mg-and-runtime-config.md` | `docs/framework/11-mg-and-runtime-config.md` | Move as-is | Trivial |
| `docs/12-departments-and-session.md` | `docs/framework/12-departments-and-session.md` | Move as-is | Trivial |
| `docs/13-onboarding-a-variant.md` | `docs/framework/13-onboarding-a-tenant.md` | Rename + move; instance region/tenant slugs (`<region>:<tenant>`) used as illustrations should remain as illustrative examples or use generic placeholders | Small |
| `docs/14-build-performance.md` | `docs/framework/14-build-performance.md` | Move as-is | Trivial |
| `docs/15-tech-stack.md` | `docs/framework/15-tech-stack.md` | Move as-is | Trivial |
| `docs/16-glossary.md` | `docs/framework/16-glossary.md` | Move; soften entries that cite instance type names to generic ("e.g., FintechAuthApi for any product; project-specific *Api for the domain") | Small |
| `docs/17-project-structure.md` | `docs/framework/17-project-structure.md` + `docs/reference-app/reference-project-structure.md` | Split: framework keeps a *skeleton* tree showing only structural shape; reference-app holds the full tree with concrete paths | Medium |
| `docs/18-webview-integration.md` | `docs/framework/18-webview-integration.md` | Move; instance-named policy examples should be marked or generalized | Small |
| `docs/19-tenants-and-variants.md` | `docs/framework/19-tenants-and-regions.md` | Rename + move; the 11-tenant migration worked example moves to `docs/reference-app/multi-tenant-migration-example.md` but a *short* reference stays in framework/19 | Small |
| `docs/20-chat.md` through `docs/30-form-wizard.md` | `docs/framework/20-30.md` | Move with light edits: "Reference instance example" sidebars around instance-flavored examples. For `docs/30-form-wizard.md` specifically: keep the wizard contract pattern in framework; move long multi-step flow inventories to `docs/reference-app/instance-flows.md` | Small per doc; medium for 30 |

---

## 3. The Four Hybrid Docs in Detail

### 3.1 `docs/03-core.md`

**What moves to framework:**
- §1 Purpose (with "Repository interfaces" example replaced by a placeholder)
- §2.1 `tenant/` (TenantContext, TenantId, TenantFlags, TenantParams, TenantCapabilities) — framework-mandatory
- §2.2 `runtime/` — framework-mandatory
- §2.3 `session/` — framework-mandatory
- §2.7 `mvi/` — framework-mandatory
- §2.8 `scope/` — framework-mandatory
- §3 What Does NOT Go In `:core` — framework rule
- §4 Stability Discipline — framework rule

**What gets a generic placeholder + "see reference app" link:**
- §2.4 `repository/` — replace concrete interfaces with one or two placeholder examples (`AuthRepository` + a generic `<YourCapability>Repository`) and a link to the reference-app's full inventory
- §2.5 `policy/` — same treatment
- §2.6 `model/` — same treatment

**What moves to `docs/reference-app/domain-types.md`:**
- The full list of concrete repository interfaces (whatever the current reference instance defines)
- The full list of concrete policy interfaces
- The full list of concrete domain models

### 3.2 `docs/05-data.md`

**What moves to framework:**
- §1 Purpose (with `Fintech*Api` described as "the project's Retrofit interfaces, split by feature area")
- §2 Module Layout (with placeholder feature areas like `auth/`, `<feature1>/`, `<feature2>/`)
- §3 The Retrofit Surface — keep one short generic `FintechAuthApi` example showing the per-area pattern
- §3 third-party clients section (`:data/external/`) — keep as generic pattern
- §4 Repository Implementation Pattern — generic example
- §5 The Hilt Binding Module — generic skeleton with placeholder bindings
- §6, §7, §8, §9 — all generic

**What moves to `docs/reference-app/api-surface.md`:**
- The full concrete `Fintech*Api` family for the reference instance (e.g., all the loan/repayment/guarantor/KYC APIs if lending; all the policy/claim/beneficiary APIs if insurance; etc.)
- The reference instance's third-party clients
- The `DataModule` example with the full set of concrete bindings

### 3.3 `docs/17-project-structure.md`

**What moves to framework:**
- Top-level tree shape (`aos-sdk`, `core`, `design-system`, `data`, `features`, `features-{name}`, `tenants/<region>/<base|default|tenant>`, `app`)
- Each section's internal layout shape (sub-package names like `policy/`, `format/`, `flags/`, `di/`)
- The `settings.gradle.kts` shape with placeholder includes

**What moves to `docs/reference-app/reference-project-structure.md`:**
- Full tree with concrete reference-instance paths
- All concrete file names (specific to the reference instance's domain)
- Full `:tenants:<region>:<tenantSlug>` layout for the reference's tenant
- `settings.gradle.kts` with concrete `include()` calls

### 3.4 `docs/30-form-wizard.md`

**What moves to framework:**
- §1 Why a separate contract
- §2 The `:core/wizard/` contract
- §4 NavGraph scoping (`hiltViewModel(parentEntry)` pattern)
- §5 Draft persistence
- §6 Validation per step
- §8, §9 — what does not belong, cross-refs

**What moves to `docs/reference-app/instance-flows.md`:**
- §3 The concrete instance flow example (full code listings)
- §7 Long multi-step flow inventories specific to the reference instance

**Replace in framework with:** a generic "MultiStepFlow" example with 3-4 placeholder steps showing the same pattern.

---

## 4. Cross-Reference Updates

The refactor breaks every cross-reference in the docs (paths change from `docs/XX-name.md` to `docs/framework/XX-name.md` or `docs/reference-app/...`). Mechanical but tedious.

**Plan:**
- One sweeping find-and-replace per cross-link target.
- Update `CLAUDE.md` and `README.md` doc index tables to point to new paths.
- Update `PRD-FIT-ASSESSMENT.md` cross-refs (it links to many `docs/`).
- Update the `framework-template/` docs once paths settle.

---

## 5. Sequence

Recommended PR sequence:

1. **PR 1: Create `docs/framework/` and move trivial moves** (filename renames + as-is moves: docs 01, 02, 04, 08, 10, 11, 12, 14, 15, 18). Update cross-refs within these docs. Doesn't touch instance content. Lowest risk.
2. **PR 2: Move FRAMEWORK + EXAMPLES docs** (06, 07, 09, 13, 16, 19, 20–29). Add "Reference instance example" sidebars. Mostly cosmetic editing.
3. **PR 3: Split hybrid doc 17 (project-structure)** — create `docs/reference-app/reference-project-structure.md` with full tree; reduce framework/17 to skeleton.
4. **PR 4: Split hybrid doc 30 (form-wizard)** — create `docs/reference-app/instance-flows.md`; reduce framework/30 to pattern.
5. **PR 5: Split hybrid doc 05 (data)** — create `docs/reference-app/api-surface.md`; reduce framework/05 to pattern.
6. **PR 6: Split hybrid doc 03 (core)** — create `docs/reference-app/domain-types.md`; reduce framework/03 to pattern. Biggest reach.
7. **PR 7: Update overview doc 00** to match new structure; finalize `CLAUDE.md` and `README.md` indexes.
8. **PR 8: Create `docs/reference-app/README.md` and `prd-summary.md`** — the front door for reference-app content.

Total estimate: ~10 working days of doc work, doable in parallel PRs.

---

## 6. Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Cross-reference rot during multi-PR refactor | Land PRs in sequence; each PR keeps the doc-set consistent (no broken links) |
| Reference-app docs become stale as the consuming project evolves | Make `docs/reference-app/` ownership clear — it tracks the consuming product; updated whenever the consuming team changes their API/domain. Framework docs should not need updates from consumer changes |
| Engineers don't realize there's a `docs/framework/` vs `docs/reference-app/` split | Aggressive README signposting; rename the docs index in `README.md` to "Framework Documentation" and add a separate "Reference Application Documentation" section |
| Future products copy from `docs/reference-app/` and inherit the reference instance's domain assumptions | `docs/reference-app/README.md` opens with "this is **one** instance of the framework; for the *rules*, see `docs/framework/`" |
| The "Reference instance example" sidebars get edited away by future updates | Make sidebars visually distinct (admonition style); add a CONTRIBUTING note that they're load-bearing |
| The framework's identity becomes coupled to the current reference instance's domain | Continue policing this: the framework spec's docs use placeholders and generic prose; instance content lives behind the reference-app divider |

---

## 7. Out of Scope (Deliberately)

The refactor does **not**:

- Rename "Compass" (separate question — see [`04-naming-decisions.md`](04-naming-decisions.md))
- Build the `starter/` skeleton (separate deliverable; extract from the consuming project's first commits when ready)
- Move content from `:aos-sdk` (the SDK is already framework-level)
- Refactor `CLAUDE.md` invariants (those are framework-spec already; only cross-refs need updating)
- Touch `PRD-FIT-ASSESSMENT.md` content (only its cross-refs need updating)

---

## 8. Cross-references

- The strategy this plan executes: [`00-strategy.md`](00-strategy.md)
- The audit that drove this plan: [`01-current-state-audit.md`](01-current-state-audit.md)
- The new-project init checklist that consumes the refactored docs: [`03-new-project-init-checklist.md`](03-new-project-init-checklist.md)
- The naming decisions that should be made *before* this refactor: [`04-naming-decisions.md`](04-naming-decisions.md)
- The reference instance (for context, not as the standard): [`05-case-study-lending.md`](05-case-study-lending.md)
