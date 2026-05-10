# 08 · `:app` — The Orchestrator

> **Type:** Android application module
> **Role:** Assembly point. Wires `:aos-core`, `:core`, `:data`, `:features`, and `:variants-*` together; runs the boot sequence.
> **Constraint:** This is the **only** module allowed to depend on every other module.

---

## 1. Purpose

`:app` is intentionally **thin**. It contains:

- The `Application` class
- The Manifest
- The top-level navigation graph
- The DI registry — every Hilt module needed to wire the running app
- The `BootCoordinator` and the `LoggedInComponent` definition
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
    │   └── BootResult.kt            # Ready | Maintenance | ForceUpdate
    │
    ├── di/
    │   ├── NetworkModule.kt         # @Provides aos-core HTTP/Retrofit foundations
    │   ├── LoggedInComponent.kt     # Custom @LoggedInScoped Hilt component definition
    │   ├── LoggedInEntryPoint.kt    # Entry point exposing :data + :variants-* bindings
    │   ├── LoggedInBindingsModule.kt# Singleton-scoped façade for ViewModel injection
    │   ├── RuntimeConfigModule.kt   # Provides immutable RuntimeConfig once MG returns
    │   └── FirebaseModule.kt        # Analytics, Crashlytics, RemoteConfig wiring
    │
    ├── session/
    │   ├── SessionFactory.kt        # Builds Session from LoginResponse
    │   ├── AccountIdInterceptor.kt  # OkHttp interceptor stamping activeAccountId
    │   ├── LoggedInComponentManager.kt # Owns the lifecycle of LoggedInComponent
    │   └── LogoutHandler.kt         # Drops LoggedInComponent, clears prefs/caches
    │
    ├── variant/
    │   ├── VariantCatalogue.kt      # The list of all known variants (VariantContexts)
    │   └── VariantContextResolver.kt # Maps VariantId → VariantContext at login time
    │
    └── debug/                       # debug build type only
        ├── EnvironmentOverride.kt   # Compose dialog: Production / Staging / UAT / Sandbox
        └── DebugOverlay.kt          # Variant + environment + active account indicator
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

`AppNavigation.kt` defines the only `NavHost` in the app. It strings together the feature graphs from `:features` and the chatbot graph from `:features-chatbot`:

```kotlin
@Composable
fun AppNavigation(
    navController: NavHostController,
    capabilities: VariantCapabilities,   // injected from active LoggedInComponent
) {
    NavHost(navController, startDestination = Route.Boot) {
        bootNavGraph(navController)                        // :features/boot — calls BootCoordinator
        authNavGraph(navController)                        // :features/auth — login + OTP
        mainScaffoldNavGraph(navController, capabilities)  // :features (transfer, account, …)
        chatbotNavGraph(navController)                     // :features-chatbot

        // Variant-locked feature: only registered when the active variant supports it.
        if (capabilities.supportsBakongDisputes()) {
            bakongDisputesNavGraph(navController)          // :features-bakong-disputes
        }
    }
}
```

**Capability-gated navigation** is how variant-only feature UI is reachable. Three levels at which a `VariantCapabilities` flag controls visibility:

1. **Whole nav graph** — the entire feature module is conditionally registered (above, for `:features-bakong-disputes`). Variants without the flag never reach those routes.
2. **Bottom-bar / scaffold tabs** — `mainScaffoldNavGraph` reads `capabilities.supportsCardlessAtm()` and includes/omits the ATM tab.
3. **In-screen elements** — a feature ViewModel reads a flag at construction and stores it as `UiState`, gating buttons or sections.

```kotlin
// Inside a :features ViewModel — flag flows into UiState, never into a `when` branch.
@HiltViewModel
internal class TransferInputViewModel @Inject constructor(
    capabilities: VariantCapabilities,
    // …
) : MviViewModel<TransferInputState, TransferInputEvent, TransferInputEffect>(
    initial = TransferInputState(showQrScanner = capabilities.supportsKhqrScan()),
)
```

The variant module sets `supportsKhqrScan() = true` (KH) or `false` (VN). The UI just reads `state.showQrScanner` — never `variantId`. Same pattern at every level.

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
| `LoggedInEntryPoint` | Hilt entry point that exposes `:data` repos + `:variants-*` policies to outside collaborators |
| `LoggedInBindingsModule` | One `@Provides` per `:core` interface that ViewModels inject; routes through `LoggedInEntryPoint` |

`DataModule` (in `:data`) and per-variant modules (e.g. `KhVariantModule` in `:variants-kh`) are **defined in their own modules**, not here. `:app` references them indirectly through `LoggedInComponent`'s install scope — Hilt's annotation processor finds and aggregates them automatically.

---

## 6. The `BootCoordinator`

`BootCoordinator` is the single place that orchestrates a cold-start session. The mechanism is detailed in [10 — Boot Phases](10-boot-phases.md). Here it is conceptually:

```kotlin
@Singleton
class BootCoordinator @Inject constructor(
    private val mgClient: MgClient,
    private val runtimeConfigStore: RuntimeConfigStore,
    private val sessionFactory: SessionFactory,
    private val componentManager: LoggedInComponentManager,
) {
    suspend fun runBoot(): BootResult {
        val config = mgClient.fetch()                  // hardcoded MG URL
        runtimeConfigStore.commit(config)              // expose to :aos-core interceptors
        return when {
            config.maintenance is MaintenanceState.Down -> BootResult.Maintenance(config.maintenance)
            isBelowMinVersion(config.forceUpdate)      -> BootResult.ForceUpdate(config.forceUpdate)
            else                                       -> BootResult.Ready
        }
    }

    suspend fun onLoginSuccess(login: LoginResponse) {
        val session = sessionFactory.build(login)
        componentManager.build(login.variantId, session)
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
- A persistent overlay showing the current MG endpoint, the resolved `RuntimeConfig.urls.main`, the active variant, and the active account

In `release` builds, this code is not compiled in — `BuildConfig.DEBUG` gating ensures the production binary cannot expose the picker. The mechanism is detailed in [11 — MG and Runtime Config](11-mg-and-runtime-config.md).

---

## 8. What `:app` Does NOT Contain

| ❌ Doesn't belong | ✅ Goes in |
|---|---|
| Repository implementations | `:data` |
| Repository interfaces | `:core` |
| Compose screens | `:features` |
| OkHttp configuration | `:aos-core` |
| Variant-specific business logic | `:variants-{id}` |
| `if (variantId == X)` switches | nowhere |

`:app` should be small enough that a new engineer can read every file in it during onboarding. If `:app` is growing past a few hundred lines per file, logic is leaking in — push it back to the right module.

---

## 9. Cross-references

- The boot mechanism in detail: [10 — Boot Phases](10-boot-phases.md)
- MG endpoint mechanics: [11 — MG and Runtime Config](11-mg-and-runtime-config.md)
- The `Session` and account-switching mechanism: [12 — Departments and Session](12-departments-and-session.md)
- What `:data` and `:variants-*` expose for `:app` to consume: [05 — `:data`](05-data.md), [07 — `:variants-*`](07-variants.md)
