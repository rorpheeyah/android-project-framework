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

---

## UI Engine

**Jetpack Compose**.

- Material3 baseline
- `:features/common/theme/` owns design tokens
- `:features/common/components/` owns the component library (`CompassButton`, `CompassTextField`, …)

→ Detail: [06 — `:features`](06-features.md)

---

## Dependency Injection

**Hilt / Dagger** with custom **`@LoggedInScoped`** Hilt component.

- `SingletonComponent` — process-lifetime infrastructure
- `LoggedInComponent` — built once at login, dropped at logout (no in-session swap)
- `:data` and every `:tenants:*:*` `@InstallIn(LoggedInComponent::class)` — auto-discovered, no central registry

→ Detail: [10 — Boot Phases](10-boot-phases.md)

---

## Boot

| Phase | Owner | Job |
|---|---|---|
| MG fetch | `BootCoordinator` | Hardcoded MG URL → `RuntimeConfig` |
| Maintenance/force-update gate | `BootCoordinator` + `:features/boot` | Hard stop on downtime / outdated app |
| Login | `:features/auth/login` | Returns `LoginResponse { userSession, accounts, variantId }` |
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

---

## Networking

**Retrofit + OkHttp** with:

- **Single `FintechApi` interface** in `:data` — server demuxes per user, so no per-variant API surface
- **Certificate pinning** — per environment configured in `:aos-sdk`
- **`BaseUrlInterceptor`** — rewrites base URL at call time using `RuntimeConfig` from MG
- **`AccountIdInterceptor`** — stamps `X-Account-Id` from `Session.activeAccountId`
- **`AuthHeaderInterceptor`** — bearer token from `EncryptedPrefs`
- **Moshi** — Kotlin-friendly JSON parsing

→ Detail: [05 — `:data`](05-data.md), [11 — MG and Runtime Config](11-mg-and-runtime-config.md), [12 — Departments and Session](12-departments-and-session.md)

---

## Storage & Security

| Concern | Library / Mechanism |
|---|---|
| At-rest secrets | `EncryptedSharedPreferences` (AES-256, MasterKey) — wrapped as `EncryptedPrefs` in `:aos-sdk` |
| At-rest blobs | `EncryptedFile` — wrapped as `SecureFileStore` |
| Biometric prompts | `androidx.biometric` |
| Root/jailbreak detection | `SecurityProvider` in `:aos-sdk` (cold-start abort on failure) |
| Keys | Android Keystore — wrapped as `KeystoreManager` |

---

## Submodule Strategy

`:aos-sdk` is consumed as a **Git submodule**, not a Maven dependency:

- Pinned to a specific commit per checkout
- Upgrades are explicit (`git submodule update --remote`)
- Source-level visibility for production debugging

→ Detail: [02 — `:aos-sdk`](02-aos-core.md)

---

## Build Configuration

| Build type | MG endpoint | Logging | Override picker |
|---|---|---|---|
| `release` | Production MG | WARN+ only, no PII | Compiled out |
| `debug` | Staging MG (default) | Verbose | Compiled in via `app/src/debug/` |

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

## Observability

| Concern | Tool |
|---|---|
| Logging | Timber, wrapped as `Logger` |
| Crash reporting | Firebase Crashlytics, wrapped as `CrashlyticsTree` |
| Analytics | Firebase Analytics, wrapped as `AnalyticsClient` (PII-stripping middleware) |
| Remote feature flags | Firebase Remote Config, wrapped as `RemoteConfigClient` |

All Firebase access goes through `:aos-sdk` wrappers — consumers never import Firebase SDKs directly.

> **Note:** feature flags are *not* part of `RuntimeConfig` (MG's payload). Use `RemoteConfigClient` for flags. Keeping MG narrow keeps boot fast.

---

## Testing

| Layer | Approach |
|---|---|
| `:core` | Pure JVM unit tests; trivial because `:core` is mostly contracts and immutable models |
| `:data` | JUnit + MockK on `FintechApi`; assert mapping correctness |
| `:features` ViewModels | JUnit + coroutine test dispatchers; fake `:core` interfaces (no `:data` or `:tenants:*:*` involvement needed) |
| `:tenants:*:*` policies | Pure JVM unit tests of validation/calc rules |
| `:app` integration | Hilt test rules to inject a fake `Session` and a chosen variant; one happy-path per variant |
| End-to-end | Espresso/Compose UI tests against an MG that returns a Sandbox config |

The architecture's testability **is** its testing strategy: because `:features` is Logic-Blind, every ViewModel test injects fake `:core` interfaces — there is no need to construct a data graph or a variant graph for UI tests.
