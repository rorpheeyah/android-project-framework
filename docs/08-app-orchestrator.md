# 08 · `:app` — The Orchestrator

> **Type:** Android application module
> **Role:** Assembly point. Wires `:aos-sdk`, `:core`, `:data`, `:features`, and `:tenants:*:*` together; runs the boot sequence.
> **Constraint:** This is the **only** module allowed to depend on every other module.

---

## 1. Purpose

`:app` is intentionally **thin**. It contains:

- The `Application` class
- The Manifest
- The top-level navigation graph
- The DI registry — every Hilt module needed to wire the running app
- The `BootCoordinator` and the `LoggedInComponent` definition
- The `TenantCatalogue` and `TenantResolverModule` — the single point of dispatch for tenant-specific policies
- The debug-only environment-override UI

Business logic does not live here. UI does not live here (beyond the host `Activity`). The orchestrator's job is to **glue**, nothing more.

---

## 2. Layout

```
:app/
└── src/main/kotlin/com/<org>/app/
    ├── CompassApplication.kt        # @HiltAndroidApp; bootstrap
    ├── MainActivity.kt              # Single-Activity host
    ├── AppNavigation.kt             # Top-level NavHost
    │
    ├── boot/
    │   ├── BootCoordinator.kt       # Drives MG → gate → login → SessionGraph build
    │   ├── MgClient.kt              # Calls the hardcoded MG URL
    │   ├── BootResult.kt            # Ready | Maintenance | ForceUpdate
    │   └── StaleConfigFallback.kt   # 24h last-known-good cache for MG outage
    │
    ├── di/
    │   ├── NetworkModule.kt         # @Provides aos-sdk HTTP/Retrofit foundations
    │   ├── LoggedInComponent.kt     # Custom @LoggedInScoped Hilt component definition
    │   ├── LoggedInEntryPoint.kt    # Entry point exposing :data + :tenants:*:* bindings
    │   ├── LoggedInBindingsModule.kt# Singleton-scoped façade for ViewModel injection
    │   ├── TenantResolverModule.kt  # Picks active tenant's policy from the multibindings map
    │   ├── RuntimeConfigModule.kt   # Provides immutable RuntimeConfig once MG returns
    │   └── FirebaseModule.kt        # Analytics, Crashlytics, RemoteConfig wiring
    │
    ├── session/
    │   ├── SessionFactory.kt        # Builds Session from LoginResponse
    │   ├── AccountIdInterceptor.kt  # OkHttp interceptor stamping activeAccountId
    │   ├── InactivityDetector.kt    # Session timeout
    │   ├── LoggedInComponentManager.kt # Owns the lifecycle of LoggedInComponent
    │   └── LogoutHandler.kt         # Drops LoggedInComponent, clears prefs/caches
    │
    ├── tenant/
    │   ├── TenantCatalogue.kt       # TenantId → TenantProfile factory
    │   └── TenantContextResolver.kt # Builds TenantContext from LoginResponse
    │
    └── debug/                       # debug build type only
        ├── EnvironmentOverride.kt   # Compose dialog: Production / Staging / UAT / Sandbox
        └── DebugOverlay.kt          # Tenant + environment + active account indicator
```

---

## 3. The Application Class

```kotlin
@HiltAndroidApp
class CompassApplication : Application() {

    @Inject lateinit var securityProvider: SecurityProvider
    @Inject lateinit var loggerInit: Logger.Initializer

    override fun onCreate() {
        super.onCreate()
        loggerInit.install()
        securityProvider.runColdStartChecks()  // root/jailbreak abort
        // No MG fetch here — happens in BootCoordinator from BootScreen
    }
}
```

The Application class is small on purpose. Heavyweight initialization happens lazily, gated by feature consumers.

---

## 4. Top-Level Navigation

`AppNavigation.kt` defines the only `NavHost` in the app. It strings together the feature graphs from `:features` and the sibling feature modules:

```kotlin
@Composable
fun AppNavigation(
    navController: NavHostController,
    capabilities: TenantCapabilities,   // injected from active LoggedInComponent
) {
    NavHost(navController, startDestination = Route.Boot) {
        bootNavGraph(navController)                        // :features/boot — calls BootCoordinator
        authNavGraph(navController)                        // :features/auth — login + PIN + biometric + OTP
        mainScaffoldNavGraph(navController, capabilities)  // :features (dashboard, loan, …)
        chatbotNavGraph(navController)                     // :features-chatbot
        kycNavGraph(navController)                         // :features-kyc
        supportChatNavGraph(navController)                 // :features-support-chat
        branchLocatorNavGraph(navController)               // :features-branch-locator

        // Tenant-locked feature: only registered when the active tenant supports it.
        if (capabilities.supportsBakongDisputes()) {
            bakongDisputesNavGraph(navController)          // :features-bakong-disputes
        }
    }
}
```

**Capability-gated navigation** is how tenant-only feature UI is reachable. Three levels at which a `TenantCapabilities` flag controls visibility:

1. **Whole nav graph** — the entire feature module is conditionally registered (above, for `:features-bakong-disputes`). Tenants without the flag never reach those routes.
2. **Bottom-bar / scaffold tabs** — `mainScaffoldNavGraph` reads `capabilities.supportsCardlessAtm()` and includes/omits the ATM tab.
3. **In-screen elements** — a feature ViewModel reads a flag at construction and stores it as `UiState`, gating buttons or sections.

```kotlin
// Inside a :features ViewModel — flag flows into UiState, never into a `when` branch.
@HiltViewModel
internal class LoanApplyViewModel @Inject constructor(
    capabilities: TenantCapabilities,
    // …
) : MviViewModel<LoanApplyState, LoanApplyEvent, LoanApplyEffect>(
    initial = LoanApplyState(showGuarantorSection = capabilities.requiresGuarantor()),
)
```

The tenant module sets `requiresGuarantor() = true` (NH-KH) or `false` (a different tenant). The UI just reads `state.showGuarantorSection` — never `tenant.id`. Same pattern at every level.

Each `*NavGraph` extension is exposed publicly by the corresponding feature module. Routes are referenced via `Route` constants, not raw strings.

---

## 5. The DI Registry

`:app/di/` is where every Hilt module not located in another module's source set lives:

| Module | Owns |
|---|---|
| `NetworkModule` | OkHttp, Retrofit factory, interceptors (auth, base URL, account ID) |
| `RuntimeConfigModule` | Holds the singleton `RuntimeConfig` populated by `BootCoordinator` after MG returns |
| `FirebaseModule` | Analytics, Crashlytics, Remote Config wiring |
| `LoggedInComponent` | Custom Hilt component built once at login, dropped at logout |
| `LoggedInEntryPoint` | Hilt entry point that exposes `:data` repos + `:tenants:*:*` policies to outside collaborators |
| `LoggedInBindingsModule` | One `@Provides` per `:core` interface that ViewModels inject; routes through `LoggedInEntryPoint` |
| `TenantResolverModule` | The **single point of dispatch** for tenant-specific policies. One `@Provides` per `:core/policy/` interface, picking the active impl from the multibindings map by `TenantContext.id.value`. |

`DataModule` (in `:data`) and per-tenant modules (e.g. `NhKhTenantModule` in `:tenants:cambodia:nh`) are **defined in their own modules**, not here. `:app` references them indirectly through `LoggedInComponent`'s install scope — Hilt's annotation processor finds and aggregates them automatically.

---

## 6. The `BootCoordinator`

`BootCoordinator` is the single place that orchestrates a cold-start session. The mechanism is detailed in [10 — Boot Phases](10-boot-phases.md). Here it is conceptually:

```kotlin
@Singleton
class BootCoordinator @Inject constructor(
    private val mgClient: MgClient,
    private val staleConfigFallback: StaleConfigFallback,
    private val runtimeConfigStore: RuntimeConfigStore,
    private val sessionFactory: SessionFactory,
    private val tenantContextResolver: TenantContextResolver,
    private val componentManager: LoggedInComponentManager,
) {
    suspend fun runBoot(): BootResult {
        val config = try {
            mgClient.fetch().also { staleConfigFallback.cache(it) }
        } catch (_: Throwable) {
            staleConfigFallback.lastKnownGood()
                ?: return BootResult.Maintenance(MaintenanceState.Down("Service unavailable"))
        }
        runtimeConfigStore.commit(config)              // expose to :aos-sdk interceptors
        return when {
            config.maintenance is MaintenanceState.Down -> BootResult.Maintenance(config.maintenance)
            isBelowMinVersion(config.forceUpdate)      -> BootResult.ForceUpdate(config.forceUpdate)
            else                                       -> BootResult.Ready
        }
    }

    suspend fun onLoginSuccess(login: LoginResponse) {
        val session = sessionFactory.build(login)
        val tenantContext = tenantContextResolver.resolve(login)
        componentManager.build(tenantContext, session)
    }

    suspend fun onLogout() {
        componentManager.drop()  // → all @LoggedInScoped instances are GC-eligible
    }
}
```

This is the **only** function set that should be touched when changing the boot mechanics. Everything else is plumbing.

---

## 7. Debug-Only Environment Override

`debug/` source set is included only in the `debug` build type. It exposes:

- A dialog letting QA pick which **MG endpoint** to use (`Production` / `Staging` / `UAT` / `Sandbox`)
- A persistent overlay showing the current MG endpoint, the resolved `RuntimeConfig.urls.main`, the active tenant, and the active account

In `release` builds, this code is not compiled in — `BuildConfig.DEBUG` gating ensures the production binary cannot expose the picker. The mechanism is detailed in [11 — MG and Runtime Config](11-mg-and-runtime-config.md).

---

## 8. What `:app` Does NOT Contain

| ❌ Doesn't belong | ✅ Goes in |
|---|---|
| Repository implementations | `:data` |
| Repository interfaces | `:core` |
| Compose screens | `:features` |
| OkHttp configuration | `:aos-sdk` |
| Tenant-specific business logic | `:tenants:{region}:{tenantSlug}` |
| Regional baseline policies (currency, regulator rules) | `:tenants:{region}:base` |
| `if (tenant.id == X)` switches | nowhere (single point of dispatch is `TenantResolverModule`) |

`:app` should be small enough that a new engineer can read every file in it during onboarding. If `:app` is growing past a few hundred lines per file, logic is leaking in — push it back to the right module.

---

## 9. Cross-references

- The boot mechanism in detail: [10 — Boot Phases](10-boot-phases.md)
- MG endpoint mechanics: [11 — MG and Runtime Config](11-mg-and-runtime-config.md)
- The `Session` and account-switching mechanism: [12 — Departments and Session](12-departments-and-session.md)
- What `:data` and `:tenants:*:*` expose for `:app` to consume: [05 — `:data`](05-data.md), [07 — `:tenants:*`](07-variants.md)
