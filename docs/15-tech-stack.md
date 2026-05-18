# 15 · Tech Stack — Quick Reference

> One-page reference. For *why* a choice was made, follow the cross-references.

---

## Architecture Pattern

**MVI (Model-View-Intent)** with strict unidirectional data flow.

| Role | Type | Mechanism |
|---|---|---|
| `UiState` | Immutable snapshot | `StateFlow<S>` |
| `UiEvent` | User action → ViewModel | Function call: `onEvent(...)` |
| `UiEffect` | One-shot side effect (nav, toast) | `SharedFlow<F>` |

→ Detail: [09 — MVI Pattern](09-mvi-pattern.md)

> **Migrating from today:** the existing Bizplay code is MVVM-with-callbacks (Activities + `OnComTranListener` + Java ViewModels). The framework moves to Kotlin + Compose + MVI. Java code paths under `app/src/main/java/com/bizcard/bizplayPPPEnt/` are the source material for the per-feature ports.

---

## UI Engine

**Jetpack Compose**.

- Material3 baseline
- `:design-system/theme/` owns design tokens (`BizColors`, `BizTypography`, `BizSpacing`, `BizShapes`)
- `:design-system/components/` owns the component library (`BizButton`, `BizTextField`, `BizPasswordField`, `BizWebView`, `BizReceiptHeader`, …)

→ Detail: [06 — `:features`](06-features.md), [04 — `:design-system`](04-design-system.md)

---

## Dependency Injection

**Hilt / Dagger** with custom **`@LoggedInScoped`** Hilt component.

- `SingletonComponent` — process-lifetime infrastructure
- `LoggedInComponent` — built once at login, dropped at logout (no in-session swap)
- `:data` and every `:variants-*` `@InstallIn(LoggedInComponent::class)` — auto-discovered, no central registry
- Variant policies use `@IntoMap @VariantKey("<id>")` multibindings; per-tenant structural policies use `@TenantKey("<id>")`. `VariantResolverModule` and `TenantResolverModule` are the single points of dispatch.

→ Detail: [10 — Boot Phases](10-boot-phases.md), [07 — `:variants-*` § 6](07-variants.md), [19 — Tenants and Variants § 8](19-tenants-and-variants.md)

---

## Boot

| Phase | Owner | Job |
|---|---|---|
| MgGate fetch | `BootCoordinator` | Hardcoded MgGate URL → `RuntimeConfig` |
| Maintenance/force-update gate | `BootCoordinator` + `:features/boot` | Hard stop on downtime / outdated app |
| Login | `:features/auth/login` | Returns `LoginResponse { userSession, variantId, tenantId, tenantFlags, tenantParams, accounts }` |
| Institution picker (multi-`USE_INTT_ID` users) | `:features/auth/institutionpicker` | Compose successor to today's `SelectUserInttIdActivity` |
| Build session graph | `BootCoordinator` | Builds `LoggedInComponent` |
| Logout | `LogoutHandler` | Drops `LoggedInComponent`, clears prefs/caches |

→ Detail: [10 — Boot Phases](10-boot-phases.md), [11 — MG and Runtime Config](11-mg-and-runtime-config.md)

---

## Asynchrony

**Kotlin Coroutines + Flow.**

| Use case | Type |
|---|---|
| State exposure | `StateFlow<UiState>` |
| Effect channel | `SharedFlow<UiEffect>` (`replay = 0`, `extraBufferCapacity = 16`) |
| Long-running ops | `viewModelScope.launch { … }` |
| Session-scoped coroutines | `sessionScope` (cancelled when `LoggedInComponent` is dropped) |

> Today's Bizplay code uses **RxAndroid** in places. The framework migrates to Coroutines + Flow throughout. RxJava interop is acceptable as a transitional measure but new code is Coroutines-native.

---

## Networking

**Retrofit + OkHttp** with:

- **`Ippp*Api` interfaces** in `:data`, split by feature area (auth, receipt, approval, card, expense, OCR, notice) — server demuxes per user + per institution, so no per-variant API surface
- **Certificate pinning** — per environment configured in `:aos-core`
- **`BaseUrlInterceptor`** — rewrites base URL at call time using `RuntimeConfig.urls.main` from MgGate
- **`AccountIdInterceptor`** — stamps `X-Use-Intt-Id` + `X-Company-Cd` + `X-Dvsn-Cd` from `Session.activeAccount()`
- **`AuthHeaderInterceptor`** — bearer token from `EncryptedPrefs`
- **Moshi** — Kotlin-friendly JSON parsing

→ Detail: [05 — `:data`](05-data.md), [11 — MG and Runtime Config](11-mg-and-runtime-config.md), [12 — Departments and Session](12-departments-and-session.md)

> **Replaces today's `ComTran` wrapper.** The existing `comm/tran/ComTran` + `OnComTranListener` callback shape is replaced by Retrofit suspend functions and Result types.

---

## Storage & Security

| Concern | Library / Mechanism | Wrapper |
|---|---|---|
| At-rest secrets | `EncryptedSharedPreferences` (AES-256, MasterKey) | `:aos-core/storage/EncryptedPrefs` — replaces today's `PreferenceDelegator` |
| Encrypted local DB | **SQLCipher** (`sqlcipher-android` 4.9.0) | `:aos-core/storage/EncryptedDatabase` — wraps today's `AsyncGenerateCipherDatabase` |
| At-rest blobs (photos pre-upload) | `EncryptedFile` | `:aos-core/storage/SecureFileStore` |
| Secure password / PIN entry | **TransKey SDK** (`mtk_v4.6.0.53.jar`) | `:aos-core/security/SecureKeypad` — surfaced as `BizPasswordField` in `:design-system` |
| Malware scanning | **mVaccine** (`core_b2b2c_*.jar`) | `:aos-core/security/SecurityProvider` (cold-start abort) |
| Edge crypto (native) | **Secucen** (`libEdgeCrypto.so`) | `:aos-core/security/EdgeCrypto` |
| Licence verification | **RSLicenseSDK** (`RSLicenseSDK_*.jar`) | `:aos-core/security/LicenseChecker` |
| Biometric prompts | `androidx.biometric` | `:aos-core/security/BiometricAuthenticator` |
| Root/jailbreak/debugger | Composed checks | `:aos-core/security/SecurityProvider` |
| Keys | Android Keystore | `:aos-core/security/KeystoreManager` |

---

## WebView

- **`BizWebView`** in `:aos-core/webview/` — replaces today's `BizWebview` Java class with a hardened Compose Composable
- **`WebActionBridge`** — exactly one `@JavascriptInterface` method, versioned payload — replaces today's `BrowserBridge` + `iWebAction` / `iWebActionBA` near-duplicates
- **`CookieSync`** — push/pull cookies between OkHttp `CookieJar` and `CookieManager` around allowlisted URL loads
- **URL allowlist** — every webview URL resolves through `RuntimeConfig.webRoutes[<key>]`; arbitrary `loadUrl(String)` is forbidden
- **Webview-backed feature modules** sibling to `:features` (e.g. `:features-terms`, `:features-online-mall`, `:features-kakaopay-link`) — never inside `:features`

→ Detail: [18 — WebView Integration](18-webview-integration.md)

---

## Submodule Strategy

`:aos-core` is consumed as a **Git submodule**, not a Maven dependency:

- Pinned to a specific commit per checkout
- Upgrades are explicit (`git submodule update --remote`)
- Source-level visibility for production debugging

Today's `.gitmodules` already pins `SMART_LIB_STUDIO_CAMBODIA` on branch `v1.0.13` — the framework formalises this pattern.

→ Detail: [02 — `:aos-core`](02-aos-core.md)

---

## Build Configuration

| Build type | MgGate endpoint | Logging | Override picker |
|---|---|---|---|
| `release` | Production MgGate (`https://mg.bizplay.co.kr/MgGate`) | WARN+ only, no PII | Compiled out |
| `debug` | Staging MgGate (`https://mg-dev.bizplay.co.kr/MgGate`, default) | Verbose | Compiled in via `app/src/debug/` |

→ Detail: [11 — MG and Runtime Config](11-mg-and-runtime-config.md)

---

## Build Tooling

| Tool | Used for |
|---|---|
| Gradle (Kotlin DSL) + version catalog (`libs.versions.toml`) | Build scripts |
| Configuration cache, parallel projects, build cache | All enabled in `gradle.properties` |
| KSP | Hilt annotation processing (faster than kapt) |
| Compose Compiler 1.5.4+ with strong skipping | Composable performance |

→ Detail: [14 — Build Performance](14-build-performance.md)

---

## Locale & Internationalisation

The existing Bizplay project supports 5 locales: Korean (default), English, Vietnamese, Japanese, Chinese, via `LocaleHelper` and per-locale `strings.xml` resource sets. The framework keeps this — locale is **orthogonal to variant**. A KR-variant tenant could legitimately use English (e.g. for foreign employees of a Korean company); a KH-variant tenant could use English or Khmer. `VariantContext.defaultLocale` is just a *default* for the language picker, not a hard constraint.

---

## Maps & Location

| Concern | Library |
|---|---|
| Korean roads + Hi-Pass routes | **Naver Maps SDK** (`map-sdk` 3.22.0) — used by `:features-hipass` |
| Korean POI / road routing | **TMap SDK** (`Tmap_*.jar`) — used by `:features-hipass` and gasoline-route entry |
| Fused location | `play-services-location` — wrapped as `:aos-core/location/LocationProvider` (today's `BizLocationManager`) |

Maps SDKs are heavy → they live inside the feature modules that need them (`:features-hipass`, `:features-scanner`'s gasoline-route screen), **not** in `:features` or `:aos-core`.

---

## Payment-Card / Receipt OCR

| Concern | Library |
|---|---|
| Payment card scanner | **io.card** (`android-sdk` 5.5.1) — used by `:features-scanner` |
| Camera entry | **cameraviewplus** (local module) — wrapped by `:features-scanner` |
| Receipt-image OCR | OCR partner SDK + `sasapi` scraping (`sasapi_v2.6.7.jar`) — used by `:features-scanner` |

All of this lives inside `:features-scanner` (heavy-SDK isolation).

---

## Push Notifications

- **Firebase Messaging** (FCM) — wrapped behind `:aos-core/firebase/MessagingService`
- Approval-pending pushes deep-link into `:features/approval/inbox` via the top-level navigation graph

---

## Observability

| Concern | Tool |
|---|---|
| Logging | Timber, wrapped as `Logger` |
| Crash reporting | Firebase Crashlytics, wrapped as `CrashlyticsTree` |
| Analytics | Firebase Analytics, wrapped as `AnalyticsClient` (PII-stripping middleware) |
| Remote feature flags | Firebase Remote Config, wrapped as `RemoteConfigClient` |

All Firebase access goes through `:aos-core` wrappers — consumers never import Firebase SDKs directly.

> **Note:** feature flags are *not* part of `RuntimeConfig` (MgGate's payload). Use `RemoteConfigClient` for flags. Per-tenant flag *values* come on the login response (in `tenantFlags`), not from MgGate. Keeping MgGate narrow keeps boot fast.

---

## Testing

| Layer | Approach |
|---|---|
| `:core` | Pure JVM unit tests; trivial because `:core` is mostly contracts and immutable models |
| `:data` | JUnit + MockK on `Ippp*Api`; assert mapping correctness |
| `:features` ViewModels | JUnit + coroutine test dispatchers; fake `:core` interfaces (no `:data` or `:variants-*` involvement needed) |
| `:variants-*` policies | Pure JVM unit tests of validation/calc rules |
| Per-tenant profiles | Pure JVM unit tests of `TenantProfile.flags` / `params` matrices |
| `:app` integration | Hilt test rules to inject a fake `Session` and a chosen variant + tenant; one happy-path per variant |
| End-to-end | Espresso/Compose UI tests against an MgGate that returns a Sandbox config |

The architecture's testability **is** its testing strategy: because `:features` is Logic-Blind, every ViewModel test injects fake `:core` interfaces — there is no need to construct a data graph or a variant graph for UI tests.
