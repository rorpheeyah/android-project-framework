# 04 · Naming Decisions

> **Purpose:** Audit every framework-level name and classify it as **keep verbatim**, **parameterize per project**, or **rename now**. Naming is small individually but bakes into every future project — settle it before the doc refactor and before the first `:aos-sdk` release tag.

---

## 1. Classification System

| Class | Meaning |
|---|---|
| **KEEP** | Framework-level name; reuse verbatim across every consuming product. Renaming would fragment the architectural vocabulary. |
| **PARAM** | Each project picks its own value. The framework provides a placeholder convention; each project substitutes. |
| **RENAME** | Current name is wrong for the framework's reusable shape. Change before consumers lock in. |

---

## 2. The Audit

| Name | Where it lives | Class | Notes |
|---|---|---|---|
| **`Compass`** (framework brand) | Doc titles, README, "Compass framework" prose | **DECIDE** | See §3 — biggest open question |
| **`:aos-sdk`** (renamed from `:aos-core`) | Submodule name, module path, doc references | **KEEP** | Cross-product SDK; the name is generic by design. "aos" is your org's Android product prefix. |
| **`:core`** | Module name | **KEEP** | Universal Android convention |
| **`:design-system`** | Module name | **KEEP** | Universal convention |
| **`:data`** | Module name | **KEEP** | Universal convention |
| **`:features`** | Module name | **KEEP** | Universal convention |
| **`:features-<name>`** sibling pattern | Module-naming convention | **KEEP** | Standard multi-module convention |
| **`:tenants:<region>:<tenantSlug>`** | Module path convention | **KEEP** | The framework's tenant model is load-bearing; the path expression is part of the contract |
| **`:app`** | Module name | **KEEP** | Universal Android convention |
| **`CompassApplication`** | Application class name in `:app` | **PARAM** | Each project renames (e.g., `AcmeApplication`, `<Brand>Application`). Framework docs use `CompassApplication` as a placeholder; init checklist tells projects to rename. |
| **`CompassTheme` / `CompassButton` / `Compass*`** | `:design-system` component prefix | **PARAM** | Each project picks its prefix (e.g., `Acme*`, `<Brand>*`). The "Compass" prefix in framework docs is a placeholder. |
| **`CompassWebView`, `CompassCameraView`** | `:aos-sdk/webview/`, `:aos-sdk/camera/` | **DECIDE** | Lives inside `:aos-sdk` so it's cross-product. See §3 — does the SDK use a generic prefix or stay branded? |
| **`MgClient`** | `:app/boot/` | **KEEP** | MG = Mobile Gateway; framework concept, generic |
| **`RuntimeConfig`** | `:core/runtime/` | **KEEP** | Generic |
| **`MaintenanceGate`, `ForceUpdateGate`** | `:features/boot/` | **KEEP** | Generic |
| **`Session`, `UserSession`, `DepartmentAccount`, `AccountId`** | `:core/session/` | **KEEP** | Generic — applicable across fintech (banking, lending, insurance, brokerage) and beyond (multi-tenant enterprise, marketplace) |
| **`@LoggedInScoped`** | `:core/scope/` | **KEEP** | Generic |
| **`LoggedInComponent`, `LoggedInComponentManager`, `LoggedInEntryPoint`, `LoggedInBindingsModule`** | `:app/di/` | **KEEP** | Generic |
| **`BootCoordinator`, `BootResult`, `StaleConfigFallback`** | `:app/boot/` | **KEEP** | Generic |
| **`TenantContext`, `TenantId`, `TenantFlags`, `TenantParams`, `TenantCapabilities`, `TenantKey`** | `:core/tenant/` | **KEEP** | The tenant model is the framework's signature; renaming would defeat the whole abstraction |
| **`TenantCatalogue`, `TenantContextResolver`, `TenantResolverModule`** | `:app/tenant/`, `:app/di/` | **KEEP** | Generic |
| **`AccountIdInterceptor`, `AuthHeaderInterceptor`, `BaseUrlInterceptor`** | `:aos-sdk/network/` | **KEEP** | Generic |
| **`EncryptedPrefs`, `SecureFileStore`, `EncryptedDatabase`** | `:aos-sdk/storage/` | **KEEP** | Generic |
| **`SecurityProvider`, `BiometricAuthenticator`, `KeystoreManager`** | `:aos-sdk/security/` | **KEEP** | Generic |
| **`NotificationChannelRegistry`, `MessagingService`** | `:aos-sdk/firebase/` | **KEEP** | Generic |
| **Concrete channel ids (`reminder` / `transaction` / `announcement`)** | `:aos-sdk/firebase/NotificationChannelRegistry` | **PARAM** | These are reasonable defaults but the channel SET is per-product. A brokerage might add `price-alert`; a delivery app might add `order-status`. |
| **`WebActionBridge`, `CookieSync`** | `:aos-sdk/webview/` | **KEEP** | Generic |
| **`CameraXController`, `DocumentScannerWrapper`, `FaceDetectorWrapper`** | `:aos-sdk/{camera,ml}/` | **KEEP** | Generic |
| **`ImageCompressor`, `ExifStripper`, `Watermarker`, `BitmapRedactor`** | `:aos-sdk/imaging/` | **KEEP** | Generic |
| **`PdfDownloader`, `PdfViewer`** | `:aos-sdk/pdf/` | **KEEP** | Generic |
| **`LocaleManager`, `FontFallback`** | `:aos-sdk/i18n/` | **KEEP** | Generic |
| **`BackgroundWorkScheduler`** | `:aos-sdk/work/` | **KEEP** | Generic |
| **`DeepLinkResolver`** | `:aos-sdk/deeplink/` | **KEEP** | Generic |
| **`PermissionRequester`** | `:aos-sdk/permissions/` | **KEEP** | Generic |
| **`AuthRepository`** | `:core/repository/` | **KEEP** | Every product has auth |
| **Other `*Repository` interfaces** | `:core/repository/` | **PARAM** | Each project defines its own per its domain |
| **`OtpDeliveryPolicy`, `SessionTimeoutPolicy`, `AmountFormatter`, `ComplianceThresholds`, `BusinessCalendar`** | `:core/policy/` | **KEEP** | Generic patterns every multi-tenant product needs or close variants |
| **Domain-specific policy interfaces** | `:core/policy/` | **PARAM** | Each project defines its own |
| **`Money`, `Currency`** | `:core/model/` | **KEEP** | Generic. Every fintech product needs `Money(BigDecimal, Currency)`. |
| **Domain-specific model types** | `:core/model/` | **PARAM** | Each project defines its own |
| **`Fintech*Api` family** | `:data/api/` | **PARAM** | Each project defines its own Retrofit interfaces |
| **`FintechAuthApi`** specifically | `:data/api/` | **PARAM + convention** | Every project needs an auth API; convention is to call it `FintechAuthApi` for consistency across products |
| **Currency `AmountFormatter` impls** (e.g., `<ISO>AmountFormatter`) | `:tenants:<region>:base/format/` | **PARAM** | Each project's currency formatters |
| **Region-base policy impls** | `:tenants:<region>:base/policy/` | **PARAM** | Each project's region-base policies |
| **Concrete tenant types** | `:tenants:<region>:<tenant>/` | **PARAM** | Each project's tenants |
| **`region` slugs (e.g., `cambodia`, `korea`)** | Module paths | **PARAM** | Each project picks regions |
| **`tenant` slugs (e.g., `nh`, `partner-a`)** | Module paths | **PARAM** | Each project picks tenants |

---

## 3. The "Compass" Question

The framework currently identifies as "Compass". Three options:

### Option A — Keep "Compass" as the framework name; remove from code

The framework's intellectual identity is "Compass"; consuming products use their own brand for code-level naming.

- Docs: titled "Compass Framework"
- Module paths: generic (`:core`, `:data`, `:design-system`, `:aos-sdk`, `:tenants:...`)
- Component primitives in `:design-system` use the **project's own prefix** (placeholder `Compass*` in framework docs becomes `<YourBrand>*` per project)
- Application class is per-project (`<YourBrand>Application`)
- WebView/Camera wrappers in `:aos-sdk` either keep `Compass*` (and projects accept Compass-typed primitives in their code) OR rename to `Aos*` (consistent with the SDK's name)

### Option B — Rename framework to a generic name

Options: `Aos`, `AosFramework`, or strip the name entirely and let each project decide.

- The framework docs identify as e.g. "AOS Framework" or "AOS Multi-Tenant Architecture"
- No brand leakage anywhere
- Loses the distinctive name

### Option C — Split: "Compass" stays at doc/identity level only; code uses `Aos*` prefix consistently

- Doc set is "Compass Framework" (intellectual identity, easy to reference)
- All code-level types in `:aos-sdk` use `Aos*` prefix: `AosWebView`, `AosCameraView`, etc.
- All code-level placeholders in `:design-system` use `<YourBrand>*` per project
- "Compass" never appears in any consuming project's code

### Recommendation: **Option C**

Reasoning:
- Preserves what's distinctive (the framework's intellectual identity at the doc level)
- Eliminates brand leakage into consuming products
- Aligns with the SDK rename (`:aos-sdk`); consistent `Aos*` prefix throughout SDK code
- Modest rename effort: ~5–10 places in `:aos-sdk` (the `Compass*` types)

**Concrete renames if Option C is adopted:**

| Current | New |
|---|---|
| `CompassWebView` | `AosWebView` |
| `CompassCameraView` | `AosCameraView` |
| `CompassApplication` (framework doc placeholder) | Stays as placeholder; doc says "rename to `<YourBrand>Application` in your project" |
| `CompassTheme`, `CompassButton`, `CompassTextField`, etc. (in `:design-system`) | Stays as placeholder in framework docs; doc says "rename `Compass*` → `<YourBrand>*` in your project's `:design-system`" |

---

## 4. Brand Prefix Convention

For consuming projects' code (specifically `:design-system` component prefix + `Application` class + any project-specific brand-bearing types), the framework needs a **convention** so each project's choice is predictable:

**Recommended pattern:** `<2-4-letter-brand-prefix><Component>` — concise, visually distinct.

| Project (hypothetical) | Brand prefix | Example component | Application class |
|---|---|---|---|
| Acme Financial customer app | `Acme` | `AcmeButton`, `AcmeTextField` | `AcmeApplication` |
| Pyramid Insurance | `Py` or `Pyr` | `PyButton`, `PyrCard` | `PyApplication` or `PyrApplication` |
| White-label marketplace | `Mkt` or `<TenantBrand>` | `MktButton` or per-tenant theming via design-tokens | `MarketplaceApplication` |

The framework docs say: "wherever you see `Compass*` in `:design-system` or `<YourBrand>Application` in `:app`, substitute your project's brand prefix."

---

## 5. Filename Renames (Already Decided in Prior Work)

| Current | Target | Status |
|---|---|---|
| `docs/02-aos-core.md` | `docs/02-aos-sdk.md` (after refactor: `docs/framework/02-aos-sdk.md`) | Pending |
| `docs/07-variants.md` | `docs/07-tenants.md` (after refactor: `docs/framework/07-tenants.md`) | Pending |
| `docs/13-onboarding-a-variant.md` | `docs/13-onboarding-a-tenant.md` (after refactor: `docs/framework/13-onboarding-a-tenant.md`) | Pending |
| `docs/19-tenants-and-variants.md` | `docs/19-tenants-and-regions.md` (after refactor: `docs/framework/19-tenants-and-regions.md`) | Pending |

Aligned with [`02-doc-refactor-plan.md`](02-doc-refactor-plan.md). Land them together.

---

## 6. The "aos" Prefix Question

The `:aos-sdk` name uses an "aos" prefix that's presumably your org's chosen Android-product prefix. It appears in:

- The Gradle module name: `:aos-sdk`
- Type names (if Option C above is adopted): `AosWebView`, `AosCameraView`
- The package path: `com.aos.sdk.*` (or `<your-org>.aos.sdk.*`)
- The Maven coordinate (if Option A SDK distribution): `<your-org>:aos-sdk:1.0.0`

**Decision needed:** is "aos" the right org-level prefix? If your org uses a different abbreviation for its Android products, now is the moment to standardize. Once `:aos-sdk` v1.0.0 ships and the first project pins it, the package path is committed.

---

## 7. Summary of Recommended Renames

In order of importance:

1. **Adopt Option C for the Compass question:** keep "Compass Framework" at the doc/identity level; rename `CompassWebView`/`CompassCameraView` to `AosWebView`/`AosCameraView` inside `:aos-sdk`; treat `CompassTheme`, `CompassButton`, etc. in `:design-system` as `<YourBrand>*` placeholders documented as "rename to your project's prefix".
2. **Confirm "aos" as the org-level prefix.** If yes, lock it in. If a different acronym is preferred, change it across `:aos-sdk` now.
3. **Execute the four pending doc filename renames** as part of the doc refactor (per [`02-doc-refactor-plan.md`](02-doc-refactor-plan.md)).
4. **Add a "Naming Conventions" section to `docs/framework/15-tech-stack.md`** (or a new `docs/framework/31-naming-conventions.md`) so the brand-prefix convention is documented in the framework spec, not just here.

---

## 8. What's NOT a Naming Decision

To rule out confusion:

- **`compass:<region>:<tenant>` is not a tenant id.** Tenant ids are `<region>:<tenantSlug>` (e.g., `cambodia:nh`). The framework name does not appear in tenant ids.
- **The `:tenants:*` module path is not branded.** It's `:tenants:<region>:<tenantSlug>` regardless of framework name.
- **`MgClient` is not a brand.** "MG" = "Mobile Gateway", a framework concept; the name stays.
- **`@TenantKey("<region>:<tenant>")` is not a brand.** It's the composite-key annotation; the value is per-project.

---

## 9. Cross-references

- The strategy: [`00-strategy.md`](00-strategy.md)
- The audit that catalogs which names live where: [`01-current-state-audit.md`](01-current-state-audit.md)
- The doc refactor that should land the filename renames: [`02-doc-refactor-plan.md`](02-doc-refactor-plan.md)
- The init checklist that consumes the brand-prefix convention: [`03-new-project-init-checklist.md`](03-new-project-init-checklist.md)
- The reference instance for context: [`05-case-study-lending.md`](05-case-study-lending.md)
