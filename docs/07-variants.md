# 07 · `:variants-*` — Variant Silos

> **Type:** One Local Android library per variant (`:variants-kr`, `:variants-kh`, `:variants-vn`, …)
> **Role:** Variant-specific **policies** (validation, fees, formatting, capabilities, receipt rendering) + DI bindings + a `tenants/` subtree for per-corporate-customer profiles.
> **Isolation guarantee:** No variant module may depend on another variant module.

---

## 1. Purpose

A variant module is a **silo** that holds *only what differs* between regions or regulators. With a unified server-side IPPP API, the data layer is shared across variants — so a variant module is reduced to:

1. **Policy implementations** — `:core` policy interfaces (reimbursable-amount calc, expense validation, formatting, tax/VAT rules, receipt rendering)
2. **Capability flags** — implementations of `VariantCapabilities`
3. **Tenant profiles** — per-corporate-customer factories inside `tenants/{tenant-id}/` (see [19 — Tenants and Variants](19-tenants-and-variants.md))
4. **DI bindings** — one or two Hilt modules exposing the variant-level and tenant-level impls to `LoggedInComponent`

It does **not** own:

- UI (lives in `:features` or in a sibling `:features-{name}` module)
- Retrofit interfaces or DTOs (the server demuxes; the `Ippp*Api` family lives in `:data`)
- Repository implementations (also in `:data`)

A typical variant module is ~10–15 small Kotlin files at the region level, plus a handful per tenant.

---

## 2. Standard Variant Module Shape

```
:variants-kr/
└── src/main/kotlin/com/bizplay/variants/kr/
    ├── policy/
    │   ├── KrExpenseAmountPolicy.kt        # implements ExpenseAmountPolicy
    │   ├── KrFeeCalculator.kt              # implements FeeCalculator (corporate reimbursement rules)
    │   ├── KrEmployeeIdValidator.kt        # implements EmployeeIdValidator (default; tenants may override params)
    │   ├── KrOtpDeliveryPolicy.kt          # implements OtpDeliveryPolicy
    │   ├── KrApprovalThresholds.kt         # implements ApprovalThresholds
    │   ├── KrBusinessCalendar.kt           # implements BusinessCalendar
    │   └── KrReceiptRenderer.kt            # implements ReceiptRenderer (default KR shape)
    ├── format/
    │   └── KrwAmountFormatter.kt           # implements AmountFormatter
    ├── capability/
    │   └── KrCapabilities.kt               # implements VariantCapabilities
    ├── support/
    │   └── KrSupportContacts.kt            # implements SupportContacts
    ├── tenants/                            # per-corporate-customer profiles (see doc 19)
    │   ├── default/
    │   │   ├── DefaultKrTenantProfile.kt
    │   │   └── DefaultApprovalLineRenderer.kt
    │   ├── posco_ict/
    │   │   └── PoscoIctTenantProfile.kt
    │   ├── lotte/
    │   │   └── LotteTenantProfile.kt
    │   ├── nia/
    │   │   └── NiaTenantProfile.kt
    │   ├── shinsegae/
    │   │   ├── ShinsegaeTenantProfile.kt
    │   │   └── ShinsegaeApprovalLineRenderer.kt  # structural — different layout
    │   ├── itcen/
    │   │   └── ItcenTenantProfile.kt
    │   ├── wips/
    │   │   └── WipsTenantProfile.kt
    │   ├── hana/
    │   │   └── HanaTenantProfile.kt
    │   ├── ibs/
    │   │   └── IbsTenantProfile.kt
    │   └── spc/
    │       └── SpcTenantProfile.kt
    └── di/
        ├── KrVariantModule.kt              # @Module @InstallIn(LoggedInComponent::class) — variant bindings
        └── KrTenantModule.kt               # @Module @InstallIn(LoggedInComponent::class) — @TenantKey bindings
```

| Layer | Purpose | Visibility |
|---|---|---|
| `policy/` | Region-specific business rules implementing `:core` policy interfaces | `internal` |
| `format/` | Locale/currency formatting helpers | `internal` |
| `capability/` | Boolean flags gating UI features | `internal` |
| `support/` | Customer-care contact info (per-region phone, email, hours) | `internal` |
| `tenants/{id}/` | Per-corporate-customer profile factories + structural policy impls | `internal` |
| `di/` | The single public surface — Hilt bindings exposed to `:app` | `public` |

**Only the DI modules should be visible outside the variant module.** Everything else is `internal`. This prevents `:app` from accidentally referencing a concrete policy class — it should only ever reference the `:core` interface.

---

## 3. The Variant Catalogue

| Module | Market | Distinctive responsibilities |
|---|---|---|
| `:variants-kr` | Korea | KRW formatting, KR tax/VAT thresholds, KakaoPay link capability, Hi-Pass capability, MyData capability, Korean business calendar (national holidays + weekends), 10 tenant profiles |
| `:variants-kh` | Cambodia | KHR / USD dual-currency formatting, KH compliance thresholds, simpler approval shape, default tenant only |
| `:variants-vn` | Vietnam | VND formatting, SBV-aligned thresholds, default tenant only |

Future variants land here as siblings: `:variants-my`, `:variants-th`, etc. See [13 — Onboarding a Variant](13-onboarding-a-variant.md).

> **Why is Korea the heavyweight?** Because the existing Bizplay codebase's `DetailConfig.isXxx()` chain represents *Korean corporate customers* almost exclusively. The framework absorbs that complexity by treating each customer as a tenant *inside* `:variants-kr` — see § 5.9 and [19](19-tenants-and-variants.md).

---

## 4. Policy Implementation Pattern

Pure Kotlin classes implementing `:core` policy interfaces. Stateless where possible.

```kotlin
internal class KrExpenseAmountPolicy : ExpenseAmountPolicy {
    override val perReceiptLimit: Money = Money(BigDecimal("1_000_000"), Currency.KRW)
    override val dailyLimit: Money       = Money(BigDecimal("5_000_000"), Currency.KRW)

    override fun validate(amount: Money): ValidationResult = when {
        amount.value <= BigDecimal.ZERO     -> ValidationResult.Invalid("Must be positive")
        amount > perReceiptLimit            -> ValidationResult.Invalid("Above per-receipt limit")
        else                                -> ValidationResult.Valid
    }
}

internal class KrFeeCalculator : FeeCalculator {
    // KR corporate reimbursement: full reimbursement up to category cap, 0 above.
    override fun reimbursableAmount(amount: Money, category: ExpenseCategory): Money = when (category) {
        ExpenseCategory.Meal           -> amount.coerceAtMost(Money(BigDecimal("50_000"), amount.currency))
        ExpenseCategory.Taxi           -> amount       // full reimbursement, no cap
        ExpenseCategory.HighwayToll    -> amount
        ExpenseCategory.Fuel           -> amount.coerceAtMost(Money(BigDecimal("200_000"), amount.currency))
        ExpenseCategory.Entertainment  -> amount.coerceAtMost(Money(BigDecimal("100_000"), amount.currency))
        else                           -> amount
    }
}

internal class KrCapabilities : VariantCapabilities {
    override fun supportsKakaoPayLink()        = true
    override fun supportsHipassTracking()      = true       // Korea-only toll system
    override fun supportsMyDataIntegration()   = true       // Korea-only open banking
    override fun supportsBilingualReceipt()    = false      // Korean only by default
    override fun supportsOcrTicketScan()       = true
}
```

No networking, no Android dependencies, easily JVM-unit-testable.

---

## 5. Common Variant Surfaces

Beyond `ExpenseAmountPolicy`, `FeeCalculator`, `AmountFormatter`, and `VariantCapabilities`, every region typically also implements these `:core` interfaces. None of them require new infrastructure — they're added to `:core/policy/` as needed and implemented per variant.

### 5.1 `EmployeeIdValidator`

Employee-ID formats vary by region (and, inside Korea, *also* by tenant — which is where `TenantParams.employeeIdRegex` takes over; the variant-level validator is the fallback when no tenant param is supplied).

```kotlin
// :core/policy/EmployeeIdValidator.kt
interface EmployeeIdValidator {
    fun validate(employeeId: String): ValidationResult
}

// :variants-kr/policy/KrEmployeeIdValidator.kt
internal class KrEmployeeIdValidator @Inject constructor(
    private val tenant: TenantContext,
) : EmployeeIdValidator {

    private val regex: Regex = tenant.params.employeeIdRegex?.toRegex()
        ?: Regex("""^\d{7,8}$""")          // KR default: 7–8 digits

    override fun validate(employeeId: String): ValidationResult =
        if (regex.matches(employeeId)) ValidationResult.Valid
        else ValidationResult.Invalid("Invalid employee ID format")
}
```

### 5.2 `OtpDeliveryPolicy`

Preferred OTP channel and timing differ per market and risk profile. (Korean banking apps additionally route through TransKey's secure keypad on entry — see `:aos-core/security/SecureKeypad` and `:design-system`'s `BizPasswordField`.)

```kotlin
// :core/policy/OtpDeliveryPolicy.kt
enum class OtpChannel { Sms, Voice, InApp, Push }

interface OtpDeliveryPolicy {
    val preferredChannel: OtpChannel
    val codeLength: Int
    val expirySeconds: Int
}

// :variants-kr/policy/KrOtpDeliveryPolicy.kt
internal class KrOtpDeliveryPolicy : OtpDeliveryPolicy {
    override val preferredChannel: OtpChannel = OtpChannel.Sms
    override val codeLength: Int              = 6
    override val expirySeconds: Int           = 180
}
```

### 5.3 `SupportContacts`

Customer-care contact info, surfaced in the help screen. (Per-tenant overrides come through `TenantParams.supportPhoneOverride` — if a tenant supplies one, the help screen prefers it over the variant default.)

```kotlin
// :core/policy/SupportContacts.kt
interface SupportContacts {
    val customerCarePhone: String
    val customerCareEmail: String
    val customerCareHours: String
}

// :variants-kr/support/KrSupportContacts.kt
internal class KrSupportContacts : SupportContacts {
    override val customerCarePhone = "+82 2 1588 9999"
    override val customerCareEmail = "support.kr@bizplay.co.kr"
    override val customerCareHours = "Mon–Fri, 9 AM – 6 PM (KST)"
}
```

### 5.4 `ApprovalThresholds`

Per-region scrutiny rules: amounts above the threshold require extra confirmation (re-auth, additional approver). Surfaces appear in the approval-request review screen.

```kotlin
// :core/policy/ApprovalThresholds.kt
interface ApprovalThresholds {
    val reAuthThreshold: Money              // require password / OTP re-entry above this
    val seniorApproverThreshold: Money      // require a senior approver in the line
    val attachmentRequiredThreshold: Money  // require a photo / document attached
}

// :variants-kr/policy/KrApprovalThresholds.kt
internal class KrApprovalThresholds : ApprovalThresholds {
    override val reAuthThreshold              = Money(BigDecimal("500_000"), Currency.KRW)
    override val seniorApproverThreshold      = Money(BigDecimal("3_000_000"), Currency.KRW)
    override val attachmentRequiredThreshold  = Money(BigDecimal("50_000"), Currency.KRW)
}
```

### 5.5 `BusinessCalendar`

Korean national holidays + weekends drive expense-submission cutoffs (e.g. "submit by end of month, *next business day* if month-end falls on a holiday").

```kotlin
// :core/policy/BusinessCalendar.kt
interface BusinessCalendar {
    fun isHoliday(date: LocalDate): Boolean
    fun nextBusinessDay(date: LocalDate): LocalDate
    fun monthEndSubmissionDeadline(month: YearMonth): LocalDate
}

// :variants-kr/policy/KrBusinessCalendar.kt
internal class KrBusinessCalendar : BusinessCalendar {
    private val krHolidays2026 = setOf(
        LocalDate.of(2026, 1, 1),    // New Year
        LocalDate.of(2026, 2, 16),   // Seollal (representative)
        LocalDate.of(2026, 3, 1),    // Independence Movement Day
        LocalDate.of(2026, 5, 5),    // Children's Day
        // …
    )

    override fun isHoliday(date: LocalDate) = date in krHolidays2026
    override fun nextBusinessDay(date: LocalDate): LocalDate { /* skip weekends + holidays */ }
    override fun monthEndSubmissionDeadline(month: YearMonth): LocalDate { /* … */ }
}
```

### 5.6 `ReceiptRenderer`

Receipts are the variant difference most users actually see. Each region mandates a different set of fields and a different consumer disclosure — but the input (a finalised receipt) is identical everywhere. The renderer converts a variant-agnostic `Receipt` into a variant-shaped `RenderedReceipt` that the UI walks line by line.

```kotlin
// :core/policy/ReceiptRenderer.kt
interface ReceiptRenderer {
    fun render(receipt: Receipt, primaryLanguage: String): RenderedReceipt
}

// :core/model/Receipt.kt — same shape for every variant
data class Receipt(
    val id: ReceiptId,
    val amount: Money,
    val vatAmount: Money?,
    val category: ExpenseCategory,
    val payerName: String,
    val payerEmployeeId: String?,
    val merchantName: String,
    val merchantBizRegNo: String?,             // Korean business registration number (사업자등록번호)
    val purchasedAt: Instant,
    val approvalNumber: String?,
    val cardLast4: String?,
    val photos: List<PhotoRef>,
)

// :core/model/RenderedReceipt.kt — variant-shaped output
data class RenderedReceipt(
    val title: String,
    val lines: List<ReceiptLine>,
    val footer: String?,
    val regulatoryDisclosure: String?,
)

data class ReceiptLine(val label: String, val value: String)
```

The output is intentionally a flat list of label/value rows plus an optional disclosure. The UI cannot branch on field meaning because the rendered shape carries no semantics beyond "show these in order" — which is exactly what keeps `:features` Logic-Blind.

**KR — Korean primary, VAT-aware, Korean tax-receipt disclosure**

```kotlin
// :variants-kr/policy/KrReceiptRenderer.kt
internal class KrReceiptRenderer @Inject constructor(
    private val formatter: AmountFormatter,         // resolves to KrwAmountFormatter
) : ReceiptRenderer {

    override fun render(r: Receipt, primaryLanguage: String): RenderedReceipt {
        val s = if (primaryLanguage == "ko") KoStrings else EnStrings
        val lines = buildList {
            add(ReceiptLine(s.amount,    formatter.format(r.amount)))
            r.vatAmount?.let { add(ReceiptLine(s.vat, formatter.format(it))) }
            add(ReceiptLine(s.category,  s.categoryLabel(r.category)))
            add(ReceiptLine(s.merchant,  r.merchantName))
            r.merchantBizRegNo?.let { add(ReceiptLine(s.bizRegNo, it)) }
            add(ReceiptLine(s.payer,     r.payerName))
            add(ReceiptLine(s.purchased, r.purchasedAt.format(KrDateTimeFormatter)))
            r.cardLast4?.let { add(ReceiptLine(s.card, "**** ${it}")) }
            r.approvalNumber?.let { add(ReceiptLine(s.approvalNumber, it)) }
        }
        return RenderedReceipt(
            title = s.title,
            lines = lines,
            footer = s.thankYou,
            regulatoryDisclosure = s.taxReceiptNote,    // "본 영수증은 부가세법 시행령에 따라 …"
        )
    }

    private object KoStrings { /* "금액", "부가세", "사업자번호", … */ }
    private object EnStrings { /* "Amount", "VAT", "Business Reg. No.", … */ }
}
```

**KH — single-language English, simpler shape, no VAT row**

```kotlin
// :variants-kh/policy/KhReceiptRenderer.kt
internal class KhReceiptRenderer @Inject constructor(
    private val formatter: AmountFormatter,         // resolves to KhrAmountFormatter
) : ReceiptRenderer {

    override fun render(r: Receipt, primaryLanguage: String): RenderedReceipt {
        val lines = listOf(
            ReceiptLine("Amount",     formatter.format(r.amount)),
            ReceiptLine("Category",   r.category.name),
            ReceiptLine("Merchant",   r.merchantName),
            ReceiptLine("Payer",      r.payerName),
            ReceiptLine("Purchased",  r.purchasedAt.format(KhDateTimeFormatter)),
        )
        return RenderedReceipt(
            title = "Receipt",
            lines = lines,
            footer = "Thank you for using Bizplay.",
            regulatoryDisclosure = null,        // KH has no equivalent statutory note
        )
    }
}
```

What differs between the two implementations:

- **Languages** — KR swaps an entire string table at runtime (Korean ↔ English); KH is single-language.
- **Line set** — KR inserts a `VAT` row and a `Business Reg. No.` row that don't exist on KH receipts (Korean tax-receipt format mandates them).
- **Internal dependencies** — KR injects `formatter` (a `KrwAmountFormatter`); KH injects its own `KhrAmountFormatter`. Both are `internal` to their variant module.
- **Disclosure text** — different region, different statute, different language.

What stays identical: the interface, the `Receipt` input, the `RenderedReceipt` output shape, and the Hilt binding pattern in § 6.2.

**The UI consumer — same file for every variant**

```kotlin
// :features/receipt/detail/ReceiptDetailScreen.kt
@Composable
fun ReceiptDetailScreen(vm: ReceiptDetailViewModel = hiltViewModel()) {
    val rendered = vm.state.collectAsState().value.rendered ?: return
    Column {
        BizReceiptHeader(rendered.title)
        rendered.lines.forEach { BizReceiptRow(it.label, it.value) }
        rendered.footer?.let { BizReceiptFooter(it) }
        rendered.regulatoryDisclosure?.let { BizDisclosure(it) }
    }
}
```

The screen walks the rendered list. It doesn't know — and cannot know — which fields the active variant produced or whether the disclosure cites Korean tax law or nothing at all. Onboard a new region and this file does not change.

### 5.7 Adding a new variant surface

When `:core/policy/` gains a new method (or a new policy interface is added), every existing variant must update. This is by design — the build fails until each variant explicitly answers what its behavior is. Avoid `default` fallbacks on policy interfaces; force the per-variant decision.

### 5.8 Worked test example

Because policies are pure Kotlin classes with no Android dependencies, unit tests run on the JVM with no fixtures.

```kotlin
// :variants-kr/src/test/kotlin/com/bizplay/variants/kr/policy/KrExpenseAmountPolicyTest.kt
class KrExpenseAmountPolicyTest {

    private val policy = KrExpenseAmountPolicy()

    @Test fun `zero amount is invalid`() {
        val result = policy.validate(Money(BigDecimal.ZERO, Currency.KRW))
        assertEquals(ValidationResult.Invalid("Must be positive"), result)
    }

    @Test fun `negative amount is invalid`() {
        val result = policy.validate(Money(BigDecimal("-1"), Currency.KRW))
        assertEquals(ValidationResult.Invalid("Must be positive"), result)
    }

    @Test fun `amount above per-receipt limit is invalid`() {
        val result = policy.validate(Money(BigDecimal("1_000_001"), Currency.KRW))
        assertEquals(ValidationResult.Invalid("Above per-receipt limit"), result)
    }

    @Test fun `amount at per-receipt limit is valid`() {
        val result = policy.validate(Money(BigDecimal("1_000_000"), Currency.KRW))
        assertEquals(ValidationResult.Valid, result)
    }
}
```

Run with `./gradlew :variants-kr:test`. No Hilt, no Android runtime, no test fixtures.

### 5.9 Snapshot: how different are variants in practice?

To make the contract-vs-content split concrete, here are the same `:core` policy interfaces implemented for **KR (Korea)** and **KH (Cambodia)**. Same interfaces, different content — this is exactly the architecture's point.

**`ExpenseAmountPolicy`**

```kotlin
// :variants-kr
internal class KrExpenseAmountPolicy : ExpenseAmountPolicy {
    override val perReceiptLimit = Money(BigDecimal("1_000_000"), Currency.KRW)    // 1M KRW ≈ $750
    override val dailyLimit      = Money(BigDecimal("5_000_000"), Currency.KRW)
    override fun validate(amount: Money) = when {
        amount.value <= BigDecimal.ZERO -> ValidationResult.Invalid("Must be positive")
        amount > perReceiptLimit        -> ValidationResult.Invalid("Above per-receipt limit")
        else                            -> ValidationResult.Valid
    }
}

// :variants-kh
internal class KhExpenseAmountPolicy : ExpenseAmountPolicy {
    override val perReceiptLimit = Money(BigDecimal("4_000_000"), Currency.KHR)    // 4M KHR ≈ $1,000
    override val dailyLimit      = Money(BigDecimal("20_000_000"), Currency.KHR)
    override fun validate(amount: Money) = when {
        amount.value <= BigDecimal.ZERO  -> ValidationResult.Invalid("Must be positive")
        amount > perReceiptLimit         -> ValidationResult.Invalid("Above per-receipt limit")
        else                             -> ValidationResult.Valid
    }
}
```

Different currencies, different limits. Same interface, different content.

**`FeeCalculator` (corporate reimbursement caps per category)**

```kotlin
// :variants-kr — per-category caps reflecting Korean corporate norms
internal class KrFeeCalculator : FeeCalculator {
    override fun reimbursableAmount(amount: Money, category: ExpenseCategory) = when (category) {
        ExpenseCategory.Meal          -> amount.coerceAtMost(Money(BigDecimal("50_000"), amount.currency))
        ExpenseCategory.Taxi          -> amount
        ExpenseCategory.HighwayToll   -> amount
        ExpenseCategory.Fuel          -> amount.coerceAtMost(Money(BigDecimal("200_000"), amount.currency))
        ExpenseCategory.Entertainment -> amount.coerceAtMost(Money(BigDecimal("100_000"), amount.currency))
        else                          -> amount
    }
}

// :variants-kh — flat full reimbursement, no per-category cap (simpler market)
internal class KhFeeCalculator : FeeCalculator {
    override fun reimbursableAmount(amount: Money, category: ExpenseCategory) = amount
}
```

**`VariantCapabilities`**

```kotlin
// :variants-kr
internal class KrCapabilities : VariantCapabilities {
    override fun supportsKakaoPayLink()        = true
    override fun supportsHipassTracking()      = true
    override fun supportsMyDataIntegration()   = true
    override fun supportsBilingualReceipt()    = false
    override fun supportsOcrTicketScan()       = true
}

// :variants-kh
internal class KhCapabilities : VariantCapabilities {
    override fun supportsKakaoPayLink()        = false     // KakaoPay is Korea-only
    override fun supportsHipassTracking()      = false     // Hi-Pass is Korea-only
    override fun supportsMyDataIntegration()   = false
    override fun supportsBilingualReceipt()    = true      // English + Khmer
    override fun supportsOcrTicketScan()       = true
}
```

The UI in `:features` reads these flags and renders accordingly — never branching on `variantId`.

**`AmountFormatter`**

```kotlin
// :variants-kr
internal class KrwAmountFormatter : AmountFormatter {
    override fun format(amount: Money): String =
        "₩%,.0f".format(amount.value)            // "₩1,234,567" — no decimals
}

// :variants-kh
internal class KhrAmountFormatter : AmountFormatter {
    override fun format(amount: Money): String =
        "%,.0f៛".format(amount.value)            // "1,234,567៛" — symbol suffix, no decimals
}
```

**`SupportContacts`**

```kotlin
// :variants-kr
internal class KrSupportContacts : SupportContacts {
    override val customerCarePhone = "+82 2 1588 9999"
    override val customerCareEmail = "support.kr@bizplay.co.kr"
    override val customerCareHours = "Mon–Fri, 9 AM – 6 PM (KST)"
}

// :variants-kh
internal class KhSupportContacts : SupportContacts {
    override val customerCarePhone = "+855 23 999 999"
    override val customerCareEmail = "support.kh@bizplay.co.kr"
    override val customerCareHours = "Mon–Fri, 8 AM – 8 PM (ICT)"
}
```

**Total file count per variant (region level):** ~10–12 small files, each implementing one interface. Most are 5–30 lines. Tenant profiles (Korean POSCO / Lotte / NIA / …) add ~1–3 files each inside `tenants/{id}/`. The "boilerplate" is the **package layout** (`policy/`, `format/`, `capability/`, `support/`, `tenants/`, `di/`) and the Hilt module shape — **copy from an existing variant when scaffolding** (see [13 — Onboarding a Variant § 1.1](13-onboarding-a-variant.md)).

---

## 6. The Hilt Binding Module

Each variant exposes **exactly one** Hilt module that contributes its policy implementations into `LoggedInComponent` (plus an optional second module for `@TenantKey` bindings — see [19](19-tenants-and-variants.md)). Because every variant binds the same set of `:core` policy interfaces, Dagger would error on duplicate bindings if we used plain `@Binds`. The fix: **Dagger multibindings** keyed on `VariantId`. Each binding goes into a `Map<String, T>`; a small resolver in `:app` looks up the active variant's entry at runtime.

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
// :variants-kr/di/KrVariantModule.kt
@Module
@InstallIn(LoggedInComponent::class)
abstract class KrVariantModule {
    @Binds @IntoMap @VariantKey("kr") @LoggedInScoped
    abstract fun amountPolicy(impl: KrExpenseAmountPolicy): ExpenseAmountPolicy

    @Binds @IntoMap @VariantKey("kr") @LoggedInScoped
    abstract fun feeCalc(impl: KrFeeCalculator): FeeCalculator

    @Binds @IntoMap @VariantKey("kr") @LoggedInScoped
    abstract fun amountFormatter(impl: KrwAmountFormatter): AmountFormatter

    @Binds @IntoMap @VariantKey("kr") @LoggedInScoped
    abstract fun capabilities(impl: KrCapabilities): VariantCapabilities

    @Binds @IntoMap @VariantKey("kr") @LoggedInScoped
    abstract fun employeeIdValidator(impl: KrEmployeeIdValidator): EmployeeIdValidator

    @Binds @IntoMap @VariantKey("kr") @LoggedInScoped
    abstract fun otpDelivery(impl: KrOtpDeliveryPolicy): OtpDeliveryPolicy

    @Binds @IntoMap @VariantKey("kr") @LoggedInScoped
    abstract fun supportContacts(impl: KrSupportContacts): SupportContacts

    @Binds @IntoMap @VariantKey("kr") @LoggedInScoped
    abstract fun approvalThresholds(impl: KrApprovalThresholds): ApprovalThresholds

    @Binds @IntoMap @VariantKey("kr") @LoggedInScoped
    abstract fun businessCalendar(impl: KrBusinessCalendar): BusinessCalendar

    @Binds @IntoMap @VariantKey("kr") @LoggedInScoped
    abstract fun receiptRenderer(impl: KrReceiptRenderer): ReceiptRenderer
}
```

`:variants-kh/di/KhVariantModule.kt` follows the identical pattern with `@VariantKey("kh")`. Same for every variant.

### 6.3 The resolver in `:app`

`:app` provides one resolver per `:core` policy interface — each picks the active impl from the multibindings map by `VariantContext.id`:

```kotlin
// :app/di/VariantResolverModule.kt
@Module
@InstallIn(LoggedInComponent::class)
object VariantResolverModule {

    @Provides @LoggedInScoped
    fun expenseAmountPolicy(
        variant: VariantContext,
        all: Map<String, @JvmSuppressWildcards ExpenseAmountPolicy>,
    ): ExpenseAmountPolicy = checkNotNull(all[variant.id.value]) {
        "No ExpenseAmountPolicy registered for variant ${variant.id}"
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
KrVariantModule:   @IntoMap @VariantKey("kr")  ──┐
KhVariantModule:   @IntoMap @VariantKey("kh")  ──┤  Map<String, ExpenseAmountPolicy>
VnVariantModule:   @IntoMap @VariantKey("vn")  ──┤   { "kr" → KrExpenseAmountPolicy,
                                                      "kh" → KhExpenseAmountPolicy,
                                                      "vn" → VnExpenseAmountPolicy }
                                                         │
                                                         ↓
                                            VariantResolverModule (in :app)
                                            picks one by VariantContext.id.value
                                                         ↓
                                    :features ViewModels see ONE ExpenseAmountPolicy
                                    (the active variant's), via Hilt — Logic-Blind.
```

There is no compile error from "multiple bindings for `ExpenseAmountPolicy`" because each binding goes into the map under a unique key. Dagger generates the maps; the resolver picks; the consumer never knows.

> **Note on multiple variants in one APK:** every variant's Hilt module compiles into the binary. Only the entries matching the logged-in user's `variantId` are *exercised*; the others sit inert in the maps. This keeps `:features` Logic-Blind and onboarding additive. The cost is a slightly larger APK; the benefit is no per-variant `:app` change. See [13 — Onboarding](13-onboarding-a-variant.md).

---

## 7. Isolation Guarantee

The build graph enforces:

- ❌ `:variants-kr` cannot import `:variants-kh` (no shared symbols across variants)
- ❌ `:variants-*` cannot import `:features` (logic must not reach into UI)
- ❌ `:variants-*` cannot import `:data` (variants contribute policies, not data plumbing)
- ✅ `:variants-*` may import `:core` (contracts) and `:aos-core` (infrastructure helpers)

A bug in `KrFeeCalculator` cannot, by construction, reach `:variants-kh`. CI verifies this by failing any PR whose variant module declares a forbidden dependency.

---

## 8. End-to-end Example: A Receipt Submission

The path from user tap to network call shows where `:variants-*` plugs in:

```
[User taps Submit on the receipt detail screen]
        │
        ▼
:features (UI)        ReceiptDetailScreen → ReceiptDetailViewModel
                          │
                          │  amountPolicy.validate(amount)               ── from :variants-{active}
                          │  feeCalculator.reimbursableAmount(amount, category) ── from :variants-{active}
                          │  if (validationResult is Valid) →
                          │  receiptRepo.create(draft)
                          ▼
:core (interfaces)    ExpenseAmountPolicy, FeeCalculator, ReceiptRepository
                          │
                          │  (resolved by Hilt to active impls)
                          ▼
:variants-kr          KrExpenseAmountPolicy, KrFeeCalculator
:data                 IpppReceiptRepo : ReceiptRepository
                          │  api.create(req).toDomain()
                          ▼
:aos-core (infra)     RetrofitFactory + BaseUrlInterceptor + AccountIdInterceptor
                          │  HTTP POST {RuntimeConfig.urls.main}/v1/receipt
                          │       Authorization: Bearer ...
                          │       X-Use-Intt-Id: <session.activeAccountId>
                          │       X-Company-Cd: <active account's companyCode>
                          ▼
                      [Unified IPPP backend; demuxes per company / per tenant config]
```

The ViewModel only knows interfaces. Hilt resolves:
- `KrExpenseAmountPolicy` because the user's `variantId == "kr"` was bound into `LoggedInComponent` at login.
- `IpppReceiptRepo` because `:data` is the only repo provider — same for every variant.

→ For the boot mechanics: [10 — Boot Phases](10-boot-phases.md).

---

## 9. When the Variant Has Unique Features

A variant module holds *only* policies + DI + tenant profiles. If a variant introduces a feature that no other variant has — its own API endpoints, its own DTOs, its own screens — that feature does **not** go in `:variants-{id}`. It gets its own dedicated module, mirroring how `:features-scanner` is structured.

### Why not just put the unique feature inside `:variants-{id}`?

Four reasons:

1. **The variant module is logic-only.** It depends on `:core` and `:aos-core` only. Adding UI would force a `:design-system` dependency; adding API code would require Retrofit + Moshi setup; adding a Compose screen would require Compose dependencies. The variant module's small, pure shape is what makes it predictable.
2. **Variant modules are symmetric.** Every variant has the same internal structure (`policy/`, `format/`, `capability/`, `support/`, `tenants/`, `di/`). Unique features are **not** symmetric — only some variants have them. Mixing feature code into `:variants-kr` would make variants stop looking alike.
3. **Capability gating is cleaner with separate modules.** `:app` reads `capabilities.supportsHipassTracking()` and conditionally includes the `:features-hipass` nav graph. The flag and the feature module are decoupled. Embedding the feature in the variant module weakens the gate (the code is loaded either way) and conflates "what variant am I" with "what features does this user see."
4. **Features migrate; variants don't.** If a feature is later supported by another variant (say, Vietnam introduces a regional toll system), the feature module just gains a new capability-flag setter from `:variants-vn`. No code moves between modules. Embedded in `:variants-kr`, you'd have to extract it first.

### Module shape

```
:features-hipass/                             (Korea-only highway-toll capture; today's ui/receipt/hi_pass/)
└── src/main/kotlin/com/bizplay/features/hipass/
    ├── api/
    │   ├── HipassApi.kt                     # Retrofit, KR-only Hi-Pass endpoints
    │   └── dto/
    │       ├── HipassUsageRequest.kt
    │       └── HipassUsageResponse.kt
    ├── repo/
    │   └── HipassRepo.kt                    # internal; may implement a :core interface, may not
    ├── screen/
    │   ├── HipassListScreen.kt
    │   ├── HipassDetailScreen.kt
    │   └── HipassContract.kt
    └── di/
        └── HipassModule.kt                  # @InstallIn(LoggedInComponent::class)
```

Dependencies: `:core`, `:aos-core`, `:design-system` (for `BizTheme`, `BizButton`, etc.). Does **not** depend on `:features`.

### Capability gating

`:app`'s navigation conditionally includes the feature's nav graph based on a `VariantCapabilities` flag:

```kotlin
@Composable
fun AppNavigation(navController: NavHostController, capabilities: VariantCapabilities) {
    NavHost(navController, startDestination = Route.Boot) {
        bootNavGraph(navController)
        authNavGraph(navController)
        mainScaffoldNavGraph(navController)
        scannerNavGraph(navController)              // always available (camera, OCR, card-scan)
        if (capabilities.supportsHipassTracking()) {
            hipassNavGraph(navController)           // :features-hipass — KR only
        }
    }
}
```

Each variant's `VariantCapabilities` impl returns `true` for the features it supports and `false` for the rest. `:variants-kr` returns `true` for `supportsHipassTracking()`; every other variant returns `false`. The feature's Hilt bindings still install (they're inert when unreachable), but no UI is wired in.

### Naming

| Choice | Use when |
|---|---|
| `:features-{feature-name}` | Default. Mirrors `:features-scanner`. The feature *could* one day be supported by another variant. |
| `:features-{variant}-{feature-name}` | The feature is structurally locked to one variant (e.g., a regulator-mandated flow specific to that market). |

### When to extract a feature module vs. keep in `:features`

| Trigger | Action |
|---|---|
| Feature has unique heavy SDK dependencies (camera, OCR, scraping) | Extract (same rationale as `:features-scanner`) |
| Feature has unique Retrofit endpoints + DTOs that other variants don't share (Hi-Pass, MyData) | Extract |
| Feature differs only in policy/format/visibility | Keep in `:features`, gate via `VariantCapabilities` or `TenantFlags` |
| Feature differs only in business rule (limit, fee, regex) | Keep in `:features`, supply the rule via a `:core` policy interface |

The threshold mirrors the build-perf heuristic from [14](14-build-performance.md): if a variant-unique feature would slow incremental builds for everyone else, isolate it.

### Variants stay isolated from these feature modules

`:variants-kr` does **not** depend on `:features-hipass`. The connection is one-way:

- `:variants-kr` provides `KrCapabilities.supportsHipassTracking() = true` via its `VariantCapabilities` impl.
- `:features-hipass` installs its own Hilt bindings into `LoggedInComponent`.
- `:app` reads the capability flag and conditionally wires the nav graph.

No cross-edge between `:variants-kr` and `:features-hipass`. They communicate through `:core` and `:app`.

---

## 10. What Does NOT Go In `:variants-*`

| ❌ Doesn't belong | ✅ Goes in |
|---|---|
| Compose UI | `:features` |
| Retrofit interfaces or DTOs | `:data` (and the surface is the `Ippp*Api` family — variant-agnostic) |
| Repository implementations | `:data` |
| Domain models shared across variants | `:core` |
| `OkHttpClient` configuration | `:aos-core` |
| References to other variant modules | nowhere |
| MgGate endpoint URL | `:app` (build-time) |

If two variants need the same logic, **promote it to `:core`** (if it's a contract) or duplicate it deliberately (if it's an implementation detail that might diverge later). Premature consolidation across variants is how isolation erodes.

---

## 11. The `:features-scanner` Sibling

`:features-scanner` is **not a variant** — it's an isolated UI feature, sibling to `:features`. It exists because heavy SDKs (io.card payment-card OCR, the `cameraviewplus` camera library, OCR partner integrations, the `sasapi` scraping library) penalize incremental builds of unrelated features. Its dependency rules:

- Depends on: `:core`, `:aos-core`, `:design-system`
- Does **not** depend on: `:features` (no cross-imports)
- Depended on by: `:app` (via navigation entry point)

Treat `:features-scanner` as the prototype for **"isolate when SDK weight justifies it"**. New isolated features (e.g., `:features-kyc-livecheck`, `:features-mydata`) follow the same pattern.

---

## 12. Cross-references

- Variants vs **tenants** (customer-org boundaries *inside* a variant): [19 — Tenants and Variants](19-tenants-and-variants.md)
- The interfaces variants implement: [03 — `:core`](03-core.md)
- The repos that handle the API side (variant-agnostic): [05 — `:data`](05-data.md)
- The shared design system that variant-unique features consume: [04 — `:design-system`](04-design-system.md)
- How `:app` wires variants at boot: [08 — `:app`](08-app-orchestrator.md)
- The `LoggedInComponent` mechanism: [10 — Boot Phases](10-boot-phases.md)
- Onboarding a new variant: [13 — Onboarding a Variant](13-onboarding-a-variant.md)
