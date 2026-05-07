# 13 · Tech Stack — Quick Reference

> One-page reference. For *why* a choice was made, follow the cross-references.

---

## Architecture Pattern

**MVI (Model-View-Intent)** with strict unidirectional data flow.

| Role | Type | Mechanism |
|---|---|---|
| `UiState` | Immutable snapshot | `StateFlow<S>` |
| `UiEvent` | User action → ViewModel | Function call: `onEvent(...)` |
| `UiEffect` | One-shot side effect (nav, toast) | `SharedFlow<F>` |

→ Detail: [07 — MVI Pattern](07-mvi-pattern.md)

---

## UI Engine

**Jetpack Compose** — design language: *Hyper-Physical Minimalism*.

- Material3 baseline
- `:features/common/theme/` owns design tokens (Nexus orange accent + neutrals)
- `:features/common/components/` owns the component library (`NexusButton`, `NexusTextField`, …)

→ Detail: [04 — `:features`](04-features.md)

---

## Dependency Injection

**Hilt / Dagger** with custom **`@TenantScoped`** Hilt component.

- `SingletonComponent` — process-lifetime infrastructure
- `TenantComponent` — destroyed and rebuilt on tenant switch
- Tenant modules `@InstallIn(TenantComponent::class)` — auto-discovered, no central registry

→ Detail: [08 — Runtime Tenant Switching](08-runtime-tenant-switching.md)

---

## Asynchrony

**Kotlin Coroutines + Flow.**

| Use case | Type |
|---|---|
| State exposure | `StateFlow<UiState>` |
| Effect channel | `SharedFlow<UiEffect>` (`replay = 0`, `extraBufferCapacity = 16`) |
| Long-running ops | `viewModelScope.launch { … }` |
| Cancellable coroutines tied to tenant scope | `tenantScope` (cancelled by `TenantSwitcher`) |

---

## Networking

**Retrofit + OkHttp** with:

- **Certificate pinning** — per `(environment, tenant)` configured in `:aos-core`
- **`EnvironmentInterceptor`** — rewrites base URL at call time → enables runtime env switching without rebuilding clients
- **`AuthHeaderInterceptor`** — bearer token from `EncryptedPrefs`
- **Moshi** — Kotlin-friendly JSON parsing

→ Detail: [09 — Environment Configuration](09-environment-configuration.md)

---

## Storage & Security

| Concern | Library / Mechanism |
|---|---|
| At-rest secrets | `EncryptedSharedPreferences` (AES-256, MasterKey) — wrapped as `EncryptedPrefs` in `:aos-core` |
| At-rest blobs | `EncryptedFile` — wrapped as `SecureFileStore` |
| Biometric prompts | `androidx.biometric` |
| Root/jailbreak detection | `SecurityProvider` in `:aos-core` (cold-start abort on failure) |
| Keys | Android Keystore — wrapped as `KeystoreManager` |

---

## Submodule Strategy

`:aos-core` is consumed as a **Git submodule**, not a Maven dependency:

- Pinned to a specific commit per Nexus checkout
- Upgrades are explicit (`git submodule update --remote`)
- Source-level visibility for production debugging

→ Detail: [02 — `:aos-core`](02-aos-core.md)

---

## Build Configuration

| Build type | Server selection | Logging | Picker UI |
|---|---|---|---|
| `release` | Hardcoded to Production | WARN+ only, no PII | Compiled out |
| `debug` | Picker (Production / Staging / UAT / Sandbox) | Verbose | Compiled in via `app/src/debug/` source set |

→ Detail: [09 — Environment Configuration](09-environment-configuration.md)

---

## Build Tooling

| Tool | Used for |
|---|---|
| Gradle (Kotlin DSL) + version catalog (`libs.versions.toml`) | Build scripts |
| Configuration cache, parallel projects, build cache | All enabled in `gradle.properties` |
| KSP | Hilt annotation processing (faster than kapt) |
| Compose Compiler 1.5.4+ with strong skipping | Composable performance |

→ Detail: [12 — Build Performance](12-build-performance.md)

---

## Observability

| Concern | Tool |
|---|---|
| Logging | Timber, wrapped as `NexusLogger` |
| Crash reporting | Firebase Crashlytics, wrapped as `CrashlyticsTree` |
| Analytics | Firebase Analytics, wrapped as `AnalyticsClient` (PII-stripping middleware) |
| Remote feature flags | Firebase Remote Config, wrapped as `RemoteConfigClient` |

All Firebase access goes through `:aos-core` wrappers — consumers never import Firebase SDKs directly.

---

## Testing

| Layer | Approach |
|---|---|
| `:core` | Pure JVM unit tests; trivial because `:core` is mostly contracts and immutable models |
| `:features` ViewModels | JUnit + coroutine test dispatchers; fake `:core` interfaces (no `:tenants:*` involvement needed) |
| `:tenants:*` repositories | Mock the Retrofit interface; assert mapping correctness |
| `:tenants:*` policies | Pure JVM unit tests of validation/calc rules |
| `:app` integration | Hilt test rules to swap `TenantComponent` with a fake; one happy-path per tenant |
| End-to-end | Espresso/Compose UI tests against Sandbox environment |

The architecture's testability **is** its testing strategy: because `:features` is Logic-Blind, every ViewModel test injects fake `:core` interfaces — there is no need to construct a tenant graph for UI tests.
