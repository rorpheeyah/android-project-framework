# 10 · Boot Phases

> **Architectural promise:** the variant is selected once, at login. Configuration is fetched once, from MG, at cold start. There is no in-session swap.
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
│                  forceUpdate }                                  │
├─────────────────────────────────────────────────────────────────┤
│ 3. GATE         If maintenance.down or version < min →          │
│                  MaintenanceGate; HARD STOP (no login).         │
│                  Else: proceed.                                 │
├─────────────────────────────────────────────────────────────────┤
│ 4. LOGIN        AuthRepository.login(...) → LoginResponse        │
│                  containing { userSession, accounts[],          │
│                  variantId }                                    │
├─────────────────────────────────────────────────────────────────┤
│ 5. BUILD        BootCoordinator builds LoggedInComponent with   │
│                  VariantContext from variantId and the Session  │
│                  built from the LoginResponse                    │
├─────────────────────────────────────────────────────────────────┤
│ 6. ENTER MAIN   Navigate to MainScaffold. ViewModels resolve    │
│                  repositories (from :data) and policies (from   │
│                  the active :variants-{id}) via LoggedInEntryPt │
├─────────────────────────────────────────────────────────────────┤
│ 7. LOGOUT       LoggedInComponent dropped. All @LoggedInScoped  │
│                  instances become GC-eligible. RuntimeConfig    │
│                  stays (it's process-scoped). Navigate to login.│
└─────────────────────────────────────────────────────────────────┘
```

**Variant change requires logout.** There is no shortcut.

---

## 2. The Hardcoded MG URL (and only that)

The only network configuration baked into the binary is the MG URL:

```kotlin
// :app/build.gradle.kts (per build type)
buildConfigField("String", "MG_URL", "\"https://mg.compass.bank/\"")
// debug → "\"https://mg.staging.compass.bank/\""
```

`MgClient` reads `BuildConfig.MG_URL`. Everything else — main API base URL, auxiliary endpoints, maintenance state, version floor — comes from MG's response. A backend URL change requires no new APK.

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
        fun bindVariantContext(@BindsInstance ctx: VariantContext): Builder
        fun bindSession(@BindsInstance session: Session): Builder
        fun build(): LoggedInComponent
    }
}

@EntryPoint
@InstallIn(LoggedInComponent::class)
interface LoggedInEntryPoint {
    fun transferRepository(): TransferRepository      // from :data
    fun authRepository(): AuthRepository              // from :data
    fun accountRepository(): AccountRepository        // from :data
    fun transferAmountPolicy(): TransferAmountPolicy  // from :variants-{id}
    fun feeCalculator(): FeeCalculator                // from :variants-{id}
    fun capabilities(): VariantCapabilities           // from :variants-{id}
    fun session(): Session
}
```

Both `:data` and *every* `:variants-{id}` install into `LoggedInComponent::class`:

```kotlin
@Module @InstallIn(LoggedInComponent::class) abstract class DataModule { … }       // shared repos
@Module @InstallIn(LoggedInComponent::class) abstract class KhVariantModule { … }  // @IntoMap @VariantKey("kh")
@Module @InstallIn(LoggedInComponent::class) abstract class VnVariantModule { … }  // @IntoMap @VariantKey("vn")
@Module @InstallIn(LoggedInComponent::class) object VariantResolverModule { … }    // picks active by VariantContext.id
```

Variant bindings use Dagger multibindings with a `@VariantKey("<id>")` map key, so all variants compile in without a duplicate-binding error. `VariantResolverModule` (in `:app`) looks up the active variant's entry from the map at injection time. See [07 — `:variants-*` § 6](07-variants.md) for the full pattern.

When the component is dropped (on logout), **every `@LoggedInScoped` instance becomes GC-eligible**. This is the structural mechanism that prevents one user's data from leaking into another user's session after logout.

---

## 4. The `LoggedInComponentManager`

The component is built once at login and dropped at logout:

```kotlin
@Singleton
class LoggedInComponentManager @Inject constructor(
    @LoggedInBuilder private val builderProvider: Provider<LoggedInComponent.Builder>,
    private val variantContextResolver: VariantContextResolver,
) {
    private var current: LoggedInComponent? = null
    private val mutex = Mutex()

    suspend fun build(variantId: VariantId, session: Session): LoggedInComponent =
        mutex.withLock {
            check(current == null) { "LoggedInComponent already built — call drop() first" }
            val ctx = variantContextResolver.resolve(variantId)
            val fresh = builderProvider.get()
                .bindVariantContext(ctx)
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

> **Why no rebuild?** Variant change in production is logout. The user-visible operation is the same; the implementation is one Hilt component drop instead of an in-session swap with purge choreography.

---

## 5. Resolving Repositories and Policies at Call Site

`:features` ViewModels inject `:core` interfaces directly via Hilt's normal injection path:

```kotlin
@HiltViewModel
internal class TransferInputViewModel @Inject constructor(
    private val transferRepo: TransferRepository,        // resolved from :data via LoggedInComponent
    private val amountPolicy: TransferAmountPolicy,      // resolved from :variants-{active} via LoggedInComponent
    capabilities: VariantCapabilities,                   // same path
) : MviViewModel<…>(…) { … }
```

For Hilt to find these inside the ViewModel scope (which is a child of `SingletonComponent`, not `LoggedInComponent`), the `LoggedInEntryPoint` is wired into a `SingletonComponent`-scoped façade:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object LoggedInBindingsModule {

    @Provides
    fun transferRepository(manager: LoggedInComponentManager): TransferRepository =
        EntryPoints.get(manager.current(), LoggedInEntryPoint::class.java).transferRepository()

    @Provides
    fun transferAmountPolicy(manager: LoggedInComponentManager): TransferAmountPolicy =
        EntryPoints.get(manager.current(), LoggedInEntryPoint::class.java).transferAmountPolicy()

    // … one provider per :core interface ViewModels consume
}
```

This indirection is small (one provider per interface) and pays for itself: ViewModels stay test-friendly with explicit interface dependencies, and the active variant + data bindings still resolve through `LoggedInComponent`.

> **Trade-off:** every `:core` interface that ViewModels consume needs a one-line provider entry. Acceptable given the surface (~15 interfaces) and stability.

---

## 6. Memory at Logout

Logout drops three categories of state:

### 6.1 Session state (high sensitivity)

- **Tokens** held in `EncryptedPrefs`: cleared by `LogoutHandler.purge()`.
- **In-memory `Session`** held inside `LoggedInComponent`: dies with the component.
- **`UserSession`** held inside `Session`: same.
- **Repository instances** in `:data` (`@LoggedInScoped`): same.
- **Policy instances** from the active variant (`@LoggedInScoped`): same.

### 6.2 ViewModel state (medium sensitivity)

ViewModels are scoped to navigation back-stack entries. Logout **navigates to the login route** with `popUpToRoot = true`, which pops every back-stack entry. ViewModels are then GC'd along with their `StateFlow`s.

### 6.3 Caches (low sensitivity but mandatory)

| Cache | Cleared by |
|---|---|
| OkHttp response cache | `OkHttpClient.cache?.evictAll()` in `LogoutHandler` |
| Image loader cache | `Coil`/`Glide` API, called in `LogoutHandler` |

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

### 6.4 Variant change happens through logout-login

The boot mechanism naturally handles variant changes — there is no special "switch variant" code path.

Concrete flow when a user signed in as KH wants to use VN credentials:

1. User taps **Logout**. `LogoutHandler.logout()` runs: `EncryptedPrefs` cleared, OkHttp cache evicted, `LoggedInComponent` dropped, navigation popped to `Route.Login`.
2. User enters VN credentials. `AuthRepository.login(...)` returns `LoginResponse(userSession, accounts, variantId = "vn")`.
3. `BootCoordinator.onLoginSuccess(...)` builds a fresh `LoggedInComponent` with `VariantContext(id = "vn", …)` and the VN-specific policy bindings.
4. Navigation enters `MainScaffold`. ViewModels resolve `TransferAmountPolicy` to `VnTransferAmountPolicy`, `Fintech*Repo` to the same shared instances (variant-agnostic).

**Navigation safety** is automatic:

- `popUpToRoot = true` on logout pops every back-stack entry — no stale ViewModel can leak into the next session.
- The `LoggedInComponent` drop makes every `@LoggedInScoped` instance GC-eligible — no stale repo, policy, or session value can be reached.
- `EncryptedPrefs.clearSessionScope()` removes tokens — a request issued during the brief gap before re-login wouldn't authenticate.

The system handles "switch variant" as the same operation as "different user logs in" — because architecturally it is.

**Proof — instrumentation test that exercises the switch:**

```kotlin
// :app/src/androidTest/.../VariantSwitchTest.kt
@HiltAndroidTest
class VariantSwitchTest {

    @Inject lateinit var bootCoordinator: BootCoordinator
    @Inject lateinit var componentManager: LoggedInComponentManager
    @Inject lateinit var logoutHandler: LogoutHandler

    @Test fun `switching from KH to VN swaps active TransferAmountPolicy`() = runTest {
        // 1. Log in as KH.
        bootCoordinator.onLoginSuccess(loginResponseFor(VariantId("kh")))
        val khPolicy = activePolicy<TransferAmountPolicy>()
        assertTrue(khPolicy is KhTransferAmountPolicy)
        assertEquals(Money(BigDecimal("4_000_000"), Currency.KHR), khPolicy.dailyLimit)

        // 2. Log out — drops the LoggedInComponent.
        logoutHandler.logout()
        assertFailsWith<IllegalStateException> { componentManager.current() }   // no active session

        // 3. Log in as VN.
        bootCoordinator.onLoginSuccess(loginResponseFor(VariantId("vn")))
        val vnPolicy = activePolicy<TransferAmountPolicy>()
        assertTrue(vnPolicy is VnTransferAmountPolicy)
        assertEquals(Money(BigDecimal("500_000_000"), Currency.VND), vnPolicy.dailyLimit)

        // 4. The KH instance from step 1 is GC-eligible — anything still holding it is leaked state.
        assertNotSame(khPolicy, vnPolicy)
    }

    private inline fun <reified T> activePolicy(): T =
        EntryPoints.get(componentManager.current(), LoggedInEntryPoint::class.java)
            .let { entry ->
                when (T::class) {
                    TransferAmountPolicy::class -> entry.transferAmountPolicy() as T
                    else -> error("Add accessor")
                }
            }
}
```

The test confirms: after logout-login with a different `variantId`, the active `TransferAmountPolicy` is a different concrete class, with different rules, resolved by the same Hilt entry point — without any code change.

---

## 7. What This Mechanism Does NOT Solve

- **Multiple roles inside one session** — that's a `Session` concern. See [12 — Departments and Session](12-departments-and-session.md).
- **A/B testing of two variants simultaneously** — each `Application` instance can host one logged-in session. Side-by-side comparison requires two devices or two app installs.
- **Background workers (`WorkManager`) launched during one session and completing after logout** — solve by tagging work requests with `userId` and rejecting on mismatch in the worker's first step.
- **Variant change without logout** — explicitly out of scope. The user-visible operation is logout-then-login.

---

## 8. Cross-references

- Where the boot is invoked from: [08 — `:app`](08-app-orchestrator.md)
- The `RuntimeConfig` that drives the gate: [11 — MG and Runtime Config](11-mg-and-runtime-config.md)
- The `Session` and account switching: [12 — Departments and Session](12-departments-and-session.md)
