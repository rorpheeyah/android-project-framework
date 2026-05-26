# Compass Framework — Claude Code Rules & Init Agent

> **What this folder is:** rule files Claude Code reads so it can do work in a Compass-framework project without violating the architecture, plus a Claude Code subagent that bootstraps a brand-new project on the framework.
>
> **What this folder is not:** the framework spec. The spec lives in `docs/`. These files distill the spec into actionable rules and a step-by-step automation surface for Claude Code.

---

## Files

| File | Purpose |
|---|---|
| [`RULES.md`](RULES.md) | The master rule set. Hard invariants, forbidden imports, per-module dos & don'ts, tenant-dispatch rules, MVI conventions, naming. Self-contained — Claude Code can act from this file alone. |
| [`CLAUDE.md.template`](CLAUDE.md.template) | Drop-in `CLAUDE.md` for a new project. Imports `RULES.md` by reference and adds slots for project-specific decisions (brand prefix, region, tenant slugs, MG URLs). |
| [`compass-init.md`](compass-init.md) | Claude Code subagent definition (YAML-frontmatter) that walks the engineer through [`../03-new-project-init-checklist.md`](../03-new-project-init-checklist.md) Phases 0–3 interactively. Asks the Phase 0 decisions first, then scaffolds files in the right order. |

Both templates use the same placeholders documented in [`../03-new-project-init-checklist.md`](../03-new-project-init-checklist.md) (`<your-project>`, `<your-org-domain>`, `<your-org-prefix>`, `<region>`, `<tenant-slug>`, `<your-domain>`).

---

## How to use these in a new project

```
your-project/
├── CLAUDE.md                       # copy of CLAUDE.md.template, placeholders filled
├── .claude/
│   └── agents/
│       └── compass-init.md         # copy of compass-init.md
└── framework-rules/
    └── RULES.md                    # copy of RULES.md (or git submodule the framework repo and symlink)
```

Then in a fresh Claude Code session in that repo:

- Claude automatically reads `CLAUDE.md` at session start — the architectural rules are immediately in context.
- Run `/agents` to see `compass-init` listed, or just say: *"Use the compass-init agent to bootstrap this project."*
- Anywhere a deeper rule lookup is needed, Claude follows `CLAUDE.md`'s pointer to `framework-rules/RULES.md`.

---

## What Claude Code gets from these files

After reading `CLAUDE.md` + `RULES.md`, Claude should know:

1. **Which imports are illegal** at compile time — and refuse to add them even if asked.
2. **Where each kind of code lives** — refuse to put networking in `:tenants:*`, UI in `:data`, banking terms in `:aos-sdk`, etc.
3. **How tenant dispatch works** — never write `if (tenant.id == ...)`, always use polymorphism via `@TenantKey` multibinding.
4. **MVI conventions** — `UiState` / `UiEvent` / `UiEffect` shape per screen.
5. **Naming** — what to keep verbatim, what to parameterize per project, what's banned (the variant-* terminology in particular).
6. **Common red flags** — the patterns to push back on when the user asks for them.

When the engineer invokes the `compass-init` subagent, Claude additionally has the full bootstrap script: ask the 8 Phase 0 decisions, scaffold modules in dependency order, compile-checkpoint between phases, hand off with a punchlist.

---

## Why split rules from spec

The spec (`docs/`) is the **reasoning** — why the architecture is shaped this way. It's prose, examples, tradeoffs. Good for humans onboarding.

The rules (`RULES.md`) are the **conclusions** — actionable, scannable, written as "do this / don't do that" with cited sections. Optimized for an LLM's working memory: short, declarative, no narrative.

Both serve the same architecture; they trade off depth vs. density.

---

## Maintenance

When the framework spec changes:

1. Update `docs/` first (the source of truth).
2. Regenerate the affected sections of `RULES.md` from the new spec.
3. Bump the version line at the top of `RULES.md` so consuming projects know to re-pull.

The init agent (`compass-init.md`) is more stable — it changes only when the init checklist's structure changes, not on routine spec edits.

---

## Cross-references

- The framework spec: [`../../docs/`](../../docs/)
- The init checklist this agent automates: [`../03-new-project-init-checklist.md`](../03-new-project-init-checklist.md)
- Naming decisions baked into the rules: [`../04-naming-decisions.md`](../04-naming-decisions.md)
- The repository-level instructions for this framework repo: [`../../CLAUDE.md`](../../CLAUDE.md)
