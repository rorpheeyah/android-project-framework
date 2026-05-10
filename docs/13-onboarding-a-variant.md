# 13 · Onboarding a Variant

> **Promise:** Adding a new variant requires **zero changes to `:features`, `:data`, or other variants**.
> **Scope of changes:** one new variant module + one `settings.gradle.kts` line + one `VariantCatalogue` entry.
> **Worked example below:** onboarding **`variants-my`** (Malaysia).

This is the proof that the framework's architecture survives contact with growth. With the unified server-side API, variant modules are tiny — a few policy classes plus a Hilt binding module. If a step in this checklist starts requiring `:features` or `:data` edits, the framework has regressed — open an architecture issue.

---

## 1. Pre-Flight

Before writing code, gather:

| Artifact | Source | Why |
|---|---|---|
| Variant-specific business rules | Compliance / product | Drives the `*Policy` implementations |
| Server-side variant ID assignment | Backend team | Auth response must return this ID for users in this variant |
| Capability list (which features are on/off) | Product | Drives `VariantCapabilities` flags |
| Display metadata | Brand team | Display name, market code, default currency for `VariantContext` |

If any of these is unavailable, do not begin onboarding. Stub data leaks into production.

> **Note:** the server-side fintech API handles all backend routing, so no per-variant Retrofit interface, no per-variant DTOs, no per-variant repository — none of that lives on the Android side. The variant module is *just* policies + DI.

### 1.1 No default variant; scaffold by copying

There is **no default variant in production**. If the auth response does not include a recognized `variantId`, the app must reject the login — silent fallback to a default would risk routing the user through the wrong policies (wrong daily limit, wrong fee, wrong currency). Fail loud.

For **scaffolding** a new variant, copy the structure of an existing one (e.g. `:variants-kh`):

```bash
cp -R variants-kh variants-my
# In variants-my/, rename Kh* → My*, swap rule values, regex patterns, and translations.
# Then update settings.gradle.kts and the VariantCatalogue entry per Step 2 / Step 5.
```

The "boilerplate" is the **structural template** — the package layout (`policy/`, `format/`, `capability/`, `support/`, `di/`) and the Hilt module shape. The *content* (limits, fees, regex patterns, translations, currency symbol, support contacts) is what differs. For a side-by-side example of how much real content differs between two variants, see [07 — `:variants-*` § 5.9](07-variants.md).

---

## 2. The Onboarding Checklist

### Step 1 — Create the module

```bash
mkdir -p variants-my/src/main/kotlin/com/<org>/variants/my/{policy,format,capability,di}
```

Create `variants-my/build.gradle.kts` mirroring an existing variant (e.g., `variants-kh`). Required dependencies:

- `:core` (contracts)
- `:aos-core` (helpers, if needed)
- Hilt + KSP

> **Forbidden** in variant build files: dependencies on `:features`, `:features-chatbot`, `:data`, or sibling variant modules. CI must reject these.

### Step 2 — Register in `settings.gradle.kts`

Add **one line**:

```kotlin
include(":variants-my")
```

This is the only edit to the root build configuration.

### Step 3 — Implement variant policies

```kotlin
internal class MyTransferAmountPolicy : TransferAmountPolicy {
    override val dailyLimit: Money = Money(BigDecimal("50000.00"), Currency.MYR)
    override fun validate(amount: Money): ValidationResult = when {
        amount.value <= BigDecimal.ZERO -> ValidationResult.Invalid("Must be positive")
        amount > dailyLimit             -> ValidationResult.Invalid("Above daily limit")
        else                            -> ValidationResult.Valid
    }
}

internal class MyFeeCalculator : FeeCalculator {
    override fun quote(amount: Money, channel: TransferChannel): FeeQuote = when (channel) {
        TransferChannel.Internal -> FeeQuote.zero(amount.currency)
        TransferChannel.External -> FeeQuote(Money(amount.value * BigDecimal("0.003"), amount.currency))
    }
}

internal class MyrAmountFormatter : AmountFormatter {
    override fun format(amount: Money): String = "RM ${amount.value.setScale(2)}"
}

internal class MyCapabilities : VariantCapabilities {
    override fun supportsKhqrScan(): Boolean = false   // not applicable in MY
    override fun supportsCardlessAtm(): Boolean = true
    override fun supportsBilingualReceipt(): Boolean = false
}
```

This is where regulatory and product rules live. **Do not put these in `:features` or `:data`** — they're variant-specific.

### Step 4 — Wire the Hilt module

Each variant's bindings go into a multibindings map keyed by `@VariantKey("<id>")`. `:app/di/VariantResolverModule.kt` does the runtime lookup. See [07 — `:variants-*` § 6](07-variants.md) for the full mechanism and the resolver code.

```kotlin
// :variants-my/di/MyVariantModule.kt
@Module
@InstallIn(LoggedInComponent::class)
abstract class MyVariantModule {
    @Binds @IntoMap @VariantKey("my") @LoggedInScoped
    abstract fun amountPolicy(impl: MyTransferAmountPolicy): TransferAmountPolicy

    @Binds @IntoMap @VariantKey("my") @LoggedInScoped
    abstract fun feeCalc(impl: MyFeeCalculator): FeeCalculator

    @Binds @IntoMap @VariantKey("my") @LoggedInScoped
    abstract fun formatter(impl: MyrAmountFormatter): AmountFormatter

    @Binds @IntoMap @VariantKey("my") @LoggedInScoped
    abstract fun capabilities(impl: MyCapabilities): VariantCapabilities

    // … one @IntoMap binding per :core policy interface
}
```

`@InstallIn(LoggedInComponent::class)` plus `@IntoMap @VariantKey("my")` is what makes Hilt aggregate this variant's bindings into the `Map<String, T>` for each policy interface. **No edit to `:app` is required to register the module** — Hilt's annotation processor finds it automatically. **No edit to `VariantResolverModule` either** — the resolver looks up `variant.id.value` in the map, and the new variant's entries are present.

> **Important:** *every* variant's bindings compile into the same `LoggedInComponent::class`. At runtime, only the entries matching the logged-in user's `variantId` are exercised; the others sit inert in the maps. This keeps `:features` Logic-Blind and onboarding additive — the cost is a slightly larger APK.

### Step 5 — Register the variant in the catalogue

```kotlin
// :app/src/main/kotlin/com/<org>/app/variant/VariantCatalogue.kt
object VariantCatalogue {
    val all: List<VariantContext> = listOf(
        VariantContext(id = VariantId("kh"),      displayName = "PPC Bank Cambodia", marketCode = "KH", defaultCurrency = Currency.KHR),
        VariantContext(id = VariantId("vn"),      displayName = "PPC Bank Vietnam",   marketCode = "VN", defaultCurrency = Currency.VND),
        VariantContext(id = VariantId("ppcbank"), displayName = "PPCBank Legacy",     marketCode = "KH", defaultCurrency = Currency.USD),
        VariantContext(id = VariantId("my"),      displayName = "PPC Bank Malaysia",  marketCode = "MY", defaultCurrency = Currency.MYR),  // ← added
    )
}
```

This is the **one place** that must learn about the new variant. The catalogue feeds:

- `VariantContextResolver`, which looks up the right `VariantContext` after login by `variantId`.
- The debug overlay's variant indicator.

### Step 6 — Coordinate server-side

One backend concern out of the Android team's tree but on the onboarding checklist:

- **Auth response:** the auth backend must return `variantId = "my"` for users belonging to the new variant. Without this, login succeeds but `VariantCatalogue` lookup fails. The backend must also be ready to handle requests from those users without further client-side changes — that's the whole point of server-side demux.

### Step 7 — Add tests

Each new variant module ships with:

- **Unit tests** for each `*Policy` (golden cases for amount validation, fee calculation)
- **Unit tests** for capability flags (assert the boolean matrix is correct)
- **Integration test** for `:app` running with `variantId = "my"` injected (one happy-path transfer flow that exercises `MyTransferAmountPolicy` + the shared `:data` repos)

---

## 3. What You DO NOT Touch

The whole point. Verify after onboarding:

| Module | Diff line count expected |
|---|---|
| `:aos-core` | 0 |
| `:core` | 0 (the contracts are already sufficient) |
| `:data` | 0 (the API is unified server-side) |
| `:features` | **0** |
| `:features-chatbot` | 0 |
| Other `:variants-*` | 0 |
| `:app` | ~3 lines: `VariantCatalogue` entry only |
| `settings.gradle.kts` | 1 line: `include(":variants-my")` |

If a PR onboarding a variant has more than that, **review it** — something is leaking into the wrong layer.

---

## 4. Onboarding Verification Checklist

Run before merging:

```
[ ] :variants-my compiles standalone (./gradlew :variants-my:assembleDebug)
[ ] :features compiles without :variants-my as a dependency (verify build.gradle.kts)
[ ] :data compiles without :variants-my as a dependency
[ ] No reference to "variants_my" or "VariantId.MY" exists in :features or :data source sets
[ ] Logging in with a "my" user resolves MyTransferAmountPolicy as the active TransferAmountPolicy
    (verifiable via instrumentation test that injects variantId and asserts the bound class)
[ ] Logging out and logging in as a "kh" user resolves KhTransferAmountPolicy instead
    (no stale Malaysia state, no stale tokens)
[ ] Policy unit tests cover happy-path + at least one boundary case per method
[ ] Capability flags match the product spec
[ ] :app size delta (release APK) reasonable (typically <100 KB for a new variant — variants are small)
```

---

## 5. When Something Doesn't Fit

Some scenarios require thought:

### "The new variant has a feature no other variant has."

Three options, ordered by scope:

1. **It's a UI difference only** (a button, a label, a field) → use a `VariantCapabilities` flag in `:core`. Add a method, implement it in every variant (others return `false`), and gate the UI in `:features` on the flag. No new module.

   ```kotlin
   // :core/policy/VariantCapabilities.kt
   interface VariantCapabilities {
       fun supportsCardlessAtm(): Boolean
       fun supportsCorporateBulkTransfer(): Boolean   // ← new flag
       // …
   }
   ```

2. **It's a new flow built on existing APIs** (Compose + ViewModels, no new endpoints) → add a new package to `:features`, gate its visibility on the new capability flag. Stays in `:features`; no new module.

3. **It's a unique feature with its own API endpoints, DTOs, and screens** → create a dedicated `:features-{feature-name}` module (mirroring `:features-chatbot`). The module owns the variant-unique Retrofit interface, DTOs, repository, and Composables. `:app` conditionally includes its nav graph based on the capability flag. See [07 — `:variants-*` § "When the Variant Has Unique Features"](07-variants.md).

In all three options, the variant module (`:variants-{id}`) stays policies + DI only — it does not absorb feature code.

### "The new variant uses a totally different validation rule."

That's exactly what `*Policy` interfaces are for. Implement the policy with the new rule; no other module changes.

### "The new variant uses a different backend protocol."

This should not happen — the unified fintech API is the architectural assumption. If it does, escalate to backend before adding any `:variants-my`-only API code; that's a layering violation that defeats the design.

### "The new variant is staging-only."

Same module shape. Server-side gating ensures production users never receive the new variant's `variantId`. The Android side stays oblivious — it can't know, and shouldn't.

---

## 6. Cross-references

- The boot mechanism that activates the variant: [10 — Boot Phases](10-boot-phases.md)
- The contract interfaces being implemented: [03 — `:core`](03-core.md)
- The shared data layer that every variant uses: [05 — `:data`](05-data.md)
- The `Session` and account model: [12 — Departments and Session](12-departments-and-session.md)
- Build performance implications of new modules: [14 — Build Performance](14-build-performance.md)
