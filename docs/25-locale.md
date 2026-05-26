# 25 · Locale and Multi-Lingual UI

> **Languages:** Korean (KR), English (EN), Khmer (KH) — minimum. PRD item 4.
> **Mechanism:** `AppCompatDelegate.setApplicationLocales` (Android 13+) + `LocaleConfig.xml` + per-language `values-*` resources.
> **Fonts:** bundled Noto Sans KR and Noto Sans Khmer; do not rely on device font fallback.
> **Hard rule:** user-facing strings live in `strings.xml`. Tenant policy classes own *business rules*, not *display text*.

> **Why this is critical:** the framework's earlier examples baked bilingual receipt text into tenant policy classes (the `ReceiptRenderer` with hardcoded Khmer+English string concatenation). That pattern is wrong for a multi-lingual product. The user can switch language at any time; tenant policies don't change with the user's UI language. The two axes must be separate: tenant is *what business rules apply*; locale is *what language is shown*.

---

## 1. The Three Layers

```
┌────────────────────────────────────────────────────────────────────┐
│  user picks language in Settings → LocaleManager.set(locale)       │
└────────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────────┐
│  AppCompatDelegate.setApplicationLocales(LocaleListCompat)         │
│      Android persists the choice; recreates active Activity        │
└────────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────────────────┐
│  res/values-ko/, res/values-km/, res/values/ (en fallback)         │
│      Compose reads stringResource(R.string.x) → returns localized  │
└────────────────────────────────────────────────────────────────────┘
```

Number/date/currency formatting joins this pipeline: `NumberFormatter` and `DateTimeFormatter` use the active `Locale` via ICU, not hand-rolled per-tenant formatters.

---

## 2. Manifest Declaration

```xml
<!-- :app/src/main/AndroidManifest.xml -->
<application
    android:localeConfig="@xml/locales_config"
    …>
</application>
```

```xml
<!-- :app/src/main/res/xml/locales_config.xml -->
<locale-config>
    <locale android:name="en"/>
    <locale android:name="ko"/>
    <locale android:name="km"/>
</locale-config>
```

`LocaleConfig.xml` is the **Android 13+ system contract** for per-app language. Once declared, Android shows Compass in the system Settings → Languages app picker. The user can switch from there *or* from in-app Settings — both route through the same Android-managed locale list.

---

## 3. `:aos-sdk/i18n/LocaleManager`

```kotlin
// :aos-sdk/i18n/LocaleManager.kt
class LocaleManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun current(): Locale = AppCompatDelegate.getApplicationLocales()
        .takeIf { !it.isEmpty }?.get(0) ?: Locale.getDefault()

    fun set(languageTag: String) {
        // language tags: "en", "ko", "km"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        // Android persists across app restarts; no manual SharedPreferences needed.
    }

    fun supported(): List<SupportedLocale> = listOf(
        SupportedLocale("en", displayName = "English"),
        SupportedLocale("ko", displayName = "한국어"),
        SupportedLocale("km", displayName = "ខ្មែរ"),
    )
}

data class SupportedLocale(
    val languageTag: String,
    val displayName: String,            // in its OWN language; not translated
)
```

`AppCompatDelegate.setApplicationLocales` is available from `androidx.appcompat:appcompat:1.6+` and back-ports the Android 13 per-app locale API to older Android versions via a manifest service hook.

---

## 4. The LocaleSelector UI

```kotlin
// :design-system/components/i18n/LocaleSelector.kt
@Composable
fun CompassLocaleSelector(
    available: List<SupportedLocale>,
    current: Locale,
    onSelected: (SupportedLocale) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        available.forEach { locale ->
            ListItem(
                headlineContent = { Text(locale.displayName) },
                trailingContent = {
                    if (locale.languageTag == current.toLanguageTag()) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                },
                modifier = Modifier.clickable { onSelected(locale) },
            )
        }
    }
}
```

The selector lives in `:design-system` because it's a reusable primitive — every consuming app needs a language picker. The `displayName` is in each language's own script (English / 한국어 / ខ្មែរ), not translated; this is the universal convention so users find their language even from an unfamiliar starting point.

---

## 5. String Resources

```
:app/src/main/res/
├── values/strings.xml        # English — default fallback
├── values-ko/strings.xml     # Korean
└── values-km/strings.xml     # Khmer
```

Same key in every file:

```xml
<!-- values/strings.xml -->
<string name="loan_apply_submit">Submit application</string>

<!-- values-ko/strings.xml -->
<string name="loan_apply_submit">신청서 제출</string>

<!-- values-km/strings.xml -->
<string name="loan_apply_submit">ដាក់ស្នើពាក្យសុំ</string>
```

Compose reads them via `stringResource(R.string.loan_apply_submit)`. The active `Locale` (set by `AppCompatDelegate`) determines which file is read.

### 5.1 Strings live in `:app` and `:features` modules

Each feature module owns the strings it uses:

```
:features/src/main/res/
├── values/strings.xml          # English defaults for all :features screens
├── values-ko/strings.xml
└── values-km/strings.xml
```

When `:features-kyc` adds a new screen, it adds its own `strings.xml` entries in its own `res/` — not in `:app`. The build merges resources at app-assembly time.

### 5.2 Strings do **NOT** live in tenant policy classes

Bad:

```kotlin
// ❌ :tenants:cambodia:base/format/KhReceiptRenderer.kt
internal object KmStrings {
    const val AMOUNT_LABEL = "ចំនួនទឹកប្រាក់"
    const val SENDER_LABEL = "អ្នកផ្ញើ"
}
```

Good:

```xml
<!-- ✅ :features/src/main/res/values-km/strings.xml -->
<string name="receipt_amount_label">ចំនួនទឹកប្រាក់</string>
<string name="receipt_sender_label">អ្នកផ្ញើ</string>
```

```kotlin
// ✅ :features/loan/contract/ContractScreen.kt
Text(stringResource(R.string.receipt_amount_label))
```

**Why:** the language the user wants and the tenant they belong to are independent. A `cambodia:nh` user might prefer the app in English while business rules (KHR currency, NBC limits) come from the Khmer-regulator tenant. Hardcoding strings in tenant policies couples the two axes wrongly.

The tenant policy class can still own *currency formatting* (`KhrAmountFormatter`) because that's a business rule, not a language string. The decimal separator, currency symbol, and digit grouping convention belong to the *currency*, not the user's UI language.

---

## 6. Bundled Fonts

```
:app/src/main/res/font/
├── noto_sans_khmer_regular.ttf
├── noto_sans_khmer_medium.ttf
├── noto_sans_khmer_bold.ttf
├── noto_sans_kr_regular.otf
├── noto_sans_kr_medium.otf
└── noto_sans_kr_bold.otf
```

Older Android devices in Cambodia routinely render unknown-codepoint boxes for Khmer because the system font has no Khmer glyphs. Bundling Noto Sans Khmer guarantees correct rendering everywhere.

Korean is better-supported in Android system fonts but font weight handling differs — bundling Noto Sans KR ensures the same look across devices.

```kotlin
// :design-system/theme/CompassTypography.kt
val NotoSansKhmer = FontFamily(
    Font(R.font.noto_sans_khmer_regular, FontWeight.Normal),
    Font(R.font.noto_sans_khmer_medium, FontWeight.Medium),
    Font(R.font.noto_sans_khmer_bold, FontWeight.Bold),
)
val NotoSansKr = FontFamily(/* … */)

@Composable
fun appFontFamily(): FontFamily {
    val locale = LocalConfiguration.current.locales[0]
    return when (locale.language) {
        "km" -> NotoSansKhmer
        "ko" -> NotoSansKr
        else -> FontFamily.Default
    }
}

@Composable
fun CompassTheme(content: @Composable () -> Unit) {
    val typography = remember(appFontFamily()) {
        MaterialTheme.typography.copy(
            // override every text style's fontFamily
        )
    }
    MaterialTheme(typography = typography, content = content)
}
```

Size impact per language: ~1.5–2 MB per font family. Total bundled fonts: ~6 MB. Acceptable.

---

## 7. Number, Date, and Currency Formatting

Use **ICU formatters** with the active `Locale`, not hand-rolled formatters per tenant.

```kotlin
// :aos-sdk/i18n/FormatHelpers.kt
fun formatNumber(value: BigDecimal, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).format(value)

fun formatDateTime(instant: Instant, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(locale)
        .format(instant.atZone(ZoneId.systemDefault()))
```

Currency formatting is still tenant-policy-bound because the **currency symbol and placement convention** belong to the currency (KHR, USD, KRW), not the user's UI language:

```kotlin
// :tenants:cambodia:base/format/KhrAmountFormatter.kt
internal class KhrAmountFormatter : AmountFormatter {
    override fun format(amount: Money): String =
        "%,.0f៛".format(amount.value)                    // "1,234,567៛" — Khmer Riel symbol
}
```

A Korean-language user with a `cambodia:nh` tenant sees KHR amounts formatted with the riel symbol; the surrounding UI text is in Korean. Currency formatting is *currency-bound*, not *locale-bound*.

For locale-bound number formatting (e.g., a referral count, a step indicator), use `NumberFormat.getNumberInstance(locale)`.

---

## 8. RTL Handling

Khmer is left-to-right despite using non-Latin script. Korean is left-to-right. **No RTL handling required** for the current language set.

If Arabic or Hebrew is added later, the framework's existing Compose layouts will need `Modifier.layoutDirection(LayoutDirection.Rtl)` review. Defer until in scope.

---

## 9. What Does NOT Belong Here

| ❌ Wrong pattern | ✅ Right pattern |
|---|---|
| String literals in `:features` ViewModels | `stringResource(R.string.x)` in the composable; pass the resolved string into state if needed |
| String constants in tenant policy classes (`object KmStrings { const val X = "…" }`) | `strings.xml` resources per language |
| Per-tenant string overrides ("for tenant X, button reads Y") | If genuine, model as `TenantParams.customButtonLabel: String?`; default to `strings.xml` value |
| Locale-dependent business rules ("if Korean user, hide field") | Business rules are tenant-bound, not locale-bound. Use `TenantFlags`, not `Locale` |
| Hand-rolled number formatting in `:tenants:*` for non-currency values | ICU `NumberFormat` with the active `Locale` |
| Assuming device system font has Khmer glyphs | Always bundle Noto Sans Khmer |
| Restarting the activity manually after locale change | `AppCompatDelegate.setApplicationLocales` handles it |

---

## 10. Cross-references

- The Compose theme that picks the font family: [04 — `:design-system`](04-design-system.md)
- The settings screen where the user picks language: [06 — `:features`](06-features.md) (`settings/` package)
- The reason this is a hard requirement: [PRD-FIT-ASSESSMENT § 3.8](../PRD-FIT-ASSESSMENT.md)
- The tenant-policy boundary that strings must NOT cross: [07 — `:tenants:*`](07-variants.md), [19 — Tenants and Regions](19-tenants-and-variants.md)
