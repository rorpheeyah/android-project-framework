# 08 · Runtime Tenant Switching

> **Strategic Requirement #1:** Zero-Trust Runtime Switching.
> Switch tenants in a running app **without restart**, with **complete memory purge** and **fresh DI graph**.

This is the highest-leverage mechanism in the framework. Get this wrong and tenant logic leaks across users; get this right and the rest of the architecture pays dividends.

---

## 1. Why "Zero-Trust"?

Treating tenant switches as configuration changes is a security hazard. A residual `StateFlow` value, a still-warm OkHttp connection, or an undisposed cached account list is a path for **cross-tenant data contamination**.

Nexus treats every tenant switch as if it were a logout-then-login of a different user — even when it's actually just a developer toggling tenants in QA. The price is a few extra milliseconds per switch; the payoff is no class of bug where tenant A's data renders in tenant B's session.

---

## 2. The Switch Sequence

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. LOCK         UI shows blocking spinner; new events ignored   │
├─────────────────────────────────────────────────────────────────┤
│ 2. CANCEL       All in-flight coroutines cancelled              │
├─────────────────────────────────────────────────────────────────┤
│ 3. PURGE        Session tokens revoked; EncryptedPrefs scope    │
│                  switched; in-memory caches cleared             │
├─────────────────────────────────────────────────────────────────┤
│ 4. DESTROY      Tear down current TenantComponent (and all      │
│                  @TenantScoped instances within it)             │
├─────────────────────────────────────────────────────────────────┤
│ 5. REBUILD      Construct new TenantComponent for target tenant │
│                  with target tenant's bindings                   │
├─────────────────────────────────────────────────────────────────┤
│ 6. COMMIT       TenantProvider emits new TenantContext;          │
│                  navigate to splash; UI re-collects fresh state │
├─────────────────────────────────────────────────────────────────┤
│ 7. UNLOCK       UI accepts events again                         │
└─────────────────────────────────────────────────────────────────┘
```

**Order is invariant.** Never re-inject before purging — that lets stale dependencies serve a request meant for the new tenant.

---

## 3. The `@TenantScoped` Hilt Component

Hilt's standard scopes (`@Singleton`, `@ActivityRetainedScoped`, etc.) tie lifetimes to Android lifecycle objects. Tenant switching needs a scope tied to **the active tenant**, which has no Android lifecycle counterpart. We define a custom Hilt component:

```kotlin
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class TenantScoped

@DefineComponent(parent = SingletonComponent::class)
@TenantScoped
interface TenantComponent {
    @DefineComponent.Builder
    interface Builder {
        fun bindTenantContext(@BindsInstance ctx: TenantContext): Builder
        fun build(): TenantComponent
    }
}

@EntryPoint
@InstallIn(TenantComponent::class)
interface TenantEntryPoint {
    fun transferRepository(): TransferRepository
    fun authRepository(): AuthRepository
    fun accountRepository(): AccountRepository
    fun transferAmountPolicy(): TransferAmountPolicy
    fun feeCalculator(): FeeCalculator
}
```

Every tenant module installs into `TenantComponent::class`:

```kotlin
@Module
@InstallIn(TenantComponent::class)
abstract class KhTenantModule { … }
```

When the component is destroyed, **every `@TenantScoped` instance is garbage-collected**. This is the structural mechanism that guarantees no live tenant-A repository can serve tenant-B requests.

---

## 4. The `TenantComponentManager`

The component is built and torn down by a singleton:

```kotlin
@Singleton
class TenantComponentManager @Inject constructor(
    @TenantBuilder private val builderProvider: Provider<TenantComponent.Builder>,
) {
    private var current: TenantComponent? = null
    private val mutex = Mutex()

    suspend fun recreate(targetTenant: TenantId, contextLoader: suspend (TenantId) -> TenantContext): TenantComponent =
        mutex.withLock {
            current?.let { releaseInternal(it) }
            val ctx = contextLoader(targetTenant)
            val fresh = builderProvider.get().bindTenantContext(ctx).build()
            current = fresh
            fresh
        }

    fun current(): TenantComponent = checkNotNull(current) { "TenantComponent not initialized" }

    private fun releaseInternal(component: TenantComponent) {
        // Hilt itself does not surface a "destroy" call — the component object
        // becomes unreferenced and GC-eligible once we drop it. We still
        // proactively notify any listeners so they release their own resources.
    }
}
```

Hilt does not directly expose component destruction APIs, but **dropping the only reference makes all `@TenantScoped` instances eligible for GC**. The manager makes that drop atomic with the rebuild.

---

## 5. Resolving Repositories at Call Site

`:features` ViewModels do **not** receive `TransferRepository` directly via Hilt's normal injection (which binds at ViewModel construction — too early). Instead, they receive a `TenantEntryPointAccessor`:

```kotlin
@Singleton
class TenantEntryPointAccessor @Inject constructor(
    private val componentManager: TenantComponentManager,
) {
    fun current(): TenantEntryPoint =
        EntryPoints.get(componentManager.current(), TenantEntryPoint::class.java)
}

@HiltViewModel
class TransferInputViewModel @Inject constructor(
    private val tenantEntryPoint: TenantEntryPointAccessor,
) : MviViewModel<…>(…) {

    private val transferRepo: TransferRepository get() = tenantEntryPoint.current().transferRepository()
    private val amountPolicy: TransferAmountPolicy get() = tenantEntryPoint.current().transferAmountPolicy()
    // …
}
```

Resolving lazily through the entry point ensures the ViewModel always sees the **currently active tenant's** implementations — even if the tenant switched mid-session.

> **Trade-off:** the `:features` ViewModel constructor signature loses the explicit `TransferRepository` parameter and that hurts test ergonomics. Mitigation: `TenantEntryPointAccessor` is itself a thin interface, easy to fake in unit tests.

---

## 6. Memory Purge Strategy

Switching tenants must clear three categories of memory:

### 6.1 Session state (high sensitivity)

- **Tokens** held in `EncryptedPrefs`: scoped per tenant ID. The active tenant's prefs file becomes inaccessible when the scope changes.

  ```kotlin
  EncryptedPrefs.scope(tenantId).get(Keys.AUTH_TOKEN)   // tenant-A scope
  EncryptedPrefs.scope(otherTenantId).get(Keys.AUTH_TOKEN) // empty until login
  ```

- **In-memory `UserSession`** held in a `@TenantScoped` `SessionStore`: dies with the component.

### 6.2 ViewModel state (medium sensitivity)

ViewModels are scoped to navigation back-stack entries. The switch sequence **navigates to splash**, which pops every back-stack entry. ViewModels are then GC'd along with their `StateFlow`s.

If a ViewModel must outlive splash navigation (rare), it should not be `@TenantScoped` and should explicitly call `state = initial` on switch — but the recommended pattern is to never let such a ViewModel exist.

### 6.3 Caches (low sensitivity but mandatory)

| Cache | Cleared by |
|---|---|
| OkHttp response cache | `OkHttpClient.cache?.evictAll()` in `CacheCleaner` |
| Image loader cache | `Coil`/`Glide` API, called in `CacheCleaner` |
| App-level memoization | Each cache holder must implement `TenantSwitchListener` |

`CacheCleaner` aggregates these:

```kotlin
@Singleton
class CacheCleaner @Inject constructor(
    private val httpClient: OkHttpClient,
    private val listeners: Set<@JvmSuppressWildcards TenantSwitchListener>,
) {
    suspend fun clear() {
        httpClient.cache?.evictAll()
        listeners.forEach { it.onTenantSwitch() }
    }
}
```

Modules register listeners via Hilt multibindings:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class CacheCleanerBindings {
    @Binds @IntoSet abstract fun bindImageCache(impl: ImageCacheCleaner): TenantSwitchListener
    // …
}
```

Adding a new cache means adding one multibinding line.

---

## 7. The `TenantSwitcher` Public API

`:app` exposes one method to the rest of the app:

```kotlin
@Singleton
class TenantSwitcher @Inject constructor(
    private val sessionPurger: SessionPurger,
    private val cacheCleaner: CacheCleaner,
    private val componentManager: TenantComponentManager,
    private val tenantProvider: DefaultTenantProvider,
    private val navigator: GlobalNavigator,
    private val uiLock: UiLock,
) {

    suspend fun switchTo(target: TenantId) {
        uiLock.lock()
        try {
            sessionPurger.purge()
            cacheCleaner.clear()
            componentManager.recreate(target) { tenantProvider.contextFor(it) }
            tenantProvider.commit(target)
            navigator.navigate(Route.Splash, popUpToRoot = true)
        } finally {
            uiLock.unlock()
        }
    }
}
```

This is the **only** function that should be touched when changing the swap mechanics. Everything else is plumbing.

---

## 8. Verifying the Switch Worked

A QA-friendly self-check, callable from debug overlay:

```kotlin
suspend fun selfTest(): SwitchHealthReport {
    val activeId = tenantProvider.currentTenant.value.id
    val componentTenant = componentManager.current().tenantContext.id
    return SwitchHealthReport(
        tenantProviderAndComponentMatch = (activeId == componentTenant),
        sessionTokenAbsentInOtherTenants = encryptedPrefs.allScopes()
            .filter { it != activeId }
            .none { encryptedPrefs.scope(it).contains(Keys.AUTH_TOKEN) },
        httpCacheEmpty = httpClient.cache?.size() == 0L,
    )
}
```

If any check fails after a switch, the framework has a bug — escalate immediately.

---

## 9. What This Mechanism Does NOT Solve

- **Logged-in user with multiple roles in the same tenant** — that's a session-scope problem, not a tenant-scope problem.
- **A/B testing of two tenants simultaneously** — each `Application` instance can host one active tenant. Side-by-side comparison requires two devices.
- **Background workers (`WorkManager`) launched under tenant A and completing under tenant B** — solve by tagging work requests with `tenantId` and rejecting on mismatch in the worker's first step.

---

## 10. Cross-references

- Where the switch is invoked from: [06 — `:app`](06-app-orchestrator.md)
- The `TenantContext` that drives the switch: [03 — `:core`](03-core.md)
- How environment switching coexists with tenant switching: [09 — Environment Configuration](09-environment-configuration.md)
