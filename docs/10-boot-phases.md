# 10 · Boot Phases

> **Architectural promise:** the tenant is selected once, at login. Configuration is fetched once, from MG, at cold start (with stale-config fallback). There is no in-session swap.
>
> This is the highest-leverage doc in the framework. Get the boot phases right and the rest of the architecture pays dividends.

---

## 1. The Phases

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. COLD START   App.onCreate(); SecurityProvider self-checks    │
├─────────────────────────────────────────────────────────────────┤
│ 2. MG FETCH     BootCoordinator hits the hardcoded MG URL.      │
│                  Receives RuntimeConfig { urls, maintenance,    │
│                  forceUpdate, thirdPartyAppIds }. On failure,   │
│                  falls back to last-known-good cache (≤24h).    │
├─────────────────────────────────────────────────────────────────┤
│ 3. GATE         If maintenance.down or version < min →          │
│                  MaintenanceGate; HARD STOP (no login).         │
│                  Else: proceed.                                 │
├─────────────────────────────────────────────────────────────────┤
│ 4. LOGIN        AuthRepository.login(...) → LoginResponse        │
│                  containing { userSession, accounts[],          │
│                  tenantId, regionCode, defaultCurrency,         │
│                  tenantFlags, tenantParams }                    │
├─────────────────────────────────────────────────────────────────┤
│ 5. BUILD        BootCoordinator builds LoggedInComponent with   │
│                  TenantContext (from LoginResponse) and the     │
│                  Session                                         │
├─────────────────────────────────────────────────────────────────┤
│ 6. ENTER MAIN   Navigate to MainScaffold. ViewModels resolve    │
│                  repositories (from :data) and policies (from   │
│                  the active :tenants:{region}:{tenantSlug})     │
│                  via LoggedInEntryPoint                         │
├─────────────────────────────────────────────────────────────────┤
│ 7. LOGOUT       LoggedInComponent dropped. All @LoggedInScoped  │
│                  instances become GC-eligible. RuntimeConfig    │
│                  stays (it's process-scoped). Navigate to login.│
└─────────────────────────────────────────────────────────────────┘
```

**Tenant change requires logout.** There is no shortcut.

---

## 2. The Hardcoded MG URL (and only that)

The only network configuration baked into the binary is the MG URL, plus a single `staleConfigTtl` constant for the offline fallback window:

```kotlin
// :app/build.gradle.kts (per build type)
buildConfigField("String", "MG_URL", "\"https://mg.compass.bank/\"")
buildConfigField("long", "STALE_CONFIG_TTL_MS", "86_400_000L")   // 24h
// debug → "\"https://mg.staging.compass.bank/\""
```

`MgClient` reads `BuildConfig.MG_URL`. Everything else — main API base URL, third-party SDK app-ids (Sendbird, Google Maps), auxiliary endpoints, maintenance state, version floor — comes from MG's response. A backend URL change requires no new APK.

→ Detail: [11 — MG and Runtime Config](11-mg-and-runtime-config.md)

### 2.1 `BootScreen` — the UI driver of the boot phases

`:features/boot/BootScreen` is the navigation entry point. It calls `BootCoordinator.runBoot()` via its ViewModel and routes to the next destination based on the result.

```kotlin
// :features/boot/BootScreen.kt
@Composable
internal fun BootScreen(
    viewModel: BootViewModel = hiltViewModel(),
    onReady: () -> Unit,
    onMaintenance: (MaintenanceState.Down) -> Unit,
    onForceUpdate: (ForceUpdate) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                BootEffect.Ready              -> onReady()
                is BootEffect.Maintenance     -> onMaintenance(effect.state)
                is BootEffect.ForceUpdate     -> onForceUpdate(effect.forceUpdate)
            }
        }
    }

    when (state.phase) {
        BootPhase.Loading -> CompassLoadingScreen()
        BootPhase.Failed  -> CompassRetryScreen(state.errorMessage) { viewModel.onEvent(BootEvent.Retry) }
    }
}

@HiltViewModel
internal class BootViewModel @Inject constructor(
    private val bootCoordinator: BootCoordinator,
) : MviViewModel<BootState, BootEvent, BootEffect>(initial = BootState(BootPhase.Loading)) {

    init { runBoot() }

    override fun onEvent(event: BootEvent) = when (event) {
        BootEvent.Retry -> { setState { copy(phase = BootPhase.Loading) }; runBoot() }
    }

    private fun runBoot() = viewModelScope.launch {
        runCatching { bootCoordinator.runBoot() }
            .onSuccess { result ->
                when (result) {
                    BootResult.Ready              -> emitEffect(BootEffect.Ready)
                    is BootResult.Maintenance     -> emitEffect(BootEffect.Maintenance(result.state))
                    is BootResult.ForceUpdate     -> emitEffect(BootEffect.ForceUpdate(result.forceUpdate))
                }
            }
            .onFailure { e ->
                setState { copy(phase = BootPhase.Failed, errorMessage = e.message ?: "Cannot reach servers") }
            }
    }
}
```

`MaintenanceGate` and `ForceUpdateGate` are sibling Composables in the same package, rendered when navigation lands on their routes. See [11 — MG and Runtime Config](11-mg-and-runtime-config.md) for their UX.

---

## 3. The `@LoggedInScoped` Hilt Component

Hilt's standard scopes (`@Singleton`, `@ActivityRetainedScoped`, etc.) tie lifetimes to Android lifecycle objects. The framework needs a scope tied to **the logged-in session**, which has no Android lifecycle counterpart. We define a custom Hilt component:

```kotlin
// :core/scope/Scopes.kt
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class LoggedInScoped

// :app/di/LoggedInComponent.kt
@DefineComponent(parent = SingletonComponent::class)
@LoggedInScoped
interface LoggedInComponent {
    @DefineComponent.Builder
    interface Builder {
        fun bindTenantContext(@BindsInstance ctx: TenantContext): Builder
        fun bindSession(@BindsInstance session: Session): Builder
        fun build(): LoggedInComponent
    }
}

@EntryPoint
@InstallIn(LoggedInComponent::class)
interface LoggedInEntryPoint {
    fun loanRepository(): LoanRepository                  // from :data
    fun authRepository(): AuthRepository                  // from :data
    fun repaymentRepository(): RepaymentRepository        // from :data
    fun loanEligibilityPolicy(): LoanEligibilityPolicy    // from :tenants:{region}:{tenantSlug}
    fun emiCalculator(): EmiCalculator                    // from :tenants:{region}:{tenantSlug}
    fun capabilities(): TenantCapabilities                // from :tenants:{region}:{tenantSlug}
    fun session(): Session
}
```

Both `:data` and *every* concrete tenant module install into `LoggedInComponent::class`:

```kotlin
@Module @InstallIn(LoggedInComponent::class) abstract class DataModule { … }            // shared repos
@Module @InstallIn(LoggedInComponent::class) abstract class NhKhTenantModule { … }      // @IntoMap @TenantKey("cambodia:nh")
@Module @InstallIn(LoggedInComponent::class) abstract class KhDefaultTenantModule { … } // @IntoMap @TenantKey("cambodia:default")
@Module @InstallIn(LoggedInComponent::class) object TenantResolverModule { … }          // picks active by TenantContext.id
```

Tenant bindings use Dagger multibindings with a `@TenantKey("<region>:<tenantSlug>")` map key, so all tenants compile in without a duplicate-binding error. `TenantResolverModule` (in `:app`) looks up the active tenant's entry from the map at injection time. See [07 — `:tenants:*` § 6](07-variants.md) for the full pattern.

When the component is dropped (on logout), **every `@LoggedInScoped` instance becomes GC-eligible**. This is the structural mechanism that prevents one user's data from leaking into another user's session after logout.

---

## 4. The `LoggedInComponentManager`

The component is built once at login and dropped at logout:

```kotlin
@Singleton
class LoggedInComponentManager @Inject constructor(
    @LoggedInBuilder private val builderProvider: Provider<LoggedInComponent.Builder>,
) {
    private var current: LoggedInComponent? = null
    private val mutex = Mutex()

    suspend fun build(tenantContext: TenantContext, session: Session): LoggedInComponent =
        mutex.withLock {
            check(current == null) { "LoggedInComponent already built — call drop() first" }
            val fresh = builderProvider.get()
                .bindTenantContext(tenantContext)
                .bindSession(session)
                .build()
            current = fresh
            fresh
        }

    fun current(): LoggedInComponent =
        checkNotNull(current) { "LoggedInComponent not built — login first" }

    suspend fun drop() = mutex.withLock {
        current = null  // dropping the only reference makes everything inside GC-eligible
    }
}
```

`build()` is called from `BootCoordinator.onLoginSuccess(...)`. `drop()` is called from `LogoutHandler`. There are no other callers.

> **Why no rebuild?** Tenant change in production is logout. The user-visible operation is the same; the implementation is one Hilt component drop instead of an in-session swap with purge choreography.

---

## 5. Resolving Repositories and Policies at Call Site

`:features` ViewModels inject `:core` interfaces directly via Hilt's normal injection path:

```kotlin
@HiltViewModel
internal class LoanApplyViewModel @Inject constructor(
    private val loanAppRepo: LoanApplicationRepository,         // resolved from :data via LoggedInComponent
    private val eligibilityPolicy: LoanEligibilityPolicy,       // resolved from :tenants:{active} via LoggedInComponent
    capabilities: TenantCapabilities,                           // same path
) : MviViewModel<…>(…) { … }
```

For Hilt to find these inside the ViewModel scope (which is a child of `SingletonComponent`, not `LoggedInComponent`), the `LoggedInEntryPoint` is wired into a `SingletonComponent`-scoped façade:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object LoggedInBindingsModule {

    @Provides
    fun loanRepository(manager: LoggedInComponentManager): LoanRepository =
        EntryPoints.get(manager.current(), LoggedInEntryPoint::class.java).loanRepository()

    @Provides
    fun loanEligibilityPolicy(manager: LoggedInComponentManager): LoanEligibilityPolicy =
        EntryPoints.get(manager.current(), LoggedInEntryPoint::class.java).loanEligibilityPolicy()

    // … one provider per :core interface ViewModels consume
}
```

This indirection is small (one provider per interface) and pays for itself: ViewModels stay test-friendly with explicit interface dependencies, and the active tenant + data bindings still resolve through `LoggedInComponent`.

> **Trade-off:** every `:core` interface that ViewModels consume needs a one-line provider entry. Acceptable given the surface (~15 interfaces) and stability.

---

## 6. Memory at Logout

Logout drops three categories of state:

### 6.1 Session state (high sensitivity)

- **Tokens** held in `EncryptedPrefs`: cleared by `LogoutHandler.purge()`.
- **In-memory `Session`** held inside `LoggedInComponent`: dies with the component.
- **`UserSession`** held inside `Session`: same.
- **Repository instances** in `:data` (`@LoggedInScoped`): same.
- **Policy instances** from the active tenant (`@LoggedInScoped`): same.

### 6.2 ViewModel state (medium sensitivity)

ViewModels are scoped to navigation back-stack entries. Logout **navigates to the login route** with `popUpToRoot = true`, which pops every back-stack entry. ViewModels are then GC'd along with their `StateFlow`s.

### 6.3 Caches (low sensitivity but mandatory)

| Cache | Cleared by |
|---|---|
| OkHttp response cache | `OkHttpClient.cache?.evictAll()` in `LogoutHandler` |
| Image loader cache | `Coil` API, called in `LogoutHandler` |

Caches are *not* `@LoggedInScoped` (they are process-lifetime) — `LogoutHandler` has to clear them explicitly.

```kotlin
@Singleton
class LogoutHandler @Inject constructor(
    private val componentManager: LoggedInComponentManager,
    private val httpClient: OkHttpClient,
    private val encryptedPrefs: EncryptedPrefs,
    private val navigator: GlobalNavigator,
) {
    suspend fun logout() {
        encryptedPrefs.clearSessionScope()
        httpClient.cache?.evictAll()
        componentManager.drop()
        navigator.navigate(Route.Login, popUpToRoot = true)
    }
}
```

### 6.4 Tenant change happens through logout-login

The boot mechanism naturally handles tenant changes — there is no special "switch tenant" code path.

Concrete flow when a user signed in as `cambodia:nh` wants to sign in to a different tenant:

1. User taps **Logout**. `LogoutHandler.logout()` runs: `EncryptedPrefs` cleared, OkHttp cache evicted, `LoggedInComponent` dropped, navigation popped to `Route.Login`.
2. User enters new credentials. `AuthRepository.login(...)` returns `LoginResponse(userSession, accounts, tenantId = "cambodia:partner-a", ...)`.
3. `BootCoordinator.onLoginSuccess(...)` builds a fresh `LoggedInComponent` with `TenantContext(id = TenantId("cambodia:partner-a"), …)` and the Partner-A policy bindings.
4. Navigation enters `MainScaffold`. ViewModels resolve `LoanEligibilityPolicy` to the Partner-A-bound impl (often the region-base impl `KhDefaultLoanEligibilityPolicy`), `Fintech*Repo` to the same shared instances (tenant-agnostic).

**Navigation safety** is automatic:

- `popUpToRoot = true` on logout pops every back-stack entry — no stale ViewModel can leak into the next session.
- The `LoggedInComponent` drop makes every `@LoggedInScoped` instance GC-eligible — no stale repo, policy, or session value can be reached.
- `EncryptedPrefs.clearSessionScope()` removes tokens — a request issued during the brief gap before re-login wouldn't authenticate.

The system handles "switch tenant" as the same operation as "different user logs in" — because architecturally it is.

**Proof — instrumentation test that exercises the switch:**

```kotlin
// :app/src/androidTest/.../TenantSwitchTest.kt
@HiltAndroidTest
class TenantSwitchTest {

    @Inject lateinit var bootCoordinator: BootCoordinator
    @Inject lateinit var componentManager: LoggedInComponentManager
    @Inject lateinit var logoutHandler: LogoutHandler

    @Test fun `switching tenants resolves a different active policy chain`() = runTest {
        // 1. Log in as cambodia:nh.
        bootCoordinator.onLoginSuccess(loginResponseFor(TenantId("cambodia:nh")))
        val nhStaffValidator = activePolicy<StaffIdValidator>()
        assertTrue(nhStaffValidator is NhKhStaffIdValidator)

        // 2. Log out — drops the LoggedInComponent.
        logoutHandler.logout()
        assertFailsWith<IllegalStateException> { componentManager.current() }   // no active session

        // 3. Log in as a different tenant.
        bootCoordinator.onLoginSuccess(loginResponseFor(TenantId("cambodia:partner-a")))
        val partnerAValidator = activePolicy<StaffIdValidator>()
        assertTrue(partnerAValidator is PartnerAStaffIdValidator)

        // 4. The NH instance from step 1 is GC-eligible — anything still holding it is leaked state.
        assertNotSame(nhStaffValidator, partnerAValidator)
    }

    private inline fun <reified T> activePolicy(): T =
        EntryPoints.get(componentManager.current(), LoggedInEntryPoint::class.java)
            .let { entry ->
                when (T::class) {
                    StaffIdValidator::class -> entry.staffIdValidator() as T
                    else -> error("Add accessor")
                }
            }
}
```

The test confirms: after logout-login with a different `tenantId`, the active policy is a different concrete class, with different rules, resolved by the same Hilt entry point — without any code change.

---

## 7. What This Mechanism Does NOT Solve

- **Multiple roles inside one session** — that's a `Session` concern. See [12 — Departments and Session](12-departments-and-session.md).
- **A/B testing of two tenants simultaneously** — each `Application` instance can host one logged-in session. Side-by-side comparison requires two devices or two app installs.
- **Background workers (`WorkManager`) launched during one session and completing after logout** — solve by tagging work requests with `userId` and rejecting on mismatch in the worker's first step.
- **Tenant change without logout** — explicitly out of scope. The user-visible operation is logout-then-login.

---

## 8. Cross-references

- Where the boot is invoked from: [08 — `:app`](08-app-orchestrator.md)
- The `RuntimeConfig` that drives the gate: [11 — MG and Runtime Config](11-mg-and-runtime-config.md)
- The `Session` and account switching: [12 — Departments and Session](12-departments-and-session.md)
- The tenant behavioral model: [19 — Tenants and Regions](19-tenants-and-variants.md)
