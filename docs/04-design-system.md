# 04 · `:design-system` — UI Foundations

> **Type:** Local Android library
> **Role:** Theme tokens, primitive Composables, Compose-side helpers. The shared visual layer.
> **Constraint:** No business types. No expense / receipt / approval domain. UI primitives only.

---

## 1. Purpose

`:design-system` is the **shared visual library** — the theme, the components, and the Compose extensions every UI module uses. It exists for two reasons:

1. **Cross-module consistency.** `BizButton` looks the same in `:features`, `:features-scanner`, `:features-hipass`, and any future sibling. The button is defined once.
2. **Sibling isolation without duplication.** Sibling UI modules (`:features`, `:features-scanner`, `:features-hipass`, …) cannot import each other. Without `:design-system`, they would each have to re-implement the design system. With it, they all depend on it directly — no cross-edges, no duplicated primitives.

If a Composable belongs in *every* feature module, it lives here. If it belongs in *one* feature, it lives there.

---

## 2. Module Layout

```
:design-system/
└── src/main/kotlin/com/bizplay/design/
    ├── theme/
    │   ├── BizTheme.kt
    │   ├── BizColors.kt
    │   ├── BizTypography.kt
    │   ├── BizSpacing.kt
    │   └── BizShapes.kt
    ├── components/
    │   ├── button/
    │   │   ├── BizButton.kt
    │   │   ├── BizPrimaryButton.kt
    │   │   └── BizSecondaryButton.kt
    │   ├── input/
    │   │   ├── BizTextField.kt
    │   │   ├── BizPasswordField.kt           ← wraps :aos-core SecureKeypadField (TransKey)
    │   │   └── BizAmountField.kt
    │   ├── feedback/
    │   │   ├── BizSnackbar.kt
    │   │   ├── BizDialog.kt
    │   │   └── BizToast.kt
    │   ├── layout/
    │   │   ├── BizCard.kt
    │   │   ├── BizBottomSheet.kt
    │   │   ├── BizToolbar.kt                 ← Compose successor to today's FlexibleToolBar
    │   │   └── BizScaffold.kt
    │   ├── receipt/
    │   │   ├── BizReceiptHeader.kt
    │   │   ├── BizReceiptRow.kt
    │   │   └── BizReceiptFooter.kt           ← consume RenderedReceipt rows agnostically
    │   ├── webview/
    │   │   └── BizWebViewFrame.kt            ← themed loading/error overlay around :aos-core BizWebView
    │   └── icons/
    │       └── BizIcons.kt
    └── modifiers/
        ├── DebouncedClickable.kt
        └── HapticTouchable.kt
```

| Layer | Purpose |
|---|---|
| `theme/` | Material3 theme + Bizplay design tokens (colors, typography, spacing, shapes) |
| `components/` | Reusable Composable primitives — every feature uses these, none defines its own |
| `components/receipt/` | Layout primitives that walk a `RenderedReceipt` line by line. Domain-agnostic because they accept `List<ReceiptLine>` — they don't know about KR vs KH vs NIA differences. |
| `components/webview/` | Themed frame around `:aos-core`'s `BizWebView` — loading spinner, error retry, theme-consistent chrome. |
| `modifiers/` | Compose Modifier extensions (debounced click, haptic feedback, etc.) |
| `icons/` | The shared icon set |

> **The `Biz` prefix is deliberate** — it matches the existing Bizplay naming convention (today's codebase already has `BizWebview`, `BizLocationManager`). New design-system code keeps that prefix verbatim.

---

## 3. The Theme Setup

```kotlin
// :design-system/theme/BizTheme.kt
@Composable
fun BizTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BizTypography,
        shapes = BizShapes,
    ) {
        CompositionLocalProvider(
            LocalBizSpacing provides BizSpacing(),
        ) {
            content()
        }
    }
}

private val LightColorScheme = lightColorScheme(
    primary = BizColors.Brand,
    onPrimary = BizColors.OnBrand,
    surface = BizColors.Surface,
    // …
)

private val DarkColorScheme = darkColorScheme(
    primary = BizColors.Brand,
    // …
)
```

`:app` wraps the entire `NavHost` in `BizTheme { … }`, so every screen renders inside the design system without each having to opt in.

> **Per-tenant theming** (e.g. POSCO red vs Lotte navy vs NIA orange) is **not** part of `:design-system` and **not** part of `RuntimeConfig`. It is a future-roadmap concern that would be supplied through a separate mechanism reading `TenantContext` at the `:app` level (e.g. a `TenantBrandPolicy` in `:core/policy/` providing override color tokens that `:app` weaves into `BizTheme`'s composition locals). The design system stays variant- and tenant-agnostic.

---

## 4. A Component Example

```kotlin
// :design-system/components/button/BizButton.kt
@Composable
fun BizButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .debouncedClickable(),
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            content()
        }
    }
}
```

Every UI module uses this. No feature redefines its own buttons.

---

## 5. What `:design-system` Must Never Contain

- **Expense / banking types** — `Money`, `Receipt`, `ReceiptDraft`, `ApprovalRequest`, `Card`. The design system is variant-agnostic and domain-agnostic. Domain types belong in `:core/model/`.
- **Variant or tenant references** — `VariantId`, `VariantContext`, `TenantContext`, `TenantId`. The design system has no notion of variants or tenants by definition.
- **Feature-specific Composables** — `LoginScreen`, `ReceiptDetailScreen`, `ApprovalInbox`. Those belong in the feature module that owns them.
- **Networking, storage, security** — those are `:aos-core`. (The `BizPasswordField` Composable *uses* the `SecureKeypadField` from `:aos-core/security/`, but it does not implement the keypad.)
- **Hilt modules** — DI assembly is the orchestrator's job.

If a Composable needs to render a `Money` value, it accepts the formatted `String` — the formatting happens upstream (a feature ViewModel calls a variant-supplied `AmountFormatter`).

If a Composable needs to render a `Receipt`, it accepts a `RenderedReceipt` (a flat list of label/value rows produced by the variant's `ReceiptRenderer`). The design system walks the list; it doesn't know what "POSCO BizDoc Trip" means and shouldn't.

---

## 6. Dependencies

| Module | Dependency |
|---|---|
| `:design-system` → | `:aos-core` only (and Compose / Material3 libraries) |

Notably, `:design-system` does **not** depend on `:core`. The design system is variant-agnostic and domain-agnostic; nothing in `:core` should leak into it.

| Modules that depend on `:design-system` | |
|---|---|
| `:features` | Yes — uses every primitive |
| `:features-scanner` | Yes — uses theme + components (BizScaffold, BizDialog for OCR confirmation, etc.) |
| `:features-{variant-feature}` | Yes (e.g. `:features-hipass`) |
| `:data`, `:variants-*` | No — they have no UI |
| `:app` | Yes — wraps `NavHost` in `BizTheme` |

---

## 7. Public Surface

The entire module is intentionally public. There are no `internal` types worth mentioning — `BizTheme`, `BizColors`, every component is a public API consumed across module boundaries.

---

## 8. Versioning Discipline

Treat `:design-system` like a public API:

- **Adding** a new component or theme token → low-risk, additive.
- **Changing** an existing component's signature → review every call site (search across `:features` and friends).
- **Renaming** a token → expensive; do it deliberately or deprecate first.

A change to `:design-system` triggers recompile of every UI module that depends on it. Treat it accordingly.

---

## 9. Cross-references

- The UI engine that consumes `:design-system`: [06 — `:features`](06-features.md)
- The dependency DAG: [01 — Module Topology](01-module-topology.md)
- Where variant-unique features live (also consume `:design-system`): [07 — `:variants-*` § "When the Variant Has Unique Features"](07-variants.md)
- The WebView primitive that `BizWebViewFrame` decorates: [18 — WebView Integration](18-webview-integration.md)
