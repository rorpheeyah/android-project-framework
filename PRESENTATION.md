# Compass — Architecture Brief

> 10-min read · audience: eng lead + team · authoritative spec in `docs/`

---

## The problem

```
   if (bank == "KH") { … }                ← scattered across every flow
   else if (bank == "VN") { … }           ← scattered across every flow
   else if (bank == "PPC") { … }          ← scattered across every flow
   else { /* MY, TH, ID, … */ }
```

Regression risk = **O(banks × features)**.

Three approaches we rejected:

| Approach | Cost |
|---|---|
| `if (bank == "X")` chains | One PR touches every flow |
| One APK per bank | Cherry-picks across N branches; CI/store-listing matrix |
| Runtime DI hot-swap | Engineering effort on a flow users never take |

---

## The solution: a layered DAG

```
                   :aos-core                                 ← infrastructure
                        ↑                                      (HTTP, security,
                        │                                       storage, logs)
        ┌───────────────┴───────────────┐
      :core                       :design-system            ← contracts +
        ↑   ↑   ↑                       ↑   ↑                 UI primitives
        │   │   │                       │   │
      :data │  :variants-{id}      :features  :features-chatbot  :features-{name}
            │       ↑                   ↑           ↑                 ↑
            └───────┴───────────┬───────┴───────────┴─────────────────┘
                                ↓
                              :app                          ← orchestrator
```

8 module shapes. Depend upward, never sideways. Forbidden imports enforced at compile time.

---

## Four promises

| Promise | Mechanism |
|---|---|
| **Server-side demux** | One backend handles per-user routing. No `KhBakongApi` on the client. |
| **Login-once binding** | `LoggedInComponent` built at login, dropped at logout. No runtime swap. |
| **MG bootstrap** | Only the MG URL is hardcoded. Real backend URLs fetched from MG at cold start. |
| **Additive variant onboarding** | New variant = new module + 2 lines. Zero change to `:features` / `:data`. |

---

## Variant snapshot: same interface, different content

```
                       KH (Cambodia)              │  VN (Vietnam)
─────────────────────────────────────────────────────────────────────────────
TransferAmountPolicy:
  dailyLimit           4M KHR  (≈ $1,000)         │  500M VND  (≈ $20,000)
  minimum              none                       │  10,000 VND (regulator)
  errors               English                    │  Vietnamese

FeeCalculator (external transfers):
                       flat 0.5%                  │  tiered: 7,700 VND ≤ 500k,
                                                  │          0.05% above

VariantCapabilities:
  KHQR rail            ✓                          │  ✗ (uses VietQR instead)
  cardless ATM         ✗                          │  ✓
  bilingual receipt    ✓ (KH + EN)                │  ✗

AmountFormatter:
                       "1,234,567៛"               │  "1.234.567₫"

SupportContacts:
                       +855 23 999 999            │  +84 24 7300 8000
                       Mon–Fri 8a–8p ICT          │  Mon–Sat 8a–9p ICT
```

~10 small files per variant. Same `:core` interfaces. Different content.

→ Full code in [`docs/07-variants.md` § 5.9](docs/07-variants.md).

---

## Onboarding cost: adding `:variants-my`

| Touch | Lines |
|---|---|
| New `:variants-my` module | 150–250 |
| `settings.gradle.kts` | +1 |
| `:app/variant/VariantCatalogue.kt` | +1 |
| **`:features`** | **0** |
| **`:data`** | **0** |
| **`:design-system`** | **0** |
| **Other variants** | **0** |
| **`:core`** | **0** (unless adding a new policy interface) |

Compile-time enforced.

---

## Worked flow: KH user submits a transfer

```
  Submit tap
     ↓
  TransferInputViewModel
     policy.validate(amount)        ← KhTransferAmountPolicy   from :variants-kh
     repo.submit(intent)
     ↓
  TransferRepository                ← :core interface
     ↓ Hilt resolves to active impl
  FintechTransferRepo               ← :data
     POST /v1/transfer/submit
         Authorization: Bearer …
         X-Account-Id: acc-001
     ↓
  [Fintech backend → Bakong rail]   ← server-side dispatch
```

UI didn't know KH. Repo didn't know KH. The server figured it out from auth.

---

## Tradeoffs we accept

| Cost | Why we're OK with it |
|---|---|
| Slightly larger APK | Variants are 10 files each; multi-variant compile-in cost negligible |
| Convention-based feature boundaries | Lint enforces; build-perf wins justify |
| Variant change = logout-login | Runtime swap is costly machinery for a non-real flow |

---

## FAQ

| Question | Answer |
|---|---|
| Can a user log in to different banks on the same device? | **Yes.** Logout drops the session graph (component, prefs, cache) and pops navigation to root. The next login rebuilds with whatever `variantId` the server returns. No special code path. |
| What about per-bank branding (logos, colors)? | Future-roadmap. Branding is intentionally **not** in `RuntimeConfig`; per-variant theming is a separate mechanism. |
| What if our next project isn't multi-tenant? | Drop `:variants-{id}`. Everything else (`:aos-core`, `:core`, `:design-system`, `:data`, `:features`, MG, `LoggedInComponent`) is reusable as a clean modular Android stack. |
| What if a variant grows to need its own UI/API/screens? | Goes in a sibling module: `:features-{feature-name}` (e.g. `:features-bakong-disputes`), gated by a `VariantCapabilities` flag. The variant module stays pure. |

---

## Status & sequence

Architecture spec: **complete** (18 docs in `docs/`). Implementation: **not started**.

```
1. :aos-core          (vendor or new submodule)
2. :core              (interfaces, models, RuntimeConfig, Session)
3. :design-system     (theme + first components)
4. :data              (Fintech*Api family + first repo: auth)
5. :variants-kh       (policies for the first market)
6. :app               (BootCoordinator + LoggedInComponent)
7. End-to-end flow    (login → balance → first transfer)
8. :variants-vn       (canonical demo of the architecture's promise)
```

---

## Reference (deeper docs)

| Question | Doc |
|---|---|
| Why this shape? | [`docs/00-overview.md`](docs/00-overview.md) |
| Dependency rules? | [`docs/01-module-topology.md`](docs/01-module-topology.md) |
| Boot mechanism? | [`docs/10-boot-phases.md`](docs/10-boot-phases.md) |
| Onboarding step-by-step? | [`docs/13-onboarding-a-variant.md`](docs/13-onboarding-a-variant.md) |
| Full project tree? | [`docs/17-project-structure.md`](docs/17-project-structure.md) |
