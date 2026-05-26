# 04 · `:design-system` — UI Foundations

> **Type:** Local Android library
> **Role:** Theme tokens, primitive Composables, Compose-side helpers. The shared visual layer.
> **Constraint:** No business types. No banking domain. UI primitives only.

---

## 1. Purpose

`:design-system` is the **shared visual library** — the theme, the components, and the Compose extensions every UI module uses. It exists for two reasons:

1. **Cross-module consistency.** `CompassButton` looks the same in `:features`, `:features-chatbot`, and any `:features-{tenant-feature}`. The button is defined once.
2. **Sibling isolation without duplication.** Sibling UI modules (`:features` and `:features-chatbot`, plus any `:features-{tenant-feature}`) cannot import each other. Without `:design-system`, they would each have to re-implement the design system. With it, they all depend on it directly — no cross-edges, no duplicated primitives.

If a Composable belongs in *every* feature module, it lives here. If it belongs in *one* feature, it lives there.

---

## 2. Module Layout

```
:design-system/
└── src/main/kotlin/com/<org>/design/
    ├── theme/
    │   ├── CompassTheme.kt
    │   ├── CompassColors.kt
    │   ├── CompassTypography.kt
    │   ├── CompassSpacing.kt
    │   └── CompassShapes.kt
    ├── components/
    │   ├── button/
    │   │   ├── CompassButton.kt
    │   │   ├── CompassPrimaryButton.kt
    │   │   └── CompassSecondaryButton.kt
    │   ├── input/
    │   │   ├── CompassTextField.kt
    │   │   └── CompassPasswordField.kt
    │   ├── feedback/
    │   │   ├── CompassSnackbar.kt
    │   │   └── CompassDialog.kt
    │   ├── layout/
    │   │   ├── CompassCard.kt
    │   │   └── CompassBottomSheet.kt
    │   └── icons/
    │       └── CompassIcons.kt
    └── modifiers/
        ├── DebouncedClickable.kt
        └── HapticTouchable.kt
```

| Layer | Purpose |
|---|---|
| `theme/` | Material3 theme + Compass design tokens (colors, typography, spacing, shapes) |
| `components/` | Reusable Composable primitives — every feature uses these, none defines its own |
| `modifiers/` | Compose Modifier extensions (debounced click, haptic feedback, etc.) |
| `icons/` | The shared icon set |

---

## 3. The Theme Setup

```kotlin
// :design-system/theme/CompassTheme.kt
@Composable
fun CompassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CompassTypography,
        shapes = CompassShapes,
    ) {
        CompositionLocalProvider(
            LocalCompassSpacing provides CompassSpacing(),
        ) {
            content()
        }
    }
}

private val LightColorScheme = lightColorScheme(
    primary = CompassColors.Brand,
    onPrimary = CompassColors.OnBrand,
    surface = CompassColors.Surface,
    // …
)

private val DarkColorScheme = darkColorScheme(
    primary = CompassColors.Brand,
    // …
)
```

`:app` wraps the entire `NavHost` in `CompassTheme { … }`, so every screen renders inside the design system without each having to opt in.

---

## 4. A Component Example

```kotlin
// :design-system/components/button/CompassButton.kt
@Composable
fun CompassButton(
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

- **Banking types** — `Money`, `Account`, `LoanApplication`, `RepaymentSchedule`, etc. The design system is tenant-agnostic and domain-agnostic. Domain types belong in `:core/model/`.
- **Tenant references** — `TenantId`, `TenantContext`. The design system has no notion of tenants by definition.
- **Feature-specific Composables** — `LoginScreen`, `LoanApplyScreen`, `KycCaptureScreen`. Those belong in the feature module that owns them.
- **Networking, storage, security** — those are `:aos-sdk`.
- **Hilt modules** — DI assembly is the orchestrator's job.

If a Composable needs to render a `Money` value, it accepts the formatted `String` — the formatting happens upstream (a feature ViewModel calls a tenant-supplied `AmountFormatter`).

---

## 6. Dependencies

| Module | Dependency |
|---|---|
| `:design-system` → | `:aos-sdk` only (and Compose / Material3 libraries) |

Notably, `:design-system` does **not** depend on `:core`. The design system is tenant-agnostic and domain-agnostic; nothing in `:core` should leak into it.

| Modules that depend on `:design-system` | |
|---|---|
| `:features` | Yes — uses every primitive |
| `:features-chatbot` | Yes — uses theme + components |
| `:features-{tenant-feature}` | Yes (e.g. `:features-bakong-disputes`) |
| `:data`, `:tenants:*:*` | No — they have no UI |
| `:app` | Yes — wraps `NavHost` in `CompassTheme` |

---

## 7. Public Surface

The entire module is intentionally public. There are no `internal` types worth mentioning — `CompassTheme`, `CompassColors`, every component is a public API consumed across module boundaries.

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
- Where tenant-unique features live (also consume `:design-system`): [07 — `:tenants:*` § "When the Tenant Has Unique Features"](07-variants.md)
