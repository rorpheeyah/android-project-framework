# Bizplay Demo App

A small but architecturally complete Android app that materializes the framework
described in `../docs/`. It mirrors three flows from the existing
`Bizplay4.0_IPPP` project — **boot / MgGate**, **login**, and **switch
institution** — and leaves the rest as out-of-scope.

The demo is intentionally compile-only: no real Bizplay backend, no mVaccine /
TransKey SDK linkage. A `FakeAuthBackend` + `MgClient` synthesise the same
payloads so you can navigate the full flow offline.

## How to run

This project has no `gradlew` / `gradle-wrapper.jar` committed. From inside
`demo-app/`:

```bash
# one-time, requires Gradle 8.6+ on PATH
gradle wrapper
./gradlew :app:installDebug
```

Or just open `demo-app/` in Android Studio Iguana / Koala (AGP 8.5.x). It will
generate the wrapper on first sync.

### Demo credentials

| Company   | User ID | Password     | Behaviour                                                              |
|-----------|---------|--------------|------------------------------------------------------------------------|
| `BIZPLAY` | `demo`  | `demo1234`   | Returns 3 department accounts → post-login institution picker shown.   |
| `BIZPLAY` | `single`| `single1234` | Returns 1 account → picker skipped, lands on Home immediately.         |

## Module layout

```
demo-app/
├── app/             :app             orchestrator (BootCoordinator, LoggedInComponent, nav)
├── aos-core/        :aos-core        product-agnostic infrastructure (security, BaseUrlProvider)
├── core/            :core            interfaces, models, MVI base, RuntimeConfig, Session
├── design-system/   :design-system   BizTheme, BizButton, BizTextField, …
├── data/            :data            IpppAuthApi, MgClient, AccountIdInterceptor, repos
├── features/        :features        Boot / Login / SelectInstitution / Home (Compose)
├── variants-kr/     :variants-kr    KR-only policies + Hilt @IntoMap bindings
├── settings.gradle.kts
├── build.gradle.kts
└── gradle/libs.versions.toml
```

This matches the DAG in `docs/01-module-topology.md` (forbidden imports
enforced by who-depends-on-whom; no module references a sibling implementation).

## How the demo demonstrates each framework invariant

| Invariant (CLAUDE.md) | Where in the demo |
|---|---|
| **#1 No `if (variantId == …)` anywhere** | `:features/home/HomeViewModel.kt` reads `VariantCapabilities.supportsHipassTracking` — no variant-id branching. The single dispatch happens in `:app/di/VariantResolverModule.kt`. |
| **#2 `:features` is Logic-Blind** | `:features` declares `implementation(project(":core"))` + `:design-system` only. Try adding `project(":data")` — Gradle will accept it but every reviewer will reject the PR. ViewModels inject `AuthRepository` (interface), `BootCoordinator` (interface), `SessionHolder` (interface), `VariantCapabilities` (interface). |
| **#3 `:aos-core` is product-agnostic** | `:aos-core/security/SecurityProvider.kt` and `:aos-core/network/BaseUrlProvider.kt` have no `Receipt`, `Expense`, `Variant`, or `Bizplay`-domain types. The only word a domain owner could quibble with is the package prefix `com.bizplay.aoscore` — itself product-neutral. |
| **#4 One IPPP backend** | `:data/api/IpppAuthApi.kt` is the only Retrofit interface. No `KrKakaoPayApi`, no `KhLocalRailApi`. DTOs in `:data/api/dto/` are variant-agnostic. |
| **#5 Variant modules = policies + DI** | `:variants-kr/` contains exactly two files: `KrCapabilities.kt` (a policy) and `di/KrVariantModule.kt` (Hilt @IntoMap binding). No `api/`, no `repo/`, no Compose. |
| **#6 `:design-system` is variant- and domain-agnostic** | `:design-system/build.gradle.kts` has no `project(":core")` line. `BizButton`, `BizTextField`, etc. take only primitive types and slots. |
| **#7 Variant/tenant bind once at login** | `:app/di/LoggedInComponent.kt` is a custom Hilt component built from `LoginResponse` and dropped on logout (`:app/boot/BootCoordinatorImpl.kt#onLogout`). No swap path. |
| **#8 Only MG URL is hardcoded** | `:app/build.gradle.kts` sets `MG_URL` via `buildConfigField`. `MgClient` is the only consumer. Every other endpoint reads `RuntimeConfig.urls.main` via `RuntimeConfigStore` → `BaseUrlProvider`. |
| **#9 Departments are accounts, not sub-variants** | `:core/session/Session.kt` exposes `accounts: List<DepartmentAccount>` + `activeAccountId: StateFlow<AccountId>`. Switching is a StateFlow flip; no DI rebuild. `AccountIdInterceptor` (`:data/network/AccountIdInterceptor.kt`) reads it at call time. |
| **#10 `:features` is Hybrid-Monolith** | All four screens live as packages inside `:features/`. There is no `:features-login` Gradle module — and there should not be, until we add a heavy-SDK or variant-locked feature (the framework's `:features-scanner` / `:features-hipass` precedent). |
| **#11 Onboarding a variant is additive** | To add KH: `mkdir variants-kh`, copy `KrCapabilities.kt` / `KrVariantModule.kt` and change `@VariantKey("kh")`, add `include(":variants-kh")` to `settings.gradle.kts`, add `VariantId.KH` to `VariantCatalogue`. Zero edits to `:features`, `:data`, `:design-system`. |
| **#12 Tenants are an axis inside variants** | `:core/tenant/Tenant.kt` defines `TenantFlags` (named booleans) and `TenantParams` (named typed fields). The login response (`LoginResponseDto`) carries the tenant snapshot. `HomeViewModel` reads `tenant.flags.showsCorporateLogo`, never `tenant.id`. |

## Flow walkthrough

```
┌───────────┐   runBoot()           ┌─────────────────┐
│ BootScreen├──────────────────────►│ BootCoordinator │
└───────────┘                       └────────┬────────┘
       │                                     │ MgClient.fetch (only hardcoded URL)
       │                                     │ SecurityProvider.runSelfChecks
       │  BootResult.Ready                   │
       │◄────────────────────────────────────┘
       ▼
┌───────────┐  AuthRepository.login   ┌──────────────────┐
│LoginScreen├────────────────────────►│ IpppAuthRepo →   │
└───────────┘                         │ FakeAuthBackend  │
       │ onLoginSuccess(response)     └──────────────────┘
       ▼
┌────────────────────────────────────────────────────────┐
│ BootCoordinator.onLoginSuccess                         │
│   builds LoggedInComponent(Session, Variant, Tenant)   │
└─────────────────────────┬──────────────────────────────┘
                          │
        accounts.size > 1 │                  accounts.size == 1
                          ▼                          ▼
        ┌─────────────────────────────┐    ┌──────────────┐
        │ SelectInstitutionScreen     │    │ HomeScreen   │
        │   mode = PostLogin          │    │              │
        │ → coordinator.finalizeLogin │    │              │
        └─────────────────────────────┘    └──────────────┘
                          │                          ▲
                          └──────────────────────────┘
                                                     │
                  user taps "Switch institution"     │
                                                     ▼
                  ┌─────────────────────────────────────────┐
                  │ SelectInstitutionScreen                 │
                  │   mode = InSessionSwitch                │
                  │ → session.switchAccount(target)         │
                  │   (StateFlow flip — no DI rebuild)      │
                  └─────────────────────────────────────────┘
```

## Mapping to the existing Bizplay4.0_IPPP code

| Existing class                         | Mirror in demo                                                                 |
|----------------------------------------|--------------------------------------------------------------------------------|
| `IntroActivity` / `IntroViewModel`     | `BootScreen` / `BootViewModel` + `BootCoordinatorImpl.runBoot()`               |
| `Conf.SITE_MG_URL + "/MgGate"`         | `BuildConfig.MG_URL` + `MgApi` / `MgClient`                                    |
| MG response `MG_TRAN_DATA_REC`         | `MgGateResponseDto` → `RuntimeConfig`                                          |
| `mVaccine` / `AppIronSuite3`           | `SecurityProvider` (NoopSecurityProvider stub in the demo)                     |
| `LoginActivity` / `LoginViewModel`     | `LoginScreen` / `LoginViewModel`                                               |
| `P001_REQ` / `P001_RES`                | `LoginRequestDto` / `LoginResponseDto` + `toDomain()` mapper                   |
| `TransKeyCtrl` (mtk SDK)               | `SecureKeypad` (StubSecureKeypad in the demo)                                  |
| `SelectUserInttIdActivity`             | `SelectInstitutionScreen` (mode = `PostLogin`)                                 |
| `BPCD_MBL_L901_REC` / `_RES`           | `DepartmentAccount` carried inside `LoginResponse.accounts`                    |
| `USE_INTT_ID` + `COMPANY_CD` headers   | Stamped by `AccountIdInterceptor` from `Session.activeAccountId`               |
| `DetailConfig.isXxx()`                 | `TenantFlags` named booleans + `VariantCapabilities` interface                 |

## Out of scope (intentionally)

- `:features-scanner` (camera + io.card + OCR) — heavy-SDK isolation pattern is documented but not wired here.
- `:features-hipass` (Korea-only highway tolls) — variant-locked feature module pattern not wired here.
- Real Retrofit + OkHttp pipeline (`AccountIdInterceptor` is wired in code but the demo's `FakeAuthBackend` short-circuits the network layer).
- `:variants-kh` / `:variants-vn` — adding either is the "strictly additive" exercise from invariant #11.
- Persistent token storage (would live behind a `:aos-core/storage/EncryptedDatabase` wrapper).

Read the full architectural rationale in `../docs/`.
