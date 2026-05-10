# 07 · `:variants-*` — Variant Silos

> **Type:** One Local Android library per variant (`:variants-kh`, `:variants-vn`, `:variants-ppcbank`, …)
> **Role:** Variant-specific **policies** (validation, fees, formatting, capabilities) + DI bindings.
> **Isolation guarantee:** No variant module may depend on another variant module.

---

## 1. Purpose

A variant module is a **silo** that holds *only what differs* between regions or companies. With a unified server-side API, the data layer is shared across variants — so a variant module is reduced to:

1. **Policy implementations** — `:core` policy interfaces (fee calc, amount validation, formatting, business rules)
2. **Capability flags** — implementations of `VariantCapabilities`
3. **DI bindings** — one Hilt module exposing those impls to `LoggedInComponent`

It does **not** own:

- UI (lives in `:features`)
- Retrofit interfaces or DTOs (the server demuxes; one `FintechApi` lives in `:data`)
- Repository implementations (also in `:data`)

A typical variant module is 5–15 small Kotlin files.

---

## 2. Standard Variant Module Shape

```
:variants-kh/
└── src/main/kotlin/com/<org>/variants/kh/
    ├── policy/
    │   ├── KhTransferAmountPolicy.kt   # implements TransferAmountPolicy
    │   ├── KhFeeCalculator.kt          # implements FeeCalculator
    │   ├── KhBeneficiaryValidator.kt   # implements BeneficiaryValidator
    │   ├── KhOtpDeliveryPolicy.kt      # implements OtpDeliveryPolicy
    │   ├── KhComplianceThresholds.kt   # implements ComplianceThresholds
    │   └── KhBusinessCalendar.kt       # implements BusinessCalendar
    ├── format/
    │   └── KhrAmountFormatter.kt       # implements AmountFormatter
    ├── capability/
    │   └── KhCapabilities.kt           # implements VariantCapabilities
    ├── support/
    │   └── KhSupportContacts.kt        # implements SupportContacts
    └── di/
        └── KhVariantModule.kt          # @Module @InstallIn(LoggedInComponent::class)
```

| Layer | Purpose | Visibility |
|---|---|---|
| `policy/` | Variant-specific business rules implementing `:core` policy interfaces | `internal` |
| `format/` | Locale/currency formatting helpers | `internal` |
| `capability/` | Boolean flags gating UI features | `internal` |
| `support/` | Customer-care contact info (per-region phone, email, hours) | `internal` |
| `di/` | The single public surface — Hilt bindings exposed to `:app` | `public` |

**Only the DI module should be visible outside the variant module.** Everything else is `internal`. This prevents `:app` from accidentally referencing a concrete policy class — it should only ever reference the `:core` interface.

---

## 3. The Variant Catalogue

| Module | Market | Distinctive responsibilities |
|---|---|---|
| `:variants-kh` | Cambodia | NBC compliance limits, KHR formatting, KHQR capability flag |
| `:variants-vn` | Vietnam | SBV-aligned daily limits, VND formatting, VietQR capability flag |
| `:variants-ppcbank` | PPCBank legacy | PPC-specific limits, dual-currency formatting |

Future variants land here as siblings: `:variants-my`, `:variants-th`, etc. See [13 — Onboarding a Variant](13-onboarding-a-variant.md).

---

## 4. Policy Implementation Pattern

Pure Kotlin classes implementing `:core` policy interfaces. Stateless where possible.

```kotlin
internal class KhTransferAmountPolicy : TransferAmountPolicy {
    override val dailyLimit: Money = Money(BigDecimal("4_000_000.00"), Currency.KHR)
    override fun validate(amount: Money): ValidationResult = when {
        amount.value <= BigDecimal.ZERO -> ValidationResult.Invalid("Must be positive")
        amount > dailyLimit             -> ValidationResult.Invalid("Above daily limit")
        else                            -> ValidationResult.Valid
    }
}

internal class KhFeeCalculator : FeeCalculator {
    override fun quote(amount: Money, channel: TransferChannel): FeeQuote =
        when (channel) {
            TransferChannel.Internal -> FeeQuote.zero(amount.currency)
            TransferChannel.External -> FeeQuote(Money(amount.value * BigDecimal("0.005"), amount.currency))
        }
}

internal class KhCapabilities : VariantCapabilities {
    override fun supportsKhqrScan(): Boolean = true
    override fun supportsCardlessAtm(): Boolean = false
    override fun supportsBilingualReceipt(): Boolean = true
}
```

No networking, no Android dependencies, easily JVM-unit-testable.

---

## 5. Common Variant Surfaces

Beyond `TransferAmountPolicy`, `FeeCalculator`, `AmountFormatter`, and `VariantCapabilities`, fintech variants typically also implement these `:core` interfaces. None of them require new infrastructure — they're added to `:core/policy/` as needed and implemented per variant.

### 5.1 `BeneficiaryValidator`

Account-number and phone-number formats vary by region. The validator hides the regex.

```kotlin
// :core/policy/BeneficiaryValidator.kt
interface BeneficiaryValidator {
    fun validateAccountNumber(account: String): ValidationResult
    fun validatePhoneNumber(phone: String): ValidationResult
}

// :variants-kh/policy/KhBeneficiaryValidator.kt
internal class KhBeneficiaryValidator : BeneficiaryValidator {
    private val accountRegex = Regex("""^\d{9}$""")        // 9-digit Cambodia account
    private val phoneRegex = Regex("""^(?:\+855|0)\d{8,9}$""")

    override fun validateAccountNumber(account: String) =
        if (accountRegex.matches(account)) ValidationResult.Valid
        else ValidationResult.Invalid("Account must be 9 digits")

    override fun validatePhoneNumber(phone: String) =
        if (phoneRegex.matches(phone)) ValidationResult.Valid
        else ValidationResult.Invalid("Use +855 or 0 prefix")
}
```

### 5.2 `OtpDeliveryPolicy`

Preferred OTP channel and timing differ per market and risk profile.

```kotlin
// :core/policy/OtpDeliveryPolicy.kt
enum class OtpChannel { Sms, Voice, InApp }

interface OtpDeliveryPolicy {
    val preferredChannel: OtpChannel
    val codeLength: Int
    val expirySeconds: Int
}

// :variants-kh/policy/KhOtpDeliveryPolicy.kt
internal class KhOtpDeliveryPolicy : OtpDeliveryPolicy {
    override val preferredChannel: OtpChannel = OtpChannel.Sms
    override val codeLength: Int = 6
    override val expirySeconds: Int = 300
}
```

### 5.3 `SupportContacts`

Customer-care contact info, surfaced in the help screen.

```kotlin
// :core/policy/SupportContacts.kt
interface SupportContacts {
    val customerCarePhone: String
    val customerCareEmail: String
    val customerCareHours: String  // e.g. "Mon–Fri, 8 AM – 8 PM ICT"
}

// :variants-kh/support/KhSupportContacts.kt
internal class KhSupportContacts : SupportContacts {
    override val customerCarePhone   = "+855 23 999 999"
    override val customerCareEmail   = "support.kh@compass.bank"
    override val customerCareHours   = "Mon–Fri, 8 AM – 8 PM (ICT)"
}
```

### 5.4 `ComplianceThresholds`

Per-region regulatory limits for transfer scrutiny. Surfaces appear in transfer review (extra confirmation step) and history (filter highlighting).

```kotlin
// :core/policy/ComplianceThresholds.kt
interface ComplianceThresholds {
    val largeTransferThreshold: Money       // requires re-auth before submit
    val crossBorderThreshold: Money         // requires extra disclosure
    val dailyAggregateLimit: Money
}

// :variants-kh/policy/KhComplianceThresholds.kt
internal class KhComplianceThresholds : ComplianceThresholds {
    override val largeTransferThreshold = Money(BigDecimal("4_000_000"), Currency.KHR)
    override val crossBorderThreshold   = Money(BigDecimal("4_000_000"), Currency.KHR)
    override val dailyAggregateLimit    = Money(BigDecimal("40_000_000"), Currency.KHR)
}
```

### 5.5 `BusinessCalendar`

Bank holidays and settlement cutoffs. The transfer review screen reads it to display "settles next business day" warnings.

```kotlin
// :core/policy/BusinessCalendar.kt
enum class SettlementWindow { SameDay, NextBusinessDay, TPlus2 }

interface BusinessCalendar {
    fun isBankHoliday(date: LocalDate): Boolean
    fun nextBusinessDay(date: LocalDate): LocalDate
    fun settlementWindow(channel: TransferChannel): SettlementWindow
}

// :variants-kh/policy/KhBusinessCalendar.kt
internal class KhBusinessCalendar : BusinessCalendar {
    private val nbcHolidays2026 = setOf(
        LocalDate.of(2026, 1, 1),   // New Year
        LocalDate.of(2026, 4, 14),  // Khmer New Year (representative)
        // …
    )

    override fun isBankHoliday(date: LocalDate) = date in nbcHolidays2026
    override fun nextBusinessDay(date: LocalDate): LocalDate { /* skip weekends + holidays */ }
    override fun settlementWindow(channel: TransferChannel) = when (channel) {
        TransferChannel.Internal -> SettlementWindow.SameDay
        TransferChannel.External -> SettlementWindow.NextBusinessDay
    }
}
```

### 5.6 `ReceiptRenderer` (when applicable)

Variants in bilingual markets often need receipts in both languages. The interface returns a structured rendered receipt that the UI then displays or shares.

```kotlin
// :core/policy/ReceiptRenderer.kt
interface ReceiptRenderer {
    fun render(receipt: TransferReceipt, primaryLanguage: String): RenderedReceipt
}

// :variants-kh/policy/KhReceiptRenderer.kt
internal class KhReceiptRenderer : ReceiptRenderer {
    override fun render(receipt: TransferReceipt, primaryLanguage: String): RenderedReceipt {
        // Bilingual KH + EN receipt with NBC-mandated fields
        return RenderedReceipt(/* ... */)
    }
}
```

### 5.7 Adding a new variant surface

When `:core/policy/` gains a new method (or a new policy interface is added), every existing variant must update. This is by design — the build fails until each variant explicitly answers what its behavior is. Avoid `default` fallbacks on policy interfaces; force the per-variant decision.

### 5.8 Worked test example

Because policies are pure Kotlin classes with no Android dependencies, unit tests run on the JVM with no fixtures.

```kotlin
// :variants-kh/src/test/kotlin/com/<org>/variants/kh/policy/KhTransferAmountPolicyTest.kt
class KhTransferAmountPolicyTest {

    private val policy = KhTransferAmountPolicy()

    @Test fun `zero amount is invalid`() {
        val result = policy.validate(Money(BigDecimal.ZERO, Currency.KHR))
        assertEquals(ValidationResult.Invalid("Must be positive"), result)
    }

    @Test fun `negative amount is invalid`() {
        val result = policy.validate(Money(BigDecimal("-1.00"), Currency.KHR))
        assertEquals(ValidationResult.Invalid("Must be positive"), result)
    }

    @Test fun `amount above daily limit is invalid`() {
        val result = policy.validate(Money(BigDecimal("4_000_001"), Currency.KHR))
        assertEquals(ValidationResult.Invalid("Above daily limit"), result)
    }

    @Test fun `amount at daily limit is valid`() {
        val result = policy.validate(Money(BigDecimal("4_000_000"), Currency.KHR))
        assertEquals(ValidationResult.Valid, result)
    }
}
```

Run with `./gradlew :variants-kh:test`. No Hilt, no Android runtime, no test fixtures.

### 5.9 Snapshot: how different are variants in practice?

To make the contract-vs-content split concrete, here are the same `:core` policy interfaces implemented for **KH (Cambodia)** and **VN (Vietnam)**. Same interfaces, different content — this is exactly the architecture's point.

**`TransferAmountPolicy`**

```kotlin
// :variants-kh
internal class KhTransferAmountPolicy : TransferAmountPolicy {
    override val dailyLimit = Money(BigDecimal("4_000_000"), Currency.KHR)        // 4M KHR ≈ $1000
    override fun validate(amount: Money) = when {
        amount.value <= BigDecimal.ZERO -> ValidationResult.Invalid("Must be positive")
        amount > dailyLimit             -> ValidationResult.Invalid("Above daily limit")
        else                            -> ValidationResult.Valid
    }
}

// :variants-vn
internal class VnTransferAmountPolicy : TransferAmountPolicy {
    override val dailyLimit = Money(BigDecimal("500_000_000"), Currency.VND)      // 500M VND ≈ $20,000
    override fun validate(amount: Money) = when {
        amount.value <= BigDecimal.ZERO         -> ValidationResult.Invalid("Số tiền phải > 0")
        amount.value < BigDecimal("10_000")     -> ValidationResult.Invalid("Tối thiểu 10,000 VND")
        amount > dailyLimit                     -> ValidationResult.Invalid("Vượt hạn mức ngày")
        else                                    -> ValidationResult.Valid
    }
}
```

VN has an extra minimum-amount rule (regulator-mandated). KH doesn't. Same interface, different rules.

**`FeeCalculator`**

```kotlin
// :variants-kh — flat percentage
internal class KhFeeCalculator : FeeCalculator {
    override fun quote(amount: Money, channel: TransferChannel) = when (channel) {
        TransferChannel.Internal -> FeeQuote.zero(amount.currency)
        TransferChannel.External -> FeeQuote(Money(amount.value * BigDecimal("0.005"), amount.currency))  // 0.5%
    }
}

// :variants-vn — tiered
internal class VnFeeCalculator : FeeCalculator {
    override fun quote(amount: Money, channel: TransferChannel) = when (channel) {
        TransferChannel.Internal -> FeeQuote.zero(amount.currency)
        TransferChannel.External -> {
            val fee = if (amount.value <= BigDecimal("500_000"))
                Money(BigDecimal("7_700"), amount.currency)                       // flat 7,700 VND for ≤500k
            else
                Money(amount.value * BigDecimal("0.0005"), amount.currency)       // 0.05% above
            FeeQuote(fee)
        }
    }
}
```

**`VariantCapabilities`**

```kotlin
// :variants-kh
internal class KhCapabilities : VariantCapabilities {
    override fun supportsKhqrScan()           = true       // Cambodia uses KHQR
    override fun supportsCardlessAtm()        = false
    override fun supportsBilingualReceipt()   = true       // KH + EN
}

// :variants-vn
internal class VnCapabilities : VariantCapabilities {
    override fun supportsKhqrScan()           = false      // VN uses VietQR (a different rail)
    override fun supportsCardlessAtm()        = true
    override fun supportsBilingualReceipt()   = false
}
```

The UI in `:features` reads these flags and renders accordingly — never branching on `variantId`.

**`BeneficiaryValidator`**

```kotlin
// :variants-kh
internal class KhBeneficiaryValidator : BeneficiaryValidator {
    private val accountRegex = Regex("""^\d{9}$""")
    private val phoneRegex   = Regex("""^(?:\+855|0)\d{8,9}$""")
    override fun validateAccountNumber(account: String) =
        if (accountRegex.matches(account)) ValidationResult.Valid
        else ValidationResult.Invalid("Account must be 9 digits")
    override fun validatePhoneNumber(phone: String) =
        if (phoneRegex.matches(phone)) ValidationResult.Valid
        else ValidationResult.Invalid("Use +855 or 0 prefix")
}

// :variants-vn
internal class VnBeneficiaryValidator : BeneficiaryValidator {
    private val accountRegex = Regex("""^\d{8,16}$""")                // VN bank accounts: 8–16 digits
    private val phoneRegex   = Regex("""^(?:\+84|0)\d{9,10}$""")
    override fun validateAccountNumber(account: String) =
        if (accountRegex.matches(account)) ValidationResult.Valid
        else ValidationResult.Invalid("Số tài khoản 8–16 chữ số")
    override fun validatePhoneNumber(phone: String) =
        if (phoneRegex.matches(phone)) ValidationResult.Valid
        else ValidationResult.Invalid("Dùng +84 hoặc 0 ở đầu")
}
```

**`AmountFormatter`**

```kotlin
// :variants-kh
internal class KhrAmountFormatter : AmountFormatter {
    override fun format(amount: Money): String =
        "%,.0f៛".format(amount.value)            // "1,234,567៛" — symbol suffix, no decimals
}

// :variants-vn
internal class VndAmountFormatter : AmountFormatter {
    override fun format(amount: Money): String =
        "%,.0f₫".format(amount.value)            // "1.234.567₫" — Vietnamese grouping
}
```

**`SupportContacts`**

```kotlin
// :variants-kh
internal class KhSupportContacts : SupportContacts {
    override val customerCarePhone = "+855 23 999 999"
    override val customerCareEmail = "support.kh@compass.bank"
    override val customerCareHours = "Mon–Fri, 8 AM – 8 PM (ICT)"
}

// :variants-vn
internal class VnSupportContacts : SupportContacts {
    override val customerCarePhone = "+84 24 7300 8000"
    override val customerCareEmail = "support.vn@compass.bank"
    override val customerCareHours = "Mon–Sat, 8 AM – 9 PM (ICT)"
}
```

**Total file count per variant:** ~10–12 small files, each implementing one interface. Most are 5–30 lines. The "boilerplate" is the **package layout** (`policy/`, `format/`, `capability/`, `support/`, `di/`) and the Hilt module shape — **copy from an existing variant when scaffolding** (see [13 — Onboarding a Variant § 1.1](13-onboarding-a-variant.md)).

---

## 6. The Hilt Binding Module

Each variant exposes **exactly one** Hilt module that contributes its policy implementations into `LoggedInComponent`. Because every variant binds the same set of `:core` policy interfaces, Dagger would error on duplicate bindings if we used plain `@Binds`. The fix: **Dagger multibindings** keyed on `VariantId`. Each binding goes into a `Map<String, T>`; a small resolver in `:app` looks up the active variant's entry at runtime.

### 6.1 The map key (defined in `:core`)

```kotlin
// :core/scope/VariantKey.kt
@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class VariantKey(val value: String)
```

Lives in `:core` so every variant module and `:app` can use it without cross-module imports.

### 6.2 Per-variant Hilt module

Each variant's bindings are added with `@IntoMap @VariantKey("<id>")`:

```kotlin
// :variants-kh/di/KhVariantModule.kt
@Module
@InstallIn(LoggedInComponent::class)
abstract class KhVariantModule {
    @Binds @IntoMap @VariantKey("kh") @LoggedInScoped
    abstract fun amountPolicy(impl: KhTransferAmountPolicy): TransferAmountPolicy

    @Binds @IntoMap @VariantKey("kh") @LoggedInScoped
    abstract fun feeCalc(impl: KhFeeCalculator): FeeCalculator

    @Binds @IntoMap @VariantKey("kh") @LoggedInScoped
    abstract fun amountFormatter(impl: KhrAmountFormatter): AmountFormatter

    @Binds @IntoMap @VariantKey("kh") @LoggedInScoped
    abstract fun capabilities(impl: KhCapabilities): VariantCapabilities

    @Binds @IntoMap @VariantKey("kh") @LoggedInScoped
    abstract fun beneficiaryValidator(impl: KhBeneficiaryValidator): BeneficiaryValidator

    @Binds @IntoMap @VariantKey("kh") @LoggedInScoped
    abstract fun otpDelivery(impl: KhOtpDeliveryPolicy): OtpDeliveryPolicy

    @Binds @IntoMap @VariantKey("kh") @LoggedInScoped
    abstract fun supportContacts(impl: KhSupportContacts): SupportContacts

    @Binds @IntoMap @VariantKey("kh") @LoggedInScoped
    abstract fun complianceThresholds(impl: KhComplianceThresholds): ComplianceThresholds

    @Binds @IntoMap @VariantKey("kh") @LoggedInScoped
    abstract fun businessCalendar(impl: KhBusinessCalendar): BusinessCalendar

    @Binds @IntoMap @VariantKey("kh") @LoggedInScoped
    abstract fun receiptRenderer(impl: KhReceiptRenderer): ReceiptRenderer
}
```

`:variants-vn/di/VnVariantModule.kt` follows the identical pattern with `@VariantKey("vn")`. Same for every variant.

### 6.3 The resolver in `:app`

`:app` provides one resolver per `:core` policy interface — each picks the active impl from the multibindings map by `VariantContext.id`:

```kotlin
// :app/di/VariantResolverModule.kt
@Module
@InstallIn(LoggedInComponent::class)
object VariantResolverModule {

    @Provides @LoggedInScoped
    fun transferAmountPolicy(
        variant: VariantContext,
        all: Map<String, @JvmSuppressWildcards TransferAmountPolicy>,
    ): TransferAmountPolicy = checkNotNull(all[variant.id.value]) {
        "No TransferAmountPolicy registered for variant ${variant.id}"
    }

    @Provides @LoggedInScoped
    fun feeCalculator(
        variant: VariantContext,
        all: Map<String, @JvmSuppressWildcards FeeCalculator>,
    ): FeeCalculator = checkNotNull(all[variant.id.value]) {
        "No FeeCalculator registered for variant ${variant.id}"
    }

    // … one provider per :core policy interface (mechanical; ~10 entries)
}
```

The map lookup is the **single point of dispatch** in the codebase — no `when (variantId)` branching in `:features`, `:data`, or any variant module. Adding a new variant doesn't change `VariantResolverModule`; Hilt automatically adds the new variant's `@IntoMap` entries to each map.

### 6.4 What this looks like at compile time

```
KhVariantModule:    @IntoMap @VariantKey("kh")   ──┐
VnVariantModule:    @IntoMap @VariantKey("vn")   ──┤  Map<String, TransferAmountPolicy>
PpcVariantModule:   @IntoMap @VariantKey("ppc")  ──┤   { "kh" → KhTransferAmountPolicy,
                                                       "vn" → VnTransferAmountPolicy,
                                                       "ppc" → PpcTransferAmountPolicy }
                                                          │
                                                          ↓
                                             VariantResolverModule (in :app)
                                             picks one by VariantContext.id.value
                                                          ↓
                                     :features ViewModels see ONE TransferAmountPolicy
                                     (the active variant's), via Hilt — Logic-Blind.
```

There is no compile error from "multiple bindings for `TransferAmountPolicy`" because each binding goes into the map under a unique key. Dagger generates the maps; the resolver picks; the consumer never knows.

> **Note on multiple variants in one APK:** every variant's Hilt module compiles into the binary. Only the entries matching the logged-in user's `variantId` are *exercised*; the others sit inert in the maps. This keeps `:features` Logic-Blind and onboarding additive. The cost is a slightly larger APK; the benefit is no per-variant `:app` change. See [13 — Onboarding](13-onboarding-a-variant.md).

---

## 7. Isolation Guarantee

The build graph enforces:

- ❌ `:variants-kh` cannot import `:variants-vn` (no shared symbols across variants)
- ❌ `:variants-*` cannot import `:features` (logic must not reach into UI)
- ❌ `:variants-*` cannot import `:data` (variants contribute policies, not data plumbing)
- ✅ `:variants-*` may import `:core` (contracts) and `:aos-core` (infrastructure helpers)

A bug in `KhFeeCalculator` cannot, by construction, reach `:variants-vn`. CI verifies this by failing any PR whose variant module declares a forbidden dependency.

---

## 8. End-to-end Example: A Transfer Submit

The path from user tap to network call shows where `:variants-*` plugs in:

```
[User taps Submit]
        │
        ▼
:features (UI)        TransferInputScreen → TransferInputViewModel
                          │
                          │  amountPolicy.validate(amount)        ── from :variants-{active}
                          │  feeCalculator.quote(amount, channel) ── from :variants-{active}
                          │  if (validationResult is Valid) →
                          │  transferRepo.submit(intent)
                          ▼
:core (interfaces)    TransferAmountPolicy, FeeCalculator, TransferRepository
                          │
                          │  (resolved by Hilt to active impls)
                          ▼
:variants-kh          KhTransferAmountPolicy, KhFeeCalculator
:data                 FintechTransferRepo : TransferRepository
                          │  api.submitTransfer(req).toDomain()
                          ▼
:aos-core (infra)     RetrofitFactory + BaseUrlInterceptor + AccountIdInterceptor
                          │  HTTP POST {RuntimeConfig.urls.main}/v1/transfer/submit
                          │       Authorization: Bearer ...
                          │       X-Account-Id: <session.activeAccountId>
                          ▼
                      [Unified fintech backend; demuxes to the right rail per user]
```

The ViewModel only knows interfaces. Hilt resolves:
- `KhTransferAmountPolicy` because the user's `variantId == "kh"` was bound into `LoggedInComponent` at login.
- `FintechTransferRepo` because `:data` is the only repo provider — same for every variant.

→ For the boot mechanics: [10 — Boot Phases](10-boot-phases.md).

---

## 9. When the Variant Has Unique Features

A variant module holds *only* policies + DI. If a variant introduces a feature that no other variant has — its own API endpoints, its own DTOs, its own screens — that feature does **not** go in `:variants-{id}`. It gets its own dedicated module, mirroring how `:features-chatbot` is structured.

### Why not just put the unique feature inside `:variants-{id}`?

Four reasons:

1. **The variant module is logic-only.** It depends on `:core` and `:aos-core` only. Adding UI would force a `:design-system` dependency; adding API code would require Retrofit + Moshi setup; adding a Compose screen would require Compose dependencies. The variant module's small, pure shape is what makes it predictable.
2. **Variant modules are symmetric.** Every variant has the same internal structure (`policy/`, `format/`, `capability/`, `support/`, `di/`). Unique features are **not** symmetric — only some variants have them. Mixing feature code into `:variants-kh` would make variants stop looking alike.
3. **Capability gating is cleaner with separate modules.** `:app` reads `capabilities.supportsBakongDisputes()` and conditionally includes the `:features-bakong-disputes` nav graph. The flag and the feature module are decoupled. Embedding the feature in the variant module weakens the gate (the code is loaded either way) and conflates "what variant am I" with "what features does this user see."
4. **Features migrate; variants don't.** If a feature is later supported by another variant (say, VN gets bilingual dispute support), the feature module just gains a new capability-flag setter from `:variants-vn`. No code moves between modules. Embedded in `:variants-kh`, you'd have to extract it first.

### Module shape

```
:features-bakong-disputes/                    (or :features-kh-bakong-disputes if locked to one variant)
└── src/main/kotlin/com/<org>/features/bakongdisputes/
    ├── api/
    │   ├── BakongDisputeApi.kt              # Retrofit, variant-unique endpoints
    │   └── dto/
    │       ├── DisputeRequest.kt
    │       └── DisputeResponse.kt
    ├── repo/
    │   └── BakongDisputeRepo.kt             # may or may not implement a :core interface
    ├── screen/
    │   ├── DisputeListScreen.kt
    │   ├── DisputeDetailScreen.kt
    │   └── DisputeContract.kt
    └── di/
        └── BakongDisputesModule.kt          # @InstallIn(LoggedInComponent::class)
```

Dependencies: `:core`, `:aos-core`, `:design-system` (for `CompassTheme`, `CompassButton`, etc.). Does **not** depend on `:features`.

### Capability gating

`:app`'s navigation conditionally includes the feature's nav graph based on a `VariantCapabilities` flag:

```kotlin
@Composable
fun AppNavigation(navController: NavHostController, capabilities: VariantCapabilities) {
    NavHost(navController, startDestination = Route.Boot) {
        bootNavGraph(navController)
        authNavGraph(navController)
        mainScaffoldNavGraph(navController)
        chatbotNavGraph(navController)
        if (capabilities.supportsBakongDisputes()) {
            bakongDisputesNavGraph(navController)
        }
    }
}
```

Each variant's `VariantCapabilities` impl returns `true` for the features it supports and `false` for the rest. Variant `kh` returns `true` for `supportsBakongDisputes()`; every other variant returns `false`. The feature's Hilt bindings still install (they're inert when unreachable), but no UI is wired in.

### Naming

| Choice | Use when |
|---|---|
| `:features-{feature-name}` | Default. Mirrors `:features-chatbot`. The feature *could* one day be supported by another variant. |
| `:features-{variant}-{feature-name}` | The feature is structurally locked to one variant (e.g., a regulator-mandated flow specific to that market). |

### When to extract a feature module vs. keep in `:features`

| Trigger | Action |
|---|---|
| Feature has unique heavy SDK dependencies | Extract (same rationale as `:features-chatbot`) |
| Feature has unique Retrofit endpoints + DTOs that other variants don't share | Extract |
| Feature differs only in policy/format/visibility | Keep in `:features`, gate via `VariantCapabilities` |
| Feature differs only in business rule (limit, fee) | Keep in `:features`, supply the rule via a `:core` policy interface |

The threshold mirrors the build-perf heuristic from [14](14-build-performance.md): if a variant-unique feature would slow incremental builds for everyone else, isolate it.

### Variants stay isolated from these feature modules

`:variants-kh` does **not** depend on `:features-bakong-disputes`. The connection is one-way:

- `:variants-kh` provides `KhCapabilities.supportsBakongDisputes() = true` via its `VariantCapabilities` impl.
- `:features-bakong-disputes` installs its own Hilt bindings into `LoggedInComponent`.
- `:app` reads the capability flag and conditionally wires the nav graph.

No cross-edge between `:variants-kh` and `:features-bakong-disputes`. They communicate through `:core` and `:app`.

---

## 10. What Does NOT Go In `:variants-*`

| ❌ Doesn't belong | ✅ Goes in |
|---|---|
| Compose UI | `:features` |
| Retrofit interfaces or DTOs | `:data` (and there's only one — `FintechApi`) |
| Repository implementations | `:data` |
| Domain models shared across variants | `:core` |
| `OkHttpClient` configuration | `:aos-core` |
| References to other variant modules | nowhere |
| MG endpoint URL | `:app` (build-time) |

If two variants need the same logic, **promote it to `:core`** (if it's a contract) or duplicate it deliberately (if it's an implementation detail that might diverge later). Premature consolidation across variants is how isolation erodes.

---

## 11. The `:features-chatbot` Sibling

`:features-chatbot` is **not a variant** — it's an isolated UI feature, sibling to `:features`. It exists because heavy SDKs (chat NLP, voice) penalize incremental builds of unrelated features. Its dependency rules:

- Depends on: `:core`, `:aos-core`, `:design-system`
- Does **not** depend on: `:features` (no cross-imports)
- Depended on by: `:app` (via navigation entry point)

Treat `:features-chatbot` as the prototype for **"isolate when SDK weight justifies it"**. New isolated features (e.g., `:features-kyc-livecheck`, `:features-card-3ds`) follow the same pattern.

---

## 12. Cross-references

- The interfaces variants implement: [03 — `:core`](03-core.md)
- The repos that handle the API side (variant-agnostic): [05 — `:data`](05-data.md)
- The shared design system that variant-unique features consume: [04 — `:design-system`](04-design-system.md)
- How `:app` wires variants at boot: [08 — `:app`](08-app-orchestrator.md)
- The `LoggedInComponent` mechanism: [10 — Boot Phases](10-boot-phases.md)
- Onboarding a new variant: [13 — Onboarding a Variant](13-onboarding-a-variant.md)
