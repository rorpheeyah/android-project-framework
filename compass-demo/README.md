# Compass Demo

A runnable Android Kotlin project that demonstrates the **Compass Framework** (see [`../docs/`](../docs/)). It mirrors three features from the [BizplayIPPP](file:///Users/rorpheeyah/AndroidStudioProjects/Bizplay4.0_IPPP) reference project — **Boot**, **Login**, and **switch institution** — and shows how each maps onto the framework's module topology, MVI conventions, and one-time-at-login variant binding.

The rest of BizplayIPPP (receipts, cards, approvals, etc.) is intentionally **not** ported. The point is to prove the architectural shape, not to re-implement a banking app.

---

## How to read this

1. Skim **["The walk-through"](#the-walk-through)** below to see the boot → login → switch flow in code.
2. For any framework concept, jump to the matching doc in [`../docs/`](../docs/).
3. The **["BizplayIPPP → Compass map"](#bizplayippp--compass-map)** table tells you which Java file in BizplayIPPP corresponds to which Kotlin file here.

## Project structure

```
compass-demo/
├── aos-core/        # banking-agnostic infrastructure (HTTP, EncryptedPrefs, Logger)
├── core/            # interfaces + domain models + MVI base + VariantContext/Key
├── design-system/   # Compose theme + primitive components — variant-agnostic
├── data/            # FintechAuthApi (Retrofit) + FintechAuthRepo (impl) — variant-agnostic
├── features/        # Logic-Blind UI engine: BootScreen, LoginScreen, InstitutionPickerScreen, AccountSwitcherScreen
├── variants-kh/     # KH policies + DI (no UI, no networking)
├── variants-vn/     # VN policies + DI
└── app/             # CompassApplication, MainActivity, BootCoordinator, MgClient, navigation, DI wiring
```

The dependency edges and forbidden imports follow [`../docs/01-module-topology.md`](../docs/01-module-topology.md) exactly — `:features` does **not** depend on `:data` or any `:variants-*`.

## Build

```bash
cd compass-demo
# First time only — generate the Gradle wrapper:
gradle wrapper --gradle-version 8.7
./gradlew :app:assembleDebug
```

Or just open the `compass-demo/` directory in Android Studio (Hedgehog or later).

The demo runs against a fake auth backend — [`DemoAuthInterceptor`](app/src/main/kotlin/com/compass/app/demo/DemoAuthInterceptor.kt) short-circuits `POST /v1/auth/login` and returns canned data so you can exercise the flow without a server. **It is only registered in debug builds**, the production wiring goes through the real `:data` repo + the MG-discovered API base URL.

## Demo accounts (canned in `DemoAuthInterceptor`)

| User ID prefix | Variant returned | Accounts returned |
|---|---|---|
| `kh.alice` | `kh` | 3 institutions (Personal, Corporate, Joint) — triggers picker |
| `vn.bao` | `vn` | 2 institutions — triggers picker |
| `kh.solo` or `vn.solo` | `kh` / `vn` | 1 institution — skips picker |

Password is ignored. Try logging in twice with different prefixes to see the **variant switch on logout-then-login**: the same `HomeScreen` shows `1,234,567 ៛` under `kh.alice` and `1.234.567 ₫` under `vn.bao` — without a single `if (variantId == "kh")` anywhere in `:features`.

---

## The walk-through

### 1. Boot

The user opens the app. `MainActivity` hosts `AppNavigation` whose start destination is `Routes.Boot`.

- [`BootScreen`](features/src/main/kotlin/com/compass/features/boot/BootScreen.kt) (in `:features`) shows a loading state and asks its `BootViewModel` to drive boot.
- `BootViewModel` calls the `BootDriver` interface (in `:features`). The implementation, [`BootCoordinator`](app/src/main/kotlin/com/compass/app/boot/BootCoordinator.kt) in `:app`, is bound through [`BootDriverModule`](app/src/main/kotlin/com/compass/app/di/BootDriverModule.kt) — `:features` never imports `:app`.
- `BootCoordinator` calls [`MgClient`](app/src/main/kotlin/com/compass/app/boot/MgClient.kt) — the only hardcoded URL in the binary is `BuildConfig.MG_URL`, set per build type.
- MG returns a `RuntimeConfig`. The base API URL is pushed to `BaseUrlProvider` (in `:aos-core`), so every subsequent request hits the right host without recompiling.
- If `RuntimeConfig.maintenance` is `Down` → navigate to `MaintenanceGate`. If `forceUpdate` is required → `ForceUpdateGate`. Otherwise → `Login`.

This is exactly the flow documented in [`../docs/10-boot-phases.md`](../docs/10-boot-phases.md). The BizplayIPPP equivalent is `IntroActivity` → `IntroViewModel.requestMG()`.

### 2. Login

- [`LoginScreen`](features/src/main/kotlin/com/compass/features/auth/login/LoginScreen.kt) is a plain Compose form, structured MVI-style with `LoginContract`/`LoginViewModel`/`LoginScreen`.
- The screen renders the variant's support hotline + email read from `SupportContacts`; the institution-code field shows only if `VariantCapabilities.supportsInstitutionPicker` is true. Both come from [`PreLoginPolicies`](features/src/main/kotlin/com/compass/features/auth/login/PreLoginPolicies.kt) — provided by [`VariantResolverModule`](app/src/main/kotlin/com/compass/app/di/VariantResolverModule.kt) using the **default variant** (since the session doesn't exist yet).
- `LoginViewModel` calls `AuthRepository.login(...)` — that's the `:core` interface implemented by `:data/FintechAuthRepo`. **The ViewModel never names the impl**.
- The response (`LoginResponse`) carries: `userSession`, `accounts: List<DepartmentAccount>`, `variantId`. A single-account response goes straight to `Home`; a multi-account response routes to the `InstitutionPicker`.

The BizplayIPPP equivalent is `LoginActivity` + `LoginViewModel.requestLogin(...)`.

### 3. Pick institution (pre-login picker)

- [`InstitutionPickerScreen`](features/src/main/kotlin/com/compass/features/auth/institution/InstitutionPickerScreen.kt) displays the `accounts` from the `LoginResponse` and asks the user to pick one.
- On confirm, `:app`'s `BootCoordinator.onLoginSuccess(response, selectedId)` builds a `Session` (with the chosen `activeAccountId`) and a `LoggedInComponent` — that's a **one-time event per session** (`LoggedInComponentManager.build(...)`).
- Navigation transitions to `Home`.

The BizplayIPPP equivalent is `SelectUserInttIdActivity`, which lets a user pick a `JOIN_USE_INTT_ID` before final login completion.

### 4. Switch institution (in-session)

- Once on `Home`, the user can tap **"Switch institution"** to reach [`AccountSwitcherScreen`](features/src/main/kotlin/com/compass/features/account/switcher/AccountSwitcherScreen.kt).
- `AccountSwitcherViewModel` injects `Session` (provided by [`LoggedInBindingsModule`](app/src/main/kotlin/com/compass/app/di/LoggedInBindingsModule.kt)) and calls `Session.switchAccount(id)`.
- That's a single `MutableStateFlow` flip — no DI rebuild, no network call. [`AccountIdInterceptor`](app/src/main/kotlin/com/compass/app/session/AccountIdInterceptor.kt) reads `Session.activeAccountId.value` on the *next* request and stamps the new id on the wire.

The BizplayIPPP equivalent is the post-login portion of `SelectUserInttIdActivity` — the user can return to it to switch the active institution within the same login.

### 5. Logout (and the variant story)

- Tapping **"Log out"** on `Home` calls [`LogoutHandler.logout()`](app/src/main/kotlin/com/compass/app/session/LogoutHandler.kt).
- `LoggedInComponentManager.drop()` clears the active `Session`; OkHttp cache is evicted; `EncryptedPrefs` is purged.
- `AppNavigation` observes `componentManager.current` go null and pops the back-stack to `Login`.
- When the next user logs in (perhaps with a different variant), a **fresh `LoggedInComponent` is built**. Every `@LoggedInScoped` policy + repo from the previous session is now unreachable and GC-eligible. There is no in-session variant swap — variant change *is* logout-then-login. See [`../docs/10-boot-phases.md`](../docs/10-boot-phases.md) §6.

---

## BizplayIPPP → Compass map

| BizplayIPPP (Java, MVVM + DataBinding) | Compass demo (Kotlin, MVI + Compose) | Framework concept |
|---|---|---|
| `IntroActivity` | `features/boot/BootScreen` + `app/boot/BootCoordinator` | One-time boot phase ([10](../docs/10-boot-phases.md)) |
| `IntroViewModel.requestMG()` | `app/boot/MgClient` + `core/runtime/RuntimeConfig` | Boot-time URL discovery ([11](../docs/11-mg-and-runtime-config.md)) |
| `requestMG` maintenance dialog | `features/boot/MaintenanceGate` | Hard-stop maintenance gate |
| `Update_Close_Check` force-update dialog | `features/boot/ForceUpdateGate` | Hard-stop version gate |
| `LoginActivity` + `LoginViewModel.requestLogin()` | `features/auth/login/Login*` + `data/repo/FintechAuthRepo` | Login as the **only** point of variant selection |
| `SelectUserInttIdActivity` (pre-login picker) | `features/auth/institution/InstitutionPicker*` | Multi-`USE_INTT_ID` enrolment → `accounts: List<DepartmentAccount>` ([12](../docs/12-departments-and-session.md)) |
| `MemoryPreferenceDelegator` holding `JOIN_USE_INTT_ID` | `Session.activeAccountId` (a `StateFlow`) | Account-scope state — value, not graph ([12](../docs/12-departments-and-session.md)) |
| `OTPActivity` | *(out of scope; sketched as `VariantCapabilities.supportsOtpOnLogin`)* | UI gating via capability flags ([07](../docs/07-variants.md)) |
| `ComTran` + `Conf.RealConfig`/`Conf.TestConfig` (hardcoded URLs) | `aos-core/network/HttpClient` + `BaseUrlProvider` (set by MG) | MG is the **only** hardcoded URL ([10](../docs/10-boot-phases.md) §2) |
| `comm/conf/DetailConfig.isCompanyX()` chains | `VariantCapabilities`, `AmountFormatter`, `SupportContacts` interfaces | Polymorphism replaces `if (variantId == X)` chains ([07](../docs/07-variants.md)) |
| `BaseActivity`/`BaseViewModel` (LiveData-based) | `core/mvi/MviViewModel` (StateFlow + Channel) | MVI base class ([09](../docs/09-mvi-pattern.md)) |
| Single `app/` Gradle module | 8 modules: `aos-core`, `core`, `design-system`, `data`, `features`, `variants-kh`, `variants-vn`, `app` | Module topology ([01](../docs/01-module-topology.md), [17](../docs/17-project-structure.md)) |

---

## What this demo demonstrates (and what it doesn't)

| Demonstrated | Where |
|---|---|
| MG → RuntimeConfig → BaseUrlProvider plumbing | `BootCoordinator` + `MgClient` + `NetworkModule` |
| MVI Contract/State/Event/Effect convention | every `*Contract.kt` + `MviViewModel` |
| Logic-Blind `:features` (no `:data` or `:variants-*` import) | `:features/build.gradle.kts` — `dependencies` block |
| One unified `FintechAuthApi` for all variants | `:data/api/FintechAuthApi.kt` |
| Variant policies via `@VariantKey` multibindings | `:variants-*/di/*VariantModule.kt` + `:app/di/VariantResolverModule.kt` |
| Single point of variant dispatch | `VariantResolverModule` — search the codebase for `variantContext.id`; only this file consults it for dispatch |
| Session-scoped account switching | `Session.activeAccountId` (StateFlow) + `AccountIdInterceptor` |
| Logout = `LoggedInComponent` drop + cache evict | `LogoutHandler` |
| MG URL as the only hardcoded URL | `:app/build.gradle.kts` `buildConfigField("String", "MG_URL", ...)` |
| 1 line of `:features` per added variant | **zero** — onboarding a variant adds a new module + one catalogue entry only ([13](../docs/13-onboarding-a-variant.md)) |

| Not demonstrated (out of scope) | Why |
|---|---|
| Real OTP, biometric, TransKey | The framework treats them as capabilities ([07](../docs/07-variants.md)); this demo just shows the gating mechanism |
| `:features-chatbot` and `:features-{variant-feature}` siblings | The demo ports three flows; sibling UI modules are documented in [14](../docs/14-build-performance.md) |
| Tenants (sub-axis inside a variant) | See [19](../docs/19-tenants-and-variants.md); orthogonal to the boot/login/switch story |
| Force-update version comparison | `BootCoordinator.BUILD_VERSION_CODE` is stubbed to `1` |
| Real `LoggedInComponent` via `@DefineComponent` | The demo uses a `LoggedInComponentManager` holder for legibility — the structural behaviour (build-once, drop-on-logout) is identical |

## Where to look next

- The single point of variant dispatch — open [`VariantResolverModule`](app/src/main/kotlin/com/compass/app/di/VariantResolverModule.kt) and search the rest of the codebase for `variantContext.id`. You will find no other call site.
- The Logic-Blind rule — open [`:features/build.gradle.kts`](features/build.gradle.kts) and confirm there is no `project(":data")` or `project(":variants-*")` in `dependencies`.
- The forbidden imports — try adding `import com.compass.data...` inside any `:features/**/*.kt`. Gradle will refuse to compile.
