# 06 · `:app` — The Orchestrator

> **Type:** Android application module
> **Role:** Assembly point. Wires `:aos-core`, `:core`, `:features`, and `:tenants:*` together.
> **Constraint:** This is the **only** module allowed to depend on every other module.

---

## 1. Purpose

`:app` is intentionally **thin**. It contains:

- The `Application` class
- The Manifest
- The top-level navigation graph
- The DI registry — every Hilt module needed to wire the running app
- The runtime tenant switcher
- The debug-only environment selection UI

Business logic does not live here. UI does not live here (beyond the host `Activity`). The orchestrator's job is to **glue**, nothing more.

---

## 2. Layout

```
:app/
└── src/main/kotlin/com/nexus/app/
    ├── NexusApplication.kt          # @HiltAndroidApp; bootstrap
    ├── MainActivity.kt              # Single-Activity host
    ├── AppNavigation.kt             # Top-level NavHost
    │
    ├── di/
    │   ├── NetworkModule.kt         # @Provides aos-core HTTP/Retrofit foundations
    │   ├── TenantBindingModule.kt   # Runtime swap registry — see [08]
    │   ├── TenantComponent.kt       # Custom @TenantScoped Hilt component
    │   ├── CoreModule.kt            # TenantProvider impl, EncryptedPrefs scoping
    │   └── FirebaseModule.kt        # Analytics, Crashlytics, RemoteConfig wiring
    │
    ├── tenant/
    │   ├── TenantSwitcher.kt        # Performs the purge → re-inject sequence
    │   └── DefaultTenantProvider.kt # Implements :core's TenantProvider
    │
    └── debug/                       # debug build type only
        ├── EnvironmentPicker.kt     # Compose dialog: Staging / UAT / Sandbox / Prod
        └── DebugOverlay.kt          # Tenant + env indicator
```

---

## 3. The Application Class

```kotlin
@HiltAndroidApp
class NexusApplication : Application() {

    @Inject lateinit var securityProvider: SecurityProvider
    @Inject lateinit var loggerInit: NexusLogger.Initializer

    override fun onCreate() {
        super.onCreate()
        loggerInit.install()
        securityProvider.runColdStartChecks()  // root/jailbreak abort
        // No tenant resolution here — happens after splash, post-auth
    }
}
```

The Application class is small on purpose. Heavyweight initialization happens lazily, gated by feature consumers.

---

## 4. Top-Level Navigation

`AppNavigation.kt` defines the only `NavHost` in the app. It strings together the feature graphs from `:features` and the chatbot graph from `:features-chatbot`:

```kotlin
@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(navController, startDestination = Route.Splash) {
        splashNavGraph(navController)        // from :features
        authNavGraph(navController)          // from :features
        mainScaffoldNavGraph(navController)  // from :features (transfer, account, …)
        chatbotNavGraph(navController)       // from :features-chatbot
    }
}
```

Each `*NavGraph` extension is exposed publicly by the corresponding feature module. Routes are referenced via `Route` constants, not raw strings.

---

## 5. The DI Registry

`:app/di/` is where every Hilt module not located in another module's source set lives:

| Module | Owns |
|---|---|
| `NetworkModule` | OkHttp, Retrofit factory, interceptors (auth, environment) |
| `CoreModule` | `TenantProvider` impl, `EncryptedPrefs` namespace scoping |
| `FirebaseModule` | Analytics, Crashlytics, Remote Config wiring |
| `TenantComponent` | Custom Hilt component scoped to the active tenant |
| `TenantBindingModule` | Routes the active tenant's bindings into `TenantComponent` — see [08] |

Tenant DI modules (`KhTenantModule`, `VnTenantModule`, …) are **defined in the tenant modules**, not here. `:app` references them indirectly through the `TenantComponent` lifecycle.

---

## 6. Runtime Tenant Switcher

`TenantSwitcher` is the single place that orchestrates a tenant change. The mechanism is detailed in [08 — Runtime Tenant Switching](08-runtime-tenant-switching.md). Here it is conceptually:

```kotlin
@Singleton
class TenantSwitcher @Inject constructor(
    private val tenantProvider: TenantProvider,
    private val tenantComponentManager: TenantComponentManager,
    private val sessionPurger: SessionPurger,
    private val cacheCleaner: CacheCleaner,
) {
    suspend fun switchTo(target: TenantId) {
        sessionPurger.purge()                 // wipe tokens, EncryptedPrefs scope
        cacheCleaner.clear()                  // memory + disk caches
        tenantComponentManager.recreate(target)  // rebuild @TenantScoped graph
        tenantProvider.commit(target)         // emit new TenantContext
    }
}
```

Order matters: **purge first**, then re-inject. Never the reverse.

---

## 7. Debug-Only Environment UI

`debug/` source set is included only in the `debug` build type. It exposes:

- A first-launch picker dialog letting QA pick `Staging` / `UAT` / `Sandbox` / `Production`
- A persistent overlay showing the active tenant + environment

In `release` builds, this code is not compiled in — `BuildConfig.DEBUG` gating ensures the production binary cannot expose the picker. The mechanism is detailed in [09 — Environment Configuration](09-environment-configuration.md).

---

## 8. What `:app` Does NOT Contain

| ❌ Doesn't belong | ✅ Goes in |
|---|---|
| Repository implementations | `:tenants:[id]` |
| Repository interfaces | `:core` |
| Compose screens | `:features` |
| OkHttp configuration | `:aos-core` |
| Banking business logic | `:tenants:[id]` |
| `if (tenant == X)` switches | nowhere |

`:app` should be small enough that a new engineer can read every file in it during onboarding. If `:app` is growing past a few hundred lines per file, logic is leaking in — push it back to the right module.

---

## 9. Cross-references

- The DI swap mechanism in detail: [08 — Runtime Tenant Switching](08-runtime-tenant-switching.md)
- Environment picker mechanics: [09 — Environment Configuration](09-environment-configuration.md)
- What each tenant module exposes for `:app` to consume: [05 — `:tenants:*`](05-tenants.md)
