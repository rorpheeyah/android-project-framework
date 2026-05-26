# 01 · Current State Audit

> **Purpose:** Inventory every doc in `docs/` and classify it by reuse property. Drives [`02-doc-refactor-plan.md`](02-doc-refactor-plan.md).
> **Method:** Each doc is read for the ratio of *architectural rules* (apply to any consuming product) vs. *instance content* (specific to whichever product is currently being built against the framework).

---

## 1. Classification System

| Class | Meaning |
|---|---|
| **FRAMEWORK** | Pure architectural rule or capability spec. Applies to every project on this architecture. Domain-neutral. |
| **FRAMEWORK + EXAMPLES** | Architectural content + concrete examples drawn from a specific business domain. Restructure so the rule stays generic and examples are clearly tagged. |
| **INSTANCE** | Specific to one consuming product. Move to `docs/reference-app/` or extract the instance-specific bits. |
| **HYBRID** | Significant mix of framework and instance content. Needs surgical separation. |

---

## 2. Doc-by-Doc Audit

The current reference instance the framework was built against happens to be a multi-tenant lending product. Where examples are drawn from that product, they're noted as "instance examples" — they are not architectural requirements.

| # | File | Class | What's universal | What's instance-specific |
|---|---|---|---|---|
| 00 | overview.md | FRAMEWORK + EXAMPLES | Conditional-Logic-Sprawl problem, two-layer foundation, single tenant axis | The "Modules" table mentions domain types from the reference instance |
| 01 | module-topology.md | FRAMEWORK | DAG, forbidden imports, build-graph properties | None |
| 02 | aos-core.md (filename pending rename) | FRAMEWORK | `:aos-sdk` infrastructure, all sub-packages | None |
| 03 | core.md | HYBRID | Contract-layer concept, tenant types, MVI base, scope annotations | Reference repository interfaces and policy interfaces use the instance's domain types |
| 04 | design-system.md | FRAMEWORK | Theme tokens, primitive composables convention, domain-agnostic rule | None |
| 05 | data.md | HYBRID | `:data` layer architecture, Retrofit-per-feature-area pattern, `:data/external/` for third-party, `DataModule` shape | The reference `Fintech*Api` family uses the instance's domain |
| 06 | features.md | FRAMEWORK + EXAMPLES | Hybrid-Monolith principle, Logic-Blind contract, package-based feature boundaries | Example directory tree shows packages from the instance |
| 07 | variants.md (filename pending rename) | FRAMEWORK + EXAMPLES | Tenant silos and region bases, Hilt multibinding pattern, isolation rules | Worked examples use instance tenant naming |
| 08 | app-orchestrator.md | FRAMEWORK | `:app` orchestrator role, BootCoordinator, navigation, DI registry | None significant |
| 09 | mvi-pattern.md | FRAMEWORK + EXAMPLES | MVI contract, ViewModel template, state-holding discipline | Example ViewModel uses an instance domain |
| 10 | boot-phases.md | FRAMEWORK | Boot sequence, `@LoggedInScoped` component, logout safety | None — the tenant-switch test example uses a specific composite key, but the pattern is generic |
| 11 | mg-and-runtime-config.md | FRAMEWORK | MG contract, `RuntimeConfig`, force-update gate, stale-config fallback | None |
| 12 | departments-and-session.md | FRAMEWORK + EXAMPLES | DepartmentAccount, Session, AccountIdInterceptor | LoginResponse example uses instance field names |
| 13 | onboarding-a-variant.md (filename pending rename) | FRAMEWORK + EXAMPLES | Tenant/region onboarding checklist | Examples use instance region/tenant slugs |
| 14 | build-performance.md | FRAMEWORK | Hybrid-Monolith rationale, build graph properties | None significant |
| 15 | tech-stack.md | FRAMEWORK | Tech stack reference card | None |
| 16 | glossary.md | FRAMEWORK + EXAMPLES | Defines framework-specific terms | Some entries reference instance type names |
| 17 | project-structure.md | FRAMEWORK + EXAMPLES | Single-page module tree | Full tree includes the instance's domain-specific paths |
| 18 | webview-integration.md | FRAMEWORK | WebView primitives, JS bridge, URL allowlist | Some examples use instance-named policy classes |
| 19 | tenants-and-variants.md (filename pending rename) | FRAMEWORK + EXAMPLES | Tenant model: TenantContext, flags/params, structural escalation, region as Gradle hierarchy | One worked-example section uses 11 instance-specific tenant orgs |
| 20 | chat.md | FRAMEWORK | Sendbird integration pattern, provider-agnostic ChatRepository | None significant — chat is a universal capability |
| 21 | push-channels.md | FRAMEWORK + EXAMPLES | NotificationChannelRegistry, channel-set pattern | The specific channel names (reminder/transaction/announcement) are reasonable defaults but project-configurable |
| 22 | deeplinks.md | FRAMEWORK + EXAMPLES | App Links, JWT-signed payloads, NavGraph integration | Example route types use instance domain names |
| 23 | kyc-capture.md | FRAMEWORK + EXAMPLES | In-house CameraX + ML Kit split, SDK primitives in :aos-sdk | Some examples assume KYC is for borrower/guarantor; pattern is broader |
| 24 | pdf.md | FRAMEWORK + EXAMPLES | PDF download + preview + share primitives | Examples reference "loan contract" — easy to generalize |
| 25 | locale.md | FRAMEWORK + EXAMPLES | Runtime locale switching, font fallback, strings.xml rule | Specific languages (KR/EN/KH) are instance-specific |
| 26 | pin-and-session.md | FRAMEWORK | PIN UX, HMAC storage, lockout, biometric tier, session timeout | None significant |
| 27 | maps-and-location.md | FRAMEWORK + EXAMPLES | Google Maps Compose integration, offline-cached locations | "Branch locator" is an instance feature name; pattern is "show locations on a map" |
| 28 | background-work.md | FRAMEWORK + EXAMPLES | WorkManager wrapper, Hilt-integrated WorkerFactory, tag-by-userId | Worker inventory mentions instance-specific workers |
| 29 | local-database.md | FRAMEWORK + EXAMPLES | Room + SQLCipher wrapper, per-feature DBs | "What gets cached" table mentions instance-specific DBs |
| 30 | form-wizard.md | HYBRID | Wizard contract pattern, NavGraph-scoped ViewModel, draft persistence | Long multi-step flows used as worked examples are instance-specific |

---

## 3. Summary Table

| Class | Count | Action |
|---|---|---|
| FRAMEWORK (pure) | 9 | Keep in `docs/framework/` as-is |
| FRAMEWORK + EXAMPLES | 17 | Keep in `docs/framework/`. Tag examples as "Reference instance example:" sidebars. Where domain is too specific, replace with generic placeholder + a link to the case study. |
| HYBRID | 4 (docs 03, 05, 17, 30) | Extract instance-specific content to `docs/reference-app/`; keep generic pattern in `docs/framework/`. |
| INSTANCE (pure) | 0 | — |

**Net effort:** the bulk is in **4 hybrid docs** (03, 05, 17, 30) where instance content is substantial enough to deserve its own home. The 17 "FRAMEWORK + EXAMPLES" docs need lighter editing — primarily adding "Reference instance example:" callouts around concrete code or replacing with abstract placeholders.

---

## 4. What Needs to Land in `docs/reference-app/`

Inventory of instance-specific content that should live in the reference-app section:

| Content | Source doc | Destination |
|---|---|---|
| Concrete repository interfaces for the instance's domain (loan, repayment, guarantor, KYC, referral, consultation, branch) | docs/03 §2.4 | `docs/reference-app/domain-types.md` |
| Concrete policy interfaces for the instance's domain (eligibility, EMI calc, repayment penalty, KYC requirements) | docs/03 §2.5 | `docs/reference-app/domain-types.md` |
| Concrete domain model types (loan products, applications, repayment schedules, installments, guarantors, etc.) | docs/03 §2.6 + docs/17 | `docs/reference-app/domain-types.md` |
| The instance's `Fintech*Api` family with concrete endpoints | docs/05 §3 | `docs/reference-app/api-surface.md` |
| Third-party clients specific to the instance (credit bureau, statement analyzer, etc.) | docs/05 §3 | `docs/reference-app/api-surface.md` |
| The instance's concrete tenant module structure | docs/07, docs/13, docs/17 | `docs/reference-app/reference-tenant.md` |
| The instance's multi-step flows (long apply-form wizards, etc.) | docs/30 §3, §7 | `docs/reference-app/instance-flows.md` |
| The 11-tenant migration worked example | docs/19 §9 | `docs/reference-app/multi-tenant-migration-example.md` |
| Full module tree with instance-specific paths | docs/17 | `docs/reference-app/reference-project-structure.md` |

---

## 5. What Stays Generic Verbatim

These docs need essentially no editing — already product-agnostic:

- docs/01 (Module Topology) — pure architectural rules
- docs/02 (`:aos-sdk`) — infrastructure layer, banking-agnostic by invariant
- docs/04 (`:design-system`) — domain-agnostic by invariant
- docs/08 (`:app` orchestrator) — coordination pattern
- docs/10 (Boot Phases) — boot mechanics
- docs/11 (MG & RuntimeConfig) — service-discovery contract
- docs/14 (Build Performance) — Hybrid-Monolith rationale
- docs/15 (Tech Stack) — reference card
- docs/18 (WebView Integration) — primitive
- docs/26 (PIN & Session) — security pattern

These move to `docs/framework/` with zero content changes (just filename/path updates where needed).

---

## 6. Edge Cases and Decisions

### 6.1 Capability docs (20-30)

Most are FRAMEWORK + EXAMPLES because they cite uses from the current reference instance. The pattern is universal but examples are domain-flavored. Options:

- **A.** Strip examples, keep only generic patterns. Loses readability.
- **B.** Keep examples; tag with "Reference instance example:" sidebars. Preferred.
- **C.** Move examples to `docs/reference-app/` with cross-references. Acceptable but more clicks.

**Recommendation:** B. Keep examples inline with visually-distinct sidebars. New products skim past; existing project doesn't lose context.

### 6.2 Filenames carrying old names

Three filenames carry pre-refactor names (kept for cross-reference stability so far):

- `docs/02-aos-core.md` → `docs/02-aos-sdk.md`
- `docs/07-variants.md` → `docs/07-tenants.md`
- `docs/13-onboarding-a-variant.md` → `docs/13-onboarding-a-tenant.md`
- `docs/19-tenants-and-variants.md` → `docs/19-tenants-and-regions.md`

**Recommendation:** rename during the refactor. The doc-refactor PR is the natural moment — all cross-references get updated in one pass anyway.

### 6.3 `PRD-FIT-ASSESSMENT.md`

This is necessarily instance-specific (it assesses the framework against *the current reference PRD*). It stays at the repo root as historical record. It documents the journey from "framework as initially drafted" to "framework as it exists after the first PRD-fit work" — valuable context for understanding *why* certain decisions were made, but not part of the framework spec future projects need to consume.

A separate `docs/reference-app/prd-summary.md` can extract the parts of the PRD discussion that are useful for understanding the reference-app architecture, with the full assessment linked.

### 6.4 The framework brand name in the docs

The framework currently identifies as "Compass" throughout. Whether this name should stay, change, or be split (one name for the framework, another for the SDK / reference apps) is addressed in [`04-naming-decisions.md`](04-naming-decisions.md). The audit does not pre-judge this — it just notes where the name appears.

---

## 7. Cross-references

- The strategy this audit informs: [`00-strategy.md`](00-strategy.md)
- The refactor plan derived from this audit: [`02-doc-refactor-plan.md`](02-doc-refactor-plan.md)
- The framework spec being audited: [`../docs/`](../docs/)
