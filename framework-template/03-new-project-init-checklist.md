# 03 · New Project Init Checklist

> **Audience:** the engineer or tech lead bootstrapping a new multi-tenant Android product on this framework.
> **Goal:** running "hello world" app (boot → mock login → empty dashboard with active tenant displayed) within one week.
> **Prerequisites:** access to `:aos-sdk` git submodule (or Maven artifact), a project name, a regulator/region scope, at least one tenant identifier.
> **Domain neutrality:** this checklist uses placeholders. Substitute your project's values throughout.

> **What this is not:** a code generator. This is a *guided manual* — each step has a clear "done" criterion. Once 2–3 projects have followed it, the mechanical steps can be automated.

---

## Placeholders Used Throughout

| Placeholder | What you substitute | Example |
|---|---|---|
| `<your-project>` | git repo name | `acme-customer-app` |
| `<your-org-domain>` | reverse-DNS for the app id | `com.acme` |
| `<your-org-prefix>` | 2–4 letter prefix for branded code components | `Acme` |
| `<region>` | regulator/country slug | `cambodia`, `vietnam`, `korea`, `singapore` |
| `<tenant-slug>` | organization slug within a region | `nh`, `partner-a`, `partner-b` |
| `<your-domain>` | the business capability your product addresses | `loan`, `policy`, `order`, `claim`, `payment` |

The example values shown above are illustrative — pick whatever fits your product.

---

## Phase 0 — Decide

Before writing any code, gather these decisions. Each blocks subsequent steps.

| Decision | Why it matters | Example |
|---|---|---|
| Project name | Becomes the git repo name, application id, app display name, root package | `acme-customer-app` (app id `com.acme.customer`) |
| Brand prefix (`<your-org-prefix>`) | Used in design-system components and the `:app/<Org>Application` class | `Acme` (e.g., `AcmeApplication`, `AcmeButton`) |
| Region(s) | Drives `:tenants:<region>:base` module paths and tenant id composition | `cambodia` (single region for now) |
| First tenant slug | The concrete tenant module name; composite `TenantId` is `<region>:<tenantSlug>` | `nh` → `TenantId("cambodia:nh")` |
| `:aos-sdk` version | Which git tag of the SDK this project pins | `v1.0.0` |
| MG URL per build type | Production and staging service-discovery endpoints | `https://mg.acme.example/`, `https://mg.staging.acme.example/` |
| App Link host | The HTTPS host for App Links | `https://app.acme.example` |
| Primary backend host | Returned by MG; not baked into the binary, but you need to know for staging tests | `api.acme.example` |
| Initial language set | Drives `LocaleConfig.xml` and bundled fonts | English + a second language |
| Initial currencies | Drives the `AmountFormatter` implementations in the region-base module | One or more ISO-4217 codes |

**Done criterion:** all 10 decisions are documented in your new project's `DECISIONS.md` (or equivalent), reviewable by stakeholders.

---

## Phase 1 — Bootstrap the Repo

### Step 1.1 — Create the repo

```bash
mkdir <your-project> && cd <your-project>
git init
echo "# <Your Project Name>" > README.md
git add . && git commit -m "Initial commit"
```

### Step 1.2 — Add `:aos-sdk` as a git submodule

```bash
git submodule add https://github.com/<your-org>/aos-sdk.git aos-sdk
cd aos-sdk && git checkout v1.0.0 && cd ..
git add .gitmodules aos-sdk
git commit -m "Pin :aos-sdk to v1.0.0"
```

*(Substitute Maven artifact dependency in `gradle/libs.versions.toml` if your org distributes the SDK that way instead.)*

### Step 1.3 — Initialize Gradle structure

Copy the (future) `starter/` skeleton into the repo. Before `starter/` exists, hand-create:

```
<your-project>/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── aos-sdk/                          # submodule
├── core/build.gradle.kts
├── design-system/build.gradle.kts
├── data/build.gradle.kts
├── features/build.gradle.kts
├── tenants/<region>/base/build.gradle.kts
├── tenants/<region>/default/build.gradle.kts
├── tenants/<region>/<tenant-slug>/build.gradle.kts
└── app/build.gradle.kts
```

`settings.gradle.kts`:

```kotlin
rootProject.name = "<your-project>"

include(":aos-sdk")
include(":core")
include(":design-system")
include(":data")
include(":features")
include(":tenants:<region>:base")
include(":tenants:<region>:default")
include(":tenants:<region>:<tenant-slug>")
include(":app")
```

**Done criterion:** `./gradlew :app:tasks` succeeds without configuration errors.

---

## Phase 2 — Fill in the Framework Foundations

### Step 2.1 — Implement `:core` foundations

Create the framework-mandatory types (per [`docs/framework/03-core.md`](../docs/framework/03-core.md)). At minimum:

```
core/src/main/kotlin/<your-org-domain>/core/
├── tenant/
│   ├── TenantContext.kt
│   ├── TenantId.kt
│   ├── TenantFlags.kt
│   ├── TenantParams.kt
│   └── TenantCapabilities.kt
├── runtime/
│   ├── RuntimeConfig.kt
│   ├── ApiUrls.kt
│   ├── MaintenanceState.kt
│   └── ForceUpdate.kt
├── session/
│   ├── Session.kt
│   ├── DepartmentAccount.kt
│   └── AccountId.kt
├── mvi/
│   ├── UiState.kt
│   ├── UiEvent.kt
│   ├── UiEffect.kt
│   └── MviViewModel.kt
└── scope/
    ├── LoggedInScoped.kt
    └── TenantKey.kt
```

**Your domain types come next** (Step 2.4). Don't pre-define them here.

**Done criterion:** `:core:compileKotlin` succeeds. All types are `internal` to the module by default; only the listed types are `public`.

### Step 2.2 — Implement `:design-system` minimum

Create your theme (`<YourOrgPrefix>Theme`, e.g., `AcmeTheme`), colors, typography, plus three primitives (`<YourOrgPrefix>Button`, `<YourOrgPrefix>TextField`, `<YourOrgPrefix>Dialog`). Add bundled fonts for your language set (per [`docs/framework/25-locale.md`](../docs/framework/25-locale.md)).

**Done criterion:** a Compose preview of `<YourOrgPrefix>Button` renders.

### Step 2.3 — Implement `:app` skeleton

Following [`docs/framework/08-app-orchestrator.md`](../docs/framework/08-app-orchestrator.md) and [`docs/framework/10-boot-phases.md`](../docs/framework/10-boot-phases.md):

```
app/src/main/kotlin/<your-org-domain>/app/
├── <YourOrgPrefix>Application.kt     # @HiltAndroidApp; e.g., AcmeApplication
├── MainActivity.kt
├── AppNavigation.kt
├── boot/
│   ├── BootCoordinator.kt
│   ├── MgClient.kt
│   ├── BootResult.kt
│   └── StaleConfigFallback.kt
├── di/
│   ├── NetworkModule.kt
│   ├── LoggedInComponent.kt
│   ├── LoggedInEntryPoint.kt
│   ├── LoggedInBindingsModule.kt
│   ├── TenantResolverModule.kt
│   ├── RuntimeConfigModule.kt
│   └── FirebaseModule.kt
├── session/
│   ├── SessionFactory.kt
│   ├── AccountIdInterceptor.kt
│   ├── LoggedInComponentManager.kt
│   ├── InactivityDetector.kt
│   └── LogoutHandler.kt
└── tenant/
    ├── TenantCatalogue.kt
    └── TenantContextResolver.kt
```

`AndroidManifest.xml` declares:
- `android:localeConfig="@xml/locales_config"` per [`docs/framework/25-locale.md`](../docs/framework/25-locale.md)
- App Link intent filter per [`docs/framework/22-deeplinks.md`](../docs/framework/22-deeplinks.md) with your `https://app.<your-host>` declared
- `POST_NOTIFICATIONS`, `ACCESS_COARSE_LOCATION`, `CAMERA` permissions as needed
- `FileProvider` for PDF/file sharing
- Application class declared with `@HiltAndroidApp`

`build.gradle.kts` adds:
- `MG_URL` per build type via `buildConfigField`
- `STALE_CONFIG_TTL_MS` per build type
- Google Maps API key placeholder (filled at MG-init time, if maps in scope)
- `${MAPS_API_KEY}` manifest placeholder

**Done criterion:** the app launches, the boot screen appears, MG fetch fails (no MG yet) → maintenance screen shown. This proves the boot pipeline is wired.

### Step 2.4 — Define your domain types in `:core`

This is the most product-specific step. Following the patterns in [`docs/framework/03-core.md`](../docs/framework/03-core.md):

```
core/src/main/kotlin/<your-org-domain>/core/
├── repository/
│   ├── AuthRepository.kt                    # mandatory — every product needs auth
│   └── <YourCapability>Repository.kt        # one per logical capability your product addresses
├── policy/
│   ├── OtpDeliveryPolicy.kt                 # framework-level pattern
│   ├── SupportContacts.kt                   # framework-level pattern
│   ├── AmountFormatter.kt                   # if you handle money
│   ├── ComplianceThresholds.kt              # framework-level pattern
│   ├── BusinessCalendar.kt                  # framework-level pattern
│   ├── SessionTimeoutPolicy.kt              # framework-level pattern
│   └── <YourDomain>Policy.kt                # your domain-specific policies
└── model/
    ├── Money.kt                             # framework-recommended; reuse the canonical shape
    ├── Currency.kt                          # framework-recommended
    ├── UserSession.kt                       # framework-mandatory
    ├── LoginResponse.kt                     # framework-mandatory
    └── <YourDomain>Model.kt                 # your domain-specific models
```

**Use [`docs/reference-app/domain-types.md`](../docs/reference-app/domain-types.md) as one worked example.** Copy the file structure; replace the domain. The reference instance happens to be a lending product, but the *pattern* applies to any domain.

**Done criterion:** `:core:compileKotlin` succeeds; every type is reviewable as a clean contract definition with no implementation logic.

### Step 2.5 — Stand up `:data`

Following [`docs/framework/05-data.md`](../docs/framework/05-data.md):

```
data/src/main/kotlin/<your-org-domain>/data/
├── api/
│   ├── FintechAuthApi.kt
│   ├── Fintech<YourArea>Api.kt              # one per logical area in your product
│   └── dto/
│       ├── auth/
│       └── <area>/
├── repo/
│   ├── FintechAuthRepo.kt                   # implements AuthRepository
│   ├── <YourArea>Repo.kt
│   └── mapping/
├── external/                                # third-party clients if any (credit bureau, etc.)
└── di/
    └── DataModule.kt
```

**Use [`docs/reference-app/api-surface.md`](../docs/reference-app/api-surface.md) as one worked example** of how to organize a multi-area `Fintech*Api` family.

**Done criterion:** `:data:compileKotlin` succeeds; `:app` can resolve a repository via Hilt (verify with a trivial `LoggedInEntryPoint` access).

### Step 2.6 — Build the region-base tenant module

For your region (e.g., `<region>`):

```
tenants/<region>/base/src/main/kotlin/<your-org-domain>/tenants/<region>/base/
├── policy/
│   ├── <RegionPrefix>Default<YourPolicy>.kt # regulator-wide policies
│   ├── <RegionPrefix>OtpDeliveryPolicy.kt
│   ├── <RegionPrefix>ComplianceThresholds.kt
│   └── <RegionPrefix>BusinessCalendar.kt
├── format/
│   └── <Currency>AmountFormatter.kt
└── capability/
    └── <RegionPrefix>BaseCapabilities.kt
```

Region-base modules **provide classes only**; they do not declare Hilt bindings. Concrete tenants bind them.

**Done criterion:** `:tenants:<region>:base:compileKotlin` succeeds.

### Step 2.7 — Build the default tenant for the region

```
tenants/<region>/default/src/main/kotlin/<your-org-domain>/tenants/<region>/default/
└── di/
    └── <RegionPrefix>DefaultTenantModule.kt # @TenantKey("<region>:default") bindings
```

This is the sentinel tenant — used in tests and as the no-overrides baseline. It must never resolve in production.

**Done criterion:** `:tenants:<region>:default:compileKotlin` succeeds; a `@HiltAndroidTest` injecting `TenantId("<region>:default")` resolves all policies.

### Step 2.8 — Build your first concrete tenant

```
tenants/<region>/<tenant-slug>/src/main/kotlin/<your-org-domain>/tenants/<region>/<tenant>/
├── flags/
│   └── <Tenant><Region>TenantProfile.kt     # TenantContext factory
├── policy/
│   └── <Tenant><Region>Specific*.kt         # tenant-specific overrides
├── support/
│   └── <Tenant><Region>SupportContacts.kt
├── capability/
│   └── <Tenant><Region>Capabilities.kt
└── di/
    └── <Tenant><Region>TenantModule.kt      # @TenantKey("<region>:<tenant-slug>") bindings
```

Use the concrete-rebinds-everything pattern: this module declares the full set of `@TenantKey("<region>:<tenant-slug>") @Binds` lines, reusing region-base classes where the regional baseline applies and overriding where it doesn't. See [`docs/framework/19-tenants-and-regions.md`](../docs/framework/19-tenants-and-regions.md).

**Done criterion:** `:tenants:<region>:<tenant-slug>:compileKotlin` succeeds. Register the tenant in `TenantCatalogue` in `:app`.

---

## Phase 3 — Light Up the First Feature

### Step 3.1 — Boot feature

Already present from Step 2.3. Verify the boot screen renders and routes correctly:
- MG fetch succeeds → Login screen
- MG fetch fails → fallback to cached config (if any) with banner, or "service unavailable"
- Maintenance state down → maintenance screen
- Version below minimum → force-update screen

**Done criterion:** all four boot outcomes can be triggered (use staging MG with controllable response).

### Step 3.2 — Auth feature

The minimum auth flow for any product:

```
features/src/main/kotlin/<your-org-domain>/features/auth/
├── login/                # credentials entry → AuthRepository.login(...)
├── pin/                  # PIN setup, PIN-verify after timeout
├── otp/                  # OTP entry + verify
├── biometric/            # biometric prompt
└── AuthNavigator.kt
```

Wire `LoginScreen` → `AuthRepository.login()` → on success, `BootCoordinator.onLoginSuccess(...)` → builds `LoggedInComponent` → navigates to dashboard.

**Done criterion:** with a mock backend, login succeeds and the dashboard route is reached. `LoggedInComponent` is built with the right `TenantContext`.

### Step 3.3 — Dashboard (empty for now)

```
features/src/main/kotlin/<your-org-domain>/features/dashboard/
├── DashboardScreen.kt    # renders "Hello, {tenant.displayName}!" — proves tenant context is wired
├── DashboardViewModel.kt
└── DashboardContract.kt
```

**Done criterion:** the dashboard shows the active tenant's display name on screen. **This is the hello-world milestone.**

---

## Phase 4 — Add Capability Layers as Needed

The framework's capability docs (20–30) describe each capability in isolation. As your PRD's features come in, light up the relevant capability:

| Need | Doc | Add when |
|---|---|---|
| In-app notifications | [`21-push-channels.md`](../docs/framework/21-push-channels.md) | First push payload arrives |
| Multi-language | [`25-locale.md`](../docs/framework/25-locale.md) | Second language is shipped |
| PIN UX | [`26-pin-and-session.md`](../docs/framework/26-pin-and-session.md) | First user creates a PIN |
| Camera capture (KYC, photos, scanning) | [`23-kyc-capture.md`](../docs/framework/23-kyc-capture.md) | First flow needs camera |
| Real-time chat | [`20-chat.md`](../docs/framework/20-chat.md) | First chat thread is in scope |
| Deeplinks | [`22-deeplinks.md`](../docs/framework/22-deeplinks.md) | First push payload tap or SMS link |
| Map view (locator) | [`27-maps-and-location.md`](../docs/framework/27-maps-and-location.md) | First map-based screen is in scope |
| PDF | [`24-pdf.md`](../docs/framework/24-pdf.md) | First downloadable document is shipped |
| Background work | [`28-background-work.md`](../docs/framework/28-background-work.md) | First upload-with-retry need |
| Local DB | [`29-local-database.md`](../docs/framework/29-local-database.md) | First cached list (chat, locations, drafts) |
| Multi-step form | [`30-form-wizard.md`](../docs/framework/30-form-wizard.md) | First flow with 3+ steps |

Add incrementally as feature work demands. Don't over-build infrastructure.

---

## Phase 5 — Productionalization Pre-Launch

Before any production traffic:

- [ ] CI lint rule active: fail on `tenant.id ==` outside `:tenants:*` and `TenantResolverModule`
- [ ] CI lint rule active: fail on hardcoded URLs outside `BuildConfig.MG_URL` and `STALE_CONFIG_TTL_MS`
- [ ] kotlin-binary-compatibility-validator passing on `:aos-sdk` boundary
- [ ] Crashlytics + Analytics wired with PII-stripping middleware
- [ ] App Links verified via `https://app.<your-host>/.well-known/assetlinks.json` returning the right SHA-256 fingerprints for prod and staging build variants
- [ ] FCM push tested through every channel your product uses
- [ ] Stale-config fallback tested (MG down → cached config takes over → banner shown → recovery works)
- [ ] Logout flow proven to drop the `LoggedInComponent` cleanly (instrumentation test, not just smoke)
- [ ] Screenshot blocking (`FLAG_SECURE`) verified on PIN, identity-capture, profile, contract screens
- [ ] All scoped DBs wiped on logout (chat, drafts, notifications, cached lists)
- [ ] Force-update gate tested by serving a bumped `minimumVersionCode` from MG

---

## Phase 6 — Tenant Onboarding (Adding the Second Tenant)

When a second tenant ships under the same region — your second concrete tenant module — follow [`docs/framework/13-onboarding-a-tenant.md`](../docs/framework/13-onboarding-a-tenant.md). The expectation is **strictly additive**:

- New module under `:tenants:<region>:<new-tenant-slug>`
- One `settings.gradle.kts` `include(":tenants:<region>:<new-tenant-slug>")`
- One entry in `TenantCatalogue`
- Backend coordinated to return the new `tenantId` for the right users
- Zero edits to `:aos-sdk`, `:core`, `:data`, `:design-system`, `:features`, sibling tenants

This is the architectural test of "does the framework hold up?" — if onboarding tenant #2 requires more than this, the framework has been violated somewhere.

---

## Anti-Checklist (Things Not To Do)

- ❌ Do not start with a 12-module Gradle structure and try to fill it all in. Start with `:core` + `:design-system` + `:app` + one tenant; add the rest as feature work demands.
- ❌ Do not hand-roll the tenant-policy dispatch. The framework's multibinding pattern is the contract; rolling your own breaks Logic-Blind compile-time enforcement.
- ❌ Do not put your domain types in `:aos-sdk`. The SDK is product-agnostic; if you find yourself wanting to add domain types to the SDK, they belong in `:core` or your `:features` module.
- ❌ Do not skip the `:tenants:<region>:default` module. Tests depend on it.
- ❌ Do not hardcode your backend URLs in `BuildConfig`. The framework's *whole point* is MG-driven discovery; cheating here on day one breaks the architecture.
- ❌ Do not copy from `docs/reference-app/` and inherit its domain assumptions. Read `docs/framework/` for rules; treat `docs/reference-app/` as one illustration, not the standard.

---

## Cross-references

- The strategy that makes this checklist possible: [`00-strategy.md`](00-strategy.md)
- The framework spec this checklist follows: [`../docs/framework/`](../docs/) (after the refactor)
- The reference instance to crib from for shape (not for content): [`../docs/reference-app/`](../docs/) (after the refactor)
- Naming decisions to settle first: [`04-naming-decisions.md`](04-naming-decisions.md)
- The current reference instance described in detail: [`05-case-study-lending.md`](05-case-study-lending.md)
