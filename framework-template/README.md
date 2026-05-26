# Framework Template — Generalization & Reuse

> **Purpose of this folder:** strategy and planning artifacts for using this framework as a starting point for any Android product, regardless of business domain.
> **Scope:** doc structure recommendations + a domain-neutral init checklist + naming conventions. Code scaffolding is a future deliverable, deliberately deferred until a second consuming project validates the patterns.

---

## Framing

This framework is a **general-purpose Android architecture for multi-tenant products**. It applies to:

| Vertical | Example product shape |
|---|---|
| Fintech — banking | Personal banking app with multiple partner-bank tenants |
| Fintech — lending | Loan origination & servicing app with multiple lender tenants |
| Fintech — insurance | Policy management with multi-carrier tenants |
| Fintech — brokerage | Trading app with multi-broker tenants |
| Enterprise — expense | Corporate expense management with multi-company tenants |
| Marketplace | Buyer/seller app with multi-vendor tenants |
| White-label SaaS | Single APK shipping for multiple customer organizations |

What makes the framework *general* is its centerpieces:

- **Single tenant axis** for organization-level differentiation, with **regional grouping** as a Gradle module hierarchy
- **Logic-Blind UI** separated from tenant policies by compile-time-enforced dependency rules
- **`:aos-sdk`** as a banking-agnostic infrastructure layer (network, security, storage, camera, ML, push, deep links, locale, PDF, work, DB)
- **`:core`** as the domain contract layer where each consuming product defines its own types
- **MG-driven runtime config** with stale-config fallback

None of these are tied to a specific business domain. They are scaffolding for *any* multi-tenant Android product.

---

## What's In This Folder

| Doc | Purpose |
|---|---|
| [`00-strategy.md`](00-strategy.md) | The two-tier doc separation (framework spec vs. reference instance) and the rationale. Read first. |
| [`01-current-state-audit.md`](01-current-state-audit.md) | Classification of every existing `docs/` file: framework-spec, framework-spec-with-examples, or instance-specific. Drives the refactor plan. |
| [`02-doc-refactor-plan.md`](02-doc-refactor-plan.md) | Concrete plan for splitting `docs/` into `docs/framework/` (generic) and `docs/reference-app/` (instance content). |
| [`03-new-project-init-checklist.md`](03-new-project-init-checklist.md) | Domain-neutral, step-by-step checklist for bootstrapping a new product on the framework. Uses placeholders throughout. |
| [`04-naming-decisions.md`](04-naming-decisions.md) | Audit of every framework name: keep verbatim across projects, parameterize per project, or rename now. |
| [`05-case-study-lending.md`](05-case-study-lending.md) | The current consuming project (a multi-tenant lending app) treated as **one** worked example. Concentrated here so other docs stay neutral. |

---

## The Core Idea (One Page)

The framework today has two kinds of content mixed in `docs/`:

1. **Framework spec** — architectural rules, capability primitives, the tenant model, MVI conventions. Applies to *every* product on this architecture.
2. **Reference-instance content** — concrete domain types, API surfaces, and tenant identities specific to whichever product the framework is currently being built against.

For a single consuming project, the mix is acceptable. For the framework to be *reusable*, the two need to be separated so:

- A new product reads the framework spec without wading through another product's domain examples
- A new product can copy the *structural* scaffolding without inheriting the *domain* assumptions
- The reference instance remains useful as "one concrete way this framework has been applied"

### Recommended target structure

```
android-project-framework/
├── docs/
│   ├── framework/                           ← architectural rules — applies to every product
│   │   ├── 00-overview.md
│   │   ├── 01-module-topology.md
│   │   ├── 02-aos-sdk.md
│   │   ├── ...
│   │   └── 30-form-wizard.md
│   └── reference-app/                       ← one concrete product as an illustration
│       ├── README.md
│       ├── domain-types.md
│       ├── api-surface.md
│       └── ... (instance-specific files)
├── starter/                                 ← future: skeleton files a new project copies
│   └── (TBD — built once a second consuming project shapes the requirements)
└── framework-template/                      ← THIS FOLDER — planning + strategy
```

A new product's onboarding:

1. Read `docs/framework/` for the architecture.
2. (Once it exists) copy `starter/` into a new git repo as the initial commit.
3. Add `:aos-sdk` as a submodule pinned to a release tag.
4. Follow `03-new-project-init-checklist.md` to define your domain types and tenant identity.
5. Optionally consult `docs/reference-app/` for "one team did it this way."

---

## What This Folder Is NOT

- **Not the starter template itself.** The `starter/` directory will hold actual skeleton files when it exists. This folder holds the *plan*.
- **Not a code generator.** Yeoman/cookiecutter-style automation is deferred until 2–3 projects shape the requirements.
- **Not a rename of the framework name.** Whether the framework should be renamed is addressed in `04-naming-decisions.md` as a separate question.
- **Not tied to any business domain.** Lending, insurance, brokerage, enterprise — pick yours. Documents in this folder use placeholders or rotate through several domains in examples.

---

## Reading Order

1. **[`00-strategy.md`](00-strategy.md)** — the strategy. If you only read one doc, read this.
2. **[`01-current-state-audit.md`](01-current-state-audit.md)** — concrete inventory of what's where today.
3. **[`02-doc-refactor-plan.md`](02-doc-refactor-plan.md)** — the proposed reorganization.
4. **[`03-new-project-init-checklist.md`](03-new-project-init-checklist.md)** — the deliverable a future product consumes.
5. **[`04-naming-decisions.md`](04-naming-decisions.md)** — naming policy.
6. **[`05-case-study-lending.md`](05-case-study-lending.md)** — optional, for context on the first consuming project.

---

## Cross-references

- The framework spec (current state): [`../docs/`](../docs/)
- The current reference instance (lending): [`../PRD-FIT-ASSESSMENT.md`](../PRD-FIT-ASSESSMENT.md) — kept at repo root as historical record
- Project-level instructions for the framework spec: [`../CLAUDE.md`](../CLAUDE.md)
