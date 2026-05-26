# 00 · Strategy

> **Goal:** Make this framework reusable as a starting point for any multi-tenant Android product — fintech, enterprise, marketplace, white-label SaaS, or otherwise — without each new project re-deriving the architecture or being constrained by another project's domain choices.

---

## 1. The Recommendation in One Paragraph

Treat this codebase as a **framework with reference instances**, not a project that happens to be a framework. Split the existing `docs/` into a domain-neutral **framework spec** (architectural rules, capability primitives, tenant model) and a clearly-isolated **reference-app section** holding whatever concrete product the framework is currently being built against. Add a domain-neutral **init checklist** using placeholders so new products can follow it regardless of business domain. Defer code scaffolding until a second consuming project shapes the requirements — premature templating locks in shape before the second use case validates it.

---

## 2. Why Now

Three reasons:

1. **The framework spec is settled.** The architectural decisions (single tenant axis, `:aos-sdk` capability split, MG-driven config, etc.) are stable. Further refinement risks rework if we generalize *and* spec-evolve simultaneously.
2. **There is currently one consuming project.** Generalizing while only one consumer exists is theoretical; generalizing after the second consumer has shipped requires migrating two codebases. The window between project #1 and project #2 is the cheapest moment to draw the framework/instance line.
3. **The framework is doc-only.** Cost of restructuring is days, not weeks. Once consuming projects have substantial code and the framework's published-artifact contract is locked, restructuring becomes a coordinated effort across multiple repos.

---

## 3. The Three Layers of Content

Any framework with reference instances has three content layers. Recognizing them is the basis for the split:

| Layer | What it contains | Reuse property |
|---|---|---|
| **Framework spec** | Architectural rules, dependency DAG, MVI conventions, tenant model, capability primitives in `:aos-sdk` | Universal — applies to every product on this architecture |
| **Convention & placeholder** | Naming conventions, package conventions, "your project picks its prefix" guidance | Universal pattern, per-project value |
| **Reference instance** | Domain types, API surfaces, concrete tenant identities, business-flow specifics | Specific to one consuming product |

The current `docs/` mixes all three. The refactor separates them into distinct homes.

---

## 4. Why Two-Tier, Not Three-Tier

Considered alternatives:

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| **A. Keep mixed docs as-is** | Zero work | New products must mentally strip out the current reference-app domain to understand the framework; framework rules and instance content drift apart over time | ❌ |
| **B. Two-tier: framework + reference instance** | Clear separation; new products can ignore reference-app section; reference app stays useful as "how it was done once" | One doc restructure; some cross-link updates | ✅ **Recommended** |
| **C. Three-tier: framework + capability library + reference instance** | Even cleaner | The capability docs (20-30) are already SDK-aligned; splitting them out duplicates content | ❌ Over-engineering |
| **D. Separate repos: framework-spec, aos-sdk, reference-app** | Pure-est separation | Repo proliferation; cross-repo doc links rot; high coordination cost | ❌ Premature; revisit at 4+ consumers |

**Two-tier** is the lightest meaningful split. Capability docs (20-30) describe `:aos-sdk` primitives in product-agnostic terms — they don't need to move. Only truly project-specific content (domain types, concrete tenants, business-flow specifics) needs to migrate.

---

## 5. The Four Deliverables

To make the framework reusable, four things need to exist:

### 5.1 Refactored docs

`docs/framework/` contains domain-neutral architectural rules and capability specs. `docs/reference-app/` contains whichever product the framework is currently being built against, clearly labeled as one instance.

See [`02-doc-refactor-plan.md`](02-doc-refactor-plan.md) for the file-by-file plan.

### 5.2 A `starter/` directory (future)

Holds skeleton files using placeholder names:

```
starter/
├── settings.gradle.kts                      # all module includes commented; uncomment as you build
├── build.gradle.kts                         # root build script with version catalog
├── gradle/libs.versions.toml                # version catalog with :aos-sdk pinned to a tag
├── aos-sdk/                                 # git submodule pointer
├── core/                                    # minimum: TenantContext, Session, MVI base, scope annotations
├── design-system/                           # minimum: theme + 3 primitive components
├── data/                                    # minimum: DataModule scaffold + one example *Api
├── features/                                # minimum: boot/ package
├── tenants/
│   └── <region>/                            # placeholder region — rename per project
│       ├── base/                            # empty Hilt module scaffold
│       └── default/                         # empty sentinel tenant
├── app/                                     # CompassApplication, MainActivity, BootCoordinator
└── README.md                                # how to use the starter
```

A new project copies the directory wholesale, renames `<region>` and brand placeholders, and follows the init checklist.

### 5.3 An init checklist

[`03-new-project-init-checklist.md`](03-new-project-init-checklist.md) — domain-neutral, step-by-step. Covers project naming, region/tenant choice, the minimum `:core` types every product needs, the first feature module to flesh out, server contract decisions, and the smoke test that proves the architecture is wired.

### 5.4 Naming decisions

[`04-naming-decisions.md`](04-naming-decisions.md) — what's framework-level (preserve verbatim), what's convention-level (each project picks its value), what's instance-level (project's own choice). The framework's brand name itself ("Compass") is part of this question.

---

## 6. What This Strategy Does NOT Do

Worth being explicit about:

- **It does not build code.** No starter actually exists yet — `starter/` is a future deliverable, not in this folder.
- **It does not retroactively split the current reference project.** That product can continue with the existing doc structure during its development; the refactor benefits *future* projects.
- **It does not change `:aos-sdk`.** The SDK is already framework-level by invariant.
- **It does not produce a code generator.** Cookiecutter/init-script automation comes once 2–3 projects have used the starter and the variation points are understood.

---

## 7. The Recommended Sequence

1. **Read this folder fully** (6 docs total) and decide if the strategy fits your trajectory.
2. **Decide on naming** ([`04-naming-decisions.md`](04-naming-decisions.md)) — small decisions that bake into every future project.
3. **Execute the doc refactor** ([`02-doc-refactor-plan.md`](02-doc-refactor-plan.md)) — split existing `docs/` into `docs/framework/` and `docs/reference-app/`. Estimated ~10 days of doc work across 8 PRs.
4. **Build the `starter/`** — extract skeleton files from the current consuming project's first commits, once those exist. Don't synthesize them in advance.
5. **Validate with project #2** — when the next consuming project starts, have them follow [`03-new-project-init-checklist.md`](03-new-project-init-checklist.md). Their pain points reveal where the starter and checklist need refinement.
6. **Iterate** — after project #2 ships its first release, the starter is battle-tested. Consider promoting it to a separate `compass-starter` git repo with versioning aligned to `:aos-sdk`.

---

## 8. What Success Looks Like

- A new product on any domain can read `docs/framework/` cover-to-cover and understand the architecture without needing to know what the current reference app does.
- A new product, given a PRD and the starter, can have a running "hello world" app (boot → mock login → empty dashboard) in **under one week**.
- The reference instance remains useful as "this is one concrete way to apply the framework" — readable, complete, but clearly marked as instance-specific.
- Adding a third project requires no further framework changes — only following the same init checklist.
- The framework's identity is **architectural**, not domain-coupled. "Compass" stops being "the lending framework" and becomes "the multi-tenant Android architecture."

---

## 9. What Failure Looks Like (To Avoid)

- A second product starts and the team feels the need to fork the framework rather than apply it → sign that the framework/instance line wasn't drawn well.
- The starter ships with concrete domain types in `:core` from the first reference app → sign that the doc refactor wasn't followed through.
- The init checklist requires the reader to mentally substitute another product's terminology to understand each step → sign that the checklist wasn't truly genericized.
- The framework's name becomes associated with a single product's brand → sign that naming wasn't decoupled.

---

## 10. Cross-references

- The detailed audit of what's where today: [`01-current-state-audit.md`](01-current-state-audit.md)
- The doc-refactor mechanics: [`02-doc-refactor-plan.md`](02-doc-refactor-plan.md)
- The init checklist for new projects: [`03-new-project-init-checklist.md`](03-new-project-init-checklist.md)
- The naming questions: [`04-naming-decisions.md`](04-naming-decisions.md)
- The current reference instance (as one worked example): [`05-case-study-lending.md`](05-case-study-lending.md)
